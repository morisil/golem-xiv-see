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

package com.xemantic.umwelt.client

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.umwelt.api.Navigation
import com.xemantic.umwelt.api.ScreenshotFormat
import com.xemantic.umwelt.api.UmweltClient
import com.xemantic.umwelt.api.UmweltSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** Newline-delimited JSON — the wire format for the `SemanticEvent` dump stream. */
private val NDJSON = ContentType("application", "x-ndjson")

/**
 * A [UmweltClient] talking to the umwelt server over a plain JSON/REST API.
 *
 * The wire contract this client consumes is owned and documented by the server
 * module (the side that provides it) — see `browserApi` there for the routes,
 * bodies and status codes. The per-method comments below only note the
 * verb/idempotency rationale for each call, not the full contract.
 *
 * The injected [client] is expected to be configured with a base URL
 * (`defaultRequest { url(serverOrigin) }`), `ContentNegotiation` using kotlinx
 * JSON (so the `@Serializable` bodies below transcode), `expectSuccess = true`
 * (so non-2xx responses surface as exceptions rather than silent no-ops), and
 * [installUmweltErrorDecoding] (so those exceptions arrive as the typed
 * [com.xemantic.umwelt.api.UmweltException]s the [UmweltClient] contract
 * documents — `SessionNotFound`, `ReferenceNotFound`, `NavigationFailed`, …).
 */
