# Feature Specification: 01 — App Shell

**Branch:** `01-app-shell`
**Created:** 2026-04-29
**Status:** Draft (pending user ratification)

## Goal (1-line)

Wrap the renderer (shipped by `00-renderer-spike` + textured by `02-moon-renderer-mvp`) with the Compose Multiplatform shell users will actually interact with: a curated site catalog, a search bar that filters it, a tap-target info sheet for the chosen site, a "center camera" affordance that re-frames the Moon on a site (snap, no animation — that's `03-sites-and-flyto`), and the locked `MoonExplorerActions` interface (per ADR-0005) that both UI button taps and Phase-3 Koog tool calls flow through.

## User Scenarios

### User Story 1 — Search the Moon by name (Priority: P1)

**Why this priority:** Until the user can name a site and find it, the catalog is dead weight. Search is the first verb the app needs to support; everything else (info sheet, center-on-site) hangs off it.

**Independent test:** Launch the app on a fresh install. Tap the search affordance. Type `tycho`. Tycho crater appears in the result list within one frame.

**Acceptance Scenarios:**
- WHEN the user taps the search affordance THEN the system SHALL reveal a text input field that takes focus and shows the soft keyboard.
- WHEN the user types into the search field THEN the system SHALL filter the bundled catalog by case-insensitive substring match on `name` and `subtitle`, sort alphabetically, and cap the result list at 10 entries.
- WHEN the user clears the search query THEN the system SHALL hide the result list and continue showing the Moon viewport unobstructed.
- WHEN the search returns no matches THEN the system SHALL show an inline "No matches" placeholder.

### User Story 2 — Read about a site (Priority: P1)

**Why this priority:** Search yields a site identifier; the user's natural next question is "what is this thing?". The info sheet answers that without leaving the viewport.

**Independent test:** Tap a search result. A modal bottom sheet rises with the site's name, type label, coordinates, and a short description.

**Acceptance Scenarios:**
- WHEN the user taps a search-result row THEN the system SHALL open a `LocationInfoSheet` showing the site's `name`, `subtitle` (if present), `type`, formatted coordinates (`lat°N/S, lon°E/W`), and `description`.
- WHEN the user dismisses the info sheet (drag-down or scrim tap) THEN the system SHALL return to the viewport with camera / sun / texture state preserved.
- WHEN the info sheet is open AND the user taps a different search result THEN the system SHALL replace the sheet content (no double-stack).

### User Story 3 — Re-center the camera on a site (Priority: P1)

**Why this priority:** Search + read is incomplete without the visual payoff. Re-centering establishes the search → site → "look at it" loop. Animation is a polish task; the snap suffices for v1.

**Independent test:** Open the info sheet for Tycho. Tap "Center on this site". The Moon viewport jumps so Tycho is at the center of the screen.

**Acceptance Scenarios:**
- WHEN the user taps "Center on this site" in the info sheet THEN the system SHALL update `MoonRenderState.cameraYawRad` and `cameraPitchRad` so the chosen site's lat/lon is on the camera's view ray. No animation is required (the snap is acceptable for `01-app-shell`; `03-sites-and-flyto` adds the lerp).
- WHEN the camera centers on a site THEN the info sheet SHALL stay open so the user can dismiss it on their own terms.

### User Story 4 — Action surface as the public command shape (Priority: P1)

**Why this priority:** ADR-0005 locked `MoonExplorerActions` as the contract that both UI buttons and Phase-3 Koog tool calls invoke. Building the UI through it from day 1 — instead of letting Compose composables touch `MoonViewModel` directly — enforces the boundary while it's cheap; refactoring after Phase 3 would mean touching every callsite.

**Independent test:** A `commonTest` constructs `MoonExplorerActionsImpl` against a real `MoonViewModel` + `SiteCatalog`, drives `searchMoonLocations("tych")` and `flyToMoonLocation("tycho")` directly, and asserts the search returns Tycho and the StateFlow advanced to the expected camera coords.

**Acceptance Scenarios:**
- WHEN the UI needs to resolve a site for search THEN it SHALL call `actions.searchMoonLocations(query)` rather than reaching into the catalog directly.
- WHEN the UI needs to re-center the camera on a site THEN it SHALL call `actions.flyToMoonLocation(id)` rather than poking `viewModel.state` directly.
- WHEN the impl receives concurrent side-effecting calls THEN it SHALL serialize them via a `Mutex` (per ADR-0005's "defend with Mutex even when called concurrently" — Koog's `toParallelToolCallsRaw` may dispatch in parallel later).
- All eight ADR-0005 methods (`searchMoonLocations`, `getCurrentView`, `explainCurrentView`, `flyToMoonLocation`, `setLightingPreset`, `setSunDirection`, `highlightLocation`, `compareLocations`) SHALL exist on the interface; partial implementations are acceptable for the methods `01-app-shell` doesn't fully exercise (`setLightingPreset` and `compareLocations` may stub-return until later specs).

### User Story 5 — Settings entry (placeholder) (Priority: P3)

**Why this priority:** Future settings (units, lat/lon format, accessibility) need an obvious entry point but no real screen yet. A "Settings" row in the existing About sheet that opens a placeholder `SettingsSheet` discharges the spec text without committing UX surface.

**Independent test:** Open the About sheet (existing ⓘ button), tap the new "Settings" row, see a `SettingsSheet` with "Settings — coming soon."

**Acceptance Scenarios:**
- WHEN the user opens About AND taps the Settings row THEN the system SHALL open a `SettingsSheet` with placeholder content and a back affordance.

### Edge Cases

- Empty search query: result list collapses; the moon viewport is unobstructed.
- Search query has only whitespace: same as empty.
- Site catalog `sites.json` fails to parse: hard build-time failure — schema is locked + bundled, so this is a release-pipeline mistake not a user fault. Validate at app startup; crash-loud rather than silently shipping a half-loaded catalog.
- A site's lat/lon is on the far side of the Moon (lon ≈ ±180°): camera re-center math still works because we treat the Moon as a unit sphere and yaw is unbounded.
- Search field has focus and the user rotates the device: the field stays focused; the keyboard re-anchors via system inset insets.
- About sheet is open AND user taps the Settings row: replace About sheet content with Settings (or stack — implementation chooses; either is acceptable as long as both are dismissable).

## Requirements

### Functional Requirements

- **FR-001**: WHEN the app starts THEN the system SHALL load the bundled `composeResources/files/sites.json` and parse it into an in-memory `SiteCatalog` of ≥ 16 entries.
- **FR-002**: WHEN the user types into the search field THEN `MoonExplorerActions.searchMoonLocations(query, limit = 10)` SHALL return matching sites by case-insensitive substring on `name` and `subtitle`, sorted alphabetically by `name`.
- **FR-003**: WHEN the user taps a search-result row THEN the system SHALL open a `LocationInfoSheet` populated from that site's record.
- **FR-004**: WHEN the user taps "Center on this site" in the info sheet THEN `MoonExplorerActions.flyToMoonLocation(id)` SHALL update `MoonRenderState.cameraYawRad` and `cameraPitchRad` to point at the site's lat/lon. The `durationMs` parameter is accepted but ignored in `01-app-shell` (snap-to); `03-sites-and-flyto` honours it.
- **FR-005**: ALL UI mutations to renderer state SHALL flow through `MoonExplorerActions`. Continuous gesture inputs (drag/pinch from `pointerInput`) MAY continue to call `MoonViewModel.onDrag/onPinch` directly — they're not commands and ADR-0005's interface deliberately doesn't include them.
- **FR-006**: WHEN the user opens the About sheet AND taps the Settings row THEN the system SHALL open a `SettingsSheet` placeholder.
- **FR-007**: ALL eight `MoonExplorerActions` methods listed in ADR-0005 SHALL be present on the interface. Methods that `01-app-shell` doesn't fully implement (`setLightingPreset`, `compareLocations`) MAY return `ActionAck(ok = false, message = "deferred to <future spec>")` or equivalent placeholder data.

### Key Entities

- **`MoonSite`** — `id`, `name`, optional `subtitle`, `lat`, `lon` (degrees), `type` (enum: `MARE` / `CRATER` / `LANDING_SITE` / `OTHER`), `description`. Bundled in `sites.json`.
- **`SiteCatalog`** — wraps `List<MoonSite>` with `search(query, limit)` + `byId(id)`. Loaded once at startup.
- **`MoonExplorerActions`** — interface (per ADR-0005); single command surface for UI + future Koog.
- **`MoonExplorerActionsImpl(viewModel, catalog)`** — concrete impl in commonMain. Side-effecting methods serialize via `Mutex`.
- **`SearchBar`** — Compose composable; collapsed icon → expanded text field with results dropdown.
- **`LocationInfoSheet`** — Material3 `ModalBottomSheet` showing site details + "Center on this site" button.
- **`SettingsSheet`** — Material3 `ModalBottomSheet` placeholder.

## Non-Functional Requirements

- **Performance**: Search returns within 16 ms for the 16-site catalog (trivially achievable; case-insensitive substring + alphabetical sort over 16 entries).
- **Bundle size**: `sites.json` ≤ 4 KB.
- **Offline**: Fully functional offline. No network calls.
- **Layered architecture**: UI never reaches into `MoonViewModel` for commands — only for continuous gesture forwarding (per FR-005). Side-effecting commands flow through `MoonExplorerActions`.
- **Concurrency safety**: Mutex-defended side-effecting methods (per ADR-0005).
- **State preservation**: Opening / dismissing any modal sheet preserves camera, sun, and `textureSet` state.

## Success Criteria

- **SC-001**: User can search "tycho" or "tranqui" and see the matching site(s) in the result list.
- **SC-002**: Tapping a search result opens an info sheet showing the site's name, type, coordinates, and description.
- **SC-003**: Tapping "Center on this site" snaps the camera to the site's lat/lon; the user visibly sees the Moon re-orient.
- **SC-004**: Settings row in About opens the `SettingsSheet` placeholder without crashing.
- **SC-005**: `MoonExplorerActions` interface in `:shared/commonMain/.../actions/` matches ADR-0005's surface byte-for-byte (8 methods, locked signatures).
- **SC-006**: `:shared:allTests` passes including new `SiteCatalogTest` and `MoonExplorerActionsImplTest`.

## Assumptions & Out of Scope

**Out of scope:**
- **Markers / pins on the Moon surface** — `03-sites-and-flyto` adds those.
- **Animated fly-to** — `03-sites-and-flyto`. The snap-to in `01-app-shell` is a deliberate placeholder.
- **Real settings screen** — deferred. `01-app-shell` only ships the entry affordance and a "coming soon" placeholder.
- **Compare-two-locations UI** — `MoonExplorerActions.compareLocations` exists per ADR-0005, but no UI exposes it in `01-app-shell`.
- **Lighting presets UI** — `setLightingPreset` exists per ADR-0005; the slider control from `00-renderer-spike` continues to drive sun direction.
- **Localization beyond English** — site names are in their canonical Latin / English forms; future i18n is a separate spec.
- **Koog AI guide** — Phase 3 (`05-koog-guide` or similar). The action interface is locked here, but no Koog dependency lands.

**Assumptions:**
- ADR-0005 (`MoonExplorerActions` shape) is the source of truth.
- The 16 curated sites are derived from canonical Moon geography (mare, named craters, Apollo landing sites, plus Chang'e-5 to widen the historical record).
- Compose Multiplatform 1.10.x's `ModalBottomSheet` works on both Android and iOS as it does in `02-moon-renderer-mvp`'s `AboutSheet`.

## References

- ADR-0005 (`MoonExplorerActions` shape — locked here)
- ADR-0006 (selenographic coordinate convention — site coords use this)
- ADR-0007 (SDD framework)
- `ai-docs/initial-idea.md` (UX vision)
- `ai-docs/architecture.md` § Layers
- `./plan.md`
- `./tasks.md`
