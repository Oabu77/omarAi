# Retrofit service methods are invoked through reflection.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep interface com.darcloud.omarai.data.api.OmarApi { *; }
-keep class com.darcloud.omarai.data.api.** { *; }

# Moshi reflects over the small API contract models in this first release.
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.* <methods>;
}
