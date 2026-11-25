package com.xrtcf.chess.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrtcf.chess.analysis.ArrowSettings
import com.xrtcf.chess.analysis.EloComparison
import com.xrtcf.chess.analysis.EvalPoint
import com.xrtcf.chess.analysis.PositionAnalysis
import com.xrtcf.chess.analysis.PositionAnalyzer
import com.xrtcf.chess.core.Fen
import com.xrtcf.chess.core.Game
import com.xrtcf.chess.core.GameResult
import com.xrtcf.chess.core.Pgn
import com.xrtcf.chess.core.Square
import com.xrtcf.chess.engine.EloConfig
import com.xrtcf.chess.engine.StockfishEngine
import com.xrtcf.chess.ui.BoardCallbacks
import com.xrtcf.chess.ui.BoardUiState
import com.xrtcf.chess.ui.ChessBoard
import com.xrtcf.chess.ui.EvaluationBar
import com.xrtcf.chess.ui.EvaluationGraph
import com.xrtcf.chess.ui.EloSlider
import com.xrtcf.chess.ui.MoveHistoryList
import com.xrtcf.chess.ui.XrtcfColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AnalysisScreen(engine: StockfishEngine?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val game = remember { Game() }
    var boardFlip by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Square?>(null) }
    var elo by remember { mutableIntStateOf(ImportBus.selectedElo) }
    var arrowsOn by remember { mutableStateOf(true) }
    var depth by remember { mutableIntStateOf(14) }
    var multipv by remember { mutableIntStateOf(3) }
    var analysis by remember { mutableStateOf<PositionAnalysis?>(null) }
    var comparison by remember { mutableStateOf<EloComparison?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var showCompare by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var currentPly by remember { mutableIntStateOf(0) }
    var evalPoints by remember { mutableStateOf<List<EvalPoint>>(emptyList()) }

    // Consume pending import from Home
    LaunchedEffect(ImportBus.pending) {
        val p = ImportBus.pending ?: return@LaunchedEffect
        ImportBus.pending = null
        try {
            when (p.first) {
                "fen" -> {
                    game.loadFen(p.second)
                    status = "Loaded FEN"
                }
                "pgn" -> {
                    val r = game.loadPgn(p.second)
                    status = if (r.error == null) "Imported ${r.moves} moves" else "PGN error: ${r.error}"
                }
            }
            currentPly = game.history.size
        } catch (e: Exception) {
            status = "Import failed: ${e.message}"
        }
    }

    fun runAnalysis() {
        val eng = engine ?: run {
            status = com.xrtcf.chess.EngineProvider.lastError() ?: "Engine unavailable"
            return
        }
        if (analyzing) return
        analyzing = true
        status = "Analyzing…"
        val fen = game.fen()
        val movesUci = game.history.map { it.move.uci }
        val startPos = game.history.firstOrNull()?.positionBefore
        val startFen = if (startPos != null && startPos != com.xrtcf.chess.core.Position.start()) {
            Fen.format(startPos)
        } else null

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    eng.setElo(elo)
                    eng.configureAnalysis(depth, multipv)
                    if (startFen != null) {
                        eng.setPositionFromMoves(startFen, movesUci)
                    } else {
                        eng.setPositionFromMoves(null, movesUci)
                    }
                    eng.analyzeBlocking(fen, depth = depth, multipv = multipv, timeoutMs = 60_000)
                }
                val legal = game.legalMoves()
                analysis = PositionAnalyzer.buildAnalysis(
                    infos = result.infos,
                    bestMove = result.bestMove,
                    fen = fen,
                    elo = elo,
                    depth = depth,
                    legalMoves = legal,
                )
                status = "Depth ${result.infos.maxOfOrNull { it.depth ?: 0 } ?: depth} · MultiPV ${analysis?.candidates?.size ?: 0}"
                // Append eval graph point for current ply
                val we = analysis?.whiteEval ?: 0.0
                evalPoints = evalPoints.filterNot { it.ply == currentPly } +
                    com.xrtcf.chess.analysis.PositionAnalyzer.evalGraph(listOf(currentPly to we))
                evalPoints = evalPoints.sortedBy { it.ply }

                if (showCompare) {
                    val full = withContext(Dispatchers.IO) {
                        eng.setElo(3000)
                        eng.analyzeBlocking(fen, depth = depth, multipv = 1, timeoutMs = 60_000)
                    }
                    val selectedBest = analysis?.bestMove?.uci ?: result.bestMove.move
                    val selectedEval = analysis?.bestMove?.eval ?: 0.0
                    comparison = PositionAnalyzer.buildComparison(
                        fen = fen,
                        selectedElo = elo,
                        selectedOutcomeBest = selectedBest,
                        selectedEvalStm = selectedEval,
                        selectedPv = analysis?.bestMove?.pv ?: emptyList(),
                        fullBest = full.bestMove.move,
                        fullEvalStm = full.infos.find { it.pv.isNotEmpty() }?.let {
                            if (it.mateIn != null) com.xrtcf.chess.analysis.MoveQuality.mateToCp(it.mateIn!!)
                            else (it.scoreCp ?: 0).toDouble()
                        } ?: 0.0,
                        fullPv = full.infos.firstOrNull()?.pv ?: emptyList(),
                    )
                    // restore elo
                    withContext(Dispatchers.IO) { eng.setElo(elo) }
                }
            } catch (e: Exception) {
                status = "Analysis error: ${e.message}"
            } finally {
                analyzing = false
            }
        }
    }

    // Re-analyze when elo / position / depth changes
    LaunchedEffect(elo, game.currentPosition, depth, multipv) {
        runAnalysis()
    }

    DisposableEffect(Unit) {
        onDispose {
            engine?.cancel()
        }
    }

    val pos = game.currentPosition
    val legal = remember(pos, game.history.size) { game.legalMoves() }
    val selectedMoves = remember(selected, legal) {
        selected?.let { s -> legal.filter { it.from == s } } ?: emptyList()
    }
    val checkSq = remember(pos) {
        if (com.xrtcf.chess.core.Rules.isInCheck(pos, pos.turn)) pos.kingSquare(pos.turn) else null
    }
    val arrows = remember(analysis, pos, arrowsOn) {
        analysis?.let {
            PositionAnalyzer.arrowsFor(
                it,
                pos,
                ArrowSettings(visible = arrowsOn, showAllPv = true, maxArrows = 5),
            )
        } ?: emptyList()
    }

    val boardState = BoardUiState(
        position = pos,
        flipped = boardFlip || pos.turn == com.xrtcf.chess.core.Color.BLACK,
        selected = selected,
        legalTargets = selectedMoves.map { it.to }.toSet(),
        captureTargets = selectedMoves.filter { m ->
            pos.pieceAt(m.to) != null || m.isEnPassant
        }.map { it.to }.toSet(),
        lastMove = game.history.lastOrNull()?.let { it.move.from to it.move.to },
        checkSquare = checkSq,
        arrows = arrows,
        arrowVisible = arrowsOn,
        interactive = !analyzing,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Analysis", style = MaterialTheme.typography.titleLarge, color = XrtcfColors.OnSurface)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                game.reset()
                analysis = null
                evalPoints = emptyList()
                status = "Reset"
            }) { Icon(Icons.Filled.Refresh, "Reset", tint = XrtcfColors.OnSurfaceMuted) }
            IconButton(onClick = { boardFlip = !boardFlip }) {
                Icon(Icons.Filled.Flip, "Flip", tint = XrtcfColors.OnSurfaceMuted)
            }
            IconButton(onClick = {
                copyToClipboard(context, game.fen())
                status = "FEN copied"
            }) { Icon(Icons.Filled.ContentCopy, "Copy FEN", tint = XrtcfColors.OnSurfaceMuted) }
        }

        // Board + eval bar
        Row(modifier = Modifier.fillMaxWidth()) {
            EvaluationBar(
                whiteEvalPawns = (analysis?.whiteEval ?: 0.0) / 100.0,
                flipped = boardFlip,
                modifier = Modifier
                    .height(320.dp)
                    .padding(end = 6.dp),
            )
            ChessBoard(
                state = boardState,
                callbacks = BoardCallbacks(
                    onSquareTap = { sq ->
                        if (selected == null) {
                            if (pos.pieceAt(sq)?.color == pos.turn) selected = sq
                        } else {
                            val move = selectedMoves.firstOrNull { it.to == sq }
                            if (move != null) {
                                game.tryMove(move)
                                selected = null
                                currentPly = game.history.size
                                runAnalysis()
                            } else if (pos.pieceAt(sq)?.color == pos.turn) {
                                selected = sq
                            } else {
                                selected = null
                            }
                        }
                    },
                    onMoveDropped = { from, to ->
                        val move = legal.find { it.from == from && it.to == to }
                        if (move != null) {
                            game.tryMove(move)
                            selected = null
                            currentPly = game.history.size
                            runAnalysis()
                        }
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(320.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        // Status + eval
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status,
                color = XrtcfColors.OnSurfaceMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = analysis?.whiteEvalText ?: "—",
                color = XrtcfColors.Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }

        // Controls: transport
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = {
                if (game.canUndo()) {
                    game.undo()
                    currentPly = game.history.size
                    selected = null
                    runAnalysis()
                }
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = XrtcfColors.OnSurface) }
            Text(
                text = "ply $currentPly / ${game.history.size}",
                color = XrtcfColors.OnSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = {
                if (game.canRedo()) {
                    game.redo()
                    currentPly = game.history.size
                    selected = null
                    runAnalysis()
                }
            }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = XrtcfColors.OnSurface) }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (analyzing) {
                    engine?.cancel()
                    analyzing = false
                    status = "Cancelled"
                } else runAnalysis()
            }) {
                Icon(
                    if (analyzing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (analyzing) "Stop" else "Analyze",
                    tint = XrtcfColors.Primary,
                )
            }
        }

        // ELO + arrows + compare
        EloSlider(elo = elo, onEloChange = {
            elo = it
            ImportBus.selectedElo = it
        })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Arrows", color = XrtcfColors.OnSurface, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = arrowsOn,
                onCheckedChange = { arrowsOn = it },
                colors = SwitchDefaults.colors(checkedTrackColor = XrtcfColors.Primary),
            )
            Spacer(Modifier.width(16.dp))
            Text("Compare to full strength", color = XrtcfColors.OnSurface, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = showCompare,
                onCheckedChange = {
                    showCompare = it
                    if (it) runAnalysis()
                    else comparison = null
                },
                colors = SwitchDefaults.colors(checkedTrackColor = XrtcfColors.Primary),
            )
        }

        // Candidates
        analysis?.let { a ->
            Text("Candidates at ELO $elo", color = XrtcfColors.OnSurface, fontWeight = FontWeight.SemiBold)
            a.candidates.forEach { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(XrtcfColors.Surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "#${c.multipv}",
                        color = when (c.multipv) {
                            1 -> XrtcfColors.Primary
                            else -> XrtcfColors.OnSurfaceMuted
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.width(36.dp),
                    )
                    Text(
                        text = c.uci,
                        color = XrtcfColors.OnSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = c.evalText,
                        color = if (c.eval >= 0) XrtcfColors.OnSurface else XrtcfColors.OnSurfaceMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        text = c.quality.label,
                        color = c.quality.color.let {
                            when (it) {
                                com.xrtcf.chess.analysis.ArrowColor.GREEN -> XrtcfColors.Primary
                                com.xrtcf.chess.analysis.ArrowColor.BLUE -> androidx.compose.ui.graphics.Color(0xFF3B82F6)
                                com.xrtcf.chess.analysis.ArrowColor.YELLOW -> androidx.compose.ui.graphics.Color(0xFFEAB308)
                                com.xrtcf.chess.analysis.ArrowColor.ORANGE -> androidx.compose.ui.graphics.Color(0xFFF97316)
                                com.xrtcf.chess.analysis.ArrowColor.RED -> androidx.compose.ui.graphics.Color(0xFFEF4444)
                            }
                        },
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            // PV line
            a.bestMove?.pv?.let { pv ->
                Text(
                    text = "PV: ${pv.joinToString(" ")}",
                    color = XrtcfColors.OnSurfaceMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = "Depth ${a.depth} · engine ${engine?.engineId() ?: "—"}",
                color = XrtcfColors.OnSurfaceMuted,
                fontSize = 11.sp,
            )
        }

        // Comparison panel
        comparison?.let { cmp ->
            Text("ELO comparison", color = XrtcfColors.OnSurface, fontWeight = FontWeight.SemiBold)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(XrtcfColors.SurfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompareRow("Selected ELO ${cmp.selectedElo}", cmp.selectedBestUci, cmp.selectedEvalText)
                CompareRow("Full strength", cmp.fullStrengthUci, cmp.fullStrengthEvalText)
                if (!cmp.sameMove) {
                    CompareRow("Δ eval", "", cmp.evalDeltaText)
                    Text(cmp.explanation, color = XrtcfColors.OnSurfaceMuted, fontSize = 12.sp)
                }
                if (cmp.selectedPv.isNotEmpty()) {
                    Text("PV(sel): ${cmp.selectedPv.joinToString(" ")}", color = XrtcfColors.OnSurfaceMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                if (cmp.fullStrengthPv.isNotEmpty()) {
                    Text("PV(full): ${cmp.fullStrengthPv.joinToString(" ")}", color = XrtcfColors.OnSurfaceMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Move history
        Text("Moves", color = XrtcfColors.OnSurface, fontWeight = FontWeight.SemiBold)
        MoveHistoryList(
            history = game.history,
            currentPly = currentPly,
            onPlyClick = { ply ->
                game.goToPly(ply)
                currentPly = ply
                selected = null
                runAnalysis()
            },
            modifier = Modifier.height(160.dp),
        )

        // Eval graph
        EvaluationGraph(points = evalPoints)

        // PGN export
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton(onClick = {
                copyToClipboard(context, game.toPgn())
                status = "PGN copied"
            }) {
                Text("Export PGN", color = XrtcfColors.Primary)
            }
            androidx.compose.material3.TextButton(onClick = {
                copyToClipboard(context, game.fen())
                status = "FEN copied"
            }) {
                Text("Copy FEN", color = XrtcfColors.Primary)
            }
        }

        // Result banner
        if (game.result != GameResult.ONGOING) {
            Text(
                text = when (game.result) {
                    GameResult.WHITE_WINS -> "1–0 White wins"
                    GameResult.BLACK_WINS -> "0–1 Black wins"
                    GameResult.DRAW_STALEMATE -> "½–½ Stalemate"
                    GameResult.DRAW_REPETITION -> "½–½ Threefold repetition"
                    GameResult.DRAW_FIFTY_MOVE -> "½–½ Fifty-move rule"
                    GameResult.DRAW_INSUFFICIENT_MATERIAL -> "½–½ Insufficient material"
                    GameResult.DRAW_AGREEMENT -> "½–½ Draw"
                    GameResult.ONGOING -> ""
                },
                color = XrtcfColors.Primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CompareRow(label: String, move: String, evalText: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = XrtcfColors.OnSurfaceMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(move, color = XrtcfColors.OnSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.width(70.dp))
        Text(evalText, color = XrtcfColors.Primary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("xrtcf", text))
}
