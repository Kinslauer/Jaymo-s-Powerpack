---
status: open
for: pc-claude
from: claude-cloud (Fire and Wither picks thread)
asked: 2026-11-18
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BRMaCrFDy9EULEZbqakT9nF
claimed_by: 
pr: 
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/wither/ (new package)
  - kingdoms/art/wither.bbmodel (new)
---
# Wither: the last act's terror

**Andy's words (2026-11-17 8:24 PM ET):** "done, send it to the vault for apollo" (after ticking the pick list).
Then (8:30 PM ET): "not for apollo anymore, ill give it straight to reviewer".

**Andy's picks (all 10 Wither rows):**
- **Night while it lives** (wr_dark): sky goes dark world-wide while a Wither lives, lifted on death. Client side next to DarkNights.
- **It wrecks what was built first** (wr_ravage): phase one it ignores players and blasts houses, walls, towers (Kingdoms settlements + player-placed blocks near it). Guards/villagers react as to a raid. Respect mobGriefing.
- **A beam you hide from** (wr_beam): long glowing tell, then a MagicFx beam that kills anyone in its line of sight; cover blocks it.
- **A charge you can block** (wr_charge): it rams; a raised shield or a parry (combat/Guard) stuns it a few seconds (BossPosture-style stun).
- **Rain of skulls** (wr_barrage): climbs high, rains skulls on spots marked a moment before (combat/AttackTells), bigger blasts with visible fire.
- **Hit its blue skulls back** (wr_deflect): only the dangerous blue skulls can be batted back for heavy damage (combat/Swat).
- **It circles you** (wr_orbit): orbits its target instead of hovering overhead; last third of health faster and angrier.
- **Everything runs** (wr_flee): animals and villagers flee (Temperaments fear); brave tempers may stand.
- **The dead rise to it** (wr_dead): nearby skeletons, zombies and our risen dead get glowing eyes, follow and guard it; barrow knights never do. Ends on its death.
- **A new look and sound** (wr_look): our own Wither model, animations and sounds in the pack's style, like the boss remodels (bbmodel through BbMobRenderer, spec doc per REA, **previews to Andy first**).

**What a player sees:** raising the Wither darkens the sky; it tears up the nearest town first, everything living flees, the dead gather to it; then it hunts you with a beam you dodge behind walls, a charge you block, skull rain you read from the ground, and blue skulls you swat back.

**Where to start:** vanilla `WitherBoss` (genSources), Kingdoms `BossIntros`, `BossPosture`, `BossMoves`, `Temperaments`, `client/DarkNights`, Powers `combat/Guard`, `combat/Swat`, `combat/AttackTells`, memory `boss-remodels.md` (how the five remodels were made). Split into two PRs if it grows: behaviour first, then model and sounds.

**Source:** Andy's picks from the Fire and Wither pick list (https://claude.ai/artifact/5xsQSkJSnP8WszLL6JpNtL, db `picks/andy`, saved 2026-11-18 01:23 UTC; file `/mnt/project-files/fire-wither-study/fire-wither-picklist.html`). Study thread: "Cross-reference Knight Armor, Tide, Wither Reincarnated, Inferno". Memory: `fire-wither-fit.md`.

**Licences:** Wither: Reincarnated, Knight Armor and Weapons, Asterion and Inferno are All Rights Reserved / proprietary: ideas only, our own code, names and vanilla-style art. Tide is MPL-2.0 but nothing of it is needed. Only Inferno ships as its own jar (see the Inferno task).

**Rules:** CLAUDE.md as always: one PR per task, change note per mod in `changes/`, no version bumps, check 26.3 APIs against genSources, tuning constants at the top of each class, typed MagicFx/Fx only, iron gear forged via Tinkers, no screen text stating lore. Lore paragraphs for this work are in Lore.md and approved by Andy (The Wither's coming, What fire leaves, Fishing the fire, The labyrinth-builders' craft).

## Notes
- 2026-11-17 8:28 PM ET: Andy approved all four lore paragraphs; they are canon now.
