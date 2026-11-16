package app.veneer.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

val VeneerJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    coerceInputValues = true
}

/**
 * Holds the whole launcher state in memory and writes it to disk shortly after each change.
 * Writes go to a temp file first so a crash mid-write never corrupts the saved layout.
 */
class StateStore(private val file: File, scope: CoroutineScope) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LauncherState> = _state.asStateFlow()
    val current: LauncherState get() = _state.value

    private val saveRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (request in saveRequests) {
                delay(250)
                write(_state.value)
            }
        }
    }

    fun update(transform: (LauncherState) -> LauncherState) {
        _state.update(transform)
        saveRequests.trySend(Unit)
    }

    /** Applies a theme change and reflows the layout in case the grid or dock size changed. */
    fun updateTheme(transform: (ThemeConfig) -> ThemeConfig) = update { s ->
        val newTheme = transform(s.theme)
        val gridChanged = newTheme.gridColumns != s.theme.gridColumns ||
            newTheme.gridRows != s.theme.gridRows ||
            newTheme.dockColumns != s.theme.dockColumns
        val next = s.copy(theme = newTheme)
        if (gridChanged) LayoutOps.reflow(next) else next
    }

    fun updateOverride(appId: String, transform: (AppOverride) -> AppOverride) = update { s ->
        val updated = transform(s.overrides[appId] ?: AppOverride())
        s.copy(overrides = if (updated.isEmpty) s.overrides - appId else s.overrides + (appId to updated))
    }

    fun exportJson(): String = VeneerJson.encodeToString(LauncherState.serializer(), current)

    /** Replaces everything with an imported backup. Throws if the text is not a valid backup. */
    fun importJson(text: String) {
        val imported = VeneerJson.decodeFromString(LauncherState.serializer(), text)
        update { LayoutOps.reflow(imported.copy(firstRunDone = true)) }
    }

    private fun load(): LauncherState = try {
        if (file.exists()) VeneerJson.decodeFromString(LauncherState.serializer(), file.readText()) else LauncherState()
    } catch (e: Exception) {
        Log.e(TAG, "Could not read saved state, starting fresh", e)
        runCatching { file.copyTo(File(file.parentFile, file.name + ".broken"), overwrite = true) }
        LauncherState()
    }

    private fun write(state: LauncherState) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(VeneerJson.encodeToString(LauncherState.serializer(), state))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not save state", e)
        }
    }

    private companion object {
        const val TAG = "VeneerState"
    }
}
