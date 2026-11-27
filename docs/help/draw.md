# Draw a clip

## Purpose

This screen sets how big the figure is on the canvas, shades its parts with colour ramps, and shows the resulting draft. The frame with its marks is on the left. The draft of the key frame is in the middle. The settings are on the right. Roto saves every change as you make it.

## Steps

1. Go to a frame where the figure stands upright.
2. Press Mark head, then press the top of the head on the picture. Press Mark feet, then press the soles of the feet.
3. Set Body height if the figure is too big or too small for the canvas.
4. Type a name in New zone, choose a ramp, press Add, then press the figure. This makes a zone, such as skin or cloth.
5. Press more places on the figure with Sample to teach the zone. Make a zone for each part that has its own colours.
6. Go to a key frame. Use Paint to fix any pixels in the wrong zone.
7. Check the draft. Press Done.

## Controls

| Control | What it does |
|---|---|
| Done | Saves the clip and goes back to the home screen. |
| Mark head | Puts the head mark tool in your hand. Press the top of the head on a standing frame. |
| Mark feet | Puts the feet mark tool in your hand. Press the soles of the feet on the same frame. |
| Body height | Sets the figure's height on the canvas, in canvas pixels. The marks set the first value. |
| Match size of | Sets Body height to the height of a model of the same action, and draws a green ground line on the draft where the model's feet stand. It shows when the figure is marked and the profile has such a model. Squash stays as it is. |
| Squash | Shortens the figure vertically, for a three-quarter view. A value of 1 leaves it as it is. The lowest value is 0.5. |
| Walks toward or away from the camera | Scales each frame to the size of the figure and follows its feet. Use it for a clip that faces north or south. |
| Pose guide | Chooses a model of the same action to show under the reference in the handoff, so you can flip between its poses and yours. It also lists the ramps and steps the model uses, and the Ramp lists mark those ramps with a dot. No zone changes. None removes the guide. |
| Sample | Puts the sample tool in your hand for this zone. Each press on the figure adds a sample point. |
| Paint | Puts the zone brush in your hand for this zone. Drag on a key frame to paint figure pixels into the zone. |
| Ramp | Chooses the colour ramp that shades the zone. The project's ramps are the choices. A dot marks the ramps the Pose guide model uses. |
| Undo sample | Takes back the last sample of the zone. The zone goes with its last sample. |
| Remove | Removes the zone and the strokes painted into it. |
| New zone | The name of a zone to make. |
| Add, then click the figure | Starts the zone with the name and ramp above. The next press on the figure is its first sample. |
| Brush size | Sets the zone brush radius in pixels. It shows while a Paint tool is in your hand. |
| Undo stroke | Takes back the last zone stroke on this frame. |
| Dither ramp edges | Adds a fine checker pattern where two steps of a ramp meet. It is off by default. |
| Hold | Keeps a key frame's draft like the one before it unless the picture clearly changes. Higher values flicker less. 0 is off. |

## Gestures

| Gesture | What it does |
|---|---|
| Press the picture with a mark tool | Sets the head or the feet mark. |
| Press the figure with a sample tool | Adds a sample point. A press on the wall does nothing. |
| Drag on the picture with a zone brush, on a key frame | Paints figure pixels into the zone. |
| Press anywhere on the filmstrip | Moves the playhead there. It snaps to a key frame when it is near one. |
| Ctrl and mouse wheel on the filmstrip | Zooms the filmstrip. |
| Mouse wheel on a zoomed filmstrip | Scrolls it. |

## Tips and problems

- The draft needs the two marks and at least one zone. Until then the middle pane says what is missing.
- The scale belongs to the profile, not the clip. Mark it once. Every other clip of the profile uses it.
- The cyan box on the picture is the canvas on this frame. Marks are amber lines.
- Make zones only for the parts that need different ramps. Fewer zones give a cleaner draft.
- See [Set up a clip](clip-setup.md) for the mask and [Handoff and publish](handoff.md) for the next step.
