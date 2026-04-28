package org.jetbrains.moonexplorer.state

import org.jetbrains.moonexplorer.domain.Vec3

/**
 * Immutable per-frame state read by both renderer hosts (Filament-on-Android
 * and Filament-on-iOS). Per ADR-0003 the renderers pull `state.value` once
 * per frame; gestures + button taps mutate via MoonViewModel.
 *
 * Defaults: camera on +Z axis at distance 5 (Moon fills a comfortable
 * portion of the screen), full-Moon-style lighting (sun behind camera).
 */
data class MoonRenderState(
    val cameraYawRad: Float = 0f,
    val cameraPitchRad: Float = 0f,
    val cameraDistance: Float = 5f,
    val sunDirection: Vec3 = Vec3.FORWARD,   // (0, 0, 1) — sub-Earth point lit
    val moonRotationRad: Float = 0f,
    val highlightedSiteId: String? = null,
    /**
     * Phase 0 spike asset-swap toggle (Phase 6, T060). 0 = primary albedo
     * (rust/green/blue/red quadrants), 1 = alt albedo (CMYW). Useful to
     * confirm the runtime texture-swap path works end-to-end before real
     * NASA assets land in `02-moon-renderer-mvp`.
     */
    val albedoVariant: Int = 0,
)
