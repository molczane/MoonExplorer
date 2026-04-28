# Spec-Driven Development Templates: Concrete Headers

> Research output. Source: agent run 2026-04-28. Focus: extracting concrete templates from GitHub Spec Kit and AWS Kiro for adaptation in `ai-docs/`.

## 1. GitHub Spec Kit templates

Source repo: `github/spec-kit`, all paths below are inside `templates/`.

### `templates/constitution-template.md`
- `# [PROJECT_NAME] Constitution`
- `## Core Principles`
  - `### [PRINCIPLE_1_NAME]` ... `### [PRINCIPLE_5_NAME]` (numbered I–VII; example placeholders include "Library-First", "CLI Interface", "Test-First (NON-NEGOTIABLE)", "Integration Testing", "Observability", "Versioning & Breaking Changes", "Simplicity")
- `## [SECTION_2_NAME]` (e.g. Additional Constraints, Security, Performance Standards)
- `## [SECTION_3_NAME]` (e.g. Development Workflow, Review Process, Quality Gates)
- `## Governance`
- Footer line: `**Version**: ... | **Ratified**: ... | **Last Amended**: ...`

### `templates/spec-template.md`
- `# Feature Specification: [FEATURE NAME]` (with metadata: Feature Branch, Created, Status, Input)
- `## User Scenarios & Testing *(mandatory)*`
  - `### User Story 1 - [Brief Title] (Priority: P1)` with sub-fields **Why this priority**, **Independent Test**, **Acceptance Scenarios** (Given/When/Then)
  - Repeat for User Story 2 (P2), 3 (P3) ...
  - `### Edge Cases`
- `## Requirements *(mandatory)*`
  - `### Functional Requirements` (FR-001, FR-002, ... with `[NEEDS CLARIFICATION: ...]` markers)
  - `### Key Entities *(include if feature involves data)*`
- `## Success Criteria *(mandatory)*`
  - `### Measurable Outcomes` (SC-001, SC-002, ...)
- `## Assumptions`

### `templates/plan-template.md`
- `# Implementation Plan: [FEATURE]` (Branch, Date, Spec link, Input)
- `## Summary`
- `## Technical Context` (Language/Version, Primary Dependencies, Storage, Testing, Target Platform, Project Type, Performance Goals, Constraints, Scale/Scope)
- `## Constitution Check` (gate before/after design)
- `## Project Structure`
  - `### Documentation (this feature)` (tree of `specs/[###-feature]/`)
  - `### Source Code (repository root)` with `**Structure Decision**`
- `## Complexity Tracking` (Violation / Why Needed / Simpler Alternative Rejected Because table)

### `templates/tasks-template.md`
- `# Tasks: [FEATURE NAME]`
- `## Format: [ID] [P?] [Story] Description`
- `## Path Conventions`
- `## Phase 1: Setup (Shared Infrastructure)`
- `## Phase 2: Foundational (Blocking Prerequisites)`
- `## Phase 3: User Story 1 - [Title] (Priority: P1) MVP`
  - `### Tests for User Story 1 (OPTIONAL ...)`
  - `### Implementation for User Story 1`
  - `**Checkpoint**` line
- `## Phase 4: User Story 2 ...`, `## Phase 5: User Story 3 ...`
- `## Phase N: Polish & Cross-Cutting Concerns`
- `## Dependencies & Execution Order` (Phase Dependencies, User Story Dependencies, Within Each User Story, Parallel Opportunities)
- `## Parallel Example: User Story 1`
- `## Implementation Strategy` (MVP First, Incremental Delivery, Parallel Team Strategy)
- `## Notes`

Spec Kit also ships `templates/checklist-template.md` and command prompt files in `templates/commands/` (`specify.md`, `clarify.md`, `plan.md`, `tasks.md`, `analyze.md`, `implement.md`, `constitution.md`, `checklist.md`, `taskstoissues.md`).

## 2. Kiro templates

