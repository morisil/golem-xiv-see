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

package com.xemantic.umwelt.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradleExtension
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradlePlugin

/**
 * Cross-cutting build configuration shared by every umwelt module:
 * compiler options, the jvm compiler target, power-assert, and test reporting.
 *
 * Unlike a published library (which would also wire maven-publish / dokka),
 * umwelt is an application split across many modules like `-api`, and
 * `-server`, so this convention only carries the compile/test plumbing. Each
 * module declares its own Kotlin targets (`jvm()`, `js { browser() }`,
 * `macosArm64()`) directly — the convention configures whatever targets exist.
 */
@Suppress("unused") // applied by id("umwelt.convention")
class UmweltConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.doApply()
    }

}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
private fun Project.doApply() {

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val javaTarget = libs.findVersion("javaTarget").get().toString()
    val kotlinTarget = KotlinVersion.fromVersion(libs.findVersion("kotlinTarget").get().toString())
    val kotlinVersion = libs.findVersion("kotlin").get().toString()
    val jvmTarget = JvmTarget.fromTarget(javaTarget)

    plugins.apply(PowerAssertGradlePlugin::class.java)
    extensions.configure<PowerAssertGradleExtension> {
        functions.set(
            listOf(
                "com.xemantic.kotlin.test.assert",
                "com.xemantic.kotlin.test.have"
            )
        )
    }

    extensions.findByType<KotlinMultiplatformExtension>()?.apply {

        coreLibrariesVersion = kotlinVersion

        compilerOptions {
            apiVersion.set(kotlinTarget)
            languageVersion.set(kotlinTarget)
            extraWarnings.set(true)
            progressiveMode.set(true)
            freeCompilerArgs.addAll(
                "-Xcontext-sensitive-resolution"
            )
//            optIn.addAll()
        }

        // Configure the jvm compiler target for whichever module declares one;
        // configureEach is order-independent w.r.t. the module's `jvm()` call.
        targets.withType(KotlinJvmTarget::class.java).configureEach {
            compilerOptions {
                this.jvmTarget.set(jvmTarget)
                // set up according to
                // https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/
                freeCompilerArgs.add("-Xjdk-release=$javaTarget")
            }
        }

    }

    tasks.withType(JavaCompile::class.java).configureEach {
        options.release.set(javaTarget.toInt())
    }

    tasks.withType(org.gradle.api.tasks.JavaExec::class.java).configureEach {
        jvmArgs(
            "--enable-native-access=ALL-UNNAMED",
            "--sun-misc-unsafe-memory-access=allow"
        )
    }

    tasks.withType(Test::class.java).configureEach {
        useJUnitPlatform()
        // Netty/Chrome on recent JVMs (23+) need these access flags
        jvmArgs(
            "--enable-native-access=ALL-UNNAMED",
            "--sun-misc-unsafe-memory-access=allow"
        )
        // Machine-readable <test-failure> reporting (and the noise-suppressing
        // testLogging) is provided by the `xemantic.conventions` plugin's
        // `applyAxTestReporting()`, wired in the root build.
    }

}
