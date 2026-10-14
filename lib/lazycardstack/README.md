# lazycardstack (vendored)

Vendored copy of [Hukumister/LazyCardStack](https://github.com/Hukumister/LazyCardStack) (MIT, see `LICENSE`).

- Upstream commit: `bc7923df17359a1c003c92c6ba0f371c4782ded8`
- Copied: `lazycardstack/src/commonMain/kotlin/**` -> `src/main/kotlin/` (one local patch, see below)
- Not copied: sample app, convention plugins, upstream build files

## Local changes
- `LazyCardStackState.kt`: `prefetchState.schedulePrefetch(...)` -> `schedulePrecompositionAndPremeasure(...)` (removed in current Compose foundation).
- Added `androidx.compose.material` (Material 2) dependency; upstream uses `FractionalThreshold` / `ThresholdConfig`.

## Known issues
- The library uses Material 2 classes (`FractionalThreshold`, `ThresholdConfig`) for swipe threshold config, which is why `androidx.compose.material` is a dependency of this module. The Material 3 equivalent is `AnchoredDraggableState`. Migrating would mean reworking the swiper state, so it is deferred.
