# Feature Specification: 05 — Modern Theme

**Branch:** `05-modern-theme`
**Created:** 2026-04-30
**Status:** Draft (pending user ratification)

## Goal (1-line)

Replace default Material 3 theming with a coherent dark visual identity — cool-blue / warm-amber palette, refined typography + shapes, translucent sheet surfaces, accent-driven controls — so the UI reads as a designed product rather than a demo.

## Numbering note

This spec slot was originally `05-polish`, a 7-item skeleton placeholder (idle camera, marker labels, favorites, onboarding, app icon + splash, theme polish, performance pass). **Renamed to `05-modern-theme` and narrowed to the theme item only.** The deferred items each become their own future spec when prioritised; modern-theme is the highest-leverage v1 visual pass.

## User Scenarios

### User Story 1 — Coherent dark visual identity (Priority: P1)

**Why this priority:** Default Material 3 reads as "demo app." A real theme with a custom palette, type scale, and shape tokens makes the app feel intentional. This is the load-bearing change that everything else (sheet refresh, accent application) builds on.

**Independent test:** Launch on a fresh install. The Moon viewport sits on a pure-black background; sheets and controls share a coherent dark palette with cool-blue and warm-amber accents. No surface uses default Material colours.

**Acceptance Scenarios:**
- WHEN the app launches THEN every Compose surface descended from `MoonExplorerScreen` SHALL render under a `MoonExplorerTheme` composable that supplies a custom dark `ColorScheme`, custom `Typography`, and custom `Shapes` to the inner `MaterialTheme`.
- WHEN the user inspects any text element THEN its style SHALL come from the custom typography scale, not Material's defaults.
- WHEN the user inspects sheet shapes THEN their corner radii SHALL reflect the spec'd shape tokens (large radii on sheets, sharper on buttons).

### User Story 2 — Sheets feel modern, not generic (Priority: P1)

**Why this priority:** The three sheets (`AboutSheet`, `SettingsSheet`, `LocationInfoSheet`) are the most-visible UI surface after the Moon viewport itself. Default `ModalBottomSheet` containerColor is opaque grey — it reads as Material-Library-Default. Translucent dark surfaces with custom shapes + a tuned scrim pull the sheets into the app's identity.

**Independent test:** Open About / Settings / Location info. Each sheet has a translucent dark surface (alpha < 1.0), custom corner radii, and a darker scrim than Material's default. The Moon viewport behind the scrim is visibly dimmed; the sheet's edges blend smoothly into the scrim.

**Acceptance Scenarios:**
- WHEN any sheet opens THEN its `containerColor` SHALL be a translucent dark colour from the theme's surface palette (alpha ~0.85), not Material's default.
- WHEN any sheet opens THEN its `scrimColor` SHALL be a tuned darker scrim that obscures the Moon viewport without going pitch-black.
- WHEN any sheet opens THEN its top corners SHALL use the theme's `extraLarge` shape (≥28 dp radius).
- True backdrop blur over the Filament-rendered Moon **is out of scope for v1** — Compose's blur primitives only see Compose-drawn content, not Filament's separate native surface. Translucent surface + tuned scrim simulates the look without claiming frosted-blur. (See `plan.md` § "Glassmorphism — what we can and can't do".)

### User Story 3 — Accent-driven controls (Priority: P1)

**Why this priority:** A theme without applied accents is just colours sitting in a `ColorScheme` object. The user's eye reads accent application — preset buttons in warm amber, highlighted markers in cool blue, the About icon with theme-coloured stroke — as the "feel" of a designed app. The cool-blue / warm-amber split reads as space-themed without screaming about it.

**Independent test:** Tap a sun preset → button uses warm amber fill. Look at the highlighted marker (e.g., after `actions.highlightLocation("tycho")`) → marker uses cool blue. Tap the About icon → it has a refreshed stroke style, not the bare Unicode `ⓘ`.

