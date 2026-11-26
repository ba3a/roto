# Home

## Purpose

The home screen shows the places Roto works with: the open project, the library folder and your editor. Below them it lists the project's profiles and the clips of the selected profile.

## Steps

1. Press New project… to set up a project, Open folder… to open one that has a `roto.json`, or Recent… to go back to one you used before.
2. Press Choose library… to keep profiles somewhere else. Roto uses a `Roto` folder in your home folder until you do.
3. Type a name in New profile and press Create.
4. Select the profile. Press Import video… or Import photo… to add a clip.
5. Use Trim…, Set up… and Draw… on a clip row, in that order.
6. Press Import model… to add a finished animation as reference. The Models list shows what the profile holds.
7. Press Handoff and publish… when the clips are ready.

## Controls

| Control | What it does |
|---|---|
| New project… | Opens the project wizard. It makes the project files from a preset. |
| Open folder… | Opens the project in a folder that holds `roto.json`. |
| Recent… | Lists the projects you opened before. Choosing one opens it again with the library it was last used with. It is off until you have opened a second project. |
| Import preset… | Adds a `.rotopreset` file to your presets and opens the wizard on it. A bad file gives a message and changes nothing. |
| Export as preset… | Saves the open project's setup as a `.rotopreset` file. It never includes the after-publish command. |
| Choose library… | Chooses the folder where Roto keeps profiles. Without a choice, Roto uses a `Roto` folder in your home folder. Keep it outside your repositories. |
| Choose editor… | Chooses the program that opens handoff files. Without a choice, Roto uses the system's default for `.ase` files. |
| Dismiss | Closes the message bar above the profile list. |
| New profile | The name of a profile to create. A profile holds the clips for one character or object. |
| Create | Makes the profile with the name in the New profile field and selects it. |
| Import video… | Picks a video and opens the trim screen. Roto copies the video into the profile and never changes the original. |
| Import photo… | Picks a photo and opens the photo screen. A photo becomes a one-frame clip. |
| Import model… | Opens the model import screen. A model is a finished animation that Roto keeps as read-only reference. See [Import models](model-import.md). |
| Remove model… | Asks to remove a model from the profile. Nothing is removed until you confirm. |
| Remove | Removes the model from the profile. This cannot be undone. |
| Keep | Closes the question and keeps the model. |
| Handoff and publish… | Opens the handoff screen for the profile. It is off while the profile has no clips. |
| Trim… | Changes the part of the video that the clip uses. It is not shown for photos. |
| Set up… | Opens clip setup: the clean plate, the mask, the key frames and the brush. |
| Draw… | Opens the draw screen: the scale, the zones and the draft. |

## Tips and problems

- A profile that shows "Cannot be read" has a damaged `profile.json`. Select it to see the reason.
- The Profiles row shows the exact folder Roto reads: the library, then the project id. If it says "not found", the library is probably the wrong one. Press Choose library… and pick the folder that holds the project's folder, not the project's folder itself.
- Every file chooser opens where you last chose something of its kind.
- The project folder and the library are separate. The project holds the settings and the published frames. The library holds the footage and the working files.
- See [New project](wizard.md) for the wizard and [Handoff and publish](handoff.md) for the last step.
