package com.xrtcf.chess.analysis

import com.xrtcf.chess.core.Move
import com.xrtcf.chess.core.Position

/** Arrow color categories required by the UI spec. */
enum class ArrowColor(val hex: Long) {
    GREEN(0xFF22C55E),
    BLUE(0xFF3B82F6),
    YELLOW(0xFFEAB308),
    ORANGE(0xFFF97316),
    RED(0xFFEF4444),
}

/** A single board arrow. */
data class BoardArrow(
    val from: String,
    val to: String,
    val color: ArrowColor,
    val label: String = "",
    val evalText: String = "",
    val multipv: Int = 1,
) {
    val uci: String get() = "$from$to"
}

/**
 * Builds colored arrows from MultiPV candidate moves.
 *
 * Rules:
 *  - Only legal moves produce arrows.
 *  - Rank 1 (top candidate at selected ELO) → GREEN (best).
 *  - Subsequent ranks classified by centipawn loss vs the top candidate:
 *    BLUE good, YELLOW decent, ORANGE weak, RED serious mistake.
 *  - Overlap avoidance: same from→to keeps the best (lowest multipv) color;
 *    arrows sharing only a destination are kept (UI offsets them);
 *    arrows sharing origin with identical destination are deduplicated.
 *  - Toggle: [ArrowSettings.visible] false → empty list.
 */
data class ArrowSettings(
    val visible: Boolean = true,
    val showAllPv: Boolean = true,
    val maxArrows: Int = 5,
)

object ArrowGenerator {

    /**
     * @param candidates ranked engine candidates (multipv 1 = best), first move UCI in `uci`
     * @param topEvalCp eval of best move from side-to-move perspective (cp or mate-normalized)
     * @param evalOf map uci → eval from side-to-move perspective for that candidate's resulting position
     * @param position current position for legality check
     */
    fun generate(
        candidates: List<Triple<String, Int, Double>>, // uci, multipv, evalFromSideToMove
        position: Position,
        legalUcis: Set<String>,
        settings: ArrowSettings = ArrowSettings(),
    ): List<BoardArrow> {
        if (!settings.visible || candidates.isEmpty()) return emptyList()

        val top = candidates.minByOrNull { it.second } ?: return emptyList()
        val topEval = top.third

        val seen = mutableSetOf<String>()
        val arrows = mutableListOf<BoardArrow>()

        for ((uci, multipv, evalStm) in candidates.sortedBy { it.second }) {
            if (arrows.size >= settings.maxArrows) break
            if (!settings.showAllPv && multipv != 1) break
            if (uci !in legalUcis) continue
            if (uci in seen) continue
            // Require length 4+ for from/to extraction
            if (uci.length < 4) continue

            val from = uci.substring(0, 2)
            val to = uci.substring(2, 4)
            val key = "$from$to"
            if (key in seen) continue
            seen.add(key)
            seen.add(uci)

            val loss = MoveQuality.cpLoss(topEval, evalStm)
            val color = if (multipv == 1) {
                ArrowColor.GREEN
            } else {
                qualityColor(MoveQuality.classify(cpLoss = loss, isEngineBest = false))
            }

            val evalText = formatEval(evalStm)
            arrows.add(
                BoardArrow(
                    from = from,
                    to = to,
                    color = color,
                    label = "#$multipv",
                    evalText = evalText,
                    multipv = multipv,
                ),
            )
        }
        return arrows
    }

    private fun qualityColor(q: MoveQuality): ArrowColor = q.color

    fun formatEval(evalFromStm: Double): String {
        return if (kotlin.math.abs(evalFromStm) >= 90_000) {
            val mate = ((100_000 - kotlin.math.abs(evalFromStm)) / 100).toInt()
            val sign = if (evalFromStm > 0) "+" else "-"
            "M$sign$mate"
        } else {
            val cp = (evalFromStm / 10.0)
            String.format(java.util.Locale.US, "%+.1f", cp)
        }
    }

    /**
     * Classify a played move against the engine's best evaluation.
     * Both evals are from the mover's perspective before/after appropriately:
     * [bestEval] = eval of position if best move played (mover's perspective),
     * [playedEval] = eval after the played move (mover's perspective).
     */
    fun classifyPlayedMove(
        playedUci: String,
        bestUci: String,
        bestEval: Double,
        playedEval: Double,
        missedMate: Boolean = false,
        allowedMate: Boolean = false,
    ): Pair<MoveQuality, Int> {
        val loss = MoveQuality.cpLoss(bestEval, playedEval)
        val q = MoveQuality.classify(
            cpLoss = loss,
            isEngineBest = playedUci == bestUci,
            missedMate = missedMate,
            allowedMate = allowedMate,
        )
        return q to loss
    }
}
