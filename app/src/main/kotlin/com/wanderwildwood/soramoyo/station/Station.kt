package com.wanderwildwood.soramoyo.station

import org.json.JSONArray
import org.json.JSONObject

/**
 * A number and the unit the gateway gave it in.
 *
 * The gateway is the one place its owner has already said how they measure weather, so the
 * units are taken from it rather than asked for again here: a reading arrives as "0.51 in" or
 * "12.9 mm", and it is shown the same way. [text] is the number as the gateway wrote it, so a
 * rain total keeps the two places its gauge measures to.
 */
data class Measure(val number: Double, val unit: String, val text: String = number.toString())

/** One reading of an Ecowitt gateway's live data, with anything it did not report left null. */
data class StationReading(
    val temperature: Measure?,
    val feelsLike: Measure?,
    val dewPoint: Measure?,
    val humidity: Int?,
    val windSpeed: Measure?,
    val windGust: Measure?,
    /** Degrees clockwise from north that the wind is coming *from*. */
    val windFrom: Int?,
    val rainToday: Measure?,
    val rainRate: Measure?,
    /** True or false only from a gauge that can tell; a tipping bucket cannot. */
    val raining: Boolean?,
    val pressure: Measure?,
    val uvIndex: Double?,
    val soilMoisture: Int?,
) {
    /** Whether the gateway reads in Celsius, which decides the forecast's units too. */
    val metric: Boolean? get() = temperature?.unit?.let { it.equals("C", ignoreCase = true) }

    companion object {
        /**
         * Reads the `get_livedata_info` document an Ecowitt gateway serves on the local
         * network. The ids are the gateway's own and there is no published schema, so this is
         * what a GW3000 with a WS90 was seen to send, and nothing else is assumed.
         *
         * A sensor between readings reports "--", and a sensor that is not fitted is simply
         * absent. Both come out as null.
         */
        fun parse(json: String): StationReading {
            val root = JSONObject(json)
            val common = byId(root.optJSONArray("common_list"))
            // A WS90 or WS85 reports its haptic gauge as piezoRain; a tipping bucket as rain.
            // Both use the same ids, and a station with both prefers the one it was set to.
            val rain = byId(root.optJSONArray("piezoRain") ?: root.optJSONArray("rain"))
            val indoor = root.optJSONArray("wh25")?.optJSONObject(0)
            val soil = root.optJSONArray("ch_soil")?.optJSONObject(0)

            return StationReading(
                temperature = common["0x02"]?.let(::measure),
                feelsLike = common["3"]?.let(::measure),
                dewPoint = common["0x03"]?.let(::measure),
                humidity = common["0x07"]?.let(::measure)?.number?.toInt(),
                windSpeed = common["0x0B"]?.let(::measure),
                windGust = common["0x0C"]?.let(::measure),
                windFrom = (common["0x6D"] ?: common["0x0A"])?.let(::measure)?.number?.toInt(),
                rainToday = rain["0x10"]?.let(::measure),
                rainRate = rain["0x0E"]?.let(::measure),
                raining = rain["srain_piezo"]?.optString("val")?.let {
                    when (it) {
                        "1" -> true
                        "0" -> false
                        else -> null
                    }
                },
                pressure = indoor?.let { measure(it.optString("rel"), null) },
                uvIndex = common["0x17"]?.let(::measure)?.number,
                soilMoisture = soil?.let { measure(it.optString("humidity"), null) }?.number?.toInt(),
            )
        }

        private fun byId(list: JSONArray?): Map<String, JSONObject> {
            if (list == null) return emptyMap()
            val out = HashMap<String, JSONObject>()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isNotEmpty()) out[id] = item
            }
            return out
        }

        private fun measure(item: JSONObject): Measure? =
            measure(item.optString("val"), item.optString("unit").ifEmpty { null })

        /**
         * "69.6" with a unit beside it, "0.00 mph", "91%", "28.61 inHg". The number is the
         * first word; the unit is whatever the gateway put after it, or in its own field.
         */
        internal fun measure(text: String, unit: String?): Measure? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("--")) return null
            val percent = trimmed.endsWith("%")
            val words = trimmed.removeSuffix("%").trim().split(Regex("\\s+"), limit = 2)
            val number = words[0].toDoubleOrNull() ?: return null
            val after = words.getOrNull(1)?.trim().orEmpty()
            return Measure(
                number = number,
                unit = unit ?: if (percent) "%" else after,
                text = words[0],
            )
        }
    }
}
