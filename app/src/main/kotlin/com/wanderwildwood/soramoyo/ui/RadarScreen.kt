package com.wanderwildwood.soramoyo.ui

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.map.MapData
import com.wanderwildwood.soramoyo.map.MapProjection
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * kRadar's radar, as a tab: RainViewer's last two hours over a vector map centred on the
 * phone or the chosen place, a locally estimated half hour ahead, and the controls to step
 * through it.
 *
 * The map, the overlay and the forecast are kRadar's and are drawn as it draws them. What
 * changed is the frame round them: the house's type and icons, and no hidden test mode.
 */
@Composable
fun RadarTab(
    vm: RadarViewModel,
    onAllowLocation: () -> Unit,
    modifier: Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // The activity only tells the view model the app came to the front; opening this tab is
    // a coming to the front too, as far as the radar is concerned.
    LaunchedEffect(Unit) { vm.onForeground() }

    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The frame's time, which on a screen of its own was the title.
        TextMMD(text = headerText(state), style = MaterialTheme.typography.titleMedium)
        // Locally estimated cloud motion behind the ≈ forecast frames.
        state.motion?.let { m ->
            state.framesCenter?.let { c ->
                val kmh = m.speedKmh(c.lat, state.framesZoom).roundToInt()
                // Bearing is meaningless at ~0 speed — don't imply a direction.
                val label = if (kmh == 0) stringResource(R.string.motion_stationary)
                else stringResource(R.string.motion_estimate, compass(m.bearingDeg()), kmh)
                TextMMD(text = label, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Square, and as large as the panel allows once everything else has its line: under
        // the tab row a full-width square pushed RainViewer's credit off the bottom, and that
        // credit is a condition of using its data.
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.permissionDenied -> LocationPrompt(onAllowLocation)
                state.location == null -> TextMMD(text = stringResource(R.string.locating), style = MaterialTheme.typography.bodySmall)
                else -> RadarMap(state)
            }

            if (state.loading) {
                TextMMD(text = stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall)
            }
        }

        state.error?.let {
            TextMMD(text = stringResource(it), style = MaterialTheme.typography.labelSmall)
        }

        Controls(vm, state)

        Spacer(Modifier.height(8.dp))
        // RainViewer's attribution is a condition of using it, not a courtesy.
        TextMMD(text = stringResource(R.string.attribution), style = MaterialTheme.typography.labelSmall)
        state.lastUpdate?.let { t ->
            if (t > 0L) {
                TextMMD(
                    text = stringResource(R.string.updated, updateFmt.format(Date(t * 1000L))),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun LocationPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextMMD(text = stringResource(R.string.need_location), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        OutlinedButtonMMD(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.allow_location), style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * Stacked layers: a STATIC vector base map (borders + cities) that only recomposes
 * when location/zoom/size change, a DYNAMIC radar overlay repainted as the
 * animation advances (shown only when it matches the current projection, so map
 * and radar never drift), and a center "you are here" marker on top.
 */
@Composable
private fun RadarMap(state: RadarUiState) {
    val loc = state.location ?: return
    // Clipped, because a Canvas draws wherever its paths go: without it the borders ran out
    // of the square and down through the controls beneath.
    Box(modifier = Modifier.fillMaxSize().clipToBounds().background(Color.White)) {
        VectorLayer(lat = loc.lat, lon = loc.lon, zoom = state.zoom, tileSize = state.tileSize)
        // Only overlay the raster while it matches the live zoom/center.
        OverlayLayer(if (state.overlayAligned) state.frames.getOrNull(state.currentIndex) else null)
        CenterMarker()
    }
}

/** Static borders + cities. Args are primitives so it skips recomposition during playback. */
@Composable
private fun VectorLayer(lat: Double, lon: Double, zoom: Int, tileSize: Int) {
    val context = LocalContext.current
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = size.minDimension
        val scale = side / tileSize
        val proj = MapProjection(lat, lon, zoom, tileSize)
        val bounds = proj.visibleBounds()
        val margin = 0.5

        // --- state and province lines (Sky): under the borders and thinner, so a state line
        //     never reads as a national one. Culled the same way as the borders.
        val states = Path()
        for (line in MapData.states(context)) {
            var near = false
            var i = 0
            while (i < line.size) {
                if (bounds.contains(line[i].toDouble(), line[i + 1].toDouble(), margin)) {
                    near = true; break
                }
                i += 2
            }
            if (!near) continue
            var j = 0
            while (j < line.size) {
                val p = proj.project(line[j].toDouble(), line[j + 1].toDouble())
                if (j == 0) states.moveTo(p[0] * scale, p[1] * scale) else states.lineTo(p[0] * scale, p[1] * scale)
                j += 2
            }
        }
        drawPath(
            path = states,
            color = Color.Black,
            style = Stroke(width = 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // --- borders ---
        val path = Path()
        for (ring in MapData.borders(context)) {
            // cheap cull: skip rings with no vertex near the viewport
            var near = false
            var i = 0
            while (i < ring.size) {
                if (bounds.contains(ring[i].toDouble(), ring[i + 1].toDouble(), margin)) {
                    near = true; break
                }
                i += 2
            }
            if (!near) continue
            var first = true
            var j = 0
            while (j < ring.size) {
                val p = proj.project(ring[j].toDouble(), ring[j + 1].toDouble())
                val x = p[0] * scale
                val y = p[1] * scale
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                j += 2
            }
        }
        drawPath(
            path = path,
            color = Color.Black,
            style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // --- cities: reveal more as you zoom in (minZoom gate); dots for every
        //     visible place, labels hybrid (full name for important cities, abbr
        //     for the rest) with greedy collision-avoidance so text stays readable.
        //     The label budget grows with zoom since there's more room per area. ---
        val labelBudget = when (zoom) {
            4 -> 22
            5 -> 30
            6 -> 45
            else -> 60
        }
        val labelPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 11f * density
            isAntiAlias = true
        }
        val textH = (labelPaint.fontMetrics.descent - labelPaint.fontMetrics.ascent)
        val placed = ArrayList<Rect>()
        val candidate = Rect()
        var labelCount = 0
        val cities = MapData.cities(context).filter { it.minZoom <= zoom }.sortedBy { it.minZoom }
        for (c in cities) {
            if (!bounds.contains(c.lat, c.lon)) continue
            val p = proj.project(c.lat, c.lon)
            val x = p[0] * scale
            val y = p[1] * scale
            if (x < 0f || y < 0f || x > side || y > side) continue
            drawCircle(color = Color.Black, radius = 2.2f, center = Offset(x, y))

            if (labelCount >= labelBudget) continue
            val text = if (c.minZoom <= 5) c.name else c.abbr
            val w = labelPaint.measureText(text)
            val lx = x + 4f
            val ly = y - 4f
            candidate.set(lx.toInt(), (ly - textH).toInt(), (lx + w).toInt(), ly.toInt())
            var collides = false
            for (r in placed) {
                if (Rect.intersects(r, candidate)) { collides = true; break }
            }
            if (collides) continue
            placed.add(Rect(candidate))
            drawContext.canvas.nativeCanvas.drawText(text, lx, ly, labelPaint)
            labelCount++
        }
    }
}

/** Dynamic radar overlay for the current frame; repaints when [bmp] changes. */
@Composable
private fun OverlayLayer(bmp: android.graphics.Bitmap?) {
    if (bmp == null || bmp.isRecycled) return
    val image = remember(bmp) { bmp.asImageBitmap() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = size.minDimension.toInt()
        drawImage(image = image, dstSize = androidx.compose.ui.unit.IntSize(side, side))
    }
}

/** "You are here" crosshair at the box center (the map is centered on the user). */
@Composable
private fun CenterMarker() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // white halo so the marker reads over dark rain, then a black ring + cross
        drawCircle(Color.White, radius = 6.5f, center = Offset(cx, cy), style = Stroke(width = 3f))
        drawCircle(Color.Black, radius = 6.5f, center = Offset(cx, cy), style = Stroke(width = 1.6f))
        drawLine(Color.Black, Offset(cx - 10f, cy), Offset(cx + 10f, cy), strokeWidth = 1.6f)
        drawLine(Color.Black, Offset(cx, cy - 10f), Offset(cx, cy + 10f), strokeWidth = 1.6f)
    }
}

@Composable
private fun Controls(vm: RadarViewModel, state: RadarUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(Icons.Minus, stringResource(R.string.zoom_out)) { vm.zoomOut() }
        RoundButton(Icons.StepBack, stringResource(R.string.step_back)) { vm.stepBack() }
        RoundButton(
            icon = if (state.isPlaying) Icons.Pause else Icons.Play,
            description = if (state.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
        ) { vm.togglePlay() }
        RoundButton(Icons.StepForward, stringResource(R.string.step_fwd)) { vm.stepForward() }
        RoundButton(Icons.Plus, stringResource(R.string.zoom_in)) { vm.zoomIn() }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

// The phone's own way of writing a time, rather than kRadar's Czech one.
private val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)
private val updateFmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

@Composable
private fun headerText(state: RadarUiState): String {
    val t = state.currentTime ?: return stringResource(R.string.radar_title)
    if (t <= 0L) return stringResource(R.string.radar_title)
    val time = timeFmt.format(Date(t * 1000L))
    // Latest past frame shows the "now" tag. Forecast frames get a glyph beside the
    // time so they read as distinct from real radar: ≈ for our local estimate,
    // ▲ for a real API nowcast (should we ever get a keyed plan).
    val isLatestPast = !state.currentIsNowcast && state.currentIndex == lastPastIndex(state)
    return when {
        state.currentIsEstimated -> "≈ +$time"
        state.currentIsNowcast -> "▲ +$time"
        isLatestPast -> "$time (${stringResource(R.string.now)})"
        else -> time
    }
}

private fun lastPastIndex(state: RadarUiState): Int {
    var idx = -1
    state.frameNowcast.forEachIndexed { i, now -> if (!now) idx = i }
    return idx
}

/** Localized 8-point compass abbreviation for a bearing in degrees (clockwise from N). */
@Composable
private fun compass(bearingDeg: Double): String {
    val dirs = stringArrayResource(R.array.compass_points)
    return dirs[(Math.round(bearingDeg / 45.0).toInt()) % 8]
}

