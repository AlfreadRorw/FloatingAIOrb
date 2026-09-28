# ARACHNE

ARACHNE is an offline Android kinetic clock with a native, realtime and customizable home-screen widget.

## Widget
- Realtime `TextClock` ticking on the launcher, including seconds.
- Per-widget configuration: each widget can look different.
- Themes: Gold, Violet, Cyan, Crimson, Emerald, Ice.
- Custom HEX accent color.
- Styles: Arachne, Minimal, Terminal HUD.
- 12/24-hour mode.
- Toggle seconds, date, label and cinematic dim mode.
- Resizable horizontally and vertically.
- Tapping the widget opens the ARACHNE app.

The widget uses Android's native `TextClock`, so the displayed time does not depend on a broadcast every second and remains realtime while the launcher is showing it.


### Latest polish
- Native system-bar inset handling so the main UI stays below the status bar / cutout and above the navigation bar on Android 15+.
- Animated native launch screen with ARACHNE logo, glow, rotation and smooth handoff into the WebView.
- Custom ARACHNE app icon with spider-clock motif.
- Web UI now fades/slides into place instead of appearing abruptly.
