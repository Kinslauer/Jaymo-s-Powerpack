# folk notice dirt: remarks and slower hearts
- **People notice dirt** (Andy: "MCA should take note of their cleanliness and so will others"): dirt a player wears where it can be seen (Powers' `ArmourGrime.noticed`: their armour or their own clothes) is remarked on like blood, after blood and before smell, by every villager and other folk in sight, one at a time, each once in 8 minutes: dust gets a word, mud a frown, a filthy, caked player a recoil. Goblins and orcs approve, with their own lines (`lang/parts/dirt_remarks.json`).
- **MCA folk warm slower to the muddy**: a chat, joke, story or flirt with a muddy player loses a quarter of the hearts it won, a filthy one half (at least one); dust costs nothing, and goblins and orcs don't mind (`Stench.hearts`, from `McaDialogueMixin`). Being clean is never punished.
- Classes: `Stench`, `PowersCompat.grimeSeen`, `McaDialogueMixin`. Needs Powers with `ArmourGrime.noticed` (does nothing without it).
- Built; not played.
