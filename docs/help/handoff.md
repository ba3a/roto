# Handoff and publish

## Purpose

This screen is the way out of Roto and back. Roto renders one layered `.ase` file for each clip. You paint on its `art` layer in your editor. Roto then publishes the finished frames into your project.

## Steps

1. Press Render all, or Render on one clip. Roto writes the handoff file.
2. Press Open in editor on a clip. Paint on the `art` layer. Save the file.
3. Come back to Roto. The progress line shows how many frames you painted.
4. Optional: tick Publish mirrored on a clip whose facing has a mirror pair.
5. Press Publish when every clip you want is ready.
6. Press Done.

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

## Tips and problems

- Roto reads the handoff files again when you return to its window. You do not need a refresh button.
- A clip with unpainted frames is not published. Its progress line shows how many frames are left.
- Re-rendering never touches your painting. If Roto cannot keep it safe, Roto refuses and leaves the file as it is.
- Publishing writes the frames and a manifest by the project's templates. Then it runs the project's after-publish command, if there is one.
- The editor is set on the [home screen](home.md). Without a choice, the system's default program opens the file.
