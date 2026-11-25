package com.xrtcf.chess.analysis

import com.xrtcf.chess.core.Fen
import com.xrtcf.chess.core.MoveGenerator
import com.xrtcf.chess.core.Position
import com.xrtcf.chess.engine.EngineInfo

/**
 * Analysis-layer tests (classification, arrows, comparison).
 * Local runner: AnalysisTestsKt
 */
object AnalysisTests {

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

    fun runClassificationTests() {
        assertEquals("loss 0 → BEST", MoveQuality.BEST, MoveQuality.classify(0, true))
        assertEquals("loss 5 → EXCELLENT", MoveQuality.EXCELLENT, MoveQuality.classify(5, false))
        assertEquals("loss 10 → EXCELLENT", MoveQuality.EXCELLENT, MoveQuality.classify(10, false))
        assertEquals("loss 11 → GOOD", MoveQuality.GOOD, MoveQuality.classify(11, false))
        assertEquals("loss 30 → GOOD", MoveQuality.GOOD, MoveQuality.classify(30, false))
        assertEquals("loss 31 → DECENT", MoveQuality.DECENT, MoveQuality.classify(31, false))
        assertEquals("loss 60 → DECENT", MoveQuality.DECENT, MoveQuality.classify(60, false))
        assertEquals("loss 61 → INACCURACY", MoveQuality.INACCURACY, MoveQuality.classify(61, false))
        assertEquals("loss 100 → INACCURACY", MoveQuality.INACCURACY, MoveQuality.classify(100, false))
        assertEquals("loss 101 → MISTAKE", MoveQuality.MISTAKE, MoveQuality.classify(101, false))
        assertEquals("loss 200 → MISTAKE", MoveQuality.MISTAKE, MoveQuality.classify(200, false))
        assertEquals("loss 201 → BLUNDER", MoveQuality.BLUNDER, MoveQuality.classify(201, false))
        assertEquals("loss 500 → BLUNDER", MoveQuality.BLUNDER, MoveQuality.classify(500, false))

        assertEquals("allowed mate → BLUNDER", MoveQuality.BLUNDER, MoveQuality.classify(0, false, allowedMate = true))
        assertEquals("missed mate → MISTAKE", MoveQuality.MISTAKE, MoveQuality.classify(0, false, missedMate = true))
        assertEquals("engine best always BEST", MoveQuality.BEST, MoveQuality.classify(999, true))

        assertEquals("cpLoss max0", 0, MoveQuality.cpLoss(50.0, 80.0))
        assertEquals("cpLoss positive", 30, MoveQuality.cpLoss(80.0, 50.0))
        assertEquals("mateToCp positive", 99_900.0, MoveQuality.mateToCp(1))
        assertEquals("mateToCp negative", -99_900.0, MoveQuality.mateToCp(-1))

        // Arrow colors map correctly
        assertEquals("EXCELLENT green", ArrowColor.GREEN, MoveQuality.EXCELLENT.color)
        assertEquals("GOOD blue", ArrowColor.BLUE, MoveQuality.GOOD.color)
        assertEquals("DECENT yellow", ArrowColor.YELLOW, MoveQuality.DECENT.color)
        assertEquals("INACCURACY orange", ArrowColor.ORANGE, MoveQuality.INACCURACY.color)
        assertEquals("BLUNDER red", ArrowColor.RED, MoveQuality.BLUNDER.color)
        assertEquals("MISTAKE red", ArrowColor.RED, MoveQuality.MISTAKE.color)
    }

