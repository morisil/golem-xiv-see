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

package com.xemantic.umwelt.viewmodel.navigation

data class NavItem(
    val icon: String,
    val label: String,
    val accessibilityLabel: String,
    val target: Navigation.Target,
    val uri: String
)

fun navItems(
    strings: NavigationStrings
) = listOf(
    NavItem(
        icon = "tab",
        label = strings.Sessions,
        accessibilityLabel = strings.`View sessions`,
        target = Navigation.Target.Sessions(),
        uri = "sessions"
    ),
    NavItem(
        icon = "travel_explore",
        label = strings.Browse,
        accessibilityLabel = strings.Browse,
        target = Navigation.Target.Browse(),
        uri = "browse"
    ),
    NavItem(
        icon = "smart_toy",
        label = strings.Skill,
        accessibilityLabel = strings.`View agent skill`,
        target = Navigation.Target.Skill,
        uri = "skill"
    ),
    NavItem(
        icon = "info",
        label = strings.About,
        accessibilityLabel = strings.About,
        target = Navigation.Target.About,
        uri = "about"
    ),
    NavItem(
        icon = "settings",
        label = strings.Settings,
        accessibilityLabel = strings.`Server settings`,
        target = Navigation.Target.Settings,
        uri = "settings"
    )
)
