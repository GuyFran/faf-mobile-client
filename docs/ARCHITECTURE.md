# Architecture

## System

```
FAF lobby ──game_info──► Desktop FAF client (fork, authenticated, has UID)
                                │  src/companion/relay.py, port 6900, token-gated
                                ▼  ws:// over the home LAN
                         Phone (RelayClient → SnapshotAssembler → Play tab)

Phone ──wss──► chat.faforever.com (IRC)
Phone ──https─► hydra / api / user.faforever.com
```

## Ratified decision (2026-09-01)

Full reasoning: [history/COLLAB_PLAYTAB.md](history/COLLAB_PLAYTAB.md) §10 (history only).

| Topic | Decision |
|-------|----------|
| Why a relay | Open lobbies exist only on the lobby WebSocket. It needs FAF's native UID, and a second login evicts the desktop session. REST has no pre-launch games |
| Phone role | Read-only viewer. No lobby login, no UID |
| Security tier | Trusted LAN, experimental. `ws://` + 128-bit token does **not** resist a hostile peer on the same Wi-Fi |
| Before sharing | Pinned `wss://`: paired certificate as sole trust anchor, hostname verification kept, QR pairing |
| Before notifications | Gates A and B, see [STATUS](STATUS.md) |
| Rejected | Reproducing or porting `faf-uid`, placeholder-UID logins, desktop-UID relay |

## Modules (`app/src/main/java/com/faforever/mobile/`)

| Package | Role |
|---------|------|
| `auth/` | Device-code and browser OAuth, token store, refresh, login UI |
| `chat/` | `IrcClient`, `ChatRepository`, chat UI |
| `companion/` | `RelayClient` (transport), `SnapshotAssembler` (display state), `LobbyEventReducer` (pure, unwired), `CompanionPrefs`, `CompanionRepository` |
| `games/` | Play tab UI and models, fed by `companion/` |
| `profile/` | Ratings, history, `RatingChart`, profile UI |
| `network/` | `FafConfig`, `FafApiService`, `SessionManager` |
| `navigation/` | Bottom nav host |
| `di/`, `ui/theme/` | Hilt module, theme |

## Endpoints

| Service | URL |
|---------|-----|
| OAuth | `https://hydra.faforever.com` (`/oauth2/auth`, `/oauth2/token`, `/oauth2/device/auth`, `/userinfo`) |
| API | `https://api.faforever.com` (`/data/leaderboardRating`, `/data/leaderboardRatingJournal`, bearer token) |
| IRC token | `GET https://user.faforever.com/irc/ergochat/token` |
| IRC | `wss://chat.faforever.com:443`, subprotocol `binary.ircv3.net`, binary frames |
| Relay | `ws://<pc-lan-ip>:6900` |

Client ids are in `network/FafConfig.kt`. Redirect URI: `com.faforever.mobile://oauth/callback`.

## IRC

`CAP REQ :sasl` and `:server-time`, SASL PLAIN with the IRC token, `CAP END`, NICK, USER.
Joins `#aeolus`, then sends `CHATHISTORY LATEST <channel> * 100`.

## Relay protocol

The authoritative definition is the fork's `src/companion/relay.py`. Newline-terminated JSON.

| Direction | Message | Phone effect |
|-----------|---------|--------------|
| → relay | `{"type":"hello","token":"..."}` | Sent on open |
| ← relay | `hello_ok` | WAITING, retry counter reset |
| ← relay | `source_offline` | Clears games, WAITING |
| ← relay | `snapshot_begin{epoch}` | Starts staging |
| ← relay | `game_info` line | Staged, or applied live |
| ← relay | `snapshot_end{epoch}` | On matching epoch: commit, CONNECTED. Otherwise ignored |

Phone states: `DISCONNECTED`, `CONNECTING`, `WAITING`, `CONNECTED`. Callbacks from superseded sockets
are rejected. A reconnect aborts staging but keeps the committed list.
