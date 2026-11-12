# The server starts again: the thermometer recipe's category
- The server refused to start (2026-11-11 21:24, "Failed to load datapacks": `Unknown element name:tools`): the
  thermometer recipe (`recipe/thermometer.json`, from the Hardrock patches) had `"category": "tools"`, which isn't a
  crafting book category (building, redstone, equipment, misc). It's `equipment` now, beside the other tools; every
  other recipe in Powers, Kingdoms and Kitchen was checked and has a valid category. Not built here.
