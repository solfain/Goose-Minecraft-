package com.xrtcf.chess.analysis

import com.xrtcf.chess.core.Move

/** One evaluated candidate line. */
data class EvaluatedMove(
    val multipv: Int,
    val move: Move?,
    val uci: String,
    /** Eval from side-to-move perspective after this move (cp or mate-normalized). */
    val eval: Double,
    val evalText: String,
    val quality: MoveQuality,
    val cpLoss: Int,
    val pv: List<String>,
    val depth: Int,
)

/** Full analysis of one position at a given ELO. */
data class PositionAnalysis(
    val fen: String,
    val sideToMoveIsWhite: Boolean,
    val elo: Int,
    val depth: Int,
    val bestMove: EvaluatedMove?,
    val candidates: List<EvaluatedMove>,
    /** Eval of best move from White's perspective for graphing. */
    val whiteEval: Double,
    val whiteEvalText: String,
)

/**
 * Unrestricted vs selected-ELO comparison.
 * Shows why the weak engine prefers a different move.
 */
data class EloComparison(
    val fen: String,
    val selectedElo: Int,
    val selectedBestUci: String,
    val selectedEval: Double,
    val selectedEvalText: String,
    val fullStrengthUci: String,
    val fullStrengthEval: Double,
    val fullStrengthEvalText: String,
    /** True when both engines pick the same move. */
    val sameMove: Boolean,
    /** fullStrengthEval − selectedEval (both White-perspective or both STM — document usage). */
    val evalDeltaText: String,
    /** PV continuation for each line. */
    val selectedPv: List<String>,
    val fullStrengthPv: List<String>,
    /** Human-readable explanation. */
    val explanation: String,
)

/** One point on the evaluation graph along the game. */
data class EvalPoint(
    val ply: Int,
    /** White-perspective eval in pawns (mate → ±10). */
    val whitePawns: Double,
    val isMate: Boolean = false,
)

/** Post-move feedback for training mode. */
data class MoveFeedback(
    val playedUci: String,
    val quality: MoveQuality,
    val cpLoss: Int,
    val bestUci: String,
    val bestEvalText: String,
    val playedEvalText: String,
    val explanation: String,
    val arrow: BoardArrow?,
)
