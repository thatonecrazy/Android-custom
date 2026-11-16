package app.veneer

import android.app.Application
import android.content.Context
import app.veneer.apps.AppsRepository
import app.veneer.data.StateStore
import app.veneer.fonts.FontManager
import app.veneer.icons.IconPackManager
import app.veneer.icons.IconProvider
import app.veneer.system.WallpaperShuffler
import app.veneer.system.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class VeneerApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var store: StateStore
        private set
    lateinit var apps: AppsRepository
        private set
    lateinit var icons: IconProvider
        private set
    lateinit var fonts: FontManager
        private set
    lateinit var shuffler: WallpaperShuffler
        private set
    lateinit var weather: WeatherRepository
        private set

    override fun onCreate() {
        super.onCreate()
        store = StateStore(File(filesDir, "launcher.json"), scope)
        apps = AppsRepository(this, scope)
        icons = IconProvider(this, IconPackManager(this))
        fonts = FontManager(this)
        shuffler = WallpaperShuffler(this, store, scope).also { it.start() }
        weather = WeatherRepository(this, store)
        scope.launch {
            apps.packageChanges.collect { icons.onPackageChanged(it) }
        }
    }
}

val Context.veneer: VeneerApp get() = applicationContext as VeneerApp
