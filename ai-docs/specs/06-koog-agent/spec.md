# Feature Specification: 06 — Koog Agent

**Branch:** `06-koog-agent` | **Created:** 2026-04-28 | **Status:** Skeleton (placeholder)

## Goal (1-line)

Add an AI guide that can answer questions about Moon sites and drive the in-app actions ("Show me Apollo 11" → app rotates Moon, zooms, highlights site).

## Scope (placeholder — to fill in when phase begins)

In scope:
- New `:shared-ai` Gradle module depending on `:shared` and `ai.koog:koog-agents` (per ADR-0005).
- One Koog `Tool<Args, Result>` subclass per `MoonExplorerActions` method.
- `ToolRegistry` builder + `AIAgent` configuration.
- Chat panel UI in Compose (sliding bottom sheet or side drawer).
- LLM provider configuration (likely Anthropic; key in `local.properties` for personal use, proxy server for production).
- Tests using Koog's mock executor (`getMockExecutor { ... }`) in `:shared-ai/commonTest`.
- Streaming responses with mid-stream tool calls.
- `Mutex` defense in `MoonExplorerActionsImpl` for concurrent tool calls (Koog can dispatch parallel).

Out of scope (initial release of this phase):
- Web-search tool (defer to a `06b-` follow-up).
- Long-term memory / RAG over Moon facts (defer).
- Voice input (defer or out of scope).

## Depends on

- `01-app-shell` ... `05-polish` all complete and merged.
- ADR-0005 ratified, `MoonExplorerActions` interface stable.

## Open questions to resolve at the start of this phase

- LLM provider choice (Anthropic vs. OpenAI vs. JetBrains AI proxy).
- API key handling strategy (local-only? proxy server? OAuth?).
- iOS framework size impact (Koog xcframework is ~39 MB — see `ai-docs/research/koog-framework.md` §7).

## Status

Skeleton placeholder. Re-prioritize and detail when reaching this phase.

## References

- ADR-0005 (Koog adoption timing — defines the deferral strategy)
- `ai-docs/research/koog-framework.md`
- [docs.koog.ai](https://docs.koog.ai/)
- `ai-docs/initial-idea.md` "Koog later" + "Phase 3" sections
