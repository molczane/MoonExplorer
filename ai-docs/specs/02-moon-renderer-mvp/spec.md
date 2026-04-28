# Feature Specification: 02 — Moon Renderer MVP

**Branch:** `02-moon-renderer-mvp` | **Created:** 2026-04-28 | **Status:** Skeleton (placeholder)

## Goal (1-line)

Replace the Phase 0 placeholder textures with the real NASA SVS CGI Moon Kit assets. Bake the normal map. Wire CDN streaming for the HD tier. Validate that the Moon looks great.

## Scope (placeholder — to fill in when phase begins)

In scope:
- Bake the 2K + 4K + 8K albedo + normal map asset set per ADR-0004.
- Bundle the 2K fallback (~2-3 MB) in `composeResources/files/textures/`.
- Implement first-launch CDN download of the 8K HD tier (Ktor client in `commonMain`).
- Cache management: download once, validate by content hash, fall back gracefully if offline.
- Attribution string in About / Credits screen.
- Asset version pinning (so old caches don't conflict with new assets).

Out of scope:
- Tile streaming for >16K resolution (defer indefinitely or to Phase 2).
- Elevation map displacement rendering (defer).

## Depends on

- `00-renderer-spike` complete and merged.
- ADR-0004 ratified.

## Status

Skeleton placeholder. Fill in when starting this phase.

## References

- ADR-0004 (Asset strategy)
- `ai-docs/research/moon-assets.md`
- `ai-docs/research/agp9-kmp-native-deps.md` §4
