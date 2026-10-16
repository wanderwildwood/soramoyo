package com.wanderwildwood.soramoyo.almanac

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The sun's day: its rise and set, and the dawn and dusk either side, in the place's own time.
 * Each is null on a day it does not happen: the sun that never sets in a northern June, or
 * the night that never gets fully dark there for weeks either side of it.
 */
data class SunDay(
    val astronomicalDawn: LocalDateTime?,
    val nauticalDawn: LocalDateTime?,
    val civilDawn: LocalDateTime?,
    val rise: LocalDateTime?,
    val set: LocalDateTime?,
    val civilDusk: LocalDateTime?,
    val nauticalDusk: LocalDateTime?,
    val astronomicalDusk: LocalDateTime?,
    /** Whether the sun is up at noon, which says which way a day with no rise or set went. */
    val upAtNoon: Boolean,
    /** Whether the sun is above astronomical twilight at midnight, the night that never gets dark. */
    val twilightAtMidnight: Boolean,
)

/**
 * The moon's day: when it rises and sets and the compass bearing of each, in degrees from
 * north, and how it looks. A day with no rise, or no set, has null for it: the moon rises
 * about fifty minutes later each day, so once a month one of them falls past midnight.
 */
data class MoonDay(
    val rise: LocalDateTime?,
    val riseAzimuth: Double?,
    val set: LocalDateTime?,
    val setAzimuth: Double?,
    val phase: Phase,
    /** The principal phase that falls in this day, if one does. */
    val principal: Principal?,
)

/**
 * How the moon looks at a moment: how far round from new it is, in degrees (0 new, 90 first
 * quarter, 180 full, 270 last quarter), and the fraction of its face that is lit.
 */
data class Phase(val angle: Double, val lit: Double) {
    val waxing: Boolean get() = angle < 180.0

    /** The eight names, a principal one only for the day it falls in. */
    fun name(principal: Principal?): MoonName = when (principal) {
        Principal.NEW -> MoonName.NEW
        Principal.FIRST_QUARTER -> MoonName.FIRST_QUARTER
        Principal.FULL -> MoonName.FULL
        Principal.LAST_QUARTER -> MoonName.LAST_QUARTER
        null -> when {
            angle < 90.0 -> MoonName.WAXING_CRESCENT
            angle < 180.0 -> MoonName.WAXING_GIBBOUS
            angle < 270.0 -> MoonName.WANING_GIBBOUS
            else -> MoonName.WANING_CRESCENT
        }
    }
}

enum class Principal(val angle: Double) { NEW(0.0), FIRST_QUARTER(90.0), FULL(180.0), LAST_QUARTER(270.0) }

enum class MoonName {
    NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT,
}

/** A principal phase and the moment it falls. */
data class PhaseAt(val principal: Principal, val at: Instant)

/**
 * The sun and moon worked out on the phone, from the date and where it is: nothing is fetched,
 * so the almanac is there with no signal, for a place chosen or for wherever the phone last was.
 *
 * The positions are Paul Schlyter's ("How to compute planetary positions"): the orbits with the
 * moon's twelve largest perturbations, good to a minute or two of arc. Rise and set are found by
 * stepping through the day ten minutes at a time and halving the step where the height crosses
 * the line, which copes with the days other methods trip on: two moonsets, or none.
 */
object Almanac {

    /** The sun's centre at rise and set: refraction and the half of it still showing. */
    private const val SUN_HORIZON = -0.833
    private const val CIVIL = -6.0
    private const val NAUTICAL = -12.0
    private const val ASTRONOMICAL = -18.0

    fun sun(date: LocalDate, zone: ZoneId, lat: Double, lon: Double): SunDay {
        val height = { t: Instant -> sunHeight(t, lat, lon) }
        fun crossing(line: Double, rising: Boolean) = crossings(date, zone, line, height)
            .firstOrNull { it.second == rising }?.first?.let { local(it, zone) }
        val noon = date.atTime(12, 0).atZone(zone).toInstant()
        val midnight = date.atStartOfDay(zone).toInstant()
        return SunDay(
            astronomicalDawn = crossing(ASTRONOMICAL, true),
            nauticalDawn = crossing(NAUTICAL, true),
            civilDawn = crossing(CIVIL, true),
            rise = crossing(SUN_HORIZON, true),
            set = crossing(SUN_HORIZON, false),
            civilDusk = crossing(CIVIL, false),
            nauticalDusk = crossing(NAUTICAL, false),
            astronomicalDusk = crossing(ASTRONOMICAL, false),
            upAtNoon = height(noon) > SUN_HORIZON,
            twilightAtMidnight = height(midnight) > ASTRONOMICAL,
        )
    }

