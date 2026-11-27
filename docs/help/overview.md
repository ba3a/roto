# Overview

## Purpose

The Overview shows the selected profile as a grid: one row for each action and one column for each facing, in the order of `roto.json`. Each cell shows where that action and facing stand. A line above the grid names what to do next. It is on the [home screen](home.md), above the clips.

## Steps

1. Select a profile on the home screen. The cells fill in one by one.
2. Read the Next line. It names one cell.
3. Press Open Next to open the screen that moves that cell forward.
4. Or press any cell to open the screen for it.
5. Come back to the home screen. The Overview reads the profile again.

## Controls

| Control | What it does |
|---|---|
| Cell | Shows the state of one action and facing, and opens the screen that moves it forward. A small M marks a cell that also has a model. |
| Open Next | Opens the screen for the cell named on the Next line. It is off while the cells load and when everything is published. |

The states are:

- Not filmed: no clip has this action and facing. The cell opens the video import.
- Mirrored: not filmed, but the paired facing is filmed and mirrored. The cell opens the handoff screen.
- Model: not filmed, but a model exists. The cell opens the model import screen.
- Setup: filmed, with no handoff yet. The cell opens clip setup.
- Painting n/m: the handoff has art on n of m frames. The cell opens the handoff screen.
- Finished: art on every frame, not published. The cell opens the handoff screen.
- Published: published, and the frames match the art.
- Out of date: published, but the art has changed since.
- Unreadable: the handoff cannot be read. The reason shows below the grid.

## Tips and problems

- Next goes first to the clip with the most frames painted but not finished. Then it takes an out-of-date clip, a finished clip, a clip with no handoff, and last an action and facing that is not filmed.
- With nothing left, the line says "Everything is published".
- A cell that shows "Unreadable" has a damaged or unsupported handoff file. Open the handoff screen to see the reason.
- `roto status <profile>` prints the same grid and the same Next line in a terminal.
