package com.wanderwildwood.soramoyo.glance

import org.junit.Assert.assertEquals
import org.junit.Test

class ForTheWalkTest {

    /** The game reads its columns by name, but an old build of it may lean on the order: only ever add at the end. */
    @Test
    fun columnsKeepTheirOrderAndTheNewOneComesLast() {
        assertEquals(
            listOf("sky", "change", "minutes", "wind", "gust", "moon_phase", "moonrise", "moonset", "alert",
                "heavy", "snowfall", "hemisphere", "pressure_fall"),
            ForTheWalk.COLUMNS.toList(),
        )
    }
}
