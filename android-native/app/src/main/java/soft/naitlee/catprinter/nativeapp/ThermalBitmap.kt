package soft.naitlee.catprinter.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class DitherMode(val label: String) {
    NONE("No dithering"),
    FLOYD_STEINBERG("Floyd-Steinberg"),
    BAYER_4X4("Bayer 4x4"),
}

object ThermalBitmap {
    val ditherLabels: Array<String> = DitherMode.entries.map { it.label }.toTypedArray()

    fun ditherModeFromLabel(label: String): DitherMode =
        DitherMode.entries.firstOrNull { it.label == label } ?: DitherMode.FLOYD_STEINBERG

    fun fromImageStream(
        input: InputStream,
        targetWidth: Int,
        brightness: Int,
        energy: Int,
        ditherMode: DitherMode,
        bayerRange: Int,
        rotate: Boolean,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        maxHeight: Int = 1600,
    ): PrintBitmap {
        val decoded = BitmapFactory.decodeStream(input) ?: error("Could not decode image")
        val oriented = transform(decoded, rotate, flipHorizontal, flipVertical)
        val scaledHeight = max(1, (oriented.height * (targetWidth.toFloat() / oriented.width)).roundToInt()).coerceAtMost(maxHeight)
        val scaled = Bitmap.createScaledBitmap(oriented, targetWidth, scaledHeight, true)
        val originalPreview = Bitmap.createScaledBitmap(oriented, targetWidth, scaledHeight, false)
        val mono = monochrome(scaled, brightness, ditherMode, bayerRange)
        val result = fromMono(targetWidth, scaledHeight, mono, energy, originalPreview)
        if (oriented !== decoded) oriented.recycle()
        if (scaled !== oriented) scaled.recycle()
        decoded.recycle()
        return result
    }

    fun fromText(
        text: String,
        targetWidth: Int,
        textSizeSp: Float,
        brightness: Int,
        energy: Int,
        ditherMode: DitherMode,
        bayerRange: Int,
    ): PrintBitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = textSizeSp
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder
            .obtain(text.ifBlank { " " }, 0, text.ifBlank { " " }.length, paint, targetWidth - 24)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(true)
            .build()
        val height = max(64, layout.height + 24)
        val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.translate(12f, 12f)
        layout.draw(canvas)
        val originalPreview = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val mono = monochrome(bitmap, brightness, ditherMode, bayerRange)
        bitmap.recycle()
        return fromMono(targetWidth, height, mono, energy, originalPreview)
    }

    fun fromPacked(width: Int, height: Int, payload: ByteArray, energy: Int): PrintBitmap {
        val mono = IntArray(width * height)
        for (index in mono.indices) {
            val byte = payload.getOrNull(index / 8)?.toInt() ?: 0
            mono[index] = if ((byte and (0x80 shr (index and 7))) != 0) 0 else 255
        }
        val preview = previewFromMono(width, height, mono, energy)
        return PrintBitmap(width, height, payload, preview, preview.copy(Bitmap.Config.ARGB_8888, false))
    }

    private fun fromMono(width: Int, height: Int, mono: IntArray, energy: Int, originalPreview: Bitmap): PrintBitmap =
        PrintBitmap(width, height, packBits(mono), previewFromMono(width, height, mono, energy), originalPreview)

    private fun previewFromMono(width: Int, height: Int, mono: IntArray, energy: Int): Bitmap {
        val preview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val ink = previewInkColor(energy)
        val pixels = IntArray(mono.size) { index -> if (mono[index] <= 128) ink else Color.WHITE }
        preview.setPixels(pixels, 0, width, 0, 0, width, height)
        return preview
    }

    private fun previewInkColor(energy: Int): Int {
        val amount = energy.coerceIn(0, 100) * 256f / 100f
        val rate = amount / 256f
        val brightness = max(1.6f - rate * 1.5f, 0.75f)
        val contrast = 1f + rate * 2f
        val shade = ((255f * (1f - rate)) * brightness).coerceIn(0f, 255f)
        val filtered = ((shade - 128f) * contrast + 128f).coerceIn(0f, 255f).roundToInt()
        return Color.rgb(filtered, filtered, filtered)
    }

    private fun transform(source: Bitmap, rotate: Boolean, flipHorizontal: Boolean, flipVertical: Boolean): Bitmap {
        if (!rotate && !flipHorizontal && !flipVertical) return source
        val matrix = Matrix()
        if (rotate) matrix.postRotate(90f)
        matrix.postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun monochrome(bitmap: Bitmap, brightness: Int, ditherMode: DitherMode, bayerRange: Int): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val values = FloatArray(width * height)
        val offset = (brightness - 50) * 2.4f
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = bitmap.getPixel(x, y)
                val alpha = Color.alpha(color)
                val whiteBlend = 255 - alpha
                val gray = ((Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f) * alpha / 255f) + whiteBlend
                values[y * width + x] = (gray + offset).coerceIn(0f, 255f)
            }
        }
        return when (ditherMode) {
            DitherMode.NONE -> threshold(values)
            DitherMode.FLOYD_STEINBERG -> floydSteinberg(values, width, height)
            DitherMode.BAYER_4X4 -> bayer4x4(values, width, bayerRange)
        }
    }

    private fun threshold(values: FloatArray): IntArray = IntArray(values.size) { index -> if (values[index] > 128f) 255 else 0 }

    private fun bayer4x4(values: FloatArray, width: Int, bayerRange: Int): IntArray {
        val bayer = arrayOf(
            intArrayOf(0, 8, 2, 10),
            intArrayOf(12, 4, 14, 6),
            intArrayOf(3, 11, 1, 9),
            intArrayOf(15, 7, 13, 5),
        )
        val scale = if (bayerRange <= 50) {
            0.25f + (bayerRange.coerceAtLeast(0) / 50f) * 0.75f
        } else {
            1f + ((bayerRange.coerceAtMost(100) - 50) / 50f) * 1.5f
        }
        return IntArray(values.size) { index ->
            val x = index % width
            val y = index / width
            val baseThreshold = 96 + bayer[y and 3][x and 3] * 8
            val threshold = (156f + (baseThreshold - 156f) * scale).coerceIn(0f, 255f)
            if (values[index] < threshold) 0 else 255
        }
    }

    private fun floydSteinberg(values: FloatArray, width: Int, height: Int): IntArray {
        val out = IntArray(values.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val old = values[index]
                val newValue = if (old > 128f) 255f else 0f
                val error = old - newValue
                values[index] = newValue
                out[index] = newValue.toInt()
                if (x + 1 < width) values[index + 1] = (values[index + 1] + error * 7f / 16f).coerceIn(0f, 255f)
                if (x > 0 && y + 1 < height) values[index + width - 1] = (values[index + width - 1] + error * 3f / 16f).coerceIn(0f, 255f)
                if (y + 1 < height) values[index + width] = (values[index + width] + error * 5f / 16f).coerceIn(0f, 255f)
                if (x + 1 < width && y + 1 < height) values[index + width + 1] = (values[index + width + 1] + error / 16f).coerceIn(0f, 255f)
            }
        }
        return out
    }

    private fun packBits(mono: IntArray): ByteArray {
        val payload = ByteArray(mono.size / 8)
        for (start in mono.indices step 8) {
            var value = 0
            for (bit in 0 until 8) {
                if (mono[start + bit] <= 128) value = value or (0x80 shr bit)
            }
            payload[start / 8] = value.toByte()
        }
        return payload
    }
}

data class PrintBitmap(val width: Int, val height: Int, val payload: ByteArray, val preview: Bitmap, val originalPreview: Bitmap)
