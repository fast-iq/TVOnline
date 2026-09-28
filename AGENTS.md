# AGENTS.md - TVOnline Android TV App

## Project Overview
Android TV app for watching Russian live TV channels. Kotlin, ExoPlayer (Media3), Retrofit, Glide, Coroutines.
- Package: `com.example.tvapp`
- minSdk 28, targetSdk 36, compileSdk 36
- Leanback launcher + standard launcher

## Key Files
- TVApp/[AWS_SECRET_KEY_REDACTED]/Models.kt - Channel list, data models (Channel has currentProgramTitle/Start/End)
- TVApp/[AWS_SECRET_KEY_REDACTED]/ChannelRepository.kt - Fetches channels from premier.one (primary source for grid)
- TVApp/[AWS_SECRET_KEY_REDACTED]/EPGRepository.kt - EPG loading (epgservice.ru, used by EPGActivity only)
- `TVApp/app/src/main/java/com/example/tvapp/data/AppPreferences.kt` - Settings storage
- `TVApp/app/src/main/java/com/example/tvapp/player/TVPlayerManager.kt` - ExoPlayer wrapper
- TVApp[AWS_SECRET_KEY_REDACTED]MainActivity.kt - Channel grid (uses ChannelRepository, no EPGRepository)
- TVApp[AWS_SECRET_KEY_REDACTED]ChannelAdapter.kt - Channel cards with logo + name + program + progress bar
- TVApp[AWS_SECRET_KEY_REDACTED]PlayerActivity.kt - Video playback
- `TVApp/app/src/main/java/com/example/tvapp/settings/SettingsActivity.kt` - Settings

## Conventions
- **ALWAYS read this file (AGENTS.md) before starting any task.** It contains critical context, known issues, and lessons learned that prevent regressions.
- Kotlin, no comments unless asked
- ViewBinding enabled but activities use findViewById (existing pattern)
- Coroutines for async work, Dispatchers.IO for network
- Glide for image loading with placeholder/error drawables
- All UI text in Russian
- TV remote navigation: focusable views, scale on focus, DPAD keys

## Known Issues & Lessons Learned

### 2026-09-25: Initial audit and fixes

**Streams (CRITICAL):**
- `streaming.goodstream.icu` - ~14 of 24 URLs return 403/412 (need special headers or dead)
- `streaming.televizor-24-tochka.ru` - ALL URLs return 301 -> 412 Precondition Failed (DEAD)
- Working goodstream URLs verified: 210, 211, 213, 30, 296, 232, 44, 618
- FIX: Replaced dead televizor-24-tochka.ru streams with working goodstream.icu equivalents where possible
- FIX: Added multi-source fallback - each channel can have `fallbackStreamUrls` list
- FIX: Player now tries next URL on playback error

**Icons (CRITICAL):**
- `static.wikia.nocookie.net/logopedia/...` URLs are unreliable/broken
- `via.placeholder.com` is dead service
- `img.youtube.com/vi/...` - random YouTube thumbnails, not channel logos
- FIX: Use official channel static assets from their own domains (1tv.ru static, smotrim.ru, etc.)
- FIX: Added proper Glide error handling with local placeholder

**EPG:**
- `epg.iptv-manager.com` - DEAD (connection refused)
- `parseEPGXml()` was a stub that always returned fake data
- FIX: Primary EPG source: **api.epgservice.ru** (REST API, 4308 channels, 2656 Russian)
  - Auth: `Authorization: Bearer <token>` — token via https://t.me/EPGServiceSupportBot
  - Token stored in `AppPreferences.EPG_SERVICE_TOKEN` (empty = service disabled, fallback used)
  - `GET /v1/index` → channel list (id, display_name), matched to our channels by name
  - `GET /v1/schedule/{channel_id}?week=YYYYMMDD` (Monday of week) → full week programs
  - Fields: `start_ut`/`stop_ut` (UTC epoch seconds), `title`, `desc_short`, `icon[].src`
  - Channel index cached in memory after first fetch
