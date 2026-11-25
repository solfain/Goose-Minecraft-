package com.xrtcf.chess.analysis

/**
 * Classification of a move by centipawn loss relative to the engine best.
 *
 * Thresholds (suggested initial set from the spec):
 *  0–10 Excellent, 11–30 Good, 31–60 Decent,
 *  61–100 Inaccuracy, 101–200 Mistake, 201+ Blunder.
 *
 * Mate scores are handled separately: missing a forced mate or allowing one
 * is always treated as a blunder-class event; converting a mate is excellent.
 */
enum class MoveQuality(val label: String, val color: ArrowColor) {
    BEST("Best", ArrowColor.GREEN),
    EXCELLENT("Excellent", ArrowColor.GREEN),
    GOOD("Good", ArrowColor.BLUE),
    DECENT("Decent", ArrowColor.YELLOW),
    INACCURACY("Inaccuracy", ArrowColor.ORANGE),
    MISTAKE("Mistake", ArrowColor.RED),
    BLUNDER("Blunder", ArrowColor.RED);

    companion object {
        const val CP_EXCELLENT = 10
        const val CP_GOOD = 30
        const val CP_DECENT = 60
        const val CP_INACCURACY = 100
        const val CP_MISTAKE = 200

        /**
         * Classify a move given the evaluation before the move (from mover's
         * perspective) and after the move (from mover's perspective after
         * opponent would reply — i.e. engine eval of resulting position, still
         * from original mover's perspective), plus whether this was the
         * engine's top choice.
         *
         * @param cpLoss centipawn loss >= 0 (0 = engine best)
         * @param isEngineBest true when this move IS the top-ranked engine move
         * @param beforeMate mate score available before move (mover's perspective), null if none
         * @param afterMate mate score after move (mover's perspective), null if none
         * @param missedMate true if mover had a forced mate and did not play it
         * @param allowedMate true if mover allowed opponent a forced mate
         */
        fun classify(
            cpLoss: Int,
            isEngineBest: Boolean,
            missedMate: Boolean = false,
            allowedMate: Boolean = false,
        ): MoveQuality = when {
            allowedMate -> BLUNDER
            missedMate -> MISTAKE
            isEngineBest -> BEST
            cpLoss <= CP_EXCELLENT -> EXCELLENT
            cpLoss <= CP_GOOD -> GOOD
            cpLoss <= CP_DECENT -> DECENT
            cpLoss <= CP_INACCURACY -> INACCURACY
            cpLoss <= CP_MISTAKE -> MISTAKE
            else -> BLUNDER
        }

        /**
         * Centipawn loss for [playedEvalCp] vs [bestEvalCp], both from the
         * same side-to-move perspective. Loss is clamped at 0.
         * Mate scores should be normalized by the caller into large cp values
         * via [mateToCp].
         */
        fun cpLoss(bestEvalCp: Double, playedEvalCp: Double): Int =
            kotlin.math.max(0, (bestEvalCp - playedEvalCp).toInt())

        /** Convert mate-in-N (positive = mover mates) into a large centipawn value. */
        fun mateToCp(mateIn: Int): Double {
            val mag = 100_000.0 - kotlin.math.abs(mateIn) * 100.0
            return if (mateIn > 0) mag else -mag
        }
    }
}
