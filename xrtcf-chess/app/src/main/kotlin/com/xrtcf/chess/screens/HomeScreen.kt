package com.xrtcf.chess.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrtcf.chess.engine.EloConfig
import com.xrtcf.chess.engine.StockfishEngine
import com.xrtcf.chess.ui.EloSlider
import com.xrtcf.chess.ui.XrtcfColors

@Composable
fun HomeScreen(
    engine: StockfishEngine?,
    onStartAnalysis: () -> Unit,
    onStartTraining: () -> Unit,
) {
    var elo by remember { mutableStateOf(1500) }
    var showPgn by remember { mutableStateOf(false) }
    var showFen by remember { mutableStateOf(false) }
    var pgnText by remember { mutableStateOf("") }
    var fenText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(engineStatus(engine)) }
    var pendingImport by remember { mutableStateOf<Pair<String, String>?>(null) } // kind, data

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "XRTCF Chess",
            style = MaterialTheme.typography.displayLarge,
            color = XrtcfColors.OnSurface,
        )
        Text(
            text = if (engine != null) "Engine ready · ${engine.engineId()}" else status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (engine != null) XrtcfColors.Primary else XrtcfColors.OnSurfaceMuted,
        )

        // Primary actions
        ActionCard(
            title = "Start Analysis",
            subtitle = "Board, arrows, eval graph, MultiPV",
            icon = Icons.Filled.TrendingUp,
            onClick = onStartAnalysis,
        )
        ActionCard(
            title = "Training Mode",
            subtitle = "Play moves and get engine feedback",
            icon = Icons.Filled.School,
            onClick = onStartTraining,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(
                title = "Import PGN",
                subtitle = "Replay a full game",
                icon = Icons.Filled.FileDownload,
                onClick = { showPgn = true },
                modifier = Modifier.weight(1f),
            )
            ActionCard(
                title = "Enter FEN",
                subtitle = "Load a position",
                icon = Icons.Filled.FileDownload,
                onClick = { showFen = true },
                modifier = Modifier.weight(1f),
            )
        }

        // ELO selector
        Text(
            text = "Playing strength",
            style = MaterialTheme.typography.titleMedium,
            color = XrtcfColors.OnSurface,
        )
        EloSlider(elo = elo, onEloChange = { elo = it })

        // Recent positions (persisted simply)
        RecentPositionsSection(onSelect = { fen ->
            pendingImport = "fen" to fen
        })

        if (showPgn) {
            AlertDialog(
                onDismissRequest = { showPgn = false },
                containerColor = XrtcfColors.Surface,
                title = { Text("Import PGN", color = XrtcfColors.OnSurface) },
                text = {
                    OutlinedTextField(
                        value = pgnText,
                        onValueChange = { pgnText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = XrtcfColors.OnSurface,
                            unfocusedTextColor = XrtcfColors.OnSurfaceMuted,
                            focusedBorderColor = XrtcfColors.Primary,
                        ),
                        placeholder = { Text("1. e4 e5 2. Nf3 …") },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (pgnText.isNotBlank()) {
                            pendingImport = "pgn" to pgnText
                            showPgn = false
                        }
                    }) { Text("Import", color = XrtcfColors.Primary) }
                },
                dismissButton = {
                    TextButton(onClick = { showPgn = false }) {
                        Text("Cancel", color = XrtcfColors.OnSurfaceMuted)
                    }
                },
            )
        }

        if (showFen) {
            AlertDialog(
                onDismissRequest = { showFen = false },
                containerColor = XrtcfColors.Surface,
                title = { Text("Enter FEN", color = XrtcfColors.OnSurface) },
                text = {
                    OutlinedTextField(
                        value = fenText,
                        onValueChange = { fenText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = XrtcfColors.OnSurface,
                            unfocusedTextColor = XrtcfColors.OnSurfaceMuted,
                            focusedBorderColor = XrtcfColors.Primary,
                        ),
                        placeholder = { Text("rnbqkbnr/pppppppp/…") },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (fenText.isNotBlank()) {
                            pendingImport = "fen" to fenText
                            showFen = false
                        }
                    }) { Text("Load", color = XrtcfColors.Primary) }
                },
                dismissButton = {
                    TextButton(onClick = { showFen = false }) {
                        Text("Cancel", color = XrtcfColors.OnSurfaceMuted)
                    }
                },
            )
        }
    }
}

/** Hand off imports via a simple shared holder consumed by Analysis screen. */
object ImportBus {
    var pending: Pair<String, String>? = null
    var selectedElo: Int = 1500
}

private fun engineStatus(engine: StockfishEngine?): String =
    engine?.engineId() ?: (com.xrtcf.chess.EngineProvider.lastError()
        ?: "Engine not started")

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(XrtcfColors.Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = XrtcfColors.Primary)
        Spacer(Modifier.height(10.dp))
        Text(title, color = XrtcfColors.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            subtitle,
            color = XrtcfColors.OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = XrtcfColors.OnSurfaceMuted,
            modifier = Modifier.width(18.dp),
        )
    }
}

@Composable
private fun RecentPositionsSection(onSelect: (String) -> Unit) {
    val recents = remember {
        listOf(
            "start" to com.xrtcf.chess.core.Position.START_FEN,
            "kiwipete" to "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "scholars" to "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR b KQkq - 0 4",
        )
    }
    Text(
        text = "Recent positions",
        style = MaterialTheme.typography.titleMedium,
        color = XrtcfColors.OnSurface,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        recents.forEach { (label, fen) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(XrtcfColors.SurfaceVariant)
                    .clickable { onSelect(fen) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = XrtcfColors.OnSurface, fontSize = 14.sp)
                    Text(
                        fen.take(42) + if (fen.length > 42) "…" else "",
                        color = XrtcfColors.OnSurfaceMuted,
                        fontSize = 11.sp,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = XrtcfColors.Primary,
                )
            }
        }
    }
}
