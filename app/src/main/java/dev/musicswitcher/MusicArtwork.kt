package dev.musicswitcher

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
internal fun rememberTrackArtwork(data: ByteArray?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, data) {
        value = null
        if (data == null || data.size > 8 * 1024 * 1024) return@produceState
        value = withContext(Dispatchers.Default) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1_024) {
                options.inSampleSize *= 2
            }
            options.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(data, 0, data.size, options)?.asImageBitmap()
        }
    }
    return image
}

@Composable
internal fun MusicArtwork(label: String, modifier: Modifier = Modifier, image: ImageBitmap? = null, folder: Boolean = false) {
    val shape = RoundedCornerShape(if (folder) 10.dp else 7.dp)
    val background = if (folder) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurface
    if (image != null) {
        Image(image, contentDescription = null, modifier = modifier.clip(shape), contentScale = ContentScale.Crop)
    } else {
        val seed = label.hashCode()
        Canvas(modifier.clip(shape).background(background)) {
            val unit = min(size.width, size.height)
            if (folder) {
                val x = (size.width - unit) / 2
                val y = (size.height - unit) / 2
                val path = Path().apply {
                    moveTo(x + unit * .19f, y + unit * .66f)
                    lineTo(x + unit * .19f, y + unit * .33f)
                    lineTo(x + unit * .43f, y + unit * .33f)
                    lineTo(x + unit * .50f, y + unit * .40f)
                    lineTo(x + unit * .81f, y + unit * .40f)
                    lineTo(x + unit * .81f, y + unit * .72f)
                    lineTo(x + unit * .19f, y + unit * .72f)
                    close()
                }
                drawPath(path, ink, style = Stroke(unit * .042f, join = StrokeJoin.Round))
                repeat(3) { bar ->
                    val bx = x + unit * (.46f + bar * .09f)
                    val half = unit * if (bar == 1) .075f else .045f
                    drawLine(ink, Offset(bx, y + unit * .56f - half), Offset(bx, y + unit * .56f + half), unit * .033f, StrokeCap.Round)
                }
            } else {
                drawRect(Brush.linearGradient(coverTones))
                val pivot = Offset(size.width * .72f, size.height * .39f)
                repeat(7) { ring ->
                    drawCircle(Color.White.copy(alpha = .16f + ring * .016f), unit * (.16f + ring * .052f), pivot, style = Stroke(unit * .008f))
                }
                drawCircle(Color.White.copy(alpha = .30f), unit * .12f, pivot)
                drawCircle(Color.White, unit * .030f, pivot)
                repeat(3) { bar ->
                    val phase = ((seed ushr (bar * 4)) and 7) / 7f
                    drawRect(Color.White.copy(alpha = .78f - bar * .12f), Offset(unit * .09f, unit * (.69f + bar * .058f)), Size(unit * (.11f + phase * .19f), unit * .022f))
                }
            }
        }
    }
}

private val coverTones = listOf(
    Color(0xFF262626), Color(0xFF777777), Color(0xFF393939),
)

internal enum class Glyph { Play, Pause, Previous, Next, Collapse, Refresh, Sun, Moon, Folder, Download }

@Composable
internal fun MusicGlyph(glyph: Glyph, modifier: Modifier, description: String? = null, color: Color = LocalContentColor.current) {
    val semantics = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    Canvas(modifier.then(semantics)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(a: Float, b: Float, c: Float, d: Float) =
                drawLine(color, Offset(a, b), Offset(c, d), 2f, cap = StrokeCap.Round)
            fun triangle(reverse: Boolean, narrow: Boolean = false) {
                val left = if (narrow) 8f else 6f
                val right = if (narrow) 18f else 20f
                val start = if (reverse) right else left
                val tip = if (reverse) left else right
                drawPath(Path().apply { moveTo(start, 4f); lineTo(tip, 12f); lineTo(start, 20f); close() }, color)
            }
            when (glyph) {
                Glyph.Download -> {
                    line(12f, 3f, 12f, 15f); line(7f, 10f, 12f, 15f); line(12f, 15f, 17f, 10f)
                    line(3f, 16f, 3f, 20f); line(3f, 20f, 21f, 20f); line(21f, 20f, 21f, 16f)
                }
                Glyph.Folder -> drawPath(Path().apply {
                    moveTo(3f, 6f); lineTo(9f, 6f); lineTo(12f, 9f); lineTo(21f, 9f)
                    lineTo(21f, 19f); lineTo(3f, 19f); close()
                }, color, style = Stroke(1.8f, join = StrokeJoin.Round))
                Glyph.Play -> triangle(reverse = false)
                Glyph.Pause -> {
                    drawRect(color, Offset(6f, 4f), Size(4.5f, 16f))
                    drawRect(color, Offset(13.5f, 4f), Size(4.5f, 16f))
                }
                Glyph.Previous, Glyph.Next -> {
                    val previous = glyph == Glyph.Previous
                    triangle(reverse = previous, narrow = true)
                    drawRect(color, Offset(if (previous) 4f else 19f, 4f), Size(2.5f, 16f))
                }
                Glyph.Collapse -> { line(5f, 9f, 12f, 16f); line(12f, 16f, 19f, 9f) }
                Glyph.Refresh -> {
                    drawArc(color, -42f, 308f, false, Offset(4f, 4f), Size(16f, 16f), style = Stroke(1.8f, cap = StrokeCap.Round))
                    line(20f, 4f, 20f, 9f); line(20f, 9f, 15f, 9f)
                }
                Glyph.Sun -> {
                    drawCircle(color, 4.2f, Offset(12f, 12f), style = Stroke(1.8f))
                    line(12f, 2.5f, 12f, 4.8f); line(12f, 19.2f, 12f, 21.5f)
                    line(2.5f, 12f, 4.8f, 12f); line(19.2f, 12f, 21.5f, 12f)
                    line(5.3f, 5.3f, 7f, 7f); line(17f, 17f, 18.7f, 18.7f)
                    line(5.3f, 18.7f, 7f, 17f); line(17f, 7f, 18.7f, 5.3f)
                }
                Glyph.Moon -> drawPath(Path().apply {
                    moveTo(11f, 3.5f)
                    cubicTo(4.6f, 3.9f, 2.5f, 12.3f, 6.8f, 17.2f)
                    cubicTo(11.6f, 22.2f, 19.7f, 19.5f, 20.6f, 13.3f)
                    cubicTo(14.2f, 16.4f, 8.1f, 10.2f, 11f, 3.5f)
                    close()
                }, color, style = Stroke(1.8f, join = StrokeJoin.Round))
            }
        }
    }
}
