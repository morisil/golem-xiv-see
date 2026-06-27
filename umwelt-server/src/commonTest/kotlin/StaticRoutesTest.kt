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
import com.xemantic.kotlin.test.sameAs
import com.xemantic.kotlin.test.should
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test

class StaticRoutesTest {

    @Test
    fun `should serve index html on root path`() = testApplication {
        // given
        application {
            staticRoutes()
        }

        // when
        val response = client.get("/")
        val body = response.bodyAsText()

        // then
        response should {
            have(status == HttpStatusCode.OK)
            have(contentType()!!.match(ContentType.Text.Html))
        }
        body sameAs staticResources["index.html"]!!
    }

    @Test
    fun `should serve the compiled js bundle`() = testApplication {
        // given
        application {
            staticRoutes()
        }

        // when
        val response = client.get("/umwelt-web.js")
        val body = response.bodyAsText()

        // then
        response should {
            have(status == HttpStatusCode.OK)
            // pinned to text/javascript (RFC 9239) identically on JVM and native
            have(contentType()!!.match(ContentType.Text.JavaScript))
        }
        body sameAs staticResources["umwelt-web.js"]!!
    }

}
