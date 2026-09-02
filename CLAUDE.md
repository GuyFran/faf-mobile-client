# FAF Mobile Client

## Project
Android companion app for Forged Alliance Forever (FAF): Chat Lobby, a read-only Play tab
(open lobbies + players), and a Profile tab (ratings + rating history).

**Key architecture fact:** the phone NEVER logs into the FAF lobby server (blocked by FAF's
anti-smurf UID — see COLLAB_PLAYTAB.md §2/§10). The open-lobby list comes from the user's own
desktop FAF client (fork `GuyFran/client`, branch `companion-relay`) which relays `game_info`
over the home LAN (`ws://<pc>:6900`, token-gated). Chat and the REST API are direct phone→FAF.

## Tech Stack
- Kotlin + Jetpack Compose, Hilt DI, Material 3
- OkHttp WebSocket (IRC chat + LAN companion relay), Retrofit (REST API)
- OAuth2: device-code flow (working, `DeviceCodeAuth`) + browser redirect/PKCE via AppAuth
  (kept for when FAF registers a mobile client id)
- DataStore for tokens + companion pairing config

## Architecture
- `auth/` - device-code + browser OAuth2, token store/refresh
- `chat/` - IRC client over WebSocket to chat.faforever.com (binary.ircv3.net, SASL)  [WORKING]
- `companion/` - LAN relay client: `RelayClient` (transport), `SnapshotAssembler` (display
  state machine), `LobbyEventReducer` (pure notification events), `CompanionPrefs` (pairing)
- `games/` - Play tab UI, fed by `companion/` (NOT by FAF directly)
- `profile/` - ratings (`/data/leaderboardRating`) + history graph (`leaderboardRatingJournal`)
- `network/` - config, API service, `SessionManager` (session-wide chat connection)
- `navigation/` - bottom nav: Chat, Play, Profile
- `di/`, `ui/theme/`

## FAF Server Endpoints (direct from phone)
- OAuth: https://hydra.faforever.com (device-code client id in FafConfig)
- API: https://api.faforever.com  ·  User API: https://user.faforever.com (IRC token)
- IRC Chat: wss://chat.faforever.com:443 (subprotocol: binary.ircv3.net)
- Companion relay: ws://<pc-lan-ip>:6900 (pairing: see fork's COMPANION.md)

## Scope
- Chat Lobby: full IRC chat with channels, user list, history
- Play: read-only open-lobby list with players per game (no join/host/matchmaking)
- Profile: username, ratings per leaderboard, rating-evolution chart

## Build
Open in Android Studio or use the VS Code tasks ("FAF Mobile: ...").
Requires Android SDK 36 and JDK 17. Version lives in app/build.gradle.kts (versionName).

## Read first
AGENTS.md (status/backlog) → COLLAB_PLAYTAB.md §9–§10 (ratified companion decision).
