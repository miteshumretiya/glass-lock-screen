package com.mitesh.glasslock

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** What the iOS-style status row shows: carrier, signal, Wi-Fi and battery. */
data class DeviceStatus(
    val carrier: String,
    val signal: Int,
    val wifi: Boolean,
    val battery: Int,
    val charging: Boolean,
) {
    companion object {
        fun read(context: Context): DeviceStatus {
            var carrier = ""
            var signal = -1
            try {
                val tm = context.getSystemService(TelephonyManager::class.java)
                if (tm != null) {
                    carrier = tm.networkOperatorName.orEmpty().ifBlank { tm.simOperatorName.orEmpty() }
                    if (tm.simState == TelephonyManager.SIM_STATE_READY) {
                        signal = tm.signalStrength?.level ?: 0
                    }
                }
            } catch (e: Exception) {
                // Telephony not available (tablet, no SIM); leave blank.
            }
            val wifi = try {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                if (cm == null) {
                    false
                } else {
                    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
            } catch (e: Exception) {
                false
            }
            val bm = context.getSystemService(BatteryManager::class.java)
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val charging = bm?.isCharging == true
            return DeviceStatus(carrier, signal, wifi, if (level in 0..100) level else -1, charging)
        }
    }
}

/** Back camera flashlight, toggled from the lock screen without the camera permission. */
class Torch(context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = findTorchCamera()
    val on = mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) on.value = enabled
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) on.value = false
        }
    }

    fun start() {
        try {
            manager?.registerTorchCallback(callback, handler)
        } catch (e: Exception) {
            // Ignore: the button simply won't reflect outside changes.
        }
    }

    fun stop() {
        try {
            manager?.unregisterTorchCallback(callback)
        } catch (e: Exception) {
            // Already unregistered.
        }
    }

    private fun findTorchCamera(): String? {
        val m = manager ?: return null
        return try {
            m.cameraIdList.firstOrNull { id ->
                val c = m.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            null
        }
    }

    fun toggle() {
        val id = cameraId ?: return
        try {
            manager?.setTorchMode(id, !on.value)
        } catch (e: Exception) {
            // Camera in use by another app.
        }
    }
}

class MediaInfo(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val duration: Long,
    private val position: Long,
    private val positionTime: Long,
    private val speed: Float,
    val controller: MediaController,
) {
    fun elapsedAt(nowElapsed: Long): Long {
        val d = duration.coerceAtLeast(0L)
        val raw = if (!playing || positionTime <= 0L) position else position + ((nowElapsed - positionTime) * speed).toLong()
        return raw.coerceIn(0L, d)
    }
}

/** Follows whatever is playing (Spotify, YouTube Music...) for the Now Playing card. */
class NowPlayingTracker(private val context: Context) {
    val info = mutableStateOf<MediaInfo?>(null)
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick() }

    fun start() {
        try {
            sessions?.addOnActiveSessionsChangedListener(sessionsListener, NotificationService.component(context), handler)
        } catch (e: SecurityException) {
            return // Notification access not granted yet.
        }
        pick()
    }

    fun stop() {
        try {
            sessions?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // Not registered.
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    private fun pick() {
        val list: List<MediaController> = try {
            sessions?.getActiveSessions(NotificationService.component(context)).orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull {
                val s = it.playbackState?.state
                it.metadata != null && s != null && s != PlaybackState.STATE_NONE &&
                    s != PlaybackState.STATE_STOPPED && s != PlaybackState.STATE_ERROR
            }
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) {
            info.value = null
            return
        }
        val ps = c.playbackState
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        info.value = MediaInfo(
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
            art = art?.asImageBitmap(),
            playing = ps?.state == PlaybackState.STATE_PLAYING || ps?.state == PlaybackState.STATE_BUFFERING,
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            position = ps?.position ?: 0L,
            positionTime = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed ?: 1f,
            controller = c,
        )
    }
}
