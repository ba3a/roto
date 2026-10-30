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
- The Write and Edit tools turn a `\uXXXX` escape in the content into the literal character, so an
  invisible character (a byte-order mark, a zero-width space) lands in the source unseen. Write such
  characters in Kotlin as `Char(0xFEFF)`, or check the bytes afterwards with `od -c`. `SourceHygieneTest`
  fails the build on any such character in `roto-core`'s sources, comments included.

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

`FfmpegIntegrationTest` runs the real `ffmpeg` and `ffprobe` (7 or later, for `-display_rotation`)
and is skipped when they are not on `PATH`; a green run without them has not exercised them. Every
other video test goes through a fake `ProcessRunner` and always runs. `PATH` is not a Gradle task
input, so after installing `ffmpeg` an up-to-date `test` task replays the old skipped results — run
`./gradlew :roto-core:test --rerun` once.

The Gradle daemon is pinned to Java 21 by `gradle/gradle-daemon-jvm.properties`, matching the
`jvmToolchain(21)` the modules compile against. Do not remove it: detekt 1.23.x bundles the Kotlin
1.9 compiler, which dies with `IllegalArgumentException: 25.0.2` on a JDK 25 daemon.

## Architecture

Package-by-responsibility under `com.roto.core`, `com.roto.cli` and `com.roto.app`. Grow the package
table here as packages land; each entry names the package and its one responsibility.

