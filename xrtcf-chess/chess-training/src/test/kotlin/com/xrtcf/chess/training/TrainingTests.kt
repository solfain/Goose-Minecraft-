package com.xrtcf.chess.training

import com.xrtcf.chess.analysis.ArrowColor
import com.xrtcf.chess.analysis.MoveQuality
import com.xrtcf.chess.core.Fen
import com.xrtcf.chess.core.Game
import com.xrtcf.chess.core.Position
import com.xrtcf.chess.engine.BestMove
import com.xrtcf.chess.engine.EngineInfo
import com.xrtcf.chess.engine.StockfishEngine

/**
 * Training tests. Protocol-level tests need $STOCKFISH_BIN; pure logic always runs.
 * Local runner: TrainingTestsKt
 */
object TrainingTests {

    var passed = 0
    var failed = 0
    private val failures = mutableListOf<String>()

    fun check(name: String, cond: Boolean, detail: String = "") {
        if (cond) passed++
        else {
            failed++
            val msg = if (detail.isEmpty()) name else "$name — $detail"
            failures.add(msg)
            println("FAIL: $msg")
        }
    }

    fun <T> assertEquals(name: String, expected: T, actual: T) {
        check(name, expected == actual, "expected=$expected actual=$actual")
    }

    fun stockfishPath(): String {
        val env = System.getenv("STOCKFISH_BIN")?.trim().orEmpty()
        return if (env.isNotEmpty()) env
        else "/home/user/toolchain/Stockfish-sf_11/src/stockfish"
    }

    fun runProgressTests() {
        var p = TrainingProgress()
        assertEquals("start accuracy", 0, p.accuracyPercent)

        val good = TrainingAttempt(
            playedUci = "e2e4",
            bestUci = "e2e4",
            feedback = feedbackStub(MoveQuality.BEST, 0),
            hintsUsed = 0,
        )
        p = p.withAttempt(good)
        assertEquals("1 attempt", 1, p.totalAttempts)
        assertEquals("100% after best", 100, p.accuracyPercent)

        val blunder = TrainingAttempt(
            playedUci = "a2a3",
            bestUci = "e2e4",
            feedback = feedbackStub(MoveQuality.BLUNDER, 250),
            hintsUsed = 2,
        )
        p = p.withAttempt(blunder)
        assertEquals("2 attempts", 2, p.totalAttempts)
        assertEquals("50% after best+blunder", 50, p.accuracyPercent)
        assertEquals("hints tracked", 2, p.totalHintsUsed)
        assertEquals("avg cp", 125.0, p.averageCpLoss)

        // Export/import roundtrip
        val exported = TrainingEngine.exportProgressStatic(p)
        val restored = TrainingEngine.parseProgress(exported)
        assertEquals("roundtrip total", p.totalAttempts, restored.totalAttempts)
        assertEquals("roundtrip hints", p.totalHintsUsed, restored.totalHintsUsed)
        assertEquals("roundtrip avg", p.averageCpLoss, restored.averageCpLoss)
        check("avg close", kotlin.math.abs(p.averageCpLoss - restored.averageCpLoss) < 0.01)
    }

    private fun feedbackStub(q: MoveQuality, cpLoss: Int) = com.xrtcf.chess.analysis.MoveFeedback(
        playedUci = "x",
        quality = q,
        cpLoss = cpLoss,
        bestUci = "y",
        bestEvalText = "+0.0",
        playedEvalText = "-0.0",
        explanation = "stub",
        arrow = null,
    )

    fun runHintTests() {
        // Pure logic with fake engine
        val fake = TrainingEngine(
            analyze = { fen, _, _, _ ->
                TrainingEngine.AnalysisBundle(
                    infos = listOf(EngineInfo(depth = 10, multipv = 1, scoreCp = 40, pv = listOf("e2e4"))),
                    bestMove = BestMove("e2e4"),
                    fen = fen,
                )
            },
            bestMoveAt = { _, _, _ -> BestMove("e2e4") },
            progress = TrainingProgress(),
        )

        fake.startSession(TrainingConfig(fen = Position.START_FEN, targetElo = 1500))
        assertEquals("initial hint none", HintLevel.NONE, fake.session.hintLevel)
        assertEquals("hints remaining", 3, fake.session.hintsRemaining)

        val h1 = fake.requestHint(Position.START_FEN)
        assertEquals("hint1 origin", HintLevel.ORIGIN, h1)
        assertEquals("hints remaining 2", 2, fake.session.hintsRemaining)

        val h2 = fake.requestHint(Position.START_FEN)
        assertEquals("hint2 destination", HintLevel.DESTINATION, h2)

        val h3 = fake.requestHint(Position.START_FEN)
        assertEquals("hint3 full", HintLevel.FULL, h3)

        val h4 = fake.requestHint(Position.START_FEN)
        assertEquals("hint exhausted stays FULL", HintLevel.FULL, h4)
        assertEquals("hints remaining 0", 0, fake.session.hintsRemaining)

        val arrow = fake.recommendArrow(Position.START_FEN)
        assertEquals("recommend green", ArrowColor.GREEN, arrow.color)
        assertEquals("recommend from", "e2", arrow.from)
        assertEquals("recommend to", "e4", arrow.to)

        assertEquals("hint move", "e2e4", fake.hintMove(Position.START_FEN))
    }

