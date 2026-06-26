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

package com.xemantic.umwelt.viewmodel

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Reads this channel line by line and emits each line as it arrives.
 *
 * Reading stops — and the flow completes — when the channel is closed
 * (`readLine()` returns `null`). The line terminators (CR/LF) are stripped by
 * `readLine()` and are not part of the emitted strings.
 *
 * Unlike a typical cold flow, the channel is single-use shared state: a second
 * collection does not replay from the start, it resumes from wherever the
 * channel currently is. Collect the result once.
 */
fun ByteReadChannel.asLineFlow(): Flow<String> = flow {
    while (true) {
        val line = readLine() ?: break
        emit(line)
    }
}

/**
 * GETs [urlString] and exposes its body as a cold [Flow] of text lines.
 *
 * The request runs inside `prepareGet(...).execute { }`, so the HTTP call is torn
 * down deterministically when collection ends — completes, throws, or is
 * cancelled. That matters for an open-ended stream: cancelling the collector
 * releases the connection and stops the server producing. Each collector starts
 * its own request (the flow is cold).
 */
fun HttpClient.streamLines(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): Flow<String> = flow {
    prepareGet(urlString, block).execute { response ->
        emitAll(response.bodyAsChannel().asLineFlow())
    }
}

/**
 * Reads one `multipart/x-mixed-replace` part — the boundary line, headers
 * (notably `Content-Length`), the blank separator, then exactly that many bytes
 * of JPEG payload — and returns the payload, or `null` once the stream ends.
 *
 * The header scan skips everything it does not recognise (the `--<boundary>`
 * line, `Content-Type`) and tolerates the leading blank line left by the
 * previous part's trailing CRLF, so it re-synchronises on each frame without a
 * separate boundary-tracking step.
 */
suspend fun ByteReadChannel.readMjpegFrame(): ByteArray? {
    var contentLength: Int? = null
    while (true) {
        val line = readLine() ?: return null // channel closed -> stream ended
        when {
            line.isEmpty() -> if (contentLength != null) break // blank line ends the headers
            line.startsWith("Content-Length:", ignoreCase = true) ->
                contentLength = line.substringAfter(':').trim().toInt()
            // the boundary marker and Content-Type are not needed: skip them
        }
    }
    return readByteArray(contentLength)
}
