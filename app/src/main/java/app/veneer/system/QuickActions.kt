package app.veneer.system

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.net.toUri
import app.veneer.data.QuickTile

/** What tapping a tile does. */
enum class TileKind {
    /** Switches the setting directly. */
    Toggle,

    /** Opens Android's own pop-up or page for the setting, because Android doesn't let apps switch it. */
    OpensSystem,

    /** Does one thing once, with no on or off. */
    Action,
}

val QuickTile.kind: TileKind
    get() = when (this) {
        QuickTile.Flashlight, QuickTile.DoNotDisturb, QuickTile.AutoRotate -> TileKind.Toggle
        QuickTile.Screenshot, QuickTile.LockScreen, QuickTile.PowerMenu -> TileKind.Action
        else -> TileKind.OpensSystem
    }

/** What happened when a tile was tapped or a slider moved. */
sealed interface TileOutcome {
    data object Done : TileOutcome

    /** Android's own pop-up or page opened over the current app. */
    data object Opened : TileOutcome

    /** Android needs the user to allow something first; [intent] opens that page. */
    data class NeedsPermission(val text: String, val intent: Intent) : TileOutcome

    data class Failed(val text: String) : TileOutcome
}

/**
 * Reads and changes the settings behind the island's quick action tiles. Everything that can go
 * wrong comes back as a [TileOutcome] with a message, so a tile never crashes or silently does nothing.
 */
class QuickActions(private val context: Context) {

