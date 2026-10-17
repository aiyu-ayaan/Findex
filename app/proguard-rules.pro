# Proguard rules for Findex

# Kotlin & Serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <fields>;
}
-keep @kotlinx.serialization.Serializable class com.aiyu.findex.ui.navigation.** { *; }
-keep @kotlinx.serialization.Serializable class com.aiyu.findex.core.model.** { *; }

# 7-Zip-JBinding (Native JNI bindings)
-keep class net.sf.sevenzipjbinding.** { *; }
-keep interface net.sf.sevenzipjbinding.** { *; }
-keepclassmembers class net.sf.sevenzipjbinding.** {
    native <methods>;
    <fields>;
    <init>(...);
}
-dontwarn net.sf.sevenzipjbinding.**

# Apache Commons Compress & XZ
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**

# Zip4j
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# Room Database & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep class com.aiyu.findex.core.db.** { *; }
-dontwarn androidx.room.paging.**

# Coil Image Loader
-keep class coil3.** { *; }
-dontwarn coil3.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Android Components & Hilt
-keep class * extends androidx.lifecycle.ViewModel
-keep class * extends android.app.Service
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
