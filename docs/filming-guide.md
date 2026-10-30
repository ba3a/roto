# Filming guide

Roto separates the figure from the wall behind it by comparing every frame against a picture of the
empty wall (the *clean plate*). Almost every problem in the later steps traces back to footage where
that comparison was hard, so the time spent on a steady, evenly lit clip is repaid in the mask.

## Setup

- **Tripod.** The camera must not move during a clip. Roto does not stabilise the background; a
  shifting wall will show up in the mask as a ghost of the figure's surroundings.
- **Locked exposure and focus.** Turn off auto-exposure, auto-focus and any automatic HDR or night
  mode. A brightness change halfway through a clip moves the whole wall away from its plate.
- **Plain wall.** Choose a wall with one even colour and no strong texture, and keep the figure's
  clothes clearly different from it. Roto compares colours in a perceptual space (OKLab), so a hue
  difference helps as much as a lightness difference.
- **Even light.** Soft, diffuse light gives the cleanest mask. Soft shadows on the wall drop out of
  the mask, but a sharp, dark cast shadow can still be read as part of the figure.

## Framing

- **Stand far back and use the 2x lens.** A long lens keeps perspective flat, so the body keeps the
  same proportions from head to feet.
- **Tilt the phone a few degrees down.** Together with filming at shoulder height, this gives the
  slight three-quarter view the draft is built for.
- **Keep the whole figure in frame**, head to feet, in every frame you plan to use. A cropped foot
  or hand is cropped in the draft too.
- **Film in landscape or portrait as you like.** Roto reads the rotation recorded in the file.

## What to record

1. **Two to three seconds of the empty wall first**, before stepping into frame. Mark this range as
   the clean plate in clip setup. It is the best plate there is.
2. **One clip per facing.** Roto has no built-in notion of facings or actions; the list you choose
   from comes from your project's `roto.json`. Film each facing as its own clip, even when two of
   them are mirror images: mirroring is an explicit per-clip option, off by default.
3. **Walk cycles: walk across the frame**, from one side to the other, at a steady pace. Roto tracks
   the body, holds it still horizontally and finds the repeating step on its own. Film at least
   two full steps so the cycle is visible more than once.
4. **Other actions** (idle, working, carrying) can be filmed in place. Without a walk across the
   frame there is no empty wall in the clip's own footage, so keep the empty-wall range from step 1
   for these.

## Stills

A single pose can be a photo instead of a video:

- one photo of the pose, and
- one photo of the empty wall, taken from the same spot with the same settings.

Both are imported together and run through the same steps as a one-frame clip. Take them without
moving the tripod between shots.

## Quick checklist

- [ ] Tripod, nothing touched during the take
- [ ] Exposure, focus and white balance locked
- [ ] Plain wall, even light, clothes unlike the wall
- [ ] Whole figure in frame, 2x lens, phone tilted slightly down
- [ ] Two to three seconds of empty wall at the start
- [ ] One clip per facing
- [ ] Walks cross the frame at a steady pace
