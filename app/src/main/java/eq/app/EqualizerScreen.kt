package eq.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eq.app.ui.theme.EqTheme
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onVisualizerPermission: () -> Unit
) {
    EqTheme(themeMode) {
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            EqEngine.start(context)
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Eq — Эквалайзер") },
                    actions = {
                        IconButton(onClick = { onThemeChange(nextTheme(themeMode)) }) {
                            Icon(themeIcon(themeMode), contentDescription = "Тема")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val error = EqEngine.error
                if (error != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Эквалайзер недоступен", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Глобальная аудиосессия заблокирована прошивкой. Устройство не разрешает управление системным эквалайзером.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                } else if (!EqEngine.ready) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Загрузка…")
                    }
                } else {
                    val onSetEnabled: (Boolean) -> Unit = { EqEngine.setEnabled(context, it) }
                    MasterCard(onSetEnabled)

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EqEngine.presetNames.forEach { name ->
                            FilterChip(
                                selected = EqEngine.preset == name,
                                onClick = { EqEngine.applyPreset(name) },
                                label = { Text(name) }
                            )
                        }
                    }

                    for (i in 0 until EqEngine.bands) {
                        SliderRow(
                            label = freqLabel(EqEngine.freqs.getOrElse(i) { 0 }),
                            valueText = "${EqEngine.levels[i].toInt()} мБ",
                            value = EqEngine.levels[i].toInt().toFloat(),
                            range = EqEngine.range[0].toFloat()..EqEngine.range[1].toFloat(),
                            onChange = { EqEngine.setBandLevel(i, it.toShort()) }
                        )
                    }

                    if (EqEngine.boostAvailable) {
                        SliderRow(
                            label = "Общее усиление",
                            valueText = "${"%.1f".format(EqEngine.boost / 1000f)} дБ",
                            value = EqEngine.boost.toFloat(),
                            range = 0f..15000f,
                            onChange = { EqEngine.updateBoost(it) }
                        )
                    }

                    if (EqEngine.bassAvailable) {
                        SliderRow(
                            label = "Bass Boost",
                            valueText = "${EqEngine.bass} %",
                            value = EqEngine.bass.toFloat(),
                            range = 0f..100f,
                            onChange = { EqEngine.updateBass(it) }
                        )
                    }

                    if (EqEngine.virtualAvailable) {
                        SliderRow(
                            label = "Virtualizer",
                            valueText = "${EqEngine.virtual} %",
                            value = EqEngine.virtual.toFloat(),
                            range = 0f..100f,
                            onChange = { EqEngine.updateVirtual(it) }
                        )
                    }

                    VisualizerCard(onVisualizerPermission)
                }
            }
        }
    }
}

@Composable
private fun MasterCard(onSetEnabled: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Включено", style = MaterialTheme.typography.titleMedium)
                Text("Глобальный системный эквалайзер", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = EqEngine.enabled,
                onCheckedChange = onSetEnabled
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Box(modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )
    }
}

@Composable
private fun VisualizerCard(onVisualizerPermission: () -> Unit) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Визуализация", style = MaterialTheme.typography.titleMedium)
                    Text("Уровень сигнала в реальном времени", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = EqEngine.visualizerActive,
                    onCheckedChange = { want ->
                        if (want) {
                            onVisualizerPermission()
                        } else {
                            EqEngine.setVisualizer(context, false)
                        }
                    }
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(top = 8.dp)
            ) {
                val data = EqEngine.waveform
                val barCount = 48
                val gap = 2.dp.toPx()
                val barWidth = (size.width - gap * (barCount - 1)) / barCount
                for (b in 0 until barCount) {
                    val amp = if (data.isEmpty()) 0f else {
                        val start = b * data.size / barCount
                        val end = (((b + 1) * data.size / barCount).coerceAtMost(data.size))
                        var peak = 0
                        for (j in start until end) {
                            val v = abs((data[j].toInt() and 0xFF) - 128)
                            if (v > peak) peak = v
                        }
                        peak / 128f
                    }
                    val h = (amp * size.height).coerceAtLeast(2.dp.toPx())
                    val x = b * (barWidth + gap)
                    drawLine(
                        color = primary,
                        start = Offset(x + barWidth / 2, size.height),
                        end = Offset(x + barWidth / 2, size.height - h),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

private fun nextTheme(mode: String): String = when (mode) {
    EqPrefs.THEME_SYSTEM -> EqPrefs.THEME_LIGHT
    EqPrefs.THEME_LIGHT -> EqPrefs.THEME_DARK
    else -> EqPrefs.THEME_SYSTEM
}

private fun themeIcon(mode: String) = when (mode) {
    EqPrefs.THEME_LIGHT -> Icons.Filled.LightMode
    EqPrefs.THEME_DARK -> Icons.Filled.DarkMode
    else -> Icons.Filled.BrightnessAuto
}

private fun freqLabel(hz: Int): String = when {
    hz == 0 -> "?"
    hz < 1000 -> "$hz Гц"
    else -> "${"%.1f".format(hz / 1000f)} кГц"
}
