# FAF Mobile Client - Multi-AI Agents Doc

## Project Status
| Item | Status |
|------|--------|
| Version | 1.0.0 |
| Platform | Android (Kotlin + Jetpack Compose) |
| Build system | Gradle + Version Catalog |
| Last updated | 2026-09-01 |

## Implemented Features
| Feature | Status | Notes |
|---------|--------|-------|
| Project scaffold | Done | Gradle, app module, manifest |
| OAuth2 login | Done | AppAuth + PKCE via browser redirect |
| IRC Chat client | Done | Custom IRC parser over OkHttp WebSocket |
| Chat UI | Done | Channel tabs, message list, user drawer, send |
| Lobby WebSocket | Done | Connects to wss://ws.faforever.com |
| Game list UI | Done | Cards with expand for teams/players |
| Theme | Done | Material 3, dark FAF-inspired colors |
| Navigation | Done | Bottom bar: Chat Lobby + Play |

## Backlog
| Item | Priority | Notes |
|------|----------|-------|
| Register OAuth client ID with FAF | High | Current ID is placeholder; need to contact FAF admins |
| Token refresh flow | Medium | AppAuth handles refresh but needs testing |
| Map preview thumbnails | Low | Load from content.faforever.com |
| Player rating display in game cards | Low | Use player_info data |
| Private messages | Low | IRC DMs work but no UI yet |
| Notifications | Low | New message badges, background service |
| Settings screen | Low | Server config, theme toggle |

## Architecture
```
com.faforever.mobile/
  auth/          OAuth2 PKCE flow
  chat/          IRC WebSocket client + chat UI
  games/         Lobby WebSocket + game list UI
  network/       Config, API service
  navigation/    Bottom nav host
  di/            Hilt modules
  ui/theme/      Material 3 theme
```

## Key Dependencies
- OkHttp 4.12 (WebSocket for both IRC and lobby)
- Retrofit 2.11 (REST API)
- AppAuth 0.11 (OAuth2)
- Hilt 2.53 (DI)
- Compose BOM 2024.12 (UI)

## Read Order
1. `CLAUDE.md` - project overview
2. `FafConfig.kt` - all server endpoints
3. `IrcClient.kt` - IRC protocol implementation
4. `LobbyClient.kt` - lobby server protocol
5. `ChatScreen.kt` / `GamesScreen.kt` - UI
