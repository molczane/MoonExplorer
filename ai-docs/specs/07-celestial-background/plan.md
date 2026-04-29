# Implementation Plan: 07 — Celestial Background

**Branch:** `07-celestial-background`
**Created:** 2026-04-29
**Status:** Draft (pending user ratification)

## Architecture flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  UI (Compose, commonMain)                                           │
│  • SettingsSheet (existing — placeholder from 01-app-shell)         │
│      + "Show stars"  Switch  → viewModel.setShowStars(Boolean)      │
│      + "Show sun"    Switch  → viewModel.setShowSun(Boolean)        │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ MoonViewModel._state.update { … }
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  State (commonMain)                                                 │
│  • MoonRenderState                                                  │
│      + showStars: Boolean = true   (NEW)                            │
│      + showSun:   Boolean = true   (NEW)                            │
│      (existing fields untouched: cameraYaw/Pitch/Distance,          │
│       sunDirection, highlightedSiteId, textureSet, …)               │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ Per-frame state pull (ADR-0003)
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Renderer hosts (Android Kotlin/JNI + iOS Obj-C++) — DUPLICATED     │
│  On init:                                                           │
│   • Build cubemap Texture from 6 PNGs → Skybox                      │
│   • Build sun Renderable: 1×1 quad + sun.filamat MaterialInstance   │
│   • Configure View bloom (initially disabled until showSun=true)    │
│  Per frame:                                                         │
│   • if state.showStars changed: scene.setSkybox(skybox / null)      │
│   • if state.showSun:                                               │
│       update sun Entity transform from state.sunDirection           │
│       (position = sunDir·SUN_DISTANCE, billboard rotation,          │
│        scale to subtend 0.52° at origin)                            │
│   • if state.showSun changed: scene.add(sunEntity) / removeEntity   │
│   • if state.showSun changed: view.setBloomOptions(enabled=…)       │
└─────────────────────────────────────────────────────────────────────┘
```

The flow is deliberately minimal — only the two new boolean flags travel through `MoonRenderState`; the rest is per-platform Filament setup pulling those flags + the existing `state.sunDirection`. No new commonMain math, no new actions, no new ADRs.

## ADR-0004 amendment

ADR-0004 currently enumerates the NASA SVS attribution string for the lunar surface imagery. This spec adds a parallel paragraph for the star backdrop:

```
Star imagery: ESO/S. Brunier, Milky Way Panorama (ESO press release 0932).
CC BY 4.0. https://www.eso.org/public/images/eso0932a/
```

The amendment is a one-paragraph addition to ADR-0004 § "Attribution strings" (or equivalent), recorded with date 2026-04-29 and a pointer back to this spec. Same shape as ADR-0005's `setLightingPreset` durationMs amendment from 04-sun-control.

The string appears verbatim in `AboutSheet.kt` alongside the existing NASA SVS line.

## Components

### Star cubemap asset (T701, T702)

**Source.** ESO Milky Way Panorama by Serge Brunier (2009). Equirectangular HDR, 800-megapixel original. CC BY 4.0.

**Bake pipeline (offline, not in Gradle).**
1. Download a manageable resolution (4096×2048 equirectangular is plenty).
2. Convert equirectangular → cubemap, 6 faces at 1024×1024. Tools: cmft (Branimir Karadzic), AMD Cubemapgen, hdri-tools, Blender's "environment texture → cube" node, or a Python+PIL script.
3. Output: 6 PNG files, 8-bit sRGB, ~1 MB each.

**Bundled path.** `shared/src/commonMain/composeResources/files/stars/{px,nx,py,ny,pz,nz}.png`

**Face order.** Filament's cubemap face order is `[+X, -X, +Y, -Y, +Z, -Z]`. The PNG filenames mirror the convention so the loader can iterate `["px", "nx", "py", "ny", "pz", "nz"]` and feed each into `Texture.PixelBufferDescriptor` in the right slot.

**Why PNG, not KTX2.** Per ADR-0011, Android Filament 1.71 has no public binding for `Ktx2Reader`; the bundled tier ships PNG and decodes via `BitmapFactory` (Android) / `CGImageSource` (iOS). The cubemap is small enough that ETC1S compression isn't worth a divergent path.

### Sun material (T710, T711)

**Source.** New `sun.mat` in `shared/src/commonMain/materials/sun.mat`:

```
material {
    name : sun,
    shadingModel : unlit,
    blending : opaque,
    parameters : [
        { type : float, name : intensity }
    ]
}

