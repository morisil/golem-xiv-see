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

@file:OptIn(ExperimentalSerializationApi::class)

package com.xemantic.umwelt.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * The wire-serializable description of an umwelt operation failure. The server
 * sends it as a JSON payload (polymorphic on the `"type"` discriminator) and the
 * client revives it into the matching [UmweltException] via [toException].
 *
 * [message] is derived from each subtype's fields but is **also** serialized
 * (always, via [EncodeDefault]) so non-Kotlin clients get the full human-readable
 * text without reconstructing it from the discriminator and fields.
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class UmweltError {

    abstract val message: String

    @Serializable
    @SerialName("InvalidSessionId")
    class InvalidSessionId(
        val idText: String,
        @EncodeDefault
        override val message: String = "invalid session id '$idText'"
    ) : UmweltError()

    @Serializable
    @SerialName("SessionNotFound")
    class SessionNotFound(
        val id: String,
        @EncodeDefault
        override val message: String = "no session with id '$id'"
    ) : UmweltError()

    /** The [ref] is unknown on the current page — never assigned, or stale after a navigation. */
    @Serializable
    @SerialName("ReferenceNotFound")
    class ReferenceNotFound(
        val ref: String,
        @EncodeDefault
        override val message: String = "no actionable element with ref '$ref' on the current page"
    ) : UmweltError()

    /** [UmweltSession.type] target [ref] exists but is not a text-editable control. */
    @Serializable
    @SerialName("ReferenceNotEditable")
    class ReferenceNotEditable(
        val ref: String,
        @EncodeDefault
        override val message: String = "element with ref '$ref' is not a text-editable control"
    ) : UmweltError()

    /** [UmweltSession.select] target [ref] exists but is not a `<select>`. */
    @Serializable
    @SerialName("ReferenceNotSelectable")
    class ReferenceNotSelectable(
        val ref: String,
        @EncodeDefault
        override val message: String = "element with ref '$ref' is not a <select>"
    ) : UmweltError()

    /**
     * [UmweltSession.goTo] loaded no document at all — a network-level failure
     * (DNS resolution, connection refused, TLS error, timeout), as opposed to a
     * 4xx/5xx response which still yields a dumpable page and a `Navigation`
     * return. [reason] is the browser's network error text, e.g.
     * `net::ERR_NAME_NOT_RESOLVED`.
     */
    @Serializable
    @SerialName("NavigationFailed")
    class NavigationFailed(
        val url: String,
        val reason: String,
        @EncodeDefault
        override val message: String = "could not load '$url': $reason"
    ) : UmweltError()

    /** The `<select>` [ref] has no option matching the requested [option] (by label or value). */
    @Serializable
    @SerialName("OptionNotFound")
    class OptionNotFound(
        val ref: String,
        val option: String,
        @EncodeDefault
        override val message: String = "<select> '$ref' has no option matching '$option'"
    ) : UmweltError()

    /**
     * [UmweltSession.navigation] or [UmweltSession.reload] was called before the
     * session had navigated anywhere — there is no current page to report or
     * reload yet.
     */
    @Serializable
    @SerialName("NoCurrentPage")
    class NoCurrentPage(
        @EncodeDefault
        override val message: String = "the session has not navigated to any page yet"
    ) : UmweltError()

    /** [UmweltSession.back] was called while already at the first history entry. */
    @Serializable
    @SerialName("CannotGoBack")
    class CannotGoBack(
        @EncodeDefault
        override val message: String = "already at the first history entry; cannot go back"
    ) : UmweltError()

    /** [UmweltSession.forward] was called while already at the last history entry. */
    @Serializable
    @SerialName("CannotGoForward")
    class CannotGoForward(
        @EncodeDefault
        override val message: String = "already at the last history entry; cannot go forward"
    ) : UmweltError()

    fun toException(
        cause: Throwable? = null
    ): UmweltException = UmweltException(
        error = this,
        cause = cause
    )

}

class UmweltException(
    val error: UmweltError,
    cause: Throwable? = null
) : RuntimeException(error.message, cause)
