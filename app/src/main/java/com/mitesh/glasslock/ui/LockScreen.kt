package com.mitesh.glasslock.ui

import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mitesh.glasslock.DeviceStatus
import com.mitesh.glasslock.LockNotification
import com.mitesh.glasslock.MediaInfo
import com.mitesh.glasslock.NotificationService
import com.mitesh.glasslock.NowPlayingTracker
import com.mitesh.glasslock.Prefs
import com.mitesh.glasslock.Torch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Signals from the activity (screen on/off, unlock cancelled) into the UI. */
class LockController {
    val wakeTick = mutableIntStateOf(0)
    val sleepTick = mutableIntStateOf(0)
    val resetTick = mutableIntStateOf(0)
    val deviceUnlocked = mutableStateOf(false)
}

interface LockActions {
    fun requestUnlock(after: (() -> Unit)? = null)
    fun openCamera()
    fun openNotification(item: LockNotification)
    fun dismissNotification(item: LockNotification)
}

private val DateStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.SemiBold,
    fontSize = 21.sp,
    color = Color.White.copy(alpha = 0.92f),
    shadow = Shadow(Color.Black.copy(alpha = 0.25f), Offset(0f, 1f), 8f),
)

private val HintStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    color = Color.White.copy(alpha = 0.62f),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockScreen(controller: LockController, actions: LockActions) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { Prefs(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val wakeKey = controller.wakeTick.intValue

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val screenW = maxWidth
        val screenH = maxHeight

        val version = remember(wakeKey) { prefs.wallpaperVersion }
        val wallpaper by produceState(WallpaperStore.cached(wPx, hPx, version), wPx, hPx, version) {
            value = runCatching { WallpaperStore.load(context, wPx, hPx, version) }.getOrNull() ?: value
        }

        val light = rememberLight()
        val items by NotificationService.items.collectAsState()
        val showContent = remember(wakeKey) { prefs.showContent }

        val tracker = remember { NowPlayingTracker(context) }
        LifecycleStartEffect(tracker) {
            tracker.start()
            onStopOrDispose { tracker.stop() }
        }
        val media = tracker.info.value
        var lastMedia by remember { mutableStateOf<MediaInfo?>(null) }
        LaunchedEffect(media) { if (media != null) lastMedia = media }

        val torch = remember { Torch(context) }
        LifecycleStartEffect(torch) {
            torch.start()
            onStopOrDispose { torch.stop() }
        }

        val initialStatus = remember { DeviceStatus.read(context) }
        val status by produceState(initialStatus, wakeKey) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = DeviceStatus.read(context)
                    delay(10_000L)
                }
            }
        }
        val now by produceState(System.currentTimeMillis(), wakeKey) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = System.currentTimeMillis()
                    delay((60_000L - value % 60_000L).coerceAtLeast(250L))
                }
            }
        }
        val is24 = remember(wakeKey) { DateFormat.is24HourFormat(context) }
        val locale = remember(wakeKey) { Locale.getDefault() }
        val timeText = remember(now, is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", locale).format(Date(now)) }
        val dateText = remember(now, locale) {
            SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale).format(Date(now))
        }

        var expanded by remember { mutableStateOf(false) }
        val drag = remember { Animatable(0f) }
        val wake = remember { Animatable(1f) }
        val scope = rememberCoroutineScope()
        val unlockDistance = hPx * 0.36f
        val progress: () -> Float = { (-drag.value / unlockDistance).coerceIn(0f, 1f) }

        LaunchedEffect(wakeKey) {
            if (wakeKey > 0) {
                drag.snapTo(0f)
                wake.snapTo(0f)
                wake.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
        }
        LaunchedEffect(controller.sleepTick.intValue) {
            expanded = false
            drag.snapTo(0f)
        }
        LaunchedEffect(controller.resetTick.intValue) {
            if (controller.resetTick.intValue > 0) {
                drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 260f))
            }
        }

        val unlockedLook by remember { derivedStateOf { controller.deviceUnlocked.value || progress() > 0.55f } }
        val hasBottom = items.isNotEmpty() || media != null
        val clockHeight by animateDpAsState(
            targetValue = when {
                expanded -> screenH * 0.12f
                hasBottom -> screenH * 0.19f
                else -> screenH * 0.25f
            },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 180f),
            label = "clockHeight",
        )

        CompositionLocalProvider(LocalGlass provides wallpaper?.glass, LocalLight provides light) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(unlockDistance) {
                        val velocity = VelocityTracker()
                        var armed = false
                        detectVerticalDragGestures(
                            onDragStart = {
                                velocity.resetTracking()
                                armed = false
                            },
                            onDragEnd = {
                                val v = velocity.calculateVelocity().y
                                val p = (-drag.value / unlockDistance).coerceIn(0f, 1f)
                                scope.launch {
                                    if (p > 0.4f || (v < -1600f && p > 0.08f)) {
                                        drag.animateTo(-hPx.toFloat(), tween(240, easing = FastOutSlowInEasing))
                                        actions.requestUnlock(null)
                                    } else {
                                        drag.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 380f), initialVelocity = v)
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f)) }
                            },
                        ) { change, dy ->
                            change.consume()
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val cur = drag.value
                            val next = (if (cur + dy > 0f) cur + dy * 0.25f else cur + dy).coerceIn(-hPx.toFloat(), 160f)
                            val p = -next / unlockDistance
                            if (!armed && p > 0.4f) {
                                armed = true
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            } else if (armed && p < 0.35f) {
                                armed = false
                            }
                            scope.launch { drag.snapTo(next) }
                        }
                    },
            ) {
                val wp = wallpaper
                if (wp != null) {
                    Image(
                        bitmap = wp.sharp,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = progress()
                                val s = 1f + 0.08f * p
                                scaleX = s
                                scaleY = s
                                renderEffect = if (p > 0.01f) BlurEffect(34f * p, 34f * p, TileMode.Clamp) else null
                            },
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val a = 0.05f + 0.32f * progress() + 0.5f * (1f - wake.value)
                            drawRect(Color.Black.copy(alpha = a.coerceIn(0f, 1f)))
                        },
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = progress()
                            translationY = drag.value
                            val s = (0.965f + 0.035f * wake.value) * (1f - 0.04f * p)
                            scaleX = s
                            scaleY = s
                            alpha = (1f - p * 1.3f).coerceIn(0f, 1f) * (0.3f + 0.7f * wake.value)
                        },
                ) {
                    Column(
                        Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)),
                            contentAlignment = Alignment.Center,
                        ) {
                            StatusRow(status)
                        }
                        Crossfade(targetState = unlockedLook, label = "lock") { open ->
                            Icon(
                                if (open) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                contentDescription = if (open) "Unlocked" else "Locked",
                                tint = Color.White,
                                modifier = Modifier.padding(top = 4.dp).size(18.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(dateText, style = DateStyle)
                        GlassClock(timeText, maxWidth = screenW * 0.8f, targetHeight = clockHeight)
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 10.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            NotificationStack(
                                items = items,
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                                showContent = showContent,
                                now = now,
                                onOpen = { actions.openNotification(it) },
                                onDismiss = { actions.dismissNotification(it) },
                            )
                            if (items.isNotEmpty() && media != null) Spacer(Modifier.height(10.dp))
                            AnimatedVisibility(
                                visible = media != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                val shown = media ?: lastMedia
                                if (shown != null) NowPlayingCard(shown)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassActionButton(GlassIcons.Flashlight, torch.on.value, "Flashlight") { torch.toggle() }
                            Text(
                                "Swipe up to open",
                                style = HintStyle,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            GlassActionButton(GlassIcons.Camera, false, "Camera") { actions.openCamera() }
                        }
                    }
                }
            }
        }
    }
}
