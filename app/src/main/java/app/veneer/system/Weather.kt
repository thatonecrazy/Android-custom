package app.veneer.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import app.veneer.data.StateStore
import app.veneer.data.WeatherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** What the weather widgets show. */
data class WeatherNow(
    val temperature: Int,
    val high: Int,
    val low: Int,
    val code: Int,
    val isDay: Boolean,
    val fahrenheit: Boolean,
    val place: String?,
    val fetchedAt: Long,
) {
    val condition: WeatherCondition get() = WeatherCondition.from(code, isDay)
    val unit: String get() = if (fahrenheit) "°F" else "°C"
}

/** WMO weather codes grouped the way people talk about the weather. */
enum class WeatherCondition(val label: String) {
    Clear("Clear"), ClearNight("Clear"), PartlyCloudy("Partly cloudy"), Cloudy("Cloudy"), Fog("Fog"),
    Drizzle("Drizzle"), Rain("Rain"), Snow("Snow"), Storm("Thunderstorm");

    companion object {
        fun from(code: Int, isDay: Boolean) = when (code) {
            0 -> if (isDay) Clear else ClearNight
            1, 2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            51, 53, 55, 56, 57 -> Drizzle
            61, 63, 65, 66, 67, 80, 81, 82 -> Rain
            71, 73, 75, 77, 85, 86 -> Snow
            95, 96, 99 -> Storm
            else -> Cloudy
        }
    }
}

data class PlaceResult(val name: String, val detail: String, val latitude: Double, val longitude: Double)

/**
 * Current weather from Open-Meteo (free, no account or key). Uses the phone's rough location when
 * allowed, or a place picked by name. Refreshes at most every 30 minutes.
 */
class WeatherRepository(private val context: Context, private val store: StateStore) {

    private val _now = MutableStateFlow<WeatherNow?>(null)
    val now: StateFlow<WeatherNow?> = _now.asStateFlow()
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /** Lets tests and previews show weather without the network. */
    fun setForPreview(value: WeatherNow?) {
        _now.value = value
    }

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun usesFahrenheit(settings: WeatherSettings = store.current.weather): Boolean =
        settings.fahrenheit ?: (Locale.getDefault().country in setOf("US", "LR", "MM"))

    /** Fetches new weather if the last one is old (or [force]). Returns false when there's no location or no network. */
    suspend fun refresh(force: Boolean = false): Boolean = lock.withLock {
        val last = _now.value
        val settings = store.current.weather
        val fahrenheit = usesFahrenheit(settings)
        if (!force && last != null && last.fahrenheit == fahrenheit && System.currentTimeMillis() - last.fetchedAt < 30 * 60_000L) return true
        withContext(Dispatchers.IO) {
            val (lat, lon, place) = location(settings) ?: return@withContext false
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1" + if (fahrenheit) "&temperature_unit=fahrenheit" else ""
            val body = get(url) ?: return@withContext false
            val parsed = runCatching { parseForecast(body, fahrenheit, place) }.getOrNull() ?: return@withContext false
            _now.value = parsed
            true
        }
    }

    private fun location(settings: WeatherSettings): Triple<Double, Double, String?>? {
        if (!settings.useDeviceLocation && settings.latitude != null && settings.longitude != null) {
            return Triple(settings.latitude, settings.longitude, settings.place)
        }
        if (!hasLocationPermission()) {
            return if (settings.latitude != null && settings.longitude != null) Triple(settings.latitude, settings.longitude, settings.place) else null
        }
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val best = try {
            lm.getProviders(true).mapNotNull { p -> lm.getLastKnownLocation(p) }.maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null // permission taken away since the check above
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
        // Round to about 1 km: plenty for weather, and less precise than the phone knows.
        return Triple((best.latitude * 100).roundToInt() / 100.0, (best.longitude * 100).roundToInt() / 100.0, null)
    }

    /** Finds places by name for the location picker. */
    suspend fun search(name: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val lang = Locale.getDefault().language
        val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=$lang") ?: return@withContext emptyList()
        runCatching { parsePlaces(body) }.getOrDefault(emptyList())
    }

    fun parseForecast(body: String, fahrenheit: Boolean, place: String?): WeatherNow {
        val root = json.parseToJsonElement(body).jsonObject
        val current = root["current"]!!.jsonObject
        val daily = root["daily"]!!.jsonObject
        return WeatherNow(
            temperature = current["temperature_2m"]!!.jsonPrimitive.double.roundToInt(),
            high = daily["temperature_2m_max"]!!.jsonArray[0].jsonPrimitive.double.roundToInt(),
            low = daily["temperature_2m_min"]!!.jsonArray[0].jsonPrimitive.double.roundToInt(),
            code = current["weather_code"]!!.jsonPrimitive.int,
            isDay = current["is_day"]?.jsonPrimitive?.int != 0,
            fahrenheit = fahrenheit,
            place = place,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    fun parsePlaces(body: String): List<PlaceResult> {
        val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
        return results.map { it.jsonObject }.map { o: JsonObject ->
            val name = o["name"]!!.jsonPrimitive.content
            val detail = listOfNotNull(o["admin1"]?.jsonPrimitive?.content, o["country"]?.jsonPrimitive?.content).joinToString(", ")
            PlaceResult(name, detail, o["latitude"]!!.jsonPrimitive.double, o["longitude"]!!.jsonPrimitive.double)
        }
    }

    private fun get(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Veneer launcher")
        try {
            if (conn.responseCode != 200) null else conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
