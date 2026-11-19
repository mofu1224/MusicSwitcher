package dev.musicswitcher

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/** Two cut rails exchange their lead when the transport engages. */
@Composable
internal fun SwitchRails(engaged: Boolean, modifier: Modifier) {
    val travel by animateFloatAsState(
        if (engaged) 1f else -1f,
        tween(390, easing = CubicBezierEasing(.18f, .88f, .22f, 1f)), label = "switch throw",
    )
    Canvas(modifier) {
        val mix = (travel + 1f) / 2f
        val ink = Brush.horizontalGradient(
            0f to lerp(Color.White, Color(0xFFED2054), mix),
            .36f to lerp(Color.White, Color(0xFF6633FF), mix),
            1f to lerp(Color.White, Color(0xFF006BFF), mix),
        )
        val u = minOf(size.width / 108f, size.height / 56f)
        val left = (size.width - 108 * u) / 2
        val top = (size.height - 56 * u) / 2
        repeat(2) { i ->
            val x = left + (10 + (if (i == 0) travel else -travel) * 10) * u
            val y = top + i * 29 * u
            val rail = Path().apply {
                moveTo(x + 14*u, y); lineTo(x + 88*u, y)
                lineTo(x + 74*u, y + 25*u); lineTo(x, y + 25*u); close()
            }
            drawPath(rail, ink)
            drawPath(rail, Color.DarkGray.copy(alpha = .8f * (1f - mix)), style = Stroke(1.dp.toPx()))
        }
    }
}
