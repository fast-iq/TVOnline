# Project-specific R8/ProGuard rules.
# Media3, Glide, coroutines и androidx поставляют собственные consumer rules
# (MEDIA-INF/proguard), JSON парсится вручную через org.json — рефлексии по
# классам приложения нет. Правила ниже — страховка на случай, если R8 агрессивнее
# consumer-правил сломает динамическую инициализацию медиа-классов.

# Media3: экстракторы/DataSource создаются по именам (DefaultMediaSourceFactory).
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.datasource.** { *; }
-dontwarn androidx.media3.**
