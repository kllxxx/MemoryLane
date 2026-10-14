# CLAUDE.md

Oct 13, 2026 · @dani

## Project Overview

MemoryLane is an Android app for sorting photos from the gallery using a Tinder-style swipe interface. The core loop: open the app, see photos taken on this day in previous years ("On This Day"), swipe right to keep, swipe left to mark for deletion. At the end of a session the user reviews marked photos and confirms bulk deletion via a single system dialog.

All processing is local — no network, no cloud, no uploads.

## Tech Stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Architecture | MVI |
| DI | Hilt |
| Navigation | Compose Navigation |
| Database | Room |
| Async | Coroutines + Flow |
| Build | Kotlin DSL + Version Catalog (libs.versions.toml) |
| Swipe cards | LazyCardStack (vendored as :lib:lazycardstack) |
| Min SDK | 24 (Android 7.0) |
| Theme | System (light/dark) with manual toggle |

## Module Structure

```
:app                        — entry point, DI setup, bottom navigation
:feature:swipe:api          — swipe screen contracts, navigation interface
:feature:swipe:impl         — ViewModel, Composables, MVI implementation
:feature:achievements:api
:feature:achievements:impl
:feature:profile:api
:feature:profile:impl
:core:photos:api            — repository interfaces, photo domain models
:core:photos:impl           — MediaStore access, reading and deleting photos
:core:ui                    — shared Compose components, theme, swipe overlay
:core:data:api              — Room DAOs interfaces, domain models
:core:data:impl             — Room database, repository implementations
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
- Side effects (navigation, system dialogs) — emitted via SharedFlow as `Effect`

**Photo deletion flow:**

1. Swipe left → photo ID written to Room as `PendingDelete`
2. User taps "Delete X photos" on Review screen
3. Single `MediaStore.createDeleteRequest()` system dialog
4. On confirmation — photos go to system trash (30-day recovery)

**Swipe overlay:** During drag, show green (Keep) or red (Delete) overlay on the card based on drag direction and offset. Implemented in `:core:ui`.

**Theme:** Follows system by default. User can override in Profile. Stored in DataStore.

## Git Conventions

**Branch naming:**

```
feat/MMLN-0-initial-setup
feat/MMLN-1-tinder-card-animation
fix/MMLN-2-read-permission
chore/MMLN-3-setup-modules
```

Ticket numbers start from 0 and increment by 1.

**Commit format (Conventional Commits):**

```
feat(swipe): add green/red overlay on card drag
fix(photos): handle missing READ_MEDIA_IMAGES permission
refactor(core): extract photo repository interface
chore: add Room and Hilt dependencies
test(swipe): add ViewModel unit tests
docs: update CLAUDE.md with new module
```

**Prefixes:** `feat` · `fix` · `refactor` · `chore` · `test` · `docs`

**Push workflow:**

1. Commit freely during work — as many commits as needed
2. Before push: squash all commits on the branch into one logical commit
3. The human reviews all commits manually before any push
4. Claude Code has no permission to push — suggest commit message only

Main branch: `main`. PRs from feature branches into `main`.

During initial project setup and chore commits, commit directly to master branch. 
Branch out only when working on specific features or fixes (look at branch naming). 
Do not create new branches for each commit.

## Design System

``Minimalist green-themed UI. All colors defined as tokens in `:core:ui`. Once `Color.kt` and `Theme.kt` are generated from the scheme below, remove the raw color data from this file and keep only a reference to those files.``

**Named colors (`Color.kt`):**

```kotlin
// Brand colors
val BritishRacingGreen = Color(0xFF054A29)
val DarkSpringGreen = Color(0xFF137547)
val Emerald = Color(0xFF5BBA6F)
val Licorice = Color(0xFF230903)
val BabyPowder = Color(0xFFFCFFFC)
val RedCrayola = Color(0xFFEE204E)
val LightGray = Color.LightGray.copy(red = 0.6f, blue = 0.6f, green = 0.6f)

