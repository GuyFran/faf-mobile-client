# Status

Version: see [AGENTS.md](../AGENTS.md). Last reviewed 2026-11-24 by code reading. No build, test or
device run was done for that review.

## Features

| Feature | State | Evidence |
|---------|-------|----------|
| Device-code login | Working | Live use, 2026-09 |
| Browser login (PKCE) | Dormant | Needs a FAF-registered mobile client id |
| Chat: receive, channel, users, history | Working | Live use, 2026-09 |
| Chat: seeing your own sent message | **Suspected defect** | Code: no local echo, `echo-message` not requested. Not checked on a device |
| Play: open lobbies via relay | Working against a mock | On-device mock e2e passed 2026-09-02 (pairing, snapshot, streaming, expand, filters, edit connection) |
| Play: real desktop client e2e | **Not run** | Release gate B |
| Profile: ratings + chart | Working | Live use, 2026-09 |
| Notification reducer | Built, **not wired** | No production caller. No service, notification code or permission |
| Desktop relay | Built in the fork | Tracked in the fork's docs |
| Direct FAF lobby login | Removed | Deliberate, see [ARCHITECTURE](ARCHITECTURE.md) |

## Tests

| Suite | Tests | Last recorded run |
|-------|-------|-------------------|
| `LobbyEventReducerTest` | 19 | Passed 2026-09-01 |
| `SnapshotAssemblerTest` | 9 | Passed 2026-09-01 |
| **Total** | **28** | Not re-run 2026-11-24 |

No automated tests cover UI, auth, chat, the relay socket or profile.

Desktop relay, last recorded 2026-09-01: 11/11 on Python 3.12, 15/15 with a compatibility shim.
A real Python 3.14 run is still owed (gate A) unless the fork's own docs record one.

## Release gates

| Gate | Requirement | State |
|------|-------------|-------|
| A | Desktop suite on real Python 3.14 / CI | Open |
| B | Live phone↔desktop e2e: snapshot, live add/update/close, lobby reconnect, phone reconnect, bad token, source offline↔ready | Open |
| Distribution | Pinned `wss://` + QR pairing | Open |
