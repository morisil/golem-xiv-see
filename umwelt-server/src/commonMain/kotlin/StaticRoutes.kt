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

import io.ktor.http.ContentType
import io.ktor.http.defaultForFilePath
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Serves the embedded static web app.
 *
 * [staticResources] is generated at build time from the umwelt-web production
 * distribution — the page shell (`index.html`), the stylesheet (`style.css`),
 * the `umwelt-web.js` bundle and any webpack chunks — plus the server's
 * `static/` folder. The content is baked into the binary, so it is served
 * identically on JVM and native with no classpath or filesystem lookup.
 *
 * Each resource is served at its own path; `index.html` is additionally served
 * at `/`.
 */
fun Application.staticRoutes() {

    routing {

        staticResources.forEach { (path, content) ->
            val contentType = ContentType.defaultForFilePath(path)
            get("/$path") {
                call.respondText(content, contentType)
            }
            if (path == "index.html") {
                get("/") {
                    call.respondText(content, contentType)
                }
            }
        }

    }

}