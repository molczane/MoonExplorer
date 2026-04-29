# Tasks: 01 — App Shell

## Format: `[ID] [P?] [US?] Description`

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.
Acceptance criteria for each user story live in `spec.md`.

## Path conventions

All paths relative to `MoonExplorer/` repo root. Task IDs are namespaced **T200+** to avoid collision with `00-renderer-spike` (T001–T093) and `02-moon-renderer-mvp` (T100–T145).

---

## Phase 1: Site catalog data layer

- [x] **T201** Author `composeResources/files/sites.json` with the 16 curated entries
  - Use the roster table in `plan.md` § "Curated 16-site roster" for the `id` / `type` columns.
  - Look up canonical lat/lon from the [USGS Lunar Gazetteer](https://planetarynames.wr.usgs.gov/) — accept ±0.1° rounding.
  - Each entry has a 60–200 character `description` written for a curious-but-non-expert reader.
  - File size ≤ 4 KB.
  - _Requirements: FR-001, NFR bundle-size_

- [x] **T202** [P] Implement `domain/MoonSite.kt` in commonMain
  - `@Serializable data class MoonSite(id, name, subtitle?, lat, lon, type, description)`.
  - `@Serializable enum class SiteType { MARE, CRATER, LANDING_SITE, OTHER }`.
  - _Requirements: FR-001, FR-003, ADR-0005 (with extensions per plan.md)_

- [x] **T203** [P] Implement `domain/SiteCatalog.kt` in commonMain
  - `class SiteCatalog(private val sites: List<MoonSite>) { fun search(...); fun byId(...) }`.
  - `companion object { suspend fun loadBundled(): SiteCatalog }` reading via `Res.readBytes("files/sites.json")` + `AssetManifest`-style `Json { ignoreUnknownKeys = true }` parser.
  - Search: case-insensitive substring on `name` + `subtitle`, alphabetical by `name`, capped at `limit`.
  - _Requirements: FR-002_

- [x] **T204** [P] `commonTest`: `SiteCatalogTest` — 11 cases against a hardcoded sample list (search by name / subtitle, case-insensitive, empty + whitespace queries, sort order, limit, byId hit + miss, all-returns-bundled-order). `loadBundled()` deferred to runtime exercise — the AGP-9-alpha `Res.readBytes`-in-commonTest gap is the same one that deferred T143.
  - Load bundled JSON, assert ≥ 16 entries.
  - `search("tych")` returns Tycho.
  - `search("MARE", limit = 3)` returns the first three mare alphabetically (case-insensitive).
  - `search(" ")` returns empty.
  - `byId("apollo_11")` non-null; `byId("missing")` null.
  - _Requirements: SC-006_

**Checkpoint**: `sites.json` ships in compose resources; `SiteCatalog` loads it; `:shared:testAndroidHostTest` includes a passing `SiteCatalogTest`.

---

## Phase 2: Action surface (ADR-0005)

- [x] **T210** Implement `actions/MoonExplorerActions.kt` in commonMain — interface + supporting types
  - Eight methods verbatim from ADR-0005: `searchMoonLocations`, `getCurrentView`, `explainCurrentView`, `flyToMoonLocation`, `setLightingPreset`, `setSunDirection`, `highlightLocation`, `compareLocations`.
  - `@Serializable` data classes: `CurrentView`, `ActionAck`, `ComparisonResult`. `enum class LightingPreset { Day, Night, Terminator, HighContrast }`.
  - _Requirements: FR-005, FR-007, SC-005_

- [x] **T211** Implement `actions/MoonExplorerActionsImpl.kt` in commonMain
  - `class MoonExplorerActionsImpl(viewModel: MoonViewModel, catalog: SiteCatalog) : MoonExplorerActions`.
  - `private val mutex = Mutex()`. Side-effecting methods wrap with `mutex.withLock { ... }`.
  - `searchMoonLocations` → `catalog.search`.
  - `flyToMoonLocation(id, durationMs)` → look up site, snap-to via `viewModel.setCameraTarget(yaw, pitch)`. `durationMs` is accepted but ignored in `01-app-shell`; honored in `03-sites-and-flyto`.
  - `getCurrentView()` → snapshot `viewModel.state.value`, convert yaw/pitch back to lat/lon via the inverse of `MoonMath.latLonToYawPitch` (T211a — small math utility), return `CurrentView(...)`.
  - `explainCurrentView()` → format a short string from the current state ("Centered on Tycho crater · sun lit from the west").
  - `setSunDirection(lat, lon)` → convert lat/lon to a unit vector, call `viewModel.setSunDirection(...)`.
  - `highlightLocation(id, on)` → `viewModel.highlightLocation(if (on) id else null)`.
  - `setLightingPreset(...)` → `ActionAck(ok = false, message = "lighting presets deferred to a future spec")`.
  - `compareLocations(...)` → `ComparisonResult(a, b, distanceKm, notes = "compare deferred")` with the geodesic distance computed (cheap; Math is already there).
  - _Requirements: FR-002, FR-004, FR-005, FR-007_

- [x] **T211a** [P] Add `MoonMath.latLonToYawPitch` and `yawPitchToLatLon` in commonMain — also added `greatCircleDistKm` (haversine on the IAU mean lunar radius) for `compareLocations`.
  - Forward: given site lat/lon and the camera at unit distance facing the origin, compute (yawRad, pitchRad) that puts the site at the camera centre.
  - Inverse: given (yawRad, pitchRad), return (lat, lon) of the point at the camera centre.
  - Both are ~5-line trig functions; covered by the convention in ADR-0006.
  - _Requirements: FR-004, supporting T211_

- [x] **T212** [P] `commonTest`: `MoonExplorerActionsImplTest`
  - Construct against a real `MoonViewModel` + `SiteCatalog`.
  - `searchMoonLocations("tych")` → contains Tycho.
  - `flyToMoonLocation("tycho")` → `viewModel.state.value.cameraYawRad/Pitch` advance to expected values (compare with `latLonToYawPitch(tycho.lat, tycho.lon)`).
  - `flyToMoonLocation("garbage")` → `ActionAck(ok = false)`.
  - `getCurrentView()` after a `flyTo` returns the chosen site's lat/lon (round-trip).
  - `setLightingPreset(Day)` returns `ok = false` per the deferred-stub policy.
  - Two concurrent `flyTo` calls (via `coroutineScope { launch{}; launch{} }`) both complete without races; final state is one of the two targets (Mutex serialises).
  - _Requirements: SC-005, SC-006_

**Checkpoint**: `MoonExplorerActions` interface matches ADR-0005 byte-for-byte. The impl can be exercised end-to-end without UI.

---

## Phase 3: UI shell

- [ ] **T220** Implement `ui/SearchBar.kt` in commonMain
  - Collapsed: 🔍 magnifier icon at TopEnd, `statusBarsPadding()`. Tap expands.
  - Expanded: text field with focus + soft keyboard. Below the field: dropdown with up to 10 result rows (each row: `name`, `subtitle?` in dim text). "No matches" placeholder when query yields zero.
  - State hoisted via `query: String, onQueryChange: (String) -> Unit, results: List<MoonSite>, onResultTap: (MoonSite) -> Unit, onCollapse: () -> Unit`.
  - _Requirements: FR-002, US1 acceptance scenarios_

- [ ] **T221** Implement `ui/LocationInfoSheet.kt` in commonMain
  - `@OptIn(ExperimentalMaterial3Api::class)` Material3 `ModalBottomSheet`.
  - Content: site name (headlineSmall), subtitle if present (labelMedium / secondary colour), type chip ("Crater" / "Mare" / "Landing site"), formatted coords (`"43.31° S, 11.36° W"`), description (bodyMedium), `FilledButton("Center on this site")` at the bottom.
  - Drag-down + scrim-tap dismiss are ModalBottomSheet defaults; "Center on this site" stays the sheet open per US3 acceptance scenario.
  - State hoisted: `site: MoonSite, onCenterClick: () -> Unit, onDismissRequest: () -> Unit`.
  - _Requirements: FR-003, FR-004_

- [ ] **T222** Implement `ui/SettingsSheet.kt` in commonMain
  - Material3 `ModalBottomSheet` placeholder. Body: a single `Text("Settings — coming soon")` with `bodyMedium`.
  - `onDismissRequest: () -> Unit`.
  - _Requirements: FR-006_

- [ ] **T223** Modify existing `ui/AboutSheet.kt`
  - Add a clickable "Settings" row at the bottom (above the existing reference list). Tap → invoke a new `onSettingsClick: () -> Unit` parameter.
  - The host (`MoonExplorerScreen`) wires that callback to flip a `settingsSheetVisible` flag.
  - _Requirements: FR-006_

- [ ] **T224** Wire the shell into `ui/MoonExplorerScreen.kt`
  - Construct the `SiteCatalog` + `MoonExplorerActions` once via `remember { ... }` (or a `LaunchedEffect(Unit) { catalogState.value = SiteCatalog.loadBundled() }` since loading is suspending — see plan.md).
  - State for the search bar, the chosen site (info-sheet target), the about-sheet visibility, the settings-sheet visibility.
  - Layout: `Box { MoonViewport(...); SunControl(...); IconButton(About, TopStart); SearchBar(...); /* info+settings sheets render conditionally */ }`. About → AboutSheet → tap-Settings → SettingsSheet (sequential, never stacked).
  - All side-effecting interactions (search, info-sheet "Center", settings open) call through `MoonExplorerActions`. Continuous gestures (drag/pinch) keep calling `viewModel` directly per FR-005.
  - _Requirements: FR-002, FR-003, FR-004, FR-005, FR-006_

- [x] **T225** Add `MoonViewModel.setCameraTarget(yawRad, pitchRad)` (small extension) — moved up from Phase 3 since it's a hard dependency of T211. Pure state update; pitch clamped to ±PITCH_LIMIT_RAD to match the gesture handler.
  - Pure state update; doesn't touch distance / sun / textureSet.
  - _Requirements: FR-004, supporting T211_

**Checkpoint**: User journey works end-to-end — open app → tap 🔍 → type → tap result → see info sheet → tap "Center on this site" → camera snaps. About → Settings → "Coming soon" placeholder.

---

## Phase Final: Polish + tests + docs

- [ ] **T230** Run `:shared:testAndroidHostTest :shared:iosSimulatorArm64Test` and confirm green
  - All existing tests + new `SiteCatalogTest` + new `MoonExplorerActionsImplTest` pass on both platforms.
  - _Requirements: SC-006_

- [ ] **T231** [P] Write `ai-docs/specs/01-app-shell/results.md`
  - Status by phase; user-confirmed items; pending hardware confirmation; deferred items (UI tests; animated fly-to).
  - _Requirements: agent-runbook.md_

- [ ] **T232** [P] Update `ai-docs/specs/02-moon-renderer-mvp/results.md` § "Pending hardware measurements"
  - Cross-reference `01-app-shell` if any 02-mvp deferred items got picked up here (e.g., About-sheet UX validation now exercised through the new Settings row).
  - _Requirements: agent-runbook.md_

**Final Checkpoint**: all five user stories' acceptance criteria pass; `results.md` filed; `MoonExplorerActions` byte-for-byte matches ADR-0005.

---

## Dependencies & Execution Order

| From → To | Why |
|---|---|
| T201 → T203, T204 | Bundled JSON must exist before the catalog can load it |
| T202 → T203 | `MoonSite` data class is the catalog's element type |
| T202, T203 → T210 | Actions interface returns `MoonSite`; tests need `SiteCatalog` |
| T210 → T211 | Impl extends the interface |
| T203, T211a → T211 | Impl needs catalog + lat/lon math |
| T211 → T212 | Tests exercise the impl |
| T211, T220, T221, T222, T223 → T224 | Shell can't wire what doesn't exist |
| T225 → T211 | `setCameraTarget` is what `flyToMoonLocation` calls |
| Phase 1 + Phase 2 → Phase 3 | Data + actions before UI |
| Phase 3 → Phase Final | All flows wired before final tests + docs |

## Parallel Example: Phase 1

Once T201's JSON file exists, T202 + T203 + T204 are all parallel-safe — they're separate files with no Kotlin compile-time dependency on each other beyond imports.

## Implementation Strategy

- **Three PRs.** Land Phase 1 + Phase 2 in a single PR titled `01-app-shell: data + actions surface`. Land Phase 3 as a second PR titled `01-app-shell: search + info sheet + settings`. Land Phase Final as a third PR titled `01-app-shell: tests + results`.
- Phase 3 is bigger than the others; consider sub-batching by composable (T220 then T221+T222+T223 then T224) so review chunks stay reasonable.

## Notes

- ADR-0005's `MoonSite` had `tags: List<String>`. This spec replaces that with `type` (enum) + `subtitle?` + `description` for the bundled catalog. Documented in `plan.md` § "MoonSite". Rationale: a finite, hand-curated catalog benefits from typed buckets over freeform tags; future Koog tools can ignore the extra fields via `ignoreUnknownKeys`.
- The "snap-to camera centering" in T211 / T224 is deliberate — `03-sites-and-flyto` adds the lerp animation. The `durationMs` parameter on `flyToMoonLocation` is parsed but unused in `01-app-shell`.
- No new Gradle deps. The `kotlinx-serialization-json` / Compose Material3 / Ktor stack from `02-moon-renderer-mvp` covers everything this spec needs.
- ADR-0009's items 4 + 5 (search + info sheet UI) are resolved when this spec lands.
