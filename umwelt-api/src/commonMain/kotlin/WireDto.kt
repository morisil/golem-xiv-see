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

import kotlinx.serialization.Serializable

/*
 * The JSON request/response bodies exchanged over the umwelt HTTP API. They are
 * the wire encoding shared by the server routes that produce them and the remote
 * proxy ([UmweltClient]/[UmweltSession] implementation) that consumes them; unlike
 * [Navigation] they do not surface in the [UmweltClient]/[UmweltSession] contract
 * itself — the remote client resolves a [SessionId] into a live [UmweltSession],
 * and turns a method's arguments into the matching request body.
 */

/** The `{ "id": … }` payload of a created, listed, or resolved session. */
@Serializable
data class SessionId(val id: String)

/**
 * The `POST /sessions` body — the browser profile to open the session in (one of
 * the names from `GET /profiles`), or `null`/absent for an ephemeral session.
 */
@Serializable
data class CreateSession(val profile: String? = null)

/** The `PUT …/location` body — the URL to navigate the session's tab to. */
@Serializable
data class Location(val url: String)

/** The `POST …/click` body — the markanywhere ref of the element to click. */
@Serializable
data class Click(val ref: String)

/**
 * The `POST …/type` body — the [ref] of the target field, the [text] to enter,
 * and whether to [replace] the existing value (clear first) or append to it.
 */
@Serializable
data class Type(
    val ref: String,
    val text: String,
    val replace: Boolean = true,
)

/** The `POST …/select` body — the `<select>` [ref] and the [option] to choose. */
@Serializable
data class Select(val ref: String, val option: String)