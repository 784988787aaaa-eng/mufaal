# =====================================================================
# ProGuard / R8 Rules — SmartLedger License Studio (مُفعّل الدفتر الذكي)
# =====================================================================

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin & Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- Kotlinx Serialization (SLK1 payload, Backup envelopes, Navigation routes) ---
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.example.** { *; }

# --- Room Database (@Entity, @Dao, @Database, generated *_Impl) ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
    abstract *;
}
-keep class com.example.data.**_Impl { *; }
-dontwarn androidx.room.**

# --- Jetpack Compose & Navigation ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class * implements androidx.lifecycle.ViewModel {
    <init>(...);
}

# --- AndroidX Security Crypto (EncryptedSharedPreferences, MasterKey, Tink) ---
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# --- AndroidX BiometricPrompt ---
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# --- ZXing Core (Offline QR Code Matrix Generation) ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
