# CLAUDE.md

Oct 13, 2026 · @dani

## Project Overview

MemoryLane is an Android app for sorting photos from the gallery using a Tinder-style swipe interface. The core loop: open the app, see photos taken on this day in previous years ("On This Day"), swipe right to keep, swipe left to mark for deletion. At the end of a session the user reviews marked photos and confirms bulk deletion via a single system dialog.

All processing is local — no network, no cloud, no uploads.

## Tech Stack

| Layer | Choice                                            |
| --- |---------------------------------------------------|
| Language | Kotlin                                            |
| UI | Jetpack Compose + Material3                       |
| Architecture | MVI                                               |
| DI | Hilt                                              |
| Navigation | Compose Navigation                                |
| Database | Room                                              |
| Async | Coroutines + Flow                                 |
| Build | Kotlin DSL + Version Catalog (libs.versions.toml) |
| Swipe cards | LazyCardStack (vendored as :lib:lazycardstack)    |
| Min SDK | 24 (Android 7.0)                                  |
| Theme | Dark by default, system/manual toggle             |

## Module Structure

```
:app                        — entry point, DI setup, bottom navigation
:feature:onboarding:api       — onboarding route
:feature:onboarding:impl      — onboarding screens
:feature:swipe:api          — swipe/calendar/review routes and contracts
:feature:swipe:impl         — ViewModel, Composables, MVI implementation
:feature:achievements:api  — achievements route
:feature:achievements:impl — streak, freed space stats, MVI
:feature:profile:api       — profile route
:feature:profile:impl      — theme, notifications, streak goal settings, MVI
:core:photos:domain         — repository interfaces, photo domain models
:core:photos:data           — MediaStore access, reading and deleting photos
:core:ui                    — shared Compose components, theme, swipe overlay
:core:domain                — repository interfaces, domain models
:core:common                — PerfLog, the performance logging (CalendarPerf), a no-op in release
:core:data                  — Room database, repository implementations
:lib:lazycardstack          — vendored Tinder-style card stack library
:build-logic                — convention plugins (shared Gradle config)
```

`:app` depends on all `:impl` modules. Features depend only on `:api` modules of other features and core. Features never depend on each other's `:impl`.

Each feature module depends only on :core:\* modules. Features never depend on each other.

## Architecture and Patterns

**MVI flow per feature:**

```
Intent → ViewModel → State → UI
```

- `Intent` — user actions (SwipeLeft, SwipeRight, ConfirmDelete)
- `ViewModel` — processes intents, emits new State via StateFlow
- `State` — single immutable data class, UI renders from it
- Side effects (navigation, system dialogs) — sent via `Channel<Effect>(Channel.BUFFERED)` and exposed with `receiveAsFlow()` as `Effect`

**Photo deletion flow:**

1. Swipe left → photo ID written to Room as PendingDelete
2. Swipe right → photo kept, PendingDelete removed if existed
3. All photos swiped → navigate to Review screen (if nothing pending → navigate to Calendar)
4. Review screen shows: kept count, marked for deletion count
5. User taps "Delete X photos" → single MediaStore.createDeleteRequest() system dialog
6. On confirmation → animate freed space counter → navigate to Calendar

**Swipe overlay:** During drag, show green (Keep) or red (Delete) overlay on the card based on drag direction and offset. Implemented in `:core:ui`.

**Theme:** Dark by default. User can override in Profile. Stored in DataStore.

## Git Conventions

**Branch naming:**

```
feat/MMLN-0: initial-setup
feat/MMLN-1: tinder-card-animation
fix/MMLN-2: read-permission
chore: setup-modules
```

Each prefix has its own independent counter:
- feat/MMLN-XX — feature commits, own sequence
- fix/MMLN-XX — bug fix commits, own sequence
- refactor/MMLN-XX — refactor commits, own sequence
- chore, test, docs — no ticket number

Current counters (update as you go):
- feat: last used MMLN-24
- fix: last used MMLN-13
- refactor: last used MMLN-06