fragment {
    void material(inout MaterialInputs material) {
        prepareMaterial(material);
        float i = materialParams.intensity;
        material.baseColor = vec4(i, i, i, 1.0);
    }
}
```

Single `intensity` uniform. Set to ~5–10 in linear HDR space at runtime so the pixels exceed the bloom threshold without overdriving.

**Build wiring.** Existing Gradle `compileMaterials` task already runs `matc` for the Moon material. Adding `sun.mat` to the same source set produces `sun.filamat` next to the Moon's `.filamat` — bundled in the same `composeResources/files/` directory, loaded via the same `Material.Builder().payload(filamatBytes)` pattern.

**Asset path.** `shared/src/commonMain/composeResources/files/sun.filamat`

### Sun Renderable (T712, T713)

A 1×1 quad mesh built procedurally at startup (4 vertices, 2 triangles, single `BillboardVertex` attribute). Filament's `Renderable.Builder()` with the `sun.filamat` MaterialInstance.

**Per-frame transform** (T714):

```
sunPos    = state.sunDirection * SUN_DISTANCE        // 1000.0 world units
sunSize   = 2.0 * SUN_DISTANCE * tan(SUN_ANGULAR_DIAMETER_RAD / 2)  // ~9.1 units
sunOrient = lookAt(from = sunPos, to = camera.position, up = Vec3.UP)
            // billboard: face the camera each frame
transform = translate(sunPos) * rotate(sunOrient) * scale(sunSize, sunSize, 1)
```

`transformManager.setTransform(sunInstance, transform)` per frame. The lookAt-into-billboard construction is standard; same math as countless space-game suns.

### Bloom config (T720, T721)

Filament `BloomOptions` — initial values to tune in T722:

```
BloomOptions {
    enabled    = state.showSun       // tracks the sun toggle
    threshold  = true                // threshold-based; only HDR pixels above kick in
    strength   = 0.5f                // bloom intensity (eyeball)
    resolution = 360                 // bloom pass resolution (default; 720 = better, 360 = faster)
    levels     = 6                   // bloom pyramid depth
    blendMode  = ADD
    // disable everything else (no lensFlare, no starburst, no chromaticAberration, no ghosts)
}
```

Filament's bloom-threshold pixels are determined by the per-channel HDR luminance vs the threshold. Sun emissive intensity ~5–10 vs Moon max reflective ~1.0 means a threshold around the bloom's default (~1.0 in linear HDR) cleanly separates them. The `intensity` knob in `sun.filamat` is the dial — bump it up if Moon highlights bloom; bump it down if the sun bloom is too aggressive.

### State + viewmodel additions (T705, T715, T731)

```kotlin
// MoonRenderState.kt
data class MoonRenderState(
    val cameraYawRad: Float = 0f,
    // … existing fields …
    val showStars: Boolean = true,        // NEW
    val showSun: Boolean = true,          // NEW
    val textureSet: TextureSet = TextureSet.Placeholder,
)

