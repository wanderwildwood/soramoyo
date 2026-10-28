package com.wanderwildwood.soramoyo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset

// The weather set: eleven marks drawn on a 48 grid, stroke only, round caps and joins,
// black, no fill except a white one where a cloud must hide what is behind it. The path data is
// plain SVG so each converts one-to-one to a VectorDrawable when this is built for real.

enum class Weather(val label: String) {
    CLEAR("clear"),
    CLEAR_NIGHT("clear night"),
    PARTLY("partly"),
    PARTLY_NIGHT("partly night"),
    CLOUDY("cloudy"),
    FOG("fog"),
    DRIZZLE("drizzle"),
    RAIN("rain"),
    HEAVY_RAIN("heavy rain"),
    SNOW("snow"),
    THUNDER("thunder");
}

/** Each mark as (path data, whiteFill) in drawing order. */
private val MARKS: Map<Weather, List<Pair<String, Boolean>>> = mapOf(
    Weather.CLEAR to listOf(
        "M16 24 a8 8 0 1 0 16 0 a8 8 0 1 0 -16 0 Z" to false,
        "M37.5 24.0 L42.5 24.0 M33.5 33.5 L37.1 37.1 M24.0 37.5 L24.0 42.5 M14.5 33.5 L10.9 37.1 M10.5 24.0 L5.5 24.0 M14.5 14.5 L10.9 10.9 M24.0 10.5 L24.0 5.5 M33.5 14.5 L37.1 10.9" to false,
    ),
    Weather.CLEAR_NIGHT to listOf(
        "M26.1 10.3 A14 14 0 1 0 37.7 26.1 A11.2 11.2 0 0 1 26.1 10.3 Z" to false,
    ),
    Weather.PARTLY to listOf(
        "M11 17 a6 6 0 1 0 12 0 a6 6 0 1 0 -12 0 Z" to false,
        "M27.5 17.0 L31.5 17.0 M24.4 24.4 L27.3 27.3 M17.0 27.5 L17.0 31.5 M9.6 24.4 L6.7 27.3 M6.5 17.0 L2.5 17.0 M9.6 9.6 L6.7 6.7 M17.0 6.5 L17.0 2.5 M24.4 9.6 L27.3 6.7" to false,
        "M14.0 38.0 H36.0 A7.0 7.0 0 0 0 37.0 24.5 A9.0 9.0 0 0 0 19.5 23.0 A8.0 8.0 0 0 0 14.0 38.0 Z" to true,
    ),
    Weather.PARTLY_NIGHT to listOf(
        "M19.5 8.2 A10 10 0 1 0 27.8 19.5 A8.0 8.0 0 0 1 19.5 8.2 Z" to false,
        "M14.0 38.0 H36.0 A7.0 7.0 0 0 0 37.0 24.5 A9.0 9.0 0 0 0 19.5 23.0 A8.0 8.0 0 0 0 14.0 38.0 Z" to true,
    ),
    Weather.CLOUDY to listOf(
        "M23.5 18.0 H40.0 A5.2 5.2 0 0 0 40.8 7.9 A6.8 6.8 0 0 0 27.6 6.8 A6.0 6.0 0 0 0 23.5 18.0 Z" to false,
        "M10.9 35.0 H36.2 A8.0 8.0 0 0 0 37.3 19.5 A10.3 10.3 0 0 0 17.2 17.8 A9.2 9.2 0 0 0 10.9 35.0 Z" to true,
    ),
    Weather.FOG to listOf(
        "M11.6 24.0 H35.8 A7.7 7.7 0 0 0 36.9 9.2 A9.9 9.9 0 0 0 17.6 7.5 A8.8 8.8 0 0 0 11.6 24.0 Z" to false,
        "M7 31 H41" to false,
        "M12 37.5 H36" to false,
        "M7 44 H30" to false,
    ),
    Weather.DRIZZLE to listOf(
        "M11.6 25.0 H35.8 A7.7 7.7 0 0 0 36.9 10.2 A9.9 9.9 0 0 0 17.6 8.5 A8.8 8.8 0 0 0 11.6 25.0 Z" to false,
        "M14 33 l-2.025 4.05 M24 33 l-2.025 4.05 M34 33 l-2.025 4.05" to false,
        "M19 40 l-2.025 4.05 M29 40 l-2.025 4.05" to false,
    ),
    Weather.RAIN to listOf(
        "M11.6 25.0 H35.8 A7.7 7.7 0 0 0 36.9 10.2 A9.9 9.9 0 0 0 17.6 8.5 A8.8 8.8 0 0 0 11.6 25.0 Z" to false,
        "M14 33 l-3.6 7.2 M24 33 l-3.6 7.2 M34 33 l-3.6 7.2" to false,
    ),
    Weather.HEAVY_RAIN to listOf(
        "M11.6 23.0 H35.8 A7.7 7.7 0 0 0 36.9 8.2 A9.9 9.9 0 0 0 17.6 6.5 A8.8 8.8 0 0 0 11.6 23.0 Z" to false,
        "M13 29 l-3.375 6.75 M21 29 l-3.375 6.75 M29 29 l-3.375 6.75 M37 29 l-3.375 6.75" to false,
        "M17 39 l-3.375 6.75 M25 39 l-3.375 6.75 M33 39 l-3.375 6.75" to false,
    ),
    Weather.SNOW to listOf(
        "M11.6 25.0 H35.8 A7.7 7.7 0 0 0 36.9 10.2 A9.9 9.9 0 0 0 17.6 8.5 A8.8 8.8 0 0 0 11.6 25.0 Z" to false,
        "M13 34 h0.01 M24 34 h0.01 M35 34 h0.01 M18.5 42 h0.01 M29.5 42 h0.01" to false,
    ),
    Weather.THUNDER to listOf(
        "M11.6 23.0 H35.8 A7.7 7.7 0 0 0 36.9 8.2 A9.9 9.9 0 0 0 17.6 6.5 A8.8 8.8 0 0 0 11.6 23.0 Z" to false,
        "M27 28 L19.5 38.5 H28 L22 47" to false,
    ),
)

