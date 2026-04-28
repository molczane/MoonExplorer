# ADR-0006: Selenographic coordinate convention

**Status**: Accepted
**Date**: 2026-04-28
**Supersedes**: —

## Context

Site markers, camera targeting, sun direction, and equirectangular texture mapping all need a consistent rule for converting selenographic (lunar) latitude/longitude to a 3D position on a unit sphere. Multiple conventions exist (Y-up vs. Z-up; east-positive vs. west-positive; prime meridian at +X vs. +Z).

Picking once and documenting it prevents bugs where, e.g., Apollo 11 lands at the wrong spot relative to the texture, or the camera flies "behind" the Moon when targeting +90° east.

## Decision

- **Coordinate system**: right-handed, Y-up.
- **North pole** at `+Y`.
- **Prime meridian** (selenographic lat=0, lon=0; the sub-Earth point) at `+Z`.
- **East longitude** (toward Mare Crisium) increases toward `+X`.
- **Internal angle units**: radians.
- **API boundary units**: degrees (e.g., `MoonSite(lat: Double, lon: Double)` is in degrees; `setSunDirection(latDeg, lonDeg)`).

### Conversion

```kotlin
private const val DEG = (PI / 180.0).toFloat()

fun latLonToCartesian(latDeg: Float, lonDeg: Float): Vec3 {
    val lat = latDeg * DEG; val lon = lonDeg * DEG
    val cl = cos(lat)
    return Vec3(cl * sin(lon), sin(lat), cl * cos(lon))
}

fun cartesianToLatLon(p: Vec3): LatLon {
    val n = normalize(p)
    val lat = asin(n.y.coerceIn(-1f, 1f))
    val lon = atan2(n.x, n.z)            // east-positive, range (-PI, PI]
    return LatLon(lat / DEG, lon / DEG)
}
```

### Sanity check

Apollo 11 (0.6741°N, 23.4733°E) → `(0.398, 0.012, 0.917)`, magnitude 1.0000.

### Texture mapping

The albedo texture is equirectangular, lat/lon directly:
- `u = lon / 360 + 0.5` (so lon=0 maps to u=0.5; lon=+180° → u=1.0; lon=-180° → u=0)
- `v = 0.5 - lat / 180` (so lat=+90° → v=0 top; lat=-90° → v=1 bottom)

The UV sphere mesh generated procedurally must respect this `(u, v)` mapping so the texture wraps correctly.

## Rationale

- **Right-handed Y-up matches Filament's world space** — no extra transform on either platform.
- **Camera initially placed at `+Z`** sees the familiar near-side, with Mare Crisium drifting right as longitude increases. This is the most intuitive starting view for users who know the Moon visually.
- **East-positive longitude** is the IAU/USGS convention for the Moon since 2007 ([USGS lunar coordinate systems note](https://astrogeology.usgs.gov/groups/iau-wgccre)). Older sources use west-positive; we standardize on the modern convention.
- **Degrees at API boundary, radians inside**: shields callers from radian arithmetic; standard practice.

## Alternatives rejected

- **Z-up** (some game engines): doesn't match Filament; would require an extra transform.
- **West-positive longitude**: contradicts modern IAU convention; would surprise anyone reading lat/lon from a recent NASA/USGS map.
- **Prime meridian at `+X`**: would put the camera looking at the limb instead of the near-side at startup. Unintuitive default.
- **Radians at API boundary**: unfriendly to humans entering site coordinates (every site's lat/lon would need pre-conversion).

## Consequences

- All math in `commonMain` uses this convention. UV sphere mesh generation, marker positioning, camera targeting, sun-direction computation all follow.
- Site catalog (`MoonSite(lat, lon)`) stores degrees with east-positive longitude.
- Renderer must respect the texture UV mapping above. Materials that sample textures must use the matching `(u, v)` formula.
- Any third-party library that uses Z-up or west-positive must be wrapped at the boundary; never let conflicting conventions leak into shared code.

## References

- `ai-docs/research/selenographic-math-camera.md`
- [Wikipedia — Selenographic coordinate system](https://en.wikipedia.org/wiki/Selenographic_coordinate_system)
- [USGS / IAU coordinate systems](https://astrogeology.usgs.gov/groups/iau-wgccre)
