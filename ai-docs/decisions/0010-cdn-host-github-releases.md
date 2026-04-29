# ADR-0010: HD asset CDN — GitHub Releases

**Status**: Accepted
**Date**: 2026-04-29
**Refines**: ADR-0004 §"Packaging" — "CDN host TBD in a later ADR"

## Context

ADR-0004 commits to a 2 K bundled fallback + an 8 K HD tier streamed from a CDN, but punted the CDN host choice to a later ADR. The spec for `02-moon-renderer-mvp` then made T120 (Ktor HTTP fetch) and T109 (asset upload) gated on this decision.

The HD payload per asset version is small and infrequent:

- ~30 MB per release (`moon_albedo_8k.ktx2` + `moon_normal_8k.ktx2` ≤ 30 MB combined per the spec's NFR).
- Re-bakes happen on the order of weeks-to-months — when the bake script changes, the source LDEM version changes, or the visual targets shift.
- The mobile client downloads anonymously (no account), needs HTTPS, range requests for resumable downloads, and ETag-based cacheability per the spec's assumptions.
- Assets must be **immutable per version** so the manifest can hash-pin them and so cache invalidation by version is deterministic.

This is a personal / showcase project, not a commercial product. Operational simplicity matters more than per-asset analytics, custom domains, or fine-grained access control.

Three viable hosts:

| Host | Cost | Setup friction | Egress cap | Immutability | Auth needed |
|---|---|---|---|---|---|
| **GitHub Releases** | $0 | nil (already on GitHub) | unlimited (public repos) | per-tag | none |
| **Cloudflare R2** | ~$0.015/GB-month storage; egress free | account + bucket + IAM | none | only via per-key paths | none for read |
| **AWS S3 + CloudFront** | pay storage + egress | IAM + bucket + distribution + (optional) CDN | n/a | only via per-key paths | none for read |

## Decision

Host the 8 K KTX2 binaries on **GitHub Releases** of the project repo, with each asset version published under its own immutable tag.

### Tag scheme

Asset releases use the prefix **`assets-v<N>`** (e.g. `assets-v1`, `assets-v2`, …) — decoupled from the app's own release tags (`v0.x.y`). An asset bump must not force an app version bump and vice versa.

### URL pattern

Until the repo is pushed to GitHub, the owner/repo segment is a placeholder. The committed `manifest.json` template uses `<owner>/MoonExplorer` and the value is filled in before T109.

```
https://github.com/<owner>/MoonExplorer/releases/download/assets-v<N>/moon_albedo_8k.ktx2
https://github.com/<owner>/MoonExplorer/releases/download/assets-v<N>/moon_normal_8k.ktx2
```

These URLs are **stable for the lifetime of the tag**. GitHub makes the underlying `tag <-> commit` immutable; an asset replacement on an existing tag would require deleting and re-creating the release (which we don't do — we publish a new `assets-v<N+1>` tag instead).

### Manifest location

For v1, the `manifest.json` is **bundled only** — committed at `composeResources/files/manifest.json`. The renderer reads URLs + SHA-256s from the bundled copy.

The plan / spec leave room for a **remote manifest** later (so HD updates can ship without an app update). Adding it is straightforward — point the loader at a stable URL and compare versions — but GitHub Releases doesn't have a clean "latest pointer" that distinguishes asset releases from app releases without ceremony (mixing asset and app tags under `/releases/latest` is fragile). Defer that to a future ADR when we actually need OTA asset updates.

### Asset bump procedure

1. Re-bake the source assets via `tools/bake-normal-map/bake.py` and `tools/build-ktx2/build.sh`.
2. Re-run `tools/build-ktx2/manifest.py` to regenerate `manifest.json` (bumps the date-based `version` field, recomputes SHA-256s).
3. `gh release create assets-v<N> --notes "<bake notes>" tools/build-ktx2/.cache/moon_albedo_8k.ktx2 tools/build-ktx2/.cache/moon_normal_8k.ktx2`
4. Commit the new bundled `manifest.json` (with the `assets-v<N>` URLs) and the new bundled 2 K KTX2 if it changed.
5. Ship the app update — first launch on the new app build re-fetches the new HD set because the bundled manifest's `version` field changed (FR-009).

## Rationale

- **Cost.** Free at our scale; GitHub doesn't bill bandwidth on public repos. R2 / S3 are cheap but non-zero, and they'd require billing setup for what is currently a hobby project.
- **Setup friction.** Already on GitHub. No new account, no new credentials in CI, no IAM. The `gh` CLI is the only thing to learn (and it's already on most dev machines).
- **Immutability.** Tag-based releases give us per-version immutability for free. We don't need an S3 bucket-policy denying overwrites or an R2 versioning configuration.
- **Auditability.** The release tag + asset SHA-256 are visible in the GitHub UI alongside the source code that produced them (the bake script). One place to verify the chain.
- **Reverting.** If a bad asset ships, publish `assets-v<N+1>` reverting to the prior bake. The old tag stays available; clients with the old manifest version keep working.
- **Decoupled tag prefix.** `assets-v<N>` keeps asset releases out of the way of app version listings. Useful when the app has its own `v1.0.0` etc.

## Alternatives rejected

- **Cloudflare R2** — egress-free and S3-compatible; more flexible for future custom-domain + per-asset analytics. But: separate billing, separate auth, and we don't need any of those flexibility points yet. If we later need custom domain or analytics, a future ADR can re-host without changing the manifest schema (just the URLs).
- **AWS S3 (+ CloudFront)** — most professional, most operational ceremony, no benefit at our scale.
- **Bundled-only (skip CDN entirely)** — would force HD into the install bundle, breaking ADR-0004's 50 MB warning-threshold constraint. Not viable.
- **A separate `MoonExplorer-assets` repo** — would unlock a clean `/releases/latest` pointer for OTA asset updates by isolating asset tags from app tags. Premature; we don't need OTA today, and a second repo is real ongoing maintenance.

## Consequences

- `manifest.json` URLs are committed with `<owner>/MoonExplorer` placeholders until the repo is pushed to GitHub. **T109 (asset upload) is gated on the GitHub repo existing under a known owner.** First-time upload procedure: push repo → confirm owner → search-and-replace the placeholder in `manifest.json` and the bundled copy → run T109's `gh release create` command → commit the patched manifest.
- The version-bump procedure above replaces ad-hoc asset uploads. T106 / T107 / T108 / T109 should reference this ADR as the bump procedure.
- We accept the operational gap that **OTA asset updates require an app update** today. If we later want OTA, file ADR-00xx covering the remote-manifest URL strategy (likely a separate `MoonExplorer-assets` repo + `/releases/latest/download/manifest.json`).
- The `gh` CLI must be installed on the bake machine. `brew install gh && gh auth login`.

## References

- ADR-0004 §"Packaging" — the deferred decision this ADR resolves
- `ai-docs/specs/02-moon-renderer-mvp/spec.md` — FR-002 (HD download), FR-009 (manifest version)
- `ai-docs/specs/02-moon-renderer-mvp/tasks.md` — T102 (this ADR), T107–T109 (manifest + upload), T120 (Ktor fetch)
- [GitHub release asset documentation](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)
- [`gh release` CLI reference](https://cli.github.com/manual/gh_release)
