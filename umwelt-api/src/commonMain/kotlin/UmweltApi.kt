/*
 * Golem XIV See - See the web the way your AI agent sees it
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

import com.xemantic.markanywhere.SemanticEvent
import kotlinx.coroutines.flow.Flow

interface UmweltClient {

    suspend fun newSession(
        private: Boolean = true
    ): UmweltSession

    fun listSessions(): Flow<UmweltSession>

}

interface UmweltSession {

    suspend fun open(url: String)

    fun dump(): Flow<SemanticEvent>

    suspend fun screenshot(
        format: ScreenshotFormat = ScreenshotFormat.PNG,
        quality: Int? = null, // ignored for PNG
        captureBeyondViewport: Boolean = false
    ): ByteArray

    fun stream(): Flow<ByteArray>

    suspend fun close()

}

enum class ScreenshotFormat {
    PNG,
    JPEG,
    WEBP
}
