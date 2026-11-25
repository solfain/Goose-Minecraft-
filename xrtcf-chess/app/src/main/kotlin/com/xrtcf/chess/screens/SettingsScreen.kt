package com.xrtcf.chess.screens

import android.content.Context
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrtcf.chess.engine.EloConfig
import com.xrtcf.chess.engine.StockfishEngine
import com.xrtcf.chess.ui.EloSlider
import com.xrtcf.chess.ui.XrtcfColors

private const val PREFS = "xrtcf_settings"

@Composable
fun SettingsScreen(engine: StockfishEngine?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var defaultElo by remember { mutableIntStateOf(prefs.getInt("default_elo", 1500)) }
    var engineDepth by remember { mutableIntStateOf(prefs.getInt("engine_depth", 14)) }
    var multipv by remember { mutableIntStateOf(prefs.getInt("multipv", 3)) }
    var arrowsVisible by remember { mutableStateOf(prefs.getBoolean("arrows_visible", true)) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("sound", true)) }
    var vibrationOn by remember { mutableStateOf(prefs.getBoolean("vibration", true)) }
    var boardTheme by remember { mutableStateOf(prefs.getString("board_theme", "classic") ?: "classic") }
    var pieceTheme by remember { mutableStateOf(prefs.getString("piece_theme", "glyph") ?: "glyph") }
    var status by remember { mutableStateOf("") }

    fun persist() {
        prefs.edit()
            .putInt("default_elo", defaultElo)
            .putInt("engine_depth", engineDepth)
            .putInt("multipv", multipv)
            .putBoolean("arrows_visible", arrowsVisible)
            .putBoolean("sound", soundOn)
            .putBoolean("vibration", vibrationOn)
            .putString("board_theme", boardTheme)
            .putString("piece_theme", pieceTheme)
            .apply()
        // Apply ELO to live engine
        engine?.setElo(defaultElo)
        status = "Saved"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, color = XrtcfColors.OnSurface)

        SectionCard(title = "Strength") {
            EloSlider(elo = defaultElo, onEloChange = {
                defaultElo = it
                persist()
            })
            Text(
                "Band: ${EloConfig.bandFor(defaultElo)} · engine options: " +
                    run {
                        val c = EloConfig.fromElo(defaultElo)
                        "Skill ${c.skillLevel}" +
                            (if (c.limitStrength) ", UCI_Elo ${c.uciElo}" else ", full range")
                    },
                color = XrtcfColors.OnSurfaceMuted,
                fontSize = 12.sp,
            )
        }

        SectionCard(title = "Engine") {
            SettingSliderRow(
                label = "Analysis depth",
                value = engineDepth.toFloat(),
                range = 5f..24f,
                valueText = "$engineDepth",
                onChange = {
                    engineDepth = it.toInt()
                    persist()
                },
            )
            SettingSliderRow(
                label = "MultiPV count",
                value = multipv.toFloat(),
                range = 1f..5f,
                valueText = "$multipv",
                onChange = {
                    multipv = it.toInt()
                    persist()
                },
            )
            Text(
                "Engine: ${engine?.engineId() ?: com.xrtcf.chess.EngineProvider.lastError() ?: "not running"}",
                color = XrtcfColors.OnSurfaceMuted,
                fontSize = 12.sp,
            )
        }

        SectionCard(title = "Board") {
            ThemeRow(
                label = "Board theme",
                options = listOf("classic" to "Classic", "modern" to "Modern", "blue" to "Blue"),
                selected = boardTheme,
                onSelect = {
                    boardTheme = it
                    persist()
                },
            )
            ThemeRow(
                label = "Piece theme",
                options = listOf("glyph" to "Glyph", "minimal" to "Minimal"),
                selected = pieceTheme,
                onSelect = {
                    pieceTheme = it
                    persist()
                },
            )
            ToggleRow("Show arrows", arrowsVisible) {
                arrowsVisible = it
                persist()
            }
        }

        SectionCard(title = "Feedback") {
            ToggleRow("Sound", soundOn) {
                soundOn = it
                persist()
            }
            ToggleRow("Vibration", vibrationOn) {
                vibrationOn = it
                persist()
            }
        }

        OutlinedButton(
            onClick = {
                prefs.edit().clear().apply()
                defaultElo = 1500
                engineDepth = 14
                multipv = 3
                arrowsVisible = true
                soundOn = true
                vibrationOn = true
                boardTheme = "classic"
                pieceTheme = "glyph"
                engine?.setElo(1500)
                status = "Settings reset to defaults"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset settings", color = XrtcfColors.OnSurface)
        }

        if (status.isNotBlank()) {
            Text(status, color = XrtcfColors.Primary, fontSize = 13.sp)
        }

        Text(
            "XRTCF Chess · Stockfish is GPL-licensed; see NOTICE in the repository root.",
            color = XrtcfColors.OnSurfaceMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(XrtcfColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = XrtcfColors.Primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        content()
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = XrtcfColors.OnSurface, fontSize = 14.sp, modifier = Modifier.width(130.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = XrtcfColors.Primary,
                activeTrackColor = XrtcfColors.Primary,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(valueText, color = XrtcfColors.OnSurfaceMuted, fontSize = 13.sp, modifier = Modifier.width(32.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = XrtcfColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = XrtcfColors.Primary),
        )
    }
}

@Composable
private fun ThemeRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(label, color = XrtcfColors.OnSurface, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (key, title) ->
                val active = key == selected
                OutlinedButton(onClick = { onSelect(key) }) {
                    Text(
                        title,
                        color = if (active) XrtcfColors.Primary else XrtcfColors.OnSurfaceMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
