# Pipeline overview

Roto turns footage into a palette draft, hands it to you to paint over, then publishes your painting.
Every automatic step is a deterministic algorithm: the same footage and parameters always give
byte-identical output. Every other step is your own hand.

For how to shoot the footage, see the [filming guide](filming-guide.md).

## The steps

| # | Step | What happens | You decide |
|---|---|---|---|
| 1 | Film | Phone video of the motion, or a photo, against a plain wall | The footage |
| 2 | Import | The video or photo is copied into the profile's `sources/`; `ffprobe` reads its duration, frame rate and rotation | The profile |
| 3 | Trim | `ffmpeg` extracts the range you chose as frames at a working height of 480 px | The range, the action and the facing |
| 4 | Clean plate | The empty wall: a range you marked, a photo of the wall, or the per-pixel median of the clip's own frames | Which of the three |
| 5 | Mask | Each frame is compared with the plate in OKLab; hysteresis threshold, open and close, largest region, hole fill | Brush fixes on key frames |
| 6 | Stabilise | The figure's root (the median x of its hip band) is smoothed and held still horizontally; the vertical bob stays as filmed | Nothing |
| 7 | Key frames | The walk cycle's period and foot contacts are found, and evenly spaced markers are proposed from a contact | Drag, add and remove markers; spacing becomes frame duration |
| 8 | Frame box and scale | The project's canvas is centred on each frame's root with the ground row on the clip's ground line | Mark head-top and feet on one standing frame, once per profile; optional vertical squash; tick "Walks toward or away from the camera" on a north or south clip to scale each frame to the figure and follow its feet |
| 9 | Zones | Every figure pixel takes the nearest zone prototype in OKLab | Click samples for each zone and assign it a ramp; brush overrides on key frames |
| 10 | Draft | Shading from clip-wide lightness percentiles, downscaled by majority vote, with outline and orphan cleanup | Optional Bayer dither at ramp edges, off by default; optional hold (0 to 8, off by default) |
| 11 | Handoff | One layered `.ase` per clip | Paint on the `art` layer |
| 12 | Publish | Your `art` frames and a manifest are written by the project's templates, then `afterPublish` runs | Optional per-clip mirroring |

Photos take steps 2, 4 (a photo of the empty wall), 5 and 8 to 12 on a one-frame clip.

## Where things live

Roto keeps two kinds of folder apart:

- **The consuming project** holds a `roto.json` that tells Roto the palette, ramps, facings, actions,
  canvas size, output paths and post-publish command. Roto knows nothing about any one game; every
  name, size and path comes from this file. The app's project wizard (or `roto init`) writes it, with
  `roto/palette.hex` and `roto/ramps.json`, from a preset: palette and ramps first, `roto.json` last,
  never over an existing file.
- **Presets** are everything in a setup except the project's id, folder and `afterPublish`. Built-in
  presets ship inside Roto; yours live in `presets/` beside `settings.json`.
- **The library**, chosen once and kept outside every repository, holds one folder per profile:

A preset travels as a **bundle**, one `<name>.rotopreset` file (a ZIP of `preset.json`,
`palette.hex` and `ramps.json`). Export writes the same bytes for the same project. Import accepts
only those three entries, caps their sizes, validates each, and imports nothing if any part fails.

```text
<library>/<project id>/<profile>/
  profile.json    the clips, markers, zones, scale and every other setting
  sources/        the original videos and photos, never modified
  cache/          stage outputs; always regenerable, safe to delete
  models/         finished animations imported as read-only references, one <action>_<facing>/ each
  handoff/        one <action>_<facing>.ase per clip
```

## The handoff file

`handoff/<action>_<facing>.ase` is an indexed-colour sprite with the project palette embedded and one
frame per key frame, each with its duration. Palette entry 0 is transparent. It has three layers, or four when the clip has a model:

| Layer | Contents | Re-render |
|---|---|---|
| `model` | The clip's model, one model frame per key frame, at 40 % cel opacity; only with a model | Rewritten |
| `reference` | The footage, palette-mapped, at 40 % cel opacity | Rewritten |
| `draft` | The generated pixel-art draft | Rewritten |
| `art` | Yours | Never touched |

Re-rendering after a palette, zone or marker change rewrites `reference` and `draft` and keeps
everything you painted, remapping your colours to the new palette. If the file cannot be read, or the
painting cannot be kept intact, Roto refuses and leaves the file alone rather than alter your art.
The file opens in both LibreSprite and Aseprite.