| Package | Responsibility |
|---|---|
| `com.roto.core.image` | `Raster` (the one image type every stage reads and writes: row-major `0xAARRGGBB` pixels, straight alpha, equality by content), `IndexedRaster` (its palette-index counterpart), `Mask` (its foreground/background counterpart, stored as opaque white on transparent), `Argb` (channel packing) and `PngCodec` (PNG read/write through `javax.imageio`) |
| `com.roto.core.ase` | `AseSprite` / `AseLayer` / `AseFrame` / `AseCel` (the indexed `.ase` subset Roto handles; validated on construction) and `AseCodec` (the public entry point; `AseWriter`, `AseReader` and `AseFormat` behind it). A file outside the subset fails with `AseFormatException` naming the feature |
| `com.roto.core.color` | `OkLab` (perceptual colour space every stage compares colours in; conversion to and from `Argb`) and `Srgb` (the sRGB transfer function, table-driven decode) |
| `com.roto.core.template` | `Template` (`{variable}` / `{variable:format}` placeholder strings, validated on construction), `PathTemplate` (a template that always expands to a path inside the project), `TemplateVariable` / `TemplateValues`, `RelativePath` (the one portable relative-path rule) and `SafeName` (the one rule for names that become file names: ids, facings, actions) |
| `com.roto.core.project` | `ProjectConfig` (the consuming project's `roto.json`: project id, facings, actions, mirror pairs, canvas, output and manifest templates, `afterPublish`), `ProjectConfigCodec` (its JSON read/write) and `Project` (a config plus its root folder; resolves every configured path under it) |
| `com.roto.core.profile` | `Profile` / `Clip` / `TimeRange` / `PlateSource` (a profile's `profile.json`: the clips filmed for it, where each clip's clean plate comes from, its `MaskSettings`, `RootSettings` and `CycleSettings`, the artist's key-frame `markers` once set, its `fixes`, its `ZoneSettings`, `ShadeSettings` and `DraftSettings`; the ramps it uses, which every zone must be one of; and the profile's `ScaleSettings` once marked), `ProfileCodec`, `ProfileFolder` (the `profile.json` / `sources/` / `cache/` / `handoff/` layout) and `ProfileRepository` (the profiles of one project under `<library>/<project id>/`) |
| `com.roto.core.json` | `JsonCodec` (the base every JSON format's codec extends), `CanonicalJson` (the one kotlinx `Json` configuration, shared by every codec and by cache keys) and `SourceStringSerializer` (a value stored as its source string) |
| `com.roto.core.cache` | `CacheKey` / `CacheKeyBuilder` (a stage output's name: SHA-256 of stage, algorithm version, parameters and inputs), `StageCache` (entries under a profile's `cache/<stage>/<hash>/`, each filled in a temporary folder and moved into place; `get` for a value a stage computes, `getOrFill` for files another program writes), `StageOutput` (a stage's output as the next stage takes it: its key up front, its value produced on first use) and `CacheCodec` with `RasterCacheCodec`, `RasterSequenceCacheCodec`, `RasterBackedSequenceCacheCodec` (per-frame values stored as rasters: `MaskSequenceCacheCodec`, `LabelSequenceCacheCodec`) and `JsonCacheCodec` (how one kind of output is stored in an entry) |
| `com.roto.core.io` | `AtomicFile` (write-to-temporary-then-move, so no file is ever left half-written) |
| `com.roto.core.process` | `ProcessRunner` (the seam every external program goes through, with the directory it runs in, so tests can stand in for it) and `SystemProcessRunner` (runs it as an OS process, draining stdout and stderr together) |
| `com.roto.core.video` | `Ffmpeg` (probe, range extract and thumbnail through `ffmpeg` / `ffprobe`), `FfprobeParser`, `VideoInfo` / `FrameRate` (an exact fraction), and `FrameExtractor` (the first stage: a range of a source video as cached frames at the working height) |
| `com.roto.core.plate` | `TemporalMedian` (the per-pixel, per-channel median of equally sized frames) and `CleanPlateBuilder` (the plate stage: the median of a marked empty-wall range or of the clip's own frames) |
| `com.roto.core.mask` | `ForegroundMask` (one frame against the plate: weighted OKLab difference, hysteresis, open and close, largest region, hole fill), `Hysteresis`, `Morphology` (square open and close; outside the mask is background), `ConnectedComponents` (largest region, hole fill) on the shared `Flood`, `MaskSettings` (per clip in `profile.json`), `ForegroundMasker` (the mask stage), `BrushStroke` (the artist's strokes on key frames' masks, per clip in `profile.json`), `BrushPath` (the pixels a stroke covers, shared with the zone brush), `MaskBrush` (applies them) and `MaskFixer` (the mask fix stage: only what a draft is drawn from reads it, so a fix cannot move the key frames it sits on) |
| `com.roto.core.track` | `RootFinder` (a mask's root: the median x of its hip band), `TrackSmoothing` (gap filling, centred moving average), `Stabiliser` (the whole-pixel horizontal shifts that hold the smoothed root still; never vertical, so the bob stays), `HorizontalShift` (applies a shift to a `Raster` or `Mask`), `RootTrack` (with its anchor; `centreX` is where a crop follows the figure) / `RootSettings` and `RootTracker` (the root stage) |
| `com.roto.core.cycle` | `RepeatPeriod` (how often the root-aligned silhouette repeats: mask IoU autocorrelation on `MaskBits`), `ContactFinder` (the phase of widest leg spread in the bottom band), `KeyFrames` (key frames as rising boundaries; spacing is duration, exact in ms; moved, added and removed by the rules the artist's markers follow), `CycleDetector` (repeat, contacts, evenly spaced proposal from a contact), `CycleAnalysis` / `CycleSettings` and `CycleAnalyser` (the cycle stage) |
| `com.roto.core.framebox` | `ScaleSettings` / `ScaleMarks` (per profile: body height on the canvas from head-top and feet marks, vertical squash), `FrameBoxes` (canvas centred on each frame's root, the ground row's bottom edge on the clip's ground line), `FrameBoxLayout` (lays them out; ground line = median lowest mask row), `CanvasSampler` (a frame, mask or label map at N times the canvas, nearest-neighbour) and `FrameBoxer` (the frame box stage) |
| `com.roto.core.palette` | `Palette` (a project's 1..255 opaque colours; one `.ase` index stays free for transparency; `paint` shows palette labels as colours), `PaletteCodec` (reads `.gpl` and `.hex`, by extension), `HexColor` (`#rrggbb`), `RampSet` / `Ramp` (`ramps.json`: named dark-to-light ramps, checked in OKLab, and an optional outline colour) with `RampSetCodec`, `ProjectPalette` (both loaded through `Project` and checked against each other) and `PaletteQuantiser` (a raster to nearest-palette labels in OKLab) |
| `com.roto.core.zone` | `ZoneSettings` / `Zone` / `ZoneSample` / `ZoneStroke` (per clip: zones, each with a ramp and the points the artist clicked, and the artist's strokes over them on key frames), `ZoneBrush` (applies those to figure only), `NearestZone` (prototypes from 3x3 patches; every masked pixel takes its nearest in OKLab, lightness weighted down; label maps hold 0 for background, zone `i` as `i + 1`) and `ZoneClassifier` (the zone stage) |
| `com.roto.core.shade` | `ShadeSettings` (per clip: the percentiles a zone's lightness range runs between), `ClipShading` / `ZoneShading` (that range per zone, fixed for the whole clip so nothing flickers, split evenly into ramp steps), `ShadingFit` (fits it over every frame), `DraftColours` (each zone's ramp and the outline as palette labels: palette index + 1, `0` background, so `.ase` entry 0 can be transparent), `RampShader` (zone + lightness to palette label), `ShadingAnalyser` (the shading stage) and `DraftShader` (each key frame shaded at `FACTOR` = 4 times the canvas) |
| `com.roto.core.draft` | `Draft` (palette labels plus zones at canvas size), `MajorityReduce` (4x4 blocks: figure at ≥ 50 % coverage, majority zone, then that zone's majority colour; ties to the lowest label), `OrphanCleanup` (a pixel matching none of its 8 neighbours takes their majority), `RampDither` (optional 2x2 Bayer checker on the darker side of adjacent-step edges), `Outline` (1 px inside the silhouette, project outline colour or the ramp's darkest; drawn last), `DraftFinisher`, `DraftSettings` and `DraftRenderer` (the draft stage: what the handoff's `draft` layer shows) |
| `com.roto.core.handoff` | `HandoffRender` / `HandoffFrame` (what Roto writes per key frame: duration, palette-mapped reference, finished draft), `HandoffMerge` (the indexed sprite: entry 0 transparent, layers `reference` at 40 % cel opacity, `draft`, then the artist's; re-render keeps every artist layer, remapping by colour, and refuses rather than alter art) and `HandoffWriter` (reads the existing file first; never overwrites one it cannot read) |
| `com.roto.core.publish` | `ArtFrames` (the handoff's `art` layer as RGBA per frame; a frame without art stops the publish), `ArtProgress` (how far a clip's handoff has come: none yet, painted so many of its frames, or unreadable and why), `Mirror`, `ClipManifest` (frames root-relative with durations, loop, source clip, mirrored) with `ClipManifestCodec`, and `Publisher` (frames and manifests by the `roto.json` templates, stale frames removed, opt-in mirroring into the paired facing but never over a filmed one, then `afterPublish` in the project root) |
| `com.roto.core.timeline` | `FilmstripBuilder` (the filmstrip stage: evenly spread thumbnails of a whole video), `Trim` (where a video's trim handles may land: frame starts, inside the video, at least one frame), `SourceImport` (a video copied into `sources/`, never replacing a different file) and `ClipImporter` (a trimmed video saved as a clip of a profile, its frames extracted) |
| `com.roto.core.render` | `ClipFootage` (one cut of a video: its frames, held once and shared, at the source's probed frame rate), `ClipMasking` (the stages that need only footage: plate, masks, root, key frames) and `ClipPipeline` (carries on from a `ClipMasking` to the handoff; each `StageOutput` built on first use; the one wiring both front ends use), `Drawing` (the canvas, palette and scale every clip of a profile is drawn with), `ProfileRenderer` (each clip's handoff written through `HandoffWriter`) and `RenderException` |
| `com.roto.core.settings` | `Settings` (the artist's own, on this machine: the library folder, chosen once, the project last opened, and the editor chosen, if any; absolute paths), `SettingsCodec` and `SettingsStore` (`settings.json` in the system's per-user configuration folder, shared by the app and the CLI) |
| `com.roto.cli` | `Main.kt` (entry point), `Arguments` (`roto <command> <profile> [--project] [--library]`, library defaulting to `ROTO_LIBRARY`, then to the one chosen in the app) and `Cli` (opens the project and profile, hands them to `ProfileRenderer` or `Publisher`, reports; exit codes 0 / 1 failure / 2 usage) |
| `com.roto.app` | `Main.kt` (entry point), `HomeModel` / `HomeState` (the home window's state and actions, plain Kotlin and tested without a window), `HomeScreen` (the Compose window: project, library, profile list, selected profile), `TrimModel` / `TrimScreen` (import and trim: preview, filmstrip with drag handles, action and facing), `ClipSetupModel` / `ClipSetupScreen` (plate, live mask preview, key-frame markers on the filmstrip, brush fixes on key frames), `DrawModel` / `DrawScreen` (scale marks, frame box, zone samples and strokes, live draft), `HandoffModel` / `HandoffScreen` (render handoffs, see how far each is painted, opt in to mirroring, publish what is finished), `Editor` (what a handoff opens in: `ChosenEditor`, through `ProcessRunner`, or `SystemEditor`, the system's default for the file), `OffUiThread` (the one way a model runs work off the UI thread and turns a failure into its message), `Filmstrip` / `StripGestures` and `PictureView` / `PictureGestures` (the one strip and one picture every screen drags and paints on), `Controls` (the shared sliders and choices), `Screen` (what fills the window), `Pictures` and `Pickers` |

Naming patterns: data models are nouns (`Raster`, `Palette`, `ClipManifest`); a pipeline stage is a
noun named for what it produces or does (`CleanPlateBuilder`, `ForegroundMasker`, `RootTracker`);
singleton config holders use `object`.

## Adding New Features

1. Put the rule in `roto-core`, behind a narrow interface if `roto-cli` or `roto-app` needs to call
   it. Neither front end contains pipeline logic.
2. If it is a pipeline stage: make it a pure function of its inputs and parameters, give it a cache
   key from both, and cover it with a unit test on a synthetic raster (known mask, period, ramps).
   Build the key with `CacheKey.of(stage, version)`: put the parameters in as their `@Serializable`
   model, so a field added later cannot be left out of the key; put an upstream stage's output in as
   its `CacheKey`, not its content; put a source file in with `putFile`. Bump `version` whenever the
   algorithm's output changes for the same inputs, or stale entries are read back as current.
   A stage takes its upstream outputs as `StageOutput`s and returns one, like `FrameExtractor` and
   `CleanPlateBuilder`: it builds its key from their keys and calls their `get()` only inside its own
   cache miss, so a cached result never loads anything upstream. Keep the algorithm itself in a pure
   object of rasters in and rasters out (`TemporalMedian`), tested without a cache.
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
- Pipeline stages take and return `Raster`, never `BufferedImage`. PNG goes in and out only through
  `PngCodec`: `BufferedImage.getRGB` silently colour-converts gray images (brightening every
  mid-tone), and `PngCodec` is the one place that knows to read their samples directly.
  `PngCodecTest` locks this.
- Transcendental maths in anything that reaches output (`pow`, `cbrt`, `exp`, `log`, trig) goes
  through `StrictMath`, never `Math` or `kotlin.math`'s wrappers of it. `Math` may use
  platform intrinsics whose last bit differs between CPUs and JVMs, which breaks byte-identical
  output. `sqrt` and plain arithmetic are exact under IEEE 754 and need no special care.
- Colours are compared in `OkLab`, never as raw sRGB channel differences.
- A clip's frames are the largest thing in memory: a few seconds at the working height is hundreds of
  megabytes. Hold them once, in a `ClipFootage` shared by everything looking at one clip, and take a
  sequence one element at a time rather than copying or converting it whole (`writeEach`, `readEach`,
  `TemporalMedian` reading in place). Three such copies ran a 4-second clip out of a 512 MB heap.
- `.ase` output must open in both Aseprite and LibreSprite (an older Aseprite fork). Write only
  chunks and fields an older reader understands, or pair a newer one with its legacy equivalent the
  way `AseWriter` writes both palette chunks. For opacity that must look the same in every editor,
  use cel opacity, not layer opacity. `AseCodecWriteTest` checks the byte layout against an
  independent parser (`AseLayout`), not against the writer's own constants.
- `AseReader` reads files the artist saved, not only files Roto wrote. Anything an editor adds that
  `AseSprite` cannot hold is either skipped (chunks that describe nothing it models) or rejected with
  an `AseFormatException` naming the feature — never silently misread. When an editor turns out to
  write something new, add the case to `AseCodecReadTest` through `AseFileBuilder` first.
- JSON goes through kotlinx.serialization and nothing else. A JSON model validates itself in `init`
  (kotlinx runs it on decode); its codec is an `object` extending `JsonCodec`, like
  `ProjectConfigCodec` and `ProfileCodec`, with that format's own `IOException` subclass. `JsonCodec`
  decodes strictly (unknown keys are errors, not ignored), wraps every `IllegalArgumentException` —
  `SerializationException` is one — in that exception, and writes UTF-8 with `\n` endings so the same
  model always gives the same bytes. Never build a second `Json` instance or call one from a model or
  stage: `CanonicalJson` is the one configuration, so a model's file form and its cache-key form are
  the same text.
- A `@Serializable` class never has a `private companion object`: the plugin puts `serializer()` on
  the companion, and making it private makes `Model.serializer()` inaccessible everywhere else. Put a
  model's constants and helpers at top level in its file, `private` there.
- Every codec's `write(value, Path)` goes through `AtomicFile`. A profile, a handoff `.ase` with the
  artist's painting in it, or a published frame must never be left truncated by a crash mid-write.
- Every path a consuming project configures is relative to its root and goes through `RelativePath`
  or `PathTemplate`, which reject absolute paths, `..`, backslashes and characters Windows forbids,
  and stop a substituted value from adding a directory. Never `root.resolve` a configured string
  directly.
- Do not rename JSON fields casually — `roto.json`, `profile.json` and the publish manifest are read
  by consuming projects.
- Do not introduce new dependencies without strong justification; add via
  `gradle/libs.versions.toml` and the relevant `build.gradle.kts`.
- Do not introduce Spring, Ktor, Hibernate, or any backend framework speculatively.
- Every external program — `ffmpeg`, `ffprobe`, a project's `afterPublish` command, the artist's
  chosen editor — runs through a `ProcessRunner` passed in, never `ProcessBuilder` directly, so the code around it is tested without
  the program. A stage whose output comes from `ffmpeg` puts `Ffmpeg.version` in its cache key, and
  `ffmpeg` scaling and colour conversion keep swscale's `bitexact` flag: both are what make that
  output reproducible.
- `java.nio.file.Path` is an `Iterable<Path>` of its name segments, so `paths + path` on a `List<Path>`
  splices in the segments instead of adding the path. Add one path as `paths + listOf(path)`.
- Tests live in the module they exercise: `roto-core/src/test/kotlin/com/roto/core/...` for the
  pipeline, models and file formats; `roto-cli/src/test/...` and `roto-app/src/test/...` only for
  logic that genuinely lives in those modules.
- Do not cite a `CODE_REVIEW.md` paragraph number in a code comment or a commit message. The file is
  local, gitignored, and renumbers as items land — the citation goes stale almost immediately and
  points at nothing for anyone without that exact local snapshot. State the rationale in the comment
  or commit body itself instead.
