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

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAs
import com.xemantic.umwelt.api.Navigation
import com.xemantic.umwelt.client.RemoteUmweltClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.toList
import kotlin.test.Test

class UmweltRemoteClientTest {

    @Test
    fun `should create a session navigate to a page and dump it as Markdown`() = testApplication {
        // given
        umweltTestApp()
        val client = RemoteUmweltClient(client)

        // when - open a session
        val session = client.newSession()
        val navigation = session.goTo("https://example.com/")

        // then
        assert(navigation == Navigation(
            url = "https://example.com/", // <- / added, canonical URL
            status = HttpStatusCode.OK.value,
            title = "Example Domain",
            type = Navigation.Type.DOCUMENT,
            canGoBack = false,
            canGoForward = false
        ))

        // when - dump the page through the DOM->Markdown pipeline
        val builder = StringBuilder()
        session.dumpAsMarkdown().collect { builder.append(it) }
        val markdown = builder.toString()

        // then
        markdown sameAs /* language=markdown */ """
            ---
            lang: en
            title: Example Domain
            ---
            
            # Example Domain
            
            This domain is for use in documentation examples without needing permission. Avoid use in operations.
            
            [Learn more](ref:1:https://iana.org/domains/example)
            
        """.trimIndent()

        // when - close the session and verify it is gone
        session.close()

        // post check that all the sessions are closed
        assert(client.listSessions().toList().isEmpty())
    }

}
