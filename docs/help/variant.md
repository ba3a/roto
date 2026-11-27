# New variant

## Purpose

This screen makes a variant: a profile with no clips of its own that publishes the finished art of another profile in other colours. Nothing is filmed and nothing is copied. A variant needs a base profile whose clips have a rendered handoff to preview.

## Steps

1. Choose the Base, the profile to recolour.
2. Choose a ramp in From and a ramp in To, then press Add swap. Repeat for every ramp to change.
3. Choose an Outline colour if the outline should change too.
4. Watch the preview. It plays a clip of the base with the swaps applied.
5. Type a name in Variant name and press Create variant.

## Controls

| Control | What it does |
|---|---|
| Cancel | Leaves the screen and makes nothing. |
| Base | Chooses the profile to recolour. Only profiles with clips of their own are listed, since a variant cannot be the base of another variant. |
| Variant name | The name of the new profile. It becomes a folder name and the `{profile}` value in output paths. |
| From | Chooses the ramp that the base's art uses. A ramp can be swapped once. |
| To | Chooses the ramp that takes its place. It may be the From ramp of another swap. |
| Add swap | Adds the swap of the From ramp for the To ramp to the list. |
| Remove swap | Removes the swap from the list. |
| Outline | Chooses the palette colour that the outline becomes. Keep leaves the outline as it is. |
| Create variant | Makes the variant profile and selects it. It is off until a base, a swap and a free name are chosen and every swap names ramps from `ramps.json`. |

## Tips and problems

- The line under the preview names what still blocks Create variant.
- The preview shows the first clip of the base that has a handoff. Render it on the [handoff screen](handoff.md) first.
- A ramp swap maps each step to the same step. Ramps of different length map to the nearest step.
- Publish the variant from the [handoff screen](handoff.md). It publishes every finished clip of the base and lists the unfinished ones.
