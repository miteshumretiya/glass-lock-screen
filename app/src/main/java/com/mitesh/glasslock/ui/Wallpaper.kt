package com.mitesh.glasslock.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mitesh.glasslock.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

class Wallpaper(val sharp: ImageBitmap, val glass: GlassEnv)

/** Loads the lock screen wallpaper (yours or the built-in one) and its blurred glass copy. */
object WallpaperStore {
    private var cacheKey: String? = null
    private var cacheValue: Wallpaper? = null

    fun file(context: Context) = File(context.filesDir, "wallpaper.img")

    private fun key(w: Int, h: Int, version: Int) = "${w}x$h@$version"

    @Synchronized
    fun cached(w: Int, h: Int, version: Int): Wallpaper? =
        if (cacheKey == key(w, h, version)) cacheValue else null

    suspend fun load(context: Context, w: Int, h: Int, version: Int): Wallpaper = withContext(Dispatchers.Default) {
        cached(w, h, version) ?: run {
            val sharp = decodeCover(context, w, h)
            val small = Bitmap.createScaledBitmap(sharp, (sharp.width / 4).coerceAtLeast(1), (sharp.height / 4).coerceAtLeast(1), true)
            val blurred = Blur.box(small, 7)
            val wallpaper = Wallpaper(sharp.asImageBitmap(), GlassEnv(blurred, sharp.width, sharp.height, Blur.luminance(blurred)))
            synchronized(this@WallpaperStore) {
                cacheKey = key(w, h, version)
                cacheValue = wallpaper
            }
            wallpaper
        }
    }

    suspend fun thumbnail(context: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching { decodeCover(context, 360, 780).asImageBitmap() }.getOrNull()
    }

    /** Copies a photo picked by the user into private storage. */
    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.filesDir, "wallpaper.tmp")
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching false
            input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
            // Make sure it really is a readable image before using it.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(tmp)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(8)
            }
            val dest = file(context)
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            true
        }.getOrDefault(false)
    }

    fun reset(context: Context) {
        file(context).delete()
    }

    private fun decodeCover(context: Context, w: Int, h: Int): Bitmap {
        val custom = file(context)
        val decoded = (if (custom.exists()) runCatching { decode(ImageDecoder.createSource(custom), w, h) }.getOrNull() else null)
            ?: decode(ImageDecoder.createSource(context.resources, R.drawable.default_wallpaper), w, h)
        val x = ((decoded.width - w) / 2).coerceAtLeast(0)
        val y = ((decoded.height - h) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(decoded, x, y, minOf(w, decoded.width - x), minOf(h, decoded.height - y))
    }

    /** Decodes scaled so the image covers w x h (centre-crop happens afterwards). */
    private fun decode(source: ImageDecoder.Source, w: Int, h: Int): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val iw = info.size.width.toFloat()
            val ih = info.size.height.toFloat()
            val s = maxOf(w / iw, h / ih)
            decoder.setTargetSize(
                ceil(iw * s).toInt().coerceAtLeast(w),
                ceil(ih * s).toInt().coerceAtLeast(h),
            )
        }
}

/** Fast three-pass box blur (close to a Gaussian) for the small glass texture. */
object Blur {
    fun box(src: Bitmap, radius: Int, passes: Int = 3): Bitmap {
        val w = src.width
        val h = src.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        repeat(passes) {
            blurTranspose(a, b, w, h, radius)
            blurTranspose(b, a, h, w, radius)
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Blurs each row and writes the result transposed, so two calls cover both directions. */
    private fun blurTranspose(input: IntArray, output: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = input[row + i.coerceIn(0, w - 1)]
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (x in 0 until w) {
                output[x * h + y] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = input[row + (x - r).coerceIn(0, w - 1)]
                val pIn = input[row + (x + r + 1).coerceIn(0, w - 1)]
                sa += (pIn ushr 24) - (pOut ushr 24)
                sr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                sg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                sb += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }

    fun luminance(bmp: Bitmap): Float {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var sum = 0.0
        var n = 0
        var i = 0
        while (i < px.size) {
            val p = px[i]
            sum += (0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)) / 255.0
            n++
            i += 5
        }
        return if (n == 0) 0.3f else (sum / n).toFloat()
    }
}
