# UI / UX (as implemented)

Written from the code on 2026-11-24, not checked on a device. All strings are hardcoded English.
The app name is "FAF Mobile". There are no dialogs; the only overlay is the Chat user drawer.

## Navigation

Single activity, Compose, edge-to-edge. Bottom bar: **Chat**, **Play**, **Profile**, hidden on login.
Sign-in lands on Chat. Sign-out returns to login.

## Screens

### Login

| State | Content |
|-------|---------|
| Choice | Icon, "FAF Mobile", "Forged Alliance Forever", "Sign in with code", "Sign in via redirect", hints, error text |
| Loading | Spinner replaces the buttons |
| Device code | "Enter this code on the FAF website:", monospace code, copy ("Copied!"), "Open FAF login page", "Waiting for you to sign in...", "Cancel" |

### Chat

| Element / state | Behaviour |
|-----------------|-----------|
| Top bar | Channel name or "Chat"; People icon opens "Users (N)" drawer, sorted, with elevation dot, rows not clickable |
| Connecting | Linear progress bar |
| Disconnected | "Chat disconnected" + "Reconnect" |
| No channel yet | Centered "Connecting..." or "Disconnected" |
| Channel tabs | Only when more than one conversation exists |
| Rows | `HH:mm sender: text`; `* sender action` in italics; `--- system` |
| Input | "Message...", single line, Send enabled only when connected |
| Scroll | Auto-scrolls to the bottom on each new message |

### Play

| State | Content |
|-------|---------|
| Header | "Games (N)" (filtered count); settings icon "Edit connection" once configured |
| Not configured | "Connect to your PC" form: "PC local IP (e.g. 192.168.1.20)", "Port", "Pairing token", "Save & Connect", "Cancel" when editing |
| Connecting | Progress bar |
| Waiting | Progress bar + "Paired with your PC. Waiting for the FAF client there to log in…" |
| Disconnected | Error card "Not connected to PC relay", last error or hint, "Retry", "Edit connection". The last list stays visible |
| Connected, empty | "No open lobbies right now" / "No games in progress" / "No games right now" |
| Filters | Chips: All, Open (default), Playing |
| Game card | Title, lock icon, "Host: x", n/max, map, featured mod. Tap to expand |
| Expanded card | "Team N" chips, "Observers", "Mods: ...", "Rating: a - b" |

### Profile

| Card | Content |
|------|---------|
| Header | Placeholder avatar, username or "Loading...", "ID: n" |
| Ratings | Spinner / error + "Retry" / "No rated games yet" / rows "N games · P% wins" + rating |
| Rating Evolution | Only when ratings exist. Up to 3 leaderboard chips, static 200 dp line chart with fill, latest-point marker, 5 dashed grid lines, 3 date labels (`MMM yy`). "No rated games" / "Not enough games for a graph" |
| Connections | IRC Chat: Connected / Connecting... / Disconnected. PC Companion: Connected / Waiting for PC / Connecting... / Not connected |
| Footer | "Sign out" in error color, "FAF Mobile v{version}" |

## Theme

Follows system dark/light. No dynamic color. Material 3 default typography and shapes.

| Token | Dark | Light |
|-------|------|-------|
| Primary | `#4DA6FF` | `#1E3A5F` |
| Secondary | `#D4A843` | `#D4A843` |
| Background | `#0D1B2A` | `#F5F7FA` |
| Surface | `#1B2838` | `#F5F7FA` |
| Surface variant | `#243447` | M3 default |
| On background / surface | `#E0E6ED` | `#1A1A2E` |
| On primary | `#0D1B2A` | `#F5F7FA` |
| Error | `#CF6679` | M3 default |

Launcher icon background: `#0D1B2A`.

## State persistence

None for UI. Selected tab, channel, filter, expanded card, scroll and drafts reset. This is a gap
against the global persistence rule, tracked in the [BACKLOG](BACKLOG.md).
