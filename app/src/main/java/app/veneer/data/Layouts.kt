package app.veneer.data

/** One widget's spot in a ready-made layout, on the standard 4 × 6 grid. */
data class Placement(val x: Int, val y: Int, val spanX: Int, val spanY: Int, val content: ItemContent.Builtin)

/** A ready-made home screen arrangement, modeled on setups people share online. */
data class LayoutTemplate(val name: String, val description: String, val items: List<Placement>)

object Layouts {

    private fun w(kind: BuiltinKind, tweak: (WidgetStyle) -> WidgetStyle = { it }) =
        ItemContent.Builtin(kind, tweak(WidgetDefaults.style(kind)))

    private val glass: (WidgetStyle) -> WidgetStyle = { it.copy(background = 0x26FFFFFF, border = true, cornerDp = 28) }

    val all: List<LayoutTemplate> = listOf(
        LayoutTemplate(
            "Hero photo",
            "One big photo, with the weather and this month below it",
            listOf(
                Placement(0, 0, 4, 3, w(BuiltinKind.Photo) { it.copy(cornerDp = 32) }),
                Placement(0, 3, 2, 2, w(BuiltinKind.Weather, glass)),
                Placement(2, 3, 2, 2, w(BuiltinKind.MonthCalendar, glass)),
                Placement(0, 5, 4, 1, w(BuiltinKind.Search)),
            ),
        ),
        LayoutTemplate(
            "Big clock",
            "A greeting and a large clock, with weather, battery and music",
            listOf(
                Placement(0, 0, 4, 1, w(BuiltinKind.Greeting)),
                Placement(0, 1, 4, 2, w(BuiltinKind.Clock) { it.copy(sizeSp = 110) }),
                Placement(0, 3, 2, 1, w(BuiltinKind.Weather)),
                Placement(2, 3, 2, 1, w(BuiltinKind.Battery)),
                Placement(0, 5, 4, 1, w(BuiltinKind.Media)),
            ),
        ),
        LayoutTemplate(
            "Glass",
            "A flip clock and the weather on frosted glass, with the wallpaper showing",
            listOf(
                Placement(0, 0, 4, 2, w(BuiltinKind.FlipClock, glass)),
                Placement(0, 2, 4, 1, w(BuiltinKind.Weather, glass)),
            ),
        ),
        LayoutTemplate(
            "At a glance",
            "Today, your next events and music, with room for apps",
            listOf(
                Placement(0, 0, 4, 1, w(BuiltinKind.Glance)),
                Placement(0, 1, 4, 2, w(BuiltinKind.Agenda)),
                Placement(0, 5, 4, 1, w(BuiltinKind.Media)),
            ),
        ),
        LayoutTemplate(
            "Hello",
            "A HELLO! clock with battery dots and a photo pill, like the popular Galaxy setups",
            listOf(
                Placement(0, 0, 2, 2, w(BuiltinKind.ClockDate) { it.copy(text = "HELLO!", sizeSp = 52, align = WidgetAlign.Start) }),
                Placement(2, 0, 2, 2, w(BuiltinKind.Battery) { it.copy(sizeSp = 40, background = 0x33000000, cornerDp = 28) }),
                Placement(0, 2, 2, 1, w(BuiltinKind.Photo) { it.copy(photoShape = PhotoShape.Pill) }),
                Placement(2, 2, 2, 1, w(BuiltinKind.Text) { it.copy(text = "Good day", sizeSp = 20, align = WidgetAlign.Start) }),
            ),
        ),
        LayoutTemplate(
            "Classic",
            "An analog clock beside the month, and the day at a glance",
            listOf(
                Placement(0, 0, 2, 2, w(BuiltinKind.AnalogClock)),
                Placement(2, 0, 2, 2, w(BuiltinKind.MonthCalendar)),
                Placement(0, 2, 4, 1, w(BuiltinKind.Glance)),
            ),
        ),
    )
}
