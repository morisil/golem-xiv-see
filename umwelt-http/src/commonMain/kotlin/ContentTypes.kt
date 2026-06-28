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

package com.xemantic.umwelt.http

// additional content types not defined by ktor

import io.ktor.http.ContentType

/**
 * `text/markdown` — the content type of every Markdown page dump this server
 * serves. Defined as an extension on [ContentType.Text].
 */
val ContentType.Text.Markdown: ContentType get() =
    ContentType("text", "markdown")

/**
 * `application/x-ndjson` — newline-delimited JSON, the wire format for the
 * `SemanticEvent` dump stream. Defined as an extension on [ContentType.Application].
 */
val ContentType.Application.NDJSON: ContentType get() =
    ContentType("application", "x-ndjson")

/**
 * `multipart/x-mixed-replace` — the content type of the MJPEG tab stream. Defined
 * as an extension on [ContentType.MultiPart].
 */
val ContentType.MultiPart.MixedReplace: ContentType get() =
    ContentType("multipart", "x-mixed-replace")
