/*
 * Golem XIV See - See the web the way your AI agent sees it
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

package com.xemantic.umwelt.api

import com.xemantic.markanywhere.SemanticEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * The entry point for driving a browser the way an AI agent perceives the web:
 * it manages the browser profiles and the [UmweltSession]s (tabs) an agent
 * navigates and reads.
 *
 * An implementation may drive a browser **locally** (e.g. Chrome over CDP) or be
 * a thin **remote** proxy that speaks to such a client over another transport
 * (e.g. an HTTP/REST server); both honour the same contract, so callers are
 * written against this interface and never against a particular backend.
 */
interface UmweltClient {

    /**
     * Emits the names of the browser profiles available to [newSession]. A
     * profile is a named, isolated browser context (its own cookies, storage,
     * and logged-in state); the names returned here are the values accepted by
     * [newSession]'s `profile` parameter.
     *
     * @return the flow of profile names.
     */
    fun listProfiles(): Flow<String>

    /**
     * Opens a new browser session, backed by a fresh tab, and registers it so
     * it also appears in [listSessions].
     *
     * @param profile the name of the browser profile to run the session in, as
     *   listed by [listProfiles]; `null` uses a private ephemeral profile, closed
     *   on [UmweltSession.close].
     * @return the newly created session, ready to [UmweltSession.goTo] a URL.
     */
    suspend fun newSession(
        profile: String? = null
    ): UmweltSession

    /**
     * Emits the sessions currently open on this client — those created by
     * [newSession] and not yet [UmweltSession.close]d. The emitted sequence is
     * a point-in-time snapshot, so it is unaffected by sessions opened or
     * closed after collection begins.
     */
    fun listSessions(): Flow<UmweltSession>

    /**
     * Resolves an already-open session by its [UmweltSession.id] — a session
     * previously returned by [newSession] and still present in [listSessions]
     * (i.e. not yet [UmweltSession.close]d).
     *
     * @param id the id of the session to look up.
     * @return the live session for [id].
     * @throws UmweltException with:
     *   - [UmweltError.InvalidSessionId] if [id] is not a well-formed session id.
     *   - [UmweltError.SessionNotFound] if no open session has that id.
     */
    suspend fun getSession(id: String): UmweltSession

}

/**
 * A single browser session — one tab the agent drives and reads. Created by
 * [UmweltClient.newSession] (or re-acquired via [UmweltClient.getSession]) and
 * listed by [UmweltClient.listSessions] until [close].
 *
 * The navigation and interaction calls ([goTo], [back], [forward], [reload],
 * [click], [type], [select]) mutate the tab and are serialized with each other,
 * so a [dump]/[dumpAsMarkdown] taken afterwards reflects their effect. [dump]
 * (and its [dumpAsMarkdown] rendering) is the agent-readable view of the page;
 * [screenshot] and [stream] expose it visually — [stream] runs concurrently with
 * navigation so the tab can be watched while it moves.
 */
interface UmweltSession {

    /** The session's stable identifier — the handle for its underlying tab. */
    val id: String

    /**
     * Navigates to [url] (an `http(s)` URL) and suspends until the page has
     * settled.
     *
     * Like a browser, this settles on whatever the server returns: a 4xx/5xx
     * error page is still a real, [dump]-able page, so its HTTP status is
     * reported in the returned [Navigation] rather than thrown. Only a
     * navigation that produces **no document at all** fails (see `@throws`).
     *
     * A successful navigation truncates any forward history, so the returned
     * [Navigation.canGoForward] is `false`.
     *
     * @param url the `http(s)` URL to go to.
     * @return the settled [Navigation] — the final [url][Navigation.url] (after
     *   redirects), the document's [status][Navigation.status], its
     *   [title][Navigation.title] and [type][Navigation.type], and the resulting
     *   [canGoBack][Navigation.canGoBack]/[canGoForward][Navigation.canGoForward].
     * @throws UmweltException with [UmweltError.NavigationFailed] when no
     *   document loaded at all (DNS failure, connection refused, TLS error,
     *   timeout) — there is no page to [dump] in that case.
     */
    suspend fun goTo(url: String): Navigation