Kiro's IDE auto-generates files into `.kiro/specs/<feature-slug>/`. The official docs at `kiro.dev/docs/specs/` describe the three-phase artifact set; concrete headers come from Kiro's published templates ([jasonkneen/kiro spec-process-guide/templates](https://github.com/jasonkneen/kiro)) and AWS sample steering ([aws-samples/sample-kiro-cli-prompts-for-product-teams](https://github.com/aws-samples/sample-kiro-cli-prompts-for-product-teams)).

### `requirements.md` (Kiro spec)
- `# Requirements Document`
- `## Introduction`
- `## Requirements`
  - `### Requirement 1` containing `**User Story:**` (As a / I want / so that) and `#### Acceptance Criteria` written in **EARS** format ("WHEN ... THEN [system] SHALL ..." / "IF ... THEN ... SHALL ...")
  - Repeat per requirement
- Extended template adds: `## Document Information`, `## Non-Functional Requirements` (Performance/Security/Usability/Reliability), `## Constraints and Assumptions`, `## Success Criteria` (Definition of Done, Acceptance Metrics), `## Glossary`, `## Review Checklist`.

### `design.md` (Kiro spec)
Mandatory sections per Kiro's workflow definition:
- `# Design Document`
- `## Overview`
- `## Architecture`
- `## Components and Interfaces`
- `## Data Models`
- `## Error Handling`
- `## Testing Strategy`

The `kiro.dev` Design-First docs add: System components & responsibilities, Data flow & interactions, Technology stack, API contracts, Non-functional considerations. Mermaid diagrams encouraged.

### `tasks.md` (Kiro spec)
- `# Implementation Plan`
- Top-level numbered checkboxes (epics) with two-level decimal sub-tasks (`1.`, `1.1`, `1.2`)
- Each leaf task includes: objective line, sub-bullets describing what to write/modify/test, and a trailing `_Requirements: 1.1, 2.3_` link back to requirement IDs
- Constraint: "ONLY tasks that involve writing, modifying, or testing code" — no deploy/UAT/metrics tasks

### Kiro steering docs (project-level, in `.kiro/steering/`)
Per [kiro.dev/docs/steering](https://kiro.dev/docs/steering/), three default files always loaded:
- `product.md` — product purpose, target users, key features, business objectives
- `tech.md` — frameworks, libraries, dev tools, technical constraints
- `structure.md` — file organization, naming conventions, import patterns, architectural decisions

## 3. Merged template recommendation (for `ai-docs/`, KMP + Compose Multiplatform Moon Explorer)

### `ai-docs/constitution.md` (project-level principles, Spec Kit-shaped + Kiro steering folded in)
- `## Core Principles` — numbered, named principles
- `## Tech Stack & Constraints` — versions, target platforms, forbidden deps (replaces Kiro `tech.md`)
- `## Project Structure & Conventions` — module layout, naming, package rules (replaces Kiro `structure.md`)
- `## Product Context` — audience, core features, success vision (replaces Kiro `product.md`)
- `## Development Workflow` — branch model, code review gates, CI requirements
- `## Quality Gates` — required checks before merge
- `## Governance` — how to amend, version line at footer

### `ai-docs/specs/<feature>/spec.md` (what & why — Spec Kit shape with Kiro EARS folded in)
- Header block: Feature Branch, Created, Status, Input
- `## User Scenarios` — user stories prioritized P1/P2/P3, each with **Why this priority**, **Independent Test**, **Acceptance Scenarios** in Given/When/Then
- `## Requirements` — `### Functional Requirements` (FR-001…) optionally written in EARS ("WHEN ... THEN system SHALL ..."); `### Key Entities`
- `## Non-Functional Requirements` — perf, offline, accessibility, battery (Kiro contribution)
- `## Success Criteria` — measurable SC-001…
- `## Edge Cases`
- `## Assumptions & Out of Scope`

### `ai-docs/specs/<feature>/plan.md` (how — Spec Kit Plan + Kiro Design merged)
- `## Summary`
- `## Technical Context` — Kotlin/Compose versions, deps, target platforms, perf goals
- `## Constitution Check` — gate against `constitution.md`
- `## Architecture` — module/layer diagram (Mermaid OK)
- `## Components and Interfaces` — per-module purpose, expected/actual declarations, public APIs
- `## Data Models` — domain entities, serialization shape, persistence
- `## Error Handling` — failure modes, user-facing surfaces
- `## Testing Strategy` — commonTest, androidUnitTest, iosTest, screenshot/UI tests
- `## Project Structure` — file tree under `composeApp/`, `shared/`, iOS targets
- `## Complexity Tracking` — violations of constitution + justification

### `ai-docs/specs/<feature>/tasks.md` (ordered work items)
- `## Phase 1: Setup` — module wiring, deps, build files
- `## Phase 2: Foundational` — shared types, DI, navigation skeleton (blocks all stories)
- `## Phase 3..N: User Story <n> (Priority Pn)` — tests sub-block then implementation sub-block, with checkpoint line
- `## Phase Final: Polish` — perf, a11y, docs
- `## Dependencies & Execution Order`
- Task line format: `- [ ] T### [P?] [US#] <verb> <component> at <path>` followed by indented sub-bullets and `_Requirements: FR-001, FR-003_`

## 4. Task granularity

**Spec Kit** uses GitHub-flavored checkboxes with a structured ID prefix. Tasks are grouped by **phase** then by **user story**, each line tagged for parallelism (`[P]`) and story (`[US1]`). Acceptance criteria live in `spec.md`; the task itself is a one-liner with a file path. Verbatim from `templates/tasks-template.md`:

```
- [ ] T012 [P] [US1] Create [Entity1] model in src/models/[entity1].py
- [ ] T014 [US1] Implement [Service] in src/services/[service].py (depends on T012, T013)
```

**Kiro** uses a numbered checkbox list with up to two levels of decimal hierarchy, sub-bullets for sub-deliverables, and a trailing italic `_Requirements: ..._` traceability line. Verbatim from kiro-style-sdd `tasks.md`:

```
- [ ] 2.1 Create core data model interfaces and types
  - Write TypeScript interfaces for all data models
  - Implement validation functions for data integrity
  - _Requirements: 2.1, 3.3, 1.2_
```

Neither embeds full acceptance criteria inside the task — Spec Kit references the user story tag, Kiro references requirement IDs.

## 5. Philosophy difference (≤100 words)

**Spec Kit** is template + CLI scaffolding for any AI agent (`specify` CLI, prompt files, `memory/constitution.md`). It centers on **prioritized user stories as independently shippable MVP slices**, with parallelism markers and constitution gates baked into the templates. **Kiro** is an **opinionated agentic IDE** enforcing a strict three-phase approval gate (Requirements → Design → Tasks), EARS-formatted acceptance criteria, and project-level steering files always in context. **Lean Spec Kit** if you want lightweight, agent-agnostic markdown adopted into any tool. **Lean Kiro** if you want enforced phase gates, EARS rigor, and steering docs as ambient context.

## Sources
- [github/spec-kit (templates/)](https://github.com/github/spec-kit/tree/main/templates) — `constitution-template.md`, `spec-template.md`, `plan-template.md`, `tasks-template.md`, `checklist-template.md`, `commands/`
- [Kiro Specs concepts](https://kiro.dev/docs/specs/)
- [Kiro Best Practices](https://kiro.dev/docs/specs/best-practices/)
- [Kiro Design-First Workflow](https://kiro.dev/docs/specs/feature-specs/tech-design-first/)
- [Kiro Steering docs](https://kiro.dev/docs/steering/)
- [amaynez/kiro-style-sdd](https://github.com/amaynez/kiro-style-sdd) — verbatim Kiro workflow prompts
- [jasonkneen/kiro spec-process-guide/templates](https://github.com/jasonkneen/kiro) — extended Kiro templates
- [aws-samples/sample-kiro-cli-prompts-for-product-teams](https://github.com/aws-samples/sample-kiro-cli-prompts-for-product-teams) — `.kiro/steering/prd-guide.md`
