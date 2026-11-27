---
layout:
  wordmark: true
---

<div class="section-marker" id="hero"></div>

# Roto

Reference footage in, a layered `.ase` draft out for polishing in LibreSprite or Aseprite, then the finished frames published into your game.

Every step is a deterministic algorithm or the artist's own hand: no machine learning, no generative models. The same inputs always give byte-identical output.

<div class="section-marker" id="how-it-works"></div>

## How it works

1. Create a project with the wizard. Pick a preset, name it, and set facings, actions, canvas, colours and output.
2. Import a clip or a photo and trim it.
3. Roto separates the figure, proposes key frames and maps it onto your palette ramps.
4. Paint on the `art` layer of the `.ase` handoff.
5. Publish writes your frames and a manifest where your project says.

<div class="section-marker" id="features"></div>

## Features

- A project wizard: you never write `roto.json`, `ramps.json` or a palette file by hand.
- Built-in presets (`blank`, `side-view`, `top-down-4`, `top-down-8`), plus your own.
- Share a setup as one `.rotopreset` file. A bundle never carries a command, so importing one cannot run anything.
- Import a finished animation (`.ase`, PNG folder or libGDX atlas) as a model: Roto offers its timing, body height and ramps, and adds a pose guide layer to the handoff.
- A coverage overview of every action and facing, with the next thing to do. `roto status` prints it.
- A flipbook with onion skin and a loop-seam diff, and an art lint for stray pixels, off-palette colours and flicker. `roto check` runs the lint.
- Retime a finished clip (speed or frame count) without repainting it.
- Ramp-swap variants: publish a recoloured character from finished art, with no filming.
- Draft hold: a setting that makes the draft flicker less between key frames.
- Deterministic pipeline: no machine learning, byte-identical output.
- Desktop app and command line.
- Early development.

<div class="section-marker" id="quick-start"></div>

## Quick start

Download the installer for Windows (`.msi`), macOS (`.dmg`) or Linux (`.deb`), or the command-line
zip, from the [releases page](https://github.com/ba3a/roto/releases). Release candidates are marked
pre-release, and the installers are not signed yet, so your system may warn before it runs them.

Or start the desktop app from a checkout and choose **Start from a preset**:

```bash
./gradlew :roto-app:run
```

Or create a project from the command line, and export its setup for others:

```bash
roto init my-game --preset side-view --id my-game
roto export my-setup.rotopreset --project my-game
```

See what is left to do, and lint your painted art before publishing:

```bash
roto status worker --project my-game
roto check worker --project my-game
```

Installers need only `ffmpeg` and `ffprobe` on `PATH`. A checkout also needs JDK 21.
