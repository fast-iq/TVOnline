
### 2026-11-26 Session 48 — Feature roadmap batches 1-4 (favorites, search, updates, cleanup)

Four batches per user-approved roadmap; one commit each.

**Batch 1 (`255601b`) — fallback sources, manual refresh, notifications:**
- `ChannelRepository.hardcodedFallbackChannels()`: 20 federal channels (cdn.ntv.ru URLs, ids `fallback_*`) used ONLY when LimeHD throws AND disk cache empty; saved to disk + cached like a normal fetch. Fallback EPG: ids don't start with `limehd_` → templates apply.
- Settings `refreshButton`: `refreshChannels()` + `EPGRepository.invalidateAllCaches()`.
- `NotificationHelper`: channel `tvapp_epg_reminders`, notification id = `program.hashCode()`, tap opens PlayerActivity; POST_NOTIFICATIONS permission (manifest + runtime request in MainActivity on API 33+). `notify` guarded by `checkSelfPermission` (lint MissingPermission).

**Batch 2 (`827f9dc`) — favorites, search, grouping, history, sleep timer, EPG day nav + program search:**
- Favorites: `AppPreferences.favoriteChannelIds`; star badge (`favoriteBadge`, `ic_favorite`, hidden by default) toggled by long-press on card; payload-based `refreshFavorites()` (no Glide reload).
- `ChannelAdapter` now serves `GridItem` sealed class (Header/Card) with `TYPE_HEADER`/`TYPE_CARD` public consts + `SpanSizeLookup` (header spans full row).
- `ChannelGrouping` (pure): category order/headers, favorites/history filters.
- MainActivity view modes ALL/FAVORITES/HISTORY (header star/clock buttons, BACK exits mode); focus restore switched from position to channel id (`lastFocusedChannelId` + `adapterPositionForChannel`).
- History: `AppPreferences.watchHistory` (max 20, comma-joined), recorded on open in Main/Search/Player (incl. DPAD switch + number select).
- `SearchActivity` (EditText + results list, `filterChannels` in companion: lowercase + strip spaces substring) — registered in manifest, header search button (`ic_search`).
- Player sleep timer: INFO key dialog 0/15/30/60/90 min → `sleepTimerJob` delay → `playerManager.pause()` + toast.
- EPG day navigation: MENU dialog (-1..+6 days); non-today window = full day (1440 min from midnight) via `windowMinutes()` (all width/clamp/scale math switched from `totalMinutes` to `windowMinutes()`); `EPGRepository.getProgramsForDay(channels, date, dayDelta)` uses LimeHD offset `from = dayDelta+1, limit = 1` (session 40 semantics).
- EPG program search: INFO key → EditText dialog, searches `accumulatedPrograms` (≤50 results); result click = moveCursor if in window else openPlayer.
- Kotlin gotchas hit: `prefs.getStringSet(...)` returns nullable platform type → `?.toMutableSet() ?: emptySet()`; `val history` reassign → make property `List<String>` and assign `if (size>20) take(20) else history`.

**Batch 3 (`d2b142e`) — update check + settings export/import:**
- `UpdateChecker` (GitHub Releases `fast-iq/TVOnline` `releases/latest`): `checkDetailed` → Available/UpToDate/Failed; pure internal `parseRelease` (apk via assets `.apk`), `isNewerVersion` (numeric dot compare, `v` prefix, garbage→0). Manual button in Settings + silent once-per-24h check in MainActivity (`AppPreferences.lastUpdateCheckMs`); "Скачать" opens apk/release URL via ACTION_VIEW.
- `SettingsBackup` (internal, pure): typed JSON entries (`{"key","type","value"}`) for String/Boolean/Int/Long/Float/Set<String>, unknown types skipped; `apply(prefs, values)` re-puts by type. Export/import buttons in Settings → `getExternalFilesDir(null)/tvapp_settings.json`; import invalidates content + `recreate()`.
- Kotlin gotcha: `mapOf("i" to 42, "l" to 42L)` widens Int literal to Long — test needed explicit `mapOf<String, Any>(...)`.

**Batch 4 (this commit) — dead code removal, manifest hardening, tests, lint cleanup:**
- Deleted: `ChannelInfoActivity.kt` + manifest entry + `activity_channel_info.xml`, `EPGAdapter.kt`, `item_program.xml`, `item_channel_epg.xml`; then lint-flagged cascading dead resources: `button_focusable.xml`, `focus_highlight.xml`, strings `start_watching`/`channel_image_desc`/`press_ok_to_start`/`program_time_placeholder`/`program_name_placeholder` (ru+en).
- Manifest: `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|locale|layoutDirection|uiMode"` on all activities (no activity recreation on TV config events, player never restarts on keyboard/uimode changes); verified `TVApplication`, POST_NOTIFICATIONS, SearchActivity entries.
- Lint fixes: ObsoleteSdkInt (minSdk 28 → dropped `SDK_INT >= O` guard in NotificationHelper), Untranslatable (removed `channel_number_format`/`live_badge` from values-en), PluralsCandidate (`epg_day_format` string → plurals ru+en, EPGActivity uses `resources.getQuantityString`).
- Tests added: `ChannelGroupingTest` (5), `SearchFilterTest` (4, uses `ChannelList.updateChannels` + `SearchActivity.filterChannels` companion; note "ТНТ" does NOT contain "тв"), `UpdateCheckerTest` (6), `SettingsBackupTest` (7). Total 85 (was 63).

**Verification:** local SDK `testDebugUnitTest` 85/85, `ktlintCheck`, `lintDebug` (only machine-specific `local.properties` PropertyEscape, absent in CI), `assembleDebug` PASSED.
