package app.veneer.data

/** How each of Veneer's widgets looks when first added. */
object WidgetDefaults {
    fun style(kind: BuiltinKind): WidgetStyle = when (kind) {
        BuiltinKind.Clock -> WidgetStyle(sizeSp = 84)
        BuiltinKind.ClockDate -> WidgetStyle(sizeSp = 72)
        BuiltinKind.Date -> WidgetStyle(sizeSp = 24)
        BuiltinKind.Greeting -> WidgetStyle(sizeSp = 28, align = WidgetAlign.Start)
        BuiltinKind.Text -> WidgetStyle(sizeSp = 22, text = "Make it yours.")
        BuiltinKind.Search -> WidgetStyle(sizeSp = 16, cornerDp = 28, shadow = false)
        BuiltinKind.Battery -> WidgetStyle(sizeSp = 28)
        BuiltinKind.Media -> WidgetStyle(sizeSp = 16, cornerDp = 28, shadow = false, align = WidgetAlign.Start)
        BuiltinKind.Agenda -> WidgetStyle(sizeSp = 16, cornerDp = 28, shadow = false, align = WidgetAlign.Start)
        BuiltinKind.Photo -> WidgetStyle(cornerDp = 28)
        BuiltinKind.Weather -> WidgetStyle(sizeSp = 30)
        BuiltinKind.MonthCalendar -> WidgetStyle(sizeSp = 14)
        BuiltinKind.AnalogClock -> WidgetStyle()
        BuiltinKind.FlipClock -> WidgetStyle(sizeSp = 64, bold = true)
        BuiltinKind.Glance -> WidgetStyle(sizeSp = 22, align = WidgetAlign.Start)
    }
}
