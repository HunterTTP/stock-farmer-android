# libGDX: the native libraries call back into these classes by name and field through JNI,
# and the Android backend instantiates input/audio handlers reflectively.
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-dontwarn com.badlogic.gdx.utils.GdxBuild
-dontwarn com.badlogic.gdx.jnigen.**
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.badlogic.gdx.backends.android.** { *; }
-keep class com.badlogic.gdx.graphics.g2d.Gdx2DPixmap { *; }
-keep class com.badlogic.gdx.math.Matrix4 { *; }
-keep class com.badlogic.gdx.utils.BufferUtils { *; }
-keep class com.badlogic.gdx.utils.SharedLibraryLoader { *; }

# kotlinx.serialization: generated serializers are found through companion lookups at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Room builds WorkManager's database by reflection. Room's own rule keeps the class but, under R8 full mode, not its
# constructor, which crashes the release build at launch.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
