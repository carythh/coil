package coil

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

fun Project.setupLibraryModule(block: LibraryExtension.() -> Unit = {}) {
    setupBaseModule<LibraryExtension> {
        libraryVariants.all {
            generateBuildConfigProvider?.configure { enabled = false }
        }
        testOptions {
            unitTests.isIncludeAndroidResources = true
        }
        block()
    }
}

fun Project.setupAppModule(block: BaseAppModuleExtension.() -> Unit = {}) {
    setupBaseModule<BaseAppModuleExtension> {
        defaultConfig {
            versionCode = project.versionCode
            versionName = project.versionName
            resourceConfigurations += "en"
            vectorDrawables.useSupportLibrary = true
        }
        block()
    }
}

private inline fun <reified T : BaseExtension> Project.setupBaseModule(crossinline block: T.() -> Unit = {}) {
    extensions.configure<BaseExtension>("android") {
        compileSdkVersion(project.compileSdk)
        defaultConfig {
            minSdk = project.minSdk
            targetSdk = project.targetSdk
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        kotlinOptions {
            jvmTarget = "1.8"
            allWarningsAsErrors = true

            val arguments = mutableListOf(
                // https://kotlinlang.org/docs/compiler-reference.html#progressive
                "-progressive",
                // Generate native Java 8 default interface methods.
                "-Xjvm-default=all",
                // Generate smaller bytecode by not generating runtime not-null assertions.
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions",
                // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-requires-opt-in/#requiresoptin
                "-Xopt-in=kotlin.RequiresOptIn"
            )
            if (project.name != "coil-test") {
                arguments += "-Xopt-in=coil.annotation.ExperimentalCoilApi"
            }
            freeCompilerArgs = arguments
        }
        testOptions {
            unitTests.all { test ->
                test.testLogging {
                    exceptionFormat = TestExceptionFormat.FULL
                    events = setOf(TestLogEvent.SKIPPED, TestLogEvent.PASSED, TestLogEvent.FAILED)
                    showStandardStreams = true
                }
            }
        }
        (this as T).block()
    }
}

private fun BaseExtension.kotlinOptions(block: KotlinJvmOptions.() -> Unit) {
    (this as ExtensionAware).extensions.configure("kotlinOptions", block)
}
