package app.veneer.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.veneer.data.IslandConfig
import app.veneer.data.QuickTile
import app.veneer.system.TileKind
import app.veneer.system.kind
import kotlinx.coroutines.launch

val QuickTile.icon: ImageVector
    get() = when (this) {
        QuickTile.Wifi -> Icons.Filled.Wifi
        QuickTile.Bluetooth -> Icons.Filled.Bluetooth
        QuickTile.Flashlight -> Icons.Filled.FlashlightOn
        QuickTile.DoNotDisturb -> Icons.Filled.DoNotDisturbOn
        QuickTile.AutoRotate -> Icons.Filled.ScreenRotation
        QuickTile.MobileData -> Icons.Filled.SignalCellularAlt
        QuickTile.Location -> Icons.Filled.LocationOn
        QuickTile.PowerSaving -> Icons.Filled.BatterySaver
        QuickTile.Airplane -> Icons.Filled.AirplanemodeActive
        QuickTile.Hotspot -> Icons.Filled.WifiTethering
        QuickTile.Screenshot -> Icons.Filled.Screenshot
        QuickTile.LockScreen -> Icons.Filled.Lock
        QuickTile.PowerMenu -> Icons.Filled.PowerSettingsNew
    }

/** One short line at the top of the drop-down, with an optional button (like "Allow"). */
data class PanelMessage(val text: String, val button: String? = null, val onButton: (() -> Unit)? = null)

/** Everything the drop-down shows, so the live island and the settings preview draw the same thing. */
data class QuickPanelState(
    val tiles: List<QuickTile>,
    val on: Map<QuickTile, Boolean?>,
    val brightness: Float,
    val autoBrightness: Boolean,
    val volume: Float,
    val message: PanelMessage? = null,
)

class QuickPanelActions(
    val onTile: (QuickTile) -> Unit = {},
    val onBrightness: (Float) -> Unit = {},
    val onAutoBrightness: () -> Unit = {},
    val onVolume: (Float) -> Unit = {},
    val onClose: () -> Unit = {},
)

/** The quick actions drop-down: round tiles four across, then brightness and media volume. Swipe up to close. */
@Composable
fun QuickPanel(
    config: IslandConfig,
    accent: Color,
    state: QuickPanelState,
    actions: QuickPanelActions,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    font: FontFamily? = null,
) {
    val bg = lerp(Color(config.background), Color(config.content), 0.06f)
    val fg = Color(config.content)
    val text = TextStyle(color = fg, fontFamily = font, fontSize = 11.sp, textAlign = TextAlign.Center)
    var dragged by remember { mutableFloatStateOf(0f) }
    Column(
        modifier
            .width(340.dp)
            .testTag("quickPanel")
            .clip(RoundedCornerShape(30.dp))
            .background(bg)
            .then(if (config.outline) Modifier.border(1.5.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(30.dp)) else Modifier)
            .pointerInput(actions) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -40.dp.toPx()) actions.onClose() },
                    onVerticalDrag = { change, dy ->
                        dragged += dy
                        change.consume()
                    },
                )
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        state.message?.let { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("panelMessage"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(m.text, style = text.copy(fontSize = 12.sp, textAlign = TextAlign.Start), modifier = Modifier.weight(1f))
                if (m.button != null && m.onButton != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        m.button,
                        style = text.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = readableOn(accent)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent)
                            .clickable(onClick = m.onButton)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        // Off tiles are a plain grey that suits a dark or a light island.
        val grey = if (Color(config.background).luminance() < 0.5f) Color(0xFF4A4A50) else Color(0xFFCFCFD4)
        state.tiles.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { tile -> Tile(tile, state.on[tile], accent, fg, grey, text, reduceMotion) { actions.onTile(tile) } }
                repeat(4 - row.size) { Spacer(Modifier.width(76.dp)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        PanelSlider(Icons.Filled.BrightnessMedium, "Brightness", state.brightness, accent, fg, actions.onBrightness) {
            Text(
                "Auto",
                style = text.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (state.autoBrightness) readableOn(accent) else fg),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (state.autoBrightness) accent else fg.copy(alpha = 0.14f))
                    .clickable(onClick = actions.onAutoBrightness)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .semantics { stateDescription = if (state.autoBrightness) "On" else "Off" },
            )
        }
        PanelSlider(Icons.AutoMirrored.Filled.VolumeUp, "Media volume", state.volume, accent, fg, actions.onVolume, null)
        // A small handle: swipe up anywhere on the panel to close it.
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(fg.copy(alpha = 0.3f)))
        }
    }
}

@Composable
private fun PanelSlider(
    icon: ImageVector,
    label: String,
    value: Float,
    accent: Color,
    fg: Color,
    onChange: (Float) -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.padding(horizontal = 6.dp).size(22.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = fg.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f).semantics { contentDescription = label }.testTag("slider-$label"),
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

/**
 * A round tile: grey when off, filled with the highlight color when on. The fill grows from the
 * center out (and shrinks back in), and a tap gives the tile a small bounce. Tiles that open
 * Android's own pop-up carry a small arrow.
 */
@Composable
private fun Tile(tile: QuickTile, on: Boolean?, accent: Color, fg: Color, grey: Color, text: TextStyle, reduceMotion: Boolean, onClick: () -> Unit) {
    val fill by animateFloatAsState(if (on == true) 1f else 0f, if (reduceMotion) snap() else tween(260), label = "tileFill")
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .width(76.dp)
            .testTag("tile-${tile.name}")
            .semantics { stateDescription = when (on) { true -> "On"; false -> "Off"; null -> "" } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
                .testTag("tileCircle-${tile.name}")
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(grey)
                    if (fill > 0f) drawCircle(accent, radius = size.minDimension / 2f * fill)
                }
                .clickable {
                    if (!reduceMotion) {
                        scope.launch {
                            bounce.snapTo(0.86f)
                            bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
                        }
                    }
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(tile.icon, contentDescription = tile.label, tint = if (fill > 0.5f) readableOn(accent) else readableOn(grey), modifier = Modifier.size(24.dp))
            if (tile.kind == TileKind.OpensSystem) {
                Icon(
                    Icons.Filled.NorthEast,
                    contentDescription = "Opens Android's settings",
                    tint = (if (fill > 0.5f) readableOn(accent) else readableOn(grey)).copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-9).dp, y = 9.dp).size(11.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(tile.label, style = text, maxLines = 2, modifier = Modifier.height(28.dp))
    }
}

/** Black or white, whichever reads better on [color]. */
fun readableOn(color: Color): Color = if (color.luminance() > 0.5f) Color.Black else Color.White
