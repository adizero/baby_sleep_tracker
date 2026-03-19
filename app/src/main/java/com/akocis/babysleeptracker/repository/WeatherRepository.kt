package com.akocis.babysleeptracker.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayWeather(
    val date: LocalDate,
    val maxTemp: Double,
    val minTemp: Double,
    val weatherCode: Int
)

data class HourlyWeather(
    val date: LocalDate,
    val hour: Int,
    val temp: Double,
    val weatherCode: Int
)

data class GeoLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null
) {
    val displayName: String
        get() = buildString {
            append(name)
            admin1?.let { append(", $it") }
            country?.let { append(", $it") }
        }
}

class WeatherRepository(private val context: Context) {

    private val cacheDir = File(context.filesDir, "weather_cache")

    init {
        cacheDir.mkdirs()
    }

    suspend fun searchLocations(query: String): List<GeoLocation> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=en")
            val json = fetchJson(url) ?: return@withContext emptyList()
            val results = json.optJSONArray("results") ?: return@withContext emptyList()
            (0 until results.length()).map { i ->
                val r = results.getJSONObject(i)
                GeoLocation(
                    name = r.getString("name"),
                    latitude = r.getDouble("latitude"),
                    longitude = r.getDouble("longitude"),
                    country = r.optString("country", null).takeIf { it != "null" },
                    admin1 = r.optString("admin1", null).takeIf { it != "null" }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Get historical weather data (cached, fetches missing ranges).
     * Returns immediately with cached data, then fetches missing ranges.
     */
    suspend fun getHistorical(
        lat: Double,
        lon: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<LocalDate, DayWeather> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val historicalEnd = today.minusDays(2).coerceAtMost(endDate)
        if (startDate > historicalEnd) return@withContext emptyMap()

        val result = mutableMapOf<LocalDate, DayWeather>()
        result.putAll(loadCachedRange(lat, lon, startDate, historicalEnd))

        val missingRanges = findMissingRanges(result, startDate, historicalEnd)
        for ((rangeStart, rangeEnd) in missingRanges) {
            val fetched = fetchHistorical(lat, lon, rangeStart, rangeEnd)
            result.putAll(fetched)
            cacheWeatherData(lat, lon, fetched)
        }

        result
    }

    /**
     * Get forecast weather data (cached for 1 hour).
     */
    suspend fun getForecast(
        lat: Double,
        lon: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<LocalDate, DayWeather> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val forecastStart = today.minusDays(2).coerceAtLeast(startDate)
        if (forecastStart > endDate) return@withContext emptyMap()

        val fetched = loadOrFetchForecast(lat, lon)
        fetched.filterKeys { it in startDate..endDate }
    }

    /**
     * Get hourly forecast for today and tomorrow.
     */
    suspend fun getHourlyForecast(
        lat: Double,
        lon: Double
    ): List<HourlyWeather> = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&hourly=temperature_2m,weather_code" +
                    "&forecast_days=2" +
                    "&timezone=auto"
            )
            val json = fetchJson(url) ?: return@withContext emptyList()
            val hourly = json.optJSONObject("hourly") ?: return@withContext emptyList()
            val times = hourly.optJSONArray("time") ?: return@withContext emptyList()
            val temps = hourly.optJSONArray("temperature_2m") ?: return@withContext emptyList()
            val codes = hourly.optJSONArray("weather_code") ?: return@withContext emptyList()
            val result = mutableListOf<HourlyWeather>()
            for (i in 0 until times.length()) {
                val timeStr = times.getString(i) // "2026-03-19T14:00"
                val datePart = timeStr.substringBefore("T")
                val date = LocalDate.parse(datePart)
                val hour = timeStr.substringAfter("T").substringBefore(":").toIntOrNull() ?: continue
                val temp = if (temps.isNull(i)) continue else temps.getDouble(i)
                val code = if (codes.isNull(i)) continue else codes.getInt(i)
                result.add(HourlyWeather(date, hour, temp, code))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findMissingRanges(
        existing: Map<LocalDate, DayWeather>,
        start: LocalDate,
        end: LocalDate
    ): List<Pair<LocalDate, LocalDate>> {
        val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()
        var rangeStart: LocalDate? = null
        var d = start
        while (d <= end) {
            if (d !in existing) {
                if (rangeStart == null) rangeStart = d
            } else {
                if (rangeStart != null) {
                    ranges.add(rangeStart to d.minusDays(1))
                    rangeStart = null
                }
            }
            d = d.plusDays(1)
        }
        if (rangeStart != null) {
            ranges.add(rangeStart to end)
        }
        return ranges
    }

    private fun fetchHistorical(
        lat: Double, lon: Double, start: LocalDate, end: LocalDate
    ): Map<LocalDate, DayWeather> {
        // Open-Meteo archive API has a limit; fetch in chunks of 366 days
        val result = mutableMapOf<LocalDate, DayWeather>()
        var chunkStart = start
        while (chunkStart <= end) {
            val chunkEnd = (chunkStart.plusDays(365)).coerceAtMost(end)
            val url = URL(
                "https://archive-api.open-meteo.com/v1/archive" +
                    "?latitude=$lat&longitude=$lon" +
                    "&start_date=$chunkStart&end_date=$chunkEnd" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                    "&timezone=auto"
            )
            val json = fetchJson(url)
            if (json != null) {
                result.putAll(parseDailyWeather(json))
            }
            chunkStart = chunkEnd.plusDays(1)
        }
        return result
    }

    private fun forecastCacheFile(lat: Double, lon: Double): File {
        val latStr = "%.2f".format(lat)
        val lonStr = "%.2f".format(lon)
        return File(cacheDir, "forecast_${latStr}_${lonStr}.json")
    }

    private fun loadOrFetchForecast(lat: Double, lon: Double): Map<LocalDate, DayWeather> {
        val file = forecastCacheFile(lat, lon)
        // Check if cached forecast is fresh (< 1 hour old)
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                val timestamp = json.optLong("timestamp", 0L)
                if (System.currentTimeMillis() - timestamp < 3_600_000) {
                    val data = json.optJSONObject("data")
                    if (data != null) {
                        val result = mutableMapOf<LocalDate, DayWeather>()
                        for (key in data.keys()) {
                            val obj = data.getJSONObject(key)
                            val date = LocalDate.parse(key)
                            val minTemp = obj.optDouble("minTemp", obj.getDouble("temp"))
                            result[date] = DayWeather(date, obj.getDouble("temp"), minTemp, obj.getInt("code"))
                        }
                        return result
                    }
                }
            } catch (_: Exception) { }
        }

        // Fetch fresh forecast
        val fetched = fetchForecastFromApi(lat, lon)

        // Cache it
        try {
            val wrapper = JSONObject()
            wrapper.put("timestamp", System.currentTimeMillis())
            val data = JSONObject()
            for ((date, weather) in fetched) {
                val obj = JSONObject()
                obj.put("temp", weather.maxTemp)
                obj.put("minTemp", weather.minTemp)
                obj.put("code", weather.weatherCode)
                data.put(date.toString(), obj)
            }
            wrapper.put("data", data)
            file.writeText(wrapper.toString())
        } catch (_: Exception) { }

        return fetched
    }

