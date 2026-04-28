# Moon Globe — Math & Interaction Reference

> Research output. Source: agent run 2026-04-28. Conventions: right-handed, Y-up, radians internal. `Vec3 = (x,y,z)`. Unit Moon radius. Filament uses RH Y-up, matching this. Source on selenographic system: https://en.wikipedia.org/wiki/Selenographic_coordinate_system

---

## 1. Selenographic → Cartesian

**Adopted convention.** North pole at `+Y`. Prime meridian (sub-Earth point, lat=0, lon=0) at `+Z`. East longitude (toward Mare Crisium) increases toward `+X`. This is the standard astronomical mapping `(lon, lat) → (sin·cos, sin, cos·cos)` with east-positive; it matches how a viewer initially placed at `+Z` sees the familiar near-side, with Mare Crisium drifting right as longitude increases.

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

**Worked example — Apollo 11 (0.6741°N, 23.4733°E):**
- `cos(lat) ≈ 0.999931`, `sin(lat) ≈ 0.011767`
- `sin(lon) ≈ 0.398125`, `cos(lon) ≈ 0.917325`
- Result: `(0.398098, 0.011767, 0.917262)`. `|v| = 1.0000` (sanity check).

---

## 2. Orbit Camera

**Recommendation: explicit `(yaw, pitch, distance)` + `lookAt`.** Quaternion+slerp is mathematically nicer but for a single-target orbit camera the Euler form is simpler, debuggable, and lets you clamp pitch to dodge the pole singularity outright. Gimbal lock isn't a real risk because we never compose orientations — we just compute a position and call `lookAt` each frame. (Judgment call.)

Define `yaw` as rotation around `+Y` (longitude-like; 0 = camera on `+Z` axis), `pitch` as elevation above the equatorial plane, `distance` from Moon center.

```kotlin
fun cameraPosition(yaw: Float, pitch: Float, distance: Float): Vec3 {
    val cp = cos(pitch)
    return Vec3(
        distance * cp * sin(yaw),
        distance * sin(pitch),
        distance * cp * cos(yaw),
    )
}

private const val PITCH_LIMIT = (PI / 2 - 0.01).toFloat()   // ~89.4°

fun cameraTransform(yaw: Float, pitch: Float, distance: Float): Mat4 {
    val p = cameraPosition(yaw, pitch.coerceIn(-PITCH_LIMIT, PITCH_LIMIT), distance)
    // Stable up: standard +Y, except when nearly looking straight up/down,
    // bias up away from the view direction to keep the cross product non-degenerate.
    val up = if (abs(pitch) > PITCH_LIMIT - 0.05f) Vec3(0f, 0f, if (pitch > 0) -1f else 1f)
             else Vec3(0f, 1f, 0f)
    return lookAt(eye = p, center = Vec3.ZERO, up = up)
}
```

The pitch clamp (≈89.4°) avoids `up ≈ ±view` blowing up `lookAt`'s cross product. The conditional `up` is belt-and-braces; with the clamp, plain `+Y` works.

---

## 3. Pinch Zoom — Exponential

Linear `distance += k·(scale - 1)` feels jerky: the same finger gesture produces a huge angular change in object size when zoomed in close, and barely moves the camera when far. Exponential mapping gives **constant proportional change per pinch unit**, matching how humans perceive scale (Weber–Fechner).

`distance' = distance · exp(−s)` where `s` is the log-scale of the pinch. With `scale = finalDist/initialDist` from the gesture recognizer, `s = ln(scale)`, so:

```kotlin
private const val MIN_DIST = 1.5f      // ~0.5 radius above surface
private const val MAX_DIST = 20.0f     // judgment: comfortable max framing

fun onPinch(scale: Float) {
    // scale > 1 = fingers spread = zoom in = smaller distance
    state.distance = (state.distance / scale).coerceIn(MIN_DIST, MAX_DIST)
}
```

Equivalent to `distance *= exp(-ln(scale))`. For incremental deltas use `distance *= exp(-dz)` where `dz` is the per-frame log-zoom delta.

---

## 4. Drag-to-Rotate, Zoom-Aware

