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

import com.xemantic.umwelt.api.UmweltError
import com.xemantic.umwelt.api.UmweltException
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

// lenient on purpose: an older client must still revive a known UmweltError even
// if the server adds fields to it; an unrecognised body just fails to decode and
// the original transport exception is left to propagate.
private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Installs an [HttpResponseValidator] that revives the server's serialized
 * [UmweltError] body into the matching [UmweltException], so [RemoteUmweltClient]
 * fails the same typed way a local client does — `getSession` throws
 * [UmweltError.SessionNotFound], `click` throws [UmweltError.ReferenceNotFound],
 * and so on.
 *
 * It runs **after** `expectSuccess = true` has raised its [ResponseException]:
 * the validator best-effort decodes that response's body and, if it is a
 * recognisable [UmweltError] (discriminated on `type`), rethrows it as an
 * [UmweltException] carrying the original as its cause. A body that is not an
 * [UmweltError] (an empty body, an opaque reverse-proxy error, the server's
 * generic `{ "error": … }` fallback) is left alone, so the original transport
 * exception still propagates.
 *
 * Apply it on the [io.ktor.client.HttpClient] handed to [RemoteUmweltClient],
 * alongside `expectSuccess = true` and `ContentNegotiation` (kotlinx JSON).
 */
fun HttpClientConfig<*>.installUmweltErrorDecoding() {
    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, _ ->
            val response = (cause as? ResponseException)?.response
                ?: return@handleResponseExceptionWithRequest
            val error = try {
                errorJson.decodeFromString<UmweltError>(response.bodyAsText())
            } catch (e: Exception) {
                return@handleResponseExceptionWithRequest // not an UmweltError body
            }
            throw error.toException(cause)
        }
    }
}
