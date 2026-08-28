package com.forli.meteo.ui.widgetconfig

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.SkyState
import com.forli.meteo.ui.theme.MeteoType
import com.forli.meteo.ui.theme.skyColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Quale colore del widget si sta modificando in questo momento. */
enum class ColorTarget { BACKGROUND, ACCENT }

/**
 * Sfondo e accento del widget, con qualche tinta pronta e una ruota HSV per
 * chi vuole abbinarli esattamente al proprio sfondo del telefono.
 */
@Composable
fun WidgetColorPicker(
    background: Color,
    accent: Color,
    onBackgroundChange: (Color) -> Unit,
    onAccentChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    var target by remember { mutableStateOf(ColorTarget.BACKGROUND) }
    val current = if (target == ColorTarget.BACKGROUND) background else accent
    val onChange: (Color) -> Unit =
        if (target == ColorTarget.BACKGROUND) onBackgroundChange else onAccentChange

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetPill(
                label = "SFONDO",
                selected = target == ColorTarget.BACKGROUND,
                onClick = { target = ColorTarget.BACKGROUND },
            )
            TargetPill(
                label = "ACCENTO",
                selected = target == ColorTarget.ACCENT,
                onClick = { target = ColorTarget.ACCENT },
            )
        }

        Spacer(Modifier.height(14.dp))
        PresetRow(selected = current, onPick = onChange)

        Spacer(Modifier.height(18.dp))
        HueSaturationWheel(
            color = current,
            onColorChange = onChange,
            modifier = Modifier.size(150.dp).align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(12.dp))
        Text(text = "LUMINOSITÀ", style = MeteoType.caption, color = Color.White.copy(alpha = 0.7f))
        BrightnessSlider(
            color = current,
            onColorChange = onChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TargetPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .pointerInput(onClick) { detectTapGestures { onClick() } },
    ) {
        Text(
            text = label,
            style = MeteoType.value,
            color = if (selected) Color.Black else Color.White,
        )
    }
}

/** Le tinte piu' rapide da scegliere, senza aprire la ruota. */
private val PRESET_COLORS: List<Color> = listOf(
    skyColors(SkyState.Giorno).background,
    skyColors(SkyState.of(-0.9f)).background,
    Color(0xFFEFEFF2),
    Color(0xFF1D2026),
    Color(0xFFFFFFFF),
    Color(0xFF000000),
    Color(0xFF3C8DF5),
    Color(0xFFFFDE59),
    Color(0xFFC9331D),
    Color(0xFF2E5C92),
    Color(0xFFCFA255),
)

@Composable
private fun PresetRow(selected: Color, onPick: (Color) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items = PRESET_COLORS) { swatch ->
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(
                        width = if (swatch == selected) 2.dp else 1.dp,
                        color = if (swatch == selected) Color.White else Color.White.copy(alpha = 0.25f),
                        shape = CircleShape,
                    )
                    .pointerInput(swatch) { detectTapGestures { onPick(swatch) } },
            )
        }
    }
}

private fun Color.toHsv(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    return hsv
}

private fun hsvColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

/**
 * Ruota classica tonalita'/saturazione: angolo = tonalita', distanza dal
 * centro = saturazione. La luminosita' resta quella corrente e si cambia
 * dallo slider accanto.
 */
@Composable
private fun HueSaturationWheel(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = color.toHsv()

    fun updateFrom(offset: Offset, radius: Float, center: Offset) {
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val distance = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (angle < 0f) angle += 360f
        onColorChange(hsvColor(angle, (distance / radius).coerceIn(0f, 1f), hsv[2]))
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val radius = min(size.width, size.height) / 2f
                    updateFrom(offset, radius, Offset(size.width / 2f, size.height / 2f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val radius = min(size.width, size.height) / 2f
                    updateFrom(change.position, radius, Offset(size.width / 2f, size.height / 2f))
                }
            },
    ) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.sweepGradient(
                colors = (0..360 step 30).map { hsvColor(it.toFloat(), 1f, 1f) },
                center = center,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        val angle = Math.toRadians(hsv[0].toDouble())
        val distance = hsv[1] * radius
        val handle = Offset(
            center.x + (distance * cos(angle)).toFloat(),
            center.y + (distance * sin(angle)).toFloat(),
        )
        drawCircle(color = Color.Black, radius = 7f, center = handle, style = Stroke(width = 2f))
        drawCircle(color = Color.White, radius = 5.5f, center = handle)
    }
}

@Composable
private fun BrightnessSlider(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = color.toHsv()
    Slider(
        value = hsv[2],
        onValueChange = { value -> onColorChange(hsvColor(hsv[0], hsv[1], value)) },
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
        ),
    )
}
