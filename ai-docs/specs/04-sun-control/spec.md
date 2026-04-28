# Feature Specification: 04 — Sun Control

**Branch:** `04-sun-control` | **Created:** 2026-04-28 | **Status:** Skeleton (placeholder)

## Goal (1-line)

Replace the Phase 0 placeholder slider with a real sun-direction control: 2D joystick + presets (Full / Half / Crescent / Apollo lighting).

## Scope (placeholder — to fill in when phase begins)

In scope:
- 2D circular sun-joystick UI (Compose) mapping `(x, y) ∈ [-1, 1]²` → world-space sun direction (per `selenographic-math-camera.md` §6 mode (a)).
- Preset buttons: Full, Waxing Half, Waning Half, Crescent, Apollo-style ("dramatic crater shadows").
- Optional "Scientific" mode: enter selenographic sun lat/lon directly.
- Smooth transition between presets (animated, ~0.5 s).
- The `setSunDirection(lat, lon)` and `setLightingPreset(preset)` actions wired end-to-end.

Out of scope:
- True ephemeris-driven Moon-phase-by-date selector — defer or out of scope per Constitution V.
- Time-of-day animation — defer.

## Depends on

- `00-renderer-spike` complete (sun direction is already a state field).
- `01-app-shell` complete (panel infrastructure for the joystick UI).

## Status

Skeleton placeholder. Fill in when starting this phase.

## References

- ADR-0005 (`MoonExplorerActions` shape)
- ADR-0006 (Selenographic convention)
- `ai-docs/research/selenographic-math-camera.md` §6
- `ai-docs/initial-idea.md` "Sun placement tool" section
