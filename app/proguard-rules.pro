# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.faforever.mobile.**$$serializer { *; }
-keepclassmembers class com.faforever.mobile.** { *** Companion; }
-keepclasseswithmembers class com.faforever.mobile.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# AppAuth
-keep class net.openid.appauth.** { *; }
