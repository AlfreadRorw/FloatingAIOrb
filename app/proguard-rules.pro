# ALF Voice Control proguard rules

# Keep Room generated classes
-keep class com.alfread.alfvoicecontrol.data.** { *; }

# Keep SpeechRecognizer related classes
-keep class android.speech.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