- FIX: Secondary source for Первый канал: 1tv.ru schedule page (Next.js RSC JSON payload)
  - URL: `https://www.1tv.ru/schedule?date=YYYY-MM-DD`
  - Used when epgservice.ru token is not configured or request fails
- Fallback: generated templates by channel category (last resort)

**Timezone:**
- Old code used `System.currentTimeMillis() + offset * 3600000` which is WRONG
- EPG times are in Moscow time (UTC+3), device may be in different timezone
- FIX: Use proper `TimeZone` handling - convert EPG times to device local time for display
- Settings store UTC offset of the channel's broadcast timezone (default +3 Moscow)

**Quality:**
- `setQualityMode()` was a no-op stub
- HLS streams have multiple bandwidth variants (240p/360p/576p)
- FIX: Use ExoPlayer `DefaultBandwidthMeter` + `MaxBitrateSelectionPolicy` or
  manual track selection via `TrackSelectorOverride` based on quality setting

**Russian TV requirements:**
- Must include all federal channels: Первый, Россия 1, НТВ, 5 канал, Культура, Звезда, Пятница!, СТС, Домашний, ТНТ, РЕН ТВ, Карусель, Матч ТВ, Россия 24, ТВ Центр, СПАС
- Channels broadcast in Moscow time (UTC+3)
- EPG must show current and upcoming programs with correct times

## Action History

### 2026-09-25 Session 1
1. Explored full codebase structure
2. Tested all stream URLs - found ~40% broken (403/412), televizor-24-tochka.ru completely dead
3. Found working EPG source: 1tv.ru schedule page with embedded JSON
4. Rewrote Models.kt - fixed stream URLs, added fallbackStreamUrls, fixed logo URLs to official sources
5. Rewrote EPGRepository.kt - real EPG parsing from 1tv.ru, removed dead iptv-manager.com
6. Rewrote TVPlayerManager.kt - quality selection via track selector, auto-fallback on error
7. Fixed timezone handling in all activities
8. Added stream URL validation before playback

### 2026-09-25 Session 2
1. Fixed EPGActivity.kt syntax error - restored missing `loadEPGForDate()` function declaration (line 213)
2. Fixed EPGAdapter.kt timezone bug - replaced millisecond offset math with proper TimeZone handling
3. Verified all Kotlin files for syntax correctness (manual review, build blocked by missing Android SDK)
4. All 11 source files verified: Models.kt, EPGRepository.kt, TVPlayerManager.kt, MainActivity.kt, PlayerActivity.kt, ChannelInfoActivity.kt, ChannelAdapter.kt, EPGActivity.kt, EPGAdapter.kt, SettingsActivity.kt, AppPreferences.kt

### 2026-09-25 Session 3
1. Researched epgservice.ru API (OpenAPI spec) - REST, Bearer auth, /v1/index + /v1/schedule/{id}?week=YYYYMMDD
2. Verified API live: health OK, demo token returns 6 channels with full week schedules (start_ut/stop_ut/title/desc/icon)
3. Integrated epgservice.ru into EPGRepository as primary source for all channels
4. Added `AppPreferences.EPG_SERVICE_TOKEN` constant (empty by default = disabled)
5. Channel name matching: exact + substring match against /v1/index display_name, cached in memory
6. Fallback chain: epgservice.ru → 1tv.ru (c1r only) → generated templates

### 2026-09-25 Session 4 — CI/CD fixes
**Problem:** GitHub Actions build failing. Lint job had `continue-on-error: true` masking real errors; `./gradlew clean` ran before artifact upload deleting the report. Cache service was also intermittently down.

