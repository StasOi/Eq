package eq.app

import android.content.Context
import android.content.SharedPreferences

class EqPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)

    fun loadBandLevels(bands: Int): ShortArray =
        ShortArray(bands) { i -> sp.getInt(keyBand(i), 0).toShort() }

    fun saveBandLevel(band: Int, level: Short) {
        sp.edit().putInt(keyBand(band), level.toInt()).apply()
    }

    fun loadBoost(): Int = sp.getInt(KEY_BOOST, 0)

    fun saveBoost(mB: Int) {
        sp.edit().putInt(KEY_BOOST, mB).apply()
    }

    fun loadEnabled(): Boolean = sp.getBoolean(KEY_ENABLED, true)

    fun saveEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun keyBand(i: Int) = "band_$i"

    companion object {
        private const val KEY_BOOST = "boost_mB"
        private const val KEY_ENABLED = "enabled"
    }
}
