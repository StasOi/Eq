package eq.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import eq.app.ui.theme.EqTheme

class MainActivity : ComponentActivity() {

    private val visualizerPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            EqEngine.setVisualizer(this, granted)
        }

    private val notificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        EqEngine.attachFromIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EqEngine.attachFromIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val prefs = remember { EqPrefs(this) }
            var themeMode by remember { mutableStateOf(prefs.loadTheme()) }
            EqTheme(themeMode) {
                EqualizerScreen(
                    themeMode = themeMode,
                    onThemeChange = { mode ->
                        themeMode = mode
                        prefs.saveTheme(mode)
                    },
                    onVisualizerPermission = { visualizerPermission.launch(Manifest.permission.RECORD_AUDIO) }
                )
            }
        }
    }
}
