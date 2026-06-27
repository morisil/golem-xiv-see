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

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.be
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import kotlin.test.Test

class ErrorsToExceptionTest {

    @Test
    fun `should wrap error into exception carrying its message`() {
        // given
        val error = UmweltError.SessionNotFound("42")

        // when
        val exception = error.toException()

        // then
        exception should {
            be<UmweltException>()
            have(this.error === error)
            have(message == "no session with id '42'")
            have(this.cause == null)
        }
    }

    @Test
    fun `should preserve the cause when wrapping error into exception`() {
        // given
        val error = UmweltError.NavigationFailed(
            "https://nope.example", "net::ERR_NAME_NOT_RESOLVED"
        )
        val cause = RuntimeException("boom")

        // when
        val exception = error.toException(cause)

        // then
        exception should {
            have(this.error === error)
            have(message == "could not load 'https://nope.example': net::ERR_NAME_NOT_RESOLVED")
            have(this.cause === cause)
        }
    }

    @Test
    fun `should default to null cause`() {
        // given
        val error = UmweltError.NoCurrentPage()

        // when
        val exception = error.toException()

        // then
        assert(exception.cause == null)
    }

}
