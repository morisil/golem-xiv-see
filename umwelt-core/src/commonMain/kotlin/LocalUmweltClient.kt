/*
 * Umwelt - The web as your AI agent's Umwelt - every page transduced into the language a model natively perceives
 * Copyright (C) 2026  Kazimierz Pogoda / Xemantic
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xemantic.umwelt.core

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.browse.PageSession
import com.xemantic.markanywhere.browse.waitUntilLoaded
import com.xemantic.markanywhere.html.transformHtmlToMarkdown
import com.xemantic.markanywhere.render.asMarkdown
import com.xemantic.umwelt.api.Navigation
import com.xemantic.umwelt.api.ScreenshotFormat
import com.xemantic.umwelt.api.UmweltClient
import com.xemantic.umwelt.api.UmweltError
import com.xemantic.umwelt.api.UmweltSession
import dev.kdriver.cdp.domain.Network
import dev.kdriver.cdp.domain.network
import dev.kdriver.cdp.domain.page
import dev.kdriver.cdp.domain.target
import dev.kdriver.core.dom.Element
import dev.kdriver.core.tab.Tab
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

@Serializable
data class Config(
    val session: Session? = Session()
) {

    @Serializable
    data class Session(
        val screencast: Screencast? = Screencast()
    ) {
        @Serializable
        data class Screencast(
            val format: String? = "jpeg",
            val quality: Int? = null,
            val everyNthFrame: Int? = null
        )
    }

}

class LocalUmweltClient(
    private val config: Config,
    private val browsers: Browsers,
) : UmweltClient {

    private val sessionMap = mutableMapOf<String, UmweltSession>()

    private val mutex = Mutex()

    override fun listProfiles(): Flow<String> = flow {
        emitAll(browsers.profiles().map { it.name }.asFlow())
    }

    override suspend fun newSession(
        profile: String?
    ): UmweltSession = CoreUmweltSession(
        id = Uuid.random().toString(),
        tab = browsers.newTab(profile = profile),
        sessionConfig = config.session ?: Config.Session()
    ).also { session ->
        mutex.withLock {
            sessionMap[session.id] = session
        }
    }

    // snapshot under the lock: asFlow() iterates lazily at collection time, so
    // handing out the live mutable list would race newSession()/close()
    override fun listSessions(): Flow<UmweltSession> = flow {
        emitAll(
            mutex.withLock {
                sessionMap.values.toList()
            }.asFlow()
        )
    }

    override suspend fun getSession(
        id: String
    ): UmweltSession {
        validateSessionId(id)
        return mutex.withLock {
            sessionMap[id] ?: throw UmweltError.SessionNotFound(id).toException()
        }
    }

    private inner class CoreUmweltSession(
        override val id: String,
        private val tab: Tab,
        private val sessionConfig: Config.Session
    ) : UmweltSession {

        private val logger = KotlinLogging.logger {}

        private val pageSession = PageSession(tab)

        // serializes mutating operations on this tab — concurrent CDP calls
        // against one tab race. The screencast deliberately stays off this lock
        // so the tab can be watched while it navigates.
        private val lock = Mutex()

        // the session's own authoritative navigation history: every navigation it
        // performs appends or replaces an entry, so back/forward replay the url,
        // status, title and type recorded at first load instead of re-reading the
        // live browser — which is why status is always known, even from cache.
        // Guarded by [lock] like every other mutating operation.
        private val history = mutableListOf<HistoryEntry>()
        private var cursor = -1 // index of the current entry; -1 before the first navigation

        // session-scoped lifetime: cancelled in close(), which also tears down any
        // running screencast (the shared upstream below runs in this scope)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // One screencast shared across all collectors of stream(). WhileSubscribed
        // ref-counts subscribers: startScreencast fires on the first collector and
        // the upstream callbackFlow is cancelled (-> stopScreencast in its finally)
        // when the last one leaves. replay = 0 so a late joiner waits for the next
        // live frame rather than getting a stale one. send() still rides downstream
        // backpressure, so now the *slowest* active collector throttles the ack.
        private val screencast: Flow<ByteArray> = tab.page.let { page ->
            val screencastConfig = sessionConfig.screencast ?: Config.Session.Screencast()
            page.screencastFrame.onStart {
                logger.debug { "Starting screencast for session $id" }
                page.enable()
                page.startScreencast(
                    format = screencastConfig.format,
                    quality = screencastConfig.quality,
                    everyNthFrame = screencastConfig.everyNthFrame
                )
            }.transform { frame ->
                // emit first so the ack rides downstream backpressure: the
                // slowest active subscriber throttles Chrome instead of
                // buffering frames unboundedly
                emit(Base64.decode(frame.data))
                page.screencastFrameAck(frame.sessionId)
            }.onCompletion {
                withContext(NonCancellable) {
                    logger.debug { "Stopping screencast for session $id" }
                    runCatching { page.stopScreencast() }
                }
            }.shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                replay = 0
            )
        }

        // call page.navigate directly (not tab.get, which discards its result) so
        // observeDocument can read its synchronous errorText.
        override suspend fun goTo(url: String): Navigation = lock.withLock {
            val status = when (val outcome = observeDocument { tab.page.navigate(url).errorText }) {
                is DocumentOutcome.Failed ->
                    throw UmweltError.NavigationFailed(url, outcome.reason).toException()
                is DocumentOutcome.Loaded -> outcome.status
                DocumentOutcome.None -> STATUS_UNOBSERVED
            }
            val (finalUrl, title) = currentLocation()
            appendEntry(HistoryEntry(finalUrl, status, title, Navigation.Type.DOCUMENT))
            navigationOf(cursor)
        }

        override suspend fun back(): Navigation = lock.withLock {
            if (cursor <= 0) throw UmweltError.CannotGoBack().toException()
            stepHistory(step = -1)
            cursor--
            navigationOf(cursor)
        }

        override suspend fun forward(): Navigation = lock.withLock {
            if (cursor >= history.lastIndex) throw UmweltError.CannotGoForward().toException()
            stepHistory(step = +1)
            cursor++
            navigationOf(cursor)
        }

        override suspend fun navigation(): Navigation = lock.withLock { requireCurrent() }

        // CDP Page.reload reports no errorText of its own, so the only failure
        // signal is the Network domain's loadingFailed, which observeDocument
        // watches. A reload can still redirect, so the current entry is replaced
        // with the freshly settled url/status/title.
        override suspend fun reload(bypassCache: Boolean): Navigation = lock.withLock {
            if (cursor < 0) throw UmweltError.NoCurrentPage().toException()
            val status = when (
                val outcome = observeDocument {
                    tab.page.reload(ignoreCache = bypassCache)
                    null
                }
            ) {
                is Failed -> throw UmweltError.NavigationFailed(
                    history[cursor].url, outcome.reason
                ).toException()
                is Loaded -> outcome.status
                None -> history[cursor].status
            }
            val (finalUrl, title) = currentLocation()
            history[cursor] = HistoryEntry(finalUrl, status, title, Navigation.Type.DOCUMENT)
            navigationOf(cursor)
        }

        override suspend fun click(ref: String): Navigation = lock.withLock {
            val element = resolve(ref)
            val before = history.getOrNull(cursor)?.url
            // watch for a document load triggered by the click while it settles;
            // its absence (with an unchanged url) means an in-place mutation.
            val outcome = observeDocument { element.click(); null }
            val (finalUrl, title) = currentLocation()
            when (outcome) {
                is Failed -> {
                    throw UmweltError.NavigationFailed(
                        finalUrl,
                        outcome.reason
                    ).toException()
                }
                is DocumentOutcome.Loaded -> {
                    // a real document loaded — a navigating click
                    appendEntry(
                        HistoryEntry(finalUrl, outcome.status, title, Navigation.Type.DOCUMENT)
                    )
                }
                None -> when {
                    cursor < 0 -> { // a click that navigated before any tracked page (rare)
                        appendEntry(HistoryEntry(finalUrl, STATUS_UNOBSERVED, title, Navigation.Type.DOCUMENT))
                    }
                    finalUrl != before -> {
                        // url changed with no document fetch — an SPA history change;
                        // status is inherited from the document this location shares
                        appendEntry(
                            HistoryEntry(finalUrl, history[cursor].status, title, Navigation.Type.WITHIN_DOCUMENT)
                        )
                    }
                    else -> {
                        // in-place mutation — only the title may have changed
                        history[cursor] = history[cursor].copy(title = title)
                    }
                }
            }
            navigationOf(cursor)
        }

        override suspend fun type(
            ref: String,
            text: String,
            replace: Boolean
        ): Unit = lock.withLock {
            val element = resolve(ref)
            if (element.tag.lowercase() !in EDITABLE_TAGS) {
                throw UmweltError.ReferenceNotEditable(ref).toException()
            }
            // clearInput() zeroes the value via JS; sendKeys() then focuses and
            // dispatches real key events per character, so page-side input/change
            // handlers (validation, React controlled inputs, …) fire as for a user.
            if (replace) element.clearInput()
            element.sendKeys(text)
        }

        override suspend fun select(
            ref: String,
            option: String
        ): Unit = lock.withLock {
            val element = resolve(ref)
            if (element.tag.lowercase() != "select") {
                throw UmweltError.ReferenceNotSelectable(ref).toException()
            }
            // JSON-encode the target so it embeds as a safe JS string literal.
            // Match the option the agent can see (visible label/text) first, then
            // fall back to its underlying value; fire input + change like a user.
            val target = JsonPrimitive(option).toString()
            val matched = element.rawApply(
                "(el) => { " +
                    "const o = Array.from(el.options).find(o => " +
                    "o.label === $target || o.text === $target || o.value === $target); " +
                    "if (!o) return false; " +
                    "el.value = o.value; " +
                    "el.dispatchEvent(new Event('input', { bubbles: true })); " +
                    "el.dispatchEvent(new Event('change', { bubbles: true })); " +
                    "return true; }"
            )
            if ((matched as? JsonPrimitive)?.booleanOrNull != true) {
                throw UmweltError.OptionNotFound(ref, option).toException()
            }
        }

        /**
         * Resolves [ref] against the most recent [dump] to a live [Element],
         * translating [PageSession]'s [NoSuchElementException] (unknown or stale
         * ref) into the API's [UmweltError.ReferenceNotFound].
         */
        private suspend fun resolve(ref: String): Element = try {
            pageSession.element(ref)
        } catch (_: NoSuchElementException) {
            throw UmweltError.ReferenceNotFound(ref).toException()
        }

        /**
         * Runs [action] (a navigate, reload or click) and observes the main
         * document's CDP outcome: a `responseReceived` yields its HTTP status, a
         * `loadingFailed` means no document loaded ([DocumentOutcome.Failed]).
         *
         * The Network subscription is opened *before* [action] triggers the
         * request. After the page settles a short grace window is given for the
         * document event to surface (it fires with the response headers, well
         * before load completes), so a navigation is detected promptly while an
         * in-place click — which produces no document event — falls through to
         * [DocumentOutcome.None] without stalling.
         *
         * [action] may also return a synchronous errorText (the navigate call's
         * own failure report); that yields [DocumentOutcome.Failed] at once.
         */
        private suspend fun observeDocument(
            action: suspend () -> String?
        ): DocumentOutcome {
            tab.network.enable()
            // first() over the merged document events: redirects (3xx) ride a
            // request's redirectResponse, not separate responseReceived events,
            // so the first DOCUMENT response here is the final landed document.
            val event = scope.async {
                merge(
                    tab.network.responseReceived
                        .filter { it.type == Network.ResourceType.DOCUMENT }
                        .map { DocumentOutcome.Loaded(it.response.status) },
                    tab.network.loadingFailed
                        .filter { it.type == Network.ResourceType.DOCUMENT }
                        .map { DocumentOutcome.Failed(it.errorText) }
                ).first()
            }
            action()?.let { reason ->
                event.cancel()
                return DocumentOutcome.Failed(reason)
            }
            tab.waitUntilLoaded()
            val outcome = withTimeoutOrNull(SETTLE_GRACE) { event.await() }
            event.cancel()
            return outcome ?: DocumentOutcome.None
        }

        /** The tab's current document url and title, from its navigation history. */
        private suspend fun currentLocation(): Pair<String, String> =
            tab.page.getNavigationHistory().let { nav ->
                nav.entries.getOrNull(nav.currentIndex).let { entry ->
                    (entry?.url ?: "") to (entry?.title ?: "")
                }
            }

        /**
         * Steps the live browser [step] entries through its own navigation history
         * (−1 = back, +1 = forward) and waits for the page to settle. The session's
         * [cursor] is moved by the caller; this only drives the underlying tab.
         */
        private suspend fun stepHistory(step: Int) {
            val nav = tab.page.getNavigationHistory()
            nav.entries.getOrNull(nav.currentIndex + step)?.let { entry ->
                tab.page.navigateToHistoryEntry(entryId = entry.id)
                tab.waitUntilLoaded()
            }
        }

        /** Truncates any forward history, appends [entry], and points [cursor] at it. */
        private fun appendEntry(entry: HistoryEntry) {
            while (history.lastIndex > cursor) history.removeAt(history.lastIndex)
            history.add(entry)
            cursor = history.lastIndex
        }

        /** The [Navigation] for history entry [index], with affordances from [cursor]. */
        private fun navigationOf(index: Int): Navigation = history[index].let { entry ->
            Navigation(
                url = entry.url,
                status = entry.status,
                title = entry.title,
                type = entry.type,
                canGoBack = index > 0,
                canGoForward = index < history.lastIndex
            )
        }

        /** The current [Navigation], or [UmweltError.NoCurrentPage] if nothing has loaded yet. */
        private fun requireCurrent(): Navigation {
            if (cursor < 0) throw UmweltError.NoCurrentPage().toException()
            return navigationOf(cursor)
        }

        override fun dump(): Flow<SemanticEvent> = flow {
            // capture the snapshot under the lock, then emit off-lock
            val dump = lock.withLock { pageSession.dump() }
            emitAll(dump.events.asFlow())
        }

        // the local driver renders Markdown right here, where the page lives:
        // the dump() event stream through markanywhere's HTML->Markdown transform
        // and renderer. A remote proxy fetches this output over the wire instead.
        override fun dumpAsMarkdown(): Flow<String> =
            dump().transformHtmlToMarkdown().asMarkdown()

        override suspend fun screenshot(
            format: ScreenshotFormat,
            quality: Int?,
            captureBeyondViewport: Boolean
        ): ByteArray = lock.withLock {
            Base64.decode(
                tab.page.captureScreenshot(
                    format = format.name.lowercase(),
                    quality = quality,
                    captureBeyondViewport = captureBeyondViewport
                ).data
            )
        }

        override fun stream(): Flow<ByteArray> = screencast

        override suspend fun close() {
            logger.debug { "Closing session $id" }
            scope.cancel()
            // drop the session and close the tab unconditionally — a null
            // targetId must not strand the session in the list nor leak the tab
            mutex.withLock { sessionMap.remove(this.id) }
            tab.targetId?.let { tab.target.closeTarget(targetId = it) }
        }

    }

}


