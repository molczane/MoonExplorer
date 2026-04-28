# ADR-0007: SDD framework — hybrid Spec Kit + Kiro

**Status**: Accepted
**Date**: 2026-04-28
**Supersedes**: —

## Context

This project uses spec-driven development for AI-agent implementation: future agents will pick up tasks from markdown specs and execute them. The two actively-maintained SDD frameworks for AI agents are:

- **GitHub Spec Kit** (https://github.com/github/spec-kit) — lightweight, agent-agnostic markdown; `memory/constitution.md` + per-feature `specs/<branch>/{spec.md, plan.md, tasks.md}`. Centers on prioritized user stories as independently shippable MVP slices.
- **AWS Kiro** (https://kiro.dev) — opinionated agentic IDE; `.kiro/specs/<feature>/{requirements.md, design.md, tasks.md}` plus steering docs (`product.md`, `tech.md`, `structure.md`). Enforces strict Requirements → Design → Tasks gates and EARS-formatted acceptance criteria ("WHEN ... THEN system SHALL ...").

We could pick one wholesale, or merge.

## Decision

**Hybrid.** Take Spec Kit's overall shape (lightweight, agent-agnostic markdown; per-feature folders with `spec.md` / `plan.md` / `tasks.md`; user-story-first decomposition) and fold in Kiro's concrete improvements (EARS-formatted acceptance criteria for verifiability; design merged into plan; project-level steering content folded into `constitution.md` + `architecture.md` + `tech-stack.md`).

### Project-level layout

```
ai-docs/
├── constitution.md          # durable principles (Spec Kit shape + Kiro steering folded in)
├── architecture.md          # module boundaries, seams, data flow
├── tech-stack.md            # pinned versions, plugins, libs, forbidden deps
├── agent-runbook.md         # how agents work in this repo
├── decisions/               # ADRs (append-only)
│   ├── 0001-...md
│   └── ...
├── research/                # raw research outputs from agent runs (reference, not authoritative)
└── specs/                   # per-feature specs
    ├── 00-renderer-spike/
    │   ├── spec.md          # user stories, FR (in EARS), success criteria
    │   ├── plan.md          # technical context, components, data models, testing strategy
    │   └── tasks.md         # ordered, agent-sized work items with [P] / [US#] tags
    └── ...
```

### Per-feature `spec.md` headers

```markdown
# Feature Specification: <name>
**Branch:** <branch-name> | **Created:** <date> | **Status:** Draft|Ratified|Implemented

## User Scenarios
### User Story 1 — <title> (Priority: P1)
**Why this priority:** ...
**Independent Test:** ...
**Acceptance Scenarios:** (Given/When/Then OR EARS)

### User Story 2 ...
### Edge Cases

## Requirements
### Functional Requirements
- FR-001: WHEN ... THEN the system SHALL ...
- FR-002: IF ... THEN ... SHALL ...
### Key Entities

## Non-Functional Requirements
(performance, offline, accessibility, battery)

## Success Criteria
- SC-001: <measurable outcome>

## Edge Cases
## Assumptions & Out of Scope
```

### Per-feature `plan.md` headers

```markdown
# Implementation Plan: <name>
**Branch:** ... | **Date:** ... | **Spec:** ./spec.md

## Summary
## Technical Context
(language/version, deps, target platforms, perf goals, constraints)

## Constitution Check
(gate against ai-docs/constitution.md)

## Architecture
(module/layer diagram — Mermaid OK)

## Components and Interfaces
(per-module purpose, expected/actual, public APIs)

## Data Models
## Error Handling
## Testing Strategy
## Project Structure
(file tree under :shared, :androidApp, iosApp/)

## Complexity Tracking
(violations of constitution + justification)
```

### Per-feature `tasks.md` headers

```markdown
# Tasks: <name>
## Format: [ID] [P?] [US?] Description
## Path Conventions

## Phase 1: Setup
- [ ] T001 [P] <one-line task> at <path> — _Requirements: FR-001_

## Phase 2: Foundational
## Phase 3: User Story 1 (P1) — MVP
### Tests for User Story 1
### Implementation for User Story 1
**Checkpoint:** <visible behavior verifiable by user>

## Phase 4: User Story 2 ...
## Phase Final: Polish

## Dependencies & Execution Order
## Parallel Example: User Story 1
```

Task line format:

```
- [ ] T012 [P] [US1] Create MoonSite model in shared/src/commonMain/.../MoonSite.kt
  - Fields: id, name, lat, lon, tags
  - _Requirements: FR-001, FR-003_
```

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.

## Rationale

- Spec Kit's structure is **lightweight, agent-agnostic markdown** — no IDE lock-in, works with any AI coding agent.
- Kiro's **EARS-formatted acceptance criteria** ("WHEN ... THEN system SHALL ...") improve testability and machine-readability without breaking Spec Kit's structure.
- Merging Spec Kit's `plan.md` with Kiro's `design.md` content (architecture / components / data models / testing strategy) avoids a 3-file split where 2-file works.
- Steering docs (`product.md` / `tech.md` / `structure.md`) are unnecessary as separate files when we have `constitution.md`, `architecture.md`, `tech-stack.md` covering the same ground.
- Leaning toward Spec Kit means agents reading our docs get familiar shapes that match Spec Kit prompts in the wild.

## Alternatives rejected

- **Pure Spec Kit**: missing EARS rigor; acceptance criteria sometimes vague.
- **Pure Kiro**: forces an IDE-specific gate workflow we don't have; `requirements.md` + `design.md` split is more docs than we need; steering folder duplicates constitution/architecture/tech-stack.
- **One big PLAN.md**: agents pull more context than they need per task; harder to own sections.

## Consequences

- All future feature specs use this template. The first one (`00-renderer-spike/`) is the live example.
- Agent prompts can reference `spec.md` / `plan.md` / `tasks.md` as standard.
- Acceptance criteria use EARS where possible (`WHEN ... THEN ... SHALL ...`).
- The constitution gates apply to both spec.md and plan.md.
- ADRs (`decisions/NNNN-*.md`) are append-only; supersedes are recorded in a new ADR's frontmatter, not by editing the original.

## References

- `ai-docs/research/sdd-methodology.md`
- [github/spec-kit](https://github.com/github/spec-kit)
- [Kiro docs](https://kiro.dev/docs/specs/)
- [EARS notation overview](https://alistairmavin.com/ears/)
