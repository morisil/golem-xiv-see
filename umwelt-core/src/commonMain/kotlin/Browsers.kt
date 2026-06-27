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

package com.xemantic.umwelt.core

import dev.kdriver.core.tab.Tab

/**
 * A persistent, dir-backed browsing identity — its cookies, local storage and
 * login state survive across tabs and restarts (Chrome's notion of a "profile").
 * Opening a tab *without* a profile yields an ephemeral incognito context
 * instead: it starts with no cookies and persists nothing.
 */
data class Profile(
    val name: String,
)

/**
 * Vends browser tabs, optionally bound to a persistent [Profile].
 *
 * This is the seam between the rest of the app and *where* Chrome actually runs:
 * the local implementation ([LocalBrowsers]) drives a Chrome on this machine via
 * kdriver/CDP, but a remote-CDP backend (steel.dev, Browserbase, …) can implement
 * the same contract — kdriver can drive a remote endpoint too, so the returned
 * [Tab] stays the common currency either way.
 */
interface Browsers {

    /** The persistent [Profile]s a tab may be opened within. */
    suspend fun profiles(): List<Profile>

    /**
     * Opens a fresh tab.
     *
     * @param profile the name of a persistent profile to open the tab within, or
     *   `null` (the default) to open an ephemeral **incognito** tab — no cookies
     *   carried in, nothing persisted.
     * @throws IllegalArgumentException if [profile] is non-`null` but is not one
     *   of [profiles].
     */
    suspend fun newTab(profile: String? = null): Tab

    /** Stops every browser this manager started and releases its resources. */
    suspend fun close()

}
