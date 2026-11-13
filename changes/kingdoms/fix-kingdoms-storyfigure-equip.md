# fix the Life Story screen crashing the game when it draws your figure
- The game crashed on the Life Story screen ("Cannot get config value before config is loaded", from Horseman): the figure is dressed in the story's gear for the drawing, and each change fires the equip hook other mods listen to. A failure in another mod's equip hook no longer takes the screen down; the figure is still drawn in its gear.
- Built in CI; screen not opened here.
