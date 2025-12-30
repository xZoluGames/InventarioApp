# Add project specific ProGuard rules here.

# Keep Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Room entities
-keep class com.inventario.py.data.local.entities.** { *; }

# Keep data classes for serialization
-keep class com.inventario.py.data.remote.dto.** { *; }
-keep class com.inventario.py.domain.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Apache POI
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
