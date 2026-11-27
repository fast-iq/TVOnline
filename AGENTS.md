
### 2026-11-26 Session 49 — D-pad header focus fix + v1.0.16 + CI hardening (Robolectric prefetch, Central mirror, dep cache)

**D-pad fix (`469bb72`, user: «Вверх не работает» from channel grid):** geometric focus search never left RecyclerView upward (LayoutManager eats UP as scroll; left cards → right buttons too far). `MainActivity.dispatchKeyEvent`: DPAD_UP (ACTION_DOWN, repeatCount 0, `!canScrollVertically(-1)`, focus is topmost focusable RV child) → `historyButton` (card left of screen center) or `settingsButton` (right half); all 5 header buttons get `nextFocusDownId = channelsRecyclerView`. Grid row-to-row nav untouched.

**v1.0.16 (`0db9c44`):** version bump 1.0.15→1.0.16 (versionCode 10016); tag `v1.0.16` published after 2 CI fixes below. Release assets verified: app-release.apk 22,245,311 B + aab.

**CI fix 1 (`ce95740`) — Robolectric jar prefetch:** tag run died at `Run Unit Tests` in 27s: Robolectric downloads 151MB `android-all-instrumented-14-robolectric-10818077-i7.jar` from repo1.maven.org into `~/.m2/repository/org/robolectric/...` AT TEST START, no retries; clean runner = flaky. Proven experimentally (moved jar away + `--rerun` → re-downloaded exactly that path; with jar present download skipped; note: plain re-run without `--rerun` is UP-TO-DATE — jar is not a task input). Build job now: `actions/cache` on `~/.m2/repository` + curl prefetch (5 attempts, mirror+repo1 alternating, pom+sha512 best-effort) before tests.

**CI fix 2 (`352feca`) — 429 from Maven Central:** next tag run failed resolving buildscript classpath (kotlin-gradle-plugin etc): Build & Test job had NO dependency cache (unlike lint/OWASP jobs) → hundreds of jar fetches per run from shared runner IPs → HTTP 429. Fixes: GCS mirror `https://maven-central.storage-download.googleapis.com/maven2` added BEFORE mavenCentral in `TVApp/settings.gradle` pluginManagement+dependencyResolutionManagement (verified serves the robolectric jar 200 OK); `gradle/actions/setup-gradle@v6` added to build job.

**Ops notes:** tag re-point via `git tag -f -a` + `git push -f origin v1.0.16` (3 total attempts: flaky test download → 429 → success). GitHub REST API from user IP rate-limits fast (60/h) — poll `releases/latest` HTML instead; job-step failure details: jobs API + PowerShell ConvertFrom-Json (bash eats `$` in inline PS, use -File; no python3 in git-bash). JAVA_HOME for local gradle: `C:/Users/Sidelnikov/.jdks/corretto-17.0.15`.

**Verification:** local ktlintCheck + testDebugUnitTest (106) + lintDebug (only local.properties PropertyEscape) + assembleDebug + `help` with new repos — all green.

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

### 2026-11-27 Session 50 — 7-item quality roadmap + real security scanning (SBOM+osv.dev) + player/settings tests

**Roadmap (one commit each, all pushed):** `63d36a9` D-pad router tests + `DpadFocusRouter` extraction; `440fbf8` R8 minify release (APK 22.2→4.9MB; had to keep retrofit/gson/okhttp rules then dropped them — unused after Media3-only networking); `a49547f` DiffUtil in ChannelAdapter (`ChannelDiffCallback`, observer tests); `a7f9932` LimeHdClient flow tests via `httpGet`/`httpPostForm` internal-open seams + CI test-results artifact; `ebe11f0` JaCoCo (gate 45% instructions on `com.example.tvapp.data`, actual ~57%; badge.json pushed to orphan `coverage` branch, endpoint badge in README; class dir under AGP9 built-in Kotlin = `intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`); `425bf04` `StallWatchdog` (pure logic, injected clock; 30s frozen position → refetch URL, 60s cooldown; 5s poll in TVPlayerManager); `69c7e22` Glide RGB_565 + 100MB disk/20MB mem cache — Glide 4.16 has no instance setters, must use `Glide.init(context, GlideBuilder()...)` in TVApplication (no kapt under AGP9, so no AppGlideModule). `e483029` gitignore: dc-reports/, jacocoTestReport(.xml)/; `07710e7` lint suppress VisibleForTests on configureGlide.

**OWASP job was fake-green (root cause):** Action scanned raw checkout — saw only gradle-wrapper.jar; the old "Resolve dependencies" step ran `dependencies` on ROOT project (config only exists in :app) and error was swallowed by `|| true`. dependency-check-gradle plugin does NOT support Android modules → replaced with SBOM pipeline (`5bc3411`): `:app:generateSbom` (CycloneDX 1.5 JSON of resolved `releaseRuntimeClasspath`, 100 components with purls, filter `version=='unspecified'`=self) + osv-scanner v2.7.1 job `Security Scan (osv.dev)`; gate fails on High/Critical; artifact `security-report` (osv-report.json + bom.json). First real scan found media3-common→`guava 33.3.1-android` GHSA-xxph-c9ww-hj94 High 8.2 → `constraints { implementation 'com.google.guava:guava:33.7.2-android' }`, rescan CLEAN.

**osv-scanner v2.7.1 gotchas (all validated locally):** linux release asset is RAW binary `osv-scanner_linux_amd64` (NOT .tar.gz — CI install step died, fixed `5bacbcc`); scan syntax `osv-scanner scan source --sbom <file> -f json --output-file <f>` (`--reporter`/`--output` don't exist; `--sbom` deprecated-but-works); SBOM FILENAME must match spec names (`bom.json` ok, arbitrary name → "invalid SBOM filename"); JSON `results[]` only contains packages WITH vulnerabilities (clean = `results: []`) — summary counts components from bom.json instead (`355a424`); exit code unreliable via pipe → gate parses JSON severity in python3.

**Tests (`f9e177f`, +`7bded2c` style):** TVPlayerManagerTest (8, Robolectric, no live ExoPlayer: candidate list/dedup, url index, quality pref, safe no-ops pre-init/post-release) + SettingsActivitySmokeTest (8: quality/language rows reflect+persist, region display incl. offline, BACK, backup export/import round-trip; `LimeHdClient.baseUrl="http://127.0.0.1:9/"` kills network; idle main looper loop to drain IO-dispatch callbacks). GOTCHA: BaseActivity forces app language (ru) but `RuntimeEnvironment.getApplication()` context resolves default locale (en) — assert strings via `activity.getString(...)`. `playChannel` now writes `prefs.lastChannelId` OUTSIDE `exoPlayer?.apply` (selection persists even uninitialized). Totals: ~166 tests; data 57%, settings 0→covered.

**Verification:** local pipeline green (ktlintCheck/testDebugUnitTest/lintDebug/assembleDebug); CI on `355a424`: Lint ✓ Build&Test ✓ Security Scan (osv.dev) ✓; artifact contents verified by downloading zip (100 components, 0 advisories). NEXT: tag v1.0.17.
