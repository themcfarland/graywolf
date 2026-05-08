package com.nw5w.graywolf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.nw5w.graywolf.audio.AudioPump
import com.nw5w.graywolf.jni.ModemBridge
import java.io.File

class GraywolfService : Service() {
    private val audioPump = AudioPump()

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NotificationManager::class.java)!!
        mgr.createNotificationChannel(
            NotificationChannel(
                getString(R.string.notification_channel_id),
                "graywolf foreground",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val notif: Notification = Notification.Builder(this, getString(R.string.notification_channel_id))
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
        val v = try {
            ModemBridge.modemVersion()
        } catch (t: Throwable) {
            Log.e(TAG, "modemVersion threw: $t")
            "ERROR"
        }
        Log.i(TAG, "modem cdylib version=$v")

        val socketPath = File(cacheDir, "graywolf-modem.sock").absolutePath
        val rc = ModemBridge.modemStart(socketPath, /* gainDb = */ -6.0f)
        if (rc != 0) {
            Log.e(TAG, "modemStart rc=$rc; aborting")
            stopSelf()
            return
        }
        val ready = ModemBridge.modemAwaitReady(10_000)
        Log.i(TAG, "modemAwaitReady=$ready")
        if (!ready) {
            Log.e(TAG, "modem not ready in 10s; aborting")
            ModemBridge.modemStop()
            stopSelf()
            return
        }
        audioPump.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        audioPump.stop()
        ModemBridge.modemStop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GraywolfService"
        private const val NOTIF_ID = 0x6757
    }
}
