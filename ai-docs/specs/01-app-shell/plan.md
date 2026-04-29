# Implementation Plan: 01 — App Shell

**Branch:** `01-app-shell`
**Created:** 2026-04-29
**Status:** Draft (pending user ratification)

## Architecture flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  UI (Compose, commonMain)                                           │
│  • SearchBar (collapsed icon → expanded text field + results)       │
│  • LocationInfoSheet (ModalBottomSheet from Material3)              │
│  • SettingsSheet (placeholder ModalBottomSheet)                     │
│  • Existing: MoonExplorerScreen, MoonViewport, SunControl,          │
│    AboutSheet (now with a "Settings" row)                           │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ search / flyTo / explainCurrentView / ...
                          │ (FR-005: high-level commands flow here)
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Actions (commonMain/actions/)                                      │
│  • MoonExplorerActions interface  ← ADR-0005 locked surface         │
│  • MoonExplorerActionsImpl(viewModel, catalog)                      │
│       - search* methods read from SiteCatalog                       │
│       - flyTo* / set* methods mutate via MoonViewModel,             │
│         serialised by a Mutex (per ADR-0005)                        │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ viewModel.setCamera(...) / setSunDirection(...) / ...
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  State (existing — commonMain/state/)                               │
│  • MoonViewModel + MutableStateFlow<MoonRenderState>                │
│  • Already in place from 00-renderer-spike + 02-moon-renderer-mvp   │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ state pulled per frame
                          ▼
                   Renderer hosts (unchanged)


   ──── parallel concern, doesn't go through Actions ────

┌─────────────────────────────────────────────────────────────────────┐
│  Site catalog (commonMain/domain/)                                  │
│  • MoonSite data class (id, name, subtitle, lat, lon, type, desc)   │
│  • SiteCatalog wraps List<MoonSite>; search(q, limit) + byId(id)    │
│  • Loaded once at app startup from composeResources/files/sites.json│
└─────────────────────────────────────────────────────────────────────┘
```

**Boundary that this spec enforces:** continuous gestures (`pointerInput { detectTransformGestures }`) keep calling `viewModel.onDrag/onPinch` directly — they're not commands and ADR-0005 deliberately doesn't include them. Discrete commands (search, fly-to, settings, sun-direction) flow through `MoonExplorerActions`. Drawing this line in the sand here keeps the action surface honest without forcing every gesture frame through a `Mutex`.

## Components

### `MoonSite` (commonMain/domain/MoonSite.kt)

```kotlin
@Serializable
data class MoonSite(
    val id: String,
    val name: String,
    val subtitle: String? = null,        // e.g., "Sea of Tranquility" for Mare Tranquillitatis
    val lat: Double,                     // degrees, +N
    val lon: Double,                     // degrees, +E (per ADR-0006)
    val type: SiteType,
    val description: String,
)

@Serializable
enum class SiteType { MARE, CRATER, LANDING_SITE, OTHER }
```

ADR-0005's existing `MoonSite` (in `actions/`) has only `id`, `name`, `lat`, `lon`, `tags: List<String>`. **This spec extends the model** with `subtitle`, `type` (enum, replaces tags for our finite catalog), and `description`. The actions interface returns the richer model — Koog tools can ignore fields they don't need (kotlinx.serialization tolerates extras with `ignoreUnknownKeys`).

### `SiteCatalog` (commonMain/domain/SiteCatalog.kt)

```kotlin
class SiteCatalog(private val sites: List<MoonSite>) {
    fun search(query: String, limit: Int = 10): List<MoonSite>
    fun byId(id: String): MoonSite?

