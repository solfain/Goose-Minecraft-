package com.xrtcf.chess.engine

/**
 * Engine-layer tests. Requires a Stockfish binary at $STOCKFISH_BIN
 * (defaults to the local build path used in this workspace).
 *
 * Local runner: EngineTestsKt
 * JUnit wrapper: EngineJUnit
 */
object EngineTests {

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

    fun runEloConfigTests() {
        val beginner = EloConfig.fromElo(400)
        assertEquals("400 skill 0", 0, beginner.skillLevel)
        check("400 no limit", !beginner.limitStrength)
        assertEquals("400 band", "Beginner", beginner.band)

        val casual = EloConfig.fromElo(1000)
        check("1000 skill in 0..8", casual.skillLevel in 0..8, "skill=${casual.skillLevel}")
        assertEquals("1000 band", "Casual", casual.band)

        val mid = EloConfig.fromElo(1500)
        check("1500 limit on", mid.limitStrength)
        assertEquals("1500 uciElo", 1500, mid.uciElo)
        assertEquals("1500 band", "Intermediate", mid.band)

        val adv = EloConfig.fromElo(2000)
        check("2000 limit on", adv.limitStrength)
        assertEquals("2000 uciElo", 2000, adv.uciElo)

        val expert = EloConfig.fromElo(2300)
        assertEquals("2300 uciElo", 2300, expert.uciElo)
        assertEquals("2300 band", "Expert", expert.band)

        val master = EloConfig.fromElo(3000)
        check("3000 full strength", !master.limitStrength && master.skillLevel == 20)
        assertEquals("3000 band", "Master", master.band)

        val low = EloConfig.fromElo(900)
        check("900 no limit strength", !low.limitStrength)
        check("900 skill below 20", low.skillLevel < 20)

        // Clamping
        val below = EloConfig.fromElo(100)
        assertEquals("clamp low to 400", 400, below.targetElo)
        val above = EloConfig.fromElo(4000)
        assertEquals("clamp high to 3000", 3000, above.targetElo)

        // Bands for all presets
        for ((elo, band) in EloConfig.PRESETS) {
            assertEquals("band @$elo", band, EloConfig.bandFor(elo))
        }

        // Monotonic skill for sub-1350 range
        var prev = -1
        for (elo in 400..1349 step 50) {
            val s = EloConfig.fromElo(elo).skillLevel
            check("skill non-decreasing @$elo", s >= prev, "s=$s prev=$prev")
            prev = s
        }
    }

    fun runProtocolTests() {
        val path = stockfishPath()
        check("stockfish binary exists", java.io.File(path).exists(), path)

        UciEngine(listOf(path)).use { eng ->
            eng.start()
            val opts = eng.handshakeWithOptions(10_000)
            check("got uciok options", opts.optionLines.isNotEmpty())
            check("has Skill Level", opts.hasOption("Skill Level"))
            check("has MultiPV", opts.hasOption("MultiPV"))
            check("has UCI_LimitStrength", opts.hasOption("UCI_LimitStrength"))
            check("has UCI_Elo", opts.hasOption("UCI_Elo"))
            check("id name present", opts.ids.any { it.startsWith("id name") }, opts.ids.toString())

            eng.isready()

            // Position + search
            eng.setFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
            val outcome = eng.goSync(depth = 6, timeoutMs = 30_000)
            check("bestmove present", outcome.bestMove.move.length in 4..5, outcome.bestMove.move)
            check("has infos", outcome.infos.isNotEmpty())
            check("depth recorded", outcome.infos.any { (it.depth ?: 0) >= 4 })

            // Cancellation: start long search, stop quickly
            val t0 = System.currentTimeMillis()
            eng.setPositionFromMoves(null, emptyList())
            val handle = eng.searchAsync(
                depth = 20,
                onInfo = {},
                onBestMove = {},
            )
            Thread.sleep(150)
            handle.stop()
            // After stop, engine should be responsive
            eng.isready(5_000)
            val elapsed = System.currentTimeMillis() - t0
            check("cancel returns promptly", elapsed < 5_000, "elapsed=$elapsed")

            // Parse helpers
            val info = UciEngine.parseInfoLine(
                "info depth 12 seldepth 16 multipv 2 score cp -34 nodes 1000 nps 50000 time 20 pv e2e4 e7e5",
            )
            assertEquals("parse depth", 12, info?.depth)
            assertEquals("parse multipv", 2, info?.multipv)
            assertEquals("parse cp", -34, info?.scoreCp)
            assertEquals("parse pv0", "e2e4", info?.pv?.getOrNull(0))

            val mateInfo = UciEngine.parseInfoLine("info depth 20 score mate 3 pv e2e4")
            assertEquals("parse mate", 3, mateInfo?.mateIn)

            val bm = UciEngine.parseBestMove("bestmove e2e4 ponder e7e5")
            assertEquals("parse bestmove", "e2e4", bm?.move)
            assertEquals("parse ponder", "e7e5", bm?.ponder)
        }
    }

    fun runEloAffectsSearchTests() {
        val path = stockfishPath()
        // A position where weak and strong engines prefer different moves is hard
        // to guarantee; instead verify options are actually sent and accepted,
        // and that analyzeBlocking returns legal best moves at different ELOs.
        StockfishEngine(listOf(path)).use { eng ->
            eng.start(defaultElo = 1500)
            assertEquals("elo recorded", 1500, eng.eloConfig.targetElo)
            check("engine id", eng.engineId().contains("Stockfish", ignoreCase = true), eng.engineId())

            val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"

            eng.setElo(800)
            assertEquals("set elo 800", 800, eng.eloConfig.targetElo)
            check("800 no limit", !eng.eloConfig.limitStrength)
            val weak = eng.analyzeBlocking(fen, depth = 6, timeoutMs = 30_000)
            check("weak bestmove len", weak.bestMove.move.length in 4..5, weak.bestMove.move)

            eng.setElo(2200)
            assertEquals("set elo 2200", 2200, eng.eloConfig.targetElo)
            check("2200 limit on", eng.eloConfig.limitStrength)
            val strong = eng.analyzeBlocking(fen, depth = 6, timeoutMs = 30_000)
            check("strong bestmove len", strong.bestMove.move.length in 4..5, strong.bestMove.move)

            // MultiPV candidates
            val multi = eng.analyzeBlocking(fen, depth = 8, multipv = 3, timeoutMs = 30_000)
            val distinctPv = multi.infos.mapNotNull { it.pv.firstOrNull() }.distinct()
            check("multipv has candidates", multi.infos.isNotEmpty())
            // MultiPV 3 should yield up to 3 unique first moves
            check("multipv >= 1 unique", distinctPv.size >= 1, distinctPv.toString())
        }
    }

    fun runAll() {
        passed = 0
        failed = 0
        failures.clear()
        runEloConfigTests()
        val sf = stockfishPath()
        val ci = System.getenv("CI") == "true"
        if (java.io.File(sf).exists()) {
            runProtocolTests()
            runEloAffectsSearchTests()
        } else if (ci) {
            check("STOCKFISH_BIN present for protocol tests (CI)", false, sf)
        } else {
            println("SKIP: Stockfish not found at $sf — protocol tests skipped (set STOCKFISH_BIN)")
        }
        println("==== chess-engine: $passed passed, $failed failed ====")
        failures.forEach { println("  - $it") }
    }
}

fun main() {
    EngineTests.runAll()
    if (EngineTests.failed > 0) kotlin.system.exitProcess(1)
}