    /**
     * Navigates the tab back one entry in its history, like a browser's Back
     * button, and suspends until the page has settled.
     *
     * The caller is expected to gate this on the last reported
     * [Navigation.canGoBack]: calling [back] at the first entry is a misuse and
     * **throws** rather than silently doing nothing, so a driver maintaining its
     * own model of the tab fails fast instead of drifting out of sync.
     *
     * @return the [Navigation] the tab moved to — the restored entry's
     *   [url][Navigation.url], [status][Navigation.status] (as recorded when it
     *   was first loaded), [title][Navigation.title] and [type][Navigation.type],
     *   plus the new history affordances.
     * @throws UmweltException with:
     *   - [UmweltError.CannotGoBack] if the tab is already at the first entry.
     *   - [UmweltError.NavigationFailed] if re-entering the page loaded no
     *     document at all (the server became unreachable, etc.).
     */
    suspend fun back(): Navigation

    /**
     * Navigates the tab forward one entry in its history, like a browser's
     * Forward button, and suspends until the page has settled.
     *
     * As with [back], the caller is expected to gate this on the last reported
     * [Navigation.canGoForward]; calling [forward] at the last entry is a misuse
     * and **throws** rather than doing nothing.
     *
     * @return the [Navigation] the tab moved to — the restored entry's
     *   [url][Navigation.url], [status][Navigation.status] (as recorded when it
     *   was first loaded), [title][Navigation.title] and [type][Navigation.type],
     *   plus the new history affordances.
     * @throws UmweltException with:
     *   - [UmweltError.CannotGoForward] if the tab is already at the last entry.
     *   - [UmweltError.NavigationFailed] if re-entering the page loaded no
     *     document at all.
     */
    suspend fun forward(): Navigation

    /**
     * The tab's current [Navigation] state — where it is now and what history
     * moves are possible — read without moving. Use it to recover the state of a
     * session obtained via [UmweltClient.getSession], or after a non-navigating
     * [click].
     *
     * @return the current navigation state.
     * @throws UmweltException with [UmweltError.NoCurrentPage] if the session has
     *   not navigated anywhere yet (a freshly opened session before its first
     *   [goTo]).
     */
    suspend fun navigation(): Navigation

    /**
     * Reloads the tab's current page, like a browser's Reload button, and
     * suspends until the reloaded page has settled.
     *
     * It reports the reloaded document's outcome exactly like [goTo]: a 4xx/5xx
     * page is still a real, [dump]-able page, so its HTTP status is returned in
     * the [Navigation] rather than thrown.
     *
     * @param bypassCache when `true`, forces a network re-fetch ignoring the HTTP
     *   cache (a "hard reload"); when `false` (the default) a normal reload that
     *   may be served from cache.
     * @return the settled [Navigation] — the document's final [url][Navigation.url]
     *   (a reload can still redirect, e.g. onto a login page once the session
     *   expired), its [status][Navigation.status] and [title][Navigation.title].
     * @throws UmweltException with:
     *   - [UmweltError.NoCurrentPage] if the session has not navigated anywhere
     *     yet — there is no page to reload.
     *   - [UmweltError.NavigationFailed] when the reload loaded no document at all
     *     (the server became unreachable, DNS failure, TLS error, timeout).
     */
    suspend fun reload(bypassCache: Boolean = false): Navigation

    /**
     * Clicks the element [ref] points at — a link, button, checkbox, radio, or
     * any element markanywhere marked actionable (`role=button`, etc.). Suspends
     * until the resulting page settles, so a [dump] issued immediately afterwards
     * reflects the effect.
     *
     * Always returns the tab's settled [Navigation]: a click may load a new
     * document, change location within a single-page app, or only mutate the DOM
     * in place, and the caller tells which from the result — compare the returned
     * [Navigation.url] to the one it held (unchanged ⇒ no navigation, just an
     * in-place mutation) and read [Navigation.type] to distinguish a full
     * document load from a same-document change. Either way the page content has
     * likely changed and any references read before the click are stale, so it
     * must be re-[dump]ed.
     *
     * @return the tab's current [Navigation] once the click settles.
     * @throws UmweltException with:
     *   - [UmweltError.ReferenceNotFound] if [ref] is unknown or stale.
     *   - [UmweltError.NavigationFailed] if the click triggered a document
     *     navigation that loaded no document at all.
     */
    suspend fun click(ref: String): Navigation

