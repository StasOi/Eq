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

    fun loadBass(): Int = sp.getInt(KEY_BASS, 0)

    fun saveBass(percent: Int) {
        sp.edit().putInt(KEY_BASS, percent).apply()
    }

    fun loadVirtual(): Int = sp.getInt(KEY_VIRTUAL, 0)

    fun saveVirtual(percent: Int) {
        sp.edit().putInt(KEY_VIRTUAL, percent).apply()
    }

    fun loadEnabled(): Boolean = sp.getBoolean(KEY_ENABLED, true)

    fun saveEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun loadPreset(): String = sp.getString(KEY_PRESET, PRESET_USER) ?: PRESET_USER

    fun savePreset(name: String) {
        sp.edit().putString(KEY_PRESET, name).apply()
    }

    fun loadVisualizer(): Boolean = sp.getBoolean(KEY_VISUALIZER, false)

    fun saveVisualizer(active: Boolean) {
        sp.edit().putBoolean(KEY_VISUALIZER, active).apply()
    }

    fun loadTheme(): String = sp.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM

    fun saveTheme(mode: String) {
        sp.edit().putString(KEY_THEME, mode).apply()
    }

    private fun keyBand(i: Int) = "band_$i"

    companion object {
        const val PRESET_USER = "custom"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        private const val KEY_BOOST = "boost_mB"
        private const val KEY_BASS = "bass_percent"
        private const val KEY_VIRTUAL = "virtual_percent"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PRESET = "preset"
        private const val KEY_VISUALIZER = "visualizer"
        private const val KEY_THEME = "theme"
    }
}
