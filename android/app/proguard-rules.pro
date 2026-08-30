# Retrofit service methods are invoked through reflection.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep interface com.darcloud.omarai.data.api.OmarApi { *; }
-keep class com.darcloud.omarai.data.api.** { *; }

# KotlinJsonAdapterFactory reflects over these API contract classes in release builds.
# Keep class names, constructors, fields, Kotlin metadata, and annotations intact.
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.* <methods>;
}