    /**
     * Sets the value of a text input or `<textarea>` [ref] to [text], firing the
     * `input`/`change` events a real keypress would.
     *
     * @param replace when `true` (the default) the field is cleared first, so it
     *   ends up holding exactly [text]; when `false` [text] is inserted at the
     *   caret — incremental typing.
     * @throws UmweltException with:
     *   - [UmweltError.ReferenceNotFound] if [ref] is unknown or stale.
     *   - [UmweltError.ReferenceNotEditable] if [ref] is not a text-editable control.
     */
    suspend fun type(ref: String, text: String, replace: Boolean = true)

    /**
     * Selects [option] in the `<select>` [ref] points at, matching first by the
     * option's visible label (the text the agent reads in the Markdown), then by
     * its underlying `value`, and fires `change`.
     *
     * @param ref the reference pointing at a page element.
     * @param option the option to set.
     * @throws UmweltException with:
     *   - [UmweltError.ReferenceNotFound] if [ref] is unknown or stale.
     *   - [UmweltError.ReferenceNotSelectable] if [ref] is not a `<select>`.
     *   - [UmweltError.OptionNotFound] if no option matches [option].
     */
    suspend fun select(ref: String, option: String)

    /**
     * Streams the current page as the [SemanticEvent]s markanywhere derived from
     * its DOM — the structured, agent-oriented view of the page (headings, text,
     * links, form controls, …) that [dumpAsMarkdown] is rendered from. The
     * actionable elements carry the `ref`s that [click], [type], and [select]
     * accept.
     *
     * The snapshot is taken when collection begins, so each collection reflects
     * the page as it is at that moment; any mutating call ([goTo], [click], …)
     * since the last dump makes earlier `ref`s stale.
     *
     * @see dumpAsMarkdown
     * @return the flow of [SemanticEvent]s
     */
    fun dump(): Flow<SemanticEvent>

    /**
     * The page [dump] rendered as the Markdown an agent reads. The flow streams
     * the document incrementally, in the arbitrary chunks the renderer emits
     * (not line- or block-aligned), so a consumer wanting the whole page joins
     * them.
     *
     * The Markdown is rendered where the page lives — **server-side**, never in
     * the caller. A local driver runs the markanywhere HTML→Markdown pipeline
     * over its own [dump] events; a remote proxy fetches the Markdown the server
     * already rendered rather than re-running that pipeline client-side. Either
     * way the heavy transform stays off the consumer.
     */
    fun dumpAsMarkdown(): Flow<String>

    /**
     * Captures a raster screenshot of the tab and returns its encoded bytes.
     *
     * @param format the image encoding ([ScreenshotFormat.PNG] by default).
     * @param quality lossy-encoder quality (`0`–`100`) for [ScreenshotFormat.JPEG]
     *   and [ScreenshotFormat.WEBP]; **ignored for [ScreenshotFormat.PNG]**.
     *   `null` (the default) lets the encoder pick.
     * @param captureBeyondViewport when `true`, captures the full scrollable page;
     *   when `false` (the default) only the currently visible viewport.
     * @return the image bytes encoded in the requested [format].
     */
    suspend fun screenshot(
        format: ScreenshotFormat = ScreenshotFormat.PNG,
        quality: Int? = null, // ignored for PNG
        captureBeyondViewport: Boolean = false
    ): ByteArray

    /**
     * A live video feed of the tab as a [Flow] of JPEG-encoded frames, one per
     * emission (an MJPEG-style screencast). Unlike the mutating calls it does not
     * serialize with navigation, so the tab can be watched while it is being
     * driven.
     *
     * The feed is flow-controlled by the collector's backpressure — a slow
     * consumer throttles the capture rate rather than buffering frames. Collecting
     * starts the screencast and cancelling the collection stops it.
     */
    fun stream(): Flow<ByteArray>

