package org.jetbrains.moonexplorer.assets

/**
 * Whether the platform renderer can transcode + bind the HD KTX2 + Basis Universal tier
 * shipped on `assets-v<N>` GH Releases. iOS = true (Filament/ktxreader pod exposes
 * `Ktx2Reader`). Android = false until a JNI wrapper is added — see ADR-0011.
 *
 * `MoonAssetLoader` consults this before kicking off the HD fetch so Android doesn't burn
 * bandwidth downloading bytes the renderer cannot consume.
 */
expect val isHdStreamingSupported: Boolean