**Commit format (Conventional Commits):**

Commit message must include the ticket number (numbers count separately for each logical commit format):

```
feat/MMLN-01: add green/red overlay on card drag
fix/MMLN-02: handle missing READ_MEDIA_IMAGES permission
refactor/MMLN-03: extract photo repository interface
chore: add Room and Hilt dependencies
test: add ViewModel unit tests
docs: update CLAUDE.md with new module
```

**Prefixes:** `feat` · `fix` · `refactor` · `chore` · `test` · `docs`

**Push workflow:**

1. Commit freely during work — as many commits as needed
2. If many commits were created during the task, squash them into one logical commit
3. The human reviews all commits manually before any push
4. Claude Code has no permission to push — suggest commit message only

Commit directly to master branch. 
Do not create new branches for each commit.
Do not add Co-Authored-By or any other trailer lines to commit messages.
Do not add any "build successful" or similar lines to commit messages.

## Design System

Minimalist green-themed UI. All design tokens live in `:core:ui`:
`core/ui/src/main/java/com/kllxx/memorylane/core/ui/theme/`

- `Color.kt` — brand colors and light/dark color schemes
- `Theme.kt` — `MemoryLaneTheme` (default: dark); `ThemeMode` lives in `:core:domain`
- `Type.kt` — Nunito typography
- `Shape.kt` — corner radius tokens
- `Spacing.kt` — spacing constants

Swipe overlays: left → `RedCrayola` (error), right → `Emerald` (secondary).
Font license: `core/ui/OFL-Nunito.txt`
Icon license (Material Symbols, Apache 2.0): `core/ui/LICENSE-MaterialSymbols.txt`

## Claude Code Instructions

**Working style:**

- Always ask before creating a new module — confirm naming and placement
- Never delete or rename existing files without explicit confirmation
- When adding a dependency, add it to `libs.versions.toml` first
- Keep each commit focused on one change; suggest commit message after completing a task

**Code conventions:**

- One ViewModel per screen
- State is a single immutable data class; never mutate it directly
- All side effects (navigation, system dialogs, toasts) go through the `Effect` Channel (`receiveAsFlow()`)
- No business logic in Composables — only UI
- No runBlocking in production code — tests only.
- Repository interfaces live in `:core:domain`, implementations in `:core:data`
- `:core:photos:data` is the only module allowed to touch MediaStore

**What to ask before doing:**

- Adding a new feature module
- Changing Room schema (migration required)
- Any change to :lib:lazycardstack (vendored code)
- Anything touching AndroidManifest.xml permissions

## Testing

**Unit tests (JUnit4 + MockK + Turbine) — mandatory for:**
- Every ViewModel: state transitions, intent handling, effect emission
- Every UseCase
- Every Repository implementation (mock DAOs and MediaStore)
- All mapper functions

**Flow tests (Turbine) — mandatory for:**
- Any StateFlow or effect Flow emitted by ViewModel
- Repository methods returning Flow

**Compose UI tests — required for key user scenarios:**
- Swipe left/right gesture and overlay color
- Card stack navigation (next card appears after swipe)
- Review screen shows correct pending delete count
- Bottom navigation between tabs

**Room DAO tests (JVM, Robolectric + in-memory Room database):**
- Each DAO: insert, query, delete
- Live in src/test with `@RunWith(RobolectricTestRunner::class)`, so they run with the other unit tests

**Rules:**
- Mock everything outside the unit under test
- No business logic in Composables — so UI tests only verify rendering and gestures
- Tests that need the Android runtime (Bundle, SavedStateHandle.toRoute(), Log, Room, ContentResolver) use `@RunWith(RobolectricTestRunner::class)`; do not mockkStatic Android framework classes in them
- The real MediaStore is never queried: other layers mock the PhotoRepository interface, and PhotoRepositoryImpl tests use a fake ContentProvider registered with Robolectric
- Test files mirror source structure: src/test/ for unit, src/androidTest/ for instrumented