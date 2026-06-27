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

package com.xemantic.umwelt.api

import com.xemantic.kotlin.test.be
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.sameAsJson
import com.xemantic.kotlin.test.should
import kotlinx.serialization.json.Json
import kotlin.test.Test

class ErrorsSerializationTest {

    @Test
    fun `should serialize InvalidSessionId error`() {
        // given
        val error = UmweltError.InvalidSessionId("oops")

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "InvalidSessionId",
              "idText": "oops",
              "message": "invalid session id 'oops'"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize InvalidSessionId error`() {
        // given
        val json = """
            {
              "type": "InvalidSessionId",
              "idText": "oops",
              "message": "invalid session id 'oops'"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.InvalidSessionId>()
            have(idText == "oops")
            have(message == "invalid session id 'oops'")
        }
    }

    @Test
    fun `should serialize SessionNotFound error`() {
        // given
        val error = UmweltError.SessionNotFound("42")

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "SessionNotFound",
              "id": "42",
              "message": "no session with id '42'"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize SessionNotFound error`() {
        // given
        val json = """
            {
              "type": "SessionNotFound",
              "id": "42",
              "message": "no session with id '42'"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.SessionNotFound>()
            have(id == "42")
            have(message == "no session with id '42'")
        }
    }

    @Test
    fun `should serialize ReferenceNotFound error`() {
        // given
        val error = UmweltError.ReferenceNotFound("13")

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "ReferenceNotFound",
              "ref": "13",
              "message": "no actionable element with ref '13' on the current page"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize ReferenceNotFound error`() {
        // given
        val json = """
            {
              "type": "ReferenceNotFound",
              "ref": "13",
              "message": "no actionable element with ref '13' on the current page"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.ReferenceNotFound>()
            have(ref == "13")
            have(message == "no actionable element with ref '13' on the current page")
        }
    }

    @Test
    fun `should serialize ReferenceNotEditable error`() {
        // given
        val error = UmweltError.ReferenceNotEditable("13")

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "ReferenceNotEditable",
              "ref": "13",
              "message": "element with ref '13' is not a text-editable control"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize ReferenceNotEditable error`() {
        // given
        val json = """
            {
              "type": "ReferenceNotEditable",
              "ref": "13",
              "message": "element with ref '13' is not a text-editable control"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.ReferenceNotEditable>()
            have(ref == "13")
            have(message == "element with ref '13' is not a text-editable control")
        }
    }

    @Test
    fun `should serialize ReferenceNotSelectable error`() {
        // given
        val error = UmweltError.ReferenceNotSelectable("13")

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "ReferenceNotSelectable",
              "ref": "13",
              "message": "element with ref '13' is not a <select>"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize ReferenceNotSelectable error`() {
        // given
        val json = """
            {
              "type": "ReferenceNotSelectable",
              "ref": "13",
              "message": "element with ref '13' is not a <select>"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.ReferenceNotSelectable>()
            have(ref == "13")
            have(message == "element with ref '13' is not a <select>")
        }
    }

    @Test
    fun `should serialize OptionNotFound error`() {
        // given
        val error = UmweltError.OptionNotFound("13", "Blue")

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "OptionNotFound",
              "ref": "13",
              "option": "Blue",
              "message": "<select> '13' has no option matching 'Blue'"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize OptionNotFound error`() {
        // given
        val json = """
            {
              "type": "OptionNotFound",
              "ref": "13",
              "option": "Blue",
              "message": "<select> '13' has no option matching 'Blue'"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.OptionNotFound>()
            have(ref == "13")
            have(option == "Blue")
            have(message == "<select> '13' has no option matching 'Blue'")
        }
    }

    @Test
    fun `should serialize NavigationFailed error`() {
        // given
        val error = UmweltError.NavigationFailed(
            "https://nope.example", "net::ERR_NAME_NOT_RESOLVED"
        )

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "NavigationFailed",
              "url": "https://nope.example",
              "reason": "net::ERR_NAME_NOT_RESOLVED",
              "message": "could not load 'https://nope.example': net::ERR_NAME_NOT_RESOLVED"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize NavigationFailed error`() {
        // given
        val json = """
            {
              "type": "NavigationFailed",
              "url": "https://nope.example",
              "reason": "net::ERR_NAME_NOT_RESOLVED",
              "message": "could not load 'https://nope.example': net::ERR_NAME_NOT_RESOLVED"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.NavigationFailed>()
            have(url == "https://nope.example")
            have(reason == "net::ERR_NAME_NOT_RESOLVED")
            have(message == "could not load 'https://nope.example': net::ERR_NAME_NOT_RESOLVED")
        }
    }

    @Test
    fun `should serialize NoCurrentPage error`() {
        // given
        val error = UmweltError.NoCurrentPage()

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "NoCurrentPage",
              "message": "the session has not navigated to any page yet"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize NoCurrentPage error`() {
        // given
        val json = """
            {
              "type": "NoCurrentPage",
              "message": "the session has not navigated to any page yet"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.NoCurrentPage>()
            have(message == "the session has not navigated to any page yet")
        }
    }

    @Test
    fun `should serialize CannotGoBack error`() {
        // given
        val error = UmweltError.CannotGoBack()

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "CannotGoBack",
              "message": "already at the first history entry; cannot go back"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize CannotGoBack error`() {
        // given
        val json = """
            {
              "type": "CannotGoBack",
              "message": "already at the first history entry; cannot go back"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.CannotGoBack>()
            have(message == "already at the first history entry; cannot go back")
        }
    }

    @Test
    fun `should serialize CannotGoForward error`() {
        // given
        val error = UmweltError.CannotGoForward()

        // when
        val json = Json.encodeToString<UmweltError>(error)

        // then
        json sameAsJson """
            {
              "type": "CannotGoForward",
              "message": "already at the last history entry; cannot go forward"
            }
        """.trimIndent()
    }

    @Test
    fun `should deserialize CannotGoForward error`() {
        // given
        val json = """
            {
              "type": "CannotGoForward",
              "message": "already at the last history entry; cannot go forward"
            }
        """.trimIndent()

        // when
        val error = Json.decodeFromString<UmweltError>(json)

        // then
        error should {
            be<UmweltError.CannotGoForward>()
            have(message == "already at the last history entry; cannot go forward")
        }
    }

}
