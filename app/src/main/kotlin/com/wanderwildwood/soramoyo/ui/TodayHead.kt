package com.wanderwildwood.soramoyo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.forecast.Day
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** What the head of Today shows, from a station's reading or from the forecast's estimate alike. */
internal class HeadFigures(
    val temperature: Int?,
    val unit: String,
    val feelsLike: Int?,
    val humidity: Int?,
    val pressure: String?,
    val windSpeed: Int?,
    val windUnit: String,
    val windFrom: Int?,
    val gust: Int?,
    val code: Int,
    val night: Boolean,
    val source: String,
)

/** Whether [time] falls between that day's sunset and the next sunrise, for the moon in place of the sun. */
internal fun isNight(time: LocalDateTime, days: List<Day>): Boolean {
    val day = days.firstOrNull { it.date == time.toLocalDate() }
    val rise = day?.sunrise ?: time.toLocalDate().atTime(7, 0)
    val set = day?.sunset ?: time.toLocalDate().atTime(18, 30)
    return time.isBefore(rise) || !time.isBefore(set)
}

/**
 * The temperature and what it feels like on the left, with the humidity and pressure under
 * them and the line saying where the figures came from; on the right the weather drawn, and
 * under it the wind. Everything that used to be a row of its own under the head is here, so the
 * first page has room for the next hours. The sky is a picture only: the day's sentence above
 * says it in words.
 */
@Composable
internal fun TodayHead(f: HeadFigures, wide: Boolean = false) {
    if (wide) {
        TodayHeadNarrow(f)
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
        // The figure with the sky drawn beside it, then under them two columns of the same height:
        // what it feels like, humidity and pressure on the left, the wind and the source on the right.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextMMD(
                text = f.temperature?.let { "$it°" } ?: "–",
                fontSize = 72.sp,
                lineHeight = 66.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = f.unit, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.weight(1f))
            val sky = stringResource(conditionFor(f.code))
            WeatherMark(weatherFor(f.code, f.night), 64.dp, 3.2f, Modifier.semantics { contentDescription = sky })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                f.feelsLike?.let {
                    TextMMD(text = stringResource(R.string.sky_feels_like, it), style = MaterialTheme.typography.bodyMedium)
                }
                f.humidity?.let {
                    TextMMD(text = stringResource(R.string.sky_humidity_pct, it), style = MaterialTheme.typography.bodyMedium)
                }
                f.pressure?.let {
                    TextMMD(text = stringResource(R.string.sky_pressure_line, it), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                f.windSpeed?.let { speed -> HeadWind(speed, f) }
                TextMMD(text = f.source, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
        }
    }
}

/**
 * The head in the narrow column turned sideways leaves it: the figure with the mark beside it,
 * then each of the rest on a line of its own, the wind too.
 */
@Composable
private fun TodayHeadNarrow(f: HeadFigures) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextMMD(text = f.temperature?.let { "$it°" } ?: "–", fontSize = 64.sp, lineHeight = 66.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            TextMMD(text = f.unit, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            val sky = stringResource(conditionFor(f.code))
            WeatherMark(weatherFor(f.code, f.night), 56.dp, 3.4f, Modifier.semantics { contentDescription = sky })
        }
        f.feelsLike?.let { TextMMD(text = stringResource(R.string.sky_feels_like, it), style = MaterialTheme.typography.bodyMedium) }
        f.humidity?.let { TextMMD(text = stringResource(R.string.sky_humidity_pct, it), style = MaterialTheme.typography.bodyMedium) }
        f.pressure?.let { TextMMD(text = stringResource(R.string.sky_pressure_line, it), style = MaterialTheme.typography.bodyMedium) }
        f.windSpeed?.let { HeadWind(it, f, Alignment.Start) }
        Spacer(Modifier.height(4.dp))
        TextMMD(text = f.source, style = MaterialTheme.typography.labelSmall)
    }
}

/** The wind under the mark: an arrow going where it blows and the figure, or "Calm"; the gusts under. */
@Composable
private fun HeadWind(speed: Int, f: HeadFigures, side: Alignment.Horizontal = Alignment.End) {
    val compass = stringArrayResource(R.array.compass_16)
    val from = f.windFrom?.takeIf { speed > 0 }
    val named = from?.let { compass[((it / 22.5).roundToInt()) % 16] }
    val words = stringResource(R.string.sky_wind) + " " +
        if (speed == 0) stringResource(R.string.sky_calm) else listOfNotNull(named, speed.toString(), f.windUnit).joinToString(" ")
    Column(
        horizontalAlignment = side,
        modifier = Modifier.padding(top = 2.dp).semantics(mergeDescendants = true) { contentDescription = words },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (speed == 0) {
                TextMMD(text = stringResource(R.string.sky_calm), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            } else {
                from?.let {
                    WindArrow(it, 28.dp)
                    Spacer(Modifier.width(8.dp))
                }
                TextMMD(
                    text = listOfNotNull(named, speed.toString()).joinToString(" "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(5.dp))
                TextMMD(text = f.windUnit, style = MaterialTheme.typography.labelSmall)
            }
        }
        f.gust?.takeIf { it > speed }?.let {
            TextMMD(text = stringResource(R.string.sky_gusts_to, it, f.windUnit), style = MaterialTheme.typography.labelSmall)
        }
    }
}
