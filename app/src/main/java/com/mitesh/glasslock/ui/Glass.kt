package com.mitesh.glasslock.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The blurred wallpaper every glass element refracts, plus tints tuned to its brightness. */
class GlassEnv(val blurred: Bitmap, screenWidth: Int, screenHeight: Int, val luminance: Float) {
    val image: ImageBitmap = blurred.asImageBitmap()
    val scaleX = blurred.width / screenWidth.toFloat()
    val scaleY = blurred.height / screenHeight.toFloat()
    val shader: BitmapShader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
        if (Build.VERSION.SDK_INT >= 33) it.filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

    /** Frosted white over dark wallpapers, smoky over bright ones, so white text stays readable. */
    val panelTint: Color = if (luminance < 0.45f) {
        Color.White.copy(alpha = (0.07f + (0.45f - luminance) * 0.18f).coerceIn(0.06f, 0.16f))
    } else {
        Color.Black.copy(alpha = (0.12f + (luminance - 0.45f) * 0.6f).coerceIn(0.12f, 0.42f))
    }

    val buttonTint: Color = if (luminance < 0.45f) {
        Color.White.copy(alpha = (0.10f + (0.45f - luminance) * 0.20f).coerceIn(0.08f, 0.20f))
    } else {
        Color.Black.copy(alpha = (0.08f + (luminance - 0.45f) * 0.5f).coerceIn(0.08f, 0.32f))
    }
}

val DefaultLight = Offset(-0.45f, -0.89f)

val LocalGlass = staticCompositionLocalOf<GlassEnv?> { null }
val LocalLight = staticCompositionLocalOf<State<Offset>> { mutableStateOf(DefaultLight) }

@RequiresApi(33)
internal class PanelShader(env: GlassEnv) {
    val shader = RuntimeShader(GlassShaders.PANEL).apply { setInputShader("wall", env.shader) }

    fun update(
        env: GlassEnv,
        origin: Offset,
        width: Float,
        height: Float,
        radius: Float,
        light: Offset,
        tint: Color,
        refraction: Float,
        bevel: Float,
        rimAlpha: Float,
        opacity: Float,
    ) {
        shader.setFloatUniform("wallScale", env.scaleX, env.scaleY)
        shader.setFloatUniform("origin", origin.x, origin.y)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("light", light.x, light.y)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        shader.setFloatUniform("refraction", refraction)
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("rimAlpha", rimAlpha)
        shader.setFloatUniform("opacity", opacity)
    }
}

@RequiresApi(33)
internal class TextShader(env: GlassEnv) {
    val shader = RuntimeShader(GlassShaders.TEXT).apply { setInputShader("wall", env.shader) }

    fun update(
        env: GlassEnv,
        origin: Offset,
        stretch: Float,
        center: Offset,
        light: Offset,
        tint: Color,
        magnify: Float,
        top: Float,
        bottom: Float,
    ) {
        shader.setFloatUniform("wallScale", env.scaleX, env.scaleY)
        shader.setFloatUniform("origin", origin.x, origin.y)
        shader.setFloatUniform("stretch", 1f, stretch)
        shader.setFloatUniform("center", center.x, center.y)
        shader.setFloatUniform("light", light.x, light.y)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        shader.setFloatUniform("magnify", magnify)
        shader.setFloatUniform("bounds", top, bottom)
    }
}

/** Creates the shader, or returns null (plain frosted fallback) if the device rejects it. */
internal fun createPanelShader(env: GlassEnv?): Any? {
    if (env == null || Build.VERSION.SDK_INT < 33 || GlassShaders.broken) return null
    return try {
        PanelShader(env)
    } catch (t: Throwable) {
        GlassShaders.broken = true
        null
    }
}

internal fun createTextShader(env: GlassEnv?): Any? {
    if (env == null || Build.VERSION.SDK_INT < 33 || GlassShaders.broken) return null
    return try {
        TextShader(env)
    } catch (t: Throwable) {
        GlassShaders.broken = true
        null
    }
}

