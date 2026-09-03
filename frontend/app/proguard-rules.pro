# PDFBox-Android
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.apache.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.aipdfreader.app.**$$serializer { *; }
-keepclassmembers class com.aipdfreader.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.aipdfreader.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
