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

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("umwelt.convention")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {

    // The server is jvm + native only — no js — so the default source-set
    // hierarchy (commonMain -> jvmMain, commonMain -> nativeMain -> macosArm64Main)
    // wires it for us. `expect val serverEngineFactory` is Netty on jvm, CIO on native.
    jvm()

    js {
        browser()
    }

    macosArm64 {
//        binaries {
//            sharedLib {  }
//        }
    }

    sourceSets {

        commonMain {
            dependencies {
                api(project(":umwelt-api"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kdriver.core)
                implementation(libs.kotlin.logging)
                implementation(libs.markanywhere.browse)
                implementation(libs.markanywhere.html)
                implementation(libs.markanywhere.render)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
            }
        }

    }

}
