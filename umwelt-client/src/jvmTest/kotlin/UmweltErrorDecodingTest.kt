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

import com.xemantic.kotlin.test.be
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import com.xemantic.umwelt.api.UmweltError
import com.xemantic.umwelt.api.UmweltException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

private const val UNKNOWN_ID = "00000000-0000-0000-0000-0000000000ff"

class UmweltErrorDecodingTest {

    @Test
    fun `should revive serialized UmweltError into typed exception`() = testApplication {
        // given a server replying with the polymorphic UmweltError body
        routing {
            get("/sessions/{id}") {
                call.respondText(
                    Json.encodeToString<UmweltError>(UmweltError.SessionNotFound(UNKNOWN_ID)),
                    ContentType.Application.Json,
                    HttpStatusCode.NotFound
                )
            }
        }
        val httpClient = createClient {
            expectSuccess = true
            installUmweltErrorDecoding()
        }
        val client = RemoteUmweltClient(httpClient)

        // when
        val exception = assertFailsWith<UmweltException> {
            client.getSession(UNKNOWN_ID)
        }

        // then — getSession surfaces the typed UmweltException
        exception should {
            have(message == "no session with id '00000000-0000-0000-0000-0000000000ff'")
            error should {
                be<UmweltError.SessionNotFound>()
                have(id == UNKNOWN_ID)
            }
        }
    }

    @Test
    fun `should leave non-UmweltError responses as transport exception`() = testApplication {
        // given a generic, non-UmweltError error body (e.g. an opaque proxy error)
        routing {
            get("/boom") {
                call.respondText(
                    """{"error":"upstream is down"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.BadGateway
                )
            }
        }
        val httpClient = createClient {
            expectSuccess = true
            installUmweltErrorDecoding()
        }

        // when
        val error = assertFailsWith<ResponseException> {
            httpClient.get("/boom")
        }

        // then — the original transport exception propagates, not an UmweltException
        error should {
            have(message == "Server error(GET http://localhost/boom: 502 Bad Gateway. Text: \"{\"error\":\"upstream is down\"}\"")
        }

    }

}
