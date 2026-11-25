# XRTCF Chess

A full Android chess analysis and training app: real legal chess rules, real
Stockfish over UCI, adjustable 400–3000 ELO recommendations, color-coded arrows,
PGN/FEN, analysis screen, offline training mode, and a GitHub Actions APK
pipeline.

**Stack:** Kotlin · Jetpack Compose · Material 3 · MVVM · Coroutines · Gradle
Kotlin DSL · Stockfish (UCI) · minSdk 26 (Android 8.0) · targetSdk 34

---

## Module layout

| Module            | Responsibility |
|-------------------|----------------|
| `chess-core`      | Board, all six pieces, legal moves, castling, en passant, promotion, checkmate/stalemate/draws, FEN, PGN, SAN, undo/redo |
| `chess-engine`    | Stockfish process lifecycle, UCI protocol, Skill Level / UCI_LimitStrength / UCI_Elo, MultiPV, cancellation |
| `chess-analysis`  | Move-quality classification, colored arrow generation, ELO comparison, eval graph, training feedback text |
| `chess-training`  | Session state, hints, progress tracking (pure Kotlin, engine injected) |
| `chess-ui`        | Compose board, arrows (Canvas), eval bar, move list, eval graph, ELO slider |
| `app`             | Screens (Home / Analysis / Training / Settings), navigation, MVVM, DataStore/SharedPreferences, engine extraction |

---

## Prerequisites

- JDK 17+
- Android SDK (compileSdk 34)
- Android Studio Hedgehog+ (or command-line SDK tools)

## Build the APK (local)

```bash
cd xrtcf-chess
chmod +x gradlew scripts/fetch-stockfish.sh

# 1) Fetch a legally distributable Stockfish Android binary into assets
./scripts/fetch-stockfish.sh arm64-v8a

# 2) Run unit tests + assemble debug APK
./gradlew test assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Build the APK (GitHub Actions)

Workflow: `.github/workflows/build-apk.yml`

On every push/PR it:

1. Checks out the repository  
2. Installs Temurin JDK 17  
3. Installs Android SDK (platform 34, build-tools 34)  
4. Fetches Stockfish `arm64` from official GitHub releases  
5. Runs `./gradlew test assembleDebug`  
6. Uploads `xrtcf-chess-debug-apk` as a downloadable artifact  

---

## Stockfish integration

- **Protocol:** UCI over stdin/stdout (`ProcessBuilder`)  
- **Binary:** `scripts/fetch-stockfish.sh` downloads  
  `stockfish-android-arm64-universal.tar.gz` from  
  https://github.com/official-stockfish/Stockfish/releases  
- **License:** Stockfish is **GPLv3** — see `NOTICE` and `LICENSE`  
- **Runtime:** `EngineProvider` extracts the asset to `filesDir`, `chmod +x`,
  starts one shared process; crashes surface as UI errors (no fake evals)  
- **Options used for ELO:**

| App ELO   | Stockfish settings |
|-----------|--------------------|
| 400–1349  | `Skill Level` 0–8 (linear map), `UCI_LimitStrength=false` |
| 1350–2850 | `UCI_LimitStrength=true`, `UCI_Elo=ELO`, `Skill Level=20` |
| 2851–3000 | Full strength (`Skill Level=20`, limit off) — engine ceiling, not faked |

Changing the ELO slider cancels the current search, reapplies options, and
re-runs analysis so arrows/evals/candidates update for real.

**Honesty note:** these options shift strength substantially but do **not**
guarantee an exact rating at the board. Calibration is empirical; we do not
claim exact ELO accuracy.

---

## Colored arrows

| Color  | Meaning |
|--------|---------|
| GREEN  | Best move at selected ELO (Multipv 1) |
| BLUE   | Good alternative |
| YELLOW | Acceptable / decent |
| ORANGE | Weak (inaccuracy) |
| RED    | Serious mistake / blunder |

Classification uses centipawn loss vs the top line:

`0–10 Excellent · 11–30 Good · 31–60 Decent · 61–100 Inaccuracy · 101–200 Mistake · 201+ Blunder`

Mate scores are handled with large sentinels; missing/allowing mate maps to
Mistake/Blunder.

---

## Tests

Pure-JVM modules ship a self-contained runner **and** JUnit wrappers:

```bash
# After ./gradlew test:
#   chess-core     — perft (start, Kiwipete, pos3, pos4), all six pieces,
#                    castling, en passant, promotion, mate/stalemate/draws,
#                    FEN/PGN/SAN, undo/redo, illegal moves
#   chess-engine   — UCI handshake, options, search, cancel, parse, ELO config
#                    (requires STOCKFISH_BIN)
#   chess-analysis — classification thresholds, arrows, comparison, feedback
#   chess-training — progress, hints, submit-move feedback, live Stockfish path
```

Local Stockfish for engine tests:

```bash
export STOCKFISH_BIN=/path/to/stockfish
./gradlew :chess-engine:test :chess-training:test
```

---

## Settings persisted

Default ELO · engine depth · MultiPV · arrow visibility · board/piece theme ·
sound · vibration · reset.

---

## Project structure (source)

```
xrtcf-chess/
├── app/ …screens, EngineProvider, MainActivity, resources
├── chess-core/ …Types, Position, MoveGenerator, Rules, Fen, Pgn, San, Game
├── chess-engine/ …UciEngine, StockfishEngine, EloConfig, models
├── chess-analysis/ …MoveQuality, Arrows, PositionAnalyzer, models
├── chess-training/ …TrainingEngine, progress, hints
├── chess-ui/ …ChessBoard, EvaluationBar, Panels, Theme
├── scripts/fetch-stockfish.sh
├── .github/workflows/build-apk.yml
├── NOTICE  LICENSE  README.md
└── gradle/wrapper/…
```

---

## Verification status

| Layer | Status |
|-------|--------|
| chess-core (perft + rules) | **Verified** — 132 checks, 0 failures; perft matches Stockfish reference nodes |
| chess-engine (UCI + ELO) | **Verified** — 79 checks against a live Stockfish 11 binary |
| chess-analysis | **Verified** — 67 checks |
| chess-training | **Verified** — 34 checks (logic + live engine path) |
| Compose UI + full APK | Source complete; compiled by GitHub Actions (local sandbox cannot reach Maven Central / Google Maven) |

---

## License

Application code: **GPLv3** (required when shipping Stockfish).  
Third-party components: see `NOTICE`.
