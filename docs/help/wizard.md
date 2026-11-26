# New project

## Purpose

The wizard sets up a project from a preset. It has seven steps: Preset, Name and folder, Animation, Canvas, Colours, Output and Review. It writes the palette and the ramps first and `roto.json` last. It never overwrites a project file.

## Steps

1. Preset: choose the preset to start from.
2. Name and folder: choose the project folder and type the project id.
3. Animation: edit the facings, the mirror pairs and the actions.
4. Canvas: set the width, the height and the ground row.
5. Colours: import a palette, build the ramps and choose the outline colour.
6. Output: choose where published frames go and how they are laid out.
7. Review: check the summary and press Create project.

## Controls

| Control | What it does |
|---|---|
| Cancel | Leaves the wizard and creates nothing. |
| Back | Goes to the step before. It is off on the first step. |
| Next | Goes to the next step. It is off while the step has a problem. |
| Preset | Chooses the setup to start from. The summary shows its canvas, facings, actions and palette size. |
| Choose folder… (project) | Chooses the folder of the new project. It is the folder that will hold `roto.json`. |
| Project id | The project's name. Roto uses it for the project's folder in the library. It starts with a letter or digit and holds only letters, digits, hyphens and underscores. |
| Facing | Selects a facing. Select two facings to pair them. Select one to rename or remove it. |
| Unpair | Ends a mirror pair. Both facings stay. |
| Name for add or rename | The name that Add and Rename use, for a facing or an action. |
| Add facing | Adds a facing with the name you typed. |
| Rename facing | Renames the selected facing to the name you typed. |
| Remove facing | Removes the selected facings. |
| Pair facings | Makes the first selected facing mirror the second. Clips of one can be published as the other. |
| Action | Selects an action. Select one to rename or remove it. |
| Add action | Adds an action with the name you typed. |
| Rename action | Renames the selected action to the name you typed. |
| Remove action | Removes the selected actions. |
| Width | The canvas width in pixels. |
| Height | The canvas height in pixels. |
| Ground row | The row of the canvas where the feet stand. Rows count from the top, starting at 0. |
| Import palette… | Reads a `.gpl` or `.hex` palette file. The palette has 1 to 255 colours. |
| Colour swatch | Picks or drops a palette colour for the ramp you are building. |
| Ramp name | The name of the ramp you are building. |
| Add ramp | Makes a ramp from the picked colours. Roto sorts them from dark to light. Two colours of equal lightness are refused. |
| Update ramp | Saves your changes to the ramp you are editing. |
| Suggest ramps | Groups the palette into ramps by hue, each from dark to light. |
| Clear | Drops the picked colours and the name. |
| Edit | Loads a ramp into the builder to change it. |
| Remove | Removes the ramp. |
| Outline | Chooses the colour of the outline. The default is the darkest colour of each ramp. |
| Choose folder… (output) | Chooses the folder for published frames, inside the project. |
| Folder inside the project | The folder for published frames, as a path inside the project. |
| Layout | Chooses one folder for each clip, or one folder for each profile. |
| Advanced | Shows or hides the template fields. |
| Output template | The path of each published frame. It may use the `{variable}` placeholders. |
| Manifest template | The path of each clip's manifest. It may use the `{variable}` placeholders. |
| After publish command | A command that Roto runs in the project folder after every publish. Write one argument for each word. |
| Create project | Writes the project files. It shows the list of files first. |

## Tips and problems

- A preset can come from a `.rotopreset` file. Choose File > Import preset… on the [home screen](home.md).
- The after-publish command is never part of a preset, so a shared file cannot make Roto run a command.
- When the folder is already a Roto project, the Name step says so. Choose another folder.
- Roto does not know your game. Every name, size and path here is yours.
