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

package com.xemantic.umwelt.web.common.events

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import org.w3c.dom.PopStateEvent
import org.w3c.dom.Window
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.UIEvent

// TODO to be moved to xemantic-kotlin-js
/**
 * Returns a cold [Flow] of [type] events dispatched to this target.
 *
 * The flow is cold and per-collector: each collector installs its own DOM listener,
 * removed again when the collecting coroutine is cancelled.
 * Share the flow with `shareIn` if fan-out to many collectors is needed.
 *
 * The default [capacity] is [Channel.CONFLATED]: when events arrive faster than
 * the collector processes them, intermediate events are dropped and only the latest
 * one is delivered — the right policy for UI state like resize or scroll.
 * Pass [Channel.UNLIMITED] or a positive capacity when every event matters.
 *
 * Events are delivered to the collector asynchronously, after the browser has finished
 * dispatching them, so calling [Event.preventDefault] on a collected event has no effect
 * and APIs requiring transient user activation cannot be called from the collector —
 * attach a listener directly for such cases.
 *
 * The event type [T] is not verified at runtime — it must match what the browser
 * dispatches for [type]. Prefer the typed wrappers like [resizes].
 *
 * @param type the DOM event type to listen for.
 * @param capacity the buffer capacity between the DOM listener and the collector.
 * @param capture registers a capturing listener.
 * @param passive marks the listener passive, allowing the browser to scroll without
 *   waiting for it; when `null` the browser's per-event-type default applies.
 */
fun <T : Event> EventTarget.eventFlow(
    type: String,
    capacity: Int = Channel.CONFLATED,
    capture: Boolean = false,
    passive: Boolean? = null
): Flow<T> = callbackFlow {
    val listener: (Event) -> Unit = {
        trySend(it.unsafeCast<T>())
    }
    val options: dynamic = js("{}")
    options.capture = capture
    if (passive != null) options.passive = passive
    addEventListener(type, callback = listener, options = options)
    awaitClose {
        removeEventListener(type, callback = listener, options = options)
    }
}.buffer(capacity)

fun Window.resizes(): Flow<UIEvent> = eventFlow("resize")

fun Window.popStates(): Flow<PopStateEvent> = eventFlow("popstate")
