---
status: in-review
for: pc-claude
from: claude-cloud (Fire and Wither picks thread)
asked: 2026-11-18
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BRMaCrFDy9EULEZbqakT9nF
claimed_by: 
pr: 1716
suggested_owns:
  - build.gradle (packMods lines)
  - default config for Inferno
---
# Inferno: ship it, plus a torch rule

**Andy's words (2026-11-17 8:24 PM ET):** "done, send it to the vault for apollo" (after ticking the pick list).
Then (8:30 PM ET): "not for apollo anymore, ill give it straight to reviewer".

**Andy's picks:** Inferno as it is (in_ship, his note: "can we just ship the heavy fire smog effect?"), Charcoal trunks (in_charred, note: "they should"), Smoke that grows with the fire, Burning things spread fire, Strike with a torch, Soul fire bites deeper, Metals burn their own colour, Fire lower on your screen. Not picked: burnt ground that heals, fire flowers, the ore oak.

**Plan (checked in Inferno's source, github.com/MoriyaShiine/inferno, `InfernoConfig`):** every picked row is one of Inferno's own config switches, so ship the jar unchanged and set its default config: `charredLogs=true, copperFire=true, onSoulFire=true, torchIgnition=true, entityFireSpreadChance=0.5, lowerHudFire=true, smokyFog=true`, and **`scorchedEarth=false`** (the row Andy didn't pick; it also stops the Shining Oak and new plants, which only grow from scorched earth). Answer to his note: yes, the smog is its `smokyFog` switch and it ships with the jar.

**Ship:** Inferno Fabric 26.3 (`inferno-fabric-26.3-r1.jar`, CurseForge project 1230123, Modrinth ORIQOtZ5) + StrawberryLib 26.3-r1 (same author, ARR, bundles Cardinal Components; MidnightLib is embedded in Inferno). Both ARR: shipped unchanged only, credited on the pack page. Ship the config through the pack's default-config route.

**Test with our fire code:** Inferno's charred-log and scorched-earth mixins hook `FireBlock.checkBurnOut` (ModifyExpressionValue + WrapOperation on `removeBlock`); our `mixin/tundraworld/BurntLogMixin` injects HEAD/RETURN on the same method for taiga Regrowth, and Kingdoms `disasters/Calamities` runs wildfires. Check a burning taiga still scorches (Regrowth), charred logs appear, and nothing double-handles. Also check Vampires' torch burning doesn't double with torchIgnition.

**What a player sees:** thick smog near big fires, burning mobs spreading fire, torches as weapons, blue soul fire doing double damage, green copper fire, charred trunks that break into charcoal, the fire overlay lower on screen.

**Source:** Andy's picks from the Fire and Wither pick list (https://claude.ai/artifact/5xsQSkJSnP8WszLL6JpNtL, db `picks/andy`, saved 2026-11-18 01:23 UTC; file `/mnt/project-files/fire-wither-study/fire-wither-picklist.html`). Study thread: "Cross-reference Knight Armor, Tide, Wither Reincarnated, Inferno". Memory: `fire-wither-fit.md`.

**Licences:** Wither: Reincarnated, Knight Armor and Weapons, Asterion and Inferno are All Rights Reserved / proprietary: ideas only, our own code, names and vanilla-style art. Tide is MPL-2.0 but nothing of it is needed. Only Inferno ships as its own jar (see the Inferno task).

**Rules:** CLAUDE.md as always: one PR per task, change note per mod in `changes/`, no version bumps, check 26.3 APIs against genSources, tuning constants at the top of each class, typed MagicFx/Fx only, iron gear forged via Tinkers, no screen text stating lore. Lore paragraphs for this work are in Lore.md and approved by Andy (The Wither's coming, What fire leaves, Fishing the fire, The labyrinth-builders' craft).

## Notes
- 2026-11-17 8:28 PM ET: Andy approved all four lore paragraphs; they are canon now.
- 2026-11-17 21:02 pc-claude's claim had no sign of work for 30 min: handed back as open.