    fun moon(date: LocalDate, zone: ZoneId, lat: Double, lon: Double): MoonDay {
        // The moon's horizon moves with its distance: its parallax lifts it, its size and the
        // refraction lower the line its centre crosses.
        val height = { t: Instant -> moonHeight(t, lat, lon) }
        val all = crossings(date, zone, 0.0, height)
        val rise = all.firstOrNull { it.second }?.first
        val set = all.firstOrNull { !it.second }?.first
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return MoonDay(
            rise = rise?.let { local(it, zone) },
            riseAzimuth = rise?.let { moonAzimuth(it, lat, lon) },
            set = set?.let { local(it, zone) },
            setAzimuth = set?.let { moonAzimuth(it, lat, lon) },
            phase = phase(date.atTime(12, 0).atZone(zone).toInstant()),
            principal = phases(start, 1).first().takeIf { it.at.isBefore(end) }?.principal,
        )
    }

    /** How the moon looks at [at]. */
    fun phase(at: Instant): Phase {
        val d = dayNumber(at)
        val sun = sunEcliptic(d)
        val moon = moonEcliptic(d)
        val angle = norm(moon.lon - sun.lon)
        // The elongation, from both longitudes and the moon's latitude off the ecliptic.
        val elongation = Math.toDegrees(acos(cosd(moon.lat) * cosd(moon.lon - sun.lon)))
        return Phase(angle, (1 - cosd(elongation)) / 2)
    }

    /**
     * The next [count] principal phases from [from]: new, first quarter, full and last quarter,
     * each found where the moon's angle round from the sun passes ninety degrees more.
     */
    fun phases(from: Instant, count: Int): List<PhaseAt> {
        val found = ArrayList<PhaseAt>()
        var t = from
        var before = phase(t).angle
        // Six hours a step: the angle moves about three degrees in it, never a whole quarter.
        while (found.size < count) {
            val next = t.plus(6, ChronoUnit.HOURS)
            val after = phase(next).angle
            Principal.entries.forEach { p ->
                if (passes(before, after, p.angle)) found += PhaseAt(p, refinePhase(t, next, p.angle))
            }
            t = next
            before = after
        }
        return found.sortedBy { it.at }.take(count)
    }

    /** Whether the angle went past [target] between [a] and [b], going round through 360 to 0. */
    private fun passes(a: Double, b: Double, target: Double): Boolean {
        val from = norm(a - target)
        val to = norm(b - target)
        // Just short of the target, then just past it: from near 360 round to near 0.
        return from > 180.0 && to < 180.0
    }

    private fun refinePhase(lo0: Instant, hi0: Instant, target: Double): Instant {
        var lo = lo0
        var hi = hi0
        repeat(20) {
            val mid = lo.plusMillis(ChronoUnit.MILLIS.between(lo, hi) / 2)
            if (norm(phase(mid).angle - target) > 180.0) lo = mid else hi = mid
        }
        return hi.truncatedTo(ChronoUnit.MINUTES)
    }

    /**
     * Where the height crosses [line] within the local day: each moment, and whether it was
     * rising through it.
     */
    private fun crossings(
        date: LocalDate,
        zone: ZoneId,
        line: Double,
        height: (Instant) -> Double,
    ): List<Pair<Instant, Boolean>> {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val out = ArrayList<Pair<Instant, Boolean>>()
        var t = start
        var h = height(t) - line
        while (t.isBefore(end)) {
            val next = t.plus(10, ChronoUnit.MINUTES).let { if (it.isAfter(end)) end else it }
            val hn = height(next) - line
            if ((h < 0) != (hn < 0)) {
                var lo = t
                var hi = next
                var hlo = h
                repeat(12) {
                    val mid = lo.plusMillis(ChronoUnit.MILLIS.between(lo, hi) / 2)
                    val hm = height(mid) - line
                    if ((hm < 0) == (hlo < 0)) {
                        lo = mid
                        hlo = hm
                    } else {
                        hi = mid
                    }
                }
                out += hi to (h < 0)
            }
            t = next
            h = hn
        }
        return out
    }

    private fun local(t: Instant, zone: ZoneId): LocalDateTime =
        // Rounded to the minute, as an almanac gives it; seconds would claim more than it knows.
        LocalDateTime.ofInstant(t.plusSeconds(30), zone).truncatedTo(ChronoUnit.MINUTES)

    // ---- Positions ----

    private class Ecliptic(val lon: Double, val lat: Double, val distance: Double)

    /** Days since 2000 January 0.0 UT, Schlyter's day number. */
    internal fun dayNumber(t: Instant): Double = t.toEpochMilli() / 86_400_000.0 + 2440587.5 - 2451543.5

    private fun obliquity(d: Double) = 23.4393 - 3.563e-7 * d

    private fun sunEcliptic(d: Double): Ecliptic {
        val w = 282.9404 + 4.70935e-5 * d
        val e = 0.016709 - 1.151e-9 * d
        val m = norm(356.0470 + 0.9856002585 * d)
        val ea = m + Math.toDegrees(e * sind(m) * (1 + e * cosd(m)))
        val xv = cosd(ea) - e
        val yv = sqrt(1 - e * e) * sind(ea)
        val v = Math.toDegrees(atan2(yv, xv))
        return Ecliptic(norm(v + w), 0.0, sqrt(xv * xv + yv * yv))
    }

