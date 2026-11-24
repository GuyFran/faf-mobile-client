# UI persistence inventory

Owner rule (global `~/.claude/CLAUDE.md` → "Persist ALL UI geometry and layout"): every screen,
panel, drawer, list and view state reopens as left, across rotation **and** process death.

**Store:** one Preferences DataStore file `faf_ui_state`, wrapped by
`ui/state/UiStatePrefs.kt` (loaded synchronously when MainActivity is injected, before the first
frame; writes are immediate in memory, persisted in order in the background). Composables reach it
through `LocalUiState`. Generic helpers in `ui/state/PersistedScroll.kt`:
`rememberPersistedLazyListState(key, ready)` and `rememberPersistedScrollState(key)` (saveable for
rotation, saved debounced 400 ms + on stop + on dispose, restored once content exists, clamped to
the list size). Tokens and relay pairing stay in their own existing stores (`faf_auth`,
`faf_companion`); they are data, not layout.

Inventory taken from the code on 2026-11-25 (1.1.6): every Compose screen under `app/src/main`.

| Element | What is kept | Key | Status |
|---|---|---|---|
| Activity window | Single full-screen activity; no movable/resizable geometry on Android phones | — | n/a |
| Bottom navigation (Chat / Play / Profile) | Last tab; reopened after the (auto-)login | `nav.tab` | persisted |
| Login screen | No adjustable state (choice / device-code panel follows auth state); "Copied!" confirmation label after copying the code (feedback, not layout) | — | exempt: toast-like feedback |
| Chat: channel tabs (`ScrollableTabRow`) | Selected channel; reselected as soon as it is joined again (automatic fallback picks are not saved) | `chat.channel` | persisted |
| Chat: users drawer (`ModalNavigationDrawer`) | Open / closed (settled value) | `chat.usersDrawerOpen` | persisted |
| Chat: users drawer list | Scroll position, per channel | `scroll.chat.users.<channel>` | persisted |
| Chat: message list | Scroll position per channel + "follow newest" (on while at the bottom, off after scrolling up) | `scroll.chat.messages.<channel>`, `chat.follow.<channel>` | persisted |
| Chat: message input | Draft text | — | not layout (draft) |
| Play: filter chips (All / Open / Playing) | Selected filter | `games.filter` | persisted |
| Play: lobby list | Scroll position, per filter | `scroll.games.list.<FILTER>` | persisted |
| Play: expanded lobby card | Expanded game uid | `games.expandedUid` | persisted |
| Play: connection editor panel (in-screen sheet) | Open / closed when reopened from settings | `games.connectionEditorOpen` | persisted |
| Play: connection editor fields | Host / port / token draft | — | not layout (draft); saved values live in `faf_companion` |
| Profile: page scroll (`verticalScroll`) | Scroll offset (px), restored once ratings are measured | `scroll.profile.page` | persisted |
| Profile: rating-evolution leaderboard chips | Selected leaderboard (falls back to most-played if it no longer exists) | `profile.leaderboard` | persisted |
| Profile: rating chart (`RatingChart`) | Static drawing, no zoom/pan | — | n/a |

Counts: 16 elements; 11 newly persisted; 0 previously persisted; 1 feedback label exempt; 2 drafts; 2 with
no adjustable state. Add any new screen element to this table in the same change.
