# ARACHNE widget build fix

Fixed `ClockWidgetProvider.java`.

The previous code called `RemoteViews.setImageViewTintList(...)`, which is not a
public method available to the compiler for this project. The widget now uses
`RemoteViews.setColorStateList(R.id.widgetRing, "setImageTintList", ...)` on
Android 12 / API 31+.

This keeps the realtime TextClock behavior and custom widget colors while
allowing the project to compile with compileSdk 35.

Reference:
https://developer.android.com/reference/android/widget/RemoteViews
