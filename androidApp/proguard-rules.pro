# Wajiha release rules (R8 full mode)

# Keep crash stack traces readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization — keep serializers for our models
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.wajiha.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wajiha.**$$serializer { *; }

# Room (KMP) generated database constructors
-keep class com.wajiha.data.db.** { *; }

# Ktor uses reflection for engine discovery
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keepnames class io.ktor.client.HttpClientEngineContainer

# Koin: classes are resolved via reflection-free DSL; keep workers created
# by WorkManager reflection
-keep class com.wajiha.android.work.** { *; }

# AccessibilityService / BroadcastReceiver entry points
-keep class com.wajiha.android.monitor.GameDetectAccessibilityService { *; }
-keep class com.wajiha.android.system.BootReceiver { *; }
