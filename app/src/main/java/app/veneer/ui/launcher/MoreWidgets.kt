package app.veneer.ui.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.veneer.data.PhotoShape
import app.veneer.data.WidgetStyle
import app.veneer.system.WeatherCondition
import app.veneer.system.WeatherNow
import app.veneer.system.WidgetPhotos
import app.veneer.ui.theme.LocalLauncherTheme
import app.veneer.ui.theme.contentColorFor
import app.veneer.veneer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.min

/** True in the widget picker and layout previews: widgets show sample data instead of asking for anything. */
val LocalWidgetPreview = compositionLocalOf { false }

private val SampleWeather = WeatherNow(21, 24, 15, 1, true, false, "Sample", 0L)

// --- Photo ------------------------------------------------------------------------------------

fun photoShape(style: WidgetStyle): Shape = when (style.photoShape) {
    PhotoShape.Rounded -> RoundedCornerShape(style.cornerDp.dp)
    PhotoShape.Pill -> RoundedCornerShape(50)
    PhotoShape.Circle -> CircleShape
    PhotoShape.Square -> RectangleShape
}

/** A picture in a frame. Until one is picked it invites a tap. */
@Composable
fun PhotoWidget(style: WidgetStyle, textColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val image by produceState<ImageBitmap?>(null, style.image) {
        value = style.image?.let { name -> withContext(Dispatchers.IO) { WidgetPhotos.load(context, name, 1280) } }
    }
    val shape = photoShape(style)
    Box(modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .then(if (style.photoShape == PhotoShape.Circle) Modifier.fillMaxHeight().aspectRatio(1f) else Modifier.fillMaxSize())
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(theme.accent).copy(alpha = 0.55f), Color.Black.copy(alpha = 0.55f))))
                .then(if (style.border) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.45f), shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = image
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (style.monochrome) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = textColor, modifier = Modifier.size(32.dp))
                    if (!LocalWidgetPreview.current) {
                        Text("Tap to pick a photo", color = textColor, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// --- Weather ----------------------------------------------------------------------------------

fun weatherIcon(c: WeatherCondition): ImageVector = when (c) {
    WeatherCondition.Clear -> Icons.Filled.WbSunny
    WeatherCondition.ClearNight -> Icons.Filled.NightsStay
    WeatherCondition.PartlyCloudy -> Icons.Filled.WbCloudy
    WeatherCondition.Cloudy -> Icons.Filled.Cloud
    WeatherCondition.Fog -> Icons.Filled.Dehaze
    WeatherCondition.Drizzle -> Icons.Filled.Grain
    WeatherCondition.Rain -> Icons.Filled.Umbrella
    WeatherCondition.Snow -> Icons.Filled.AcUnit
    WeatherCondition.Storm -> Icons.Filled.Thunderstorm
}

/** The current weather, refreshed every 30 minutes while the home screen is showing. */
@Composable
fun rememberWeather(): State<WeatherNow?> {
    val app = LocalContext.current.veneer
    if (LocalWidgetPreview.current) return remember { mutableStateOf(SampleWeather) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = app.weather.now.collectAsState()
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                app.weather.refresh()
                delay(30 * 60_000L)
            }
        }
    }
    return state
}

