package eq.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

object EqEngine {

    const val ACTION_SET_ENABLED = "eq.app.action.SET_ENABLED"
    const val EXTRA_ENABLED = "eq.app.extra.ENABLED"

    var ready by mutableStateOf(false)
        private set
    var enabled by mutableStateOf(false)
        private set
    var bands by mutableIntStateOf(0)
        private set
    var freqs by mutableStateOf(IntArray(0))
        private set
    var range by mutableStateOf(shortArrayOf(-1500, 1500))
        private set
    var levels by mutableStateOf(ShortArray(0))
        private set
    var boost by mutableIntStateOf(0)
        private set
    var bass by mutableIntStateOf(0)
        private set
    var virtual by mutableIntStateOf(0)
        private set
    var preset by mutableStateOf(EqPrefs.PRESET_USER)
        private set
    var waveform by mutableStateOf(ByteArray(0))
        private set
    var visualizerActive by mutableStateOf(false)
        private set
    var boostAvailable by mutableStateOf(false)
        private set
    var bassAvailable by mutableStateOf(false)
        private set
    var virtualAvailable by mutableStateOf(false)
        private set

    private var controller: EqualizerController? = null
    private var prefs: EqPrefs? = null

    val error: String?
        get() = controller?.error

    val presetNames: List<String>
        get() = PRESET_CURVES.keys.toList()

    fun start(context: Context) {
        if (controller != null) return
        val appContext = context.applicationContext
        val p = EqPrefs(appContext)
        prefs = p
        val c = EqualizerController()
        c.start()
        controller = c
        if (c.error == null && c.numberOfBands > 0) {
            bands = c.numberOfBands
            freqs = c.centerFreqs
            range = c.bandLevelRange
            levels = p.loadBandLevels(bands).let { saved ->
                ShortArray(bands) { i -> saved[i].coerceIn(range[0], range[1]) }
            }
            boost = p.loadBoost()
            bass = p.loadBass()
            virtual = p.loadVirtual()
            preset = p.loadPreset()
            enabled = p.loadEnabled()
            for (i in 0 until bands) c.setBandLevel(i, levels[i])
            c.setBoost(boost)
            c.setBassStrength(bass)
            c.setVirtualStrength(virtual)
            c.setEnabled(enabled)
            boostAvailable = c.isBoostAvailable()
            bassAvailable = c.isBassAvailable()
            virtualAvailable = c.isVirtualAvailable()
            ready = true
            if (p.loadVisualizer() &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                setVisualizer(appContext, true)
            }
        }
        if (enabled) startService(appContext)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        prefs?.saveEnabled(value)
        controller?.setEnabled(value)
        val appContext = context.applicationContext
        if (value) {
            startService(appContext)
        } else {
            appContext.stopService(Intent(appContext, EqService::class.java))
        }
    }

    fun setBandLevel(band: Int, level: Short) {
        if (band !in levels.indices) return
        levels = levels.copyOf().also { it[band] = level }
        prefs?.saveBandLevel(band, level)
        if (preset != EqPrefs.PRESET_USER) {
            preset = EqPrefs.PRESET_USER
            prefs?.savePreset(EqPrefs.PRESET_USER)
        }
        controller?.setBandLevel(band, level)
    }

    fun updateBoost(value: Int) {
        boost = value
        prefs?.saveBoost(value)
        controller?.setBoost(value)
    }

    fun updateBass(value: Int) {
        bass = value
        prefs?.saveBass(value)
        controller?.setBassStrength(value)
    }

    fun updateVirtual(value: Int) {
        virtual = value
        prefs?.saveVirtual(value)
        controller?.setVirtualStrength(value)
    }

    fun applyPreset(name: String) {
        val p = prefs ?: return
        if (name !in PRESET_CURVES) return
        preset = name
        p.savePreset(name)
        val gains = presetGains(name, bands, range)
        levels = gains
        for (i in gains.indices) {
            controller?.setBandLevel(i, gains[i])
            p.saveBandLevel(i, gains[i])
        }
    }

    fun setVisualizer(context: Context, active: Boolean) {
        if (active) {
            val c = controller ?: return
            visualizerActive = c.startVisualizer { w -> waveform = w }
        } else {
            controller?.stopVisualizer()
            visualizerActive = false
            waveform = ByteArray(0)
        }
        prefs?.saveVisualizer(active && visualizerActive)
    }

    fun presetGains(name: String, bandCount: Int, bandRange: ShortArray): ShortArray {
        val curve = PRESET_CURVES[name] ?: return ShortArray(bandCount) { i ->
            levels.getOrElse(i) { 0 }
        }
        val scale = bandRange[1].toInt() / 1500f
        val out = ShortArray(bandCount)
        for (i in 0 until bandCount) {
            val t = if (bandCount == 1) 2f else (i.toFloat() / (bandCount - 1)) * 4f
            val idx = t.toInt().coerceIn(0, 3)
            val frac = t - idx
            val db = curve[idx] * (1f - frac) + curve[idx + 1] * frac
            out[i] = (db * 100f * scale).toInt().toShort()
        }
        return out
    }

    private fun startService(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, EqService::class.java))
    }

    private val PRESET_CURVES: Map<String, FloatArray> = mapOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to floatArrayOf(6f, 5f, 3f, 1f, 0f),
        "Rock" to floatArrayOf(5f, 4f, 2f, 0f, 4f),
        "Pop" to floatArrayOf(-1f, 3f, 4f, 2f, -1f),
        "Jazz" to floatArrayOf(3f, 2f, -1f, 2f, 3f),
        "Classical" to floatArrayOf(0f, 0f, 0f, 2f, 3f),
        "Vocal" to floatArrayOf(-2f, 2f, 4f, 3f, -1f),
        "Treble" to floatArrayOf(0f, 1f, 3f, 5f, 6f)
    )
}
