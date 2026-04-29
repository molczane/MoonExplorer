package org.jetbrains.moonexplorer.assets

/**
 * Per-platform Files-dir-backed cache. Atomic writes via temp + rename so a crash mid-write
 * never leaves a half-written file the renderer would try to decode. T111 / refactored in
 * Phase Final to be an interface so commonTest can supply a fake.
 *
 * Production implementations:
 *   - [org.jetbrains.moonexplorer.assets.AndroidStorageDir] backed by `Context.filesDir`
 *   - [org.jetbrains.moonexplorer.assets.IosStorageDir] backed by `NSFileManager` documentDirectory
 *
 * Construction is platform-specific (Android needs `Context`; iOS doesn't), so the entry
 * points (`MainActivity` on Android, `MainViewController` on iOS) instantiate the right impl
 * and pass it through `App(storage = …)`.
 */
interface StorageDir {
    suspend fun read(name: String): ByteArray?
    suspend fun writeAtomically(name: String, bytes: ByteArray)
    fun exists(name: String): Boolean
    fun delete(name: String)
}
