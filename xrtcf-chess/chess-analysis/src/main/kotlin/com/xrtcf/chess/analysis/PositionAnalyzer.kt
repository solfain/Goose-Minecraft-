package com.xrtcf.chess.analysis

import com.xrtcf.chess.core.Fen
import com.xrtcf.chess.core.Game
import com.xrtcf.chess.core.Move
import com.xrtcf.chess.core.MoveGenerator
import com.xrtcf.chess.core.Position
import com.xrtcf.chess.engine.BestMove
import com.xrtcf.chess.engine.EngineInfo
import com.xrtcf.chess.engine.StockfishEngine

/**
 * Turns raw engine output into [PositionAnalysis], arrows, comparisons,
 * and move-quality feedback. Pure logic — no Android dependencies.
 */
object PositionAnalyzer {

    /**
     * Build analysis from a completed (or partial) MultiPV search.
     *
     * @param infos engine info lines (may include multiple depths; highest per multipv wins)
     * @param bestMove engine bestmove
     * @param fen position FEN
     * @param elo selected ELO for labeling
     * @param depth requested depth
     */
    fun buildAnalysis(
        infos: List<EngineInfo>,
        bestMove: BestMove?,
        fen: String,
        elo: Int,
        depth: Int,
        legalMoves: List<Move>,
    ): PositionAnalysis {
        val pos = Fen.parse(fen)
        val stmIsWhite = pos.turn == com.xrtcf.chess.core.Color.WHITE

        // Keep highest-depth info per multipv
        val bestByPv = mutableMapOf<Int, EngineInfo>()
        for (info in infos) {
            if (info.pv.isEmpty()) continue
            val prev = bestByPv[info.multipv]
            if (prev == null || (info.depth ?: 0) > (prev.depth ?: 0)) {
                bestByPv[info.multipv] = info
            }
        }

        val legalUcis = legalMoves.map { it.uci }.toSet()
        val byUci = legalMoves.associateBy { it.uci }

        // Side-to-move eval for each candidate: engine scores are STM perspective
        // for the position BEFORE the move. After candidate move X, eval for the
        // mover is −(opponent eval). We approximate candidate quality using the
        // reported score for that multipv line (UCI reports score from STM of the
        // root position for the PV starting with that move — actually scores are
        // from side-to-move at root, so multipv line score IS the eval after that
        // move from root STM perspective). Stockfish reports score from the
        // perspective of the side to move at the root for each PV line.
        val rootStmIsWhite = stmIsWhite
        val candidates = bestByPv.values
            .sortedBy { it.multipv }
            .mapNotNull { info ->
                val uci = info.pv.firstOrNull() ?: return@mapNotNull null
                if (uci !in legalUcis) return@mapNotNull null
                val evalStm = evalFromInfo(info, rootStmIsWhite)
                EvaluatedMove(
                    multipv = info.multipv,
                    move = byUci[uci],
                    uci = uci,
                    eval = evalStm,
                    evalText = ArrowGenerator.formatEval(evalStm),
                    quality = MoveQuality.classify(
                        cpLoss = MoveQuality.cpLoss(
                            candidatesTopEval(bestByPv, rootStmIsWhite),
                            evalStm,
                        ),
                        isEngineBest = info.multipv == 1,
                    ),
                    cpLoss = MoveQuality.cpLoss(candidatesTopEval(bestByPv, rootStmIsWhite), evalStm),
                    pv = info.pv,
                    depth = info.depth ?: 0,
                )
            }

        val top = candidates.firstOrNull()
        // Re-classify with proper top baseline
        val topEval = top?.eval ?: 0.0
        val reclassified = candidates.map { c ->
            val loss = MoveQuality.cpLoss(topEval, c.eval)
            c.copy(
                cpLoss = loss,
                quality = MoveQuality.classify(cpLoss = loss, isEngineBest = c.multipv == 1),
            )
        }

        val whiteEval = if (rootStmIsWhite) topEval else -topEval

        return PositionAnalysis(
            fen = fen,
            sideToMoveIsWhite = rootStmIsWhite,
            elo = elo,
            depth = depth,
            bestMove = reclassified.firstOrNull(),
            candidates = reclassified,
            whiteEval = whiteEval,
            whiteEvalText = ArrowGenerator.formatEval(whiteEval),
        )
    }

    private fun candidatesTopEval(bestByPv: Map<Int, EngineInfo>, stmIsWhite: Boolean): Double {
        val top = bestByPv[1] ?: bestByPv.values.minByOrNull { it.multipv } ?: return 0.0
        return evalFromInfo(top, stmIsWhite)
    }

    /**
     * Engine score → side-to-move-at-root perspective double.
     * Info scores are already from root side-to-move perspective in Stockfish.
     */
    private fun evalFromInfo(info: EngineInfo, rootStmIsWhite: Boolean): Double = when {
        info.mateIn != null -> MoveQuality.mateToCp(info.mateIn!!)
        info.scoreCp != null -> info.scoreCp!!.toDouble()
        else -> 0.0
    }

