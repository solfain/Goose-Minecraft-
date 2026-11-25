package com.xrtcf.chess.engine

/** One `info` line parsed from UCI. */
data class EngineInfo(
    val depth: Int? = null,
    val seldepth: Int? = null,
    val multipv: Int = 1,
    /** Centipawns; null when mate score present. */
    val scoreCp: Int? = null,
    /** Mate in N plies (signed: + white mates, − black mates); null when cp. */
    val mateIn: Int? = null,
    val nodes: Long? = null,
    val nps: Long? = null,
    val timeMs: Long? = null,
    val pv: List<String> = emptyList(),
    val currmove: String? = null,
) {
    /** True if this line reports a forced mate. */
    val isMate: Boolean get() = mateIn != null

    /**
     * Score from White's perspective in centipawns for comparison,
     * mapping mate scores to large sentinel values.
     */
    fun scoreForSideToMoveWhitePerspective(sideIsWhite: Boolean): Double {
        val v = when {
            mateIn != null -> {
                val m = mateIn!!
                // Mate in fewer plies = better; white-positive
                val mag = (100000 - kotlin.math.abs(m) * 100).toDouble()
                if (m > 0) mag else -mag
            }
            scoreCp != null -> scoreCp!!.toDouble()
            else -> 0.0
        }
        // Scores from UCI are from the side-to-move perspective; convert to White
        return if (sideIsWhite) v else -v
    }
}

/** Final `bestmove` line. */
data class BestMove(
    val move: String,
    val ponder: String? = null,
)

/** Parsed MultiPV candidate with optional evaluation. */
data class CandidateMove(
    val multipv: Int,
    val uci: String,
    val info: EngineInfo,
)
