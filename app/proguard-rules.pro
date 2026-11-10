# ProGuard rules for Soundbox

# Data models and entities
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }

# Player and Audio Engine
-keep class com.example.player.** { *; }
-keep class com.example.util.** { *; }
-keep class com.example.ui.screens.lockscreen.** { *; }

# Room SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Moshi JSON
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-dontwarn com.squareup.moshi.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Palette
-keep class androidx.palette.graphics.** { *; }

# General
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
