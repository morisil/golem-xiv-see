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

import com.xemantic.umwelt.api.UmweltError
import com.xemantic.umwelt.api.UmweltException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException

/**
 * Maps a thrown [UmweltException] to its HTTP status (via [httpStatus]) plus the
 * serialized [UmweltError] body, and any other failure to `502`. Installed once
 * for the whole application, so every route — not just the ones that wrap their
 * body — reports an invalid session id as `400`, a missing session as `404`, the
 * ref-based failures as `404`/`422`, and so on, rather than surfacing as `500`.
 *
 * The error body is the polymorphic [UmweltError] itself (discriminated on
 * `type`), so a Kotlin client revives it into the matching [UmweltException] and
 * any other client still reads the human-readable `message` — see
 * `RemoteUmweltClient`.
 */
fun Application.statusPagesModule() {

    val logger = KotlinLogging.logger(
        "com.xemantic.umwelt.server.errors"
    )

    install(StatusPages) {
        exception<UmweltException> { call, cause ->
            call.respond(cause.error.httpStatus(), cause.error)
        }
        exception<Throwable> { call, cause ->
            // a client disconnect mid-stream cancels the call; let it propagate
            // rather than logging it as a failure or trying to respond to a
            // connection that is already gone
            if (cause is CancellationException) throw cause
            logger.error(cause) { "Failed to handle request" }
            call.respond(
                HttpStatusCode.BadGateway,
                mapOf("error" to (cause.message ?: "failed to handle request"))
            )
        }
    }
}

/**
 * Maps each [UmweltError] to the HTTP status that best describes it. The `when`
 * is exhaustive over the sealed hierarchy, so a new error type fails to compile
 * here until it is given a status — no silent fall-through to `500`.
 */
private fun UmweltError.httpStatus(): HttpStatusCode = when (this) {
    is InvalidSessionId -> HttpStatusCode.BadRequest
    is SessionNotFound -> HttpStatusCode.NotFound
    is ReferenceNotFound -> HttpStatusCode.NotFound
    is ReferenceNotEditable -> HttpStatusCode.UnprocessableEntity
    is ReferenceNotSelectable -> HttpStatusCode.UnprocessableEntity
    is OptionNotFound -> HttpStatusCode.UnprocessableEntity
    is NavigationFailed -> HttpStatusCode.BadGateway
    // operation invalid in the tab's current navigation state
    is NoCurrentPage -> HttpStatusCode.Conflict
    is CannotGoBack -> HttpStatusCode.Conflict
    is CannotGoForward -> HttpStatusCode.Conflict
}
