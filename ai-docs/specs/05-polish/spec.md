# Feature Specification: 05 — Polish

**Branch:** `05-polish` | **Created:** 2026-04-28 | **Status:** Skeleton (placeholder)

## Goal (1-line)

Take the working MVP and make it feel finished: cinematic camera idle, label fade-in/out, favorites, onboarding, theme polish.

## Scope (placeholder — to fill in when phase begins)

Likely items (will be re-prioritized when this phase begins):
- Idle camera: gentle drift / breathing rotation when user is idle for >5 s.
- Marker label collision avoidance (so labels don't overlap when zoomed out).
- Marker label fade based on zoom + facing.
- Favorites: bookmark sites; quick-jump from a list.
- Onboarding: first-launch micro-tutorial (3-4 cards explaining gestures).
- Visual polish: app icon, splash screen, theme refinement, dark/light mode handling.
- Performance pass: confirm 60 FPS budget on a low-end target device.

Out of scope:
- Anything requiring a new architectural decision (would need its own ADR).
- Real elevation displacement (defer or Phase 2 follow-up).

## Depends on

- All of `01-app-shell`, `02-moon-renderer-mvp`, `03-sites-and-flyto`, `04-sun-control` complete.

## Status

Skeleton placeholder. Re-prioritize when this phase begins; user feedback on the MVP should inform what's actually polish-worthy.

## References

- `ai-docs/initial-idea.md` "Phase 2: Polish" section
- `ai-docs/constitution.md` (Quality Gates)
