# Implementation Plan: 05 — Modern Theme

**Branch:** `05-modern-theme`
**Created:** 2026-04-30
**Status:** Draft (pending user ratification)

## Architecture flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  MoonExplorerScreen (commonMain)                                    │
│  └── MoonExplorerTheme {                  ← NEW outermost wrapper   │
│         MaterialTheme(                                              │
│             colorScheme = MoonExplorerColorScheme.dark(),           │
│             typography  = MoonExplorerTypography(),                 │
│             shapes      = MoonExplorerShapes(),                     │
│         ) { … existing content … }                                  │
│      }                                                              │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ Theme propagates via CompositionLocal
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Themed composables (existing)                                      │
│  • AboutSheet / SettingsSheet / LocationInfoSheet                   │
│      Pass containerColor = colorScheme.surface.copy(alpha = 0.85f)  │
│      Pass scrimColor    = colorScheme.scrim.copy(alpha = 0.6f)      │
│      Pass shape         = shapes.extraLarge                         │
│  • LightingPresetRow                                                │
│      FilledTonalButton with                                         │
│          containerColor = colorScheme.secondaryContainer (amber)    │
│  • MarkerOverlay                                                    │
│      Highlighted dot → colorScheme.primary (cool blue)              │
│  • SearchBar                                                        │
│      Material 3 SearchBar picks up colorScheme automatically;       │
│      no per-call override needed                                    │
│  • AboutIcon (NEW)                                                  │
│      Compose Icon with stroke-style "info" glyph instead of "ⓘ"    │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ + edge-to-edge wiring (per-platform)
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Edge-to-edge (FR-010)                                              │
│  • Android: MainActivity calls                                      │
│      WindowCompat.setDecorFitsSystemWindows(window, false)          │
│      window.statusBarColor = Color.TRANSPARENT                      │
│      window.navigationBarColor = Color.TRANSPARENT                  │
│  • iOS: edge-to-edge is the default for SwiftUI; verify Compose-iOS │
│    hosting view doesn't re-clip                                     │
└─────────────────────────────────────────────────────────────────────┘
```

The theme is one composable wrap; everything inside `MoonExplorerScreen` inherits via Compose's `CompositionLocal` mechanism. No per-call colour overrides except where the slot semantics need explicit nudging (sheet `containerColor` for translucency, preset buttons for amber accent).

## Glassmorphism — what we can and can't do

The user originally asked for "glassmorphism" — frosted-glass blur on the sheets. The honest reality:

**Compose's blur primitives only see Compose-drawn content.** The Moon viewport in this app is rendered by Filament into a *separate native surface*: Android `SurfaceView`, iOS `CAMetalLayer`. Compose's drawing pipeline composites *on top of* that native surface at the system level, but cannot read its pixels. Both `Modifier.blur()` (Compose 1.7+) and the `dev.chrisbanes.haze` library work by capturing Compose-drawn content into a `RenderNode` (Android) / `Skia surface` (iOS-Compose) and applying a blur shader — neither sees Filament's pixels.

True backdrop-blur over the Moon would need either:
- **Per-platform native overlays** — `UIVisualEffectView` overlay on iOS; on Android, capture the `SurfaceView` to a bitmap each frame and feed to a blur shader (expensive). Significant per-platform work.
- **Render the Moon offscreen and composite in Compose** — fundamental restructuring of the renderer-host pattern (ADR-0003); breaks the pull-not-push model.

**For v1 we ship the visual feel without true blur:**
- Translucent dark surfaces (`Color.copy(alpha = 0.85f)`) on sheet containers
- Tuned `scrimColor` darker than Material's default (alpha ~0.6 vs default ~0.32) to obscure the Moon
- Theme-coloured outline / shadow on sheets to suggest depth

The result reads as "premium dark glass" without claiming to be frosted-blur. True blur is a polish task; if anyone asks why the sheets aren't blurred, the answer is "Filament + Compose composite at the system level, not in Compose's pipeline." Documented in `spec.md` § "Out of scope".

## ADR considerations

No new ADRs. The theme work fits cleanly under:
- Existing Compose Multiplatform foundation (no new deps for v1)
- Existing `MoonExplorerScreen` composable hierarchy (one outermost wrap covers all)

If a future polish task adds true backdrop blur, that probably *does* need an ADR (per-platform native overlays + offscreen-render rework are architectural). Out of scope here.

## Components

### `MoonExplorerColorScheme.kt` (commonMain/ui/theme/)

Exposes named colour constants + a `dark(): ColorScheme` factory. Concrete v1 values (tunable in QA):

```kotlin
object MoonColors {
    val SpaceBlack = Color(0xFF000000)         // background — matches renderer clear color
    val DeepBlueGray = Color(0xFF0F1218)       // surface (opaque base; sheets use translucent variant)
    val DeepBlueGray80 = Color(0xD90F1218)     // surface translucent (alpha ≈ 0.85)
    val MoonBlue = Color(0xFF5B86FF)           // primary — cool electric blue
    val MoonBlueDim = Color(0xFF2E4485)        // primary container — same hue, dimmer
    val SunAmber = Color(0xFFFFAB40)           // secondary — warm sun-like amber
    val SunAmberDim = Color(0xFF8E5A1F)        // secondary container — dimmer
    val Bone = Color(0xFFE8EEF5)               // onSurface, onBackground — high-emphasis text
    val BoneDim = Color(0xB3E8EEF5)            // medium-emphasis (alpha 0.7)
    val Outline = Color(0xFF3A3F4A)            // subtle surface outlines
    val Scrim = Color(0xCC000000)              // scrim — alpha 0.8 for sheet backdrops
}

