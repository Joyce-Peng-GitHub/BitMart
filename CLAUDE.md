# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

```
bitmart-server/   Ktor backend (JVM 21)
bitmart-android/  Jetpack Compose Android app
docs/             Architecture and requirements docs
```

---

## Backend (`bitmart-server/`)

### Commands

```bash
cd bitmart-server
./gradlew test                                          # all tests
./gradlew test --tests cn.edu.bit.bitmart.SomeTest      # single class
./gradlew run                                           # start on :8080
```

Tests use an embedded PostgreSQL instance (no Docker needed) pre-warmed in `ProjectConfig.beforeProject`.

### Key conventions

- **Routes** are all mounted under `/api/v1` prefix. `/health` stays at root.
- Entry point is `Application.module()` (no-arg); use `Application.configureApp(AppComponents)` for tests.
- **Flyway SQL** is the DDL source of truth; Exposed is query-only (no schema generation).
- All tables use `app_user` (not `user` — reserved keyword). Soft-delete via `deleted_at TIMESTAMPTZ NULL`.
- `Contact.channel` is a free-text `String` (not an enum) — channel type is not enforced by the API.
- **Keyset pagination**: cursor is `createdAt|id` pair, not OFFSET.
- `quantity_sold` may increase or decrease (range `[0, quantityTotal]`).
- `search_tsv` is maintained by a DB trigger; `bitmart_search_config()` returns `text`, cast `::regconfig` at call site.
- JSONB columns that store raw strings use `jsonbRaw()` helper; structured JSONB uses `jsonb<T>(name, json, serializer)`.

### Tech stack

Kotlin 2.3.21 · Ktor 3.5.0 · Exposed 1.3.0 (`org.jetbrains.exposed.v1.*`) · Flyway · PostgreSQL 16 + pg_trgm + zhparser · HikariCP · Argon2id · Caffeine · Kotest/JUnit 5

### Environment variables (all optional, have defaults in `application.conf`)

`BITMART_DB_URL` · `BITMART_DB_USER` · `BITMART_DB_PASSWORD` · `BITMART_SHOWAPI_APP_KEY` · `BITMART_BIT101_BASE_URL` · `BITMART_STORAGE_ROOT`

---

## Android (`bitmart-android/`)

### Commands

```bash
cd bitmart-android
./gradlew assembleDebug                  # build APK
./gradlew :app:testDebugUnitTest         # all JVM unit tests
adb reverse tcp:8080 tcp:8080            # required before running on a physical device
```

### Key conventions

- **Clean Architecture**: `domain` (pure Kotlin) ← `data` (Ktor + DataStore) ← `feature` (Compose + ViewModel). `domain` has zero Android/Ktor imports.
- **Navigation**: single outer `NavHost` (shell + auth + publish + detail + …). ISBN scan result is passed back via `previousBackStackEntry.savedStateHandle` key `"isbn_result"`. Consume it in `LaunchedEffect` (not in composition-time `.also{}`).
- **Contact** is a plain `String` field in `DraftItem`. The `ContactChannel` enum exists only for UI display-label mapping.
- **LLM key never leaves the device** — backend has no knowledge of LLM config.
- `collectAsStateWithLifecycle()` everywhere; `hiltViewModel()` from `androidx.hilt.lifecycle.viewmodel.compose`.
- `enableEdgeToEdge()` in `MainActivity`; screens use `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`.
- Debug `API_BASE_URL = "http://127.0.0.1:8080"` (works with ADB reverse for both emulator and physical device).

### Tech stack

AGP 9.2.1 · Kotlin 2.3.21 · Compose BOM 2026.05.01 · Hilt 2.59.2 · Room 2.8.4 · KSP 2.3.9 · Ktor Client 3.5.0 (OkHttp) · Coil 3 · ML Kit Barcode (bundled)
