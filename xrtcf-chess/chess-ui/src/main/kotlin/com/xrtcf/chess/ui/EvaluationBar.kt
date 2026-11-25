package com.xrtcf.chess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrtcf.chess.engine.EloConfig

/**
 * Vertical evaluation bar: white advantage grows from bottom (or top when flipped).
 * [whiteEvalPawns] positive = white better; mate clamped to ±10.
 */
@Composable
fun EvaluationBar(
    whiteEvalPawns: Double,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    width: Dp = 14.dp,
) {
    val clamped = whiteEvalPawns.coerceIn(-10.0, 10.0)
    // White fraction: 0.5 at equal; more white eval → more white area from bottom
    val whiteFraction = (0.5 + clamped / 20.0).coerceIn(0.05, 0.95)

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(XrtcfColors.EvalBarBlack),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height * whiteFraction.toFloat()
            val whiteTop = if (flipped) 0f else size.height - h
            drawRect(
                color = XrtcfColors.EvalBarWhite,
                topLeft = Offset(0f, whiteTop),
                size = Size(size.width, h),
            )
        }
        // Eval label
        Text(
            text = formatPawns(clamped),
            color = if (clamped >= 0) Color.Black else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

fun formatPawns(pawns: Double): String {
    return if (kotlin.math.abs(pawns) >= 9.5 && kotlin.math.abs(pawns) >= 10.0 - 0.01) {
        // mate sentinel from graph layer uses ±10
        if (pawns > 0) "M" else "-M"
    } else {
        String.format(java.util.Locale.US, "%.1f", pawns)
    }
}

/** Horizontal ELO slider 400–3000 with band label. */
@Composable
fun EloSlider(
    elo: Int,
    onEloChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ELO $elo",
                style = MaterialTheme.typography.titleMedium,
                color = XrtcfColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = EloConfig.bandFor(elo),
                style = MaterialTheme.typography.labelLarge,
                color = XrtcfColors.Primary,
            )
        }
        Slider(
            value = elo.toFloat(),
            onValueChange = { onEloChange(it.toInt().coerceIn(EloConfig.MIN_ELO, EloConfig.MAX_ELO)) },
            valueRange = EloConfig.MIN_ELO.toFloat()..EloConfig.MAX_ELO.toFloat(),
            steps = (EloConfig.MAX_ELO - EloConfig.MIN_ELO) / 50 - 1,
            colors = SliderDefaults.colors(
                thumbColor = XrtcfColors.Primary,
                activeTrackColor = XrtcfColors.Primary,
                inactiveTrackColor = XrtcfColors.SurfaceElevated,
            ),
        )
        // Preset chips row
        Row(modifier = Modifier.fillMaxWidth()) {
            for ((presetElo, _) in listOf(
                400 to "Beg",
                1000 to "Cas",
                1500 to "Int",
                1900 to "Adv",
                2300 to "Exp",
                2800 to "Mas",
            )) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (kotlin.math.abs(elo - presetElo) < 150) XrtcfColors.Primary.copy(alpha = 0.25f)
                            else XrtcfColors.SurfaceVariant,
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = presetElo.toString(),
                        fontSize = 11.sp,
                        color = if (kotlin.math.abs(elo - presetElo) < 150) XrtcfColors.Primary
                        else XrtcfColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}
