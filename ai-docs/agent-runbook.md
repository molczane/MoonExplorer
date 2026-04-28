# Agent Runbook

> How AI coding agents (and humans) work in this repo. Read this before picking up a task.

## Reading order when picking up a task

For any non-trivial task, read in this order:

1. `ai-docs/agent-runbook.md` (this file) — always.
2. `ai-docs/constitution.md` — durable principles. Constitution gates apply to every change.
3. The feature folder under `ai-docs/specs/<NN-feature>/`:
   1. `spec.md` (what & why; user stories; acceptance criteria).
   2. `plan.md` (architecture, components, files, contracts).
   3. `tasks.md` (ordered work items; pick one to execute).
4. `ai-docs/architecture.md` and `ai-docs/tech-stack.md` for any module/dependency questions.
5. Relevant ADRs in `ai-docs/decisions/` (each task in `plan.md` lists the ADRs it depends on).
6. Relevant raw research in `ai-docs/research/` only if you need detail beyond what the ADR captures. Research is **reference, not authoritative** — ADRs are.

For a trivial task (typo fix, version bump within update policy), skip steps 3 and 5.

## Definition of Done for a task

A task in `tasks.md` is "done" when **all** of:

1. Code compiles cleanly on Android (`./gradlew :androidApp:assembleDebug`).
2. Code compiles cleanly on iOS (`./gradlew :shared:embedAndSignAppleFrameworkForXcode`).
3. All `commonTest` tests pass (`./gradlew :shared:allTests`).
4. Acceptance criteria from the parent user story (in `spec.md`) are verified — automated where possible, manual on a real device for UI tasks.
5. The task line in `tasks.md` is checked off (`- [x]`).
6. If the task changed `architecture.md` or `tech-stack.md`, an ADR was filed first.

A task is **not** done if:

- Tests fail (or were skipped to make CI green).
- It "works on emulator/simulator" but wasn't tried on a real device, for a UI task.
- An acceptance criterion is "loosely met" — they are pass/fail.
- Documentation is out of sync with the change.

## When to update each doc

| Doc | Edit policy |
|---|---|
| `constitution.md` | Never without explicit user approval and an ADR. |
| `architecture.md` | When a structural change happens. ADR first, then update. |
| `tech-stack.md` | When versions / libraries / tools change. Patch and minor bumps update directly; major changes need ADR. |
| `agent-runbook.md` | When the agent workflow itself changes. Rare. |
| `decisions/NNNN-*.md` | **Append-only.** Once accepted, never edit. Supersede via a new ADR. |
| `specs/<feature>/spec.md` | Treat as read-only during implementation. If a real constraint emerges that changes the spec, stop and surface — don't silently edit. |
| `specs/<feature>/plan.md` | Update **once** when implementation reveals a non-trivial unforeseen constraint. Note the change in the file with a `> Updated YYYY-MM-DD: ...` block. |
| `specs/<feature>/tasks.md` | Check off boxes as you complete them. Add follow-up tasks at the end of the relevant phase if you discover them. |
| `research/*.md` | Reference material; do not edit. Add new research files as agents produce them. |

## Conventions

### Code

- All new shared logic goes in `:shared/commonMain` unless it genuinely needs a platform API.
- Use `expect`/`actual` only at architectural seams (renderer host, platform-specific hardware access). Not for convenience.
- `data class` for state, `interface` for actions, `StateFlow` for cross-thread state.
- Mutations to `MoonRenderState` go through `MoonViewModel` only, never directly into the StateFlow.
- Side-effecting `MoonExplorerActions` methods serialize through a `Mutex`.
- Comments explain *why*, not *what*. Default to none. No multi-paragraph docstrings.
- No emojis in code or commits unless explicitly requested.

### File names

- Kotlin: `PascalCase.kt` for types, lowercase package names.
- `Platform.android.kt` / `Platform.ios.kt` for `actual` declarations alongside the `expect`.
- Tests: `*Test.kt` matching the module under test.

### Branches and PRs

- Branch name: `<NN>-<feature-slug>` matching the spec folder (e.g., `00-renderer-spike`).
- Conventional commits: `feat:`, `fix:`, `chore:`, `docs:`, `refactor:`.
- PR description links to the feature `spec.md` and lists which `tasks.md` items are completed.

## When to stop and ask

You stop and surface to the user (don't push through) when:

- An acceptance criterion can't be satisfied as written. Example: "fly-to animation completes in 1.2 s" but on real hardware it's 2.5 s. Ask whether the criterion bends or the implementation needs more work — don't quietly relax the spec.
- A task requires editing `architecture.md` or `tech-stack.md` and there's no ADR authorizing it.
- You discover an architectural conflict between two ADRs.
- You can't reach the listed verification command (e.g., Xcode build environment missing). Don't fake-pass.
- Filament/Compose/Kotlin behaves differently from what the research/ADR predicts. Note the discrepancy; the research may be stale.

## Glossary

Terms that recur. Don't guess these.

| Term | Definition |
|---|---|
| **Selenographic** | Lunar surface coordinate system. Latitude north-positive, longitude east-positive (post-2007 IAU convention). Per ADR-0006. |
| **Mare (pl. maria)** | Dark basaltic plain on the Moon (e.g., Mare Tranquillitatis). |
| **Terminator** | Boundary between lit and unlit Moon hemispheres. Visible as the curved line on a half/crescent Moon. |
| **Phase angle** | Sun–Moon–Earth angle. 0° = full, 90° = half, 180° = new (from Earth's perspective). |
| **Libration** | Apparent wobble of the Moon as seen from Earth. Out of v1 scope; relevant in polish phase. |
| **filamat** | Compiled Filament material, output of the `matc` host tool. Cross-platform binary. |
| **KTX2** | Khronos texture container format. Holds Basis Universal compressed textures. |
| **Basis Universal** | GPU-portable texture compression. Two flavors: ETC1S (small, slight quality loss) and UASTC (better quality, larger). |
| **ETC1S** | One Basis Universal mode. Used here for albedo (sRGB). |
| **UASTC** | The other Basis Universal mode. Used here for normal maps (linear). |
| **Equirectangular** | 2:1 aspect texture projection where x = longitude, y = latitude. Standard for globe textures. |
| **Orbit camera** | Camera that always looks at a fixed center (Moon) at variable yaw/pitch/distance. Per `selenographic-math-camera.md` §2. |
| **Fly-to** | Smooth camera animation from current orbit to a target lat/lon + zoom. Per `selenographic-math-camera.md` §5. |
| **MoonRenderState** | Shared immutable per-frame state. See `architecture.md`. |
| **MoonExplorerActions** | Single command surface for UI mutations and (Phase 3) Koog tool calls. See ADR-0005. |
| **CMP** | Compose Multiplatform. |
| **KMP** | Kotlin Multiplatform. |
| **Koog** | JetBrains' Kotlin AI agents framework. Phase 3 only. |
| **AGP** | Android Gradle Plugin. We use 9.0.x with the new `com.android.kotlin.multiplatform.library` plugin. |
| **`expect`/`actual`** | KMP's mechanism for declaring a multi-platform API in commonMain (`expect`) and providing per-target implementations (`actual`). |

## When the runbook is wrong

If a workflow step in this file blocks reasonable progress, surface it to the user. The runbook is a living document and a corner case shouldn't make work impossible. Do not silently work around it.

## References

- `ai-docs/constitution.md` (durable principles)
- `ai-docs/architecture.md` (system shape)
- `ai-docs/tech-stack.md` (versions and forbidden patterns)
- `ai-docs/decisions/` (ADRs)
- `ai-docs/research/` (reference research)
