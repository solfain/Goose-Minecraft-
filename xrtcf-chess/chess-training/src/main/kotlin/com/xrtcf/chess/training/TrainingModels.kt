package com.xrtcf.chess.training

import com.xrtcf.chess.analysis.MoveFeedback
import com.xrtcf.chess.analysis.MoveQuality

/** A training session configuration. */
data class TrainingConfig(
    val fen: String,
    val targetElo: Int,
    val sideIsWhite: Boolean = true,
    val maxHints: Int = 3,
)

/** Hint levels revealed progressively. */
enum class HintLevel(val description: String) {
    NONE("No hint"),
    ORIGIN("Show candidate from-square"),
    DESTINATION("Show from and to squares"),
    FULL("Show the full best move"),
}

/** Result of one training attempt. */
data class TrainingAttempt(
    val playedUci: String,
    val bestUci: String,
    val feedback: MoveFeedback,
    val hintsUsed: Int,
)

/** Cumulative progress stored locally. */
data class TrainingProgress(
    val totalAttempts: Int = 0,
    val byQuality: Map<MoveQuality, Int> = emptyMap(),
    val sessionsCompleted: Int = 0,
    val totalHintsUsed: Int = 0,
    val averageCpLoss: Double = 0.0,
    val lastElo: Int = 1350,
) {
    val accuracyPercent: Int
        get() = if (totalAttempts == 0) 0
        else {
            val good = (byQuality[MoveQuality.BEST] ?: 0) +
                (byQuality[MoveQuality.EXCELLENT] ?: 0) +
                (byQuality[MoveQuality.GOOD] ?: 0)
            (good * 100 / totalAttempts)
        }

    fun withAttempt(attempt: TrainingAttempt): TrainingProgress {
        val q = attempt.feedback.quality
        val newByQ = byQuality.toMutableMap().apply {
            put(q, (get(q) ?: 0) + 1)
        }
        val newTotal = totalAttempts + 1
        val newAvg = (averageCpLoss * totalAttempts + attempt.feedback.cpLoss) / newTotal
        return copy(
            totalAttempts = newTotal,
            byQuality = newByQ,
            totalHintsUsed = totalHintsUsed + attempt.hintsUsed,
            averageCpLoss = newAvg,
        )
    }
}

/** Live session state. */
data class TrainingSession(
    val config: TrainingConfig,
    val attemptNumber: Int = 0,
    val lastAttempt: TrainingAttempt? = null,
    val hintLevel: HintLevel = HintLevel.NONE,
    val hintsRemaining: Int = config.maxHints,
    val isAwaitingMove: Boolean = true,
    val opponentMoveUci: String? = null,
)