    private fun fetchForecastFromApi(lat: Double, lon: Double): Map<LocalDate, DayWeather> {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                "&past_days=2&forecast_days=16" +
                "&timezone=auto"
        )
        val json = fetchJson(url) ?: return emptyMap()
        return parseDailyWeather(json)
    }

    private fun parseDailyWeather(json: JSONObject): Map<LocalDate, DayWeather> {
        val daily = json.optJSONObject("daily") ?: return emptyMap()
        val dates = daily.optJSONArray("time") ?: return emptyMap()
        val maxTemps = daily.optJSONArray("temperature_2m_max") ?: return emptyMap()
        val minTemps = daily.optJSONArray("temperature_2m_min")
        val codes = daily.optJSONArray("weather_code") ?: return emptyMap()
        val result = mutableMapOf<LocalDate, DayWeather>()
        for (i in 0 until dates.length()) {
            val date = LocalDate.parse(dates.getString(i))
            val maxTemp = if (maxTemps.isNull(i)) continue else maxTemps.getDouble(i)
            val minTemp = if (minTemps == null || minTemps.isNull(i)) maxTemp else minTemps.getDouble(i)
            val code = if (codes.isNull(i)) continue else codes.getInt(i)
            result[date] = DayWeather(date, maxTemp, minTemp, code)
        }
        return result
    }

    private fun fetchJson(url: URL): JSONObject? {
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                JSONObject(body)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // Cache: one file per month per location: weather_48.86_2.35_2026-03.json
    private fun cacheKey(lat: Double, lon: Double, yearMonth: String): File {
        val latStr = "%.2f".format(lat)
        val lonStr = "%.2f".format(lon)
        return File(cacheDir, "weather_${latStr}_${lonStr}_$yearMonth.json")
    }

    private fun cacheWeatherData(lat: Double, lon: Double, data: Map<LocalDate, DayWeather>) {
        // Group by year-month and merge with existing cache
        val byMonth = data.entries.groupBy { "${it.key.year}-${"%02d".format(it.key.monthValue)}" }
        for ((yearMonth, entries) in byMonth) {
            val file = cacheKey(lat, lon, yearMonth)
            val existing = if (file.exists()) {
                try {
                    JSONObject(file.readText())
                } catch (_: Exception) {
                    JSONObject()
                }
            } else {
                JSONObject()
            }
            for ((date, weather) in entries) {
                val dayObj = JSONObject()
                dayObj.put("temp", weather.maxTemp)
                dayObj.put("minTemp", weather.minTemp)
                dayObj.put("code", weather.weatherCode)
                existing.put(date.toString(), dayObj)
            }
            file.writeText(existing.toString())
        }
    }

    private fun loadCachedRange(
        lat: Double, lon: Double, start: LocalDate, end: LocalDate
    ): Map<LocalDate, DayWeather> {
        val result = mutableMapOf<LocalDate, DayWeather>()
        var ym = start.withDayOfMonth(1)
        val endYm = end.withDayOfMonth(1)
        while (ym <= endYm) {
            val yearMonth = "${ym.year}-${"%02d".format(ym.monthValue)}"
            val file = cacheKey(lat, lon, yearMonth)
            if (file.exists()) {
                try {
                    val json = JSONObject(file.readText())
                    for (key in json.keys()) {
                        val date = LocalDate.parse(key)
                        if (date in start..end) {
                            val obj = json.getJSONObject(key)
                            val minTemp = obj.optDouble("minTemp", obj.getDouble("temp"))
                            result[date] = DayWeather(date, obj.getDouble("temp"), minTemp, obj.getInt("code"))
                        }
                    }
                } catch (_: Exception) { }
            }
            ym = ym.plusMonths(1)
        }
        return result
    }

    companion object {
        fun weatherIcon(code: Int): String = when (code) {
            0 -> "\u2600"           // Clear sky - sun
            1 -> "\u26C5"           // Mainly clear - sun behind cloud
            2 -> "\u26C5"           // Partly cloudy
            3 -> "\u2601"           // Overcast - cloud
            45, 48 -> "\uD83C\uDF2B" // Fog
            51, 53, 55 -> "\uD83C\uDF27" // Drizzle
            56, 57 -> "\uD83C\uDF28"     // Freezing drizzle
            61, 63, 65 -> "\uD83C\uDF27" // Rain
            66, 67 -> "\uD83C\uDF28"     // Freezing rain
            71, 73, 75, 77 -> "\u2744"   // Snow
            80, 81, 82 -> "\uD83C\uDF26" // Rain showers
            85, 86 -> "\uD83C\uDF28"     // Snow showers
            95 -> "\u26C8"               // Thunderstorm
            96, 99 -> "\u26C8"           // Thunderstorm with hail
            else -> "\u2601"             // Default cloud
        }

        fun weatherDescription(code: Int): String = when (code) {
            0 -> "Clear sky"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45 -> "Fog"
            48 -> "Rime fog"
            51 -> "Light drizzle"
            53 -> "Moderate drizzle"
            55 -> "Dense drizzle"
            56 -> "Light freezing drizzle"
            57 -> "Dense freezing drizzle"
            61 -> "Slight rain"
            63 -> "Moderate rain"
            65 -> "Heavy rain"
            66 -> "Light freezing rain"
            67 -> "Heavy freezing rain"
            71 -> "Slight snow"
            73 -> "Moderate snow"
            75 -> "Heavy snow"
            77 -> "Snow grains"
            80 -> "Slight rain showers"
            81 -> "Moderate rain showers"
            82 -> "Violent rain showers"
            85 -> "Slight snow showers"
            86 -> "Heavy snow showers"
            95 -> "Thunderstorm"
            96 -> "Thunderstorm with slight hail"
            99 -> "Thunderstorm with heavy hail"
            else -> "Unknown"
        }
    }
}