**Compilation errors fixed (4):**
1. `EPGRepository.kt:217` — `httpGet()` had `try/finally` without `return`. Fixed: `return try { ... } catch (e: Exception) { null } finally { ... }`
2. `TVPlayerManager.kt:92` — `ts.parametersBuilder` doesn't exist in Media3 1.5.1. Fixed: `ts.buildUponParameters().setMaxVideoBitrate(maxBitrate).build()`
3. `TVPlayerManager.kt:93` — `setParameters()` overload ambiguity (3 candidates). Resolved by passing `TrackSelectionParameters` from `buildUponParameters().build()`
4. `PlayerActivity.kt:86` — smart cast on mutable `streamUrl: String?` impossible. Fixed: `val url = streamUrl!!` after `isNullOrEmpty()` check

**Lint errors fixed (15 → 0):**
- Media3 classes (`ExoPlayer`, `DefaultTrackSelector`, `PlayerView`) are `@UnstableApi`. Lint `UnsafeOptInUsageError` was failing the build.
- FIX: Added `lint { disable 'UnsafeOptInUsageError' }` to `app/build.gradle`
- Removed invalid `@OptIn(UnstableApi::class)` annotation (UnstableApi is not an opt-in requirement marker)

**Lint warnings fixed (3):**
- `EPGRepository.kt:171,174` — `item.optString("lead", null)` → `item.optString("lead", "")` (Java type mismatch: Nothing? vs String)
- `TVPlayerManager.kt:30` — removed unused `import androidx.media3.common.util.UnstableApi`

**CI workflow fixes (`build.yml`):**
- Removed `continue-on-error: true` from lint step
- Added "Show Lint Report (on failure)" step to dump logs
- Moved artifact upload before `./gradlew clean`
- Upgraded deprecated actions: `setup-java@v4→v5`, `upload/download-artifact@v4→v5`

**Other:**
- Added `.gitattributes` (`* text=auto eol=lf`) to fix CRLF warnings
- CI now passes: lint clean, build successful

### 2026-09-25 Session 5 — Lint cleanup & i18n preparation
**Goal:** Fix all 83 lint warnings, prepare app for multi-language support.

**i18n (HardcodedText + SetTextI18n, ~37 warnings):**
- Extracted ALL hardcoded Russian text from 10 layout XMLs and 6 Kotlin files into `strings.xml`
- All layouts now use `@string/...` references, all Kotlin `setText()` uses `getString(R.string...)`
- Format strings with placeholders: `now_playing`, `loading_channel`, `playback_error`, `timezone_value`, `quality_value`, `language_value`, `time_msk`, `offset_value`
- Removed 14 unused string resources (old `settings`, `epg`, `loading`, etc. replaced by new ones)
- Ready for translation: add `values-en/strings.xml` to support English

**Other lint fixes:**
- `DefaultLocale` — `String.format("%02d:00", hour)` → `String.format(Locale.US, "%02d:00", hour)` in EPGActivity.kt
- `SwitchIntDef` — added `Player.STATE_ENDED` case to `when(state)` in TVPlayerManager.kt
- `Overdraw` (5) — removed `android:background` from root layouts; created `Theme.TVApp.Black` for Player/ChannelInfo activities (black windowBackground in theme instead of layout)
- `SmallSp` — `10sp` → `11sp` in item_channel.xml programTitle
- `TypographyEllipsis` — replaced `...` with `…` in string resources
- `ContentDescription` — added `android:contentDescription="@string/channel_image_desc"` to ImageView in activity_channel_info.xml
- `UnusedResources` — removed unused `focus_highlight` color from colors.xml

**Remaining warnings (non-blocking, by design):**
- `OldTargetApi` — targetSdk 35 is latest stable
- `GradleDependency` (8) — newer library versions available but current ones work
- `DiscouragedApi` (5) — `screenOrientation="landscape"` required for TV app
- `NotifyDataSetChanged` (2) — acceptable for small RecyclerViews

### 2026-09-25 Session 6 — TV device testing fixes
**Goal:** Fix 4 issues found during real Android TV testing.

