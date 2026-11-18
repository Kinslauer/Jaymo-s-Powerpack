---
status: claimed
for: any
from: coordinator (Zemo Baki cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BX7wxqnAirwqSZbVfgHyi5g
claimed_by: hermes-a
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/rig/RigBack.java (new)
  - src/client/java/com/jaymospowers/client/rig/RigBackClient.java (new)
  - tools/gen_rig.py (back plates only) + regenerated RigSuitMesh
  - changes/powers/<branch>.md
---
# Rig patch 3: the back rig

**Andy's words:** "all done, you can split it and add it to the vault" (after ticking these rows on the Rig pick list).

**What's wanted:** a Rig wearer gets these, each a Rig menu entry with charge cost, poses and sounds:

- **Folding wings** (`bk-wings`): Thin wings unfold from the back for fast flight and fold away when you hover or land, with a buzz while they beat. _Our side:_ RigSuit gets two vane panels on the Core's back plate that spread as full flight starts and fold in hover (eased, RigBeats). Adds lift in turns so cruising banks better.
- **Wing slash** (`bk-wingslash`): Wings out like blades and you dash through everything. _Our side:_ The thruster dash with wings spread: it cuts everything you pass instead of ramming the first thing.
- **Spear legs** (`bk-legs`): Spear-tipped legs burst from your back and impale what's around you. _Our side:_ Four mechanical legs from the back plate stab out and back in a ring. The symbiote's tendrils are organic and stay its own; these are jointed metal.
- **Pincer grab and throw** (`bk-pincer`): Back pincers seize a target; press again to throw it. _Our side:_ Two jointed arms over the shoulders grab one creature and hold it in front of you (CarryPose for the held one), then a throw along your look.
- **Grapple claw** (`bk-grapple`): A claw on a line: zip to a wall, or yank an enemy to you. _Our side:_ Uses WebHeft's rope weight and limits, so it behaves like a real line. A Rig active, not a web.
- **Chain lightning from the helm** (`bk-horns`): Lightning jumps from the helm to the nearest foes in a chain. _Our side:_ A Mind level 4 active: arcs to up to 4 targets (MagicFx spark), stronger in rain, and it overcharges the Core like our lightning strike does.

**Where to start:** Wings: two vane panels on the Core's back plate, spread in full flight, folded in hover (eased). Wing slash upgrades RigStrikes' dash (blade drones were not picked). Pincer hold uses CarryPose for the held one. Grapple uses WebHeft's rope rules (a Rig active, not a web). Horn lightning overcharges the Core like the lightning strike does (RigActives OVER_*).

## Shared rules for every rig-beetle patch
- Source: Andy's picks (2026-11-18) from the Zemo&Baki "DC Universe" jar, which is one Blue Beetle armour mod. **All Rights Reserved: ideas only.** Own code, own poses, own art, no DC names (no scarab/Khaji-Da/Kord/Bug). Pick list: https://claude.ai/artifact/4hWigMxitF3zRsp1JYQsVX (db `picks/andy`). Reference dump on Andy's PC: `Mod Testing\zemo-dc\inventory.md` (names and text only).
- Everything is **Frostborn Rig tech**: plates slide, lock and fold; nothing grows or is alive (the symbiote's grown weapon arm and tendrils stay the symbiote's).
- New actives are **Rig menu entries** (RigMenuScreen / RigPicks pattern), so J or a no-power key can be set to them; don't invent new keybinds. Gate the stronger ones by Mind level (RigLevels) and whole set where it makes sense; every one costs Core charge.
- Poses go through RigBeats / RigPose (three beats: wind-up, snap with overshoot, settle) and RigSuit draws the plates moving into place. Effects through MagicFx only. Tuning numbers as `static final` at the top of each class.
- Files several patches touch: `RigActives`, `RigPose`, `RigSuit`, `RigClient`, `RigHud`, `RigMenuScreen`/`RigPicks`. Add your own class and keep your edits there to a few hook lines; rebase before PR.
- Run 2 patches at a time. Suggested pairs: (1 arm forms + 5 the Mind), (3 back rig + 6 senses and nova), (2 guns + 4 flight).
- Lore first: read `Lore.md` (the Frostborn rigs, Los Perrito's troopers). Anything needing a new line gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/powers/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging.

## Notes
- 2026-11-18 14:15 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
