# Import models

## Purpose

This screen imports finished animations into the profile as model clips. A model is read-only reference: Roto measures it and offers its timing, size and colours for new clips. Roto never edits or publishes a model.

## Steps

1. Press Choose file… for an `.ase`, `.aseprite` or `.atlas` file, or Choose folder… for a folder of PNG frames.
2. Check the rows. Each row is one animation that Roto found in the source.
3. Choose the Action and the Facing of every row.
4. Set the durations and the loop flags that the source does not give.
5. Press Import.

## Controls

| Control | What it does |
|---|---|
| Close | Leaves the screen. It is off while Roto imports. |
| Choose file… | Picks an `.ase`, `.aseprite` or `.atlas` file and reads the animations in it. |
| Choose folder… | Picks a folder of PNG frames. A `clip.json` beside the frames gives the durations and the loop flag. |
| Atlas name pattern | Sets how Roto reads an atlas region name as an action, a facing and a frame number. Regions that match no pattern are listed and skipped. |
| Frame duration | Sets the duration in ms of every frame of a source that gives none. |
| Action | Files the row under one of the project's actions. |
| Facing | Files the row under one of the project's facings. |
| Row duration | Gives every frame of the row one duration in ms. Leave it empty to keep the source's own. |
| Loop | Marks the animation as one that repeats. |
| Replace existing models | Agrees to replace models that already have the same action and facing. It shows only when a row would replace one. |
| Import | Writes every row as a model of the profile. It is off while a row has a problem. |

## Tips and problems

- A frame smaller than the project's canvas is placed on it, centred across and with its bottom row on the canvas's bottom row. Roto never rescales a model, so a frame larger than the canvas is refused, and the message names both sizes.
- A row that shows a problem keeps Import off until you fix it. Two rows cannot have the same action and facing.
- An `.ase` file must be indexed. Other files fail with a message that names the feature.
- To remove a model, use the Models list on the [Home](home.md) screen.
