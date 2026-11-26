package app.veneer.ui.launcher

import android.appwidget.AppWidgetHostView
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withStarted
import app.veneer.icons.IconProvider
import app.veneer.icons.iconSpec
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.veneer.apps.AppEntry
import app.veneer.data.HomeItem
import app.veneer.data.AppOverride
import app.veneer.data.IconRef
import app.veneer.data.forApp
import app.veneer.ui.theme.LocalLauncherTheme
import app.veneer.ui.theme.LocalThemeFont
import app.veneer.veneer
import app.veneer.widgets.WidgetHost

/** Notification counts keyed by "package#userSerial", for the dots on icons. Empty outside the home screen. */
val LocalNotificationCounts = compositionLocalOf<Map<String, Int>> { emptyMap() }

/** The small dot at the top corner of an icon that has notifications. */
/** A notification dot in the chosen dot color, showing the number when that's turned on. Pops in when it appears. */
@Composable
fun NotificationDot(iconSize: Dp, count: Int, modifier: Modifier = Modifier) {
    val theme = LocalLauncherTheme.current
    val color = Color(theme.dotColor ?: theme.accent)
    val withNumber = theme.dotCount && count > 0
    val size = if (withNumber) (iconSize * 0.36f).coerceIn(16.dp, 22.dp) else (iconSize * 0.26f).coerceIn(9.dp, 16.dp)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 520f)) }
    Box(
        modifier
            .offset(x = size * 0.15f, y = -size * 0.15f)
            .graphicsLayer { scaleX = appear.value; scaleY = appear.value }
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (withNumber) {
            Text(
                if (count > 99) "99+" else count.toString(),
                color = if (color.luminance() > 0.6f) Color.Black else Color.White,
                fontSize = (size.value * (if (count > 9) 0.42f else 0.55f)).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Where something was last drawn, in window pixels, so an app can open out of its icon. */
class LaunchBounds {
    var rect: android.graphics.Rect? = null
}

fun Modifier.recordBounds(holder: LaunchBounds): Modifier = onGloballyPositioned { coords ->
    val r = coords.boundsInWindow()
    holder.rect = android.graphics.Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
}

/** Every app's own changes (shape, style, colors), so any icon anywhere can draw itself the way its app was set up. */
val LocalAppLooks = compositionLocalOf<Map<String, AppOverride>> { emptyMap() }

/**
 * An app icon rendered with the current theme. While a restyled icon is being drawn the old one
 * stays up, then the new one fades in with a small bounce. Size changes glide.
 */
@Composable
fun AppIcon(entry: AppEntry?, override: IconRef?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val provider = context.veneer.icons
    val theme = LocalLauncherTheme.current.forApp(entry?.let { LocalAppLooks.current[it.key.id] })
    val shownSize by animateDpAsState(size, spring(stiffness = Spring.StiffnessMediumLow), label = "iconSize")
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val version by provider.version.collectAsState()
    if (entry == null) {
        Box(modifier.size(shownSize).clip(RoundedCornerShape(shownSize / 4)).background(Color(0x40FFFFFF)))
        return
    }
    val spec = theme.iconSpec()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val icon by produceState(
        initialValue = provider.cached(entry, override, theme, sizePx),
        entry.key, override, theme.iconPack, spec, IconProvider.bucket(sizePx), version,
    ) {
        val ready = provider.cached(entry, override, theme, sizePx)
        if (ready != null) {
            value = ready
            return@produceState
        }
        // A screen in the background (the home screen while you're in Customize) waits until it's seen again.
        lifecycle.withStarted { }
        value = provider.appIcon(entry, override, theme, sizePx)
    }
    IconImage(icon, shownSize, modifier)
}

/** Draws an icon bitmap at [size]; a new bitmap fades in over the old one, and a restyle bounces. */
@Composable
private fun IconImage(bitmap: ImageBitmap?, size: Dp, modifier: Modifier) {
    var current by remember { mutableStateOf(bitmap) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(bitmap) {
        if (bitmap == null || bitmap === current) return@LaunchedEffect
        val restyle = current != null
        previous = current
        current = bitmap
        fade.snapTo(0f)
        coroutineScope {
            launch { fade.animateTo(1f, tween(if (restyle) 260 else 160)) }
            if (restyle) {
                launch {
                    pop.snapTo(0.86f)
                    pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 380f))
                }
            }
        }
        previous = null
    }
    Canvas(modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
        val dst = IntSize(this.size.width.roundToInt().coerceAtLeast(1), this.size.height.roundToInt().coerceAtLeast(1))
        previous?.let { drawImage(it, dstSize = dst, alpha = 1f - fade.value, filterQuality = FilterQuality.Medium) }
        current?.let { drawImage(it, dstSize = dst, alpha = fade.value, filterQuality = FilterQuality.Medium) }
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
    val theme = LocalLauncherTheme.current
    val counts = LocalNotificationCounts.current
    val count = if (entry != null) counts["${entry.packageName}#${entry.key.userSerial}"] ?: 0 else 0
    val hasDot = theme.notificationDots && count > 0
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AppIcon(entry, override, iconSize)
            if (hasDot) NotificationDot(iconSize, count, Modifier.align(Alignment.TopEnd))
        }
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

/** A rounded popup menu used for every hold-to-open menu on the home screen and in the app list. */
@Composable
fun FriendlyMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = content,
    )
}

/** One menu option with a small colored icon in front. */
@Composable
fun MenuRow(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontWeight = FontWeight.Medium) },
        leadingIcon = {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp)) }
        },
        onClick = onClick,
    )
}