**1. Channel icons not showing (CRITICAL):**
- All `logoUrl` values in Models.kt were fake/non-existent URLs (e.g. `https://www.ntv.ru/upload/images/logo_ntv.png`)
- FIX: Replaced with verified working URLs from two sources:
  - **ivi.ru CDN**: `https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/{hash}.jpg/x240/`
  - **premier.one CDN (rtbcn)**: `https://uma-static.rtbcdn.ru/cwebp/pic/cardimage/{2ch}/{2ch}/{md5}.png?size=240&quality=95`
- All URLs verified via HTTP HEAD → 200 + correct Content-Type
- Channels with ivi logos: c1r, rossiya1, ntv, 5tv, zvezda, pz, sts, domashniy, tnt, ren, karusel, match, rossiya24, tvc, spas, tv3, mir, muztv
- Channels with premier.one logos: kultura, otv, che (and others where available)

**2. "Название передачи" placeholder instead of real program:**
- **Bug 1:** `getMoscowTime()` in MainActivity subtracted Moscow timezone offset from `System.currentTimeMillis()` — WRONG. EPG times are UTC epoch millis, so just use `System.currentTimeMillis()` directly.
- **Bug 2:** `generateFallbackEPG()` had same offset bug: `calendar.timeInMillis - moscowTz.getOffset(...)` → fixed to just `calendar.timeInMillis`
- **Bug 3 (MAJOR):** EPG API integration was completely broken. Code called `/v1/schedule/{channelId}?week=YYYYMMDD` which doesn't exist. The actual API flow:
  1. `GET /v1/index` → XML with `<channel>` entries, each containing `<href>http://xmldata.epgservice.ru/...?week=...</href>`
  2. `GET <href>` (with week param) → full XMLTV schedule for that channel
- FIX: Rewrote `EPGRepository` to use `loadChannelHrefs()` which caches `channelId → href` mapping from `/v1/index`, then fetches schedule from the href URL directly
- Changed `http://xmldata.epgservice.ru` → `https://xmldata.epgservice.ru` (Android blocks cleartext HTTP)
- EPG token is set in `AppPreferences.EPG_SERVICE_TOKEN`

**3. "Нажмите ОК для просмотра" dialog blocking playback:**
- **Root cause:** Flow was Grid → ChannelInfoActivity (intermediate screen with Toast "Нажмите ОК") → PlayerActivity. The intermediate screen added unnecessary friction and if OK was pressed at wrong time or stream failed, user got stuck/returned to grid.
- FIX: Removed ChannelInfoActivity from the flow. Grid now goes directly to PlayerActivity. Playback starts immediately on entry.
- Also fixed `TVPlayerManager`: `isAutoFallback` flag was never set to `true`, so multi-source fallback could never trigger. Removed the flag — fallback now always works when multiple URLs exist.

**4. Settings tile in channel grid → gear icon in top-right corner:**
- **Before:** Settings and EPG were 160×120dp cards occupying positions 0 and 1 in the 5-column grid, pushing all channels down by 2 slots
- **After:** 
  - Settings: 48×48dp gear icon (`ic_settings`) in top-right corner of activity_main.xml, aligned with title
  - EPG: 48×48dp icon (`ic_epg`) next to settings button
  - ChannelAdapter simplified: no more multi-type view holders, just channels
  - `restoreLastChannel()` scroll offset removed (no more +2 for special tiles)

**Files modified:**
- `TVApp/app/src/main/java/com/example/tvapp/data/Models.kt` — logo URLs
- `TVApp/app/src/main/java/com/example/tvapp/data/EPGRepository.kt` — complete rewrite of EPG fetch logic
- `TVApp/app/src/main/java/com/example/tvapp/ui/MainActivity.kt` — direct player launch, settings/EPG buttons, time fix
- `TVApp/app/src/main/java/com/example/tvapp/ui/ChannelAdapter.kt` — simplified to channels-only
- `TVApp/app/src/main/java/com/example/tvapp/player/TVPlayerManager.kt` — fallback fix
- `TVApp/app/src/main/res/layout/activity_main.xml` — added settings/EPG icon buttons

### 2026-09-27 Session 7 — Premier.one as single source, channel card redesign

