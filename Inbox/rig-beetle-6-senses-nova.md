---
status: claimed
for: any
from: coordinator (Zemo Baki cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BX7wxqnAirwqSZbVfgHyi5g
claimed_by: hermes-a
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/rig/RigCloak.java (new)
  - src/main/java/com/jaymospowers/rig/RigOverride.java (new)
  - src/main/java/com/jaymospowers/rig/RigNova.java (new)
  - changes/powers/<branch>.md
---
# Rig patch 6: senses, tech override and nova

**Andy's words:** "all done, you can split it and add it to the vault" (after ticking these rows on the Rig pick list).

**What's wanted:** a Rig wearer gets these, each a Rig menu entry with charge cost, poses and sounds:

- **Active camouflage** (`se-camo`): The suit goes see-through and your first hit from it is an ambush. _Our side:_ Quiet frames are already seen late (RigVisibility). Add a timed cloak that shimmers, mobs lose you, and the first blow does more.
- **Tech override** (`se-override`): Golems shut down and doors obey you. _Our side:_ Iron and clockwork golems freeze for a moment, redstone doors open at a look, and androids and mechs (built from Frostborn designs) stutter. Would need a line in the lore.
- **Ore callouts** (`se-ore`): While mining, the visor calls out ore nearby. _Our side:_ The scan marks ores already (RigScan). A passive quiet ping when you dig near ore, no scan needed.
- **Hex visor** (`se-visor`): A hex pattern and vignette wash over the view as the helm closes. _Our side:_ RigHelmView draws the visor. Add a short closing wipe and a faint hex grid at the edges.
- **Nova** (`hl-nova`): The shell opens and blasts everything away from you. _Our side:_ A panic button: all charge spent, everything near thrown back. Good when you're swarmed or grabbed.

**Where to start:** Camo builds on RigVisibility (quiet frames); first blow out of it is an ambush. Ore pings extend RigScan's ore marks (passive, while digging). Visor wipe + faint hex edge in RigHelmView. Nova: all charge spent, everything near thrown back, breaks a grab. **Tech override needs lore**: golems freeze, redstone doors open at a look, androids and mechs (built from Frostborn designs, Lore.md) stutter. Write a (new) Lore.md paragraph and quote it in full to Andy before merging.

## Shared rules for every rig-beetle patch
- Source: Andy's picks (2026-11-18) from the Zemo&Baki "DC Universe" jar, which is one Blue Beetle armour mod. **All Rights Reserved: ideas only.** Own code, own poses, own art, no DC names (no scarab/Khaji-Da/Kord/Bug). Pick list: https://claude.ai/artifact/4hWigMxitF3zRsp1JYQsVX (db `picks/andy`). Reference dump on Andy's PC: `Mod Testing\zemo-dc\inventory.md` (names and text only).
- Everything is **Frostborn Rig tech**: plates slide, lock and fold; nothing grows or is alive (the symbiote's grown weapon arm and tendrils stay the symbiote's).
- New actives are **Rig menu entries** (RigMenuScreen / RigPicks pattern), so J or a no-power key can be set to them; don't invent new keybinds. Gate the stronger ones by Mind level (RigLevels) and whole set where it makes sense; every one costs Core charge.
- Poses go through RigBeats / RigPose (three beats: wind-up, snap with overshoot, settle) and RigSuit draws the plates moving into place. Effects through MagicFx only. Tuning numbers as `static final` at the top of each class.
- Files several patches touch: `RigActives`, `RigPose`, `RigSuit`, `RigClient`, `RigHud`, `RigMenuScreen`/`RigPicks`. Add your own class and keep your edits there to a few hook lines; rebase before PR.
- Run 2 patches at a time. Suggested pairs: (1 arm forms + 5 the Mind), (3 back rig + 6 senses and nova), (2 guns + 4 flight).
- Lore first: read `Lore.md` (the Frostborn rigs, Los Perrito's troopers). Anything needing a new line gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/powers/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging.