    fun runArrowTests() {
        val pos = Position.start()
        val legal = MoveGenerator.legalMoves(pos).map { it.uci }.toSet()

        // Three candidates with decreasing eval
        val candidates = listOf(
            Triple("e2e4", 1, 30.0),
            Triple("d2d4", 2, 25.0),   // loss 5 → excellent/green-ish via classify
            Triple("g1f3", 3, -10.0),  // loss 40 → decent yellow
        )
        val arrows = ArrowGenerator.generate(candidates, pos, legal)
        assertEquals("arrow count", 3, arrows.size)
        assertEquals("best is green", ArrowColor.GREEN, arrows[0].color)
        // multipv2 loss 5 ≤ 10 → EXCELLENT → GREEN
        assertEquals("multipv2 excellent green", ArrowColor.GREEN, arrows[1].color)
        // multipv3 loss 40 → DECENT → YELLOW
        assertEquals("multipv3 decent yellow", ArrowColor.YELLOW, arrows[2].color)

        // Hidden toggle
        val hidden = ArrowGenerator.generate(candidates, pos, legal, ArrowSettings(visible = false))
        assertEquals("hidden empty", 0, hidden.size)

        // Illegal moves filtered
        val withIllegal = listOf(Triple("e2e5", 1, 30.0), Triple("a1a3", 2, 10.0))
        val filtered = ArrowGenerator.generate(withIllegal, pos, legal)
        assertEquals("illegal filtered", 0, filtered.size)

        // maxArrows
        val many = (1..10).map { Triple("e2e4", it, 30.0 - it) }
        // dedup same from-to
        val deduped = ArrowGenerator.generate(many, pos, legal)
        assertEquals("same from-to deduped", 1, deduped.size)

        // Distinct arrows capped
        val distinct = listOf(
            Triple("e2e4", 1, 30.0),
            Triple("d2d4", 2, 29.0),
            Triple("c2c4", 3, 28.0),
            Triple("g1f3", 4, 27.0),
            Triple("b1c3", 5, 26.0),
            Triple("a2a3", 6, 25.0),
        )
        val capped = ArrowGenerator.generate(distinct, pos, legal, ArrowSettings(maxArrows = 4))
        assertEquals("maxArrows cap", 4, capped.size)

        // eval formatting
        assertEquals("eval cp", "+1.2", ArrowGenerator.formatEval(12.0))
        assertEquals("eval neg", "-0.5", ArrowGenerator.formatEval(-5.0))
        check("eval mate", ArrowGenerator.formatEval(99_900.0).startsWith("M+"), ArrowGenerator.formatEval(99_900.0))

        // classifyPlayedMove
        val (q, loss) = ArrowGenerator.classifyPlayedMove("d2d4", "e2e4", 30.0, 25.0)
        assertEquals("played good-ish", MoveQuality.EXCELLENT, q)
        assertEquals("loss 5", 5, loss)

        val (q2, _) = ArrowGenerator.classifyPlayedMove("a2a3", "e2e4", 30.0, -250.0)
        assertEquals("played blunder", MoveQuality.BLUNDER, q2)
    }

    fun runBuildAnalysisTests() {
        val fen = Position.START_FEN
        val legal = MoveGenerator.legalMoves(Position.start())

        val infos = listOf(
            EngineInfo(depth = 10, multipv = 1, scoreCp = 40, pv = listOf("e2e4", "e7e5")),
            EngineInfo(depth = 10, multipv = 2, scoreCp = 30, pv = listOf("d2d4", "d7d5")),
            EngineInfo(depth = 12, multipv = 1, scoreCp = 45, pv = listOf("e2e4", "e7e5")), // deeper wins
            EngineInfo(depth = 10, multipv = 3, scoreCp = -20, pv = listOf("g1f3", "g8f6")),
        )
        val analysis = PositionAnalyzer.buildAnalysis(
            infos = infos,
            bestMove = com.xrtcf.chess.engine.BestMove("e2e4"),
            fen = fen,
            elo = 1500,
            depth = 12,
            legalMoves = legal,
        )
        assertEquals("best multipv depth", 12, analysis.bestMove?.depth)
        assertEquals("best uci", "e2e4", analysis.bestMove?.uci)
        assertEquals("3 candidates", 3, analysis.candidates.size)
        assertEquals("elo labeled", 1500, analysis.elo)
        // White to move, stm white → whiteEval = top eval
        assertEquals("white eval = 45", 45.0, analysis.whiteEval)

        val arrows = PositionAnalyzer.arrowsFor(analysis, Position.start())
        check("arrows from analysis", arrows.isNotEmpty())
        assertEquals("first arrow green", ArrowColor.GREEN, arrows.first().color)
    }

