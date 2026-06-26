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

package com.xemantic.umwelt.viewmodel

import com.xemantic.markanywhere.SemanticEvent
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Newline-delimited JSON — the wire format for the `SemanticEvent` dump stream. */
private val NDJSON = ContentType("application", "x-ndjson")

/**
 * A [UmweltClient] talking to the umwelt server over a plain JSON/REST API.
 *
 * The contract it consumes (designed for this client):
 *
 * | verb + path                      | body / query                         | response                                   |
 * |----------------------------------|--------------------------------------|--------------------------------------------|
 * | `POST /sessions`                 | `{ "private": Boolean }`             | `201` `{ "id": "<uuid>" }`                 |
 * | `GET /sessions`                  | —                                    | `200` `[ { "id": "<uuid>" }, … ]`          |
 * | `PUT /sessions/{id}/location`    | `{ "url": "https://…" }`            | `204` once navigated and loaded            |
 * | `GET /sessions/{id}/events`      | —                                    | `200 application/x-ndjson` — one `SemanticEvent` JSON per line |
 * | `GET /sessions/{id}/screenshot`  | `?format=&quality=&captureBeyondViewport=` | `200` `image/<fmt>` raw bytes        |
 * | `GET /sessions/{id}/stream`      | —                                    | `200 multipart/x-mixed-replace` MJPEG      |
 * | `DELETE /sessions/{id}`          | —                                    | `204`                                      |
 *
 * The injected [client] is expected to be configured with a base URL
 * (`defaultRequest { url(serverOrigin) }`), `ContentNegotiation` using kotlinx
 * JSON (so the `@Serializable` bodies below transcode), and `expectSuccess = true`
 * (so non-2xx responses surface as exceptions rather than silent no-ops).
 */
class RemoteUmweltClient(
    private val client: HttpClient
) : UmweltClient {

    override suspend fun newSession(private: Boolean): UmweltSession {
        val session = client.post("/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateSession(private))
        }.body<SessionId>()
        return RemoteUmweltSession(session.id)
    }

    override fun listSessions(): Flow<UmweltSession> = flow {
        client.get("/sessions")
            .body<List<SessionId>>()
            .forEach { emit(RemoteUmweltSession(it.id)) }
    }

    private inner class RemoteUmweltSession(
        private val id: String
    ) : UmweltSession {

        override suspend fun open(url: String) {
            // PUT: setting the tab's location is idempotent; the server replies
            // 204 only after the navigation has settled (waitUntilLoaded).
            client.put("/sessions/$id/location") {
                contentType(ContentType.Application.Json)
                setBody(Location(url))
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

/** The `POST /sessions` body selecting an isolated (incognito) session. */
@Serializable
private data class CreateSession(
    @SerialName("private") val isPrivate: Boolean
)

/** The `PUT …/location` body — the URL to navigate the session's tab to. */
@Serializable
private data class Location(val url: String)
