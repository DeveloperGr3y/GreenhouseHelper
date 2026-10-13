# GreenhouseHelper

Greenhouse layouts for Hypixel SkyBlock, drawn on your plot like a schematic: first the **floor** (the ground block
each cell needs: farmland, sand, soul sand…), then the **crops** (what to plant where, at full size), with each plant's
short code floating above it.

It also checks your greenhouse against the layout as you go:

- **Only what's left** is drawn: plants already in place disappear, a wrong plant shows red, wrong ground gets a red
  outline with the block it should be.
- **Hold a plant's seeds** and the cells it still needs light up.
- **Mutation spots**: how much of each ring is planted (a green post when it's complete), then the grown mutation's
  stage and a gold post when it's ready.
- The **shopping list** counts down what's still missing, with plants in place, rings complete, mutations grown and a
  decay warning for mutations that decay.

Fabric, Minecraft 26.1 and 26.2.

## Getting started

1. Stand in your greenhouse and run `/greenhouse setup`. It finds the 10×10 grid by itself (its north-west corner is
   the top-left). If it can't, or picks wrong, use `/greenhouse setup manual`: look at the **top-left** ground block
   and click **[Set corner]** (or run `/greenhouse corner`), then the **bottom-right** one.
2. Load a layout:
   - from [skylayouts.io](https://skylayouts.io): `/greenhouse load https://skylayouts.io/layout?m=ALL_IN_ALOE&i=7`
     (links to layouts edited on the site, including `/l/…` share links, work too)
   - or one you've saved: `/greenhouse use <name>`, or pick it in `/greenhouse layouts`

Set up as many greenhouses as you like; each remembers its own layout, per SkyBlock profile.

## Commands

| Command | |
|---|---|
| `/greenhouse` (or `/gh`) | Settings |
| `/greenhouse setup` · `setup manual` · `corner` · `cancel` | Set up the greenhouse you're in |
| `/greenhouse load <link>` | Load a layout from skylayouts.io |
| `/greenhouse layouts` | Your layouts (on your greenhouses, and saved): preview, use, save as, rename, rotate, share, delete |
| `/greenhouse save <name>` | Save this greenhouse's layout under a name (stays on your computer) |
| `/greenhouse capture <name>` | Save what's planted in this greenhouse as a layout |
| `/greenhouse use <name>` · `delete <name>` | Your saved layouts |
| `/greenhouse share [name]` | Copy a skylayouts.io link to a layout, to share it |
| `/greenhouse layer <floor\|crops\|both>` | Which layers to show |
| `/greenhouse rotate` | Turn the layout a quarter turn |
| `/greenhouse clear` · `remove` · `list` | Clear the layout, forget the greenhouse, list them (with progress) |
| `/greenhouse gui` | Move the shopping list panel |
| `/greenhouse dump` | Write what's in each cell to a file (to teach the mod what your plants look like) |
| `/greenhouse help` | Every command |

## What it does and doesn't do

It only draws: it never plants, breaks or clicks anything, and never sends chat or commands. It works out what's
planted from what your game already shows (the plants' heads and name tags). Layout links are loaded from
skylayouts.io's public data, and only the layout you asked for is kept; the layouts list only shows layouts you've
loaded or saved. Saved layouts stay in `config/greenhousehelper/layouts/`.

Growth stages and decay times come from skylayouts.io's plant data; "ready" and "decays in" are the mod's best guess
from those, counted from when it first saw the mutation grown.

## Building

```
./gradlew build
```

Jars end up in `build/libs/`.

## Credits

- Layouts from [skylayouts.io](https://skylayouts.io) by nock0015.
