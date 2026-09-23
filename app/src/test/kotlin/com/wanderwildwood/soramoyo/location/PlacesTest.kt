package com.wanderwildwood.soramoyo.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    /** Five towns called Portland, as Open-Meteo answered: each needs its state to be told apart. */
    @Test
    fun readsARealSearch() {
        val places = Places.parse(fixture("open-meteo-places-portland.json"))
        assertEquals(5, places.size)
        assertEquals(Place("Portland, Oregon, United States", 45.52, -122.68), places[0])
        assertEquals(Place("Portland, Maine, United States", 43.66, -70.26), places[1])
    }

    /** An answer with nothing in it has no "results" key at all. */
    @Test
    fun nothingFoundIsAnEmptyList() {
        assertTrue(Places.parse("""{"generationtime_ms":0.41}""").isEmpty())
    }

    @Test
    fun aPlaceWithNoRegionIsNamedByWhatItHas() {
        val places = Places.parse(
            """{"results":[{"name":"Monaco","latitude":43.73,"longitude":7.42,"country":"Monaco"}]}""",
        )
        assertEquals("Monaco", places.single().label)
    }

    /** What follows a comma picks one town out of several of the same name. */
    @Test
    fun aRegionAfterACommaNarrowsTheAnswer() {
        assertEquals("Portland" to "maine", Places.split(" Portland , maine"))
        val all = Places.parse(fixture("open-meteo-places-portland.json"))
        assertEquals(listOf("Portland, Maine, United States"), Places.narrow(all, "maine").map { it.label })
        // A region nothing matches leaves the whole answer, rather than an empty one.
        assertEquals(all, Places.narrow(all, "Narnia"))
        // The name itself is not searched again: "Portland" is in every label.
        assertEquals(all, Places.narrow(all, ""))
    }
}