fun moonExplorerDarkScheme(): ColorScheme = darkColorScheme(
    primary = MoonColors.MoonBlue,
    onPrimary = MoonColors.SpaceBlack,
    primaryContainer = MoonColors.MoonBlueDim,
    onPrimaryContainer = MoonColors.Bone,
    secondary = MoonColors.SunAmber,
    onSecondary = MoonColors.SpaceBlack,
    secondaryContainer = MoonColors.SunAmberDim,
    onSecondaryContainer = MoonColors.Bone,
    background = MoonColors.SpaceBlack,
    onBackground = MoonColors.Bone,
    surface = MoonColors.DeepBlueGray,
    onSurface = MoonColors.Bone,
    surfaceVariant = MoonColors.DeepBlueGray,
    onSurfaceVariant = MoonColors.BoneDim,
    outline = MoonColors.Outline,
    scrim = MoonColors.Scrim,
    // surfaceContainerHigh used by ModalBottomSheet's default surface — keep
    // consistent with surface
    surfaceContainer = MoonColors.DeepBlueGray,
    surfaceContainerHigh = MoonColors.DeepBlueGray,
    surfaceContainerHighest = MoonColors.DeepBlueGray,
)
```

WCAG AA verification: `MoonBlue` on `SpaceBlack` (#5B86FF on #000000) gives ~7.4:1 contrast — passes. `SunAmber` on `SpaceBlack` (#FFAB40 on #000000) gives ~10.6:1 — passes. `Bone` on `DeepBlueGray` (#E8EEF5 on #0F1218) gives ~16:1 — passes generously.

### `MoonExplorerTypography.kt`

Refined Material 3 type scale. Base on `Typography()` defaults; override the styles the app actually uses:

```kotlin
fun moonExplorerTypography(): Typography {
    val base = Typography()
    return base.copy(
        // Sheet titles ("Moon Explorer", "Settings", site name)
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        ),
        // Section headers ("Lunar surface imagery", "Celestial background")
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.Medium,
        ),
        // Body copy in sheets
        bodyMedium = base.bodyMedium.copy(
            lineHeight = 22.sp,
        ),
        // Button labels (preset buttons, search row)
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        ),
    )
}
```

System fonts only — no custom typeface in v1.

### `MoonExplorerShapes.kt`

```kotlin
fun moonExplorerShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),  // chips
    small = RoundedCornerShape(8.dp),       // buttons
    medium = RoundedCornerShape(16.dp),     // cards
    large = RoundedCornerShape(24.dp),      // dialog containers
    // Sheets — bigger top corners than Material default (28 dp) for a softer feel.
    extraLarge = RoundedCornerShape(
        topStart = 32.dp,
        topEnd = 32.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    ),
)
```

### `MoonExplorerTheme.kt`

```kotlin
@Composable
fun MoonExplorerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = moonExplorerDarkScheme(),
        typography  = moonExplorerTypography(),
        shapes      = moonExplorerShapes(),
        content = content,
    )
}
```

### `MoonExplorerScreen` rewire

Wrap the existing root `Box` in `MoonExplorerTheme`:

```kotlin
@Composable
fun MoonExplorerScreen(...) {
    MoonExplorerTheme {
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            // … existing content unchanged …
        }
    }
}
```

### Sheet container colours

Each `ModalBottomSheet` call site gets two new args:

```kotlin
ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    scrimColor = MaterialTheme.colorScheme.scrim,
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier,
) { … }
```

The Bottom-sheet content's `Column` already has `padding(horizontal = 24.dp)` — keep.

### `LightingPresetRow` amber buttons

Replace the current `FilledTonalButton` with a colour-overridden variant:

```kotlin
@Composable
private fun PresetButton(
    preset: LightingPreset,
    label: String,
    onTap: (LightingPreset) -> Unit,
) {
    FilledTonalButton(
        onClick = { onTap(preset) },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier
            .width(PRESET_BUTTON_WIDTH_DP)
            .height(PRESET_BUTTON_HEIGHT_DP),
    ) {
        Text(label)
    }
}
```

### `MarkerOverlay` highlighted accent

Already reads from `MaterialTheme.colorScheme.primary`. Under the new theme, that resolves to `MoonBlue`. **Zero changes** to `MarkerOverlay.kt` — pure-theme effect.

### Refreshed About icon

Replace the bare `ⓘ` Unicode glyph in `MoonExplorerScreen`:

```kotlin
// Before:
IconButton(onClick = { aboutSheetVisible = true }, …) {
    Text(text = "ⓘ", color = Color.White, fontSize = 28.sp, …)
}