// M3 generated (seed #137547)
val Green10 = Color(0xFF002110)
val Green20 = Color(0xFF00391F)
val Green30 = Color(0xFF0C5130)
val Green40 = Color(0xFF2B6A46)
val Green80 = Color(0xFF94D5A9)
val Green90 = Color(0xFFAFF2C4)
val DarkBackground = Color(0xFF0F1511)
val DarkSurface = Color(0xFF1C211D)
```

**Light color scheme (`lightColorScheme`):**

```kotlin
primary = DarkSpringGreen,        // #137547 — screen background
onPrimary = BabyPowder,           // cards, nav
secondary = Emerald,              // #5BBA6F — stat cards, key buttons
onSecondary = BabyPowder,
tertiary = BritishRacingGreen,    // #054A29 — nav buttons, forms
onTertiary = BabyPowder,
error = RedCrayola,               // #EE204E
onError = BabyPowder,
background = DarkSpringGreen,
onBackground = LightGray,
surface = BabyPowder,
onSurface = Licorice,
```

**Dark color scheme (`darkColorScheme`) — M3 generated + brand error:**

```kotlin
primary = Green80,                // #94D5A9 — light green on dark
onPrimary = Green20,              // #00391F
primaryContainer = Green30,       // #0C5130
onPrimaryContainer = Green90,     // #AFF2C4
secondary = Color(0xFFB5CCBA),
onSecondary = Color(0xFF213528),
tertiary = Color(0xFFA3CDDB),
onTertiary = Color(0xFF033641),
error = RedCrayola,               // keep brand red, not M3 pink
onError = Color(0xFF690005),
background = DarkBackground,      // #0F1511
onBackground = Color(0xFFDFE4DD),
surface = DarkBackground,
onSurface = Color(0xFFDFE4DD),
surfaceVariant = Color(0xFF414942),
onSurfaceVariant = Color(0xFFC0C9C0),
```

| Token name | Color name | Hex | Light role | Notes |
| --- | --- | --- | --- | --- |
| `BritishRacingGreen` | British Racing Green | `#054A29` | `tertiary`, dark `primary` | Buttons, nav, forms bg |
| `DarkSpringGreen` | Dark Spring Green | `#137547` | `primary` | Screen background |
| `Emerald` | Emerald | `#5BBA6F` | `secondary` | Stat cards, key buttons |
| `Licorice` | Licorice | `#230903` | `onSurface` | Text, icons |
| `BabyPowder` | Baby Powder | `#FCFFFC` | `onPrimary`, `surface` | Cards, nav, backgrounds |
| `RedCrayola` | Red Crayola | `#EE204E` | `error` | Errors, delete, swipe-left overlay |
| `LightGray` | Light Gray | `Color.LightGray(0.6f)` | `onBackground` | Hints, placeholders |

**Shape tokens (corner radius):**

| Element | Radius |
| --- | --- |
| Cards, dialogs | `16.dp` |
| Buttons (default) | `8.dp` |
| Primary action button | `12.dp` |
| Text fields | `8.dp` |
| Stat item cards | `12.dp` |

**Spacing:**

- Screen horizontal padding: `16.dp` (toolbar: `24.dp`)
- Between elements: `16.dp`
- Primary button height: `56.dp`

**Typography (Material3 defaults + overrides):**

- `bodyLarge`: `16.sp`, `FontWeight.Normal`, `lineHeight 24.sp`, `letterSpacing 0.5.sp`
- App title: `26.sp`, `FontWeight.SemiBold`, `letterSpacing 0.5.sp`
- Dialog titles: `18–24.sp`, `FontWeight.Medium`
- Button labels: `16.sp`
- Secondary text: `14.sp`
- Font family: system default (no custom font)

Swipe overlays: left → `RedCrayola` (`error`), right → `Emerald` (`secondary`).

Default theme: **dark**. Follows system otherwise. Manual override in Profile tab stored in DataStore.

## Claude Code Instructions

**Working style:**

- Always ask before creating a new module — confirm naming and placement
- Never delete or rename existing files without explicit confirmation
- When adding a dependency, add it to `libs.versions.toml` first
- Keep each commit focused on one change; suggest commit message after completing a task

**Code conventions:**

- One ViewModel per screen
- State is a single immutable data class; never mutate it directly
- All side effects (navigation, system dialogs, toasts) go through `Effect` SharedFlow
- No business logic in Composables — only UI
- Repository interfaces live in `:core:data`, implementations too
- `:core:photos` is the only module allowed to touch MediaStore

**What to ask before doing:**

- Adding a new feature module
- Changing Room schema (migration required)
- Any change to :lib:lazycardstack (vendored code)
- Anything touching AndroidManifest.xml permissions

**Module status:** *(update as modules are implemented)*

- [ ] :app
- [ ] :feature:swipe
- [ ] :feature:achievements
- [ ] :feature:profile
- [ ] :core:photos
- [ ] :core:ui
- [ ] :core:data
- [ ] :lib:lazycardstack
- [ ] :build-logic
