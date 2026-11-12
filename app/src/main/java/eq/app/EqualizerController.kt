package eq.app

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

class EqualizerController {

    private var equalizer: Equalizer? = null
    private var enhancer: LoudnessEnhancer? = null

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
        try {
            equalizer = Equalizer(0, 0)
            equalizer?.enabled = true
        } catch (t: Throwable) {
            error = t.message ?: "Equalizer unavailable"
            equalizer = null
        }
        try {
            enhancer = LoudnessEnhancer(0)
        } catch (t: Throwable) {
            enhancer = null
        }
    }

    fun isEnabled(): Boolean = equalizer?.enabled ?: false

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
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

    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Throwable) {
        }
        try {
            enhancer?.release()
        } catch (_: Throwable) {
        }
        equalizer = null
        enhancer = null
    }
}
