# ADR-0008: Workaround for Filament's stale CocoaPods trunk — consume podspec by raw URL

**Status**: Accepted
**Date**: 2026-04-28
**Refines**: ADR-0002 §"Subspec selection (locked for Phase 0/1)"

## Context

Per ADR-0002 we consume Filament on iOS via CocoaPods: `pod 'Filament/filament', '~> 1.71.1'` and `pod 'Filament/ktxreader', '~> 1.71.1'`. Running `pod install` against the locked Podfile failed:

```
[!] CocoaPods could not find compatible versions for pod "Filament/filament":
  In Podfile:
    Filament/filament (~> 1.71.1)

None of your spec sources contain a spec satisfying the dependency: `Filament/filament (~> 1.71.1)`.
```

Investigation:

1. `cocoapods.org/pods/Filament` exists (HTTP 200). The pod is registered on trunk.
2. The CocoaPods CDN versions index `https://cdn.cocoapods.org/all_pods_versions_9_6_d.txt` lists Filament versions through **1.69.3** only. **1.70.x and 1.71.x are missing** despite being released on GitHub with podspec files at `ios/CocoaPods/Filament.podspec`. Probing per-version JSON specs on the CDN confirms `1.65.0` exists, `1.69.3` exists, `1.70.0` and `1.71.1` do not.
3. Maven Central has `com.google.android.filament:filament-android:1.71.1`. Android publishing is in lockstep with releases; CocoaPods publishing has fallen behind.
4. The raw podspec file at `https://raw.githubusercontent.com/google/filament/v1.71.1/ios/CocoaPods/Filament.podspec` is accessible (HTTP 200) and points to `:http => "https://github.com/google/filament/releases/download/v1.71.1/filament-v1.71.1-ios.tgz"` for the actual archives.

So Filament's iOS CocoaPods publishing has stopped at 1.69.3 (most recent push date unknown but well before 1.70.0). We have three options:

(i) **Pin to 1.69.3** (last on trunk). Simplest Podfile (`pod 'Filament/...', '~> 1.69.3'`). Forces a regression on the Android Maven dep too (currently 1.71.1) since ADR-0002's "single shader pipeline" claim relies on matched versions. Two minors of bug fixes and Filament work lost.

(ii) **Stay on 1.71.1, reference podspec via `:podspec => '<raw github URL>'`**. Same artifact as upstream; one-time non-default Podfile syntax. Maven side stays at 1.71.1.

(iii) **Build Filament from source** and vendor a custom local podspec pointing at the locally-built archives. 30–60 min one-time setup, ongoing maintenance burden, no functional advantage over (ii).

## Decision

Use **(ii)** — keep Filament `1.71.1`, point `:podspec` at the raw podspec URL at the immutable `v1.71.1` git tag.

```ruby
FILAMENT_PODSPEC = 'https://raw.githubusercontent.com/google/filament/v1.71.1/ios/CocoaPods/Filament.podspec'

target 'iosApp' do
  use_frameworks!
  pod 'Filament/filament',  :podspec => FILAMENT_PODSPEC
  pod 'Filament/ktxreader', :podspec => FILAMENT_PODSPEC
end
```

## Rationale

- Keeps version parity between Android (Maven `1.71.1`) and iOS, which is a load-bearing assumption for ADR-0002's claim of a single shared shader pipeline (`matc -a all -p mobile` produces a `.filamat` valid for the GLES/Vulkan and Metal backends of the same Filament version).
- The `:podspec => '<URL>'` form is a documented CocoaPods feature (see [CocoaPods Podfile syntax](https://guides.cocoapods.org/syntax/podfile.html#pod)) — not a hack.
- Tag-based raw URLs (`/v1.71.1/`) are immutable git refs. Filament can't break us by editing the tag without rewriting their history.
- The two `pod` lines reference the same podspec URL; CocoaPods reads the podspec once and resolves both subspecs from it.
- If/when Filament resumes trunk publishing, the revert is one line per pod entry plus removing the URL constant.

## Alternatives rejected

- **(i) Pin to 1.69.3**: regresses both Android and iOS by two minors. The Phase 1 work (`./gradlew :androidApp:assembleDebug` with Filament on classpath) was already verified at 1.71.1 — downgrading invalidates that verification. Not worth the simpler Podfile.
- **(iii) Build from source**: invasive setup, no benefit until/unless Filament drops their CocoaPods support entirely.

## Consequences

- `iosApp/Podfile` uses the `:podspec => '<URL>'` form with a `FILAMENT_PODSPEC` constant. The Podfile comment block explains why and points here.
- **A future Filament version bump now requires three coordinated edits**: `gradle/libs.versions.toml` (`filament = "x.y.z"`), `iosApp/Podfile` (`FILAMENT_PODSPEC` URL tag), and a re-run of `./gradlew :shared:downloadFilamentTools` + `cd iosApp && pod install`. ADR-0002's "Update policy" implicitly assumed two edits (Maven + Podfile semver); this ADR adds the URL update.
- ADR-0002's "Subspec selection (locked)" snippet showing `'~> 1.71.1'` is now mechanically stale. This ADR is the source of truth for the actual Podfile lines; ADR-0002 still governs the *which subspecs* decision (`Filament/filament` + `Filament/ktxreader`).
- If Filament eventually publishes 1.71.1 (or 1.71.2+) to trunk, file a new ADR-0009 superseding this one and revert the Podfile to plain semver constraints.

## References

- ADR-0002 §"Subspec selection (locked for Phase 0/1)"
- [CocoaPods Podfile syntax — pod options](https://guides.cocoapods.org/syntax/podfile.html#pod)
- [Filament releases](https://github.com/google/filament/releases)
- Filament trunk version index: `https://cdn.cocoapods.org/all_pods_versions_9_6_d.txt`
- Raw podspec at v1.71.1: `https://raw.githubusercontent.com/google/filament/v1.71.1/ios/CocoaPods/Filament.podspec`
