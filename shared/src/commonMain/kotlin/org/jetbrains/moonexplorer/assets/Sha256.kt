package org.jetbrains.moonexplorer.assets

/**
 * Hex-encoded SHA-256 of [bytes]. T112.
 *
 * Used to verify cached HD asset files against the manifest hash before binding them
 * to Filament (FR-004) — a corrupt/tampered cache should not surface to the renderer.
 */
expect fun sha256(bytes: ByteArray): String