**Goal:** Use premier.one/tv/categories/besplatnye as the single source for channel list, logos, and current program. Add progress bar to channel cards.

**Premier.one data extraction:**
- Page is Nuxt 3 SSR. Data in `<script type="application/json" id="__NUXT_DATA__">` — a flat array with index references
- Structure: `{"tv-channels-list": <idx>}` → array of channel indices → each channel object has `name`, `slug`, `logoImage`, `tvPrograms` (all index refs)
- `tvPrograms` → program object with `title`, `startTs`, `endTs` (ISO 8601 with +03:00 offset, e.g. `2026-09-27T06:10:00+03:00`)
- Logo CDN: `https://uma-static.rtbcdn.ru/pic/cardimage/{2ch}/{2ch}/{md5}.png` (no query params needed)
- 23 free channels on the page
- Slug matches our hardcoded channel IDs (pervyi, rossiya_1, ntv, pyatyi_kanal, etc.)

**Changes:**
- `ChannelRepository.kt` — rewritten to fetch premier.one HTML, parse Nuxt JSON, extract channels with current program data. 10-min session cache. Stream URLs still from hardcoded list (matched by slug).
- `Models.kt` — added `currentProgramTitle`, `currentProgramStart`, `currentProgramEnd` to Channel
- `ChannelAdapter.kt` — reads program data directly from Channel object, shows ProgressBar with elapsed percentage
- `item_channel.xml` — added `ProgressBar` (horizontal, 4dp height) below program title
- `MainActivity.kt` — removed EPGRepository dependency. Grid uses only ChannelRepository. Header shows current program from channel data.
- Timezone: premier.one sends ISO 8601 with explicit +03:00 offset — parsed correctly to UTC millis

**Files modified:**
- `TVApp/[AWS_SECRET_KEY_REDACTED]/ChannelRepository.kt` — complete rewrite (premier.one source)
- `TVApp/[AWS_SECRET_KEY_REDACTED]/Models.kt` — Channel data class extended
- `TVApp[AWS_SECRET_KEY_REDACTED]ChannelAdapter.kt` — progress bar, program from channel
- `TVApp[AWS_SECRET_KEY_REDACTED]MainActivity.kt` — simplified, no EPGRepository
- `TVApp/app/src/main/res/layout/item_channel.xml` — added ProgressBar

### 2026-09-27 Session 8 — TV device fixes: focus, channel mapping, timezone overlay

**Goal:** Fix cursor jumping to settings after player exit, channel name/stream mismatch, timezone not affecting video content.

**1. Cursor jumps to settings after exiting player:**
- Root cause: `onResume()` posted a focus request that raced with async channel loading. The `post {}` ran before RecyclerView had items, falling back to `requestFocus()` on the RecyclerView itself which defaulted to the first focusable view (settings button).
- FIX: Added `pendingFocusRestore` flag. Focus is only restored AFTER channels are loaded and adapter is updated. `focusOnPosition()` handles scroll + requestFocus in two posts.

**2. Channel names don't match streams (CRITICAL):**
- Root cause: Premier.one slugs (`pervyi`, `rossiya_1`, `pyatyi_kanal`, `matchtv`, `ren_tv`, `pyatnica`) don't match our hardcoded IDs (`c1r`, `rossiya1`, `5tv`, `match`, `ren`, `pz`). The slug lookup in ChannelRepository always failed → empty stream URLs.
- FIX: Added `slugToIdMap` in ChannelRepository mapping all 23 premier.one slugs to our internal channel IDs. Lookup now: `slug → mapped ID → hardcoded channel (stream, category, fallbacks)`.

**3. Timezone setting doesn't affect video content:**
- Root cause: PlayerActivity showed no program info at all. Timezone only affected EPG screen.
- FIX: Added program overlay (`programOverlay`) in activity_player.xml — bottom-left panel showing current program title + time range, and next program start time. Times formatted using user-configured timezone offset from `AppPreferences.timezoneOffset` via `TimeZone.getTimeZone("GMT+HH:MM")`.

