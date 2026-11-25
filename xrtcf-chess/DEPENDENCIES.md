# Dependency documentation

## Gradle plugins (root `build.gradle.kts`)

| Plugin | Version | Purpose |
|--------|---------|---------|
| `com.android.application` / `com.android.library` | 8.5.2 | Android build |
| `org.jetbrains.kotlin.android` | 2.0.21 | Kotlin for Android modules |
| `org.jetbrains.kotlin.jvm` | 2.0.21 | Pure JVM modules |
| `org.jetbrains.kotlin.plugin.compose` | 2.0.21 | Compose compiler (K2) |

Gradle wrapper distribution: **8.9** (`gradle/wrapper/gradle-wrapper.properties`).

## Android / Compose libraries (`app`, `chess-ui`)

| Artifact | Version | Why |
|----------|---------|-----|
| `androidx.compose:compose-bom` | 2024.09.03 | Aligns Compose versions |
| `androidx.compose.material3:material3` | via BOM | Material 3 UI |
| `androidx.compose.foundation:foundation` | via BOM | Canvas, gestures |
| `androidx.compose.material:material-icons-extended` | via BOM | Navigation icons |
| `androidx.core:core-ktx` | 1.13.1 | KTX |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.6 | Lifecycle |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.6 | ViewModel Compose |
| `androidx.activity:activity-compose` | 1.9.2 | ComponentActivity + setContent |
| `androidx.navigation:navigation-compose` | 2.8.2 | Bottom nav / NavHost |
| `androidx.datastore:datastore-preferences` | 1.1.1 | Ready for typed prefs |

`chess-ui` and `app` use `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`,
Java/Kotlin target 17.

## JVM modules

| Dependency | Version | Modules |
|------------|---------|---------|
| Kotlin stdlib | 200.21 (via plugin) | all |
| `junit:junit` | 4.13.2 | test only — all JVM modules + app |

## Engine binary (not a Maven dependency)

| Component | Source | License | How obtained |
|-----------|--------|---------|--------------|
| Stockfish Android arm64 | GitHub releases `official-stockfish/Stockfish` tag `sf_19` | GPLv3 | `scripts/fetch-stockfish.sh` before build; CI runs the same script |

The binary lands in `app/src/main/assets/stockfish/stockfish` and is extracted
at first launch by `EngineProvider`.

## Local JVM test runner (no Android SDK)

`scripts/run-jvm-tests.sh` compiles the four pure modules with `kotlinc` and
runs their self-contained main() suites. Optional: `STOCKFISH_BIN` for engine
protocol tests.