Goal: at close zoom, dragging 1 px should move the surface point under the finger by ≈1 px (direct manipulation). At far zoom, rotating the whole globe faster feels right because the visible angular extent of the Moon is small.

**Geometric derivation.** With vertical FOV `fovY` and viewport height `H_px`, the radians-per-pixel at the focal plane (distance `d` from origin, sphere radius `1`) is:

```
radPerPixelAtSurface ≈ 2 · tan(fovY/2) · (d − 1) / H_px
```

That's the "drag the surface" rate: each pixel maps to that many radians of yaw/pitch around the Moon center such that the projected surface point moves with the finger. It naturally scales: `d → 1` makes drags tiny (you're zoomed in), `d → 20` makes them large.

```kotlin
fun onDrag(dxPx: Float, dyPx: Float, viewportH: Int, fovY: Float) {
    val k = 2f * tan(fovY * 0.5f) * (state.distance - 1f) / viewportH
    state.yaw   -= dxPx * k                       // drag right = world rotates left
    state.pitch = (state.pitch + dyPx * k).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
}
```

Sign of `dy` depends on whether the platform reports y-down (Android/iOS touch: yes) — invert if needed so dragging up tilts the north pole into view. For an even more "1:1 surface" feel you can do unprojected ray-on-sphere math (arcball), but the linear approximation above is indistinguishable for typical drag distances and far cheaper.

---

## 5. Fly-To

**Targeting.** To frame lat/lon dead-centre, place the camera on the ray from origin through the site at `targetDistance`. From `latLonToCartesian` we get the surface point `s`; convert back to camera angles:

```kotlin
fun siteToOrbit(latDeg: Float, lonDeg: Float, dist: Float): Orbit {
    val s = latLonToCartesian(latDeg, lonDeg)
    val yaw   = atan2(s.x, s.z)
    val pitch = asin(s.y)
    return Orbit(yaw, pitch.coerceIn(-PITCH_LIMIT, PITCH_LIMIT), dist)
}
```

(The camera at `(yaw, pitch, d)` from §2 sits on that same ray scaled by `d`, so it looks straight at `s`.)

**Interpolation.** Three different curves because the three quantities are perceptually different:

- **yaw**: shortest-arc, ease-in-out (cubic). Wrap so `|Δyaw| ≤ π`.
- **pitch**: ease-in-out (cubic) — already bounded, no wrap.
- **distance**: exponential (lerp in log-space) so a 20→2 fly-to doesn't crash through the surface near `t=0.7`.

Default duration **1.2 s**, ease-in-out cubic: `e(t) = if (t<0.5) 4t³ else 1 - (-2t+2)³/2`.

```kotlin
fun shortestArcDelta(from: Float, to: Float): Float {
    var d = (to - from) % (2f * PI.toFloat())
    if (d >  PI) d -= 2f * PI.toFloat()
    if (d < -PI) d += 2f * PI.toFloat()
    return d
}

class FlyTo(val from: Orbit, val to: Orbit, val durationMs: Long = 1200) {
    private val dYaw = shortestArcDelta(from.yaw, to.yaw)
    private val logFrom = ln(from.distance); private val logTo = ln(to.distance)

    fun sample(tMs: Long): Orbit {
        val u  = (tMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val e  = if (u < 0.5f) 4f*u*u*u else 1f - (-2f*u + 2f).pow(3) / 2f
        return Orbit(
            yaw      = from.yaw + dYaw * e,
            pitch    = from.pitch + (to.pitch - from.pitch) * e,
            distance = exp(logFrom + (logTo - logFrom) * e),
        )
    }
}
```

Quaternion-slerp variant: build `qFrom`, `qTo` from `(yaw, pitch, 0)` Euler angles, slerp by `e`, decompose back. Cleaner if you ever want banking, but here the Euler form gives identical visuals at lower cost. (Judgment call.)

---

## 6. Sun Direction

Sun is a directional light: only the unit vector matters, not position.

**(a) 2D joystick → hemisphere.** Map `(x, y) ∈ [-1,1]²` to a unit vector on the camera-facing hemisphere:

