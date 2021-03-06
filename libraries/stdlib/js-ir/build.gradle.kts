import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType.IR

plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        nodejs()
    }
}

val unimplementedNativeBuiltIns =
    (file("$rootDir/core/builtins/native/kotlin/").list().toSortedSet() - file("$rootDir/libraries/stdlib/js-ir/builtins/").list())
        .map { "core/builtins/native/kotlin/$it" }

// Required to compile native builtins with the rest of runtime
val builtInsHeader = """@file:Suppress(
    "NON_ABSTRACT_FUNCTION_WITH_NO_BODY",
    "MUST_BE_INITIALIZED_OR_BE_ABSTRACT",
    "EXTERNAL_TYPE_EXTENDS_NON_EXTERNAL_TYPE",
    "PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED",
    "WRONG_MODIFIER_TARGET"
)
"""

val commonMainSources by task<Sync> {
    val sources = listOf(
        "libraries/stdlib/common/src/",
        "libraries/stdlib/src/kotlin/",
        "libraries/stdlib/unsigned/"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
        }
    }

    into("$buildDir/commonMainSources")
}

val jsMainSources by task<Sync> {
    val sources = listOf(
        "core/builtins/src/kotlin/",
        "libraries/stdlib/js/src/",
        "libraries/stdlib/js/runtime/",
        "libraries/stdlib/js-ir/builtins/",
        "libraries/stdlib/js-ir/src/",
        "libraries/stdlib/js-ir/runtime/",

        // TODO get rid - move to test module
        "js/js.translator/testData/_commonFiles/"
    ) + unimplementedNativeBuiltIns

    val excluded = listOf(
        // stdlib/js/src/generated is used exclusively for current `js-v1` backend.
        "libraries/stdlib/js/src/generated/**",

        // JS-specific optimized version of emptyArray() already defined
        "core/builtins/src/kotlin/ArrayIntrinsics.kt"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
            excluded.filter { it.startsWith(path) }.forEach {
                exclude(it.substring(path.length))
            }
        }
    }

    into("$buildDir/jsMainSources")

    doLast {
        unimplementedNativeBuiltIns.forEach { path ->
            val file = File("$buildDir/jsMainSources/$path")
            val sourceCode = builtInsHeader + file.readText()
            file.writeText(sourceCode)
        }
    }
}

val commonTestSources by task<Sync> {
    val sources = listOf(
        "libraries/stdlib/test/",
        "libraries/stdlib/common/test/"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
        }
    }

    into("$buildDir/commonTestSources")
}

val jsTestSources by task<Sync> {
    from("$rootDir/libraries/stdlib/js/test/")
    into("$buildDir/jsTestSources")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(commonMainSources.get().destinationDir)
        }
        val jsMain by getting {
            kotlin.srcDir(jsMainSources.get().destinationDir)
        }
        val commonTest by getting {
            dependencies {
                api(project(":kotlin-test:kotlin-test-js-ir"))
            }
            kotlin.srcDir(commonTestSources.get().destinationDir)
        }
        val jsTest by getting {
            dependencies {
                api(project(":kotlin-test:kotlin-test-js-ir"))
            }
            kotlin.srcDir(jsTestSources.get().destinationDir)
        }
    }
}

tasks.withType<KotlinCompile<*>>().configureEach {
    kotlinOptions.freeCompilerArgs += listOf(
        "-Xallow-kotlin-package",
        "-Xallow-result-return-type",
        "-Xuse-experimental=kotlin.Experimental",
        "-Xuse-experimental=kotlin.ExperimentalMultiplatform",
        "-Xuse-experimental=kotlin.contracts.ExperimentalContracts",
        "-Xinline-classes",
        "-Xopt-in=kotlin.RequiresOptIn",
        "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
        "-Xopt-in=kotlin.ExperimentalStdlibApi"
    )
}

tasks.named("compileKotlinJs") {
    (this as KotlinCompile<*>).kotlinOptions.freeCompilerArgs += "-Xir-module-name=kotlin"
    dependsOn(commonMainSources)
    dependsOn(jsMainSources)
}

tasks.named("compileTestKotlinJs") {
    dependsOn(commonTestSources)
    dependsOn(jsTestSources)
}

val packFullRuntimeKLib by tasks.registering(Jar::class) {
    dependsOn(tasks.named("compileKotlinJs"))
    from(buildDir.resolve("classes/kotlin/js/main"))
    destinationDirectory.set(rootProject.buildDir.resolve("js-ir-runtime"))
    archiveFileName.set("full-runtime.klib")
}