package com.wanderwildwood.soramoyo.ui

/**
 * The sky to draw over each hour of Today's strip: the first hour's, then only an hour whose
 * sky differs from the one before it. Twelve clouds in a row say "cloudy" twelve times; one
 * cloud, then drizzle where it starts, says when it changes, which is what the strip is for.
 */
internal fun changeMarks(skies: List<Weather>): List<Weather?> =
    skies.mapIndexed { i, sky -> if (i == 0 || sky != skies[i - 1]) sky else null }
