# FAF Mobile Client

## Project
Android mobile client for Forged Alliance Forever (FAF), porting the Chat Lobby and Play tabs from the original Python client.

## Tech Stack
- Kotlin + Jetpack Compose
- Hilt for DI
- OkHttp WebSocket for IRC chat and lobby server
- Retrofit for REST API
- AppAuth for OAuth2
- DataStore for token persistence

## Architecture
- `auth/` - OAuth2 login via browser redirect (PKCE)
- `chat/` - IRC client over WebSocket to chat.faforever.com:443
- `games/` - Lobby WebSocket to ws.faforever.com for game list
- `network/` - Shared config, API service, Retrofit setup
- `navigation/` - Bottom nav with Chat Lobby and Play tabs
- `di/` - Hilt dependency injection
- `ui/theme/` - Material 3 dark/light theme

## FAF Server Endpoints
- OAuth: https://hydra.faforever.com
- API: https://api.faforever.com
- Lobby WS: wss://ws.faforever.com
- IRC Chat: wss://chat.faforever.com:443 (subprotocol: binary.ircv3.net)
- Content: https://content.faforever.com

## Scope
- Chat Lobby: full IRC chat with channels, user list, message history
- Play: read-only game list with players per game (no join/host/matchmaking)

## Build
Open in Android Studio, sync Gradle, run on device/emulator.
Requires Android SDK 35 and JDK 17.
