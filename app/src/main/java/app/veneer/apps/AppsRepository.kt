package app.veneer.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Collator
import java.text.Normalizer

/** Identifies one launchable activity for one user profile. */
data class AppKey(val component: ComponentName, val userSerial: Long) {
    val id: String get() = "${component.flattenToString()}#$userSerial"

    companion object {
        fun parse(id: String): AppKey? {
            val hash = id.lastIndexOf('#')
            if (hash <= 0) return null
            val component = ComponentName.unflattenFromString(id.substring(0, hash)) ?: return null
            val serial = id.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(component, serial)
        }
    }
}

class AppEntry(
    val key: AppKey,
    val label: String,
    val user: UserHandle,
    val info: LauncherActivityInfo,
    val isWorkProfile: Boolean,
) {
    val packageName: String get() = key.component.packageName
    val searchText: String = normalizeForSearch(label)
}

fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * The list of launchable apps across the user's profiles, kept up to date as apps are
 * installed, removed or updated.
 */
class AppsRepository(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val refreshLock = Mutex()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Emits a package name whenever that package changes, so cached icons can be dropped. */
    private val _packageChanges = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val packageChanges: SharedFlow<String> = _packageChanges.asSharedFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun changed(packageName: String) {
        _packageChanges.tryEmit(packageName)
        refresh()
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            refreshLock.withLock {
                val collator = Collator.getInstance()
                val me = Process.myUserHandle()
                val result = userManager.userProfiles.flatMap { user ->
                    val serial = userManager.getSerialNumberForUser(user)
                    runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                        .filter { it.componentName.packageName != context.packageName || it.name.endsWith("CustomizeActivity") }
                        .map { info ->
                            AppEntry(
                                key = AppKey(info.componentName, serial),
                                label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                                user = user,
                                info = info,
                                isWorkProfile = user != me,
                            )
                        }
                }.sortedWith { a, b -> collator.compare(a.label, b.label) }
                _apps.value = result
                _loaded.value = true
            }
        }
    }

    fun find(key: AppKey): AppEntry? = _apps.value.firstOrNull { it.key == key }

    fun findById(id: String): AppEntry? = AppKey.parse(id)?.let(::find)

    fun findByPackage(packageName: String): AppEntry? =
        _apps.value.firstOrNull { it.packageName == packageName && !it.isWorkProfile }

    /** True if the package still exists for that profile (used to clean up removed apps). */
    fun isInstalled(key: AppKey): Boolean {
        val user = userManager.getUserForSerialNumber(key.userSerial) ?: return false
        return runCatching { launcherApps.isPackageEnabled(key.component.packageName, user) }.getOrDefault(true)
    }

    fun userSerialIsMain(serial: Long): Boolean =
        userManager.getSerialNumberForUser(Process.myUserHandle()) == serial

    /** The app that handles [intent] by default, if it has a launcher entry. */
    fun appForIntent(intent: Intent): AppEntry? {
        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull() ?: return null
        val pkg = resolved.activityInfo?.packageName ?: return null
        if (pkg == "android") return null // the "choose an app" resolver, no default set
        return findByPackage(pkg)
    }

    fun launch(key: AppKey, sourceBounds: Rect? = null) {
        val user = userManager.getUserForSerialNumber(key.userSerial)
        try {
            if (user == null) throw ActivityNotFoundException()
            launcherApps.startMainActivity(key.component, user, sourceBounds, null)
        } catch (e: Exception) {
            Log.w("Veneer", "Could not launch ${key.component}", e)
            Toast.makeText(context, "Couldn't open that app", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(key: AppKey) {
        val user = userManager.getUserForSerialNumber(key.userSerial) ?: return
        runCatching { launcherApps.startAppDetailsActivity(key.component, user, null, null) }
    }

    fun uninstall(key: AppKey) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.component.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        userManager.getUserForSerialNumber(key.userSerial)?.let { intent.putExtra(Intent.EXTRA_USER, it) }
        runCatching { context.startActivity(intent) }
    }
}
