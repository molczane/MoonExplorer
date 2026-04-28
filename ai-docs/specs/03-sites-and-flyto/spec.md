# Feature Specification: 03 — Sites and Fly-to

**Branch:** `03-sites-and-flyto` | **Created:** 2026-04-28 | **Status:** Skeleton (placeholder)

## Goal (1-line)

Render site markers on the Moon surface (Apollo landings, named craters, maria, poles) and animate the camera to a selected site smoothly.

## Scope (placeholder — to fill in when phase begins)

In scope:
- Marker rendering: billboard sprites at fixed selenographic lat/lon, projected to screen each frame.
- Far-side culling + limb fade per `selenographic-math-camera.md` §7.
- Tap-to-show: tap a marker → show the location info sheet (from `01-app-shell`).
- Search → fly-to: select from search results → smooth camera animation per `selenographic-math-camera.md` §5.
- Keyboard / accessibility: select-by-name action that triggers the same fly-to.
- The `flyToMoonLocation(id)` action in `MoonExplorerActions` is wired end-to-end.

Out of scope:
- Custom user-pinned markers (long-press to drop a pin) — defer.
- Marker labels with collision avoidance — defer to `05-polish`.

## Depends on

- `01-app-shell` complete and merged (site catalog + `MoonExplorerActions` shape).
- `02-moon-renderer-mvp` complete and merged (so users see the real Moon when they fly).

## Status

Skeleton placeholder. Fill in when starting this phase.

## References

- ADR-0003 (Renderer host pattern)
- ADR-0005 (`MoonExplorerActions` shape)
- ADR-0006 (Selenographic convention)
- `ai-docs/research/selenographic-math-camera.md` §1, §5, §7