    fun runComparisonTests() {
        val cmp = PositionAnalyzer.buildComparison(
            fen = Position.START_FEN,
            selectedElo = 800,
            selectedOutcomeBest = "a2a3",
            selectedEvalStm = 20.0,
            selectedPv = listOf("a2a3", "a7a6"),
            fullBest = "e2e4",
            fullEvalStm = 45.0,
            fullPv = listOf("e2e4", "e7e5"),
        )
        check("differs", !cmp.sameMove)
        check("explanation mentions 800", cmp.explanation.contains("800"))
        check("explanation mentions e2e4", cmp.explanation.contains("e2e4") || cmp.explanation.contains("e2→e4"), cmp.explanation)
        assertEquals("selected label", "a2a3", cmp.selectedBestUci)
        assertEquals("full label", "e2e4", cmp.fullStrengthUci)

        val same = PositionAnalyzer.buildComparison(
            fen = Position.START_FEN,
            selectedElo = 2800,
            selectedOutcomeBest = "e2e4",
            selectedEvalStm = 45.0,
            selectedPv = listOf("e2e4"),
            fullBest = "e2e4",
            fullEvalStm = 45.0,
            fullPv = listOf("e2e4"),
        )
        check("same move", same.sameMove)
        check("agreement text", same.explanation.contains("agrees"))
    }

    fun runFeedbackTests() {
        val fb = PositionAnalyzer.feedbackForMove(
            playedUci = "a2a3",
            bestUci = "e2e4",
            bestEvalStm = 45.0,
            playedEvalStm = 10.0,
            depth = 12,
        )
        assertEquals("loss 35", 35, fb.cpLoss)
        assertEquals("quality decent", MoveQuality.DECENT, fb.quality)
        check("explanation nonempty", fb.explanation.isNotBlank())
        check("arrow present", fb.arrow != null)
        assertEquals("arrow color yellow", ArrowColor.YELLOW, fb.arrow?.color)

        val blunder = PositionAnalyzer.feedbackForMove(
            playedUci = "f2f3",
            bestUci = "e2e4",
            bestEvalStm = 45.0,
            playedEvalStm = -200.0,
            depth = 12,
        )
        assertEquals("blunder quality", MoveQuality.BLUNDER, blunder.quality)
        check("blunder explanation", blunder.explanation.contains("Blunder"))
    }

    fun runGraphTests() {
        val pts = PositionAnalyzer.evalGraph(
            listOf(
                0 to 0.0,
                2 to 35.0,
                4 to -120.0,
                6 to 99_900.0,
            ),
        )
        assertEquals("graph size", 4, pts.size)
        assertEquals("start pawns", 0.0, pts[0].whitePawns)
        assertEquals("cp to pawns", 0.35, pts[1].whitePawns)
        assertEquals("neg pawns", -1.2, pts[2].whitePawns)
        assertEquals("mate cap", 10.0, pts[3].whitePawns)
        check("mate flag", pts[3].isMate)
    }

    fun runAll() {
        passed = 0
        failed = 0
        failures.clear()
        runClassificationTests()
        runArrowTests()
        runBuildAnalysisTests()
        runComparisonTests()
        runFeedbackTests()
        runGraphTests()
        println("==== chess-analysis: $passed passed, $failed failed ====")
        failures.forEach { println("  - $it") }
    }
}

fun main() {
    AnalysisTests.runAll()
    if (AnalysisTests.failed > 0) kotlin.system.exitProcess(1)
}
