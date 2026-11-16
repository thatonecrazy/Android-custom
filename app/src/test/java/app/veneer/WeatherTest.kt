package app.veneer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.veneer.system.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherTest {

    private val weather get() = ApplicationProvider.getApplicationContext<VeneerApp>().weather

    @Test
    fun readsAnOpenMeteoForecast() {
        val body = """
            {"latitude":52.52,"longitude":13.42,"timezone":"Europe/Berlin",
             "current_units":{"temperature_2m":"°C"},
             "current":{"time":"2026-11-16T10:00","interval":900,"temperature_2m":7.6,"weather_code":61,"is_day":1},
             "daily":{"time":["2026-11-16"],"temperature_2m_max":[9.4],"temperature_2m_min":[2.5]}}
        """.trimIndent()
        val now = weather.parseForecast(body, fahrenheit = false, place = "Berlin")
        assertEquals(8, now.temperature)
        assertEquals(9, now.high)
        assertEquals(3, now.low)
        assertEquals(WeatherCondition.Rain, now.condition)
        assertEquals("°C", now.unit)
        assertEquals("Berlin", now.place)
    }

    @Test
    fun nightAndClearSkyShowTheMoon() {
        val body = """{"current":{"temperature_2m":51.0,"weather_code":0,"is_day":0},"daily":{"temperature_2m_max":[60.2],"temperature_2m_min":[48.0]}}"""
        val now = weather.parseForecast(body, fahrenheit = true, place = null)
        assertEquals(WeatherCondition.ClearNight, now.condition)
        assertEquals("°F", now.unit)
    }

    @Test
    fun readsPlaceSearchResults() {
        val body = """
            {"results":[
              {"id":2950159,"name":"Berlin","latitude":52.52437,"longitude":13.41053,"country":"Germany","admin1":"Land Berlin"},
              {"id":4348460,"name":"Berlin","latitude":38.32262,"longitude":-75.21769,"country":"United States"}
            ],"generationtime_ms":0.6}
        """.trimIndent()
        val places = weather.parsePlaces(body)
        assertEquals(2, places.size)
        assertEquals("Land Berlin, Germany", places[0].detail)
        assertEquals("United States", places[1].detail)
        assertEquals(-75.21769, places[1].longitude, 1e-6)
        assertTrue(weather.parsePlaces("""{"generationtime_ms":0.2}""").isEmpty())
    }
}
