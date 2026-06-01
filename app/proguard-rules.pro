# Add project specific ProGuard rules here.

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Coil
-keep class coil.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Keep data models
-keep class com.ryzix.player.model.** { *; }
-keep class com.ryzix.player.db.** { *; }
