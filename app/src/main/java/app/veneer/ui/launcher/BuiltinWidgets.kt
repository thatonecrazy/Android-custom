package app.veneer.ui.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.veneer.data.BuiltinKind
import app.veneer.data.WidgetAlign
import app.veneer.data.WidgetStyle
import app.veneer.ui.theme.LocalLauncherTheme
import app.veneer.ui.theme.LocalThemeFont
import app.veneer.veneer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The current time, ticking every second or every minute while the screen is visible. */
@Composable
fun rememberNow(everySecond: Boolean): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(System.currentTimeMillis(), everySecond) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                val period = if (everySecond) 1_000L else 60_000L
                delay(period - value % period + 20)
            }
        }
    }
}

data class BatteryStatus(val percent: Int, val charging: Boolean)

@Composable
fun rememberBattery(): State<BatteryStatus> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(BatteryStatus(0, false)) }
    DisposableEffect(context) {
        fun read(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            state.value = BatteryStatus(
                if (level >= 0) level * 100 / scale else 0,
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = read(intent)
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        read(sticky)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private fun clockPattern(context: Context, style: WidgetStyle): String {
    val is24 = style.use24h ?: DateFormat.is24HourFormat(context)
    val base = if (is24) "HH:mm" else "h:mm"
    return if (style.showSeconds) "$base:ss" else base
}

private fun datePattern(): String = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd")

private fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

/** One of Veneer's own widgets, drawn entirely with the user's chosen font, size and colors. */
@Composable
fun BuiltinWidgetView(kind: BuiltinKind, style: WidgetStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themeFont = LocalThemeFont.current
    val font = remember(style.fontId, themeFont) {
        style.fontId?.let { context.veneer.fonts.family(it) } ?: themeFont
    }
    val theme = LocalLauncherTheme.current
    val textColor = Color(style.color ?: theme.labelColor)
    val shadow = style.shadow ?: theme.labelShadow
    val background = style.background
        ?: if (kind == BuiltinKind.Search || kind == BuiltinKind.Media || kind == BuiltinKind.Agenda) theme.dockBg else 0
    val align = when (style.align) {
        WidgetAlign.Start -> TextAlign.Start
        WidgetAlign.Center -> TextAlign.Center
        WidgetAlign.End -> TextAlign.End
    }
    val horizontal = when (style.align) {
        WidgetAlign.Start -> Alignment.Start
        WidgetAlign.Center -> Alignment.CenterHorizontally
        WidgetAlign.End -> Alignment.End
    }
    val base = TextStyle(
        fontFamily = font,
        color = textColor,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 8f) else null,
    )
    fun String.cased() = if (style.uppercase) uppercase(Locale.getDefault()) else this

    val hasBackground = (background ushr 24) != 0
    // The card hugs its content, centered in the cell, so a one-row search bar doesn't balloon.
    Box(modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (hasBackground) {
                    Modifier.clip(RoundedCornerShape(style.cornerDp.dp)).background(Color(background))
                } else Modifier,
            )
            .padding(horizontal = if (hasBackground) 18.dp else 8.dp, vertical = if (hasBackground) 14.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            BuiltinKind.Clock -> {
                val now by rememberNow(style.showSeconds)
                val text = remember(now, style.use24h, style.showSeconds) {
                    SimpleDateFormat(clockPattern(context, style), Locale.getDefault()).format(Date(now))
                }
                FittedText(text.cased(), base, style.sizeSp, Modifier.fillMaxWidth())
            }
            BuiltinKind.Date -> {
                val now by rememberNow(false)
                val text = remember(now) { SimpleDateFormat(datePattern(), Locale.getDefault()).format(Date(now)) }
                FittedText(text.cased(), base, style.sizeSp, Modifier.fillMaxWidth())
            }
            BuiltinKind.ClockDate -> {
                val now by rememberNow(style.showSeconds)
                val time = remember(now, style.use24h, style.showSeconds) {
                    SimpleDateFormat(clockPattern(context, style), Locale.getDefault()).format(Date(now))
                }
                val date = remember(now) { SimpleDateFormat(datePattern(), Locale.getDefault()).format(Date(now)) }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = horizontal, verticalArrangement = Arrangement.Center) {
                    FittedText(time.cased(), base, style.sizeSp, Modifier.fillMaxWidth().weight(1f, fill = false))
                    FittedText(date.cased(), base, (style.sizeSp * 0.26f).toInt().coerceAtLeast(10), Modifier.fillMaxWidth())
                }
            }
            BuiltinKind.Greeting -> {
                val now by rememberNow(false)
                val hour = remember(now) { Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) }
                val text = greeting(hour) + (style.text.trim().takeIf { it.isNotEmpty() }?.let { ", $it" } ?: "")
                FittedText(text.cased(), base, style.sizeSp, Modifier.fillMaxWidth(), maxLines = 2)
            }
            BuiltinKind.Text -> {
                val text = style.text.ifBlank { "Hold to edit this text" }
                FittedText(text.cased(), base, style.sizeSp, Modifier.fillMaxWidth(), maxLines = 3)
            }
            BuiltinKind.Search -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = textColor, modifier = Modifier.size((style.sizeSp + 6).dp))
                    Spacer(Modifier.width(12.dp))
                    FittedText("Search apps".cased(), base.copy(textAlign = TextAlign.Start), style.sizeSp, Modifier.weight(1f))
                }
            }
            BuiltinKind.Battery -> {
                val battery by rememberBattery()
                val text = "${battery.percent}%" + if (battery.charging) " ⚡" else ""
                FittedText(text, base, style.sizeSp, Modifier.fillMaxWidth())
            }
            BuiltinKind.Media -> MusicWidget(base, style.sizeSp, textColor)
            BuiltinKind.Agenda -> AgendaWidget(base, style.sizeSp, textColor)
        }
    }
    }
}

/** Text that shrinks to fit its box, never growing past [maxSp]. */
@Composable
fun FittedText(text: String, style: TextStyle, maxSp: Int, modifier: Modifier = Modifier, maxLines: Int = 1) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = maxSp.sp, lineHeight = (maxSp * 1.15f).sp),
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = maxSp.coerceAtLeast(9).sp, stepSize = 1.sp),
    )
}
