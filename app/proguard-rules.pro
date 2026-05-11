# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name.
-renamesourcefileattribute SourceFile

# --- Gson ---
# Keep Gson's internal type-resolution machinery
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Keep JsonElement and all its concrete subclasses (JsonObject, JsonArray, JsonPrimitive, JsonNull)
# R8 otherwise strips the abstract class, causing "Abstract classes can't be instantiated" at runtime
-keep class com.google.gson.JsonElement { *; }
-keep class com.google.gson.JsonObject { *; }
-keep class com.google.gson.JsonArray { *; }
-keep class com.google.gson.JsonPrimitive { *; }
-keep class com.google.gson.JsonNull { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.internal.** { *; }

# Keep all DTO data classes used with Gson serialization/deserialization
-keep class com.samwise.unshelved.core.network.**Dto { *; }
-keep class com.samwise.unshelved.core.network.**Dto$* { *; }
-keep class com.samwise.unshelved.core.network.**Request { *; }
-keep class com.samwise.unshelved.core.network.**Response { *; }
-keep class com.samwise.unshelved.core.network.**Result { *; }

# Keep field names on all network DTOs so Gson can map JSON keys
-keepclassmembers class com.samwise.unshelved.core.network.** {
    <fields>;
}
-keepclassmembers class com.samwise.unshelved.feature.auth.** {
    <fields>;
}

# --- Media3 session (Android Auto) ---
-keep class androidx.media3.session.** { *; }

# --- Cast SDK ---
-keep class com.samwise.unshelved.service.CastOptionsProvider { *; }