class RemoteUmweltClient(
    private val client: HttpClient
) : UmweltClient {

    override fun listProfiles(): Flow<String> = flow {
        emitAll(
            client.get("/profiles")
                .body<List<String>>()
                .asFlow()
        )
    }

    override suspend fun newSession(
        profile: String?
    ): UmweltSession {
        val session = client.post("/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateSession(profile))
        }.body<SessionId>()
        return RemoteUmweltSession(session.id)
    }

    override fun listSessions(): Flow<UmweltSession> = flow {
        client.get("/sessions")
            .body<List<SessionId>>()
            .forEach {
                emit(RemoteUmweltSession(it.id))
            }
    }

    // GET: resolving a session is a read; the server echoes its id once it
    // confirms the session is open, or replies 400/404 (revived into an
    // InvalidSessionId/SessionNotFound UmweltException by the error decoder).
    override suspend fun getSession(
        id: String
    ): UmweltSession = RemoteUmweltSession(
        client.get("/sessions/$id").body<SessionId>().id
    )

    private inner class RemoteUmweltSession(
        override val id: String
    ) : UmweltSession {

        override suspend fun goTo(url: String): Navigation =
            // PUT: setting the tab's location is idempotent; the server replies
            // only after the navigation has settled (waitUntilLoaded) with the
            // full Navigation. A navigation that loaded no document at all
            // surfaces as an UmweltException (NavigationFailed).
            client.put("/sessions/$id/location") {
                contentType(ContentType.Application.Json)
                setBody(Location(url))
            }.body()

        override suspend fun back(): Navigation = navigate("back")

        override suspend fun forward(): Navigation = navigate("forward")

        // POST: history navigation mutates the tab (and the dedicated verb keeps
        // it clear of the GET reads); the reply is the full Navigation the tab
        // moved to, or a 409 UmweltException (CannotGoBack/CannotGoForward) at a
        // history boundary.
        private suspend fun navigate(direction: String): Navigation =
            client.post("/sessions/$id/$direction").body()

        // GET: a pure read of the current navigation state; a session that has
        // not navigated yet surfaces as a NoCurrentPage UmweltException.
        override suspend fun navigation(): Navigation =
            client.get("/sessions/$id/navigation").body()

        // POST: reloading mutates the tab; like goTo it returns the reloaded
        // document's Navigation, or — when no document loaded — surfaces as an
        // UmweltException (NavigationFailed). bypassCache rides a query param.
        override suspend fun reload(
            bypassCache: Boolean
        ): Navigation = client.post("/sessions/$id/reload") {
            parameter("bypassCache", bypassCache)
        }.body()

        // POST: ref-based interactions mutate the page; click returns the
        // Navigation the tab settled on (compare its url to detect whether it
        // navigated). type/select reply 204. A 4xx (unknown ref, wrong element
        // type, missing option) surfaces as the matching UmweltException — the
        // server's UmweltError body, revived by installUmweltErrorDecoding.
        override suspend fun click(
            ref: String
        ): Navigation = client.post("/sessions/$id/click") {
            contentType(ContentType.Application.Json)
            setBody(Click(ref))
        }.body()

        override suspend fun type(
            ref: String,
            text: String,
            replace: Boolean
        ) {
            client.post("/sessions/$id/type") {
                contentType(ContentType.Application.Json)
                setBody(Type(ref, text, replace))
            }
        }

        override suspend fun select(
            ref: String,
            option: String
        ) {
            client.post("/sessions/$id/select") {
                contentType(ContentType.Application.Json)
                setBody(Select(ref, option))
            }
        }

        // the page is streamed as NDJSON — one SemanticEvent JSON object per
        // line. Decode each line off the raw channel as it arrives rather
        // than buffering the whole body, so events surface incrementally and
        // closing the collector cancels the request.
        override fun dump(): Flow<SemanticEvent> = client.streamLines(
            "/sessions/$id/events"
        ) {
            accept(NDJSON)
        }.map(SemanticEvent::fromJson)

        // the server renders the Markdown (HTML->Markdown pipeline runs there, on
        // the live page); this proxy just relays the rendered text/markdown body
        // rather than re-running the pipeline in the browser. Streamed line by
        // line off the raw channel as it arrives (like dump()), so the document
        // surfaces incrementally and closing the collector cancels the request.
        // readLine() strips the terminator, so re-add a "\n" to keep the joined
        // emissions a faithful document (the chunking itself is arbitrary).
        override fun dumpAsMarkdown(): Flow<String> = client.streamLines(
            "/sessions/$id/markdown"
        ).map { "$it\n" }

        override suspend fun screenshot(
            format: ScreenshotFormat,
            quality: Int?,
            captureBeyondViewport: Boolean
        ): ByteArray = client.get("/sessions/$id/screenshot") {
            parameter("format", format.name.lowercase())
            if (quality != null) parameter("quality", quality)
            parameter("captureBeyondViewport", captureBeyondViewport)
        }.body<ByteArray>()

        override fun stream(): Flow<ByteArray> = flow {
            // a long-lived multipart/x-mixed-replace (MJPEG) response: stream it
            // frame-by-frame off the raw channel rather than buffering the body,
            // so each JPEG is emitted live and the screencast is flow-controlled
            // by this collector's backpressure. Closing the collector cancels the
            // request, which stops the server-side screencast.
            client.prepareGet("/sessions/$id/stream").execute { response ->
                val channel = response.bodyAsChannel()
                while (true) {
                    val frame = channel.readMjpegFrame() ?: break
                    emit(frame)
                }
            }
        }

        override suspend fun close() {
            client.delete("/sessions/$id")
        }

    }

}

/** The `{ "id": … }` payload of a created/listed session. */
@Serializable
private data class SessionId(val id: String)

/**
 * The `POST /sessions` body: the persistent profile to open the session in, or
 * `null` (the default) for an ephemeral incognito session.
 */
@Serializable
private data class CreateSession(
    val profile: String? = null
)

/** The `PUT …/location` body — the URL to navigate the session's tab to. */
@Serializable
private data class Location(val url: String)

/** The `POST …/click` body — the ref of the element to click. */
@Serializable
private data class Click(val ref: String)

/** The `POST …/type` body — the field ref, the text, and replace-vs-append. */
@Serializable
private data class Type(
    val ref: String,
    val text: String,
    val replace: Boolean = true,
)

/** The `POST …/select` body — the `<select>` ref and the option to choose. */
@Serializable
private data class Select(val ref: String, val option: String)