// After:
IconButton(onClick = { aboutSheetVisible = true }, …) {
    Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = "About",
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.size(28.dp),
    )
}
```

`Icons.Outlined.Info` is from `androidx.compose.material.icons.outlined`. Adds the dependency `androidx.compose.material:material-icons-extended` if not already present (it usually is in CMP).

### Edge-to-edge wiring (per-platform)

**Android (`MainActivity.kt`):**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    setContent { MoonExplorerScreen(...) }
}
```

**iOS:** edge-to-edge is the default for SwiftUI hosting CMP. Confirm `ContentView.swift` / `iOSApp.swift` don't add `safeAreaInset` modifiers that re-clip.

The existing `statusBarsPadding()` / `navigationBarsPadding()` calls on the UI overlay composables (About icon, search bar, sun panel) keep them out of the system-bar areas; the Moon viewport (`MoonViewport`) fills the entire window.

## Project structure delta

```
shared/src/commonMain/kotlin/org/jetbrains/moonexplorer/
└── ui/
    ├── theme/                        (NEW package)
    │   ├── MoonExplorerColorScheme.kt    (NEW — colours + scheme factory)
    │   ├── MoonExplorerTypography.kt     (NEW)
    │   ├── MoonExplorerShapes.kt         (NEW)
    │   └── MoonExplorerTheme.kt          (NEW — wrapper)
    ├── AboutSheet.kt                 (containerColor + scrimColor + shape passed)
    ├── SettingsSheet.kt              (same)
    ├── LocationInfoSheet.kt          (same)
    ├── LightingPresetRow.kt          (button colours overridden)
    └── MoonExplorerScreen.kt         (wrap in MoonExplorerTheme; refresh About icon)

androidApp/src/main/java/.../MainActivity.kt
                                      (edge-to-edge wiring)

shared/src/commonTest/kotlin/org/jetbrains/moonexplorer/
└── ui/theme/                         (NEW)
    └── MoonExplorerColorSchemeTest.kt    (NEW — verify colour values)
```

No platform-specific Kotlin/Native (iOS) changes beyond confirming SwiftUI edge-to-edge default. No new Gradle dependencies for v1.

## Error handling

| Scenario | Handling |
|---|---|
| User has system high-contrast accessibility on | Material 3's accessibility tooling reads `colorScheme` and applies platform high-contrast adjustments where supported. Our scheme already passes WCAG AA; behaviour should be reasonable. |
| Locale with right-to-left text | Theme is direction-agnostic. Compose's RTL handling is unchanged. |
| Light system theme + user expects light | We force dark; if a user complains, light theme is a future spec. Document in results.md. |
| Sheet content too tall for translucent surface (gradient through scrim) | Scroll handles long content; surface stays alpha 0.85 throughout. Acceptable. |
| Compose Multiplatform version bump breaks `darkColorScheme` slot names | Slot names are stable since Material 3 1.0; low risk. |

## Testing strategy

### `commonTest`

