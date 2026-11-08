package com.mitesh.glasslock

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import com.mitesh.glasslock.ui.LockActions
import com.mitesh.glasslock.ui.LockController
import com.mitesh.glasslock.ui.LockScreen

/**
 * The glass lock screen itself. It is shown on top of the Pixel's own (secure) lock screen;
 * swiping up hands over to the system fingerprint/PIN prompt, then gets out of the way.
 */
class LockActivity : ComponentActivity(), LockActions {

    private val controller = LockController()
    private lateinit var keyguard: KeyguardManager
    private lateinit var power: PowerManager
    private var preview = false
    private var pendingAfterUnlock: (() -> Unit)? = null
    private var unlockInFlight = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    controller.deviceUnlocked.value = !keyguard.isKeyguardLocked
                    controller.wakeTick.intValue++
                }
                Intent.ACTION_SCREEN_OFF -> {
                    controller.sleepTick.intValue++
                    if (preview) finishQuietly()
                }
                Intent.ACTION_USER_PRESENT -> {
                    controller.deviceUnlocked.value = true
                    // Unlocked some other way (for example the system gesture) while we were hidden.
                    if (!preview && !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) finishQuietly()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        isAlive = true
        setShowWhenLocked(true)
        keyguard = getSystemService(KeyguardManager::class.java)!!
        power = getSystemService(PowerManager::class.java)!!

        window.isNavigationBarContrastEnforced = false
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.lock_exit)
        }

        readIntent(intent)
        controller.deviceUnlocked.value = !keyguard.isKeyguardLocked

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (preview) finishQuietly()
            }
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent { LockScreen(controller, this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        pendingAfterUnlock = null
        unlockInFlight = false
        controller.deviceUnlocked.value = !keyguard.isKeyguardLocked
        controller.resetTick.intValue++
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations || isFinishing) return
        if (preview) {
            finishQuietly()
            return
        }
        // The user got past the lock screen some other way: don't linger in the background.
        if (power.isInteractive && !keyguard.isKeyguardLocked) finishQuietly()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already gone", e)
        }
        isAlive = false
        super.onDestroy()
    }

    private fun readIntent(intent: Intent?) {
        preview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true
    }

    // ----- LockActions -----

    override fun requestUnlock(after: (() -> Unit)?) {
        if (unlockInFlight) return
        pendingAfterUnlock = after
        if (preview || !keyguard.isKeyguardLocked) {
            completeUnlock()
            return
        }
        unlockInFlight = true
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                unlockInFlight = false
                completeUnlock()
            }

            override fun onDismissCancelled() {
                unlockInFlight = false
                pendingAfterUnlock = null
                controller.resetTick.intValue++
            }

            override fun onDismissError() {
                unlockInFlight = false
                if (!keyguard.isKeyguardLocked) {
                    completeUnlock()
                } else {
                    pendingAfterUnlock = null
                    controller.resetTick.intValue++
                }
            }
        })
    }

    override fun openCamera() {
        if (keyguard.isKeyguardLocked) {
            try {
                // The secure camera opens straight over the lock screen, no unlock needed.
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No secure camera", e)
            }
        }
        requestUnlock {
            try {
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Log.w(TAG, "No camera app", e)
            }
        }
    }

    override fun openNotification(item: LockNotification) {
        requestUnlock {
            val pi = item.contentIntent
            if (pi != null) {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                try {
                    pi.send(this, 0, null, null, null, null, options.toBundle())
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open notification", e)
                }
            } else {
                packageManager.getLaunchIntentForPackage(item.packageName)?.let {
                    try {
                        startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not open app", e)
                    }
                }
            }
            if (item.autoCancel) NotificationService.cancel(item.key)
        }
    }

    override fun dismissNotification(item: LockNotification) {
        NotificationService.cancel(item.key)
    }

    private fun completeUnlock() {
        val action = pendingAfterUnlock
        pendingAfterUnlock = null
        if (action != null) {
            try {
                action()
            } catch (e: Exception) {
                Log.w(TAG, "Post-unlock action failed", e)
            }
        }
        finishQuietly()
    }

    private fun finishQuietly() {
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.lock_exit)
        }
    }

    companion object {
        private const val TAG = "GlassLock"
        const val EXTRA_PREVIEW = "preview"

        @Volatile
        var isAlive = false
            private set

        fun launch(context: Context, preview: Boolean = false) {
            val intent = Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PREVIEW, preview)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION,
                )
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not show lock screen", e)
            }
        }
    }
}
