# ── Stack traces ──────────────────────────────────────────────────────────────
# Keep file names & line numbers so crash reports are readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ────────────────────────────────────────────────────────────────────
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ── Android Bluetooth (BLE) ──────────────────────────────────────────────────
-keep class android.bluetooth.** { *; }

# ── Jetpack Compose ──────────────────────────────────────────────────────────
# R8 full-mode needs a hint for Compose lambdas / State objects.
-dontwarn androidx.compose.**

# ── AndroidX Lifecycle ───────────────────────────────────────────────────────
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**
