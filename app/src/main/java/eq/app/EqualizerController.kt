package eq.app

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer

class EqualizerController {

    private var equalizer: Equalizer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var visualizer: Visualizer? = null

    var error: String? = null
        private set

    val numberOfBands: Int
        get() = equalizer?.numberOfBands?.toInt() ?: 0

    val bandLevelRange: ShortArray
        get() = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)

    val centerFreqs: IntArray
        get() {
            val eq = equalizer ?: return IntArray(0)
            return Array(eq.numberOfBands.toInt()) { i ->
                eq.getCenterFreq(i.toShort()) / 1000
            }.toIntArray()
        }

    fun start() {
        error = null
        try {
            equalizer = Equalizer(0, 0)
        } catch (t: Throwable) {
            error = t.message ?: "Equalizer unavailable"
            equalizer = null
        }
        try {
            enhancer = LoudnessEnhancer(0)
        } catch (_: Throwable) {
            enhancer = null
        }
        try {
            bassBoost = BassBoost(0, 0)
        } catch (_: Throwable) {
            bassBoost = null
        }
        try {
            virtualizer = Virtualizer(0, 0)
        } catch (_: Throwable) {
            virtualizer = null
        }
    }

    fun restart(): Boolean {
        release()
        start()
        return error == null && numberOfBands > 0
    }

    fun setEnabled(enabled: Boolean) {
        try { equalizer?.enabled = enabled } catch (_: Throwable) {}
        try { bassBoost?.enabled = enabled } catch (_: Throwable) {}
        try { virtualizer?.enabled = enabled } catch (_: Throwable) {}
        try { enhancer?.enabled = enabled } catch (_: Throwable) {}
    }

    fun getBandLevel(band: Int): Short =
        equalizer?.getBandLevel(band.toShort()) ?: 0

    fun setBandLevel(band: Int, level: Short) {
        equalizer?.setBandLevel(band.toShort(), level)
    }

    fun setBoost(mB: Int) {
        enhancer?.setTargetGain(mB)
    }

    fun isBoostAvailable(): Boolean = enhancer != null

    fun setBassStrength(percent: Int) {
        bassBoost?.setStrength((percent.coerceIn(0, 100) * 10).toShort())
    }

    fun isBassAvailable(): Boolean = bassBoost != null

    fun setVirtualStrength(percent: Int) {
        virtualizer?.setStrength((percent.coerceIn(0, 100) * 10).toShort())
    }

    fun isVirtualAvailable(): Boolean = virtualizer != null

    fun startVisualizer(onWaveform: (ByteArray) -> Unit): Boolean {
        if (visualizer != null) return true
        return try {
            val v = Visualizer(0)
            val sizeRange = Visualizer.getCaptureSizeRange()
            v.captureSize = sizeRange[1]
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform != null) onWaveform(waveform)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )
            v.enabled = true
            visualizer = v
            true
        } catch (_: Throwable) {
            visualizer = null
            false
        }
    }

    fun stopVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
    }

    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Throwable) {
        }
        try {
            bassBoost?.enabled = false
            bassBoost?.release()
        } catch (_: Throwable) {
        }
        try {
            virtualizer?.enabled = false
            virtualizer?.release()
        } catch (_: Throwable) {
        }
        try {
            enhancer?.release()
        } catch (_: Throwable) {
        }
        stopVisualizer()
        equalizer = null
        enhancer = null
        bassBoost = null
        virtualizer = null
    }
}
