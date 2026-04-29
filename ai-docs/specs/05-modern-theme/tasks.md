# Tasks: 05 — Modern Theme

## Format: `[ID] [P?] [US?] Description`

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.
Acceptance criteria for each user story live in `spec.md`.

## Path conventions

All paths relative to `MoonExplorer/` repo root. Task IDs are namespaced **T500+** to avoid collision with `00-renderer-spike` (T001–T093), `02-moon-renderer-mvp` (T100–T145), `01-app-shell` (T200–T232), `03-sites-and-flyto` (T301–T342), `04-sun-control` (T410–T452), and `07-celestial-background` (T701–T743). The 600 range stays reserved for the existing `06-koog-agent` skeleton placeholder.

---

## Phase 1: Theme foundation

- [ ] **T501** [P] [US1] Implement `ui/theme/MoonExplorerColorScheme.kt`
  - `object MoonColors` exposes named colour constants per `plan.md` § "MoonExplorerColorScheme.kt": `SpaceBlack`, `DeepBlueGray`, `MoonBlue`, `SunAmber`, `Bone`, etc.
  - `fun moonExplorerDarkScheme(): ColorScheme` returns a Material 3 `darkColorScheme(...)` populated with the spec'd values across all relevant slots (primary, secondary, background, surface, surface containers, scrim, outline, etc.).
  - `surfaceContainer` / `surfaceContainerHigh` / `surfaceContainerHighest` all map to `DeepBlueGray` so `ModalBottomSheet` picks the right colour by default.
  - _Requirements: FR-003_

- [ ] **T502** [P] [US1] Implement `ui/theme/MoonExplorerTypography.kt`
  - `fun moonExplorerTypography(): Typography` starts from Material 3's `Typography()` defaults and overrides four styles per `plan.md` § "Typography": `headlineSmall` (sheet titles), `titleMedium` (section headers), `bodyMedium` (body), `labelLarge` (button labels).
  - System fonts only — no custom typeface in v1.
  - _Requirements: FR-004_

- [ ] **T503** [P] [US1] Implement `ui/theme/MoonExplorerShapes.kt`
  - `fun moonExplorerShapes(): Shapes` with `extraSmall = 4.dp`, `small = 8.dp`, `medium = 16.dp`, `large = 24.dp`, `extraLarge = 32.dp` (top corners only — `bottomStart = bottomEnd = 0.dp` for sheets).
  - _Requirements: FR-005_

- [ ] **T504** [US1] Implement `ui/theme/MoonExplorerTheme.kt`
  - `@Composable fun MoonExplorerTheme(content: @Composable () -> Unit)` wraps `MaterialTheme(colorScheme = moonExplorerDarkScheme(), typography = moonExplorerTypography(), shapes = moonExplorerShapes()) { content() }`.
  - _Requirements: FR-001_

- [ ] **T505** [P] [US1] `commonTest` — `MoonExplorerColorSchemeTest` (5 cases)
  - `dark_primaryIsCoolBlue`: `moonExplorerDarkScheme().primary == MoonColors.MoonBlue`.
  - `dark_secondaryIsWarmAmber`: same shape for `.secondary == SunAmber`.
  - `dark_backgroundIsSpaceBlack`: `.background == Color.Black`.
  - `dark_surfaceContainerStaysSurface`: `surfaceContainer == surfaceContainerHigh == surfaceContainerHighest == surface == DeepBlueGray`.
  - `MoonColors_primaryPassesWcagAa`: contrast-ratio of `MoonBlue` on `SpaceBlack` ≥ 4.5:1.
  - _Requirements: SC-006_

- [ ] **T506** [US1] Wrap `MoonExplorerScreen`'s root `Box` in `MoonExplorerTheme { ... }`
  - The new wrap is the outermost composable in `MoonExplorerScreen`; everything inside inherits via Compose's `CompositionLocal`.
  - _Requirements: FR-002_