    fun arrowsFor(
        analysis: PositionAnalysis,
        position: Position,
        settings: ArrowSettings = ArrowSettings(),
    ): List<BoardArrow> {
        val legalUcis = MoveGenerator.legalMoves(position).map { it.uci }.toSet()
        val triples = analysis.candidates.map { Triple(it.uci, it.multipv, it.eval) }
        return ArrowGenerator.generate(triples, position, legalUcis, settings)
    }

    /**
     * Classify a move the user played: requires evals from mover's perspective.
     * [bestEvalStm] and [playedEvalStm] are from the mover's perspective.
     */
    fun feedbackForMove(
        playedUci: String,
        bestUci: String,
        bestEvalStm: Double,
        playedEvalStm: Double,
        depth: Int,
        missedMate: Boolean = false,
        allowedMate: Boolean = false,
    ): MoveFeedback {
        val (quality, loss) = ArrowGenerator.classifyPlayedMove(
            playedUci = playedUci,
            bestUci = bestUci,
            bestEval = bestEvalStm,
            playedEval = playedEvalStm,
            missedMate = missedMate,
            allowedMate = allowedMate,
        )
        val explanation = explain(quality, loss, bestUci, playedUci)
        val color = quality.color
        val from = playedUci.take(2)
        val to = playedUci.drop(2).take(2)
        return MoveFeedback(
            playedUci = playedUci,
            quality = quality,
            cpLoss = loss,
            bestUci = bestUci,
            bestEvalText = ArrowGenerator.formatEval(bestEvalStm),
            playedEvalText = ArrowGenerator.formatEval(playedEvalStm),
            explanation = explanation,
            arrow = BoardArrow(from, to, color, label = quality.label),
        )
    }

    fun explain(quality: MoveQuality, cpLoss: Int, bestUci: String, playedUci: String): String {
        val bestPretty = prettyUci(bestUci)
        val playedPretty = prettyUci(playedUci)
        return when (quality) {
            MoveQuality.BEST -> "Best move — matches the engine's first choice."
            MoveQuality.EXCELLENT ->
                "Excellent move — only $cpLoss cp from the engine's evaluation."
            MoveQuality.GOOD ->
                "Good alternative — loses $cpLoss cp vs $bestPretty, still solid."
            MoveQuality.DECENT ->
                "Decent — $cpLoss cp behind $bestPretty. Playable, but not precise."
            MoveQuality.INACCURACY ->
                "Inaccuracy — $cpLoss cp worse than $bestPretty. Prefer $bestPretty here."
            MoveQuality.MISTAKE ->
                "Mistake — $cpLoss cp swing. $bestPretty was clearly stronger."
            MoveQuality.BLUNDER ->
                "Blunder — $cpLoss cp lost. $bestPretty was necessary; $playedPretty concedes a lot."
        }
    }

    fun prettyUci(uci: String): String {
        if (uci.length < 4) return uci
        val base = "${uci.take(2)}→${uci.substring(2, 4)}"
        return if (uci.length >= 5) "$base=${uci[4].uppercaseChar()}" else base
    }

    /**
     * Build comparison between selected-ELO best and full-strength best.
     * Both engines analyze the same FEN.
     */
    fun buildComparison(
        fen: String,
        selectedElo: Int,
        selectedOutcomeBest: String,
        selectedEvalStm: Double,
        selectedPv: List<String>,
        fullBest: String,
        fullEvalStm: Double,
        fullPv: List<String>,
    ): EloComparison {
        val same = selectedOutcomeBest == fullBest
        val delta = fullEvalStm - selectedEvalStm
        val explanation = when {
            same ->
                "At ELO $selectedElo the engine already agrees with full strength: $selectedOutcomeBest."
            else ->
                "At ELO $selectedElo the engine prefers ${prettyUci(selectedOutcomeBest)} " +
                    "(${ArrowGenerator.formatEval(selectedEvalStm)}), while full strength prefers " +
                    "${prettyUci(fullBest)} (${ArrowGenerator.formatEval(fullEvalStm)}). " +
                    "The restricted search misses the deeper tactical point behind ${prettyUci(fullBest)}."
        }
        return EloComparison(
            fen = fen,
            selectedElo = selectedElo,
            selectedBestUci = selectedOutcomeBest,
            selectedEval = selectedEvalStm,
            selectedEvalText = ArrowGenerator.formatEval(selectedEvalStm),
            fullStrengthUci = fullBest,
            fullStrengthEval = fullEvalStm,
            fullStrengthEvalText = ArrowGenerator.formatEval(fullEvalStm),
            sameMove = same,
            evalDeltaText = ArrowGenerator.formatEval(delta),
            selectedPv = selectedPv,
            fullStrengthPv = fullPv,
            explanation = explanation,
        )
    }

    /** Eval graph points from a game's analyses (white perspective, pawns). */
    fun evalGraph(points: List<Pair<Int, Double>>): List<EvalPoint> =
        points.map { (ply, whiteEval) ->
            val mate = kotlin.math.abs(whiteEval) >= 90_000
            val pawns = if (mate) {
                if (whiteEval > 0) 10.0 else -10.0
            } else {
                whiteEval / 100.0
            }
            EvalPoint(ply, pawns, mate)
        }
}
