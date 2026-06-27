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

import dev.kdriver.cdp.domain.target
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import dev.kdriver.core.tab.Tab
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path

/**
 * A [Browsers] backed by Chrome instances on this machine, driven over CDP
 * through [kdriver](https://github.com/cdpdriver/kdriver).
 *
 * Each persistent [Profile] maps to its **own Chrome process** launched with that
 * profile's `--user-data-dir` (a process per profile is the only way Chrome
 * isolates persistent state), started lazily on first use and reused afterwards.
 * Incognito tabs share a single throwaway host process but each gets its own CDP
 * [browser context][dev.kdriver.cdp.domain.Target.createBrowserContext], so two
 * incognito tabs never see each other's cookies; a context is disposed when its
 * tab is closed.
 *
 * @param parentScope the scope the Chrome processes are bound to; an internal
 *   child scope is derived from it so [close] can tear down this component's
 *   coroutines, while cancelling the parent still stops everything automatically.
 * @param profileDirs the configured profiles: profile name to its persistent
 *   user-data directory.
 * @param headless whether to launch Chrome headless.
 * @param sandbox whether to enable Chrome's sandbox.
 * @param executablePath an explicit Chrome binary, or `null` to autodetect.
 */
class LocalBrowsers(
    parentScope: CoroutineScope,
    private val profileDirs: Map<String, Path>,
    private val headless: Boolean = true,
    private val sandbox: Boolean = true,
    private val executablePath: Path? = null,
) : Browsers {

    private val logger = KotlinLogging.logger {}

    // a child scope parented to parentScope: cancelling the parent still tears
    // everything down automatically, but close() can cancel just our coroutines
    // (browser connections + the incognito targetDestroyed collector below)
    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(
            parentScope.coroutineContext.job
        )
    )

    // guards the lazy browser/context bookkeeping below; held across a browser
    // launch so two concurrent requests for the same profile don't start two
    // Chrome processes
    private val lock = Mutex()

    // one persistent Chrome process per configured profile, created on demand
    private val profileBrowsers = mutableMapOf<String, Browser>()

    // a single throwaway host process for incognito tabs, plus a map from each
    // incognito tab's targetId to the CDP browser context to dispose when it closes
    private var incognitoHost: Browser? = null
    private val incognitoContexts = mutableMapOf<String, String>()

    override suspend fun profiles(): List<Profile> =
        profileDirs.keys.map { Profile(it) }

    override suspend fun newTab(profile: String?): Tab =
        if (profile == null) newIncognitoTab() else newProfileTab(profile)

    private suspend fun newProfileTab(profile: String): Tab {
        val dir = requireNotNull(profileDirs[profile]) {
            "no such profile '$profile'; configured: ${profileDirs.keys.joinToString()}"
        }
        val browser = lock.withLock {
            profileBrowsers[profile] ?: launchBrowser(userDataDir = dir).also {
                logger.debug { "Launched browser for profile '$profile' ($dir)" }
                profileBrowsers[profile] = it
            }
        }
        // the profile's persistent default context — a plain new tab is enough
        return browser.get(newTab = true)
    }

    private suspend fun newIncognitoTab(): Tab {
        val host = incognitoHost()
        val target = host.cdpTarget
        // a fresh, isolated context per tab so incognito sessions never share
        // cookies; disposeOnDetach is a backstop for an abrupt disconnect
        val context = target.createBrowserContext(disposeOnDetach = true)
        val created = target.createTarget(
            url = "about:blank",
            browserContextId = context.browserContextId,
        )
        lock.withLock { incognitoContexts[created.targetId] = context.browserContextId }
        // surface the freshly created target as a kdriver Tab
        host.updateTargets()
        return host.tabs.firstOrNull { it.targetId == created.targetId }
            ?: error("incognito tab '${created.targetId}' not found after creation")
    }

    private suspend fun incognitoHost(): Browser = lock.withLock {
        incognitoHost ?: launchBrowser(userDataDir = null).also { host ->
            logger.debug { "Launched incognito host browser" }
            incognitoHost = host
            // dispose each incognito context once its tab is gone, so closed
            // private sessions don't pile up empty contexts in the host process
            scope.launch {
                host.cdpTarget.targetDestroyed.collect { event ->
                    val contextId = lock.withLock { incognitoContexts.remove(event.targetId) }
                        ?: return@collect
                    runCatching { host.cdpTarget.disposeBrowserContext(contextId) }
                        .onFailure { logger.warn(it) { "Failed to dispose incognito context" } }
                }
            }
        }
    }

    private suspend fun launchBrowser(
        userDataDir: Path?
    ): Browser = createBrowser(
        coroutineScope = scope,
        userDataDir = userDataDir,
        headless = headless,
        sandbox = sandbox,
        browserExecutablePath = executablePath,
    )

    override suspend fun close() {
        lock.withLock {
            (profileBrowsers.values + listOfNotNull(incognitoHost)).forEach { browser ->
                runCatching { browser.stop() }
                    .onFailure { logger.warn(it) { "Error while stopping browser" } }
            }
            profileBrowsers.clear()
            incognitoHost = null
            incognitoContexts.clear()
        }
        // tears down our own coroutines (incl. the incognito targetDestroyed
        // collector) without touching the parent scope
        scope.cancel()
    }

    /** The browser-level CDP `Target` domain; the connection exists once started. */
    private val Browser.cdpTarget
        get() = (connection ?: error("browser not connected")).target

}