    companion object {
        suspend fun loadBundled(): SiteCatalog  // reads files/sites.json via Res.readBytes
    }
}
```

Loaded once at startup, cached for the process lifetime. `search` is case-insensitive substring on `name` + `subtitle`, sorted alphabetically by `name`, capped at `limit`. Trivially fast for 16 entries.

### `MoonExplorerActions` (commonMain/actions/MoonExplorerActions.kt)

Verbatim per ADR-0005:

```kotlin
interface MoonExplorerActions {
    suspend fun searchMoonLocations(query: String, limit: Int = 10): List<MoonSite>
    suspend fun getCurrentView(): CurrentView
    suspend fun explainCurrentView(): String
    suspend fun flyToMoonLocation(id: String, durationMs: Long = 1500): ActionAck
    suspend fun setLightingPreset(preset: LightingPreset): ActionAck
    suspend fun setSunDirection(lat: Double, lon: Double): ActionAck
    suspend fun highlightLocation(id: String, on: Boolean = true): ActionAck
    suspend fun compareLocations(id1: String, id2: String): ComparisonResult
}
```

Plus the supporting types from ADR-0005: `CurrentView`, `ActionAck`, `LightingPreset`, `ComparisonResult`.

### `MoonExplorerActionsImpl` (commonMain/actions/MoonExplorerActionsImpl.kt)

```kotlin
class MoonExplorerActionsImpl(
    private val viewModel: MoonViewModel,
    private val catalog: SiteCatalog,
) : MoonExplorerActions {
    private val mutex = Mutex()

    override suspend fun searchMoonLocations(query: String, limit: Int): List<MoonSite> =
        catalog.search(query, limit)

    override suspend fun flyToMoonLocation(id: String, durationMs: Long): ActionAck = mutex.withLock {
        val site = catalog.byId(id) ?: return@withLock ActionAck(ok = false, message = "no such site: $id")
        // 01-app-shell: snap-to. 03-sites-and-flyto adds the lerp using durationMs.
        viewModel.setCameraTarget(latToYaw(site.lat, site.lon), latToPitch(site.lat))
        ActionAck(ok = true, message = "centered on ${site.name}")
    }
    // ... other methods
}
```

`getCurrentView()` reads the current StateFlow snapshot and converts yaw/pitch back to lat/lon via the inverse of `latLonToYawPitch`. `explainCurrentView()` returns a short human-readable string (e.g., "Centered on Tycho crater, sun lit from the west"); the impl does this from the current state without LLM. `setSunDirection(lat, lon)` calls `viewModel.setSunDirection(...)` after converting lat/lon to a unit vector. `highlightLocation` sets `state.highlightedSiteId` (already in the state model). `setLightingPreset` and `compareLocations` may stub-return for `01-app-shell` per FR-007.

### `MoonViewModel` (existing — minor additions)

Add a `setCameraTarget(yawRad, pitchRad)` method that updates the state without touching distance / sun. `01-app-shell` ships only the snap path; `03-sites-and-flyto` adds an animated variant.

### UI components (commonMain/ui/)

- **`SearchBar`** — collapsed state shows a magnifier icon (TopEnd, with `statusBarsPadding()`); tapping expands to a text field + dropdown result list. Tapping outside, pressing Escape, or clearing the query collapses back.
- **`LocationInfoSheet`** — Material3 `ModalBottomSheet`. Shows name, optional subtitle, type label (small chip), formatted coords, description, and a single "Center on this site" filled button at the bottom.
- **`SettingsSheet`** — Material3 `ModalBottomSheet`. Just a "Settings — coming soon" placeholder + dismiss affordance.
- **`AboutSheet` (existing)** — gains a "Settings" row at the bottom that opens `SettingsSheet`.

## Data models

### `sites.json` schema

```json
[
  {
    "id": "mare_tranquillitatis",
    "name": "Mare Tranquillitatis",
    "subtitle": "Sea of Tranquility",
    "lat": 8.5,
    "lon": 31.4,
    "type": "MARE",
    "description": "A basaltic plain on the Moon's near side. Apollo 11's landing site (Tranquillity Base) sits at its south-western edge."
  }
]
```

A flat array of sixteen entries. Schema enforced by `kotlinx.serialization` deserialization (`Json { ignoreUnknownKeys = true }` so the file can carry extra fields without breaking the runtime parser).

### Curated 16-site roster

| # | id | Type | Notes |
|---|---|---|---|
| 1 | `mare_tranquillitatis` | MARE | Sea of Tranquility |
| 2 | `mare_serenitatis` | MARE | Sea of Serenity |
| 3 | `mare_imbrium` | MARE | Sea of Showers |
| 4 | `mare_crisium` | MARE | Sea of Crises |
| 5 | `oceanus_procellarum` | MARE | Ocean of Storms (largest mare) |
| 6 | `tycho` | CRATER | Bright ray crater, ~108 Mya |
| 7 | `copernicus` | CRATER | Prominent ejecta system |
| 8 | `plato` | CRATER | Dark-floored, near-side north |
| 9 | `aristarchus` | CRATER | Brightest crater on near side |
| 10 | `kepler` | CRATER | Small bright crater near Copernicus |
| 11 | `apollo_11` | LANDING_SITE | Tranquillity Base, 1969-07-20 |
| 12 | `apollo_14` | LANDING_SITE | Fra Mauro, 1971-02-05 |
| 13 | `apollo_15` | LANDING_SITE | Hadley Rille, 1971-07-30 |
| 14 | `apollo_17` | LANDING_SITE | Taurus-Littrow, 1972-12-11 (last crewed) |
| 15 | `change_5` | LANDING_SITE | Mons Rümker, 2020-12-01 (sample return) |
| 16 | `south_pole_aitken` | OTHER | Largest known impact basin |

Lat/lon values to be filled in during T201; rough bounds in NASA's published gazetteer.

### `MoonRenderState` extension

`highlightedSiteId: String?` is already in the state model from `00-renderer-spike`. No new fields are required; `flyToMoonLocation` updates `cameraYawRad` / `cameraPitchRad`, `highlightLocation` updates `highlightedSiteId`.

## Project structure delta

```
shared/src/commonMain/kotlin/org/jetbrains/moonexplorer/
├── actions/                            (NEW)
│   ├── MoonExplorerActions.kt          (interface + ADR-0005 supporting types)
│   └── MoonExplorerActionsImpl.kt
├── domain/                             (existing — Vec3, MoonMath, UvSphere stay)
│   ├── MoonSite.kt                     (NEW — extended model with subtitle, type, description)
│   └── SiteCatalog.kt                  (NEW)
├── state/                              (existing)
│   ├── MoonRenderState.kt              (unchanged)
│   ├── MoonViewModel.kt                (+ setCameraTarget)
│   └── TextureSet.kt                   (unchanged)
├── ui/                                 (existing — additions)
│   ├── MoonExplorerScreen.kt           (wires SearchBar + LocationInfoSheet + Settings link)
│   ├── SearchBar.kt                    (NEW)
│   ├── LocationInfoSheet.kt            (NEW)
│   ├── SettingsSheet.kt                (NEW)
│   ├── AboutSheet.kt                   (+ Settings row)
│   └── SunControl.kt                   (unchanged)
└── App.kt                              (constructs SiteCatalog + Actions impl in remember{})

