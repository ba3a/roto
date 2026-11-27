# Home

## Purpose

The home screen lists the open project's profiles and shows the clips and models of the selected profile. The File menu makes, opens and shares projects. File > Settings… shows the project, the library folder, the profiles folder and your editor.

## Steps

1. Choose File > New project… to set up a project, File > Open folder… to open one that has a `roto.json`, or File > Open recent to go back to one you used before.
2. Choose File > Settings… and press Choose library… to keep profiles somewhere else. Roto uses a `Roto` folder in your home folder until you do.
3. Type a name in New profile and press Create.
4. Select the profile. Press Import video… or Import photo… to add a clip.
5. Use Trim…, Set up… and Draw… on a clip row, in that order.
6. Read the Overview above the clips. It shows every action and facing and names what to do next. See [Overview](overview.md).
7. Press Import model… to add a finished animation as reference. Each model shows as a card with its first frame. Rest the pointer on it to play it.
8. Press Handoff and publish… when the clips are ready.

## Controls

| Control | What it does |
|---|---|
| New project… | Opens the project wizard. It makes the project files from a preset. It is in the File menu, and Ctrl+N does the same. |
| Open folder… | Opens the project in a folder that holds `roto.json`. It is in the File menu, and Ctrl+O does the same. |
| Open recent | Lists the projects you opened before. Choosing one opens it again with the library it was last used with. It is in the File menu, and it is off until you have opened a second project. |
| Import preset… | Adds a `.rotopreset` file to your presets and opens the wizard on it. A bad file gives a message and changes nothing. It is in the File menu. |
| Export as preset… | Saves the open project's setup as a `.rotopreset` file. It never includes the after-publish command. It is in the File menu. |
| Settings… | Opens the settings window: the open project, the library, the profiles folder and the editor. It is in the File menu, and Ctrl+, does the same. |
| Exit | Closes Roto. It is in the File menu. |
| Choose library… | Chooses the folder where Roto keeps profiles. Without a choice, Roto uses a `Roto` folder in your home folder. Keep it outside your repositories. It is in the settings window. |
| Choose editor… | Chooses the program that opens handoff files. Without a choice, Roto uses the system's default for `.ase` files. It is in the settings window. |
| Close | Closes the settings window. Every choice is already saved. |
| Dismiss | Closes the message bar above the profile list. |
| New profile | The name of a profile to create. A profile holds the clips for one character or object. |
| Create | Makes the profile with the name in the New profile field and selects it. |
| Import video… | Picks a video and opens the trim screen. Roto copies the video into the profile and never changes the original. |
| Import photo… | Picks a photo and opens the photo screen. A photo becomes a one-frame clip. |
| Import model… | Opens the model import screen. A model is a finished animation that Roto keeps as read-only reference. See [Import models](model-import.md). |
| Model preview | Shows the model's first frame, and plays the model while the pointer rests on it. A looping model plays round and round; any other plays once and holds its last frame. |
| Remove model… | Asks to remove a model from the profile. Nothing is removed until you confirm. |
| Remove | Removes the model from the profile. This cannot be undone. |
| Keep | Closes the question and keeps the model. |
| Handoff and publish… | Opens the handoff screen for the profile. It is off while the profile has no clips. |
| Trim… | Changes the part of the video that the clip uses. It is not shown for photos. |
| Set up… | Opens clip setup: the clean plate, the mask, the key frames and the brush. |
| Draw… | Opens the draw screen: the scale, the zones and the draft. |

## Tips and problems

- The File menu works only on the home screen. Finish or cancel a clip screen first.
- A profile that shows "Cannot be read" has a damaged `profile.json`. Select it to see the reason. A model card that shows "Cannot be read" has a missing or damaged frame or `model.json`.
- The Profiles row of the settings window shows the exact folder Roto reads: the library, then the project id. If it says "not found", the library is probably the wrong one. Press Choose library… and pick the folder that holds the project's folder, not the project's folder itself.
- The window title shows the open project's id.
- Every file chooser opens where you last chose something of its kind.
- The project folder and the library are separate. The project holds the settings and the published frames. The library holds the footage and the working files.
- See [New project](wizard.md) for the wizard and [Handoff and publish](handoff.md) for the last step.
