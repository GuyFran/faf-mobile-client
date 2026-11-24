# FAF Mobile — Multi-AI Agents Entry Point

Start here. Android companion app for Forged Alliance Forever (FAF): chat, a read-only list of
open lobbies fed by the user's own PC, and a ratings profile.

| Item | Value |
|------|-------|
| Version | **1.1.5** (versionCode 7). Source of truth: `versionName` / `versionCode` in `app/build.gradle.kts` |
| Status | **Active, personal use.** Trusted home LAN only, not for distribution (see [BACKLOG](docs/BACKLOG.md)) |
| Owner | GuyFran (guynemerp@free.fr) |
| Authority order | `~/.claude/CLAUDE.md` → root `Projects/AGENTS.md` → this project (`AGENTS.md`, `CLAUDE.md`) |
| Stack | Kotlin, Jetpack Compose, Material 3, Hilt, OkHttp, Retrofit, DataStore, AppAuth. Android SDK 36, min 26, JDK 17 |
| Desktop side | Fork `GuyFran/faf-client-python`, branch `companion-relay` (its own `AGENTS.md`, `COMPANION.md`) |

## Read order

| # | Doc | Use |
|---|-----|-----|
| 1 | `AGENTS.md` | Entry point, hard rules |
| 2 | `CLAUDE.md` | Short project brief |
| 3 | [docs/STATUS.md](docs/STATUS.md) | What works, what was verified and when |
| 4 | [docs/BACKLOG.md](docs/BACKLOG.md) | Open work, known defects |
| 5 | [docs/SPEC.md](docs/SPEC.md) | Product scope and behaviour as implemented |
| 6 | [docs/UI_UX.md](docs/UI_UX.md) | Screens, states, labels, theme |
| 7 | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, endpoints, relay protocol, ratified decision |
| 8 | [docs/CHANGELOG.md](docs/CHANGELOG.md) | Version history |
| — | [docs/history/COLLAB_PLAYTAB.md](docs/history/COLLAB_PLAYTAB.md) | **History only, not a status source.** Full 21-turn decision log |

## Hard rules

- The phone must **never** attempt FAF lobby login. It is blocked by the anti-smurf UID and would
  evict the desktop session. The old direct path was deleted; do not reintroduce it.
- Do not reverse-engineer or forge FAF's UID.
- The Play tab stays **read-only**: no join, host or matchmaking.
- Do not wire notifications or a foreground service before release gates A and B pass.
- Do not share the app beyond the owner's devices before pinned `wss://` lands.
- Never log or export OAuth tokens or the pairing token.

## Recent work (newest first)

| Version | Date | Work |
|---------|------|------|
| 1.1.5 | 2026-11-25 | VS Code tasks default to the release variant; debug tasks kept as "only on request" |
| 1.1.4 | 2026-11-24 | Docs restructured under `docs/`; specs and UI/UX rewritten from code; code-audit defects logged |
| 1.1.3 | 2026-09-18 | Release-gate Python target corrected to 3.14 |
| 1.1.2 | 2026-09 | Stale fork reference refreshed |
| 1.1.1 | 2026-09 | Custom launcher emblem, themed icon, in-app version |
| 1.1.0 | 2026-09-02 | Companion architecture, relay-fed Play tab, profile, device-code auth |

## Build and test

Use the VS Code tasks (`FAF Mobile: ...`) or Android Studio. Unit tests: `gradlew testDebugUnitTest`.
