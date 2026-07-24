/*
 * Golem XIV - Autonomous metacognitive AI system with semantic memory and self-directed research
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

package com.xemantic.umwelt.web.common.dev

import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.browser.window

fun attachXemanticDebugObject() {
    val xemantic = js("{}")
    xemantic.setLogLevel = { level: String ->
        KotlinLoggingConfiguration.direct.logLevel = Level.valueOf(
            level.uppercase()
        )
    }
    xemantic.getLogLevel = {
        KotlinLoggingConfiguration.direct.toString()
    }
    window.asDynamic().xemantic = xemantic
}