**Checkpoint**: launching the app on simulator shows the same content as before but with a new palette — the existing `MaterialTheme.colorScheme.*` references in `MarkerOverlay`, `LightingPresetRow`, `SettingsSheet`'s switches, etc. now resolve to the new colours automatically.

---

## Phase 2: Sheet refresh

- [ ] **T510** [US2] Refresh `AboutSheet`
  - Pass `containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)`, `scrimColor = MaterialTheme.colorScheme.scrim`, and `shape = MaterialTheme.shapes.extraLarge` to `ModalBottomSheet`.
  - _Requirements: FR-006_

- [ ] **T511** [US2] Refresh `SettingsSheet`
  - Same three parameters passed to `ModalBottomSheet` as T510.
  - _Requirements: FR-006_

- [ ] **T512** [US2] Refresh `LocationInfoSheet`
  - Same three parameters passed to `ModalBottomSheet` as T510.
  - _Requirements: FR-006_

**Checkpoint**: opening any sheet shows a translucent dark surface with 32 dp top corners; the Moon viewport behind is visible but darker via the tuned scrim.

---

## Phase 3: Accent application

- [ ] **T520** [US3] `LightingPresetRow` buttons → warm amber
  - Override `FilledTonalButton`'s `colors` with `containerColor = MaterialTheme.colorScheme.secondaryContainer` and `contentColor = MaterialTheme.colorScheme.onSecondaryContainer`. Per-button via the existing `PresetButton` private composable in `LightingPresetRow.kt`.
  - _Requirements: FR-007_

- [ ] **T521** [P] [US3] `MarkerOverlay` highlighted accent — verify (no code change)
  - `MarkerDot` already reads `MaterialTheme.colorScheme.primary` for the highlighted fill. Under the new theme this auto-resolves to `MoonBlue` (cool blue). Verify behaviour with a quick on-simulator launch + `actions.highlightLocation("tycho")` from a debug menu (or via the test path).
  - _Requirements: FR-008_

- [ ] **T522** [US3] Refresh About icon in `MoonExplorerScreen`
  - Replace the `Text(text = "ⓘ", ...)` glyph with `Icon(imageVector = Icons.Outlined.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))`. Adds an import from `androidx.compose.material.icons.outlined`.
  - _Requirements: FR-009_

**Checkpoint**: tap a sun preset → button is amber; highlight a marker → marker is cool blue; About icon is a stroke-style "i" instead of a Unicode glyph.

---

## Phase 4: Edge-to-edge

- [ ] **T530** [US4] [Android] Edge-to-edge wiring in `MainActivity.kt`
  - Add `WindowCompat.setDecorFitsSystemWindows(window, false)` in `onCreate` before `setContent { ... }`.
  - Set `window.statusBarColor = android.graphics.Color.TRANSPARENT` and `window.navigationBarColor = android.graphics.Color.TRANSPARENT`.
  - _Requirements: FR-010_

- [ ] **T531** [US4] [iOS] Verify SwiftUI edge-to-edge default
  - Confirm `ContentView.swift` / `iOSApp.swift` don't add `safeAreaInset` or other modifiers that re-clip the Compose-iOS hosting view.
  - If they do, override at the SwiftUI level (`.ignoresSafeArea()` on the appropriate container).
  - _Requirements: FR-010_

**Checkpoint**: launching on hardware shows the Moon viewport extending behind the status / nav bars; UI overlay (icons, search, sun panel) still respects safe areas.

---

## Phase Final: Polish + tests + docs

