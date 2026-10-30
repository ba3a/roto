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
| 8 | Frame box and scale | The project's canvas is centred on each frame's root with the ground row on the clip's ground line | Mark head-top and feet on one standing frame, once per profile; optional vertical squash |
| 9 | Zones | Every figure pixel takes the nearest zone prototype in OKLab | Click samples for each zone and assign it a ramp; brush overrides on key frames |
| 10 | Draft | Shading from clip-wide lightness percentiles, downscaled by majority vote, with outline and orphan cleanup | Optional Bayer dither at ramp edges, off by default |
| 11 | Handoff | One layered `.ase` per clip | Paint on the `art` layer |
| 12 | Publish | Your `art` frames and a manifest are written by the project's templates, then `afterPublish` runs | Optional per-clip mirroring |

Photos take steps 2, 4 (a photo of the empty wall), 5 and 8 to 12 on a one-frame clip.

## Where things live

Roto keeps two kinds of folder apart:

- **The consuming project** holds a `roto.json` that tells Roto the palette, ramps, facings, actions,
  canvas size, output paths and post-publish command. Roto knows nothing about any one game; every
  name, size and path comes from this file.
- **The library**, chosen once and kept outside every repository, holds one folder per profile:

```text
<library>/<project id>/<profile>/
  profile.json    the clips, markers, zones, scale and every other setting
  sources/        the original videos and photos, never modified
  cache/          stage outputs; always regenerable, safe to delete
  handoff/        one <action>_<facing>.ase per clip
```

## The handoff file

`handoff/<action>_<facing>.ase` is an indexed-colour sprite with the project palette embedded and one
frame per key frame, each with its duration. Palette entry 0 is transparent. It has three layers:

| Layer | Contents | Re-render |
|---|---|---|
| `reference` | The footage, palette-mapped, at 40 % cel opacity | Rewritten |
| `draft` | The generated pixel-art draft | Rewritten |
| `art` | Yours | Never touched |

Re-rendering after a palette, zone or marker change rewrites `reference` and `draft` and keeps
everything you painted, remapping your colours to the new palette. If the file cannot be read, or the
painting cannot be kept intact, Roto refuses and leaves the file alone rather than alter your art.
The file opens in both LibreSprite and Aseprite.

## Publishing

Publish reads the `art` layer and writes each frame as a PNG at the path the project's `output`
template gives, plus a small manifest per clip at the `manifest` template: the frames, their durations
in milliseconds, whether the clip loops, and the source clip. Frames left over from an earlier
publish with more frames are removed.

A clip can opt in to **mirroring**, which also writes a mirrored copy into the paired facing named in
`mirrorPairs`. It is off by default and never overwrites a facing you filmed yourself.

Last, the project's `afterPublish` command runs in the project's root, with `{profile}` available to
it. Anything specific to your game, such as updating a skin file or baking an atlas, belongs there.

## Running it

The desktop app walks the same steps in order: trim, clip setup, draw, handoff and publish. The command
line runs the stages that need no judgement:

```bash
roto render <profile>     # write every clip's handoff, keeping what you painted
roto publish <profile>    # write the art frames and manifests, then run afterPublish
```

Because stage outputs are cached under each parameter set, changing the palette and re-rendering only
redoes the stages that palette reaches.
