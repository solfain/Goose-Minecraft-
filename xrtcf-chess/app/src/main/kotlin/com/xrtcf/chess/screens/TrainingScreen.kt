package com.xrtcf.chess.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrtcf.chess.analysis.ArrowColor
import com.xrtcf.chess.analysis.BoardArrow
import com.xrtcf.chess.core.Fen
import com.xrtcf.chess.core.Game
import com.xrtcf.chess.core.Square
import com.xrtcf.chess.engine.StockfishEngine
import com.xrtcf.chess.training.HintLevel
import com.xrtcf.chess.training.TrainingConfig
import com.xrtcf.chess.training.TrainingEngine
import com.xrtcf.chess.training.TrainingProgress
import com.xrtcf.chess.ui.BoardCallbacks
import com.xrtcf.chess.ui.BoardUiState
import com.xrtcf.chess.ui.ChessBoard
import com.xrtcf.chess.ui.EloSlider
import com.xrtcf.chess.ui.QualityBadge
import com.xrtcf.chess.ui.XrtcfColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrainingScreen(engine: StockfishEngine?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember {
        context.getSharedPreferences("xrtcf_training", android.content.Context.MODE_PRIVATE)
    }
    var progress by remember {
        mutableStateOf(
            TrainingEngine.parseProgress(prefs.getString("progress", "") ?: ""),
        )
    }
    var elo by remember { mutableIntStateOf(ImportBus.selectedElo) }
    var fenInput by remember { mutableStateOf("") }
    var showFenEditor by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Set a position and ELO, then play a move.") }
    var busy by remember { mutableStateOf(false) }
    var lastFeedback by remember { mutableStateOf<com.xrtcf.chess.analysis.MoveFeedback?>(null) }
    var hintText by remember { mutableStateOf<String?>(null) }
    var recommendation by remember { mutableStateOf<BoardArrow?>(null) }

    val game = remember { Game() }
    var boardFlip by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Square?>(null) }
    var training by remember {
        mutableStateOf<TrainingEngine?>(null) }
    var sessionStarted by remember { mutableStateOf(false) }

    fun ensureTraining(): TrainingEngine? {
        val eng = engine ?: run {
            status = com.xrtcf.chess.EngineProvider.lastError() ?: "Engine unavailable"
            return null
        }
        training?.let { return it }
        val t = TrainingEngine(eng, progress)
        training = t
        return t
    }

    fun startSession() {
        val t = ensureTraining() ?: return
        val startFen = if (fenInput.isNotBlank()) fenInput else com.xrtcf.chess.core.Position.START_FEN
        try {
            game.loadFen(startFen)
        } catch (e: Exception) {
            status = "Invalid FEN: ${e.message}"
            return
        }
        t.startSession(TrainingConfig(fen = game.fen(), targetElo = elo))
        sessionStarted = true
        lastFeedback = null
        hintText = null
        recommendation = null
        selected = null
        status = "Your move — play white" + if (game.turn == com.xrtcf.chess.core.Color.BLACK) " (black to move)" else ""
        ImportBus.selectedElo = elo
    }

    fun submit(square: Square) {
        if (!sessionStarted || busy) return
        val t = ensureTraining() ?: return
        val legal = game.legalMoves()

        if (selected == null) {
            if (game.currentPosition.pieceAt(square)?.color == game.turn) selected = square
            return
        }
        val from = selected!!
        val move = legal.find { it.from == from && it.to == square }
        if (move == null) {
            if (game.currentPosition.pieceAt(square)?.color == game.turn) selected = square
            else selected = null
            return
        }

        // Snapshot root, play move, evaluate
        val rootFen = game.fen()
        val entry = game.tryMove(move) ?: return
        selected = null
        val resultingFen = Fen.format(entry.positionAfter)

        busy = true
        status = "Evaluating your move at ELO $elo…"
        scope.launch {
            try {
                val attempt = withContext(Dispatchers.IO) {
                    t.submitMove(move.uci, rootFen, resultingFen)
                }
                lastFeedback = attempt.feedback
                progress = t.progress
                prefs.edit().putString("progress", TrainingEngine.exportProgressToString(t.progress)).apply()
                status = attempt.feedback.explanation
                // Show engine recommendation arrow for the position before their move
                recommendation = withContext(Dispatchers.IO) {
                    t.recommendArrow(rootFen)
                }
            } catch (e: Exception) {
                status = "Evaluation failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun requestHint() {
        val t = ensureTraining() ?: return
        if (!sessionStarted) return
        val rootFen = game.fen()
        scope.launch {
            try {
                val level = withContext(Dispatchers.IO) { t.requestHint(rootFen) }
                val best = withContext(Dispatchers.IO) { t.hintMove(rootFen) }
                hintText = when (level) {
                    HintLevel.NONE -> "No hints left"
                    HintLevel.ORIGIN -> "Hint: piece on ${best.take(2)}"
                    HintLevel.DESTINATION -> "Hint: ${best.take(2)} → ${best.substring(2, 4)}"
                    HintLevel.FULL -> "Hint: play $best"
                }
                if (level == HintLevel.FULL) {
                    recommendation = withContext(Dispatchers.IO) { t.recommendArrow(rootFen) }
                }
                progress = t.progress
            } catch (e: Exception) {
                status = "Hint failed: ${e.message}"
            }
        }
    }

    val pos = game.currentPosition
    val legal = remember(pos, game.history.size) { game.legalMoves() }
    val selectedMoves = remember(selected, legal) {
        selected?.let { s -> legal.filter { it.from == s } } ?: emptyList()
    }
    val arrows = listOfNotNull(recommendation)

    val boardState = BoardUiState(
        position = pos,
        flipped = boardFlip || pos.turn == com.xrtcf.chess.core.Color.BLACK,
        selected = selected,
        legalTargets = selectedMoves.map { it.to }.toSet(),
        captureTargets = selectedMoves.filter { m ->
            pos.pieceAt(m.to) != null || m.isEnPassant
        }.map { it.to }.toSet(),
        lastMove = game.history.lastOrNull()?.let { it.move.from to it.move.to },
        checkSquare = if (com.xrtcf.chess.core.Rules.isInCheck(pos, pos.turn)) pos.kingSquare(pos.turn) else null,
        arrows = arrows,
        arrowVisible = true,
        interactive = sessionStarted && !busy,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Training", style = MaterialTheme.typography.titleLarge, color = XrtcfColors.OnSurface)
        Text(
            text = status,
            color = XrtcfColors.OnSurfaceMuted,
            fontSize = 13.sp,
        )

        ChessBoard(
            state = boardState,
            callbacks = BoardCallbacks(onSquareTap = ::submit),
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(10.dp)),
        )

        EloSlider(elo = elo, onEloChange = { elo = it })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = ::startSession,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = XrtcfColors.Primary),
                modifier = Modifier.weight(1f),
            ) { Text(if (sessionStarted) "Restart" else "Start session") }
            OutlinedButton(
                onClick = { showFenEditor = !showFenEditor },
                modifier = Modifier.weight(1f),
            ) { Text("Position FEN", color = XrtcfColors.OnSurface) }
        }

        if (showFenEditor) {
            androidx.compose.material3.OutlinedTextField(
                value = fenInput,
                onValueChange = { fenInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("FEN (empty = start position)") },
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = XrtcfColors.OnSurface,
                    fontSize = 12.sp,
                ),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = ::requestHint, enabled = sessionStarted && !busy) {
                Text("Hint", color = XrtcfColors.Primary)
            }
            OutlinedButton(onClick = { boardFlip = !boardFlip }) {
                Text("Flip", color = XrtcfColors.OnSurface)
            }
            OutlinedButton(onClick = {
                game.reset()
                sessionStarted = false
                lastFeedback = null
                recommendation = null
                hintText = null
                selected = null
                status = "Board reset."
            }) { Text("Reset", color = XrtcfColors.OnSurface) }
        }

        hintText?.let {
            Text(it, color = XrtcfColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        lastFeedback?.let { fb ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(XrtcfColors.SurfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QualityBadge(fb.quality)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "loss ${fb.cpLoss} cp",
                        color = XrtcfColors.OnSurfaceMuted,
                        fontSize = 13.sp,
                    )
                }
                Text(
                    text = "Best: ${fb.bestUci} (${fb.bestEvalText}) · Yours: ${fb.playedUci} (${fb.playedEvalText})",
                    color = XrtcfColors.OnSurface,
                    fontSize = 13.sp,
                )
                Text(fb.explanation, color = XrtcfColors.OnSurfaceMuted, fontSize = 13.sp)
            }
        }

        // Progress panel
        Text("Progress", style = MaterialTheme.typography.titleMedium, color = XrtcfColors.OnSurface)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(XrtcfColors.Surface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Attempts: ${progress.totalAttempts}  ·  Accuracy: ${progress.accuracyPercent}%  ·  " +
                    "Avg loss: ${"%.0f".format(progress.averageCpLoss)} cp",
                color = XrtcfColors.OnSurface,
                fontSize = 13.sp,
            )
            Text(
                "Hints used: ${progress.totalHintsUsed}  ·  Sessions: ${progress.sessionsCompleted}",
                color = XrtcfColors.OnSurfaceMuted,
                fontSize = 12.sp,
            )
            if (progress.byQuality.isNotEmpty()) {
                Text(
                    progress.byQuality.entries.joinToString("  ") { "${it.key.label}: ${it.value}" },
                    color = XrtcfColors.OnSurfaceMuted,
                    fontSize = 12.sp,
                )
            }
            OutlinedButton(onClick = {
                training?.resetProgress() ?: run {
                    progress = TrainingProgress()
                }
                progress = TrainingProgress()
                prefs.edit().remove("progress").apply()
                status = "Progress reset."
            }) { Text("Reset progress", color = XrtcfColors.OnSurfaceMuted) }
        }
    }
}

/** Export helper so TrainingEngine companion can be used with instance progress. */
private fun TrainingEngine.Companion.exportProgressToString(p: TrainingProgress): String {
    // Reconstruct via a throwaway engine
    val e = TrainingEngine(
        analyze = { f, _, _, _ ->
            TrainingEngine.AnalysisBundle(emptyList(), com.xrtcf.chess.engine.BestMove("e2e4"), f)
        },
        bestMoveAt = { _, _, _ -> com.xrtcf.chess.engine.BestMove("e2e4") },
        progress = p,
    )
    return e.exportProgress()
}