/** Tags whose elements accept [UmweltSession.type] text entry. */
private val EDITABLE_TAGS = setOf("input", "textarea")

/**
 * Grace window for the main document's CDP event to surface after the page has
 * settled. The event fires with the response headers — well before load
 * completes — so by the time the page has settled it has almost always already
 * arrived; this only bounds the wait for a navigation that produced no document
 * event at all (an in-place click), keeping such calls snappy.
 */
private val SETTLE_GRACE = 500.milliseconds

/** Status recorded for a document navigation whose CDP response was never observed. */
private const val STATUS_UNOBSERVED = 0

/** The main document's CDP load outcome, as observed by `observeDocument`. */
private sealed interface DocumentOutcome {
    /** A `responseReceived` for the document — its HTTP [status]. */
    data class Loaded(val status: Int) : DocumentOutcome
    /** A `loadingFailed` for the document — the network-level [reason]. */
    data class Failed(val reason: String) : DocumentOutcome
    /** No document event surfaced within the grace window (e.g. an in-place click). */
    object None : DocumentOutcome
}

/** One entry in a [LocalUmweltClient] session's own navigation history. */
private data class HistoryEntry(
    val url: String,
    val status: Int,
    val title: String,
    val type: Navigation.Type
)

private fun validateSessionId(id: String) {
    try {
        Uuid.parse(id)
    } catch (e: IllegalArgumentException) {
        throw UmweltError.InvalidSessionId(id).toException(e)
    }
}
