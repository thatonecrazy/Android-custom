package app.veneer.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import app.veneer.veneer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A published version of Veneer. */
data class Release(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val page: String)

/** Reading GitHub's description of a release, and comparing versions. Plain Kotlin, so it's easy to test. */
object ReleaseInfo {
    private val json = Json { ignoreUnknownKeys = true }

    /** The release in GitHub's "latest release" answer, or null when it has no APK attached. */
    fun parse(body: String): Release? {
        val root = json.parseToJsonElement(body).jsonObject
        val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
        val apk = root["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { a: JsonObject ->
            a["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true
        } ?: return null
        return Release(
            version = tag.removePrefix("v").removePrefix("V"),
            notes = root["body"]?.jsonPrimitive?.content?.trim().orEmpty(),
            apkUrl = apk["browser_download_url"]?.jsonPrimitive?.content ?: return null,
            sizeBytes = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            page = root["html_url"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    /** Whether [candidate] is a later version than [current], comparing number by number (1.10 is after 1.9). */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-', '+').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/**
 * Keeps Veneer up to date from the releases on GitHub: checks for a newer version, downloads it,
 * and hands it to Android's installer. The first update asks for confirmation; after that,
 * Android 12 and newer let Veneer update itself without asking.
 */
class Updater(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val version: String) : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Installing(val release: Release) : State
        data class Failed(val message: String, val release: Release? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Lets tests and screenshots show a state without the network. */
    fun setStateForPreview(state: State) {
        _state.value = state
    }

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** Whether Veneer looks for updates on its own (at most a few times a day). */
    var autoCheck: Boolean
        get() = prefs.getBoolean("auto", true)
        set(value) {
            prefs.edit { putBoolean("auto", value) }
        }

    val currentVersion: String
        get() = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** Whether Android lets Veneer install apps (the one-time "Install unknown apps" switch). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system switch that lets Veneer install its own updates. */
    fun allowInstalls(from: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
        SystemActions.open(from, intent)
    }

    /** Checks only when automatic checks are on and the last one was a while ago. */
    suspend fun checkIfDue() {
        if (!autoCheck) return
        if (System.currentTimeMillis() - prefs.getLong("last", 0L) < 6 * 60 * 60 * 1000L) return
        if (_state.value !is State.Idle && _state.value !is State.UpToDate) return
        check()
    }

    suspend fun check(): State {
        _state.value = State.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Veneer-updater")
                }
                try {
                    when (conn.responseCode) {
                        200 -> ReleaseInfo.parse(conn.inputStream.use { it.readBytes().decodeToString() })
                        404 -> null // no releases yet
                        else -> error("GitHub answered ${conn.responseCode}")
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }
        prefs.edit { putLong("last", System.currentTimeMillis()) }
        val next = result.fold(
            onSuccess = { release ->
                if (release != null && ReleaseInfo.isNewer(release.version, currentVersion)) State.Available(release)
                else State.UpToDate(currentVersion)
            },
            onFailure = { State.Failed("Couldn't reach GitHub. Check the internet connection and try again.") },
        )
        _state.value = next
        return next
    }

    /** Downloads [release] and starts installing it. */
    suspend fun update(release: Release) {
        if (_state.value is State.Downloading || _state.value is State.Installing) return
        _state.value = State.Downloading(release, 0f)
        val file = withContext(Dispatchers.IO) { runCatching { download(release) } }.getOrElse {
            _state.value = State.Failed("The download didn't finish. Try again.", release)
            return
        }
        // Make sure it really is a newer Veneer before handing it to Android.
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (info?.packageName != context.packageName) {
            _state.value = State.Failed("That download isn't Veneer, so it wasn't installed.", release)
            file.delete()
            return
        }
        _state.value = State.Installing(release)
        withContext(Dispatchers.IO) { runCatching { install(file) } }.onFailure {
            _state.value = State.Failed("Android couldn't start the update: ${it.message ?: "unknown error"}", release)
        }
    }

    private fun download(release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Veneer-${release.version}.apk")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Veneer-updater")
        }
        try {
            if (conn.responseCode != 200) error("download answered ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = 0f
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done.toFloat() / total).coerceIn(0f, 1f)
                            if (p - lastShown >= 0.01f) {
                                lastShown = p
                                _state.value = State.Downloading(release, p)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return target
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("veneer.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName), flags)
            session.commit(result.intentSender)
        }
    }

    /** Called with Android's answer about an install. */
    fun onInstallResult(status: Int, message: String?) {
        val release = (_state.value as? State.Installing)?.release
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit // the confirmation screen is showing
            PackageInstaller.STATUS_SUCCESS -> _state.value = State.UpToDate(release?.version ?: currentVersion)
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = release?.let { State.Available(it) } ?: State.Idle
            else -> _state.value = State.Failed(
                when (status) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "This update was signed differently from the Veneer on your phone, so Android refused it."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "There isn't enough free space for the update."
                    else -> "Android couldn't install the update" + (message?.let { ": $it" } ?: ".")
                },
                release,
            )
        }
    }

    companion object {
        /** Where new versions are published. */
        const val REPO = "thatonecrazy/Android-custom"
    }
}

/** Gets Android's answer about an update install, and shows the confirmation screen when Android asks for one. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { confirm ->
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        context.veneer.updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
