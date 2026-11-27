# Handoff and publish

## Purpose

This screen is the way out of Roto and back. Roto renders one layered `.ase` file for each clip. You paint on its `art` layer in your editor. Roto then publishes the finished frames into your project.

## Steps

1. Press Render all, or Render on one clip. Roto writes the handoff file.
2. Press Open in editor on a clip. Paint on the `art` layer. Save the file.
3. Come back to Roto. The progress line shows how many frames you painted.
4. Optional: tick Publish mirrored on a clip whose facing has a mirror pair.
5. Optional: press Flipbook on a clip to play it. Check the loop seam and the onion skin.
6. Press Publish when every clip you want is ready.
7. Press Done.

## Controls

| Control | What it does |
|---|---|
| Done | Goes back to the home screen. |
| Render all | Renders the handoff file of every clip. It never changes the `art` layer you painted. |
| Publish | Publishes the clips that have art on every frame. The label shows how many clips are ready. |
| Render | Renders the handoff file of this clip for the first time. |
| Render again | Renders the handoff file again after a change in setup or draw. It rewrites the `reference` and `draft` layers only. |
| Open in editor | Opens the clip's handoff file in your editor. It is off until the clip is rendered. |
| Publish mirrored | Also publishes this clip, flipped, as the facing it mirrors. It never replaces a facing that you filmed. |
| Flipbook | Plays this clip at its real frame durations. Each frame shows your art where you painted, else the draft, else the reference. |
| Close player | Closes the flipbook. |
| Play | Plays or pauses the clip. A clip that loops starts again after its last frame. |
| Previous | Pauses and steps back one frame. |
| Next | Pauses and steps forward one frame. |
| Speed | Plays at 25 %, 50 % or 100 % of the frame durations. |
| Zoom | Enlarges the frame in whole-number steps, with sharp pixels. |
| Background | Sets what shows behind the frame: a checker, mid grey or a colour from your ramps. |
| Onion skin | Shows the previous frame in red and the next frame in blue behind the current one, at 30 % opacity. |
| Loop seam | Marks in magenta the pixels that change when the clip jumps from its last frame to its first, and counts them. A large count shows a pop in the loop. |
| Model ghost | Shows the clip's model frame behind the current frame at 40 %. It is off when the clip has no model. |
| Show mirrored | Plays the clip flipped, as it publishes for the facing it mirrors. It shows only for a clip that is published mirrored. |

## Tips and problems

- Roto reads the handoff files again when you return to its window. You do not need a refresh button.
- The flipbook reads the handoff file each time you open it. Open it again after you save in your editor.
- A clip with unpainted frames is not published. Its progress line shows how many frames are left.
- Re-rendering never touches your painting. If Roto cannot keep it safe, Roto refuses and leaves the file as it is.
- Publishing writes the frames and a manifest by the project's templates. Then it runs the project's after-publish command, if there is one.
- The editor is set on the [home screen](home.md). Without a choice, the system's default program opens the file.
