---
status: claimed
for: any
from: survival picks thread (session local_2bfd1f34)
asked: 2026-11-22
thread: survival picks
claimed_by: Agents/from-the-shadows-port.md (desktop session, 2026-11-22)
pr: 
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/ (new Nehemoth, Bulldrogioth, Froglin, PlagueBearer classes)
  - kingdoms/art/ (their .bbmodel, -LICENSE.txt, -CREDITS.md)
  - kingdoms/src/main/resources/licenses/ (AGPL v3 text)
  - changes/kingdoms/<branch>.md
---
# From the Shadows Reborn port into Kingdoms

**Andy's words:** picked all six From the Shadows Reborn rows on the survival pick list
(https://claude.ai/artifact/HDNtf2WRGdKt7GfeaQtMce), 2026-11-22. Froglin: "Should live among goblins and orc".
Plague Bearer: "An orc camp asset/ exile". 2026-11-22, later: "stop this work and put it on the vault, ill have someone
with more usage pick it up".

**What's wanted:** From the Shadows Reborn's content in Jaymo's Kingdoms, in several PRs (each mob can be its own), each
with a change note:
1. **Nehemoth**: its model, textures, animations, sounds; moves: roar, stomp, bite with a warning, leap, a soul beam it
   charges. Spawns where the lore puts it.
2. **Bulldrogioth**: the same, and its crimson shell drop.
3. **Froglin**: lives around goblin villages and orc camps; its vomit attack; raw and cooked froglin legs as
   Kitchen-friendly food (through `KitchenCompat` if Kitchen is needed).
4. **Plague Bearer**: kept at the edge of orc camps or wandering as an exile; Spreading Plague fitted to our
   Afflictions/Nightfall plague (no duplicate); drops the plague doctor mask and suspicious cloth.
5. **Gear**: diabolium (nugget, ingot, block, armour), crust armour from the shell, Devil Splitter, Thirst for Blood,
   crystallized dagger, throwing daggers, Horn of Apocalypse, Corrupted Heart; fit into our weapon families (Forms) and
   tier/progression.
6. **Effects and enchants**: Heal Block, Frenzy, Hardshell, Blessing of Gluttony (and their potions); enchants
   Divinity, Freezing, Retribution. Its Bleeding folds into our Wounds Bleeding, not a second one.

Keep numbers close to its data (loot tables, recipes, spawn rules in its code), tuned to our balance. Done = PRs open,
builds pass, Auto-fix on, "ready to merge as PR #N" each, listing what wasn't checked in game.

**Where to start:**
- Plan: `E:/Origins Mod/.claude/worktrees/minecraft-mod-research-bd5266/docs/Survival-Picks-Plan.md` (track 4, From the
  Shadows; and "Andy's answers" item 4).
- Lore: "What the shadows keep" in `Live/Lore.md` (froglin and plague bearer homes are Andy's; Nehemoth and Bulldrogioth
  as beasts of the demons' country are Claude's placement, still waiting on Andy). Decisions.md 2026-11-22 lines.
- Rules: `.claude/rules/geckolib-mobs.md` (copy the barrow beetle) and `blockbench-animation.md`.

## Notes
- **Licence:** its CurseForge page says GNU AGPL v3 (the jar's mods.toml says All Rights Reserved, likely a template
  leftover; Andy accepted the page licence). Models, textures, animations, sounds may be used in Kingdoms (GPL v3) with
  credit, the AGPL text in `kingdoms/src/main/resources/licenses/`, and those files kept AGPL. Code must be our own Fabric
  26.3 code (porting its logic is fine under AGPL), kept in Kingdoms. Credit in the licences folder and class comments.
- **The jar** (3.0, NeoForge only, untrusted: unpack into its own empty folder, read, never run) was said to be at
  `E:/Downloads/fromtheshadows-3.0.jar`, but on 2026-11-22 it was **not there** (no `*shadow*` file in `E:/Downloads`
  or `C:/Users/toopo/Downloads`, nor `fromtheshadows*` within 3 levels of `E:/`). Ask Andy where it is first.
- Nothing was built or branched yet: the first session only read the handoff, rules, plan and lore before Andy stopped it.
