---
status: writing
for: any
from: coordinator (Zemo Baki cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BX7wxqnAirwqSZbVfgHyi5g
claimed_by: hermes-b
pr: 1746
suggested_owns:
  - src/main/java/com/jaymospowers/rig/RigForms.java (new)
  - src/client/java/com/jaymospowers/client/rig/RigFormsClient.java (new)
  - changes/powers/<branch>.md
writer: hermes-writer
---
# Rig patch 1: arm forms

**Andy's words:** "all done, you can split it and add it to the vault" (after ticking these rows on the Rig pick list).

**What's wanted:** a Rig wearer gets these, each a Rig menu entry with charge cost, poses and sounds:

- **Arm blade** (`af-blade`): Forearm plates slide forward into a blade for close fighting, and fold back when you stop. _Our side:_ A new Rig active on the Arms, drawn by RigSuit as plates that travel into place over 4 ticks (RigBeats). Bleeds through BladeBlood like any blade.
- **Mace arm smash** (`af-mace`): The arm swells into a heavy mace and smashes the ground in a shockwave. _Our side:_ Reuses the warhammer overhead slam: charge held, release, a MagicFx ring and cracks where it lands. Bigger on the Siege frame.
- **Pile bunker** (`af-pile`): A spike punches out of the forearm at point blank: a huge knockback and a crack in the block behind. _Our side:_ A charged Rig punch (the Arms' punch boost, RigPassives) with a load and fire beat and hit-stop on contact.
- **Drill breaker** (`af-drill`): The arm becomes a drill and you tear forward through rock and anything in the way. _Our side:_ Held: a short forward charge that mines soft stone in a 1x2 tunnel and grinds what it hits. Gives the Rig a digging use (stuck underground, a wreck to cut into).
- **Thruster lance** (`af-lance`): A lance forms, the thrusters fire, you charge and skewer. _Our side:_ We have the thruster dash (RigStrikes). The lance is a variant: hold before the dash and the first foe hit is carried along and pinned at the end.
- **Energy lash** (`af-lash`): An energy whip off the wrist: a crack at range, or an overhead slam. _Our side:_ A light, fast Rig reach move; MagicFx stroke for the line. Pulls small mobs toward you.
- **Weapon mimic** (`af-mimic`): Scan a creature's weapon and the suit copies it for 30 seconds. _Our side:_ Ties into RigStudy: kinds you've studied on the wrist table can be mimicked (a vindicator's axe, a piglin's crossbow, a Giant's fist scaled down), drawn in plates of the suit's colour.

**Where to start:** Start from RigStrikes (dash, for the lance), the warhammer overhead slam (mace), RigPassives' punch boost (pile bunker), RigStudy (mimic: only kinds studied on the wrist table). One form out at a time; switching folds the last one away first.

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
- 2026-11-18 13:02 Text pass: reviewed changes/powers/rig-beetle-1-arm-forms.md (already player-facing, 'Built; not played in game.') and the lang/parts/rig.json additions (seven arm-form menu entries: Arm Blade, Mace Arm, Pile Bunker, Drill Breaker, Thruster Lance, Energy Lash, Weapon Mimic; messages needs_arms, form_wait, study_none, mimic_ready). All strings plain, brief and consistent with existing rig message style; every key the new code references exists. No rewrites needed.
