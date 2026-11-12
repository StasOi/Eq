package eq.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eq.app.ui.theme.EqTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen() {
    EqTheme {
        val context = androidx.compose.ui.platform.LocalContext.current
        val controller = remember { EqualizerController() }
        val prefs = remember { EqPrefs(context) }

        var bands by remember { mutableStateOf(0) }
        var freqs by remember { mutableStateOf(IntArray(0)) }
        var range by remember { mutableStateOf(shortArrayOf(-1500, 1500)) }
        var levels by remember { mutableStateOf(ShortArray(0)) }
        var boost by remember { mutableIntStateOf(0) }
        var enabled by remember { mutableStateOf(true) }
        var initialized by remember { mutableStateOf(false) }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            controller.start()
            if (controller.error == null && controller.numberOfBands > 0) {
                bands = controller.numberOfBands
                freqs = controller.centerFreqs
                range = controller.bandLevelRange
                levels = prefs.loadBandLevels(bands).let { saved ->
                    ShortArray(bands) { i -> saved[i].coerceIn(range[0], range[1]) }
                }
                boost = prefs.loadBoost()
                enabled = prefs.loadEnabled()
                for (i in 0 until bands) controller.setBandLevel(i, levels[i])
                controller.setBoost(boost)
                controller.setEnabled(enabled)
                initialized = true
            }
        }

        androidx.compose.runtime.DisposableEffect(Unit) {
            onDispose { controller.release() }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(title = { Text("Eq — Эквалайзер") })
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
                val error = controller.error
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
                } else if (initialized && bands > 0) {
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
                                checked = enabled,
                                onCheckedChange = {
                                    enabled = it
                                    controller.setEnabled(it)
                                    prefs.saveEnabled(it)
                                }
                            )
                        }
                    }

                    for (i in 0 until bands) {
                        BandRow(
                            freqHz = freqs.getOrElse(i) { 0 },
                            minLevel = range[0].toInt(),
                            maxLevel = range[1].toInt(),
                            level = levels[i].toInt(),
                            onChange = { value ->
                                levels[i] = value.toShort()
                                controller.setBandLevel(i, value.toShort())
                                prefs.saveBandLevel(i, value.toShort())
                            }
                        )
                    }

                    if (controller.isBoostAvailable()) {
                        BoostRow(
                            boost = boost,
                            onChange = { value ->
                                boost = value
                                controller.setBoost(value)
                                prefs.saveBoost(value)
                            }
                        )
                    } else {
                        Text(
                            "Общее усиление громкости не поддерживается устройством",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Загрузка…")
                    }
                }
            }
        }
    }
}

@Composable
private fun BandRow(
    freqHz: Int,
    minLevel: Int,
    maxLevel: Int,
    level: Int,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(freqLabel(freqHz), style = MaterialTheme.typography.labelMedium)
            Box(modifier = Modifier.weight(1f))
            Text("$level мБ", style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = level.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = minLevel.toFloat()..maxLevel.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )
    }
}

@Composable
private fun BoostRow(
    boost: Int,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Общее усиление", style = MaterialTheme.typography.labelMedium)
            Box(modifier = Modifier.weight(1f))
            Text("${"%.1f".format(boost / 1000f)} дБ", style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = boost.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..15000f,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )
    }
}

private fun freqLabel(hz: Int): String = when {
    hz == 0 -> "?"
    hz < 1000 -> "$hz Гц"
    else -> "${"%.1f".format(hz / 1000f)} кГц"
}

