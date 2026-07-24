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

package com.xemantic.umwelt.web.common

import com.xemantic.kotlin.js.dom.NodeBuilder
import org.w3c.dom.GlobalEventHandlers
import org.w3c.dom.HTMLElement

/**
 * Binds Enter and Space to [action], the standard keyboard activation
 * expected of non-button elements carrying a click handler.
 */
fun GlobalEventHandlers.onKeyboardActivation(
    action: () -> Unit
) {
    onkeydown = { event ->
        if (event.key == "Enter" || event.key == " ") {
            event.preventDefault()
            action()
        }
    }
}

fun NodeBuilder<out HTMLElement>.onKeyboardActivation(
    action: () -> Unit
) {
    node.onKeyboardActivation(action)
}