// MoonViewModel.kt
fun setShowStars(value: Boolean) {
    _state.update { it.copy(showStars = value) }
}
fun setShowSun(value: Boolean) {
    _state.update { it.copy(showSun = value) }
}
```

Same shape as `setSunDirection` / `highlightLocation` / `setTextureSet`. No Mutex needed — these are direct setters, not commands; concurrent calls last-writer-wins which is fine for boolean toggles.

### SettingsSheet additions (T730)

Two `Switch` rows added to the existing `SettingsSheet` placeholder. Composable shape:

```kotlin
@Composable
fun SettingsSheet(
    showStars: Boolean,
    showSun: Boolean,
    onShowStarsChange: (Boolean) -> Unit,
    onShowSunChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Each row: a `Row` with `Text("Show stars")` + `Switch(checked, onCheckedChange)`. Material3 styling. Visible when `state.showStars` / `showSun` flips, no animation overlay.

### `MoonExplorerScreen` rewire (T732)

Two new state reads + callbacks:

```kotlin
SettingsSheet(
    showStars = state.showStars,
    showSun = state.showSun,
    onShowStarsChange = viewModel::setShowStars,
    onShowSunChange = viewModel::setShowSun,
    onDismissRequest = { settingsSheetVisible = false },
)
```

No new job tracker (these are non-suspending direct setters; instant state update, no animation pipeline).

## Project structure delta

```
shared/src/commonMain/kotlin/org/jetbrains/moonexplorer/
├── state/
│   ├── MoonRenderState.kt             (+ showStars, showSun fields)
│   └── MoonViewModel.kt               (+ setShowStars, setShowSun methods)
└── ui/
    ├── SettingsSheet.kt               (+ 2 Switches with viewmodel hooks)
    └── MoonExplorerScreen.kt          (rewire SettingsSheet args)

shared/src/commonMain/composeResources/files/
├── stars/                             (NEW)
│   ├── px.png, nx.png, py.png, ny.png, pz.png, nz.png
└── sun.filamat                        (NEW — baked from materials/sun.mat)

shared/src/commonMain/materials/
└── sun.mat                            (NEW — matc source)

shared/src/androidMain/kotlin/org/jetbrains/moonexplorer/
└── render/MoonHost.kt                 (+ Skybox, sun Renderable, bloom config)

iosApp/iosApp/MoonRenderer.mm          (+ Skybox, sun Renderable, bloom config)

shared/src/commonTest/kotlin/org/jetbrains/moonexplorer/
└── state/MoonViewModelTest.kt        (+ setShowStars, setShowSun cases)

ai-docs/decisions/
└── 0004-asset-strategy.md             (+ ESO attribution paragraph)
```

No new Gradle modules, no new platform targets, no new pods. The `compileMaterials` task already runs `matc` for the Moon material; adding `sun.mat` to the same source set is a one-line build script change at most (or zero lines if `compileMaterials` globs `materials/*.mat`).

## Error handling

| Scenario | Handling |
|---|---|
| One of the 6 cubemap PNGs fails to decode | Hard failure with clear log error. Asset corruption is a release-pipeline mistake, never a user fault. |
| `sun.filamat` missing or fails to parse | Same — hard failure at startup. |
| `state.showStars` rapidly toggling | `_state.update` deduplicates equal values (StateFlow); per-frame state pull handles transitions cleanly. |
| Bloom enabled but `sun.showSun = false` | Defensive: `setBloomOptions(enabled = state.showSun)` reads the same flag, so they're always in sync. |
| iOS pod missing the Skybox / bloom symbols | Symbol-not-found at link time. ADR-0008's existing `Filament/filament + Filament/uberz + Filament/ktxreader` subspecs cover all three; verified during T703 / T722. |
| Cubemap face wrong-rotation | Visible diagonal seams. Fix in the offline bake pipeline; no runtime guard needed. |

## Testing strategy

### `commonTest`

- **`MoonViewModelTest`** extensions (T732):
  - `setShowStars_togglesState`: default true → call `setShowStars(false)` → state.showStars == false.
  - `setShowSun_togglesState`: default true → call `setShowSun(false)` → state.showSun == false.
  - `setShowStars_idempotent`: calling with the same value doesn't emit a new state value (StateFlow distinct-equals semantics).
  - Same for `setShowSun_idempotent`.

### Per-platform native (no commonTest coverage)

- Skybox setup, sun Renderable, bloom config, transform updates — none of this is exercised by `commonTest` because it lives in `androidMain` / `iosApp/`. **This is the established gap from 02-mvp / 04-sun-control where renderer-host code has no unit tests.** Verification path is on-device hardware testing per the SC-005 carryover target.
- Hardware checklist lives in `results.md` § Pending hardware measurements (T741): bloom threshold cleanly separates sun from Moon, bloom feels right on Pixel 6, eclipse-occlusion looks correct, settings toggles update within one frame, etc.

### Out of scope for tests

- Screenshot diffs of the celestial backdrop — same Compose-UI-test gap noted in every prior spec.
- Bloom-pass cost measurement — hardware concern, captured in results.md.

## Complexity tracking

| Decision | Why this complexity is in scope |
|---|---|
| Per-platform Filament duplication (Skybox + Renderable + bloom) | Standard for this project; matches 02-mvp / 03-flyto / 04-sun-control. No shortcut without abandoning Filament. |
| Bundled cubemap (6 PNGs at 1024×1024) instead of streamed HD | Skybox is "what you see all the time"; no resolution tier needed. ~6 MB install bump is acceptable. |
| Lat/lon-space sun direction (existing) — billboard transform reads `state.sunDirection` directly | No new math. Reuses what 04-sun-control already wired through. The billboard's lookAt is the only per-platform code. |
| Bloom always-on when sun is visible (no separate `showSunBloom` flag) | One mental model: "showing the sun = the sun glows." Splitting bloom into its own toggle adds a tuning knob nobody asked for. If hardware perf requires it, file a follow-up. |
| Settings persistence deferred | Two booleans persisted via DataStore is a small task that stands alone; doesn't gate this spec. |
| ADR-0004 amendment, not new ADR | Same pattern as 04's ADR-0005 amendment. Star attribution is a one-paragraph addition, not an architectural decision worth its own file. |

## Risks / open questions

1. **ESO Milky Way Panorama license verification.** I'm 95% sure it's CC BY 4.0 (ESO's standard for press images), but T701 should confirm against the original press release page before we bundle. Worst case: fall back to NASA's Tycho Star Catalog skymap (public domain, no attribution constraint).
2. **Equirectangular → cubemap conversion drift.** The bake step is offline; if face seams are visible at runtime, the conversion tool's filtering needs adjusting. cmft has a flag for this; document the tool + version used in `results.md`.
3. **Bloom threshold tuning vs hardware HDR ranges.** Filament's bloom is sensitive to the actual HDR pixel values. The 5–10 emissive intensity is a starting point; T722 may need to crank it higher if the Moon's directional-light highlights creep past threshold on iPhone 12.
4. **iOS pod symbol coverage.** `Filament/filament` should include `Skybox`, `BloomOptions`, and the material-loading entry points. Verified through ADR-0008's prior pod testing, but a quick `nm libfilament.a | grep -i Skybox` during T703 is cheap insurance.
5. **GPU memory headroom.** Cubemap (~24 MB) on top of 8K Moon textures (~32 MB) is ~56 MB of GPU texture memory. Comfortable on Pixel 6 (~6 GB RAM), tighter on iPhone 12 (~4 GB) but well within Apple's per-app GPU budget. If we ever target iPhone SE / older, drop the cubemap to 512×512 (~6 MB GPU memory).
6. **Bloom on the Moon's lit edge during sun-direction wrap.** When the joystick or preset moves the sun across the Moon's silhouette, the bloom may briefly leak through the Moon's outline due to the post-FX nature of the pass. Documented as accepted edge case in spec.md; if it's distracting, raise the threshold further.

## References

- ADR-0001 (Filament as the renderer)
- ADR-0003 (Renderer host pattern — pull-not-push state)
- ADR-0004 (Asset strategy — amended in T701 to add ESO attribution)
- ADR-0006 (Selenographic coordinate convention — sun direction frame)
- ADR-0008 (Filament pod via raw URL — Skybox / Material / bloom symbols)
- ADR-0011 (Android HD KTX2 deferred — PNG bundling rule applies)
- `ai-docs/specs/02-moon-renderer-mvp/plan.md` — bundled-PNG asset pattern reused here
- `ai-docs/specs/04-sun-control/plan.md` — `state.sunDirection` and animation pattern
- `./spec.md` — acceptance criteria
- `./tasks.md` — execution plan
