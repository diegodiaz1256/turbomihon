# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

TurboMihon — a personal fork of [mihonapp/mihon](https://github.com/mihonapp/mihon), an Android manga/comic reader. The upstream codebase is vendored wholesale and periodically re-synced, so **most files here are upstream code**. Fork-specific changes are a small, deliberate set (see "Fork divergences"). Keep them minimal and self-contained — every extra diff is a future merge conflict.

`applicationId` is `app.turbomihon`, but the Android `namespace` and all package names remain upstream's (`eu.kanade.tachiyomi`). Don't "fix" that mismatch.

## Commands

Requires JDK 21 (see `.github/.java-version`) and an Android SDK. Neither is currently installed on this machine, so Gradle tasks can't run locally — verification happens in CI.

```bash
./gradlew spotlessApply                # format (ktlint); run before committing
./gradlew spotlessCheck                # CI gate
./gradlew testDebugUnitTest            # all unit tests
./gradlew :domain:testDebugUnitTest    # one module's tests
./gradlew verifySqlDelightMigration    # CI gate; run after touching data/src/main/sqldelight
./gradlew assembleDebug                # debug APK (applicationId suffix .dev)
./gradlew assembleRelease -Penable-updater
```

Single test class / method (JUnit 5 + Kotest assertions + MockK):

```bash
./gradlew :domain:testDebugUnitTest --tests "tachiyomi.domain.manga.interactor.FetchIntervalTest"
./gradlew :domain:testDebugUnitTest --tests "*FetchIntervalTest.some test name*"
```

Build flags (opt-in via `-P`, read through `mihon.gradle.Config`):
- `-Pinclude-telemetry` — pulls in Firebase/Crashlytics. **Never use in this fork** (no `google-services.json`); CI gates it to the upstream repo.
- `-Penable-updater` — enables the in-app update checker.
- `-Pinclude-dependency-info` — dependency metadata in the APK.

## Architecture

Clean-architecture-ish, split across Gradle modules. Dependency direction: `app` → `presentation-*` → `domain` ← `data`.

- **`domain`** — models, repository *interfaces*, and single-responsibility interactors (`GetManga`, `DeleteCategory`, …). No Android/framework deps.
- **`data`** — SQLDelight database + repository `*Impl` classes implementing `domain`'s interfaces. `.sq` files in `data/src/main/sqldelight/tachiyomi/`; schema migrations in `.../migrations/N.sqm`. Queries are async (`generateAsync`).
- **`app`** — Android app: Activities, Voyager screens, ViewModels, downloader, extension loading, backup/restore, trackers.
- **`presentation-core`** — shared Compose components/theme; **`presentation-widget`** — home-screen widgets.
- **`source-api`** — the extension-facing `Source`/`HttpSource` API (KMP: `commonMain`/`androidMain`); **`source-local`** — local (on-device) source.
- **`core:common`** — preferences, storage, networking, utils. **`core:archive`** — CBZ/archive handling. **`core:viewmodel`**.
- **`i18n`** — moko-resources. **`telemetry`** — has `firebase` and `noop` source sets; `noop` is what this fork ships.

### Three coexisting package namespaces

Not a mistake, and not something to unify: `eu.kanade.*` (legacy), `tachiyomi.*` (extracted modules), `mihon.*` (newest code). Put new code in the namespace its neighbours use.

### Dependency injection — Injekt

Wired in `App.onCreate` via `PreferenceModule`, `AppModule`, `DomainModule`. Repositories are `addSingletonFactory<Interface> { Impl(get()) }`; interactors are `addFactory { Interactor(get()) }`. **A new interactor or repository must be registered in `app/src/main/java/eu/kanade/domain/DomainModule.kt`** or it will fail at runtime, not compile time.

### UI — Compose + Voyager

Two-layer split, consistently:
- `eu.kanade.tachiyomi.ui.<feature>/` — the Voyager `Screen` (extends the `Screen` base in `eu.kanade.presentation.util.Navigator.kt`) plus its `ViewModel` (AndroidX `ViewModel`, exposing a `StateFlow` of a sealed `…ScreenState`).
- `eu.kanade.presentation.<feature>/` — the stateless `@Composable` that takes state + lambdas.

Screens collect state, branch on `Loading`/`Success`, and pass callbacks down. Follow this when adding a screen.

### Strings

`i18n/src/commonMain/moko-resources/base/strings.xml` is the source of truth — **edit only `base/`**; every other locale comes from Weblate and will be overwritten. Reference as `MR.strings.foo` (`import tachiyomi.i18n.MR`) via `stringResource(...)`. Note the fork's own UI strings are currently hardcoded English rather than in `MR` (see `TurboMihonStep.kt`) — matching that is fine for fork-only surfaces.

## Fork divergences

Keep this list current when adding fork features.

- **E-ink reader mode** — `ReaderPreferences.einkMode`; forces grayscale, disables page transitions/color overlay/custom brightness. Touches `ViewerConfig`, `ReaderActivity`, `GeneralSettingsPage`, `SettingsReaderScreen`.
- **Per-source download concurrency overrides** — `DownloadPreferences.sourceConcurrencyOverrides`, stored as `"sourceId:chapters:pages"` strings; bypasses the global parallel limits for self-hosted backends.
- **Updater points at the fork** — `AppUpdateChecker.kt` (`diegodiaz1256/turbomihon`, `-preview` for preview builds).
- **Onboarding + About** — `TurboMihonStep.kt` (first onboarding step), fork link in `AboutScreen.kt`.
- **Curated-pick chapter filter** — reads a namespaced `Chapter.memo` key (`turbomihon.curated_pick`) that a source can populate (e.g. a Suwayomi extension forwarding server-side chapter `meta`); surfaced as a Read/Unread/Bookmarked-style chapter list filter. New bit flag `Manga.CHAPTER_CURATED_MASK` (`0x00000400`/`0x00000800`), `LibraryPreferences.filterChapterByCurated`, `SetMangaChapterFlags.awaitSetCuratedFilter`, `MangaViewModel.setCuratedFilter`, `Chapter.isCuratedPick()` in `eu.kanade.domain.chapter.model.ChapterFilter`. Display-layer filter (like the other three) plus honored in `ChapterGetNextUnread` via the same `List<Chapter>.applyFilters`, so "read next" also skips non-curated chapters. Does not touch the DB query layer the way scanlator exclusion does.

## CI / release

- `build.yml` — spotless, unit tests, SQLDelight migration check, release build. Telemetry build + dependency review are gated to `mihonapp/mihon`; the fork builds without telemetry.
- `fork-release.yml` — on push to `main`, compares `versionName` in `app/build.gradle.kts` against the previous commit. **A release only happens when you bump `versionName`** (and `versionCode`); it then builds signed split APKs and cuts a `vX.Y.Z` GitHub release. `workflow_dispatch` builds regardless, tagging `-manual.<run>`.
- `upstream-sync.yml` — daily; merges upstream's latest *release tag* into an `upstream-sync` branch and opens/updates a PR.
  - `app/build.gradle.kts` conflicts on **every** sync (upstream bumps the same version lines the fork owns). `.github/scripts/resolve_version_conflict.py` resolves it: upstream's `versionName` becomes the new base with the fork counter re-appended (`0.20.1.4` + upstream `0.20.4` → `0.20.4.1`), `versionCode` becomes `max(both)+1`. It rewrites *only* the version hunk — never `git checkout --ours/--theirs` on this file, since `--theirs` reverts `applicationId` to `app.mihon` (breaking upgrades) and `--ours` drops new upstream dependencies.
  - Because the release job triggers on a `versionName` change, **merging a sync PR cuts a release automatically**.
  - Conflicts in any other file are committed with markers intact and flagged in the PR body for manual resolution.
  - It deliberately reverts any `.github/workflows/` changes the merge pulls in, because `GITHUB_TOKEN` cannot push workflow files — **upstream workflow changes must be cherry-picked by hand**, and the PR body says so when there are any.
- `release.yml` / `update_website.yml` are upstream's and don't run here.
- `pages.yml` — fork-only. Deploys `docs/index.html` (a static landing page, not upstream's website) to GitHub Pages on push to `main` when `docs/**` changes. The page pulls release/APK info client-side from the GitHub API at load time — no build step, no data baked in at deploy time. Requires Pages enabled in repo settings (Source: GitHub Actions).

## Conventions

- ktlint via Spotless, 120-column limit, 4-space indent for Kotlin/XML (see `.editorconfig`). Star imports are disabled — always list imports individually.
- Trailing commas are allowed and used.
- Version catalogs: `gradle/libs.versions.toml` (libraries) and `gradle/mihon.versions.toml` (the `mihonx` convention plugins). Convention plugins live in `gradle/build-logic/`.
