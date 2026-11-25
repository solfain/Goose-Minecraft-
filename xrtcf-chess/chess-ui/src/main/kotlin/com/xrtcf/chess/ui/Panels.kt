package com.xrtcf.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrtcf.chess.analysis.EvalPoint
import com.xrtcf.chess.analysis.MoveQuality
import com.xrtcf.chess.core.MoveHistoryEntry

/** SAN move list with ply numbers; tap to jump. */
@Composable
fun MoveHistoryList(
    history: List<MoveHistoryEntry>,
    currentPly: Int,
    onPlyClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentPly) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem((currentPly).coerceIn(0, history.size - 1))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(XrtcfColors.Surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Pair up moves: white on left, black on right
        val rows = (history.indices step 2).map { i ->
            Triple(i / 2 + 1, history[i], history.getOrNull(i + 1))
        }
        itemsIndexed(rows) { _, (moveNo, white, black) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$moveNo.",
                    fontSize = 13.sp,
                    color = XrtcfColors.OnSurfaceMuted,
                    modifier = Modifier.width(34.dp),
                )
                MoveChip(
                    san = white.san,
                    active = history.indexOf(white) + 1 == currentPly,
                    onClick = { onPlyClick(history.indexOf(white) + 1) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                if (black != null) {
                    MoveChip(
                        san = black.san,
                        active = history.indexOf(black) + 1 == currentPly,
                        onClick = { onPlyClick(history.indexOf(black) + 1) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (history.isEmpty()) {
            item {
                Text(
                    text = "No moves yet — play or import a game.",
                    fontSize = 13.sp,
                    color = XrtcfColors.OnSurfaceMuted,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun MoveChip(san: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = san,
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) XrtcfColors.OnPrimary else XrtcfColors.OnSurface,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) XrtcfColors.Primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Evaluation graph across plies: green/purple line on dark panel.
 * White advantage above midline.
 */
@Composable
fun EvaluationGraph(
    points: List<EvalPoint>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Evaluation",
            style = MaterialTheme.typography.labelLarge,
            color = XrtcfColors.OnSurfaceMuted,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(10.dp))
                .background(XrtcfColors.Surface),
        ) {
            val w = size.width
            val h = size.height
            val mid = h / 2f
            // midline
            drawLine(
                color = XrtcfColors.Outline,
                start = Offset(0f, mid),
                end = Offset(w, mid),
                strokeWidth = 2f,
            )
            if (points.size >= 2) {
                val maxPly = points.maxOf { it.ply }.coerceAtLeast(1)
                val path = Path()
                points.forEachIndexed { i, p ->
                    val x = (p.ply.toFloat() / maxPly) * w
                    // clamp pawns to [-5,5] for visual range with soft saturation
                    val v = p.whitePawns.coerceIn(-5.0, 5.0)
                    val y = mid - (v / 5.0).toFloat() * (mid - 6f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = XrtcfColors.Primary, style = Stroke(width = 3f))
            } else if (points.size == 1) {
                drawCircle(XrtcfColors.Primary, radius = 4f, center = Offset(w / 2, mid))
            }
        }
    }
}

/** Quality badge for feedback display. */
@Composable
fun QualityBadge(quality: MoveQuality, modifier: Modifier = Modifier) {
    val bg = when (quality.color) {
        com.xrtcf.chess.analysis.ArrowColor.GREEN -> Color(0xFF22C55E)
        com.xrtcf.chess.analysis.ArrowColor.BLUE -> Color(0xFF3B82F6)
        com.xrtcf.chess.analysis.ArrowColor.YELLOW -> Color(0xFFEAB308)
        com.xrtcf.chess.analysis.ArrowColor.ORANGE -> Color(0xFFF97316)
        com.xrtcf.chess.analysis.ArrowColor.RED -> Color(0xFFEF4444)
    }
    Text(
        text = quality.label.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
