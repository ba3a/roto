# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Roto is a standalone, open-source reference-to-pixel-art rotoscoping tool. It turns phone footage (or
a photo) into a palette draft for hand polish in LibreSprite/Aseprite, then publishes the polished
frames into a consuming project's source tree with no manual file handling.

Kotlin-first Gradle multi-module build, Compose Multiplatform for Desktop. Modules:

- `roto-core` — pure Kotlin, no UI: image raster, every pipeline stage, palette, `.ase` IO, project
  and profile models, stage cache. Fully unit-testable and usable as a library.
- `roto-cli` — headless runner (`roto render <profile>`, `roto publish <profile>`). Keep it thin:
  argument parsing and wiring only, every rule lives in `roto-core`.
- `roto-app` — Compose Desktop UI: profile list, timeline, clip setup with live preview,
  handoff/publish panel. Keep it thin the same way: it presents and edits state, `roto-core` decides.
- `buildSrc` — shared Gradle convention plugin (`buildsrc.convention.kotlin-jvm`)

No HTTP stack, REST API, database, ORM, web framework, or DTO/entity/mapper packages.

## Hard rules

- **Determinism.** Every step is a deterministic algorithm or the artist's own hand. No ML, no
  generative models, no "AI upscaling". Same inputs + same parameters give byte-identical output —
  no `Random()` without a fixed seed derived from the inputs, no iteration over unordered hash
  collections where order reaches the output, no wall-clock time or locale in anything rendered.
- **Generic tool, configured consumer.** Roto knows nothing about any one game. Action names,
  facings, canvas size, output paths and post-publish steps come from the consuming project's
  `roto.json`; anything consumer-specific runs on the consumer's side through the `afterPublish`
  hook. Never hard-code a consumer's names, folder layout or file formats in Roto.
- **The artist never thinks about formats, sizes, file names or copying.**
- **The `art` layer is the artist's.** Re-rendering a handoff rewrites `reference` and `draft` only;
  once the artist has saved, `art` is never touched.
- **Sources are read-only.** Original videos in a profile's `sources/` are never modified; `cache/`
  is always regenerable from sources plus parameters.
- **Video goes through `ffmpeg` / `ffprobe` as external processes** on `PATH`, never a bundled
  native decoder.

## Shell

### Linux / Ubuntu (CI runners)

One shell: the runner's standard **Bash**. All Unix tools work (`cat`, `grep`, `find`, `sed`,
`head`, pipes, `&&`, heredocs, `/dev/null`). There is no `.bat` wrapper — use `./gradlew` directly:

```bash
./gradlew test --console plain
```

### Windows 10 (local development)

Two shells are available and they are **not** interchangeable — pick one per command and use that
shell's syntax.

- **Bash tool = Git Bash.** The default for everything. `cat`, `grep`, `find`, `sed`, `head`, pipes,
  `&&`, heredocs and `/dev/null` all work. Use forward slashes.
- **PowerShell tool = Windows PowerShell 5.1.** No `&&`, no `||`, no `head`/`tail`/`which`. Reach for
  it only when a PowerShell cmdlet is genuinely the right tool.

The wrapper is a `.bat`, so its invocation differs per shell. Getting this wrong is the single most
common failure:

| Shell | Correct | Wrong |
|---|---|---|
| Bash | `./gradlew.bat test --console plain` | `.\gradlew.bat …` — the backslash is swallowed and you get `.gradlew.bat: command not found` |
| PowerShell | `.\gradlew.bat test --console plain` | chaining with `&&` — PowerShell 5.1 has no pipeline chain operators |

Two more rules that follow from the above:

- Do not redirect a `gradlew.bat` run through `2>&1` in PowerShell. PS 5.1 wraps native stderr in
  ErrorRecords and reports failure on an exit-0 build. Redirect in Bash, or don't redirect.
- Backslashes in a Bash heredoc get collapsed by the escaping layer. When writing a file whose
  content contains literal backslashes (JSON with Windows paths, regex), use the Write tool.

## Build & Test Commands

The Gradle wrapper differs by environment:

| Environment | Command prefix |
|---|---|
| Linux / Ubuntu | `./gradlew` |
| Windows — Git Bash | `./gradlew.bat` |
| Windows — PowerShell | `.\gradlew.bat` |

Examples below use the Linux form; substitute the appropriate prefix for your environment.

```bash
./gradlew build --console plain           # full build
./gradlew test --console plain            # run tests
./gradlew :roto-app:run --console plain   # run the desktop app
./gradlew :roto-cli:run --args="render <profile>" --console plain
./gradlew :roto-core:test --tests "com.roto.core.SomeTest" --console plain
./gradlew detekt --console plain          # static analysis; also runs as part of `check`/`build`
```

A cold Gradle daemon pushes `build` past the 2-minute default tool timeout — pass an explicit
`timeout` of ~420000 ms rather than retrying.

Run `test` after every change. If you touched build wiring, dependencies, or startup flow, also run
`build`.

