# FAF Mobile Client

## Project
Android companion app for Forged Alliance Forever (FAF): Chat, a read-only Play tab
(open lobbies + players), and a Profile tab (ratings + rating history).

**Key architecture fact:** the phone NEVER logs into the FAF lobby server (blocked by FAF's
anti-smurf UID, see `docs/ARCHITECTURE.md`). The open-lobby list comes from the user's own
desktop FAF client (fork `GuyFran/faf-client-python`, branch `companion-relay`) which relays
`game_info` over the home LAN (`ws://<pc>:6900`, token-gated). Chat and the REST API are direct
phone→FAF.

## Tech Stack
- Kotlin + Jetpack Compose, Hilt DI, Material 3
- OkHttp WebSocket (IRC chat + LAN companion relay), Retrofit (REST API)
- OAuth2: device-code flow (working, `DeviceCodeAuth`) + browser redirect/PKCE via AppAuth
  (kept for when FAF registers a mobile client id)
- DataStore for tokens + companion pairing config

## Scope
- Chat: auto-joined `#aeolus`, inbound private-message tabs, user list, history on join
- Play: read-only open-lobby list with players per game (no join/host/matchmaking)
- Profile: username, ratings per leaderboard, rating-evolution chart

## Build
Open in Android Studio or use the VS Code tasks ("FAF Mobile: ...").
Requires Android SDK 36 and JDK 17. Version lives in `app/build.gradle.kts` (`versionName`).

## Read first
`AGENTS.md` (entry point and read order). Modules and endpoints: `docs/ARCHITECTURE.md`.