- **`MoonExplorerColorSchemeTest`** (T505):
  - `dark_primaryIsCoolBlue`: verify `moonExplorerDarkScheme().primary == MoonColors.MoonBlue`.
  - `dark_secondaryIsWarmAmber`: same shape for secondary.
  - `dark_backgroundIsSpaceBlack`: `background == Color.Black`.
  - `dark_surfaceContainerStaysSurface`: `surfaceContainer / surfaceContainerHigh / surfaceContainerHighest` all equal `surface` (so ModalBottomSheet picks the right colour by default).
  - `MoonColors_passWcagAa`: a quick contrast-ratio spot-check on `primary` vs `background` and `secondary` vs `background`.

### Out of scope for tests

- UI screenshot tests (Compose-UI-test gap noted in every prior spec).
- Visual diff between Android JVM + iOS sim (Compose Multiplatform handles the rendering identically; mismatch would indicate a CMP regression, not our code).
- Edge-to-edge behaviour (per-platform; visible on hardware only).

## Complexity tracking

| Decision | Why this complexity is in scope |
|---|---|
| Custom `Typography` rather than defaults | Default Material 3 type scale is fine, but the app's content (sheet titles, body, button labels) reads as generic. Refining the four styles the app actually uses is a small change with outsized visual impact. |
| Translucent solid sheets instead of true blur | Filament-Compose composite limits true backdrop blur (see § Glassmorphism). Translucent solid + tuned scrim is the visually-equivalent v1 path. |
| Cool-blue + warm-amber two-accent palette | Single-accent (e.g., cyan only) reads more sci-fi; two-accent reads more "designed app." The cool/warm split lets sun-control surfaces feel visually distinct from the rest of the UI without screaming about it. |
| Edge-to-edge content (FR-010) | Modern phone UI standard. Without it the app reads as "older Android" — the Moon viewport's edges don't go to the screen edges. |
| Refresh About icon, not Search icon | The Unicode `ⓘ` is a known eyesore. Search bar's icon is already a Compose-rendered `Icon`; no refresh needed. Scope minimization. |
| No new ADR | Theme work doesn't add architectural commitments. The Filament-Compose composite limit (no backdrop blur) is a *constraint we discovered*, not a new decision. |

## Risks / open questions

1. **Compose Multiplatform's `surfaceContainer*` slot behaviour.** `ModalBottomSheet` may pick a different slot than `surface` for its default container colour depending on the M3 version. Verified during T510 — if the sheet uses a slot that we set to `DeepBlueGray`, behaviour matches plan; if it picks a slot we left at default, override via `containerColor` param.
2. **iOS edge-to-edge regression risk.** SwiftUI's hosting view for Compose-iOS *should* be edge-to-edge by default. If the Compose-iOS hosting layer adds a `safeAreaInset`, we'd need to override at the SwiftUI level. Hardware verification only.
3. **WCAG AA borderline cases.** Spot-check with a real contrast tool during T530; if any colour pair fails, nudge the value (e.g., bump `MoonBlue`'s lightness) and document.
4. **Dark-only forcing on light-mode users.** Some users prefer light theme. We're explicit-out-of-scope; if pushback, light variant is a one-task follow-up (`darkColorScheme()` → `if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()`).
5. **Refreshed About icon visibility.** `Icons.Outlined.Info` at 28 dp on a black background should be plainly visible. If hardware testing shows it's too subtle, increase tint contrast or add a subtle drop shadow / outline.
6. **Translucent sheets revealing the Moon.** With sheets at alpha 0.85 + tuned scrim at alpha 0.6, the Moon should read as a faint silhouette behind the sheet. If it's too distracting, increase scrim alpha to 0.7 or 0.8. Hardware tunable.

## References

- ADR-0003 (Renderer host pattern — pull-not-push state; constrains backdrop-blur architecture)
- `ai-docs/initial-idea.md` § "Phase 2: Polish" — original prompt for visual polish
- `ai-docs/specs/01-app-shell/plan.md` — `AboutSheet` / `SettingsSheet` / `LocationInfoSheet` predecessors; theme application here doesn't change their structure
- `ai-docs/specs/04-sun-control/plan.md` — `SunPanel` / `LightingPresetRow`; preset button colour override here
- `ai-docs/specs/07-celestial-background/plan.md` — `SettingsSheet` toggle predecessor; new theme picks up Switch styling automatically
- [Material 3 ColorScheme — slot mapping](https://m3.material.io/styles/color/system/overview)
- [WCAG 2.1 AA contrast minimums](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html) — 4.5:1 for normal text, 3:1 for large
- `./spec.md` — acceptance criteria
- `./tasks.md` — execution plan