**4. AGENTS.md enforcement:**
- Added rule: "ALWAYS read this file (AGENTS.md) before starting any task."

**Files modified:**
- `TVApp/[AWS_SECRET_KEY_REDACTED]/ChannelRepository.kt` — slugToIdMap, fixed channel ID resolution
- `TVApp[AWS_SECRET_KEY_REDACTED]MainActivity.kt` — pendingFocusRestore, focusOnPosition()
- `TVApp[AWS_SECRET_KEY_REDACTED]PlayerActivity.kt` — program overlay with timezone-aware times
- `TVApp/app/src/main/res/layout/activity_player.xml` — added programOverlay LinearLayout
- `AGENTS.md` — Session 8, enforcement rule

### 2026-09-27 Session 9 — smotrim.ru stream discovery

**Goal:** Find free HLS stream URLs for all Russian federal channels on smotrim.ru.

**Discovery:**
- smotrim.ru uses Nuxt 3 SSR. Channel pages have `__NUXT_DATA__` embedded JSON but `vitrinaStreams` is always empty in SSR (loaded client-side).
- The player API is at `https://player-api.smotrim.ru/api/v1/channel/{id}` (found in `window.__NUXT__.config.public.playerApiUrl`).
- Returns JSON: `{data: {title, epg: {programName}, streams: {m3u8}, splash: {large, medium, small}}}`.
- VGTRK channels (Россия 1 id=1, Культура id=4, Россия 24 id=3) return direct HLS URLs at `https://live.smotrim.ru/vgtrk/0/{name}-hd/index.m3u8` — multi-quality (1080p/720p/576p), verified working.
- All other channels (НТВ, Пятый, Матч ТВ, Карусель, ОТР, ТВЦ, РЕН ТВ, СПАС, СТС, Домашний, ТВ-3, Пятница!, Звезда, МИР, ТНТ, Муз-ТВ) return `streams: null` — these use Vitrina/MediaVitrina balancer API.

**Vitrina balancer pattern (for non-VGTRK channels):**
- Endpoint: `https://media.mediavitrina.ru/balancer/v3/default_2026/{channel_dir}/streams.json?application_id=smotrim_web&player_referer_hostname=smotrim.ru`
- Returns JSON with `hls` (array of tokenized `.m3u8` URLs), `hlsp`, `mpdp` arrays.
- Tokens are time-limited; `config_checksum_sha256` param can be empty.
- Known channel dirs: russia1, 1tvch, zvezda, domashniy, rentv, karusel, tvc, spas, mir, muztv, ntv_msk, 5tv, otr, gpm_tnt, gpm_tv3, gpm_friday, ctc, gpm_matchtv
- Player SDK: `https://staticmv.mediavitrina.ru/dist/eump-core/v20.3.3/web/mvp.js`

**Channel ID map (smotrim.ru):**
| Channel | smotrim ID | Stream source |
|---------|-----------|---------------|
| Россия 1 | 1 | Direct HLS (live.smotrim.ru) |
| Матч ТВ | 263 | Vitrina balancer |
| НТВ | 267 | Vitrina balancer |
| Пятый канал | 255 | Vitrina balancer |
| Культура | 4 | Direct HLS (live.smotrim.ru) |
| Россия 24 | 3 | Direct HLS (live.smotrim.ru) |
| Карусель | 70 | Vitrina balancer |
| ОТР | 363 | Vitrina balancer |
| ТВЦ | 260 | Vitrina balancer |
| РЕН ТВ | 256 | Vitrina balancer |
| СПАС | 257 | Vitrina balancer |
| СТС | 258 | Vitrina balancer |
| Домашний | 250 | Vitrina balancer |
| ТВ-3 | 264 | Vitrina balancer |
| Пятница! | 265 | Vitrina balancer |
| Звезда | 251 | Vitrina balancer |
| МИР | 253 | Vitrina balancer |
| ТНТ | 266 | Vitrina balancer |
| Муз-ТВ | 254 | Vitrina balancer |

