#!/usr/bin/env bash
# Local verification runner for pure-JVM modules (no Android SDK required).
# Requires: JAVA_HOME (JDK 17+), kotlinc on PATH, STOCKFISH_BIN (optional but
# needed for chess-engine protocol tests).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${OUT:-/tmp/xrtcf-jvm-tests}"
STOCKFISH_BIN="${STOCKFISH_BIN:-}"
mkdir -p "$OUT"

KOTLINC="${KOTLINC:-kotlinc}"
echo "== Compiling modules =="

"$KOTLINC" "$ROOT/chess-core/src/main/kotlin" -d "$OUT/core"
"$KOTLINC" -cp "$OUT/core" "$ROOT/chess-engine/src/main/kotlin" -d "$OUT/engine"
"$KOTLINC" -cp "$OUT/core:$OUT/engine" "$ROOT/chess-analysis/src/main/kotlin" -d "$OUT/analysis"
"$KOTLINC" -cp "$OUT/core:$OUT/engine:$OUT/analysis" "$ROOT/chess-training/src/main/kotlin" -d "$OUT/training"

echo "== Compiling tests =="
"$KOTLINC" -cp "$OUT/core" \
  "$ROOT/chess-core/src/test/kotlin/com/xrtcf/chess/core/ChessCoreTests.kt" \
  -d "$OUT/core-test"
"$KOTLINC" -cp "$OUT/core:$OUT/engine" \
  "$ROOT/chess-engine/src/test/kotlin/com/xrtcf/chess/engine/EngineTests.kt" \
  -d "$OUT/engine-test"
"$KOTLINC" -cp "$OUT/core:$OUT/engine:$OUT/analysis" \
  "$ROOT/chess-analysis/src/test/kotlin/com/xrtcf/chess/analysis/AnalysisTests.kt" \
  -d "$OUT/analysis-test"
"$KOTLINC" -cp "$OUT/core:$OUT/engine:$OUT/analysis:$OUT/training" \
  "$ROOT/chess-training/src/test/kotlin/com/xrtcf/chess/training/TrainingTests.kt" \
  -d "$OUT/training-test"

# Locate kotlin-stdlib
STDLIB=""
for p in "$KOTLINC/../lib/kotlin-stdlib.jar" \
         "$(dirname "$(command -v kotlinc)")/../lib/kotlin-stdlib.jar" \
         /home/user/toolchain/package/lib/kotlin-stdlib.jar; do
  if [ -f "$p" ]; then STDLIB="$p"; break; fi
done
if [ -z "$STDLIB" ]; then
  # Ask kotlinc where it lives
  KDIR="$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)"
  STDLIB="$KDIR/lib/kotlin-stdlib.jar"
fi

CP="$OUT/core:$OUT/engine:$OUT/analysis:$OUT/training:$OUT/core-test:$OUT/engine-test:$OUT/analysis-test:$OUT/training-test:$STDLIB"
export STOCKFISH_BIN

echo "== Running tests =="
FAIL=0
java -cp "$CP" com.xrtcf.chess.core.ChessCoreTestsKt || FAIL=1
java -cp "$CP" com.xrtcf.chess.engine.EngineTestsKt || FAIL=1
java -cp "$CP" com.xrtcf.chess.analysis.AnalysisTestsKt || FAIL=1
java -cp "$CP" com.xrtcf.chess.training.TrainingTestsKt || FAIL=1

if [ "$FAIL" -ne 0 ]; then
  echo "FAILED"
  exit 1
fi
echo "ALL JVM TEST SUITES PASSED"
