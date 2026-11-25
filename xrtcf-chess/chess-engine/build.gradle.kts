plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":chess-core"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
    // Stockfish binary for protocol tests: CI sets STOCKFISH_BIN; local default documented in README
    environment("STOCKFISH_BIN", System.getenv("STOCKFISH_BIN") ?: "")
}