The Gradle daemon is pinned to Java 21 by `gradle/gradle-daemon-jvm.properties`, matching the
`jvmToolchain(21)` the modules compile against. Do not remove it: detekt 1.23.x bundles the Kotlin
1.9 compiler, which dies with `IllegalArgumentException: 25.0.2` on a JDK 25 daemon.

## Architecture

Package-by-responsibility under `com.roto.core`, `com.roto.cli` and `com.roto.app`. Grow the package
table here as packages land; each entry names the package and its one responsibility.

| Package | Responsibility |
|---|---|
| `com.roto.cli` | CLI entry point (`Main.kt`) |
| `com.roto.app` | Compose Desktop entry point (`Main.kt`) |

Naming patterns: data models are nouns (`Raster`, `Palette`, `ClipManifest`); a pipeline stage is a
noun named for what it produces or does (`CleanPlateBuilder`, `ForegroundMasker`, `RootTracker`);
singleton config holders use `object`.

## Adding New Features

1. Put the rule in `roto-core`, behind a narrow interface if `roto-cli` or `roto-app` needs to call
   it. Neither front end contains pipeline logic.
2. If it is a pipeline stage: make it a pure function of its inputs and parameters, give it a cache
   key from both, and cover it with a unit test on a synthetic raster (known mask, period, ramps).
3. If it reads or writes a file format (`roto.json`, `profile.json`, manifest, `.ase`): change the
   model, its serializer and its round-trip test together.
4. If it is consumer-specific: it does not belong here — make it configuration in `roto.json` or
   leave it to the consumer's `afterPublish` hook.

## Code quality

- **Match the existing pattern first.** Before writing a new stage, model, reader or writer, search
  for one that does the same job. If one exists, follow its exact structure and naming. Introducing a
  second idiom for the same problem makes the codebase harder to read and extend. When the pattern
  itself no longer fits the new work, the fix is to change the pattern everywhere, not to run two —
  see **Refactoring**.
- **Abstract duplicated logic.** When two or more classes share near-identical method bodies,
  extract a parameterised base class or helper and have each concrete class extend or delegate.
- **detekt is the floor, not the ceiling.** `config/detekt/detekt.yml` holds the shared rule set on
  top of detekt's defaults. There is no baseline: all code has to come in clean. Fix a finding rather
  than widening the rule; adjust `detekt.yml` only when a rule genuinely does not fit this codebase,
  with the reason in a comment beside the override.
- **SOLID — weighted for this codebase.** Apply Single Responsibility (each class has one reason to
  change), Interface Segregation (prefer narrow interfaces over fat ones), and Dependency Inversion
  (depend on abstractions passed in, not on concrete singletons constructed inside a class).
  Open/Closed and Liskov follow naturally. When a refactor conflicts with these, prefer S then I
  then D.

## Refactoring

The project is pre-release: no shipped builds, no outside consumers relying on a stable API or file
format. **There is no backwards compatibility to keep.** When a shape is wrong, change it outright —
no deprecation shims, no migration code, no compatibility aliases, no second code path kept "just in
case" — and delete the old way in the same commit. (A file format still moves as a unit — model,
serializer, sample files and round-trip tests change together — but nothing has to keep reading an
older shape.)

In this phase, prefer consistency, efficiency and cleanliness over speed of delivery. There is
budget for maintainable code: a change that lands later but leaves the codebase uniform beats one
that ships today and leaves a wart. This is not a licence to gold-plate — it means that when the
choice is between bolting a special case onto the current shape and reworking that shape so the new
code belongs, take the rework.

So when a feature touches existing code:

- **It fits the surrounding idiom** → mirror the neighbours (**Match the existing pattern first**,
  above).
- **It doesn't fit** — a parameter half the call sites suddenly need, a class that has grown a
  second reason to change, an abstraction that no longer spans its cases → refactor the existing
  code first, then add the feature to the corrected shape.

When a refactor clears out a mistake that could be made again, leave something behind so it isn't:

- **Prefer an enforcing test** that locks the invariant the refactor established.
- **Otherwise, or as well, write it down** — a line under **Constraints** below, or in the relevant
  section, giving the rule and the reason.

## Constraints

- Do not add pipeline logic to `roto-cli` or `roto-app` — both are front ends over `roto-core`.
- Do not let `roto-core` depend on Compose, AWT windowing or any UI toolkit. `javax.imageio` for
  PNG IO is fine.
- Do not rename JSON fields casually — `roto.json`, `profile.json` and the publish manifest are read
  by consuming projects.
- Do not introduce new dependencies without strong justification; add via
  `gradle/libs.versions.toml` and the relevant `build.gradle.kts`.
- Do not introduce Spring, Ktor, Hibernate, or any backend framework speculatively.
- Tests live in the module they exercise: `roto-core/src/test/kotlin/com/roto/core/...` for the
  pipeline, models and file formats; `roto-cli/src/test/...` and `roto-app/src/test/...` only for
  logic that genuinely lives in those modules.
- Do not cite a `CODE_REVIEW.md` paragraph number in a code comment or a commit message. The file is
  local, gitignored, and renumbers as items land — the citation goes stale almost immediately and
  points at nothing for anyone without that exact local snapshot. State the rationale in the comment
  or commit body itself instead.