- [ ] **T540** Run `:shared:testAndroidHostTest :shared:iosSimulatorArm64Test` — all suites green
  - Carryover (103 from 07-celestial-background's Phase Final) + new `MoonExplorerColorSchemeTest` (~5 cases). Target ~108 tests across 12 suites.
  - _Requirements: SC-006_

- [ ] **T541** [P] Write `ai-docs/specs/05-modern-theme/results.md`
  - Status by phase; user-confirmed items vs pending hardware confirmation (translucent sheet feel, scrim balance, edge-to-edge on a notched device, About icon visibility).
  - Test counts table.
  - Deviations log: dark-only theme (light deferred); true backdrop blur deferred (Filament-Compose composite constraint); custom typeface deferred; app icon + splash deferred to a separate spec.
  - WCAG AA spot-check results (primary on background, secondary on background).
  - _Requirements: agent-runbook.md_

- [ ] **T542** [P] Cross-reference notes in 01 / 04 / 07 results
  - `01-app-shell/results.md` — note that the SettingsSheet's "first real toggles" from 07 + the modern-theme styling from 05 close out the placeholder sheet's evolution.
  - `04-sun-control/results.md` § References — add forward-link; preset buttons get the warm-amber accent in 05.
  - `07-celestial-background/results.md` § References — add forward-link; SettingsSheet's translucent surface lands in 05.
  - _Requirements: agent-runbook.md_

**Final Checkpoint**: all four user stories' acceptance criteria pass on real devices; theme is coherent across surfaces; sheets feel modern; preset buttons amber; markers cool blue; About icon refreshed; viewport edge-to-edge.

---

## Dependencies & Execution Order

| From → To | Why |
|---|---|
| T501 + T502 + T503 → T504 | Theme wrapper composes the three factory functions |
| T501 → T505 | Tests reference the colour constants |
| T504 → T506 | Screen wrap needs the theme composable to exist |
| T506 → T510 / T511 / T512 | Sheets read theme via `MaterialTheme.colorScheme` — needs the theme applied |
| T506 → T520 | Same — preset buttons read theme colours |
| T506 → T521 | Same — markers read theme colours |
| T506 → T522 | About icon reads theme tint |
| Phases 1–4 → Phase Final | Everything wired before final tests + results |

## Parallel Examples

- **Phase 1**: T501, T502, T503, T505 are on different files (Color / Typography / Shape / Color tests); all parallel-safe. T504 + T506 sequential after.
- **Phase 2**: T510, T511, T512 each touch one sheet file; safe in parallel.
- **Phase 3**: T520 + T521 + T522 touch different files; safe in parallel.
- **Phase 4**: T530 (Android) + T531 (iOS) on different platforms; safe in parallel.
- **Phase Final**: T541 + T542 are independent doc edits.

## Implementation Strategy

- **Two PRs**, mirroring the prior-spec pattern:
  - **PR 1**: Phase 1 + Phase 2 — theme foundation + sheet refresh. The "biggest visual lift" arrives here.
  - **PR 2**: Phase 3 + Phase 4 + Phase Final — accent application + edge-to-edge + results.
- **No new Gradle deps** — Compose Material 3 + Material Icons Extended are already on classpath. WCAG verification is a hand-spot-check, not a library.

## Notes

- **No new ADRs.** The theme work fits cleanly under existing Compose Multiplatform foundation. The "no true backdrop blur" finding is a *constraint we surfaced*, not a decision worth its own ADR.
- **Cool-blue + warm-amber** is the v1 accent palette. If on-device testing shows the cool/warm split feels too contrasty, the second-best fallback is monochromatic silver-on-black with a single amber highlight; one-line edit in `MoonExplorerColorScheme.kt`.
- **Translucent sheet alpha (0.88) + scrim alpha (0.8)** are the starting values. Hardware tunable in T541.
- **Original 05-polish skeleton items** (idle camera, marker labels, favorites, onboarding, app icon + splash, performance pass) were narrowed away from this spec. Each is a future spec when prioritised; modern-theme is the highest-leverage v1 visual pass.
- **Light theme** is intentionally out of scope. If anyone wants it, the change is `if (isSystemInDarkTheme()) moonExplorerDarkScheme() else moonExplorerLightScheme()` plus authoring the light scheme — one-task follow-up.
- **True backdrop blur over the Moon** is also out of scope; Filament + Compose composite at the system level (see `plan.md` § Glassmorphism). Translucent solid + tuned scrim simulates the look for v1.
