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

package com.xemantic.umwelt.server

import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HealthCheckRoutesTest {

    private fun ApplicationTestBuilder.healthCheckApp() {
        application {
            serverContentNegotiation()
            healthCheckRoutes()
        }
        client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    @Test
    fun `should return healthy status with timestamp`() = testApplication {
        // given
        healthCheckApp()
        val now = Clock.System.now()

        // when
        val response = client.get("/health")
        val payload = response.body<Map<String, String>>()

        // then
        response should {
            have(status == HttpStatusCode.OK)
            have(contentType()!!.match(ContentType.Application.Json))
        }
        payload should {
            have(get("status") == "healthy")
            val timestampString = get("timestamp")
            have(timestampString != null)
            val timestamp = Instant.parse(timestampString!!)
            have(timestamp in now..(now + 10.seconds))
        }
    }

}