    private val resolver = context.contentResolver
    private val camera = context.getSystemService(CameraManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    /** The back camera with a flash, if there is one. */
    private val torchCamera: String? by lazy {
        runCatching {
            val ids = camera?.cameraIdList.orEmpty().toList()
            fun flash(id: String) = camera?.getCameraCharacteristics(id)?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            fun back(id: String) = camera?.getCameraCharacteristics(id)?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            ids.firstOrNull { flash(it) && back(it) } ?: ids.firstOrNull { flash(it) }
        }.getOrNull()
    }

    @Volatile
    private var torchOn = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCamera) torchOn = enabled
        }
    }

    /** Starts following the flashlight, which Android only reports through a callback. */
    fun start() {
        runCatching { camera?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) }
    }

    fun stop() {
        runCatching { camera?.unregisterTorchCallback(torchCallback) }
    }

    /** On or off right now; null for tiles without an on/off state, or whose state Android doesn't share. */
    fun isOn(tile: QuickTile): Boolean? = runCatching {
        when (tile) {
            QuickTile.Wifi -> context.getSystemService(WifiManager::class.java)?.isWifiEnabled
            QuickTile.Bluetooth -> context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
            QuickTile.Flashlight -> torchOn
            QuickTile.DoNotDisturb -> notifications?.currentInterruptionFilter?.let {
                it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            }
            QuickTile.AutoRotate -> Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            QuickTile.MobileData -> context.getSystemService(TelephonyManager::class.java)?.isDataEnabled
            QuickTile.Location -> context.getSystemService(LocationManager::class.java)?.let { lm ->
                if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
            QuickTile.PowerSaving -> context.getSystemService(PowerManager::class.java)?.isPowerSaveMode
            QuickTile.Airplane -> Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            QuickTile.Hotspot -> hotspotOn()
            QuickTile.Screenshot, QuickTile.LockScreen, QuickTile.PowerMenu -> null
        }
    }.getOrNull()

    /** Android has no public way to read the hotspot; this works on most phones and gives up quietly otherwise. */
    private fun hotspotOn(): Boolean? = runCatching {
        val wifi = context.getSystemService(WifiManager::class.java) ?: return@runCatching null
        WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifi) as? Boolean
    }.getOrNull()

    fun tap(tile: QuickTile): TileOutcome = runCatching {
        when (tile) {
            QuickTile.Flashlight -> toggleTorch()
            QuickTile.DoNotDisturb -> toggleDoNotDisturb()
            QuickTile.AutoRotate -> toggleAutoRotate()
            QuickTile.Screenshot -> global(if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT else null, "Screenshots from here need Android 9 or newer.")
            QuickTile.LockScreen -> global(if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN else null, "Locking from here needs Android 9 or newer.")
            QuickTile.PowerMenu -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, null)
            QuickTile.Wifi -> open(
                if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS),
                Intent(Settings.ACTION_WIFI_SETTINGS),
            )
            QuickTile.MobileData -> open(
                if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
            QuickTile.Airplane -> open(
                if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            )
            QuickTile.Bluetooth -> open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            QuickTile.Location -> open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            QuickTile.Hotspot -> open(
                Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
                Intent().setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
            QuickTile.PowerSaving -> open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }
    }.getOrElse { TileOutcome.Failed("${tile.label} didn't respond. ${it.message.orEmpty()}".trim()) }

    private fun toggleTorch(): TileOutcome {
        val id = torchCamera ?: return TileOutcome.Failed("Veneer can't find a flashlight on this phone.")
        return try {
            camera!!.setTorchMode(id, !torchOn)
            torchOn = !torchOn
            TileOutcome.Done
        } catch (_: CameraAccessException) {
            TileOutcome.Failed("The camera is in use, so the flashlight can't change right now.")
        }
    }

    private fun toggleDoNotDisturb(): TileOutcome {
        val nm = notifications ?: return TileOutcome.Failed("Do not disturb isn't available on this phone.")
        if (!nm.isNotificationPolicyAccessGranted) {
            return TileOutcome.NeedsPermission(
                "Do not disturb needs Do Not Disturb access for Veneer.",
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            )
        }
        val on = isOn(QuickTile.DoNotDisturb) == true
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return TileOutcome.Done
    }

    private fun writeSettingsPermission(what: String) = TileOutcome.NeedsPermission(
        "$what needs permission for Veneer to modify system settings.",
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri()),
    )

    private fun toggleAutoRotate(): TileOutcome {
        if (!Settings.System.canWrite(context)) return writeSettingsPermission("Auto-rotate")
        val on = isOn(QuickTile.AutoRotate) == true
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 0 else 1)
        return TileOutcome.Done
    }

    private fun global(action: Int?, unsupported: String?): TileOutcome {
        if (action == null) return TileOutcome.Failed(unsupported ?: "This phone can't do that from here.")
        if (!LockService.running) return TileOutcome.Failed("Turn on Veneer in Accessibility settings to use this tile.")
        return if (LockService.globalAction(action)) TileOutcome.Done else TileOutcome.Failed("Android didn't allow that just now.")
    }

    /** Opens the first of [intents] the phone has, over the current app. */
    private fun open(vararg intents: Intent): TileOutcome {
        for (intent in intents) {
            try {
                context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return TileOutcome.Opened
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
        return TileOutcome.Failed("This phone doesn't have that settings page.")
    }

    // --- Sliders ------------------------------------------------------------------------------

    /** Screen brightness from 0 to 1. */
    fun brightness(): Float = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f).coerceIn(0f, 1f)

    fun autoBrightness(): Boolean = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    /** Sets the brightness; moving the slider turns automatic brightness off, like the phone's own slider. */
    fun setBrightness(level: Float): TileOutcome = runCatching {
        if (!Settings.System.canWrite(context)) return writeSettingsPermission("Brightness")
        if (autoBrightness()) {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        }
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (level.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255))
        TileOutcome.Done
    }.getOrElse { TileOutcome.Failed("Brightness didn't change. ${it.message.orEmpty()}".trim()) }

    fun setAutoBrightness(on: Boolean): TileOutcome = runCatching {
        if (!Settings.System.canWrite(context)) return writeSettingsPermission("Automatic brightness")
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        TileOutcome.Done
    }.getOrElse { TileOutcome.Failed("Automatic brightness didn't change. ${it.message.orEmpty()}".trim()) }

    /** Media volume from 0 to 1. */
    fun volume(): Float = runCatching {
        val am = audio!!
        am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }.getOrDefault(0f).coerceIn(0f, 1f)

    fun setVolume(level: Float): TileOutcome = runCatching {
        val am = audio ?: return TileOutcome.Failed("Volume isn't available.")
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (level.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max), 0)
        TileOutcome.Done
    }.getOrElse {
        TileOutcome.Failed(if (it is SecurityException) "Do not disturb is blocking volume changes right now." else "Volume didn't change.")
    }
}
