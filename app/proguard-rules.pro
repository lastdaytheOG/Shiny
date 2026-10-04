# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# WEB_REMIX Streaming - WebView JavaScript interfaces
-keepclassmembers class com.shiny.music.utils.sabr.EjsNTransformSolver$SolverWebView {
    @android.webkit.JavascriptInterface public *;
}
-keepclassmembers class com.shiny.music.utils.cipher.CipherWebView {
    @android.webkit.JavascriptInterface public *;
}
-keepclassmembers class com.shiny.music.utils.potoken.PoTokenWebView {
    @android.webkit.JavascriptInterface public *;
}

# Keep streaming utility classes
-keep class com.shiny.music.utils.cipher.** { *; }
-keep class com.shiny.music.utils.sabr.** { *; }
-keep class com.shiny.music.utils.potoken.** { *; }

# Keep coroutine continuation for WebView callbacks
-keepclassmembers class * {
    void resume(...);
    void resumeWithException(...);
}

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

## Kotlin Serialization
# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclasseswithmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclasseswithmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclasseswithmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn javax.servlet.ServletContainerInitializer
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.slf4j.impl.StaticLoggerBinder

## Rules for NewPipeExtractor
-keep class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

## Logging (does not affect Timber)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    ## Leave in release builds
    #public static int i(...);
    #public static int w(...);
    #public static int e(...);
}

# Generated automatically by the Android Gradle plugin.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Keep all classes within the kuromoji package
-keep class com.atilika.kuromoji.** { *; }

## Queue Persistence Rules
# Keep queue-related classes to prevent serialization issues in release builds
-keep class com.shiny.music.models.PersistQueue { *; }
-keep class com.shiny.music.models.PersistPlayerState { *; }
-keep class com.shiny.music.models.QueueData { *; }
-keep class com.shiny.music.models.QueueType { *; }
-keep class com.shiny.music.playback.queues.** { *; }

# Keep serialization methods for queue persistence
-keepclassmembers class * implements java.io.Serializable {
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

## Firebase component registrars
# firebase-components 18.0.0 keeps registrar classes but not their constructors. AGP 9 runs R8 in
# full mode, where a class-only -keep no longer keeps <init>(), so ComponentDiscovery logged
# "Could not instantiate ...Registrar" and Crashlytics never started in release builds.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

## Credential Manager (Sign in with Google): the Play services provider is found by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Vibra fingerprint library
-keep class com.shiny.music.recognition.VibraSignature { *; }
-keepclassmembers class com.shiny.music.recognition.VibraSignature {
    native <methods>;
}

## Kotlin Reflection Fix
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

## Ktor Serialization
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

## Shazam Models
-keep class com.music.shazamkit.models.** { *; }
-keepclassmembers class com.music.shazamkit.models.** {
    *;
}

## Kotlinx Serialization
-keepattributes *Annotation*
-keepclassmembers class com.music.shazamkit.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.music.shazamkit.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

## Listen Together (Shiny Together) wire models
-keepclassmembers class com.shiny.music.together.** {
    *** Companion;
}
-keepclasseswithmembers class com.shiny.music.together.** {
    kotlinx.serialization.KSerializer serializer(...);
}
