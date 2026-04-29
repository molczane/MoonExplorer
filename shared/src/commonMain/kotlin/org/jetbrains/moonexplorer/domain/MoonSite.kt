package org.jetbrains.moonexplorer.domain

import kotlinx.serialization.Serializable

/**
 * A named Moon location bundled in `composeResources/files/sites.json`. Loaded once at app
 * startup by [SiteCatalog]. T202 / 01-app-shell.
 *
 * Extends ADR-0005's `MoonSite(id, name, lat, lon, tags)` shape with `subtitle?`, `type`,
 * and `description` for the curated catalog. The trade-off is documented in
 * `01-app-shell/plan.md` § "MoonSite": typed buckets beat freeform tags for our 16-entry
 * roster, and Koog tools (Phase 3) can ignore the extra fields via `ignoreUnknownKeys`.
 *
 * Coordinate convention per ADR-0006: latitude positive north, longitude positive east of
 * the prime meridian (the Moon's Earth-facing meridian).
 */
@Serializable
data class MoonSite(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val lat: Double,
    val lon: Double,
    val type: SiteType,
    val description: String,
)

@Serializable
enum class SiteType {
    /** Mare or oceanus — basaltic lava plain. */
    MARE,

    /** Impact crater. */
    CRATER,

    /** Crewed or robotic landing site. */
    LANDING_SITE,

    /** Anything else (basins, mountains, rilles, etc.). */
    OTHER,
}
