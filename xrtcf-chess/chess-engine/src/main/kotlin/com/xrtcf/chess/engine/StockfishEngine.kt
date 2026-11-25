package com.xrtcf.chess.engine

/**
 * Facade over [UciEngine] with ELO reconfiguration, MultiPV candidate collection,
 * cancellation, and a small in-process cache so the UI can reuse one process.
 *
 * Threading: all methods that talk to the process may be called from any
 * background dispatcher; callbacks arrive on the engine reader thread.
 */
class StockfishEngine(
    command: List<String>,
) : AutoCloseable {

    private val uci = UciEngine(command)

    @Volatile var eloConfig: EloConfig = EloConfig.fromElo(1350)
        private set

    @Volatile var lastDepth: Int = 0
        private set

    private var currentHandle: SearchHandle? = null
    private var options: EngineInfoOptions? = null

    val isRunning: Boolean get() = uci.isRunning

    /** Start process, handshake, apply default analysis options. */
    fun start(defaultElo: Int = 1350) {
        uci.start()
        options = uci.handshakeWithOptions()
        uci.configureAnalysis(depth = DEFAULT_DEPTH, multipv = DEFAULT_MULTIPV)
        setElo(defaultElo)
        uci.isready()
    }

    fun supports(option: String): Boolean = options?.hasOption(option) == true

    fun engineId(): String = options?.ids?.firstOrNull { it.startsWith("id name") } ?: "unknown"

    /**
     * Reconfigure strength. Cancels any in-flight search, applies UCI options,
     * and returns true if options were applied (always true when engine running).
     */
    fun setElo(elo: Int): Boolean {
        if (!uci.isRunning) return false
        cancel()
        val config = EloConfig.fromElo(elo)
        // Only send UCI_Elo when the engine advertises it
        if (supports("UCI_Elo") || options == null) {
            uci.applyElo(config)
        } else {
            uci.setOption("Skill Level", config.skillLevel.toString())
            uci.isready()
        }
        eloConfig = config
        return true
    }

    /** Cancel current search if any. */
    fun cancel() {
        currentHandle?.let {
            it.stop()
            it.dispose()
        }
        currentHandle = null
    }

    /** Send a UCI `position` command (startpos or FEN + moves). */
    fun setPositionFromMoves(startFen: String?, moves: List<String>) {
        cancel()
        uci.setPositionFromMoves(startFen, moves)
    }

    /** Raw UCI send for advanced control (isready etc. still available via helpers). */
    fun setOption(name: String, value: String) {
        uci.setOption(name, value)
    }

    fun configureAnalysis(depth: Int, multipv: Int) {
        uci.configureAnalysis(depth, multipv)
    }

    /**
     * Analyze the position described by [fen] (or startpos if null) with MultiPV.
     * Previous search is cancelled first. [onCandidates] receives the full ranked
     * list each time a new best depth is reached; [onComplete] fires once.
     */
    fun analyze(
        fen: String?,
        moves: List<String> = emptyList(),
        depth: Int = DEFAULT_DEPTH,
        multipv: Int = DEFAULT_MULTIPV,
        onCandidates: (List<CandidateMove>) -> Unit = {},
        onComplete: (SearchOutcome) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        cancel()
        if (!uci.isRunning) {
            onError(EngineException("Engine not running"))
            return
        }
        uci.setOption("MultiPV", multipv.toString())
        uci.setPositionFromMoves(fen, moves)

        val byPv = sortedMapOf<Int, CandidateMove>()
        var lastDepthSeen = 0

        val handle = uci.searchAsync(
            depth = depth,
            onInfo = { info ->
                if (info.pv.isNotEmpty()) {
                    byPv[info.multipv] = CandidateMove(info.multipv, info.pv.first(), info)
                    if ((info.depth ?: 0) >= lastDepthSeen) {
                        lastDepthSeen = info.depth ?: 0
                        lastDepth = lastDepthSeen
                        onCandidates(byPv.values.toList())
                    }
                }
            },
            onBestMove = { best ->
                currentHandle = null
                val infos = byPv.values.map { it.info }
                onComplete(SearchOutcome(best, infos))
            },
        )
        currentHandle = handle
    }

    /**
     * Synchronous convenience for tests: analyze and wait.
     */
    fun analyzeBlocking(
        fen: String?,
        depth: Int = 10,
        multipv: Int = 1,
        timeoutMs: Long = 60_000,
    ): SearchOutcome {
        cancel()
        uci.setOption("MultiPV", multipv.toString())
        if (fen != null) uci.setFen(fen) else uci.setPositionFromMoves(null, emptyList())
        val outcome = uci.goSync(depth = depth, multipv = multipv, timeoutMs = timeoutMs)
        lastDepth = depth
        return outcome
    }

    /** Quick best-move for training opponent at current ELO. */
    fun bestMove(fen: String?, depth: Int = 8, timeoutMs: Long = 30_000): BestMove =
        analyzeBlocking(fen, depth = depth, timeoutMs = timeoutMs).bestMove

    override fun close() {
        cancel()
        uci.close()
    }

    companion object {
        const val DEFAULT_DEPTH = 14
        const val DEFAULT_MULTIPV = 3
    }
}