/**
 * A rounded piece of liquid glass. It shows the wallpaper behind it blurred and bent at
 * the edges, with a bright rim on the side facing the light.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp,
    tint: Color? = null,
    refraction: Float = 1f,
    opacity: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val env = LocalGlass.current
    val light = LocalLight.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val holder = remember(env) { createPanelShader(env) }
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .drawBehind {
                drawGlass(
                    env = env,
                    holder = holder,
                    origin = origin,
                    radius = cornerRadius.toPx(),
                    tint = tint ?: env?.panelTint ?: Color.White.copy(alpha = 0.12f),
                    refraction = refraction,
                    light = light.value,
                    opacity = opacity,
                )
            },
        content = content,
    )
}

internal fun DrawScope.drawGlass(
    env: GlassEnv?,
    holder: Any?,
    origin: Offset,
    radius: Float,
    tint: Color,
    refraction: Float,
    light: Offset,
    opacity: Float,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val r = radius.coerceAtMost(size.minDimension / 2f)
    if (env != null && holder != null && Build.VERSION.SDK_INT >= 33 && !GlassShaders.broken) {
        if (drawPanelShader(env, holder, origin, r, tint, refraction, light, opacity)) return
    }
    drawGlassFallback(env, origin, r, tint, opacity)
}

@RequiresApi(33)
private fun DrawScope.drawPanelShader(
    env: GlassEnv,
    holder: Any,
    origin: Offset,
    r: Float,
    tint: Color,
    refraction: Float,
    light: Offset,
    opacity: Float,
): Boolean {
    val panel = holder as? PanelShader ?: return false
    return try {
        panel.update(
            env = env,
            origin = origin,
            width = size.width,
            height = size.height,
            radius = r,
            light = light,
            tint = tint,
            refraction = refraction * 12.dp.toPx(),
            bevel = minOf(r, 20.dp.toPx()),
            rimAlpha = 0.6f,
            opacity = opacity,
        )
        drawRect(brush = ShaderBrush(panel.shader))
        true
    } catch (t: Throwable) {
        GlassShaders.broken = true
        false
    }
}

private fun DrawScope.drawGlassFallback(env: GlassEnv?, origin: Offset, r: Float, tint: Color, opacity: Float) {
    val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    clipPath(path) {
        if (env != null) {
            val bw = env.blurred.width
            val bh = env.blurred.height
            val sx = (origin.x * env.scaleX).roundToInt().coerceIn(0, bw - 1)
            val sy = (origin.y * env.scaleY).roundToInt().coerceIn(0, bh - 1)
            val sw = (size.width * env.scaleX).roundToInt().coerceIn(1, bw - sx)
            val sh = (size.height * env.scaleY).roundToInt().coerceIn(1, bh - sy)
            drawImage(
                image = env.image,
                srcOffset = IntOffset(sx, sy),
                srcSize = IntSize(sw, sh),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                alpha = opacity,
            )
        }
        drawRect(color = tint, alpha = opacity)
    }
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.3f)),
        ),
        cornerRadius = CornerRadius(r),
        style = Stroke(width = 1.dp.toPx()),
        alpha = opacity,
    )
}

/** Light direction for the specular rims; it drifts as you tilt the phone. */
@Composable
fun rememberLight(): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(DefaultLight) }
    LifecycleResumeEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            var gx = 0f
            var gy = 0.8f

            override fun onSensorChanged(event: SensorEvent) {
                gx += (event.values[0] / SensorManager.GRAVITY_EARTH - gx) * 0.15f
                gy += (event.values[1] / SensorManager.GRAVITY_EARTH - gy) * 0.15f
                val x = -0.45f - gx * 0.9f
                val y = -0.89f + (0.8f - gy) * 0.6f
                val len = sqrt(x * x + y * y).coerceAtLeast(0.001f)
                val next = Offset(x / len, y / len)
                val cur = state.value
                if (abs(next.x - cur.x) + abs(next.y - cur.y) > 0.01f) state.value = next
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sm != null && sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onPauseOrDispose { sm?.unregisterListener(listener) }
    }
    return state
}
