# Product Spec (as implemented)

Written from the code on 2026-11-24. Defects are tracked in the [BACKLOG](BACKLOG.md).

## Scope

| Area | In scope | Not implemented |
|------|----------|-----------------|
| Sign-in | Device code; browser PKCE (dormant) | Server-side token revocation |
| Chat | `#aeolus` auto-join, send/receive, `/me`, user list, inbound PM tabs and replies, 100-line history request per join, auto and manual reconnect | Join/leave/browse channels, starting a PM, topic display, mentions, unread counts, notifications, search, history paging |
| Play | Read-only open and running games with players, filters, manual PC pairing | Join, host, spectate, matchmaking, QR pairing, discovery |
| Profile | Own username, id, ratings, rating chart, connection status, sign-out | Other players' profiles, real avatar, chart interaction |
| Notifications | Pure event reducer only | Delivery, service, permission, opt-in UI |

## Sign-in

- Device code: shows a code, opens the FAF page on request, polls at the server interval (minimum 5 s).
- Scopes: `openid offline public_profile lobby`.
- Launch always enters through the login route.
- Sign-out stops IRC and the relay and clears stored auth. It asks for no confirmation.

## Chat

- Reconnect: up to 10 attempts, delay of attempt × 5 s, reset on server welcome. Channels rejoin.
- Each reconnect fetches a fresh IRC token.
- Messages live in memory only.

## Play

- The relay connects when the Play tab is first opened, not at app start.
- Pairing fields: PC IP, port (default 6900), token, from `faf_companion_pairing.txt` in the PC's home folder.
- Validation: host without `:` or whitespace, port 1–65535, non-blank token.
- Filters: All, Open (default), Playing. Sorted by player count, descending.
- Relay reconnect: up to 30 attempts, delay of attempt × 2 s capped at 20 s, reset on `hello_ok`.
  Opening Play revives a loop that gave up.

## Profile

- Rating: API `rating`, otherwise `mean − 3 × deviation`.
- Chart points: `meanAfter − 3 × deviationAfter` over `scoreTime`, latest 2000 entries.
- Leaderboard chips: top 3 by games played, default the most played.
- Named: global, ladder_1v1, tmm_2v2, tmm_3v3, tmm_4v4_full_share, tmm_4v4_share_until_death.

## Notification events (reducer, unwired)

| Event | Trigger |
|-------|---------|
| `lobby_opened` | A game appears open that was not tracked |
| `lobby_closed` | Explicit live open → closed (playing → closed is silent) |
| `lobby_launched` | Tracked open → playing |
| `lobby_full` | Rising edge to `maxPlayers` (≥ 1) |
| `lobby_almost_full` | Rising edge to `maxPlayers − 1` (`maxPlayers` ≥ 2) |

Snapshots rebuild the baseline silently. `friend_hosting` is deferred because friend data is not relayed.

## Persistence

| Store | Keys |
|-------|------|
| DataStore `faf_auth` | access and refresh tokens, expiry, username, user id |
| DataStore `faf_companion` | relay host, port, token, enabled |

Nothing else persists. Both stores are unencrypted; `allowBackup` is false.
