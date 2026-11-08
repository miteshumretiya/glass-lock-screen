package com.mitesh.glasslock.ui

import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mitesh.glasslock.DeviceStatus
import com.mitesh.glasslock.LockNotification
import com.mitesh.glasslock.MediaInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// Icons drawn to match the filled iOS lock screen glyphs.
// ---------------------------------------------------------------------------------------------

object GlassIcons {
    val Flashlight: ImageVector by lazy {
        ImageVector.Builder("Flashlight", 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(
                    "M6.5,3.5A1,1 0 0 1 7.5,2.5H16.5A1,1 0 0 1 17.5,3.5V5L15,9V19.5A2,2 0 0 1 13,21.5H11A2,2 0 0 1 9,19.5V9L6.5,5Z" +
                        "M12,11.6A1.4,1.4 0 1 0 12,14.4A1.4,1.4 0 1 0 12,11.6Z",
                ),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.White),
            )
            .build()
    }

    val Camera: ImageVector by lazy {
        ImageVector.Builder("Camera", 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(
                    "M4.5,6.5H8L9.4,4.3A1.2,1.2 0 0 1 10.4,3.8H13.6A1.2,1.2 0 0 1 14.6,4.3L16,6.5H19.5A2.5,2.5 0 0 1 22,9V17.5A2.5,2.5 0 0 1 19.5,20H4.5A2.5,2.5 0 0 1 2,17.5V9A2.5,2.5 0 0 1 4.5,6.5Z" +
                        "M12,9.2A3.9,3.9 0 1 0 12,17A3.9,3.9 0 1 0 12,9.2Z" +
                        "M12,10.9A2.2,2.2 0 1 0 12,15.3A2.2,2.2 0 1 0 12,10.9Z",
                ),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.White),
            )
            .build()
    }
}

// ---------------------------------------------------------------------------------------------
// The big glass clock.
// ---------------------------------------------------------------------------------------------

/**
 * The tall iOS-style clock. The font size is set by the available width and the digits are
 * stretched vertically to [targetHeight], then filled with refracted, frosted wallpaper.
 */
@Composable
fun GlassClock(text: String, maxWidth: Dp, targetHeight: Dp, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val density = LocalDensity.current
    val env = LocalGlass.current
    val light = LocalLight.current
    val baseStyle = remember {
        TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 100.sp,
            fontFeatureSettings = "tnum",
            letterSpacing = (-0.01).em,
        )
    }
    val sample = if (text.length >= 5) "00:00" else "0:00"
    val reference = remember(sample) { measurer.measure(sample, baseStyle, softWrap = false, maxLines = 1) }
    val maxWidthPx = with(density) { maxWidth.toPx() }
    val scale = maxWidthPx / reference.size.width.coerceAtLeast(1)
    val fontPx = with(density) { 100.sp.toPx() } * scale
    val style = remember(scale) { baseStyle.copy(fontSize = (100f * scale).sp) }
    val layout = remember(text, style) { measurer.measure(text, style, softWrap = false, maxLines = 1) }
    val capPx = fontPx * INTER_CAP_HEIGHT
    val targetPx = with(density) { targetHeight.toPx() }
    val stretch = (targetPx / capPx).coerceIn(1f, 2.3f)
    val pad = with(density) { 6.dp.toPx() }
    val widthPx = layout.size.width.toFloat()
    val heightPx = capPx * stretch + pad * 2f
    var origin by remember { mutableStateOf(Offset.Zero) }
    val holder = remember(env) { createTextShader(env) }

    Canvas(
        modifier
            .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        val top = layout.firstBaseline - capPx
        val bottom = layout.firstBaseline
        val l = light.value
        withTransform({
            translate(0f, pad)
            scale(1f, stretch, pivot = Offset.Zero)
            translate(0f, -top)
        }) {
            // Soft shadow that lifts the glass off the wallpaper.
            drawText(
                layout,
                color = Color.Black.copy(alpha = 0.03f),
                shadow = Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 3.dp.toPx()), 20.dp.toPx()),
            )
            var filled = false
            if (env != null && holder != null && Build.VERSION.SDK_INT >= 33 && !GlassShaders.broken) {
                filled = drawClockGlass(layout, holder, env, origin, pad, stretch, top, bottom, widthPx, heightPx, l)
            }
            if (!filled) {
                drawText(
                    layout,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.96f), Color.White.copy(alpha = 0.74f)),
                        startY = top,
                        endY = bottom,
                    ),
                    alpha = 0.92f,
                )
            }
            // Specular rim: bright where it faces the light, fading across the glyph.
            drawText(
                layout,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.5f),
                    ),
                    start = Offset(widthPx * (0.5f + l.x * 0.6f), top),
                    end = Offset(widthPx * (0.5f - l.x * 0.6f), bottom),
                ),
                drawStyle = Stroke(width = 1.2.dp.toPx() / sqrt(stretch)),
            )
        }
    }
}

