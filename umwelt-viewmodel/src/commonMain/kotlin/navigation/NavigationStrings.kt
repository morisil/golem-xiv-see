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

package com.xemantic.umwelt.viewmodel.navigation

@Suppress("PropertyName")
data class NavigationStrings(
    val About: String,
    val Browse: String,
    val Sessions: String,
    val `View sessions`: String,
    val Skill: String,
    val `View agent skill`: String,

    val Settings: String,
    val `Server settings`: String,

    val `Main navigation`: String,
    val `Main menu`: String,

    val `Light mode`: String,
    val `Dark mode`: String,
    val `Theme switcher`: String,

    val `Toggle sidebar menu`: String,
    val Error: String,
)