    private fun moonEcliptic(d: Double): Ecliptic {
        val n = norm(125.1228 - 0.0529538083 * d)
        val i = 5.1454
        val w = norm(318.0634 + 0.1643573223 * d)
        val a = 60.2666
        val e = 0.054900
        val m = norm(115.3654 + 13.0649929509 * d)

        var ea = m + Math.toDegrees(e * sind(m) * (1 + e * cosd(m)))
        repeat(5) {
            ea -= (ea - Math.toDegrees(e * sind(ea)) - m) / (1 - e * cosd(ea))
        }
        val xv = a * (cosd(ea) - e)
        val yv = a * sqrt(1 - e * e) * sind(ea)
        val v = Math.toDegrees(atan2(yv, xv))
        var r = sqrt(xv * xv + yv * yv)

        val xh = r * (cosd(n) * cosd(v + w) - sind(n) * sind(v + w) * cosd(i))
        val yh = r * (sind(n) * cosd(v + w) + cosd(n) * sind(v + w) * cosd(i))
        val zh = r * sind(v + w) * sind(i)
        var lon = Math.toDegrees(atan2(yh, xh))
        var lat = Math.toDegrees(atan2(zh, sqrt(xh * xh + yh * yh)))

        // The sun pulls the moon off its ellipse; these are the largest of the effects.
        val ms = norm(356.0470 + 0.9856002585 * d)
        val ls = ms + 282.9404 + 4.70935e-5 * d
        val lm = m + w + n
        val dd = lm - ls
        val f = lm - n
        lon += -1.274 * sind(m - 2 * dd) + 0.658 * sind(2 * dd) - 0.186 * sind(ms) -
            0.059 * sind(2 * m - 2 * dd) - 0.057 * sind(m - 2 * dd + ms) + 0.053 * sind(m + 2 * dd) +
            0.046 * sind(2 * dd - ms) + 0.041 * sind(m - ms) - 0.035 * sind(dd) -
            0.031 * sind(m + ms) - 0.015 * sind(2 * f - 2 * dd) + 0.011 * sind(m - 4 * dd)
        lat += -0.173 * sind(f - 2 * dd) - 0.055 * sind(m - f - 2 * dd) - 0.046 * sind(m + f - 2 * dd) +
            0.033 * sind(f + 2 * dd) + 0.017 * sind(2 * m + f)
        r += -0.58 * cosd(m - 2 * dd) - 0.46 * cosd(2 * dd)
        return Ecliptic(norm(lon), lat, r)
    }

    /** Right ascension and declination, in degrees, from a place on the ecliptic. */
    private fun equatorial(p: Ecliptic, d: Double): Pair<Double, Double> {
        val ecl = obliquity(d)
        val x = cosd(p.lon) * cosd(p.lat)
        val y = sind(p.lon) * cosd(p.lat)
        val z = sind(p.lat)
        val ye = y * cosd(ecl) - z * sind(ecl)
        val ze = y * sind(ecl) + z * cosd(ecl)
        return Math.toDegrees(atan2(ye, x)) to Math.toDegrees(atan2(ze, sqrt(x * x + ye * ye)))
    }

    /** The local hour angle of a right ascension, in degrees. */
    private fun hourAngle(t: Instant, lon: Double, ra: Double): Double {
        val jd = dayNumber(t) + 2451543.5
        val gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0)
        return norm(gmst + lon - ra)
    }

    private fun altitude(lat: Double, dec: Double, ha: Double): Double =
        Math.toDegrees(asin(sind(lat) * sind(dec) + cosd(lat) * cosd(dec) * cosd(ha)))

    private fun azimuth(lat: Double, dec: Double, ha: Double): Double =
        norm(Math.toDegrees(atan2(sind(ha), cosd(ha) * sind(lat) - tan(Math.toRadians(dec)) * cosd(lat))) + 180.0)

    internal fun sunHeight(t: Instant, lat: Double, lon: Double): Double {
        val d = dayNumber(t)
        val (ra, dec) = equatorial(sunEcliptic(d), d)
        return altitude(lat, dec, hourAngle(t, lon, ra))
    }

    /**
     * How far the moon's centre is above the line it crosses as it rises and sets: its height
     * seen from the ground rather than the earth's centre, less the refraction and its own
     * half-width.
     */
    internal fun moonHeight(t: Instant, lat: Double, lon: Double): Double {
        val d = dayNumber(t)
        val p = moonEcliptic(d)
        val (ra, dec) = equatorial(p, d)
        val geocentric = altitude(lat, dec, hourAngle(t, lon, ra))
        val parallax = Math.toDegrees(asin(1 / p.distance))
        val seen = geocentric - parallax * cosd(geocentric)
        return seen + 0.5667 + 0.2725 * parallax
    }

    private fun moonAzimuth(t: Instant, lat: Double, lon: Double): Double {
        val d = dayNumber(t)
        val (ra, dec) = equatorial(moonEcliptic(d), d)
        return azimuth(lat, dec, hourAngle(t, lon, ra))
    }

    private fun sind(x: Double) = sin(Math.toRadians(x))
    private fun cosd(x: Double) = cos(Math.toRadians(x))
    private fun norm(x: Double) = x - 360.0 * floor(x / 360.0)
}
