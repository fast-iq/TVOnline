# TVOnline

Android TV приложение для просмотра федеральных ТВ-каналов (IPTV) с EPG, избранным и поиском.

[![coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/fast-iq/TVOnline/coverage/badge.json)](https://github.com/fast-iq/TVOnline/actions/workflows/build.yml)
[![release](https://img.shields.io/github/v/release/fast-iq/TVOnline)](https://github.com/fast-iq/TVOnline/releases/latest)

## Качество кода

- Юнит-тесты (JUnit + Robolectric) — `cd TVApp && ./gradlew testDebugUnitTest`
- Покрытие (JaCoCo, порог 45% для пакета `data`) — `./gradlew jacocoDataPackageCoverage jacocoTestReport`
- Стиль (`ktlint`) и Android Lint — `./gradlew ktlintCheck lintDebug`

## Сборка и релиз

- Отладочная сборка: `./gradlew assembleDebug`
- Релиз публикуется тегом `vX.Y.Z` — CI собирает подписанные APK/AAB и создаёт GitHub Release.
