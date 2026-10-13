# GreenhouseHelper

Greenhouse layouts for Hypixel SkyBlock, drawn on your plot like a schematic: first the **floor** (which cells need
watered ground), then the **crops** (what to plant where, at full size), with each plant's short code floating above
it. A shopping list panel shows what the layout needs.

Fabric, Minecraft 26.1 and 26.2.

## Getting started

1. Stand in your greenhouse and run `/greenhouse setup`.
2. Look at the **top-left** ground block of the 10×10 crop grid and click **[Set corner]** (or run
   `/greenhouse corner`), then the **bottom-right** one. The mod checks they're exactly 10×10 apart.
3. Load a layout:
   - from [skylayouts.io](https://skylayouts.io): `/greenhouse load https://skylayouts.io/layout.html?m=ALL_IN_ALOE&i=7`
   - or one you've saved: `/greenhouse use <name>`

Set up as many greenhouses as you like; each remembers its own layout, per SkyBlock profile.

## Commands

| Command | |
|---|---|
| `/greenhouse` (or `/gh`) | Settings |
| `/greenhouse setup` · `corner` · `cancel` | Set up the greenhouse you're in |
| `/greenhouse load <link>` | Load a layout from skylayouts.io |
| `/greenhouse save <name>` | Save this greenhouse's layout under a name (stays on your computer) |
| `/greenhouse use <name>` · `layouts` · `delete <name>` | Your saved layouts |
| `/greenhouse layer <floor\|crops\|both>` | Which layers to show |
| `/greenhouse rotate` | Turn the layout a quarter turn |
| `/greenhouse clear` · `remove` · `list` | Clear the layout, forget the greenhouse, list them |
| `/greenhouse gui` | Move the shopping list panel |
| `/greenhouse dump` | Write what's in each cell to a file (to teach the mod what your plants look like) |
| `/greenhouse help` | Every command |

## What it does and doesn't do

It only draws: it never plants, breaks or clicks anything, and never sends chat or commands. Layout links are loaded
from skylayouts.io's public data, and only the layout you asked for is kept. Saved layouts stay in
`config/greenhousehelper/layouts/`.

## Building

```
./gradlew build
```

Jars end up in `build/libs/`.

## Credits

- Layouts from [skylayouts.io](https://skylayouts.io) by nock0015.