@Composable
fun WeatherWidget(base: TextStyle, sizeSp: Int, textColor: Color) {
    val weather by rememberWeather()
    val now = weather
    val accent = Color(LocalLauncherTheme.current.accent)
    val icon = if (now != null) weatherIcon(now.condition) else Icons.Filled.WbCloudy
    val small = base.copy(fontSize = (sizeSp * 0.42f).coerceAtLeast(11f).sp, color = textColor.copy(alpha = 0.8f), textAlign = TextAlign.Start)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxHeight >= 120.dp && maxWidth < 260.dp) {
            // A square tile, the way weather widgets usually look: place, big temperature, then the details.
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        now?.place ?: "Weather",
                        style = small.copy(fontWeight = FontWeight.Bold, color = textColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (now != null) "${now.temperature}°" else "--°",
                        style = base.copy(fontSize = (sizeSp * 1.5f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start),
                        maxLines = 1,
                    )
                }
                Column {
                    Icon(icon, contentDescription = now?.condition?.label, tint = accent, modifier = Modifier.size((sizeSp * 0.8f).dp))
                    Text(now?.condition?.label ?: "Tap to set up", style = small.copy(color = textColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (now != null) Text("H:${now.high}°  L:${now.low}°", style = small, maxLines = 1)
                }
            }
        } else {
            val wide = maxWidth >= 220.dp
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(icon, contentDescription = now?.condition?.label, tint = accent, modifier = Modifier.size((sizeSp * 1.3f).dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (now != null) "${now.temperature}°" else "--°",
                        style = base.copy(fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start),
                        maxLines = 1,
                    )
                    val detail = when {
                        now == null -> "Tap to set up weather"
                        !wide -> now.condition.label
                        else -> "${now.condition.label} · ${now.high}° / ${now.low}°"
                    }
                    Text(detail, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// --- At a glance ------------------------------------------------------------------------------

/** Like Google's At a Glance: the date, the weather and the next event in one line or two. */
@Composable
fun GlanceWidget(base: TextStyle, sizeSp: Int, textColor: Color) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now by rememberNow(false)
    val weather by rememberWeather()
    val preview = LocalWidgetPreview.current
    val next by produceState<AgendaEvent?>(null, now / (5 * 60_000L)) {
        value = if (!preview && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            withContext(Dispatchers.IO) { nextEvents(context, 1).firstOrNull() }
        } else null
    }
    val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale).format(Date(now))
    Column(Modifier.fillMaxWidth()) {
        Text(date, style = base.copy(fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start), maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val w = weather
            if (w != null) {
                Icon(weatherIcon(w.condition), contentDescription = null, tint = textColor, modifier = Modifier.size((sizeSp * 0.8f).dp))
                Spacer(Modifier.width(4.dp))
                Text("${w.temperature}°", style = base.copy(fontSize = (sizeSp * 0.75f).sp, textAlign = TextAlign.Start), maxLines = 1)
            }
            val e = next
            val tail = when {
                e != null -> e.title + " · " + DateFormat.getTimeFormat(context).format(Date(e.begin))
                preview -> "Coffee with Sam · 4:30 PM"
                else -> null
            }
            if (tail != null) {
                Text(
                    (if (w != null) "  ·  " else "") + tail,
                    style = base.copy(fontSize = (sizeSp * 0.75f).sp, color = textColor.copy(alpha = 0.85f), textAlign = TextAlign.Start),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// --- Month calendar ---------------------------------------------------------------------------

/** This month at a glance, with today circled in the accent color. */
@Composable
fun MonthCalendarWidget(base: TextStyle, textColor: Color) {
    val theme = LocalLauncherTheme.current
    val locale = LocalConfiguration.current.locales[0]
    val density = LocalDensity.current
    val now by rememberNow(false)
    val cal = remember(now / 3_600_000L) { Calendar.getInstance(locale).apply { timeInMillis = now } }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val firstDow = cal.firstDayOfWeek
    val lead = (first.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val initials = DateFormatSymbols.getInstance(locale).shortWeekdays
    val month = SimpleDateFormat("LLLL", locale).format(Date(now)).uppercase(locale)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rows = 7 + 1 // header + weekdays + up to 6 weeks
        val cell = min(maxWidth.value / 7f, maxHeight.value / rows)
        val fontSp = with(density) { (cell * 0.46f).dp.toSp() }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(
                month,
                style = base.copy(fontSize = fontSp * 1.1f, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().height(cell.dp),
                maxLines = 1,
            )
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val dow = (firstDow - 1 + i) % 7 + 1
                    Text(
                        initials[dow].take(1),
                        style = base.copy(fontSize = fontSp, color = textColor.copy(alpha = 0.6f), textAlign = TextAlign.Center, shadow = null),
                        modifier = Modifier.weight(1f).height(cell.dp),
                    )
                }
            }
            val weeks = (lead + daysInMonth + 6) / 7
            for (week in 0 until weeks) {
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        val day = week * 7 + i - lead + 1
                        Box(Modifier.weight(1f).height(cell.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..daysInMonth) {
                                val isToday = day == today
                                if (isToday) {
                                    Box(Modifier.size((cell * 0.92f).dp).clip(CircleShape).background(Color(theme.accent)))
                                }
                                Text(
                                    day.toString(),
                                    style = base.copy(
                                        fontSize = fontSp,
                                        fontWeight = if (isToday) FontWeight.Bold else base.fontWeight,
                                        color = if (isToday) contentColorFor(Color(theme.accent)) else textColor,
                                        textAlign = TextAlign.Center,
                                        shadow = if (isToday) null else base.shadow,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Clocks -----------------------------------------------------------------------------------

/** A clean analog clock face. The second hand, if shown, uses the accent color. */
@Composable
fun AnalogClockWidget(style: WidgetStyle, textColor: Color) {
    val accent = Color(LocalLauncherTheme.current.accent)
    val now by rememberNow(style.showSeconds)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Canvas(Modifier.aspectRatio(1f)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(textColor.copy(alpha = 0.10f), r, c)
        drawCircle(textColor.copy(alpha = 0.35f), r, c, style = Stroke(r * 0.03f))
        for (i in 0 until 60) {
            val major = i % 5 == 0
            if (!major && r < 120f) continue
            rotate(i * 6f, c) {
                drawLine(
                    textColor.copy(alpha = if (major) 0.9f else 0.35f),
                    Offset(c.x, c.y - r * 0.92f),
                    Offset(c.x, c.y - r * (if (major) 0.78f else 0.86f)),
                    strokeWidth = r * (if (major) 0.035f else 0.015f),
                    cap = StrokeCap.Round,
                )
            }
        }
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val minutes = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
        val hours = cal.get(Calendar.HOUR) + minutes / 60f
        rotate(hours * 30f, c) { drawLine(textColor, c, Offset(c.x, c.y - r * 0.5f), strokeWidth = r * 0.07f, cap = StrokeCap.Round) }
        rotate(minutes * 6f, c) { drawLine(textColor, c, Offset(c.x, c.y - r * 0.74f), strokeWidth = r * 0.045f, cap = StrokeCap.Round) }
        if (style.showSeconds) {
            rotate(cal.get(Calendar.SECOND) * 6f, c) { drawLine(accent, Offset(c.x, c.y + r * 0.12f), Offset(c.x, c.y - r * 0.82f), strokeWidth = r * 0.018f, cap = StrokeCap.Round) }
        }
        drawCircle(accent, r * 0.06f, c)
    }
    }
}

/** Big digits on split tiles, like a flip clock. */
@Composable
fun FlipClockWidget(style: WidgetStyle, base: TextStyle, textColor: Color) {
    val context = LocalContext.current
    val now by rememberNow(false)
    val is24 = style.use24h ?: DateFormat.is24HourFormat(context)
    val text = SimpleDateFormat(if (is24) "HHmm" else "hhmm", java.util.Locale.US).format(Date(now))
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val gap = 8.dp
        val tileW = min(((maxWidth - gap * 3 - 16.dp) / 4).value, maxHeight.value * 0.72f)
        val tileH = min(maxHeight.value, tileW * 1.35f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            text.forEachIndexed { i, ch ->
                if (i == 2) {
                    Text(":", style = base.copy(fontSize = with(LocalDensity.current) { (tileH * 0.55f).dp.toSp() }, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 4.dp))
                } else if (i > 0) Spacer(Modifier.width(gap / 2))
                Box(
                    Modifier
                        .size(tileW.dp, tileH.dp)
                        .clip(RoundedCornerShape((tileW * 0.18f).dp))
                        .background(textColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ch.toString(),
                        style = base.copy(fontSize = with(LocalDensity.current) { (tileH * 0.72f).dp.toSp() }, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        maxLines = 1,
                    )
                    Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color.Black.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

// --- Battery ring -----------------------------------------------------------------------------

/** The battery as a ring that fills with the charge, percent in the middle. */
@Composable
fun BatteryRing(percent: Int, charging: Boolean, base: TextStyle, sizeSp: Int, textColor: Color) {
    val accent = Color(LocalLauncherTheme.current.accent)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().aspectRatio(1f, matchHeightConstraintsFirst = true)) {
            val stroke = size.minDimension * 0.09f
            val inset = stroke / 2
            drawArc(textColor.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), androidx.compose.ui.geometry.Size(size.minDimension - stroke, size.minDimension - stroke), style = Stroke(stroke))
            drawArc(accent, -90f, 360f * percent / 100f, false, Offset(inset, inset), androidx.compose.ui.geometry.Size(size.minDimension - stroke, size.minDimension - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", style = base.copy(fontSize = (sizeSp * 0.6f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            Text(
                if (charging) "charging" else "battery",
                style = base.copy(fontSize = (sizeSp * 0.28f).coerceAtLeast(10f).sp, color = textColor.copy(alpha = 0.7f), textAlign = TextAlign.Center),
            )
        }
    }
}
