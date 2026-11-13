package eq.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    var diag by mutableStateOf("")
        private set

    private var controller: EqualizerController? = null
    private val sessionControllers = LinkedHashMap<Int, EqualizerController>()
    private var prefs: EqPrefs? = null
    private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private var restartAttempts = 0
    private var sessionReceiver: BroadcastReceiver? = null
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private var sessionReflectionOk = true

    private val restartRunnable = Runnable { restartEffects() }

    val error: String?
        get() = controller?.error

    val presetNames: List<String>
        get() = PRESET_CURVES.keys.toList()

    fun start(context: Context) {
        if (controller != null) return
        val appContext = context.applicationContext
        this.appContext = appContext
        val p = EqPrefs(appContext)
        prefs = p
        val c = EqualizerController(0)
        c.start()
        controller = c
        if (c.error == null && c.numberOfBands > 0) {
            loadFromPrefs(c)
            applyToController(c, enabled)
            ready = true
            if (p.loadVisualizer() && hasAudioPermission()) {
                setVisualizer(appContext, true)
            }
        }
        registerDeviceCallback(appContext)
        registerSessionReceiver(appContext)
        registerPlaybackCallback(appContext)
        if (enabled) {
            startService(appContext)
            attachToActiveSessions()
            if (sessionControllers.isNotEmpty()) c.setEnabled(false)
        }
        updateDiag()
    }

    private fun loadFromPrefs(c: EqualizerController) {
        val p = prefs ?: return
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
    }

    private fun applyToController(c: EqualizerController, enable: Boolean) {
        for (i in 0 until bands) c.setBandLevel(i, levels[i])
        c.setBoost(boost)
        c.setBassStrength(bass)
        c.setVirtualStrength(virtual)
        c.setEnabled(enable)
        if (c === controller) {
            boostAvailable = c.isBoostAvailable()
            bassAvailable = c.isBassAvailable()
            virtualAvailable = c.isVirtualAvailable()
        }
    }

    private fun applyLive(block: (EqualizerController) -> Unit) {
        controller?.let(block)
        for (sc in sessionControllers.values) block(sc)
    }

    private fun hasAudioPermission(): Boolean {
        val ctx = appContext ?: return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun registerDeviceCallback(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val callback = object : AudioDeviceCallback() {
            private var initialSyncDone = false

            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (!initialSyncDone) {
                    initialSyncDone = true
                    return
                }
                if (addedDevices.any { it.isSink }) scheduleRestart()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (removedDevices.any { it.isSink }) scheduleRestart()
            }
        }
        am.registerAudioDeviceCallback(callback, handler)
    }

    private fun registerSessionReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ||
                    action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
                ) {
                    handleSessionBroadcast(intent)
                }
            }
        }
        sessionReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        log("session receiver registered")
    }

    private fun handleSessionBroadcast(intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "?"
        val open = intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        log("broadcast open=$open session=$sessionId pkg=$pkg")
        if (sessionId <= 0) return
        if (open) {
            attachSession(sessionId)
            if (enabled && sessionControllers.isNotEmpty()) {
                controller?.setEnabled(false)
            }
        } else {
            detachSession(sessionId)
            if (enabled && sessionControllers.isEmpty()) {
                controller?.setEnabled(true)
            }
        }
        updateDiag()
    }

    private fun attachSession(sessionId: Int) {
        if (sessionId <= 0 || sessionControllers.containsKey(sessionId)) return
        val sc = EqualizerController(sessionId)
        sc.start()
        if (sc.error != null || sc.numberOfBands <= 0) {
            log("attach s$sessionId failed err=${sc.error} bands=${sc.numberOfBands}")
            sc.release()
            return
        }
        sessionControllers[sessionId] = sc
        applyToController(sc, enabled)
        log("attached session=$sessionId bands=${sc.numberOfBands}")
    }

    private fun detachSession(sessionId: Int) {
        val sc = sessionControllers.remove(sessionId) ?: return
        sc.release()
        log("detached session=$sessionId remaining=${sessionControllers.size}")
    }

    private fun registerPlaybackCallback(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val cb = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                scanPlaybackConfigs(configs)
            }
        }
        playbackCallback = cb
        am.registerAudioPlaybackCallback(cb, handler)
        log("playback callback registered")
    }

    private fun attachToActiveSessions() {
        if (!enabled || !sessionReflectionOk) return
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        scanPlaybackConfigs(am.activePlaybackConfigurations)
    }

    private fun scanPlaybackConfigs(configs: List<AudioPlaybackConfiguration>) {
        if (!enabled || !sessionReflectionOk) return
        val seen = mutableSetOf<Int>()
        for (cfg in configs) {
            val sid = sessionIdOf(cfg) ?: return
            if (sid > 0) {
                seen.add(sid)
                if (!sessionControllers.containsKey(sid)) {
                    log("scan: new session=$sid")
                    attachSession(sid)
                }
            }
        }
        if (seen.isNotEmpty()) {
            val stale = sessionControllers.keys.filter { it !in seen }
            for (sid in stale) detachSession(sid)
            if (stale.isNotEmpty()) {
                if (sessionControllers.isEmpty() && enabled) {
                    controller?.setEnabled(true)
                }
                updateDiag()
            }
        }
    }

    private fun sessionIdOf(cfg: AudioPlaybackConfiguration): Int? = try {
        val m = AudioPlaybackConfiguration::class.java.getMethod("getAudioSessionId")
        m.isAccessible = true
        m.invoke(cfg) as? Int
    } catch (t: Throwable) {
        sessionReflectionOk = false
        log("getAudioSessionId unavailable: ${t.message}")
        null
    }

    private fun updateDiag() {
        val parts = mutableListOf<String>()
        controller?.let { parts.add(it.diag) }
        for ((sid, sc) in sessionControllers) parts.add("s$sid: ${sc.diag}")
        diag = parts.joinToString(" | ")
        log("diag: $diag")
    }

    private fun log(message: String) {
        Log.d("EqEngine", message)
    }

    private fun scheduleRestart() {
        restartAttempts = 0
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, RESTART_DELAY_MS)
    }

    private fun restartEffects() {
        val c = controller ?: return
        if (!c.restart()) {
            if (restartAttempts < RESTART_MAX_ATTEMPTS) {
                restartAttempts++
                handler.postDelayed(restartRunnable, RESTART_RETRY_DELAY_MS)
            }
            return
        }
        restartAttempts = 0
        if (!ready) {
            loadFromPrefs(c)
        } else {
            bands = c.numberOfBands
            freqs = c.centerFreqs
            range = c.bandLevelRange
            levels = ShortArray(bands) { i -> levels.getOrElse(i) { 0 }.coerceIn(range[0], range[1]) }
        }
        applyToController(c, enabled && sessionControllers.isEmpty())
        ready = true
        if (visualizerActive) {
            val ctx = appContext
            if (ctx != null && hasAudioPermission()) {
                setVisualizer(ctx, true)
            } else {
                visualizerActive = false
                waveform = ByteArray(0)
                prefs?.saveVisualizer(false)
            }
        }
        if (enabled) {
            appContext?.let { startService(it) }
        }
        updateDiag()
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        prefs?.saveEnabled(value)
        val appContext = context.applicationContext
        if (value) {
            attachToActiveSessions()
            for (sc in sessionControllers.values) applyToController(sc, true)
            controller?.let { applyToController(it, sessionControllers.isEmpty()) }
            startService(appContext)
        } else {
            controller?.setEnabled(false)
            for (sc in sessionControllers.values) sc.setEnabled(false)
            appContext.stopService(Intent(appContext, EqService::class.java))
        }
        updateDiag()
    }

    fun setBandLevel(band: Int, level: Short) {
        if (band !in levels.indices) return
        levels = levels.copyOf().also { it[band] = level }
        prefs?.saveBandLevel(band, level)
        if (preset != EqPrefs.PRESET_USER) {
            preset = EqPrefs.PRESET_USER
            prefs?.savePreset(EqPrefs.PRESET_USER)
        }
        applyLive { it.setBandLevel(band, level) }
    }

    fun updateBoost(value: Int) {
        boost = value
        prefs?.saveBoost(value)
        applyLive { it.setBoost(value) }
    }

    fun updateBass(value: Int) {
        bass = value
        prefs?.saveBass(value)
        applyLive { it.setBassStrength(value) }
    }

    fun updateVirtual(value: Int) {
        virtual = value
        prefs?.saveVirtual(value)
        applyLive { it.setVirtualStrength(value) }
    }

    fun applyPreset(name: String) {
        val p = prefs ?: return
        if (name !in PRESET_CURVES) return
        preset = name
        p.savePreset(name)
        val gains = presetGains(name, bands, range)
        levels = gains
        for (i in gains.indices) {
            applyLive { c -> c.setBandLevel(i, gains[i]) }
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

    private const val RESTART_DELAY_MS = 600L
    private const val RESTART_RETRY_DELAY_MS = 2000L
    private const val RESTART_MAX_ATTEMPTS = 3

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