shared/src/commonMain/composeResources/files/
└── sites.json                          (NEW — 16 entries)

shared/src/commonTest/kotlin/org/jetbrains/moonexplorer/
├── domain/SiteCatalogTest.kt           (NEW)
└── actions/MoonExplorerActionsImplTest.kt (NEW)
```

No changes to `androidMain`, `iosMain`, `:androidApp`, or `iosApp/`. Everything new lives in commonMain.

## Error handling

| Scenario | Handling |
|---|---|
| `sites.json` missing or malformed | Throws at `SiteCatalog.loadBundled()`. App crashes loud — this is a release-pipeline bug, not a user fault. |
| Search returns no matches | `actions.searchMoonLocations` returns empty list; UI shows "No matches" inline. |
| `flyToMoonLocation` with unknown id | `ActionAck(ok = false, message = "no such site: $id")`. UI logs but does not crash. |
| `compareLocations` called in 01-mvp | Returns a `ComparisonResult` with `notes = "compare deferred"` placeholder. |
| `setLightingPreset` called in 01-mvp | Returns `ActionAck(ok = false, message = "lighting presets deferred")`. |
| Concurrent `flyTo` + `setSunDirection` | Mutex serializes. The second call waits its turn. |
| User dismisses search field while results are visible | Results disappear; field collapses; viewport stays unobstructed. |

## Testing strategy

### `commonTest`

- **`SiteCatalogTest`** — load bundled JSON, assert ≥ 16 entries, assert `search("tycho")` returns Tycho, assert `search("MARE ", limit=3)` returns the first three mare alphabetically, assert `byId("apollo_11")` non-null, `byId("does-not-exist")` returns null.
- **`MoonExplorerActionsImplTest`** — drive the impl against a real `MoonViewModel` + `SiteCatalog`. Assert `searchMoonLocations("tych")` returns Tycho. Assert `flyToMoonLocation("tycho")` advances the StateFlow's `cameraYawRad` / `cameraPitchRad` to the expected values (within tolerance). Assert `flyToMoonLocation("garbage")` returns `ActionAck(ok = false)`. Assert two concurrent `flyTo` calls don't race (Mutex protects).

### Out of scope for `01-app-shell` tests

- UI / screenshot tests for `SearchBar`, `LocationInfoSheet`, `SettingsSheet` — Compose UI tests in commonMain on AGP 9 alpha + KMP need additional setup (`runComposeUiTest`); deferred to a polish task or a dedicated test-infra spec.
- Koog tool integration tests — Phase 3.

## Complexity tracking

| Decision | Why this complexity is in scope |
|---|---|
| Mutex-defended impl | ADR-0005 mandates it; cheap. Future-proofs against parallel Koog tool dispatch. |
| Site catalog as bundled JSON (not hardcoded Kotlin) | Easier to edit, plays well with future asset-pipeline tools. 4 KB cost. |
| Snap-to instead of animated fly-to | Animation belongs in `03-sites-and-flyto`. Snap is a deliberate placeholder. |
| `SettingsSheet` as a placeholder | The spec's job is to land the *entry affordance*, not a real settings UX. |

## Risks / open questions

1. **Compose Multiplatform `ModalBottomSheet` on iOS** — `02-moon-renderer-mvp` shipped one (`AboutSheet`) and the user confirmed it works. Two more (`LocationInfoSheet`, `SettingsSheet`) follow the same pattern. **Low risk.**
2. **`MoonSite` shape extension** vs. ADR-0005's `tags: List<String>` field — we add `subtitle`, `type`, `description` and drop `tags` for the catalog. ADR-0005's signature said `tags: List<String>`; deviation needs to be noted. **Mitigation:** the field is optional in serialization; downstream `:shared-ai` can read whichever shape it needs.
3. **`getCurrentView` / `explainCurrentView` shape** — these return state-snapshot data. The impl needs `MoonRenderState` to lat/lon inverse; that math already exists in `domain/MoonMath.kt` (per `00-renderer-spike`). **Low risk.**
4. **Lat/lon → yaw/pitch math** — the camera position math from `selenographic-math-camera.md` covers forward (yaw, pitch → eye position). The inverse (lat, lon → yaw, pitch needed to view that lat/lon at the camera centre) needs to be added to `MoonMath`. ~10 lines. **Low risk; covered in T211.**

## References

- ADR-0005 (`MoonExplorerActions` shape — locked here)
- ADR-0006 (selenographic coordinate convention)
- ADR-0007 (SDD framework)
- `ai-docs/architecture.md` § Layers
- `ai-docs/research/selenographic-math-camera.md` (camera math; needs inverse for FR-004)
- `ai-docs/initial-idea.md`
- `./spec.md` — acceptance criteria
- `./tasks.md` — execution plan