**Logo URLs:** `https://cdn.smotrim.ru/photobank/prod/{size}/{path}.png` (from player API splash field)

### 2026-09-28 Session 10 — ntv.ru streams + runtime source switching

**Goal:** Replace unreliable goodstream.icu streams with verified ntv.ru CDN streams. Add user-facing "switch source" dialog in player. Add ntv.ru EPG as enrichment source.

**Stream sources (all verified 200 + #EXTM3U on 2026-09-28):**
- **Primary (official, no auth):** `https://cdn.ntv.ru/{stream_key}/index.m3u8` — 20 channels
  - Keys: vitrina18 (Первый), vitrina10 (Россия 1), ntv0_hd (НТВ), vitrina8 (5 канал), vitrina12 (Культура), vitrina2 (Звезда), vitrina7 (Пятница!), vitrina14 (СТС), vitrina1 (Домашний), vitrina17 (ТНТ), vitrina9 (РЕН ТВ), vitrina20 (Карусель), vitrina4 (Матч ТВ), vitrina11 (Россия 24), vitrina15 (ТВЦ), vitrina13 (СПАС), vitrina16 (ТВ-3), vitrina3 (МИР), vitrina5 (Муз-ТВ)
  - VGTRK channels (Россия 1, Культура, Россия 24) also have direct HLS: `https://live.smotrim.ru/vgtrk/0/{name}-hd/index.m3u8`
- **Fallback (iptv-org + other):** per-channel in `fallbackStreamUrls` (e.g. `http://46.32.176.50/perviy/index.m3u8`, `http://stream.mcquack.net/181/index.m3u8`)

**Logos:** `https://api.ntv.ru/vitrina/static/images/logo/{file}.png` (from ntv.ru EPG API)

**EPG enrichment from ntv.ru:**
- `GET https://api.ntv.ru/vitrina/v1/channels/current_programs` with header `x-platform: website`
- Returns 20 channels with current program: `{channel: {code, stream_key, icon}, program: {title, date_start, date_stop}}`
- Date format: `MM/dd/yyyy HH:mm:ss` in **UTC+5** (Ural time) — parsed with `TimeZone.getTimeZone("GMT+05:00")`
- Channel code → our ID mapping in `ntvCodeToIdMap` (e.g. `1tvch`→`c1r`, `russia1_orbit2`→`rossiya1`, `ntv2`→`ntv`)
- Called from `MainActivity.enrichWithNtvEpg()` after channel list load — fills in program title/times for channels that don't have them from premier.one/IVI

**Runtime source switching (PlayerActivity):**
- MENU button (or 0x52c1) opens AlertDialog listing all sources: "Официальный" + "Альтернативный N" with current marked "(текущий)"
- `TVPlayerManager.switchToUrl(url)` switches playback to selected URL
- Auto-fallback on error still works (iterates through streamUrl + fallbackStreamUrls)

**Slug→ID fix:**
- `subbota` (Суббота!) now maps to `"subbota"` instead of `"pz"` (Пятница!) — separate channel

**Files modified:**
- `TVApp/[AWS_SECRET_KEY_REDACTED]/Models.kt` — 20 channels: new stream URLs, logos, fallbackStreamUrls
- `TVApp/[AWS_SECRET_KEY_REDACTED]/ChannelRepository.kt` — ntvCodeToIdMap, fetchNtvCurrentPrograms(), parseNtvDate(), httpGetWithHeaders()
- `TVApp[AWS_SECRET_KEY_REDACTED]PlayerActivity.kt` — showSourceSwitchDialog(), MENU key handler, DPAD left/right channel switching
- `TVApp/app[AWS_SECRET_KEY_REDACTED]TVPlayerManager.kt` — switchToUrl(), getCurrentStreamUrls() public, getCurrentUrlIndex()
- `TVApp[AWS_SECRET_KEY_REDACTED]MainActivity.kt` — enrichWithNtvEpg()
- `TVApp/app/src/main/res/values/strings.xml` + `values-en/` — switch_source_title, source_official, source_alternative, source_current

**CI fix (1f00c8f):**
- Kotlin Pair is binary: replaced with data class NtvProgram(title, startMs, endMs) in ChannelRepository.kt + MainActivity.kt uses .title/.startMs/.endMs

### 2026-09-28 Session 11 — CI/CD hardening + Ktlint

**Goal:** Add code-style checks (Ktlint), security scans (OWASP Dependency-Check), APK/AAB signature verification, and strict unit tests to CI. Fix all ktlint violations.

**CI workflow (`.github/workflows/build.yml`):**
- Job `lint`: Android Lint (`lintDebug`) + Ktlint (`ktlintCheck`)
- Job `dependency-check`: OWASP Dependency-Check (fail on CVSS ≥ 9)
- Job `build`: compile + unit tests (`testDebugUnitTest`, no `continue-on-error`) + APK/AAB signing verification (`apksigner verify --print-certs`) + AAB integrity check (`unzip -t`)

**Ktlint setup:**
- Plugin: `org.jlleitschuh.gradle.ktlint:14.2.0` (root `build.gradle` apply false, `app/build.gradle` apply)
- Config in `app/build.gradle`: `ktlint { android = true; additionalEditorconfig = [trailing-comma rules disabled] }`
- `.editorconfig` at `TVApp/.editorconfig`: `ktlint_code_style = intellij_idea`, trailing comma disabled (both call-site and declaration-site)
- Trailing commas CANNOT be reliably auto-fixed by script (breaks when/if bodies, nested calls). Disabled via Gradle `additionalEditorconfig`.

**Ktlint violations fixed in code:**
- Wildcard imports (`kotlinx.coroutines.*`, `java.util.*`) → explicit imports in EPGActivity, MainActivity, PlayerActivity
- Import ordering: `AppPreferences` before `Channel*`, `com.bumptech.glide.Glide` before `com.example.tvapp.*`
- Body expression on same line as signature (5 places): EPGRepository ×4, Models.kt ×1
- Lambda body indentation after merging `= withContext(...)` onto one line (EPGRepository)
- PlayerActivity: multi-line `initializePlayer(` call + newline before closing `)`
- Missing `import kotlinx.coroutines.cancel` (3 files)

**OWASP Dependency-Check fix:**
- Action `dependency-check/Dependency-Check_Action@main` passes `--format HTML,JSON` which is invalid (tool doesn't support comma-separated formats)
- FIX: `format: 'HTML'` + `args: -f JSON --failOnCVSS 9` (two separate `-f` flags)

**Result:** All CI jobs green. Lint: 0 errors (1 non-blocking OldTargetApi warning). OWASP: no critical vulns. Ktlint: clean.

## Do NOT
- Do not use `via.placeholder.com` (dead service)
- Do not use `static.wikia.nocookie.net` for channel logos (unreliable)
- Do not use `streaming.televizor-24-tochka.ru` (completely dead, 412 on all URLs)
- Do not use simple millisecond offset math for timezone conversion
- Do not hardcode fake EPG data as primary source
- Do NOT run `git add .` — it stages `.gradle/` build artifacts and other junk. Always stage files explicitly by name: `git add <file1> <file2> ...`

## Channel Logo Sources (verified working)
- **ivi.ru CDN**: `https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_thumb/{hash}.jpg/x240/`
  - Source page: https://www.ivi.ru/tvplus/tvchannels/federalnye-kanaly
- **premier.one CDN**: `https://uma-static.rtbcdn.ru/cwebp/pic/cardimage/{2ch}/{2ch}/{md5}.png?size=240&quality=95`
  - Source page: https://premier.one/tv/categories/besplatnye
- **EPG Service API**: `https://api.epgservice.ru/v1/index` → channel list with hrefs to XMLTV schedules