@RequiresApi(33)
private fun DrawScope.drawClockGlass(
    layout: TextLayoutResult,
    holder: Any,
    env: GlassEnv,
    origin: Offset,
    pad: Float,
    stretch: Float,
    top: Float,
    bottom: Float,
    widthPx: Float,
    heightPx: Float,
    light: Offset,
): Boolean {
    val text = holder as? TextShader ?: return false
    return try {
        text.update(
            env = env,
            origin = Offset(origin.x, origin.y + pad - stretch * top),
            stretch = stretch,
            center = Offset(origin.x + widthPx / 2f, origin.y + heightPx / 2f),
            light = light,
            tint = Color.White.copy(alpha = 0.40f),
            magnify = 0.93f,
            top = top,
            bottom = bottom,
        )
        drawText(layout, brush = ShaderBrush(text.shader), alpha = 0.94f)
        true
    } catch (t: Throwable) {
        GlassShaders.broken = true
        false
    }
}

// ---------------------------------------------------------------------------------------------
// Status row: carrier, signal, Wi-Fi, battery.
// ---------------------------------------------------------------------------------------------

private val StatusText = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)

@Composable
fun StatusRow(status: DeviceStatus, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            status.carrier,
            style = StatusText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (status.signal >= 0) {
            SignalBars(status.signal)
            Spacer(Modifier.width(6.dp))
        }
        if (status.wifi) {
            Icon(Icons.Rounded.Wifi, contentDescription = "Wi-Fi", tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        if (status.battery >= 0) BatteryGlyph(status.battery, status.charging)
    }
}

@Composable
private fun SignalBars(level: Int) {
    Canvas(Modifier.size(18.dp, 12.dp)) {
        val barW = 3.dp.toPx()
        val gap = (size.width - barW * 4) / 3f
        for (i in 0 until 4) {
            val bh = size.height * (0.35f + 0.65f * (i / 3f))
            drawRoundRect(
                color = if (i < level) Color.White else Color.White.copy(alpha = 0.32f),
                topLeft = Offset(i * (barW + gap), size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun BatteryGlyph(level: Int, charging: Boolean) {
    val measurer = rememberTextMeasurer()
    val style = remember { TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.5.sp) }
    Canvas(
        Modifier
            .size(28.dp, 13.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val nubW = 2.dp.toPx()
        val bodyW = size.width - nubW - 1.dp.toPx()
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.38f), size = Size(bodyW, size.height), cornerRadius = r)
        val fill = when {
            charging -> Color(0xFF34C759)
            level <= 20 -> Color(0xFFFF453A)
            else -> Color.White
        }
        clipRect(right = bodyW * level.coerceIn(0, 100) / 100f) {
            drawRoundRect(fill, size = Size(bodyW, size.height), cornerRadius = r)
        }
        drawRoundRect(
            Color.White.copy(alpha = 0.4f),
            topLeft = Offset(bodyW + 1.dp.toPx(), size.height * 0.3f),
            size = Size(nubW, size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        val label = measurer.measure(level.toString(), style)
        // Knock the number out of the capsule so the wallpaper shows through, like iOS.
        drawText(
            label,
            color = Color.Black,
            topLeft = Offset((bodyW - label.size.width) / 2f, (size.height - label.size.height) / 2f),
            blendMode = BlendMode.DstOut,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Notifications.
// ---------------------------------------------------------------------------------------------

private val NotifTitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp, color = Color.White)
private val NotifBody = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.92f))
private val NotifMeta = TextStyle(fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.62f))
private val PillText = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)

fun timeLabel(postTime: Long, now: Long, is24: Boolean): String {
    val diff = now - postTime
    return when {
        diff < 60_000L -> "now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        else -> SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(postTime))
    }
}

@Composable
fun NotificationCard(
    item: LockNotification,
    showContent: Boolean,
    now: Long,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val offsetX = remember(item.key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableIntStateOf(1) }
    val open by rememberUpdatedState(onOpen)
    val dismiss by rememberUpdatedState(onDismiss)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .graphicsLayer {
                translationX = offsetX.value
                alpha = 1f - (abs(offsetX.value) / widthPx).coerceIn(0f, 1f) * 0.7f
            }
            .pointerInput(item.key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (item.clearable && abs(offsetX.value) > widthPx * 0.35f) {
                                offsetX.animateTo(sign(offsetX.value) * widthPx * 1.2f, tween(180))
                                dismiss()
                            } else {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { offsetX.snapTo(offsetX.value + dx * if (item.clearable) 1f else 0.3f) }
                }
            }
            .pointerInput(item.key) { detectTapGestures(onTap = { open() }) },
        cornerRadius = 24.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(item.icon)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val title = if (showContent) item.title.ifBlank { item.appName } else item.appName
                    Text(title, style = NotifTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(timeLabel(item.postTime, now, is24), style = NotifMeta, maxLines = 1)
                }
                val body = if (showContent) item.text else "Notification"
                if (body.isNotBlank()) {
                    Text(body, style = NotifBody, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?) {
    val shape = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = shape)
    } else {
        Box(shape.background(Color.White.copy(alpha = 0.25f)))
    }
}

@Composable
fun GlassPill(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    GlassSurface(
        modifier = Modifier
            .height(30.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        cornerRadius = 15.dp,
    ) {
        Text(text, style = PillText, modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
    }
}

/**
 * Notifications the iOS way: collapsed into a stack (newest on top, two layers peeking out
 * below), tap to fan them out; swipe a card sideways to clear it.
 */
@Composable
fun NotificationStack(
    items: List<LockNotification>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showContent: Boolean,
    now: Long,
    onOpen: (LockNotification) -> Unit,
    onDismiss: (LockNotification) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.85f, stiffness = 400f))) {
        if (expanded && items.size > 1) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                GlassPill("Show less") { onExpandedChange(false) }
            }
            val shown = items.take(4)
            shown.forEachIndexed { index, n ->
                key(n.key) {
                    NotificationCard(n, showContent, now, onOpen = { onOpen(n) }, onDismiss = { onDismiss(n) })
                }
                if (index != shown.lastIndex) Spacer(Modifier.height(8.dp))
            }
            if (items.size > shown.size) {
                Text(
                    "+${items.size - shown.size} more",
                    style = PillText.copy(color = Color.White.copy(alpha = 0.7f)),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                )
            }
        } else {
            val top = items.first()
            val ghosts = (items.size - 1).coerceAtMost(2)
            Box(Modifier.fillMaxWidth().padding(bottom = (ghosts * 9).dp)) {
                for (g in ghosts downTo 1) {
                    GlassSurface(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                translationY = (g * 9).dp.toPx()
                                val s = 1f - g * 0.045f
                                scaleX = s
                                scaleY = s
                            },
                        cornerRadius = 24.dp,
                        opacity = 1f - g * 0.22f,
                    )
                }
                key(top.key) {
                    NotificationCard(
                        top,
                        showContent,
                        now,
                        onOpen = { if (items.size > 1) onExpandedChange(true) else onOpen(top) },
                        onDismiss = { onDismiss(top) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Now Playing.
// ---------------------------------------------------------------------------------------------

private val MediaTitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
private val MediaArtist = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.72f))
private val MediaTime = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), fontFeatureSettings = "tnum")

private fun formatMs(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun NowPlayingCard(info: MediaInfo, modifier: Modifier = Modifier) {
    val nowElapsed by produceState(SystemClock.elapsedRealtime(), info) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            if (!info.playing) break
            delay(500)
        }
    }
    GlassSurface(modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val artShape = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                val art = info.art
                if (art != null) {
                    Image(art, contentDescription = null, modifier = artShape, contentScale = ContentScale.Crop)
                } else {
                    Box(artShape.background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.title, style = MediaTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (info.artist.isNotBlank()) {
                        Text(info.artist, style = MediaArtist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (info.duration > 0L) {
                val elapsed = info.elapsedAt(nowElapsed)
                val fraction = (elapsed.toFloat() / info.duration).coerceIn(0f, 1f)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color.White.copy(alpha = 0.9f)))
                }
                Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                    Text(formatMs(elapsed), style = MediaTime)
                    Spacer(Modifier.weight(1f))
                    Text("-" + formatMs(info.duration - elapsed), style = MediaTime)
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val controls = info.controller.transportControls
                MediaButton(Icons.Rounded.FastRewind, 30.dp, "Previous") { controls.skipToPrevious() }
                MediaButton(if (info.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 40.dp, if (info.playing) "Pause" else "Play") {
                    if (info.playing) controls.pause() else controls.play()
                }
                MediaButton(Icons.Rounded.FastForward, 30.dp, "Next") { controls.skipToNext() }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, iconSize: Dp, description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Box(
        Modifier
            .size(iconSize + 18.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

// ---------------------------------------------------------------------------------------------
// Flashlight / camera buttons.
// ---------------------------------------------------------------------------------------------

/** Round glass button: press and hold to activate, like iOS, with a springy bulge. */
@Composable
fun GlassActionButton(icon: ImageVector, active: Boolean, description: String, onActivate: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val fill by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "fill")
    val activate by rememberUpdatedState(onActivate)
    Box(
        Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch { scale.animateTo(1.16f, spring(dampingRatio = 0.55f, stiffness = 500f)) }
                        tryAwaitRelease()
                        scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        activate()
                    },
                )
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(Modifier.fillMaxSize(), cornerRadius = 26.dp, tint = LocalGlass.current?.buttonTint, refraction = 1.3f)
        if (fill > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fill }
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Icon(icon, contentDescription = null, tint = lerp(Color.White, Color.Black, fill), modifier = Modifier.size(22.dp))
    }
}
