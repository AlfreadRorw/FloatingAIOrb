# ALF Vision Panel uses reflection only for optional Shizuku detection.
# Keep service entry points and Room entities stable for future obfuscation builds.
-keep class com.alfread.alfvision.service.** { *; }
-keep class com.alfread.alfvision.data.local.** { *; }
