package org.jetbrains.moonexplorer.assets

/**
 * Per-platform Files-dir-backed cache. Atomic writes via temp + rename so a crash
 * mid-write never leaves a half-written file the renderer would try to decode. T111.
 *
 * Each actual constructs its own root: Android via `Context.filesDir`, iOS via
 * `NSFileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)`. Construction
 * happens in platform-specific code (the Compose host wires `StorageDir` into
 * `MoonAssetLoader` in T117) — the expect class deliberately doesn't constrain
 * constructor shape.
 */
expect class StorageDir {
    suspend fun read(name: String): ByteArray?
    suspend fun writeAtomically(name: String, bytes: ByteArray)
    fun exists(name: String): Boolean
    fun delete(name: String)
}
