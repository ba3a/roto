# Roto

Roto turns reference footage — a phone video or a photo — into a pixel-art draft you finish by hand in
[LibreSprite](https://libresprite.github.io/) or [Aseprite](https://www.aseprite.org/), then publishes
the finished frames into your game's source tree.

Every step is either a deterministic algorithm or the artist's own hand: no machine learning, no
generative models, no "AI upscaling". The same inputs and parameters always produce byte-identical
output.

> **Status:** early development. The pipeline runs end to end from the command line; the desktop app
> opens projects, manages profiles, imports and trims video, and sets up plates and masks so far.

## How it works

1. Film the motion against a plain wall, one clip per facing.
2. Import the clip, trim it on a filmstrip, pick its action and facing.
3. Roto separates the figure from the wall, re-centres walk cycles, proposes key frames, and maps the
   figure onto your palette's colour ramps.
4. It hands you one layered `.ase` file per clip — a faded reference, the generated draft, and an
   empty `art` layer for your polish.
5. Publish writes your `art` frames wherever your project's config says, plus a small manifest, and
   runs your own post-publish hook.

Everything game-specific — action names, facings, canvas size, output paths — lives in a `roto.json`
in the consuming project, never in Roto itself.

## Modules

| Module | What it is |
|---|---|
| `roto-core` | Pure Kotlin library: rasters, every pipeline stage, palettes, `.ase` IO, project and profile models |
| `roto-cli` | Headless runner: `roto render <profile>`, `roto publish <profile>` |
| `roto-app` | Compose Desktop UI: profiles, timeline, clip setup, handoff and publish |

## Requirements

- JDK 21 (the Gradle wrapper provisions it automatically if missing)
- [`ffmpeg` and `ffprobe`](https://ffmpeg.org/) on `PATH`

## Building

```bash
./gradlew build          # Linux / macOS
./gradlew.bat build      # Windows
./gradlew :roto-app:run  # launch the desktop app
```

## Command line

```bash
roto render <profile> [--project <folder>] [--library <folder>]
roto publish <profile> [--project <folder>] [--library <folder>]
```

`render` writes every clip's handoff `.ase`, keeping whatever you have painted in it; `publish` writes
your `art` frames and manifests where `roto.json` says, then runs its `afterPublish` hook. The project
is the folder holding `roto.json` (the current folder by default); the library is the folder holding
your profiles (`ROTO_LIBRARY` by default, otherwise the one you chose in the app). From a checkout,
run it through Gradle:

```bash
./gradlew :roto-cli:run --args="render worker --project ../my-game --library ../roto-library"
```

## License

[Apache License 2.0](LICENSE)
