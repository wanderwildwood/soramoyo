package com.wanderwildwood.soramoyo.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicencesListTest {
    @Test fun `the list of what the app is built with is whole`() {
        assertTrue(LIBRARIES.size >= 6)
        assertTrue(LIBRARIES.all { it.name.isNotBlank() && it.licence.isNotBlank() && it.link.startsWith("https://") })
        assertTrue(LIBRARIES.any { it.licence == "Apache-2.0" } && LIBRARIES.any { it.licence == "OFL-1.1" })
    }

    @Test fun `the source link is the public repository`() {
        assertEquals("https://github.com/wanderwildwood/soramoyo", SOURCE_URL)
    }

    @Test fun `every licence text the page shows is bundled and long enough to be the text`() {
        val raw = File("src/main/res/raw")
        for (name in listOf("gpl_3_0.txt", "apache_2_0.txt", "ofl_1_1.txt")) {
            assertTrue(name, File(raw, name).readText().length > 3000)
        }
    }

    @Test fun `paragraphs keep every word`() {
        val text = File("src/main/res/raw/gpl_3_0.txt").readText()
        val words = { t: String -> t.split(Regex("\\s+")).filter { it.isNotEmpty() } }
        assertEquals(words(text), words(licenceParagraphs(text).joinToString(" ")))
    }
}