## Models

A **model** is a finished animation, imported into a profile as read-only reference: an `.ase` or
`.aseprite` file, a folder of PNG frames, or a libGDX `.atlas`. Each animation of the source becomes
a folder `models/<action>_<facing>/` holding `model.json` (action, facing, loop, durations) and the
frames `000.png`, `001.png` and so on. Every frame must be the project's canvas size; a rotated atlas
region is refused. Importing the same source twice gives byte-identical folders, and an existing model
is replaced only on consent. Roto never edits or publishes a model.

Roto measures a model (figure box per frame, body height, feet row, root, ramp and step usage; alpha
below 128 is background) and offers, never applying anything silently:

- its frame count and durations as key frames of a clip, placed along the detected cycle;
- its body height as the profile's `bodyHeight`, and its ground line;
- the ramps it uses, to mark on the profile.

A clip can name a model. The handoff then gets a `model` layer below `reference`: key frame `i` of `k`
shows model frame `round(i * (n - 1) / (k - 1))` of `n`, in integer maths. It is a pose guide for your
painting. If the model cannot be read, the offers hide and other work continues.

## Publishing

Publish reads the `art` layer and writes each frame as a PNG at the path the project's `output`
template gives, plus a small manifest per clip at the `manifest` template: the frames, their durations
in milliseconds, whether the clip loops, and the source clip. Frames left over from an earlier
publish with more frames are removed.

A clip can opt in to **mirroring**, which also writes a mirrored copy into the paired facing named in
`mirrorPairs`. It is off by default and never overwrites a facing you filmed yourself.

Last, the project's `afterPublish` command runs in the project's root, with `{profile}` available to
it. Anything specific to your game, such as updating a skin file or baking an atlas, belongs there.

## Authoring aids

These read your work and never change the `art` layer.

- **Coverage.** One cell per action and facing: not filmed, mirrored, model only, setup, painting n/m,
  finished, published, out of date or unreadable. A cell is published only when the published PNGs equal
  the (retimed, mirrored) art pixel for pixel. Next picks the clip with the most frames painted, then out
  of date, finished, setup, and last not filmed, in `roto.json` order. `roto status` prints it.
- **Flipbook.** Plays a handoff at its durations: art where painted, else draft, else reference. It has
  onion skin, a loop-seam diff and the model ghost, and a mirrored clip plays flipped.
- **Lint.** Rules `OffPalette`, `StrayPixel`, `OutlineGap`, `GroundDrift`, `Flicker`, `ModelDrift` and
  `LoopSeam`, in a fixed order. A finding is advice and never blocks a publish. `roto check` prints them
  and exits 1 when there are any.
- **Retime.** A clip's `retime` sets a speed (25 to 400 %) and which handoff frames to publish. Only the
  published durations and frames change; the handoff stays as painted. Resampling picks existing frames
  and never blends. A mirror uses the filmed clip's retime.
- **Variants.** A profile with `variant` instead of clips publishes the finished art of its base with
  ramps swapped for others. Unswapped colours and transparency pass through. A base cannot be a variant.
  Unfinished base clips are skipped and listed.
- **Hold.** `hold` in the draft settings keeps a 4x4 block as it was in the previous key frame unless the
  picture changes by more than `hold` samples of 16. 0 leaves the draft as before.

## Running it

The desktop app first creates the project with the wizard, then walks the same steps in order: trim, clip setup, draw, handoff and publish. The command
line runs the stages that need no judgement:

```bash
roto init <folder> --preset <name> --id <id>   # create a project from a preset
roto export <name.rotopreset>                  # write the project as a bundle
roto import-model <profile> <source>  # import a model clip (--action, --facing, --pattern, --duration, --replace)
roto render <profile>     # write every clip's handoff, keeping what you painted
roto publish <profile>    # write the art frames and manifests, then run afterPublish
roto status <profile>     # what is filmed, painted and published, and what to do next
roto check <profile>      # lint the painted art; exit 1 on findings
```

Because stage outputs are cached under each parameter set, changing the palette and re-rendering only
redoes the stages that palette reaches.

## Core types

The pipeline is built from a few types of `roto-core`:

- `Project` and `ProjectConfig` describe a project and its settings.
- `ClipPipeline` runs the stages on a `Clip`.
- `IndexedRaster` and `Palette` hold the pixel art.
- `AseSprite` is the Aseprite file the pipeline writes.
