package com.xrtcf.chess.training

import com.xrtcf.chess.analysis.ArrowGenerator
import com.xrtcf.chess.analysis.MoveFeedback
import com.xrtcf.chess.analysis.PositionAnalyzer
import com.xrtcf.chess.core.Game
import com.xrtcf.chess.core.MoveGenerator
import com.xrtcf.chess.core.Position
import com.xrtcf.chess.engine.BestMove
import com.xrtcf.chess.engine.EngineInfo
import com.xrtcf.chess.engine.StockfishEngine

/**
 * Training-mode orchestration: load position, accept the user's move,
 * evaluate it against the engine at the selected ELO, produce feedback
 * with a colored arrow, manage hints, and track progress.
 *
 * Engine calls are injected as lambdas so the module stays pure Kotlin
 * (unit-testable without a live engine; integration tests supply Stockfish).
 */
class TrainingEngine(
    private val analyze: (fen: String, elo: Int, depth: Int, multipv: Int) -> AnalysisBundle,
    private val bestMoveAt: (fen: String, elo: Int, depth: Int) -> BestMove,
    progress: TrainingProgress = TrainingProgress(),
) {
    data class AnalysisBundle(
        val infos: List<EngineInfo>,
        val bestMove: BestMove,
        val fen: String,
    )

    var session: TrainingSession
        private set

    var progress: TrainingProgress = progress
        private set

    private var hintsUsedThisAttempt = 0

    init {
        this.session = TrainingSession(
            config = TrainingConfig(fen = Position.START_FEN, targetElo = 1350),
        )
    }

    constructor(
        stockfish: StockfishEngine,
        progress: TrainingProgress = TrainingProgress(),
    ) : this(
        analyze = { fen, elo, depth, multipv ->
            stockfish.setElo(elo)
            val outcome = stockfish.analyzeBlocking(fen, depth = depth, multipv = multipv)
            AnalysisBundle(outcome.infos, outcome.bestMove, fen)
        },
        bestMoveAt = { fen, elo, depth ->
            stockfish.setElo(elo)
            stockfish.bestMove(fen, depth = depth)
        },
        progress = progress,
    )

    fun startSession(config: TrainingConfig) {
        hintsUsedThisAttempt = 0
        session = TrainingSession(config = config)
        progress = progress.copy(lastElo = config.targetElo)
    }

    fun currentFen(): String = session.config.fen

    /**
     * Evaluate the user's move at the session ELO.
     * @param playedUci UCI of the move the user made in the current position
     * @param resultingFen FEN after the user's move (so we can compare evals)
     * @param rootFen FEN before the user's move
     */
    fun submitMove(
        playedUci: String,
        rootFen: String,
        resultingFen: String,
    ): TrainingAttempt {
        val elo = session.config.targetElo
        val depth = TRAINING_DEPTH

        val rootBundle = analyze(rootFen, elo, depth, 1)
        val bestUci = rootBundle.bestMove.move

        // Eval after best move vs eval after played move, both from mover's perspective.
        // Approximate: analyze resulting position from opponent's perspective and negate.
        val playedBundle = analyze(resultingFen, elo, depth, 1)
        val playedEvalStm = -evalStmOf(playedBundle)

        // Eval if best had been played: play best in a temp game
        val temp = Game(com.xrtcf.chess.core.Fen.parse(rootFen))
        val entry = temp.tryMoveUci(bestUci)
        val bestResultFen = entry?.positionAfter?.let { com.xrtcf.chess.core.Fen.format(it) } ?: rootFen
        val bestLineBundle = analyze(bestResultFen, elo, depth, 1)
        val bestEvalStm = -evalStmOf(bestLineBundle)

        // Root eval from mover's perspective is the engine's score before move
        val rootEvalStm = evalStmOf(rootBundle)

        val missedMate = rootEvalStm >= 90_000 && playedUci != bestUci
        val allowedMate = playedEvalStm <= -90_000 && playedUci != bestUci

        val feedback: MoveFeedback = PositionAnalyzer.feedbackForMove(
            playedUci = playedUci,
            bestUci = bestUci,
            bestEvalStm = bestEvalStm,
            playedEvalStm = playedEvalStm,
            depth = depth,
            missedMate = missedMate,
            allowedMate = allowedMate,
        )

        val attempt = TrainingAttempt(
            playedUci = playedUci,
            bestUci = bestUci,
            feedback = feedback,
            hintsUsed = hintsUsedThisAttempt,
        )
        progress = progress.withAttempt(attempt)
        session = session.copy(
            lastAttempt = attempt,
            isAwaitingMove = false,
            attemptNumber = session.attemptNumber + 1,
            hintLevel = HintLevel.NONE,
        )
        return attempt
    }

    /** Next hint for the current position (best move known from last attempt or fresh query). */
    fun requestHint(rootFen: String): HintLevel {
        if (session.hintsRemaining <= 0) return session.hintLevel
        val next = when (session.hintLevel) {
            HintLevel.NONE -> HintLevel.ORIGIN
            HintLevel.ORIGIN -> HintLevel.DESTINATION
            HintLevel.DESTINATION -> HintLevel.FULL
            HintLevel.FULL -> HintLevel.FULL
        }
        if (next != session.hintLevel) {
            hintsUsedThisAttempt++
            session = session.copy(hintLevel = next, hintsRemaining = session.hintsRemaining - 1)
        }
        return session.hintLevel
    }

    /** Best move UCI for hint display (queried at session ELO). */
    fun hintMove(rootFen: String): String =
        bestMoveAt(rootFen, session.config.targetElo, TRAINING_DEPTH).move

    /** Colored arrow for the recommended move (GREEN = best at selected ELO). */
    fun recommendArrow(rootFen: String): com.xrtcf.chess.analysis.BoardArrow {
        val uci = hintMove(rootFen)
        return com.xrtcf.chess.analysis.BoardArrow(
            from = uci.take(2),
            to = uci.substring(2, 4),
            color = com.xrtcf.chess.analysis.ArrowColor.GREEN,
            label = "Best",
        )
    }

    fun completeSession() {
        session = session.copy(isAwaitingMove = false)
        progress = progress.copy(sessionsCompleted = progress.sessionsCompleted + 1)
    }

    /**
     * Best available score from a bundle in the root side-to-move perspective:
     * prefer multipv 1 line's score/mate.
     */
    private fun evalStmOf(bundle: AnalysisBundle): Double {
        val top = bundle.infos.filter { it.pv.isNotEmpty() }.minByOrNull { it.multipv }
            ?: bundle.infos.minByOrNull { it.multipv }
            ?: return 0.0
        return when {
            top.mateIn != null -> com.xrtcf.chess.analysis.MoveQuality.mateToCp(top.mateIn!!)
            top.scoreCp != null -> top.scoreCp!!.toDouble()
            else -> 0.0
        }
    }

    fun resetProgress() {
        progress = TrainingProgress()
    }

    fun exportProgress(): String = buildString {
        append("total=").append(progress.totalAttempts)
        append(",hints=").append(progress.totalHintsUsed)
        append(",sessions=").append(progress.sessionsCompleted)
        append(",avgCp=").append(String.format(java.util.Locale.US, "%.1f", progress.averageCpLoss))
        append(",elo=").append(progress.lastElo)
        for ((q, n) in progress.byQuality) {
            append(',').append(q.name).append('=').append(n)
        }
    }

    companion object {
        const val TRAINING_DEPTH = 10

        /** Parse progress back from [exportProgress] format (simple key=value CSV). */
        fun parseProgress(data: String): TrainingProgress {
            var total = 0
            var hints = 0
            var sessions = 0
            var avg = 0.0
            var elo = 1350
            val byQ = mutableMapOf<com.xrtcf.chess.analysis.MoveQuality, Int>()
            for (part in data.split(',')) {
                val kv = part.split('=')
                if (kv.size != 2) continue
                when (kv[0]) {
                    "total" -> total = kv[1].toIntOrNull() ?: 0
                    "hints" -> hints = kv[1].toIntOrNull() ?: 0
                    "sessions" -> sessions = kv[1].toIntOrNull() ?: 0
                    "avgCp" -> avg = kv[1].toDoubleOrNull() ?: 0.0
                    "elo" -> elo = kv[1].toIntOrNull() ?: 1350
                    else -> {
                        val q = try {
                            com.xrtcf.chess.analysis.MoveQuality.valueOf(kv[0])
                        } catch (_: Exception) {
                            null
                        }
                        if (q != null) byQ[q] = kv[1].toIntOrNull() ?: 0
                    }
                }
            }
            return TrainingProgress(
                totalAttempts = total,
                byQuality = byQ,
                sessionsCompleted = sessions,
                totalHintsUsed = hints,
                averageCpLoss = avg,
                lastElo = elo,
            )
        }
    }
}
