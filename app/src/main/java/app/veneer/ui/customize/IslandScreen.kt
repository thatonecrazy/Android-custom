package app.veneer.ui.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.veneer.apps.AppEntry
import app.veneer.data.IslandConfig
import app.veneer.data.LauncherState
import app.veneer.system.IslandEvent
import app.veneer.system.IslandEvents
import app.veneer.system.LockService
import app.veneer.system.NotificationDotsService
import app.veneer.ui.common.ChoiceRow
import app.veneer.ui.common.ClickRow
import app.veneer.ui.common.ColorRow
import app.veneer.ui.common.IntSliderRow
import app.veneer.ui.common.NoteText
import app.veneer.ui.common.SectionHeader
import app.veneer.ui.common.SettingCard
import app.veneer.ui.common.SwitchRow
import app.veneer.ui.island.IslandContent
import app.veneer.ui.island.IslandView
import app.veneer.veneer

private enum class Sample(val label: String) { Idle("Resting"), Charging("Charging"), Music("Music"), MusicOpen("Music, open"), Message("Notification") }

/** Settings for the island, with a live preview at the top of a pretend phone screen. */
@Composable
fun IslandScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    val config = state.island
    fun update(transform: (IslandConfig) -> IslandConfig) = app.store.update { it.copy(island = transform(it.island)) }
    var serviceOn by remember { mutableStateOf(LockService.isEnabled(context)) }
    var notificationAccess by remember { mutableStateOf(NotificationDotsService.isAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceOn = LockService.isEnabled(context)
        notificationAccess = NotificationDotsService.isAllowed(context)
    }
    var sample by remember { mutableStateOf(Sample.Charging) }
    val accent = Color(config.accent ?: state.theme.accent)
    val font = remember(state.theme.fontId) { app.fonts.family(state.theme.fontId) }

    SettingsPage("Island", onBack, state, apps, preview = false, section = Section.Island) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(220.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF3A3A44), Color(0xFF16161C))))
                    .testTag("islandPreview"),
            ) {
                val bar = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                    Text("9:41", style = bar)
                    Spacer(Modifier.weight(1f))
                    Text("5G  100", style = bar)
                }
                Box(Modifier.fillMaxWidth().offset(x = config.offsetXDp.dp, y = config.offsetYDp.dp), contentAlignment = Alignment.TopCenter) {
                    IslandView(config, accent, sampleContent(sample), alarm = if (config.alarms) "7:00 AM" else null, font = font)
                }
            }
        }
        item {
            ChoiceRow(null, Sample.entries, sample, { it.label }) { sample = it }
        }

        item { SectionHeader("Turn on") }
        item {
            SettingCard {
                SwitchRow("Show the island", config.enabled, "Over every app, at the top of the screen") { b -> update { it.copy(enabled = b) } }
                if (config.enabled && !serviceOn) {
                    ClickRow(
                        "Let Veneer draw over the status bar",
                        "Turn on \"Veneer\" in Accessibility. It's the same switch as the screen lock gesture.",
                        icon = Icons.Filled.Accessibility,
                    ) { LockService.openSettings(context) }
                    NoteText("Android only lets accessibility services draw above the status bar. Veneer's reads nothing on your screen.")
                }
                if (config.enabled && (config.music || config.notifications) && !notificationAccess) {
                    ClickRow(
                        "Allow notification access",
                        "Needed to show music and notifications in the island",
                        icon = Icons.Filled.NotificationsActive,
                    ) { NotificationDotsService.requestAccess(context) }
                }
            }
        }

        item { SectionHeader("What it shows") }
        item {
            SettingCard {
                SwitchRow("Charging", config.charging, "Pops open with the battery level when you plug in") { b -> update { it.copy(charging = b) } }
                SwitchRow("Music", config.music, "Album art and moving bars while music plays. Tap for controls.") { b -> update { it.copy(music = b) } }
                SwitchRow("Notifications", config.notifications, "New messages pop up for a moment. On the lock screen it only says something arrived.") { b ->
                    update { it.copy(notifications = b) }
                }
                SwitchRow("Alarms", config.alarms, "A small bubble when an alarm is set for the next 12 hours") { b -> update { it.copy(alarms = b) } }
                SwitchRow("Resting pill", config.idlePill, "A small pill around the camera when nothing is happening") { b -> update { it.copy(idlePill = b) } }
                IntSliderRow("Pop-ups stay for", config.popSeconds, 2..10, { "$it seconds" }) { v -> update { it.copy(popSeconds = v) } }
            }
        }

        item { SectionHeader("Look") }
        item {
            SettingCard {
                ColorRow("Background", config.background, "Black blends into the camera hole") { c -> update { it.copy(background = c) } }
                ColorRow("Text and icons", config.content, allowAlpha = false) { c -> update { it.copy(content = c) } }
                ColorRow(
                    "Highlight",
                    config.accent ?: state.theme.accent,
                    if (config.accent == null) "Same as your accent color. Used for the bolt, music bars and outline." else "The bolt, music bars and outline",
                    allowAlpha = false,
                ) { c -> update { it.copy(accent = c) } }
                SwitchRow("Glowing outline", config.outline, "A thin edge in the highlight color") { b -> update { it.copy(outline = b) } }
            }
        }

        item { SectionHeader("Size and place") }
        item {
            SettingCard {
                IntSliderRow("Width", config.widthDp, 40..240, { "$it dp" }) { v -> update { it.copy(widthDp = v) } }
                IntSliderRow("Height", config.heightDp, 20..56, { "$it dp" }) { v -> update { it.copy(heightDp = v) } }
                IntSliderRow("From the top", config.offsetYDp, 0..60, { "$it dp" }) { v -> update { it.copy(offsetYDp = v) } }
                IntSliderRow("Left or right", config.offsetXDp, -160..160, { if (it == 0) "Centered" else "$it dp" }) { v -> update { it.copy(offsetXDp = v) } }
                NoteText("The island centers itself on the front camera. Use these to fit it exactly around the camera hole.")
            }
        }

        item { SectionHeader("Try it") }
        item {
            SettingCard {
                ClickRow("Show a test notification", if (config.enabled && serviceOn) "Pops open at the top of your screen" else "Turn the island on first", icon = Icons.Filled.PlayCircle) {
                    IslandEvents.post(IslandEvent.Notification("Veneer", "Hello from the island", "This is what new messages look like.", null, null))
                }
                ClickRow("Show charging", icon = Icons.Filled.PlayCircle) { IslandEvents.post(IslandEvent.Charging(76)) }
            }
        }
    }
}

private fun sampleContent(sample: Sample): IslandContent = when (sample) {
    Sample.Idle -> IslandContent.Idle
    Sample.Charging -> IslandContent.Charging(76)
    Sample.Music -> IslandContent.Music("Late Night Drive", "Your music shows here", null, playing = true, open = false)
    Sample.MusicOpen -> IslandContent.Music("Late Night Drive", "Your music shows here", null, playing = true, open = true)
    Sample.Message -> IslandContent.Notification("Messages", "Sam", "On my way, see you in 10!", null)
}