**Acceptance Scenarios:**
- WHEN the sun-preset row renders THEN each `LightingPresetRow` button SHALL use a warm-amber container colour (the theme's secondary), distinguishing the sun-control surface from the cool-blue rest of the UI.
- WHEN a marker is highlighted (`state.highlightedSiteId == siteId`) THEN its fill SHALL be the theme's primary (cool blue), replacing the previous `MaterialTheme.colorScheme.primary` with the new theme's primary.
- WHEN the user opens any switch (e.g., `Show stars`) THEN its checked thumb/track SHALL use the theme's primary cool blue (Material 3 picks up `colorScheme.primary` automatically).
- WHEN the About icon (top-start) renders THEN its glyph SHALL be a refreshed stroke-style "i" or info icon, not the unstyled Unicode `ⓘ`.

### User Story 4 — Edge-to-edge content + system bars (Priority: P2)

**Why this priority:** The Moon viewport currently respects `statusBarsPadding()` so the icon + search bar sit under the system bars. For a "premium app" feel, we want **the Moon viewport itself to extend behind the system bars** (status + nav), with the UI overlay still padded. Subtle but unmistakably modern.

**Independent test:** Launch on a phone with a notch / dynamic island. The Moon's edge runs right up to the screen's top edge, behind the status bar. The Moon's bottom edge runs to the screen bottom, behind the nav bar / home indicator. UI overlay (icon, search, sun panel) still respects the safe areas.

**Acceptance Scenarios:**
- WHEN the app draws THEN the `MoonViewport` SHALL fill the entire window including the system-bar areas.
- WHEN the user views the system bars THEN they SHALL be transparent (Android: `WindowCompat.setDecorFitsSystemWindows(false)`; iOS: edge-to-edge is the default for SwiftUI hosting CMP).
- WHEN any UI overlay renders (search bar, About icon, sun panel) THEN it SHALL respect `statusBarsPadding()` / `navigationBarsPadding()` as today.

### Edge Cases

- **Light system theme**: Android user has dark-mode disabled. We ship dark-only for v1 — `MoonExplorerTheme` ignores the system theme and forces dark. Future light-theme polish is its own task.
- **Very tall sheets (LocationInfoSheet with long description)**: scroll behaviour stays Material 3 default; content scrolls inside the sheet with the translucent surface clipping at the rounded corners.
- **High-contrast accessibility settings**: Material 3's `colorScheme` is consumed by accessibility tooling. Our cool-blue + amber should pass WCAG AA against the dark surface; if not, document in `results.md` as a follow-up.
- **System font scale (large text)**: typography scale uses `sp`, scales with user accessibility settings. No regression vs current default.
- **Markers near the limb**: alpha-faded markers still receive the cool-blue accent when highlighted; the alpha is multiplicative on the colour. Looks correct (faint cool-blue near the limb, full saturation at centre).
- **Sun preset buttons during animation**: 500 ms cubic-eased animation from `setLightingPreset` doesn't change the *button* visuals; the button stays warm-amber. Only the sun direction animates.

## Requirements

### Functional Requirements

- **FR-001**: A new `MoonExplorerTheme` composable in `commonMain/ui/theme/` SHALL wrap `MaterialTheme(...)` with the spec'd `ColorScheme`, `Typography`, and `Shapes`.
- **FR-002**: `MoonExplorerScreen` SHALL invoke `MoonExplorerTheme { ... }` as its outermost composable, applying the theme to all descendants.
- **FR-003**: The custom `ColorScheme` SHALL be dark-only (no light variant in v1). Key slots: `background = Color.Black`, `surface = ~#0F1218` (translucent applied at sheet level), `primary = ~#5B86FF` (cool blue), `secondary = ~#FFAB40` (warm amber).
- **FR-004**: The custom `Typography` SHALL refine `headlineSmall` (sheet titles), `titleMedium` (section headers), `bodyMedium` (body), and `labelLarge` (button labels). Concrete values in `plan.md` § "Typography".
- **FR-005**: The custom `Shapes` SHALL set `extraLarge` to 32 dp (sheet top corners), `medium` to 16 dp (cards), `small` to 8 dp (buttons).
- **FR-006**: `AboutSheet`, `SettingsSheet`, and `LocationInfoSheet` SHALL pass `containerColor = ColorScheme.surface.copy(alpha = 0.85f)` and a tuned `scrimColor` to their `ModalBottomSheet`.
- **FR-007**: `LightingPresetRow`'s buttons SHALL use a `FilledTonalButton` variant whose container colour is `ColorScheme.secondaryContainer` (warm amber) instead of the default surface tone.
- **FR-008**: `MarkerOverlay`'s highlighted marker fill SHALL pull from `MaterialTheme.colorScheme.primary` (which now resolves to cool blue under the new theme — no code change in MarkerOverlay needed beyond the theme application).
- **FR-009**: The About icon (top-start of `MoonExplorerScreen`) SHALL be replaced with a refreshed stroke-style icon (a Compose `Icon` reading from a vector or font, not the bare Unicode `ⓘ`).
- **FR-010**: The Compose tree SHALL be configured edge-to-edge: `MoonViewport` fills the entire window; UI overlay composables retain their `statusBarsPadding` / `navigationBarsPadding`. On Android this requires `WindowCompat.setDecorFitsSystemWindows(window, false)` in `MainActivity`.

### Key Entities

- **`MoonExplorerTheme`** — Compose composable in `commonMain/ui/theme/`. Wraps `MaterialTheme(colorScheme = …, typography = …, shapes = …, content = …)`.
- **`MoonExplorerColorScheme.kt`** — `colorScheme(): ColorScheme` factory returning the dark scheme. Exposes named `Color` constants (e.g., `MoonBlue`, `SunAmber`, `SpaceBlack`) for direct reference where the slot semantics aren't sufficient.
- **`MoonExplorerTypography.kt`** — `typography(): Typography` factory.
- **`MoonExplorerShapes.kt`** — `shapes(): Shapes` factory.
- **`AboutIcon` Compose** — replaces the bare Unicode `ⓘ` glyph.

## Non-Functional Requirements

- **Visual coherence**: every Compose surface descended from `MoonExplorerScreen` reflects the theme. No surface falls through to default Material 3 colours.
- **Cross-platform parity**: same look on Android + iOS. Compose Multiplatform handles the rendering identically; the only platform divergence is edge-to-edge wiring (Android needs `WindowCompat.setDecorFitsSystemWindows`; iOS draws edge-to-edge by default).
- **Performance**: theme is statically configured at composition; no per-frame cost. Translucent surfaces (alpha < 1) on the sheets add one alpha-blend pass — well within budget.
- **Bundle size**: no new dependencies for v1 (haze deferred per glassmorphism analysis). Theme code is ~250 lines of pure Kotlin.
- **Testability**: `MoonExplorerColorScheme.kt` constants are testable via simple equality checks (verify the actual colour values match the spec). The theme composable doesn't have observable behaviour beyond what its descendants render — UI screenshot tests are the proper verification path; we have the same gap noted in every prior spec.

## Success Criteria

- **SC-001**: App launches with the custom `MoonExplorerTheme` applied; visible UI surfaces use the cool-blue / warm-amber palette.
- **SC-002**: Each of the three sheets (About, Settings, LocationInfo) renders with translucent dark surface + tuned scrim + 32 dp top corners.
- **SC-003**: `LightingPresetRow` buttons use warm-amber container colour; highlighted markers use cool-blue fill.
- **SC-004**: About icon is a refreshed stroke-style icon, not the Unicode `ⓘ`.
- **SC-005**: `MoonViewport` extends edge-to-edge behind the status + nav bars; UI overlay still respects safe areas.
- **SC-006**: `:shared:allTests` passes including new colour-scheme constant tests.

## Assumptions & Out of Scope

**Out of scope:**
- **Light theme** — defer. The app's context (deep-space backdrop, dark renderer) makes dark-only the natural fit for v1.
- **True frosted-glass blur over the Moon viewport** — Filament renders into a separate native surface (Android `SurfaceView`, iOS `CAMetalLayer`) that Compose's blur libraries can't sample. Real backdrop blur needs per-platform native overlays or offscreen-render-then-composite — both architecturally significant. **Translucent solid surfaces + tuned scrim** simulates the look for v1; documented in `plan.md` § "Glassmorphism".
- **Custom typeface** — system fonts only for v1. Adding a custom font is a separate polish task with its own asset pipeline.
- **App icon + splash screen** — platform-specific design tasks; deferred from the original 05-polish skeleton, will be its own future spec.
- **Idle camera drift / marker labels / favorites / onboarding / performance pass** — also from the 05-polish skeleton, also deferred. Each becomes its own future spec.
- **Animation polish** (entrance / exit transitions on sheets, micro-interactions) — Material 3 defaults are fine for v1.
- **Accessibility audit** (WCAG AA contrast verification, TalkBack / VoiceOver labelling) — high-level: dark scheme + cool-blue / warm-amber should pass against `Color.Black`. Formal audit deferred to a future spec.
- **Tablet / large-screen layout** — `MoonExplorerScreen`'s adaptive behaviour stays as-is; this spec changes only theming.

**Assumptions:**
- Compose Multiplatform's `MaterialTheme(colorScheme, typography, shapes)` API is stable on both platforms and the cross-platform rendering produces identical visuals.
- The cool-blue + warm-amber palette passes WCAG AA contrast against the dark surface; verified during T530.
- The current `MoonExplorerScreen` composable hierarchy is the right place to apply the theme (one wrap covers all UI). If a future feature splits the UI across multiple top-level composables, the theme application would need to be per-root.
- ADR-0011 (PNG bundling rule) does not apply here — the theme adds no new asset files (icons can ship as Compose `ImageVector` declarations, no PNG required).

## References

- `ai-docs/initial-idea.md` § "Phase 2: Polish" — the original prompt for visual polish work
- `ai-docs/specs/01-app-shell/spec.md` — `AboutSheet` / `SettingsSheet` / `LocationInfoSheet` predecessors
- `ai-docs/specs/03-sites-and-flyto/spec.md` — `MarkerOverlay` predecessor; highlighted-marker accent applied here
- `ai-docs/specs/04-sun-control/spec.md` — `SunPanel` / `LightingPresetRow` predecessor; warm-amber accent applied here
- `ai-docs/specs/07-celestial-background/spec.md` — `SettingsSheet` toggles predecessor; new theme picks up the toggle styling
- [Material 3 ColorScheme](https://m3.material.io/styles/color/system/overview) — the slot model we extend
- [Compose Multiplatform Material 3 reference](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-resources-overview.html)
- `./plan.md`
- `./tasks.md`
