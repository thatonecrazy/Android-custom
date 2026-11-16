package app.veneer.ui.customize

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.veneer.data.WeatherSettings
import app.veneer.system.PlaceResult
import app.veneer.ui.common.ChoiceRow
import app.veneer.ui.common.ClickRow
import app.veneer.ui.common.NoteText
import app.veneer.ui.common.SettingCard
import app.veneer.ui.common.SwitchRow
import app.veneer.ui.launcher.weatherIcon
import app.veneer.veneer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the weather widgets get their weather from, and in which units. */
@Composable
fun WeatherCard(settings: WeatherSettings) {
    val app = LocalContext.current.veneer
    val scope = rememberCoroutineScope()
    val now by app.weather.now.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }

    fun save(transform: (WeatherSettings) -> WeatherSettings) {
        app.store.update { it.copy(weather = transform(it.weather)) }
        scope.launch {
            busy = true
            failed = !app.weather.refresh(force = true)
            busy = false
        }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save { it.copy(useDeviceLocation = true) } else searching = true
    }

    SettingCard {
        SwitchRow(
            "Use my location",
            settings.useDeviceLocation && app.weather.hasLocationPermission(),
            "Only a rough location is used, about 1 km",
            icon = Icons.Filled.MyLocation,
        ) { on ->
            when {
                !on -> if (settings.latitude == null) searching = true else save { it.copy(useDeviceLocation = false) }
                app.weather.hasLocationPermission() -> save { it.copy(useDeviceLocation = true) }
                else -> askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        ClickRow(
            "Choose a city",
            settings.place?.let { if (settings.useDeviceLocation) "$it (used when location is off)" else it } ?: "Search any town or city",
            icon = Icons.Filled.LocationCity,
        ) { searching = true }
        ChoiceRow("Units", listOf<Boolean?>(null, false, true), settings.fahrenheit, {
            when (it) { null -> "Auto"; false -> "°C"; true -> "°F" }
        }) { v -> save { it.copy(fahrenheit = v) } }
        val w = now
        ClickRow(
            when {
                busy -> "Checking the weather…"
                w != null -> "${w.temperature}${w.unit}, ${w.condition.label}" + (w.place?.let { " in $it" } ?: "")
                else -> "No weather yet"
            },
            when {
                failed && w == null -> "Couldn't get the weather. Check the internet connection, or choose a city."
                w != null -> "High ${w.high}° · Low ${w.low}° · Tap to refresh"
                else -> "Tap to try now"
            },
            icon = if (w != null) weatherIcon(w.condition) else Icons.Filled.Refresh,
        ) {
            scope.launch {
                busy = true
                failed = !app.weather.refresh(force = true)
                busy = false
            }
        }
        NoteText("Weather comes from Open-Meteo: free, no account, and nothing but the location is sent.")
    }

    if (searching) {
        CitySearchDialog(
            onDismiss = { searching = false },
            onPick = { place ->
                searching = false
                save {
                    it.copy(
                        useDeviceLocation = false,
                        place = place.name,
                        latitude = place.latitude,
                        longitude = place.longitude,
                    )
                }
            },
        )
    }
}

@Composable
private fun CitySearchDialog(onDismiss: () -> Unit, onPick: (PlaceResult) -> Unit) {
    val app = LocalContext.current.veneer
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(350) // wait until typing pauses
        loading = true
        results = app.weather.search(query)
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a city") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { if (loading) CircularProgressIndicator(Modifier.padding(8.dp), strokeWidth = 2.dp) },
                    placeholder = { Text("Town or city") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = results
                when {
                    list == null -> Unit
                    list.isEmpty() && !loading -> Text(
                        "Nothing found. Check the spelling or the internet connection.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 300.dp).padding(top = 8.dp)) {
                        items(list) { place ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(place) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                            ) {
                                Text(place.name, style = MaterialTheme.typography.bodyLarge)
                                if (place.detail.isNotBlank()) {
                                    Text(place.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
