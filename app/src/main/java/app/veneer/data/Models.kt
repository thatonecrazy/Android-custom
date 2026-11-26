package app.veneer.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Page index used for items that live in the dock instead of on a home page. */
const val DOCK_PAGE = -1

@Serializable
enum class IconShape(val label: String) {
    Original("System"),
    Circle("Circle"),
    Squircle("Squircle"),
    RoundedSquare("Rounded"),
    Square("Square"),
    Teardrop("Teardrop"),
    Leaf("Leaf"),
    Hexagon("Hexagon"),
    Pebble("Pebble"),
    Octagon("Octagon"),
    Flower("Flower"),
    Arch("Arch"),
    Diamond("Diamond"),
}

/** The surface each icon sits on. */
@Serializable
enum class IconFinish(val label: String) {
    Flat("Flat"),
    Glass("Glass"),
}

@Serializable
enum class IconStyle(val label: String) {
    Original("Full color"),
    Vibrant("Extra vivid"),
    Themed("Themed"),
    /** Each app's symbol in a deep version of its own color, on a soft version of it. */
    Tinted("Tinted"),
    /** Each app's symbol in its own color, on your background color. */
    ColorSymbols("Color symbols"),
    Pastel("Pastel"),
    Grayscale("Grayscale"),
}

@Serializable
enum class BarIcons(val label: String) {
    Light("Light icons"),
    Dark("Dark icons"),
}

/** What a home screen gesture does. */
@Serializable
enum class GestureAction(val label: String) {
    None("Nothing"),
    AppList("App list"),
    Search("Search"),
    Notifications("Notifications"),
    QuickSettings("Quick settings"),
    LockScreen("Lock screen"),
    Customize("Customize"),
    OpenApp("Open an app"),
}

@Serializable
enum class WallpaperStyle(val label: String) {
    Solid("Solid"),
    Vertical("Vertical"),
    Diagonal("Diagonal"),
    Radial("Glow"),
    Aurora("Aurora"),
    /** Dark, with glowing segmented tendrils; a third color adds a watching eye. */
    Tendrils("Tendrils"),
}

/** A wallpaper the app can generate itself: a solid color or a two-color gradient. */
@Serializable
data class WallpaperSpec(
    val style: WallpaperStyle = WallpaperStyle.Vertical,
    val color1: Int = 0xFF1D2671.toInt(),
    val color2: Int = 0xFFC33764.toInt(),
    /** An optional third color, for three-color gradients and aurora glows. */
    val color3: Int? = null,
    /** A bundled photo (see PhotoWallpapers) used instead of the generated art. */
    val photo: String? = null,
)

/** How often the wallpaper shuffle moves to the next picture. It always changes while the screen is off. */
@Serializable
enum class ShuffleInterval(val label: String, val millis: Long) {
    EveryLock("Every time you lock", 0),
    Hourly("Every hour", 60 * 60 * 1000L),
    Daily("Every day", 24 * 60 * 60 * 1000L),
}

@Serializable
enum class ShuffleTarget(val label: String) {
    Both("Home and lock"),
    Home("Home screen"),
    Lock("Lock screen"),
}

/** Your own pictures, shown as the wallpaper one after another. Files live in the app's private storage. */
@Serializable
data class WallpaperShuffle(
    val enabled: Boolean = false,
    val files: List<String> = emptyList(),
    val interval: ShuffleInterval = ShuffleInterval.EveryLock,
    val target: ShuffleTarget = ShuffleTarget.Both,
    val next: Int = 0,
    val lastChange: Long = 0,
)

/**
 * Everything about how the launcher looks. A theme can be saved, exported and shared
 * independently of the home screen layout.
 */
