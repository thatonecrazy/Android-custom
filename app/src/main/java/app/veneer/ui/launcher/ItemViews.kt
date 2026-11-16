package app.veneer.ui.launcher

import android.appwidget.AppWidgetHostView
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.veneer.apps.AppEntry
import app.veneer.data.HomeItem
import app.veneer.data.IconRef
import app.veneer.ui.theme.LocalLauncherTheme
import app.veneer.ui.theme.LocalThemeFont
import app.veneer.veneer
import app.veneer.widgets.WidgetHost

/** An app icon rendered with the current theme. Shows nothing until the icon is ready. */
@Composable
fun AppIcon(entry: AppEntry?, override: IconRef?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val provider = context.veneer.icons
    val theme = LocalLauncherTheme.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val version by provider.version.collectAsState()
    if (entry == null) {
        Box(modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Color(0x40FFFFFF)))
        return
    }
    val icon by produceState(
        initialValue = provider.cached(entry, override, theme, sizePx),
        entry.key, override, theme.iconPack, theme.iconShape, theme.iconStyle, theme.themedFg, theme.themedBg,
        theme.wrapLegacyIcons, theme.legacyBg, sizePx, version,
    ) {
        value = provider.appIcon(entry, override, theme, sizePx)
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Spacer(modifier.size(size))
    }
}

@Composable
fun IconLabel(text: String, color: Color, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val theme = LocalLauncherTheme.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = LocalThemeFont.current,
            fontSize = theme.labelSizeSp.sp,
            lineHeight = (theme.labelSizeSp * 1.25f).sp,
            color = color,
            fontWeight = if (theme.labelBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            shadow = if (theme.labelShadow) Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 5f) else null,
        ),
    )
}

/** Icon with an optional label underneath, as shown on the home screen, dock and drawer. */
@Composable
fun AppTile(
    entry: AppEntry?,
    label: String,
    override: IconRef?,
    iconSize: Dp,
    showLabel: Boolean,
    labelColor: Color,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
) {
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(entry, override, iconSize)
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            IconLabel(label, labelColor, Modifier.padding(horizontal = 2.dp))
        }
    }
}

/** Waits until the finger lifts or moves away. Returns normally in both cases. */
private suspend fun AwaitPointerEventScope.waitForReleaseOrSlop(down: PointerInputChange, slop: Float) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed || change.isConsumed) return
        if ((change.position - down.position).getDistance() > slop) return
    }
}

/**
 * Wraps one home item: detects a long press (which opens the item menu and can turn into a drag)
 * and a tap. The long press is watched in the Initial pass so it also works over hosted widgets.
 */
@Composable
fun ItemFrame(
    item: HomeItem,
    controller: HomeController,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val currentItem by rememberUpdatedState(item)
    val drag = controller.drag
    val hidden = drag != null && drag.started && drag.item.id == item.id
    Box(
        modifier
            .onGloballyPositioned { coords = it }
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .pointerInput(item.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForReleaseOrSlop(down, viewConfiguration.touchSlop)
                    }
                    if (finished == null) {
                        val c = coords ?: return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.onItemLongPress(
                            currentItem,
                            grabOffset = down.position,
                            pointerRoot = c.localToRoot(down.position),
                            size = c.size,
                        )
                    }
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(pressed)
    }
}

/** A widget from another app, drawn by its own Android view. */
@Composable
fun HostedWidget(appWidgetId: Int, host: WidgetHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view: AppWidgetHostView? = remember(appWidgetId) { host.view(context, appWidgetId) }
    if (view == null) {
        Box(
            modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Widget unavailable\nHold to remove",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    AndroidView(
        factory = {
            (view.parent as? ViewGroup)?.removeView(view)
            view
        },
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                host.updateSize(view, size.width / density.density, size.height / density.density)
            },
    )
}
