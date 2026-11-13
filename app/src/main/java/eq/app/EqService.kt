package eq.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class EqService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (intent?.action == EqEngine.ACTION_SET_ENABLED) {
            EqEngine.setEnabled(this, intent.getBooleanExtra(EqEngine.EXTRA_ENABLED, false))
        }
        if (!EqEngine.enabled) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Эквалайзер", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val toggleAction = if (EqEngine.enabled) {
            PendingIntent.getService(
                this, 1,
                Intent(this, EqService::class.java)
                    .setAction(EqEngine.ACTION_SET_ENABLED)
                    .putExtra(EqEngine.EXTRA_ENABLED, false),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getService(
                this, 2,
                Intent(this, EqService::class.java)
                    .setAction(EqEngine.ACTION_SET_ENABLED)
                    .putExtra(EqEngine.EXTRA_ENABLED, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_eq)
            .setContentTitle(if (EqEngine.enabled) "Eq — эквалайзер активен" else "Eq")
            .setContentText("Глобальный системный эквалайзер")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
        builder.addAction(
            0,
            if (EqEngine.enabled) "Выключить" else "Включить",
            toggleAction
        )
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "eq_status"
        private const val NOTIFICATION_ID = 1
    }
}