```kotlin
fun joystickToSunDir(x: Float, y: Float): Vec3 {
    val r2 = x*x + y*y
    val z  = sqrt(max(0f, 1f - r2))     // hemisphere bulge toward camera
    return normalize(Vec3(x, y, z))
}
```

Why the hemisphere: clamping inside the unit disk then lifting to the sphere gives intuitive "drag light around" behaviour. Outside the disk (`r2 > 1`), `z=0` keeps the light grazing — equivalent to a terminator-on-meridian preset. The vector is in **camera/screen space**; transform by `inverse(viewMatrix)` upper-3×3 to get world-space direction.

**(b) Selenographic sun lat/lon.** Sun's sub-solar point on the Moon also has a (lat, lon) in selenographic coordinates — this is exactly the latitude/longitude where the sun is at zenith. Direction *from* Moon *to* sun in our world frame:

```kotlin
fun sunDirFromSelenographic(latDeg: Float, lonDeg: Float): Vec3 =
    latLonToCartesian(latDeg, lonDeg)   // already unit-length, points outward
```

The renderer uses the negation as the directional-light direction (light travels *toward* the surface).

**Phase angle.** Sun–Moon–Earth angle `φ`. Earth observer sees the near-side (around `+Z`); the sub-solar longitude relative to the sub-Earth point ≈ `φ` (with sign depending on waxing vs waning). https://en.wikipedia.org/wiki/Lunar_phase

Concrete presets (sub-solar lat ≈ 0° — the Moon's axial tilt vs ecliptic is small ~1.54°):

| Preset       | φ      | Sun lat/lon (selenographic)    | Joystick `(x,y)` |
| ------------ | ------ | ------------------------------ | ---------------- |
| Full         | 0°     | (0°, 0°)   — sun behind viewer | (0, 0)           |
| Waxing half  | 90°    | (0°, +90°) — lit east limb     | (1, 0)           |
| Waning half  | 90°    | (0°, −90°)                     | (−1, 0)          |
| Waxing cresc | 135°   | (0°, +135°)                    | (~0.71, 0), z<0  |
| New          | 180°   | (0°, 180°) — far side lit      | n/a hemisphere   |

The joystick column is the camera-space approximation when the camera sits at `(0, 0, +d)`; for off-axis camera positions, prefer mode (b).

---

## 7. Markers — World Position, Visibility, Limb Fade

Site `i` lives at fixed `(latᵢ, lonᵢ)` → world-space `pᵢ = latLonToCartesian(latᵢ, lonᵢ)` (constant per site, cache once).

**Visibility test.** Marker is on the visible hemisphere iff the vector from the marker to the camera has positive component along the marker's surface normal — equivalently, the marker is on the camera's side of the Moon center plane:

```kotlin
fun markerFacing(p: Vec3, camera: Vec3): Float {
    val viewDir = normalize(camera)        // Moon center is origin, so this is camera-from-center
    return dot(normalize(p), viewDir)      // > 0 = near-side, < 0 = far-side
}
```

(`viewDir` = `normalize(camera - origin)` = `normalize(camera)`; for a finite-radius unit sphere this is the correct dot-product test as long as `|camera| > 1`, which the zoom clamp guarantees.)

**Limb fade.** Smoothstep across the silhouette so markers don't pop:

```kotlin
fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

fun markerAlpha(p: Vec3, camera: Vec3): Float {
    val d = markerFacing(p, camera)
    return smoothstep(-0.1f, 0.2f, d)     // fully hidden a hair past limb, full opacity at d≥0.2
}
```

The `-0.1, 0.2` window is a judgment call: tuning it tighter (e.g., `0.0, 0.15`) makes markers cling to the limb; wider (`-0.2, 0.3`) gives a softer reveal. Pair with depth-test off and slight outward offset (`p * 1.001`) so the billboard never z-fights the sphere.

For screen-space layout (label collision, off-screen indicators), do the projection on the renderer side using Filament's `Camera.getProjectionMatrix() · viewMatrix · vec4(p, 1)`; the tests above are sufficient to decide *whether* to project at all.
