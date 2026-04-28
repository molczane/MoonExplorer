# Feature Specification: 01 — App Shell

**Branch:** `01-app-shell` | **Created:** 2026-04-28 | **Status:** Skeleton (placeholder)

## Goal (1-line)

Build the Compose Multiplatform shell around the Phase 0 renderer: site catalog, search bar, location info sheet, settings entry, and DI wiring for the `MoonExplorerActions` interface.

## Scope (placeholder — to fill in when phase begins)

In scope:
- Site catalog: data model + bundled JSON for the 16 named sites from `ai-docs/initial-idea.md`.
- `MoonExplorerActions` interface + `MoonExplorerActionsImpl` backed by `MoonViewModel`.
- Search bar UI (filtering by name).
- Location info sheet (modal/bottom sheet showing site details).
- Settings entry (placeholder; real settings can wait).
- DI wiring (no DI framework needed yet; manual wiring in `App()` is fine).

Out of scope:
- Markers on the Moon surface (`03-sites-and-flyto`).
- Fly-to animation (`03-sites-and-flyto`).
- Real NASA textures (`02-moon-renderer-mvp`).
- Sun control beyond the placeholder slider (`04-sun-control`).

## Depends on

- `00-renderer-spike` complete and merged.

## Status

This file is a **skeleton placeholder**. Fill in `spec.md` / `plan.md` / `tasks.md` when starting this phase, following the templates ratified in ADR-0007.

## References

- ADR-0005 (`MoonExplorerActions` shape, locked here)
- ADR-0007 (SDD framework)
- `ai-docs/initial-idea.md` (named sites list)
- `ai-docs/architecture.md` § Layers
