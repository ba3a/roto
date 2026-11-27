---
layout:
  wordmark: true
---

<div class="section-marker" id="hero"></div>

# Roto

Film yourself doing an action. Get a pixel-art animation draft in minutes. Then paint over it in Aseprite or LibreSprite.

Roto does the tedious tracing. You keep the art. It uses no AI: every step follows fixed rules, so the same footage always gives the same draft. Roto never changes what you have painted.

<!-- SCREENSHOT hero-strip: phone footage frame, Roto draft, finished sprite, side by side -->
<!-- SCREENSHOT hero-loop: the finished walk cycle looping next to the filmed clip (GIF) -->

<div class="section-marker" id="problem"></div>

## Animating takes days. Reference takes minutes.

A good walk cycle in pixel art means many frames, each drawn by hand. Most artists film themselves or a friend and redraw over the video. That works, but cutting out the figure, matching your colours and keeping every frame the same size is slow, dull work.

Roto does that part. You do the part that needs an artist: the drawing.

<div class="section-marker" id="audience"></div>

## Who it is for

- Pixel artists who animate characters for games.
- Artists who do not write code. You never edit a settings file or type a command.
- Small teams that want every character, action and direction finished the same way.

Roto works with any game engine. It saves your frames as plain PNG files, with a small data file, in the folder you choose. Your engine loads them like any other art.

<div class="section-marker" id="how-it-works"></div>

## How it works

1. **Set up once.** Pick a starting setup, such as side view or top-down. Tell Roto your sprite size and your colours. A short wizard asks the questions. There are no files to write.
2. **Film or photograph.** Import a short phone clip of someone doing the action. Trim it to the part you need. A single photo also works for a still pose.
3. **Roto cuts out the figure.** It picks the frames that matter, then colours the figure with your own palette. You can fix anything it got wrong with a brush.
4. **Paint.** Roto opens the draft in Aseprite or LibreSprite. The footage and the draft sit on their own layers. You paint on a layer above them.
5. **Publish.** One click copies your finished frames into your game's folder, named the way your engine expects.

<!-- SCREENSHOT clip-setup: mask preview and key-frame markers on the filmstrip (step 3) -->
<!-- SCREENSHOT draw-screen: live draft with colour zones (step 3) -->
<!-- SCREENSHOT handoff-in-editor: Aseprite/LibreSprite layer panel with reference, draft and art layers (step 4) -->
<!-- SCREENSHOT published-folder: file explorer showing frames inside a game project (step 5) -->

<div class="section-marker" id="features"></div>

## What you get

**Less setup**
- A wizard sets up your project. You never write a configuration file.
- Ready-made starting setups: blank, side view, top-down with 4 directions, top-down with 8 directions.
- Save your setup as one file and send it to a teammate. The file cannot run any command on their computer.

**Better drafts**
- Your own colours. The draft uses only your palette, and shades each part of the body with the colour steps you chose.
- Match an existing animation. Import one you already made (`.ase`, a folder of PNGs or a libGDX atlas). Roto uses it as a pose guide, and suggests its timing and body height.
- A setting that keeps the draft from flickering between frames.

**Safe painting**
- Your painting is yours. If you ask Roto to redo the draft, it replaces the footage and draft layers only. It never touches your art layer.
- Roto never changes your original videos.

**Checking your work**
- A player with onion skin (the frames before and after, shown faintly) and a loop check that shows what differs between the last and first frame.
- A check for stray pixels, colours outside your palette and flicker. It only gives advice. It never blocks you and never edits your art.
- A progress grid of every action and direction: what is not filmed, what is half painted, what is done and what to do next.

**After painting**
- Change the speed of a finished clip, or the number of frames, without repainting.
- Mirror a finished direction to make its opposite, such as left from right.
- Make a recolour, such as a second outfit or a team colour, from finished art. No new filming.

**Always the same result**
- Same footage and same settings give the same frames, byte for byte. Nothing is random and nothing is generated.

<!-- SCREENSHOT overview-grid: coverage grid with the Next suggestion -->
<!-- SCREENSHOT flipbook: player with onion skin and the check panel under it -->
<!-- SCREENSHOT wizard-preset: one wizard step showing the starting setups -->
<!-- SCREENSHOT variant: one character in two colour schemes -->

<div class="section-marker" id="faq"></div>

## Questions

**Is this AI?**
No. Roto uses fixed, predictable methods. It does not use machine learning or generated images. The art in your game is drawn by you.

**Will Roto repaint my work?**
No. Your painting lives on its own layer. Roto never changes it.

**Do I need to code?**
No. The app has buttons and a built-in help (press F1). A command-line tool exists for people who want to automate things. You can ignore it.

**Which game engine does it work with?**
Any. Roto saves PNG frames and a small data file into a folder you choose. If your project needs an extra step after saving, a developer can add one command that runs after each publish.

**Which painting program do I need?**
Aseprite or LibreSprite. Roto saves its drafts as `.ase` files, which both open.

**Is my footage uploaded anywhere?**
No. Roto runs on your computer.

**What does it cost?**
Roto is free and open source under the Apache License 2.0.

**Is it finished?**
Not yet. Roto is in early development. Expect rough edges, and keep a copy of your art.

<div class="section-marker" id="quick-start"></div>

## Get started

1. Download the installer for your system from the [releases page](https://github.com/ba3a/roto/releases): Windows (`.msi`), macOS (`.dmg`) or Linux (`.deb`). Releases marked pre-release are early versions. The installers are not signed yet, so your system may show a warning. Choose to run it anyway.
2. Install [ffmpeg](https://ffmpeg.org/), a free video tool that Roto uses to read your clips. Make sure it can be found by the system (on its `PATH`).
3. Open Roto and choose **Start from a preset**.

Press F1 in any screen to open the help for that screen.

### For developers

Run the app from a checkout. You need JDK 21 and ffmpeg.

```bash
./gradlew :roto-app:run
```

Create a project from the command line, and export its setup for others:

```bash
roto init my-game --preset side-view --id my-game
roto export my-setup.rotopreset --project my-game
```

See what is left to do, and check your painted art before publishing:

```bash
roto status worker --project my-game
roto check worker --project my-game
```
