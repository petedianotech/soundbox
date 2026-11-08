# Soundbox release shrinking rules
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Kotlin / coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep class **JsonAdapter { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
}
-keep class ** extends com.squareup.moshi.JsonAdapter

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# App models used by Room / reflection
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }

# Playback / service entry points
-keep class com.example.player.** { *; }
-keep class com.example.widget.** { *; }

# JNI (if native engine is linked later)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Compose reflective lookups
-dontwarn androidx.compose.**
