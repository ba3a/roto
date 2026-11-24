# Set up a clip

## Purpose

This screen turns a clip into clean figure masks and chooses its key frames. The picture is on the left. The key-frame filmstrip is below it. The settings are on the right. Roto saves every change as you make it.

## Steps

1. Choose the Mask view. The figure shows clear and the wall shows tinted.
2. Choose the clean plate. The plate is the picture of the empty wall that every frame is compared with.
3. Change the mask settings until the whole figure is clear and the wall is not.
4. Check the key frames on the filmstrip. Drag, add or remove markers until each pose you want is a key frame.
5. Go to a key frame. Use the brush to fix any part of the mask that is still wrong.
6. Press Done.

## Controls

| Control | What it does |
|---|---|
| Done | Saves the clip and goes back to the home screen. |
| Mask | Shows the frame with the wall tinted and the figure clear. |
| Frame | Shows the frame as filmed, with no mask. |
| Plate | Shows the clean plate. A moving figure or a ghost in the plate means the plate choice is wrong. |
| Add marker | Adds a key-frame marker on the frame under the playhead. It is off when a marker is already there. |
| Remove marker | Removes the selected marker. The last marker ends the last key frame, and at least one key frame must stay. |
| Loops | Says that the published animation repeats. The manifest records it. Photos never loop. |
| Match timing of | Replaces your markers with key frames timed like a model of the same action, laid over the walk cycle. It shows only when the profile has such a model. Move the markers after, as usual. |
| Back to proposal | Drops your markers and uses the key frames that Roto proposes from the walk cycle. |
| Median of the clip | Builds the plate from the middle value of every pixel over the whole clip. It works when the figure moves. |
| Empty wall | Builds the plate from a range of the video where the wall is empty. Choose the range with From and To. |
| Choose wall photo… | Picks a photo of the empty wall to use as the plate. |
| From | The start of the empty-wall range, in seconds of the video. |
| To | The end of the empty-wall range, in seconds of the video. |
| Keep lightness | Sets how much a change in lightness counts against a change in colour. A lower value ignores soft shadows on the wall. |
| Edge threshold | Sets the difference at which a pixel can join the figure when it touches a pixel that is surely figure. A lower value grows the figure. |
| Certain threshold | Sets the difference at which a pixel is surely figure. Keep it above the edge threshold. |
| Remove specks | Removes figure specks and spurs up to about twice this size, in pixels. Zero turns it off. |
| Fill gaps | Fills holes and gaps in the figure up to about twice this size, in pixels. Zero turns it off. |
| Back to defaults | Puts all five mask settings back to their starting values. |
| Add | Makes the brush paint figure into the mask. |
| Erase | Makes the brush paint figure out of the mask. |
| Size | Sets the brush radius in pixels. |
| Undo | Takes back the last stroke on this frame. |
| Clear frame | Removes every stroke on this frame. |

## Gestures

| Gesture | What it does |
|---|---|
| Drag on the picture, on a key frame in the Mask view | Paints a brush stroke. |
| Press near a marker, then drag | Selects the marker, shows its frame and moves it as far as the rules allow. |
| Press anywhere else on the filmstrip | Moves the playhead there. |
| Ctrl and mouse wheel on the filmstrip | Zooms the filmstrip. |
| Mouse wheel on a zoomed filmstrip | Scrolls it. |

## Tips and problems

- The brush works only on key frames. Strokes on frames that stop being key frames stay in the file and count again when a marker comes back.
- The spacing between markers becomes the time each key frame shows. Move the markers to change the timing.
- A photo has one frame, so it has no filmstrip and no markers.
- See [Draw a clip](draw.md) for the next step. For footage that gives clean masks, read the filming guide in the repository.
