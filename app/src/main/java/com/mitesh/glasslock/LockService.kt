package com.mitesh.glasslock

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.mitesh.glasslock.ui.WallpaperStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps running in the background and puts the glass lock screen in place
 * every time the display turns off (or on, depending on the setting).
 */
class LockService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = Prefs(context)
            if (!prefs.enabled) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (prefs.launchOnScreenOff) showLock()
                    preloadWallpaper()
                }
                Intent.ACTION_SCREEN_ON -> {
                    val keyguard = getSystemService(KeyguardManager::class.java)
                    val locked = keyguard?.isKeyguardLocked == true
                    if (locked && (!prefs.launchOnScreenOff || !LockActivity.isAlive)) showLock()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        preloadWallpaper()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs(this).enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already gone", e)
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showLock() {
        if (inCall()) return
        if (!Settings.canDrawOverlays(this)) return
        LockActivity.launch(this)
    }

    private fun inCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }

    private fun preloadWallpaper() {
        scope.launch {
            runCatching {
                val bounds = getSystemService(WindowManager::class.java)!!.maximumWindowMetrics.bounds
                val w = minOf(bounds.width(), bounds.height())
                val h = maxOf(bounds.width(), bounds.height())
                WallpaperStore.load(this@LockService, w, h, Prefs(this@LockService).wallpaperVersion)
            }
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)!!
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(channel)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "GlassLock"
        private const val CHANNEL_ID = "lock_service"
        private const val NOTIFICATION_ID = 41

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LockService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not start lock service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockService::class.java))
        }
    }
}
