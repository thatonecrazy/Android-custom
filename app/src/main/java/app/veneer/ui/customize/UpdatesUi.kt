package app.veneer.ui.customize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.veneer.system.Release
import app.veneer.system.Updater
import app.veneer.ui.common.ClickRow
import app.veneer.ui.common.NoteText
import app.veneer.ui.common.SettingCard
import app.veneer.ui.common.SwitchRow
import app.veneer.ui.theme.Vivid
import app.veneer.veneer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Starts installing [release], first asking Android for permission to install if Veneer doesn't have it yet. */
private fun startUpdate(context: android.content.Context, release: Release) {
    val app = context.veneer
    if (!app.updater.canInstall()) {
        app.updater.allowInstalls(context)
        return
    }
    app.scope.launch { app.updater.update(release) }
}

/** The Updates card in Backup: the version you have, checking, downloading and installing. */
@Composable
fun UpdatesCard() {
    val context = LocalContext.current
    val app = context.veneer
    val updater = app.updater
    val state by updater.state.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(updater.canInstall()) }
    var auto by remember { mutableStateOf(updater.autoCheck) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = updater.canInstall() }

    SettingCard {
        val s = state
        val (title, subtitle) = when (s) {
            Updater.State.Idle -> "Veneer ${updater.currentVersion}" to "Tap to check for a newer version"
            Updater.State.Checking -> "Checking for updates…" to null
            is Updater.State.UpToDate -> "Veneer ${updater.currentVersion}" to "You have the newest version"
            is Updater.State.Available -> "Veneer ${s.release.version} is ready" to "Tap to update. ${s.release.notes.lineSequence().firstOrNull().orEmpty()}".trim()
            is Updater.State.Downloading -> "Downloading Veneer ${s.release.version}" to "${(s.progress * 100).roundToInt()}%"
            is Updater.State.Installing -> "Installing Veneer ${s.release.version}" to "Veneer restarts when it's done"
            is Updater.State.Failed -> "The update didn't work" to s.message
        }
        ClickRow(title, subtitle, icon = Icons.Filled.SystemUpdate) {
            when (s) {
                is Updater.State.Available -> startUpdate(context, s.release)
                is Updater.State.Failed -> if (s.release != null) startUpdate(context, s.release) else app.scope.launch { updater.check() }
                is Updater.State.Downloading, is Updater.State.Installing, Updater.State.Checking -> Unit
                else -> app.scope.launch { updater.check() }
            }
        }
        if (s is Updater.State.Downloading) {
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (!canInstall) {
            ClickRow(
                "Allow Veneer to install its updates",
                "A one-time switch in Android's settings, so you never download update files by hand",
                icon = Icons.Filled.VerifiedUser,
            ) { updater.allowInstalls(context) }
        }
        SwitchRow("Check automatically", auto, "Looks for a new version a few times a day") { b ->
            auto = b
            updater.autoCheck = b
        }
        NoteText(
            "Updates come from Veneer's releases on GitHub, built from the code in your repository and signed with the same key. " +
                "The first update asks you to confirm; after that, Android 12 and newer let Veneer update itself.",
        )
    }
}

/** A banner at the top of Customize when a new version is ready, with its progress while it installs. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val state by context.veneer.updater.state.collectAsStateWithLifecycle()
    val s = state
    val release = when (s) {
        is Updater.State.Available -> s.release
        is Updater.State.Downloading -> s.release
        is Updater.State.Installing -> s.release
        is Updater.State.Failed -> s.release
        else -> null
    }
    AnimatedVisibility(release != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        if (release == null) return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Vivid.Teal, Vivid.Blue)))
                .padding(20.dp),
        ) {
            Text("Veneer ${release.version} is ready", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            val notes = release.notes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
            if (notes != null) {
                Spacer(Modifier.height(4.dp))
                Text(notes, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
            Spacer(Modifier.height(12.dp))
            when (s) {
                is Updater.State.Downloading -> {
                    Text("Downloading… ${(s.progress * 100).roundToInt()}%", color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is Updater.State.Installing -> Text("Installing… Veneer restarts when it's done.", color = Color.White)
                else -> {
                    if (s is Updater.State.Failed) {
                        Text(s.message, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { startUpdate(context, release) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Vivid.Blue),
                    ) { Text(if (s is Updater.State.Failed) "Try again" else "Update now", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
