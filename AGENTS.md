# FAF Mobile Client - Multi-AI Agents Doc

## Project Status
| Item | Status |
|------|--------|
| Version | 1.1.3 (versionCode 5 — synced with app/build.gradle.kts) |
| Platform | Android (Kotlin + Jetpack Compose) |
| Desktop side | Fork `GuyFran/faf-client-python` branch `companion-relay` (see its COMPANION.md) |
| Last updated | 2026-09-18 (docs pass — fixed the release-gate Python target 3.13→3.14); code last touched 2026-09-02 (full-review gap-fix pass) |

## Implemented Features
| Feature | Status | Notes |
|---------|--------|-------|
| OAuth2 device-code login | Done | Working client id; auto refresh incl. pre-request expiry check |
| OAuth2 browser redirect | Kept, needs FAF client id | PKCE enabled, public-client auth, IO-dispatched |
| IRC Chat | Done & verified | Binary-frame WS, SASL; token-refreshing reconnects, channel re-join, dedupe, manual Reconnect |
| Chat UI | Done | Channels, user drawer, disconnected banner + reconnect |
| Profile | Done & verified | Ratings + rating-evolution graph; identity backfill, no eternal spinner |
| Companion mode (Android) | **Mock e2e PASSED** (2026-09-02, on-device) | Pairing, epoch snapshot, live streaming, player expand, filters, edit-connection all verified via mock relay + adb reverse |
| Notification reducer | Done (pure), unwired | 19/19 tests; delivery/foreground service held for release gates |
| Desktop companion relay | Done (fork `companion-relay`, advanced past `a907ebc` as of 2026-09-11) | Failure-isolated, epoch snapshots, source-ready, caps; since hardened with a one-shot `setup_companion.ps1` (+ faf-uid download), VS Code tasks, real-LAN-adapter binding (`FAF_COMPANION_BIND_IP` override) and its own `AGENTS.md`/`COMPANION.md` |
| Direct FAF lobby login | **Removed** | Deleted per DECISION §8.4/§10 (LobbyClient/GamesRepository gone) |

## Direction: Companion Architecture (ratified — COLLAB_PLAYTAB.md §10)
Phone = read-only viewer; the user's desktop FAF client relays `game_info` over the home LAN
(`ws://<pc>:6900`, token-gated, trusted-LAN experimental). No UID, no desktop-session eviction.
Chat + REST stay direct phone→FAF. Setup guide: fork's `COMPANION.md`; pairing info lands in
`~/faf_companion_pairing.txt` on the PC.

## Backlog
| Item | Priority | Source/Date |
|------|----------|-------------|
| Release gate A: desktop suite under real Python 3.14/CI | High | COLLAB §10.5 (fork targets 3.14) |
| Release gate B: live phone↔desktop e2e (real forked client) | High | COLLAB §10.5 — needs Python installed on the PC |
| Wire notifications (reducer → user-started foreground service) | Medium | After gates A+B |
| Pinned `wss://` + QR pairing | Medium | Before sharing the app beyond own devices (COLLAB §10.4) |
| Desktop settings UI for companion (enable/pairing/regenerate) | Medium | COMPANION.md documents the interim env-var/settings-key path |
| Register a dedicated mobile OAuth client ID with FAF | Low | Device-flow id works meanwhile |
| Map preview thumbnails / PM UI / notifications badges | Low | Original nice-to-haves |

## Architecture
```
com.faforever.mobile/
  auth/          OAuth2 (device code + browser PKCE), tokens, refresh, identity backfill
  chat/          IRC WebSocket client + chat UI                       [WORKING]
  companion/     LAN relay client + snapshot assembler + event reducer [PRE-E2E]
  games/         Play tab UI (fed by companion/)                       [PRE-E2E]
  profile/       Ratings + rating-history graph                        [WORKING]
  network/       Config, API service, SessionManager
  navigation/    Bottom nav: Chat, Play, Profile
  di/, ui/theme/
```

## Key Facts For Any Agent
- The phone must NEVER attempt FAF lobby login (anti-smurf UID; ratified in COLLAB §10). The
  old direct path was deleted — do not reintroduce it.
- Relay wire protocol (authoritative doc: fork `src/companion/relay.py`): newline-terminated
  JSON; hello→hello_ok; source_offline; epoch-tagged snapshot_begin/end; single `game_info`
  lines only. Phone states: DISCONNECTED/CONNECTING/WAITING/CONNECTED.
- Ratings: `/data/leaderboardRating` + `/data/leaderboardRatingJournal`
  (plot `meanAfter − 3·deviationAfter` over scoreTime). Bearer token required.
- Kotlin unit tests: `app/src/test/.../companion/` (reducer 19 + assembler 9) —
  `gradlew testDebugUnitTest`.

## Read Order
1. `CLAUDE.md` - overview & the companion architecture fact
2. `AGENTS.md` - this file
3. `COLLAB_PLAYTAB.md` §9–§10 - the ratified decision (+ 21-turn review log)
4. `companion/RelayClient.kt` + `SnapshotAssembler.kt` + `LobbyEventReducer.kt` - the live path
5. Fork `GuyFran/faf-client-python@companion-relay`: `AGENTS.md` + `src/companion/relay.py` + `COMPANION.md`
6. `chat/IrcClient.kt` - IRC implementation
7. `profile/ProfileRepository.kt` - ratings/history API usage
