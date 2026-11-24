# Keep Kotlin Serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.cinematv.app.data.remote.**$$serializer { *; }
-keepclassmembers class com.cinematv.app.data.remote.** {
    *** Companion;
}
# Keep Media3 ExoPlayer classes
-keep class androidx.media3.** { *; }