    /**
     * Closes the session and its underlying tab, tearing down any running
     * [stream] and removing the session from [UmweltClient.listSessions]. The
     * session must not be used again afterwards.
     */
    suspend fun close()

}

/** The image encoding requested from [UmweltSession.screenshot]. */
enum class ScreenshotFormat {
    /** Lossless PNG; the `quality` parameter does not apply. */
    PNG,
    /** Lossy JPEG; honours the `quality` parameter. */
    JPEG,
    /** WebP; honours the `quality` parameter. */
    WEBP
}

/**
 * The settled navigation state of a [UmweltSession]'s tab — the signals a
 * browser's chrome shows once a page has loaded: the address bar ([url],
 * [title]), the response ([status]), and the Back/Forward affordances
 * ([canGoBack], [canGoForward]).
 *
 * It is returned by every navigating operation ([UmweltSession.goTo],
 * [UmweltSession.reload], [UmweltSession.back], [UmweltSession.forward], a
 * navigating [UmweltSession.click]) and queryable at any time via
 * [UmweltSession.navigation], so one type drives an entire browser UI — or an
 * agent's model of where it is.
 *
 * Because the session is the sole driver of its tab, it records the outcome of
 * every fetch it performs and owns its own history model; the values here come
 * from that model rather than a best-effort read of the live browser. That is
 * why [status] is always known — for a [back][UmweltSession.back]/
 * [forward][UmweltSession.forward] entry restored from cache it is the status
 * observed when that entry was first loaded.
 *
 * A [Navigation] is only ever produced for a navigation that yielded a document;
 * one that yielded none (DNS failure, connection refused, …) throws
 * [UmweltException] instead, so a [status] of `404`/`500` here always means "an
 * error *page* loaded", never "the navigation failed".
 *
 * @property url the document's final URL once the navigation settled, after any
 *   redirects — may differ from the URL passed to [UmweltSession.goTo].
 * @property status the main document's HTTP status code (e.g. `200`, `404`,
 *   `500`). Informational, not a failure: the page is loaded and
 *   [UmweltSession.dump]-able whatever the status.
 *   For a [WITHIN_DOCUMENT][Type.WITHIN_DOCUMENT]
 *   location (no fetch of its own) it is the status of the document it shares.
 * @property title the document's title (`<title>`), or the empty string when the
 *   document declares none — never `null` (a browser reports an absent title as
 *   `""`).
 * @property type how this location was reached — a full [document][Type.DOCUMENT]
 *   load or a [same-document][Type.WITHIN_DOCUMENT] change. Recorded per
 *   history entry, so [back][UmweltSession.back]/[forward][UmweltSession.forward]
 *   replay it.
 * @property canGoBack whether the tab can navigate [back][UmweltSession.back] —
 *   a previous history entry exists.
 * @property canGoForward whether the tab can navigate
 *   [forward][UmweltSession.forward] — a later history entry exists.
 */
@Serializable
data class Navigation(
    val url: String,
    val status: Int,
    val title: String,
    val type: Type,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
) {

    /**
     * How a [Navigation] location was reached — whether the browser loaded a new
     * document or merely changed location within the current one.
     */
    enum class Type {

        /**
         * A full main-document load over the network: a fresh HTTP request produced
         * the page, so [Navigation.status] is that response's status. The normal
         * result of [UmweltSession.goTo] and [UmweltSession.reload], and of a link
         * [click][UmweltSession.click] to a new page.
         */
        DOCUMENT,

        /**
         * A same-document location change with no document fetch of its own — a
         * single-page app updating the URL via the History API
         * (`pushState`/`replaceState`) or a fragment (`#…`) change. No new HTTP
         * response is involved, so [Navigation.status] is inherited from the document
         * this location shares.
         */
        WITHIN_DOCUMENT

    }


}
