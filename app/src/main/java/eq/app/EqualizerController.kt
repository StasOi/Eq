package eq.app

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer

class EqualizerController(private val sessionId: Int = 0) {

    private var equalizer: Equalizer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var visualizer: Visualizer? = null

    var error: String? = null
        private set

    var diag: String = ""
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
        val d = StringBuilder()
        try {
            equalizer = Equalizer(0, sessionId)
            d.append("eq ok ctrl=").append(equalizer?.hasControl()).append("; ")
        } catch (t: Throwable) {
            error = t.message ?: "Equalizer unavailable"
            equalizer = null
            d.append("eq ERR ").append(t.message).append("; ")
        }
        try {
            enhancer = LoudnessEnhancer(sessionId)
            d.append("enh ok ctrl=").append(enhancer?.hasControl()).append("; ")
        } catch (t: Throwable) {
            enhancer = null
            d.append("enh ERR ").append(t.message).append("; ")
        }
        try {
            bassBoost = BassBoost(0, sessionId)
            d.append("bass ok ctrl=").append(bassBoost?.hasControl()).append("; ")
        } catch (t: Throwable) {
            bassBoost = null
            d.append("bass ERR ").append(t.message).append("; ")
        }
        try {
            virtualizer = Virtualizer(0, sessionId)
            d.append("virt ok ctrl=").append(virtualizer?.hasControl()).append("; ")
        } catch (t: Throwable) {
            virtualizer = null
            d.append("virt ERR ").append(t.message).append("; ")
        }
        diag = "s$sessionId: $d"
    }

    fun restart(): Boolean {
        release()
        start()
        return error == null && numberOfBands > 0
    }

    fun setEnabled(enabled: Boolean) {
        val d = StringBuilder()
        try {
            equalizer?.enabled = enabled
            d.append("eq=").append(equalizer?.enabled)
        } catch (t: Throwable) {
            d.append("eq=E")
        }
        try {
            bassBoost?.enabled = enabled
            d.append(" bass=").append(bassBoost?.enabled)
        } catch (t: Throwable) {
            d.append(" bass=E")
        }
        try {
            virtualizer?.enabled = enabled
            d.append(" virt=").append(virtualizer?.enabled)
        } catch (t: Throwable) {
            d.append(" virt=E")
        }
        try {
            enhancer?.enabled = enabled
            d.append(" enh=").append(enhancer?.enabled)
        } catch (t: Throwable) {
            d.append(" enh=E")
        }
        diag = diag.substringBefore(" set{") + " set{" + d.toString() + " }"
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
