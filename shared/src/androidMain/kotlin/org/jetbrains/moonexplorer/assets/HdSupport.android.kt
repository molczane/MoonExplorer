package org.jetbrains.moonexplorer.assets

// Filament 1.71.x's filament-utils-android publishes only KTX1Loader; Ktx2Reader is not
// exposed to Java/Kotlin. ADR-0011 defers the JNI wrapper to a future spec.
actual val isHdStreamingSupported: Boolean = false
