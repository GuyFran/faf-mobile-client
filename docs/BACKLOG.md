# Backlog

Open work only. Shipped work is in the [CHANGELOG](CHANGELOG.md). "Audit" = code audit of 2026-11-24,
by reading code only: none of these defects was reproduced on a device.

## Release gates and roadmap

| Item | Priority | Source / date |
|------|----------|---------------|
| Gate A: desktop suite on real Python 3.14 / CI | High | Decision §10.5, 2026-09-01 |
| Gate B: live phone↔desktop e2e | High | Decision §10.5, 2026-09-01 |
| Wire notifications (reducer → user-started foreground service), after A + B | Medium | Decision §10.5, 2026-09-01 |
| Pinned `wss://` + QR pairing, before sharing the app | Medium | Decision §10.4, 2026-09-01 |
| Desktop settings UI for the companion (enable, pairing, regenerate) | Medium | Fork `COMPANION.md`, 2026-09 |
| `RelayClient` callback/epoch tests with an OkHttp fake | Medium | Decision log Turn 21, 2026-09-01 |
| Register a dedicated mobile OAuth client id with FAF | Low | 2026-09 |
| Optional FAF coordination (sanctioned read-only feed) | Low | Decision §10.6, 2026-09-01 |
| Map thumbnails, PM UI, notification badges | Low | Original nice-to-haves |

## Known defects

| Defect | Where | Priority | Source / date |
|--------|-------|----------|---------------|
| Own sent messages likely never appear (no local echo, no `echo-message`) | `chat/ChatRepository.kt:96` | High | Audit 2026-11-24 |
| Channel notices have no message id, so dedupe can drop later ones | `chat/IrcClient.kt:344`, `ChatRepository.kt:110` | Medium | Audit 2026-11-24 |
| Tokens and pairing token stored unencrypted in DataStore | `auth/TokenManager.kt`, `companion/CompanionPrefs.kt` | Medium | Audit 2026-11-24 |
| Cleartext allowed app-wide, not only for the LAN relay | `AndroidManifest.xml:13` | Medium | Audit 2026-11-24 |
| Sign-out keeps pairing config and in-memory chat and games | `profile/ProfileViewModel.kt:135` | Medium | Audit 2026-11-24 |
| Token refresh: no expiry margin, no mutex, no 401 handling, always uses the device client id | `auth/DeviceCodeAuth.kt:156` | Medium | Audit 2026-11-24 |
| Tab navigation pops to the already-removed `login` start destination, so back stack and tab state may misbehave | `navigation/FafNavHost.kt:53` | Medium | Audit 2026-11-24 |
| KICK, NICK and MODE unhandled; reconnect does not remove departed users | `chat/IrcClient.kt`, `ChatRepository.kt:146` | Medium | Audit 2026-11-24 |
| Chat always jumps to the bottom on a new message, even when scrolled up | `chat/ChatScreen.kt:178` | Low | Audit 2026-11-24 |
| Cancelling device login can show a cancellation error | `auth/AuthViewModel.kt:104` | Low | Audit 2026-11-24 |
| "Try again" on the device-code screen is unreachable | `auth/LoginScreen.kt:307` | Low | Audit 2026-11-24 |
| Login hint says the browser opens, but a code screen comes first | `auth/LoginScreen.kt` | Low | Audit 2026-11-24 |
| OAuth requests the unused `lobby` scope | `network/FafConfig.kt` | Low | Audit 2026-11-24 |

## Global-rule gaps

| Gap | Rule | Source / date |
|-----|------|---------------|
| No UI state persists (selected tab, channel, filter, chart leaderboard) | Global "Persist ALL UI geometry and layout" | Audit 2026-11-24 |

## Unused code to remove or wire

`TokenManager.accessTokenBlocking`, `AuthViewModel.logout`, `ChatViewModel.joinChannel`,
`ChatRepository.leaveChannel`, `FafConfig.CONTENT_URL`, `unreadCount`, `FafBlue`, `FafBlueLight`.
Source: audit 2026-11-24.