    fun runSubmitMoveTests() {
        // Fake engine: best is always e2e4; evaluating "played" positions via stub
        val fake = TrainingEngine(
            analyze = { fen, _, _, _ ->
                // Return a score that depends on fen so played/best differ
                val cp = if (fen.contains(" b ")) -100 else 40
                TrainingEngine.AnalysisBundle(
                    infos = listOf(EngineInfo(depth = 10, multipv = 1, scoreCp = cp, pv = listOf("e2e4"))),
                    bestMove = BestMove("e2e4"),
                    fen = fen,
                )
            },
            bestMoveAt = { _, _, _ -> BestMove("e2e4") },
            progress = TrainingProgress(),
        )
        fake.startSession(TrainingConfig(fen = Position.START_FEN, targetElo = 1500))

        // User plays e2e4 (same as best) — resulting fen has black to move
        val g = Game()
        val entry = g.tryMoveUci("e2e4")!!
        val resulting = Fen.format(entry.positionAfter)

        val attempt = fake.submitMove("e2e4", Position.START_FEN, resulting)
        assertEquals("best move quality", MoveQuality.BEST, attempt.feedback.quality)
        assertEquals("best uci", "e2e4", attempt.bestUci)
        check("session has attempt", fake.session.lastAttempt != null)
        assertEquals("attempt number", 1, fake.session.attemptNumber)

        // Progress updated
        assertEquals("progress attempts", 1, fake.progress.totalAttempts)

        // Complete session
        fake.completeSession()
        assertEquals("sessions completed", 1, fake.progress.sessionsCompleted)
    }

    fun runLiveStockfishIfAvailable() {
        val path = stockfishPath()
        if (!java.io.File(path).exists()) {
            check("live stockfish present", false, path)
            return
        }
        StockfishEngine(listOf(path)).use { sf ->
            sf.start(defaultElo = 1200)
            val engine = TrainingEngine(sf, TrainingProgress())
            engine.startSession(TrainingConfig(fen = Position.START_FEN, targetElo = 1200))

            val g = Game()
            val entry = g.tryMoveUci("e2e4")!!
            val resulting = Fen.format(entry.positionAfter)

            val attempt = engine.submitMove("e2e4", Position.START_FEN, resulting)
            check("live feedback present", attempt.feedback.explanation.isNotBlank())
            check("live best uci length", attempt.bestUci.length in 4..5, attempt.bestUci)
            check(
                "live quality in enum",
                attempt.feedback.quality in MoveQuality.entries,
                attempt.feedback.quality.name,
            )
            // e2e4 is almost certainly at or near best at 1200 — allow any quality but no crash
            check("live cpLoss >= 0", attempt.feedback.cpLoss >= 0, "${attempt.feedback.cpLoss}")

            // Hint path with live engine
            engine.startSession(TrainingConfig(fen = Position.START_FEN, targetElo = 1200))
            val hint = engine.hintMove(Position.START_FEN)
            check("live hint length", hint.length in 4..5, hint)
        }
    }

    fun runAll() {
        passed = 0
        failed = 0
        failures.clear()
        runProgressTests()
        runHintTests()
        runSubmitMoveTests()
        val sf = stockfishPath()
        val ci = System.getenv("CI") == "true"
        if (java.io.File(sf).exists()) {
            runLiveStockfishIfAvailable()
        } else if (ci) {
            check("Stockfish present for live training tests (CI)", false, sf)
        } else {
            println("SKIP: Stockfish not found at $sf — live tests skipped")
        }
        println("==== chess-training: $passed passed, $failed failed ====")
        failures.forEach { println("  - $it") }
    }
}

/** Helper used by tests — delegates to companion. */
private fun TrainingEngine.Companion.exportProgressStatic(p: TrainingProgress): String {
    // Reuse instance export via a throwaway engine with fake callbacks
    val e = TrainingEngine(
        analyze = { f, _, _, _ ->
            TrainingEngine.AnalysisBundle(emptyList(), BestMove("e2e4"), f)
        },
        bestMoveAt = { _, _, _ -> BestMove("e2e4") },
        progress = p,
    )
    return e.exportProgress()
}

fun main() {
    TrainingTests.runAll()
    if (TrainingTests.failed > 0) kotlin.system.exitProcess(1)
}