@Serializable
data class ThemeConfig(
    val name: String = "Default",

    // Icons
    val iconPack: String? = null,
    val iconShape: IconShape = IconShape.Squircle,
    val iconSizeDp: Int = 54,
    val iconStyle: IconStyle = IconStyle.Original,
    val themedFg: Int = 0xFF1F2340.toInt(),
    val themedBg: Int = 0xFFDDE3FF.toInt(),
    val wrapLegacyIcons: Boolean = true,
    val legacyBg: Int = 0xFFFFFFFF.toInt(),
    /** Glass puts every icon on a glossy 3D base with a light rim, a shine and a shadow. */
    val iconFinish: IconFinish = IconFinish.Flat,
    val glassTint: Int = 0xFF232327.toInt(),
    /** How see-through the glass is, 0 (solid) to 90 percent. */
    val glassClarity: Int = 10,
    /** Strength of the shine and the bright rim, 0 to 100. */
    val glassShine: Int = 70,
    /** Strength of the shading and the shadow under the icon, 0 to 100. */
    val glassDepth: Int = 60,
    /** Corner roundness of the Rounded shape, 0 (square) to 100 (circle-like). */
    val iconCorner: Int = 44,
    /** Size of the symbol inside its shape, in percent. */
    val iconScale: Int = 100,
    /** Outline around each icon: width in tenths of a percent of the icon (0 = none) and color. */
    val iconOutline: Int = 0,
    val iconOutlineColor: Int = 0xFFFFFFFF.toInt(),
    /** Soft shadow under flat icons, 0 to 100. */
    val iconShadow: Int = 0,
    /** How solid icon backgrounds are, 0 (clear) to 100. */
    val iconBgOpacity: Int = 100,
    /** Second background color for a gradient with symbol styles; null is one color. */
    val themedBg2: Int? = null,
    /** Pulls every icon's colors toward [iconTint] by [iconTintStrength] percent. */
    val iconTint: Int = 0xFF8C7BFF.toInt(),
    val iconTintStrength: Int = 0,
    /** Notification dot color (null is the accent) and whether it shows the number. */
    val dotColor: Int? = null,
    val dotCount: Boolean = false,

    // Labels and font
    val fontId: String = "system",
    val showHomeLabels: Boolean = true,
    val showDrawerLabels: Boolean = true,
    val labelSizeSp: Int = 12,
    val labelColor: Int = 0xFFFFFFFF.toInt(),
    val labelShadow: Boolean = true,
    val labelBold: Boolean = false,

    // Home screen
    val gridColumns: Int = 4,
    val gridRows: Int = 6,
    val dockEnabled: Boolean = true,
    val dockColumns: Int = 5,
    val dockBg: Int = 0x33FFFFFF,
    val dockCornerDp: Int = 28,
    val pageIndicator: Boolean = true,
    val wallpaperDim: Float = 0f,

    // System bars, gestures and notification dots
    val hideStatusBar: Boolean = false,
    val statusBarIcons: BarIcons = BarIcons.Light,
    val gestureSwipeUp: GestureAction = GestureAction.AppList,
    val gestureSwipeDown: GestureAction = GestureAction.Notifications,
    val gestureDoubleTap: GestureAction = GestureAction.None,
    /** The app each gesture opens when it's set to [GestureAction.OpenApp], keyed by gesture name. */
    val gestureApps: Map<String, String> = emptyMap(),
    val notificationDots: Boolean = true,

    // A light that runs around the screen edge, like Samsung's edge lighting
    val edgeGlow: Boolean = false,
    /** Null uses the accent color. */
    val edgeGlowColor: Int? = null,
    val soundModeGlow: Boolean = false,

    // App drawer
    val drawerColumns: Int = 4,
    val drawerBg: Int = 0xEB101216.toInt(),
    val drawerTextColor: Int = 0xFFFFFFFF.toInt(),

    // Accent used for menus, highlights and the settings screens
    val accent: Int = 0xFF8C7BFF.toInt(),

    /** The wallpaper made in Veneer for this look, if any, so a saved look can bring it back. */
    val wallpaper: WallpaperSpec? = null,
)

@Serializable
enum class BuiltinKind(val label: String, val defaultSpanX: Int, val defaultSpanY: Int) {
    Clock("Clock", 4, 2),
    Date("Date", 4, 1),
    ClockDate("Clock and date", 4, 2),
    Greeting("Greeting", 4, 1),
    Text("Text", 4, 1),
    Search("Search bar", 4, 1),
    Battery("Battery", 2, 1),
    Media("Music", 4, 1),
    Agenda("Calendar agenda", 4, 2),
    Photo("Photo", 4, 2),
    Weather("Weather", 2, 1),
    MonthCalendar("Month calendar", 2, 2),
    AnalogClock("Analog clock", 2, 2),
    FlipClock("Flip clock", 4, 2),
    Glance("At a glance", 4, 1),
}

/** The frame of a photo widget. */
@Serializable
enum class PhotoShape(val label: String) {
    Rounded("Rounded"),
    Pill("Pill"),
    Circle("Circle"),
    Square("Square"),
}

/** Where the weather comes from: the phone's rough location, or a place picked by name. */
@Serializable
data class WeatherSettings(
    val useDeviceLocation: Boolean = true,
    val place: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Null picks by country: Fahrenheit in the US, Celsius elsewhere. */
    val fahrenheit: Boolean? = null,
)

@Serializable
enum class WidgetAlign { Start, Center, End }

/** Styling for the built-in widgets. Every field is user-editable. */
@Serializable
data class WidgetStyle(
    /** Null means "use the theme font". */
    val fontId: String? = null,
    /** Null follows the theme's label color. */
    val color: Int? = null,
    val sizeSp: Int = 64,
    val align: WidgetAlign = WidgetAlign.Center,
    val bold: Boolean = false,
    /** Null follows the phone's 12/24-hour setting. */
    val use24h: Boolean? = null,
    val showSeconds: Boolean = false,
    val text: String = "",
    /** Null means none, except the search bar, which then uses the dock color. */
    val background: Int? = null,
    val cornerDp: Int = 24,
    /** Null follows the theme's label shadow setting. */
    val shadow: Boolean? = null,
    val uppercase: Boolean = false,
    /** A thin light outline around the card: with a see-through background this is the "glass" look. */
    val border: Boolean = false,
    /** The picture in a photo widget, stored in the app's private storage. */
    val image: String? = null,
    val photoShape: PhotoShape = PhotoShape.Rounded,
    /** Black-and-white photo. */
    val monochrome: Boolean = false,
    /** A widget-specific look, e.g. the battery as dots (0) or a ring (1). */
    val variant: Int = 0,
)

