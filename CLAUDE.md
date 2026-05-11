# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Philosophy

- **Latest features, no backward compat concerns** — use newest Kotlin/Android APIs freely; minSdk 26 is the only floor.
- **Simple and clean over clever** — prefer fewer abstractions; the codebase should stay readable.
- **Material You** — follow Material 3 / dynamic color design guidelines throughout.
- **Audiobook-first** — the app targets audiobooks and podcasts but audiobooks have priority.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Build and install on connected device
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumentation tests (requires device/emulator)
./gradlew lint                   # Run lint checks
./gradlew lintFix                # Auto-fix lint issues
./gradlew check                  # Run all checks (tests + lint)
./gradlew clean                  # Clean build outputs
```

**Toolchain:** AGP 9.1.1, Kotlin 2.2.10, Gradle 9.3.1, KSP 2.2.10-2.0.2, Java 17, compileSdk/targetSdk 36, minSdk 26.

## Architecture

Single-module MVVM app (`app/`) targeting an [Audiobookshelf](https://www.audiobookshelf.org/) server. Package root: `com.samwise.unshelved`.

### Layer Organization

```
core/
  di/         — Hilt AppModule (singleton bindings)
  database/   — Room DB: DownloadEntity, OfflineProgressEntity
  datastore/  — Proto DataStore: UserPreferencesRepository (auth token, server URL, prefs)
  model/      — Domain models, Mappers (DTO→domain), TimeUtils
  network/    — AbsApi (Retrofit interface), ApiProvider, AuthInterceptor, Dtos
  ui/         — Shared Compose components (BookCards)
feature/
  auth/       — LoginScreen, LoginViewModel, AuthRepository
  home/       — HomeScreen, HomeViewModel
  library/    — LibraryScreen, LibraryViewModel, LibraryRepository
  series/     — SeriesListScreen, SeriesDetailScreen + ViewModels
  detail/     — DetailScreen, DetailViewModel
  player/     — PlayerUI, MiniPlayer, FullPlayerSheet, PlayerViewModel
  downloads/  — DownloadsScreen, DownloadsViewModel
  search/     — SearchViewModel
  settings/   — SettingsScreen, SettingsViewModel
service/
  PlaybackService.kt   — MediaSessionService (Media3/ExoPlayer)
  PlayerRepository.kt  — Playback state, server session sync (every 30s), progress
```

### Key Data Flows

**Auth:** `LoginViewModel` → `AuthRepository.login()` → saves to `UserPreferencesRepository` → calls `ApiProvider.reset()` to rebuild Retrofit with the new server URL → `MainActivity` recomposes showing `MainNavigation`.

**Playback:** `PlayerViewModel.startPlayback(itemId)` → `PlayerRepository` posts `/api/items/{id}/play` → receives `PlaybackSession` → builds `MediaItem` list → sends to ExoPlayer via MediaSession → progress synced to server on a 30s loop, then closed via `/api/session/{id}/close`.

**API client:** `ApiProvider` caches a Retrofit instance keyed to the server URL stored in DataStore. Call `ApiProvider.getApi()` from repositories; after login or server URL change, call `apiProvider.reset()`. `AuthInterceptor` injects the Bearer token from DataStore into every request.

### Dependency Injection

- `@HiltAndroidApp` on `UnshelvedApp`; `@AndroidEntryPoint` on activities/services
- `@HiltViewModel` + `@Inject` constructor on all ViewModels
- All singletons in `AppModule` under `SingletonComponent`
- WorkManager uses `HiltWorkerFactory` (configured in `UnshelvedApp`)

### State & Async Conventions

- ViewModels expose `StateFlow<UiState>` (private `MutableStateFlow`, public `StateFlow` via `.stateIn(Eagerly)`)
- All repository methods are `suspend` functions wrapped in `runCatching { }`; callers use `Result.getOrElse { }`
- `PlayerRepository` runs on its own `CoroutineScope(SupervisorJob() + Dispatchers.Main)` (not tied to a ViewModel lifecycle)
- ViewModels launch coroutines via `viewModelScope.launch`

### Navigation

`MainNavigation.kt` defines a single `NavHost` with bottom navigation (Home, Library, Series, Settings) and a bottom sheet for the full player. Auth gating is in `MainActivity` — if `UserPreferencesRepository` has no token, `LoginScreen` is shown directly instead of `MainNavigation`.
