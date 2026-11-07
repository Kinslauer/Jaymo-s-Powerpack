# Hooded cloak — Ranger & Netherite Ranger

Owner: Agent 1 (modding — combat & powers)
Status: waiting for review
PR: #1104

## Goal
Extend the hooded cloak (#1095) to the Archers' ranger_armor_head and netherite_ranger_armor_head head armour: each hood's wearer gets the cloak (collar, back panel, mantles), the mask key lowers/raises the hood, and Kingdoms sees the wearer as hooded while up. Each hood has its own cloak texture coloured from the hood's own texture in the Archers jar.

## What I did
- `cloak_hoods.json`: exactly archers:archer_armor_head, archers:ranger_armor_head, archers:netherite_ranger_armor_head
- `HoodedCloakLayer.java`: cloak only when the armour head stack is in HoodedCloak.HOODS; texture picked by item id (archer -> hooded_cloak.png, ranger -> hooded_cloak_ranger.png, netherite ranger -> hooded_cloak_netherite_ranger.png); texture ids are constants at the top of the class
- `tools/gen_hooded_cloak.py`: per-hood palettes sampled from the Archers jar (archer #6d3e26 from the cloak spec; ranger bright green #257145; netherite teal #11545a), brightened so the shaded cloak still reads as its colour
- Regenerated hooded_cloak.png (archer, matches master's brown #442c1e), hooded_cloak_ranger.png (#316c43), hooded_cloak_netherite_ranger.png (#23625a)
- Version bumped to 0.21.48, MODLOG entry added
- PR checks passed, build successful

## What's left
- Waiting for Andy's "merge" on #1104

## Verified
- `sh ./gradlew build` — BUILD SUCCESSFUL
- `python3 tools/pr_checks.py` — passed
- Not played in game yet