@Serializable
sealed class ItemContent {
    /** A launchable activity. [user] is the profile serial number (work profile apps differ). */
    @Serializable
    @SerialName("app")
    data class App(val component: String, val user: Long) : ItemContent() {
        /** Matches [app.veneer.apps.AppKey.id]. */
        val id: String get() = "$component#$user"
    }

    /** A widget provided by another app, hosted through AppWidgetHost. */
    @Serializable
    @SerialName("widget")
    data class Widget(val appWidgetId: Int, val provider: String) : ItemContent()

    /** One of Veneer's own styleable widgets. */
    @Serializable
    @SerialName("builtin")
    data class Builtin(val kind: BuiltinKind, val style: WidgetStyle = WidgetStyle()) : ItemContent()

    /** A group of apps behind one icon. Always holds at least two apps. */
    @Serializable
    @SerialName("folder")
    data class Folder(val name: String = "Folder", val apps: List<App> = emptyList(), val color: Int? = null) : ItemContent()

    /** Several widgets in one spot; swipe up and down to switch. Holds [Widget] and [Builtin] items. */
    @Serializable
    @SerialName("stack")
    data class Stack(val widgets: List<ItemContent> = emptyList()) : ItemContent()
}

@Serializable
data class HomeItem(
    val id: Long,
    /** Home page index, or [DOCK_PAGE]. */
    val page: Int,
    val x: Int,
    val y: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val content: ItemContent,
)

@Serializable
sealed class IconRef {
    @Serializable
    @SerialName("pack")
    data class Pack(val pack: String, val drawable: String) : IconRef()

    /** A picture the user picked, copied into the app's private storage. */
    @Serializable
    @SerialName("image")
    data class Image(val file: String) : IconRef()
}

/** Per-app customizations, keyed by [app.veneer.apps.AppKey.id]. */
@Serializable
data class AppOverride(
    val label: String? = null,
    val icon: IconRef? = null,
    val hidden: Boolean = false,
    /** This app's own icon shape, style and colors; null follows the rest of the icons. */
    val shape: IconShape? = null,
    val style: IconStyle? = null,
    val iconFg: Int? = null,
    val iconBg: Int? = null,
    val finish: IconFinish? = null,
    /** This app's name color on the home screen; null follows the theme. */
    val labelColor: Int? = null,
) {
    val hasOwnLook: Boolean get() = shape != null || style != null || iconFg != null || iconBg != null || finish != null
    val isEmpty: Boolean get() = label == null && icon == null && !hidden && !hasOwnLook && labelColor == null
}

/** The theme as one app's icon sees it, with that app's own shape, style and colors applied. */
fun ThemeConfig.forApp(o: AppOverride?): ThemeConfig =
    if (o == null || !o.hasOwnLook) this
    else copy(
        iconShape = o.shape ?: iconShape,
        iconStyle = o.style ?: iconStyle,
        themedFg = o.iconFg ?: themedFg,
        themedBg = o.iconBg ?: themedBg,
        iconFinish = o.finish ?: iconFinish,
    )

/** The island: a pill at the top of the screen that grows to show charging, music, notifications and alarms. */
@Serializable
data class IslandConfig(
    val enabled: Boolean = false,
    /** A small pill around the camera when nothing is happening; off hides the island until something happens. */
    val idlePill: Boolean = true,
    val charging: Boolean = true,
    val music: Boolean = true,
    val notifications: Boolean = true,
    val alarms: Boolean = true,
    val background: Int = 0xFF000000.toInt(),
    /** Text and icons. */
    val content: Int = 0xFFFFFFFF.toInt(),
    /** The bolt, the music bars and the outline; null uses the theme's accent. */
    val accent: Int? = null,
    val outline: Boolean = false,
    val widthDp: Int = 110,
    val heightDp: Int = 32,
    val offsetYDp: Int = 6,
    val offsetXDp: Int = 0,
    val popSeconds: Int = 4,
)

@Serializable
data class LauncherState(
    val theme: ThemeConfig = ThemeConfig(),
    val pageCount: Int = 1,
    val items: List<HomeItem> = emptyList(),
    val overrides: Map<String, AppOverride> = emptyMap(),
    val savedThemes: List<ThemeConfig> = emptyList(),
    val island: IslandConfig = IslandConfig(),
    val nextId: Long = 1,
    val firstRunDone: Boolean = false,
    val shuffle: WallpaperShuffle = WallpaperShuffle(),
    val weather: WeatherSettings = WeatherSettings(),
)
