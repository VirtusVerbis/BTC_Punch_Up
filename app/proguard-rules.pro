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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Project / R8 rules (release minify) ---

# Debugging: preserve line numbers and source file for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson: annotations, signatures, and app data/DTO classes used for JSON
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe
-keep class com.google.gson.** { *; }
-keep class com.vv.btcpunchup.data.** { *; }

# Retrofit: annotations and service interface
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-keep class com.vv.btcpunchup.data.CryptoApiService { *; }

# OkHttp / WebSocket: suppress warnings if consumer rules are sufficient
-dontwarn okhttp3.**
-dontwarn okio.**

# android-youtube-player: keep public API and names for WebView/IFrame and callbacks
-keep public class com.pierfrancescosoffritti.androidyoutubeplayer.** { public *; }
-keepnames class com.pierfrancescosoffritti.androidyoutubeplayer.*

# Kotlin / Compose
-keepattributes RuntimeVisibleAnnotations
-dontwarn kotlin.**