/** Open-Meteo's WMO code, as one of the eleven; [night] swaps the sun for the moon. */
fun weatherFor(code: Int, night: Boolean): Weather = when (code) {
    0, 1 -> if (night) Weather.CLEAR_NIGHT else Weather.CLEAR
    2 -> if (night) Weather.PARTLY_NIGHT else Weather.PARTLY
    3 -> Weather.CLOUDY
    45, 48 -> Weather.FOG
    51, 53, 55, 56, 57 -> Weather.DRIZZLE
    65, 82 -> Weather.HEAVY_RAIN
    61, 63, 66, 67, 80, 81 -> Weather.RAIN
    71, 73, 75, 77, 85, 86 -> Weather.SNOW
    95, 96, 99 -> Weather.THUNDER
    else -> if (night) Weather.CLEAR_NIGHT else Weather.CLOUDY
}

/**
 * One weather mark at [size]. [weight] is the stroke on the 48 grid: 3.2 for the large one,
 * 3.8 for the small ones in the strip, so a thin line does not go grey on the panel.
 */
@Composable
fun WeatherMark(weather: Weather, size: Dp, weight: Float = 3.2f, modifier: Modifier = Modifier) {
    val paths = remember(weather) { MARKS.getValue(weather).map { (d, white) -> PathParser().parsePathString(d).toPath() to white } }
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(size)) {
        scale(this.size.width / 48f, pivot = Offset.Zero) {
            val stroke = Stroke(weight, cap = StrokeCap.Round, join = StrokeJoin.Round)
            paths.forEach { (path: Path, white) ->
                if (white) drawPath(path, Color.White)
                drawPath(path, ink, style = stroke)
            }
        }
    }
}

/**
 * The wind arrow: points where the wind is GOING, as on a weather map, so a wind from the
 * north points down. The words beside it say where it comes from.
 */
@Composable
fun WindArrow(from: Int, size: Dp, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val shaft = remember { PathParser().parsePathString("M24 43 V7 M12 19 L24 7 L36 19").toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 48f, pivot = Offset.Zero) {
            rotate((from + 180).toFloat(), pivot = Offset(24f, 24f)) {
                drawPath(shaft, ink, style = Stroke(4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
