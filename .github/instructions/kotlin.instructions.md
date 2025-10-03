---
description: Coding Standards & Rules for Kotlin (Android) with Google Play services
globs: **/*.kt, **/*.kts, **/*.xml
alwaysApply: true
---

You are an expert in Kotlin, Android, and Google Play services. You focus on producing clear, readable, and maintainable code. You use the latest stable Android Gradle Plugin, Kotlin, AndroidX, and Google Play services libraries, and you are familiar with modern Jetpack architecture and best practices.

## Code Style
Adhere to Kotlin and Android style guidelines for consistency and readability.

- Prefer **Kotlin** with **Jetpack** components; avoid Java unless required by a library.
- Favor **immutability**: `val` over `var`, data classes for models, sealed classes for state.
- **Naming**: PascalCase for classes/types; camelCase for functions/variables; `UPPER_SNAKE_CASE` for constants; `IThing`/`ThingImpl` discouraged—use interfaces with descriptive names.
- **Visibility first**: keep APIs minimal (`internal`/`private`); expose only what’s necessary.
- **KDoc** public APIs; inline comments only for non-obvious logic.
- **Coroutines** & **Flow**: use `suspend` functions and cold streams; never block the main thread.
- **ViewModel scope**: use `viewModelScope` (or `lifecycleScope` in UI) with structured concurrency; cancel on lifecycle.
- **DI**: use Hilt (preferred) or Koin; avoid service locators and singletons without DI.
- **Lint & format**: enforce ktlint or Ktlint + Detekt; CI must fail on violations.
- **File layout**: one top-level class per file; keep files focused and short.

## Usage (Architecture & APIs)
Follow these practices to use modern Android + Google Play services effectively.

### UI
- Prefer **Jetpack Compose** for new UI. Use `@Composable` functions that are small and pure; hoist state; use `remember`, `derivedStateOf`, and `rememberSaveable` as appropriate.
- For legacy XML, keep views thin; move logic to ViewModels; use ViewBinding, not Kotlin synthetics.

### State & Data
- MVVM (or MVI) with **single source of truth** in ViewModel. Expose UI state as `StateFlow`/`ImmutableList` (or Compose `State`).
- Use **Repository** pattern; domain layer uses **use cases** (pure Kotlin).
- Networking: Retrofit + OkHttp; JSON via Kotlinx Serialization or Moshi; handle errors with sealed results.
- Persistence: Room with DAOs; prefer `Flow` from Room for reactive streams.
- Pagination: Paging 3.
- Background work: WorkManager for deferrable tasks.

### Google Play services (GPS)
- **Dependency management**: use the Play services **BOM** when available to keep versions aligned.
- **Availability checks**: use `GoogleApiAvailability` to verify services and prompt resolution when needed.
- **Location**: use `FusedLocationProviderClient`; request runtime permissions via AndroidX; choose power-appropriate `Priority` (balanced vs. high accuracy); handle `lastLocation` vs. active updates; throttle and stop updates in `onStop`.
- **Maps**: use Maps SDK; keep API keys in secure gradle properties/secrets, injected via manifest placeholders; respect lifecycle (MapView/Compose Maps).
- **Auth**: prefer One Tap / Google Sign-In from `play-services-auth`; handle `ActivityResult` APIs; store tokens securely.
- **Play Core / In-App Updates & Review**: use Play Core libraries; follow flexible vs. immediate flows; gate prompts by heuristics.
- **Play Integrity / Safety**: use Play Integrity API instead of legacy SafetyNet Attestation.
- **Analytics/Measurement**: if using Google Analytics for Firebase, isolate analytics behind an interface to keep core logic testable (even though Firebase isn’t strictly “Play services,” treat it similarly in structure).

## Performance Optimization
- Never block the main thread; offload I/O and CPU work to appropriate dispatchers (`Dispatchers.IO`, `Default`).
- Scope coroutines properly; avoid `GlobalScope`; tie work to lifecycle owners / ViewModels.
- Use **memoization**/`remember` in Compose; minimize recomposition by passing stable, immutable models; use `@Stable` if appropriate.
- Prefer **Flow** operators over manual callbacks; debounce/throttle user input streams.
- Cache network results (Room/HTTP cache). Batch network calls when possible.
- Optimize bitmaps and lists: use `LazyColumn`/`LazyGrid`, `remember` image painters; prefer `Modifier.placeholder` during loads.
- Profile regularly with Android Studio Profiler; fix overdraw, allocations, and jank before release.

## Security & Privacy
- Store secrets outside source control; use Gradle properties and CI secrets.
- Use `EncryptedSharedPreferences` or a proper keystore-backed solution for sensitive data.
- Request only necessary runtime permissions; explain clearly; degrade gracefully when denied.
- Sanitize logs; never log PII or tokens.

## Testing
- Unit tests for use cases, repositories, and mappers (JUnit, Turbine for Flow).
- UI tests with Compose testing or Espresso for XML.
- Fake Play services interactions behind interfaces; use test doubles or Robolectric where needed.
- Instrumentation tests should avoid flakiness; use Idling resources or await idle for Compose.

## Build & CI
- Use **Version Catalogs** (`libs.versions.toml`) for dependency management; prefer BOMs where available.
- Enable **R8/ProGuard** with explicit keep rules for reflective libraries.
- Enable **StrictMode** in debug; crash on coroutine leaks in tests.
- App signing via Play App Signing; use **Play Console** tracks (internal, alpha, beta, production) with clear promotion criteria.

## Updating Practices
Stay current with stable releases across the stack.

- Monitor release notes for **Kotlin**, **Android Gradle Plugin**, **AndroidX**, and **Google Play services**; update BOMs and libraries regularly.
- Communicate deprecations and migrations (e.g., to new Play services APIs, Play Integrity, One Tap).
- Track minimum SDK/target SDK changes and policy deadlines from Google Play; plan upgrades in advance.
- Maintain an **Upgrade Guide** in the repo: entries include version bumps, breaking changes, and migration steps.
- If our versions lag behind stable, propose a concrete plan (test matrix, rollout, and rollback steps) to reach current stable.

## Practical Examples & Conventions
- **Coroutines**: Repository APIs are `suspend` or return `Flow<T>`. Avoid callback-based Play services APIs when coroutine wrappers exist; otherwise wrap with `suspendCancellableCoroutine`.
- **Result handling**: Use a sealed `AppResult<out T>` (Success / Error / Loading); map low-level exceptions at the repository boundary.
- **Navigation**: Use Jetpack Navigation (or Compose Navigation); pass only IDs/parcelables; avoid passing repositories/contexts.
- **Permissions**: Centralize permission prompts; surface rationale; handle “don’t ask again” with a path to Settings.
