---
status: open
for: any
from: coordinator (Zemo Baki cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BX7wxqnAirwqSZbVfgHyi5g
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/rig/RigGuns.java (new)
  - src/client/java/com/jaymospowers/client/rig/RigGunsClient.java (new)
  - changes/powers/<branch>.md
---
# Rig patch 2: guns that unfold

**Andy's words:** "all done, you can split it and add it to the vault" (after ticking these rows on the Rig pick list).

**What's wanted:** a Rig wearer gets these, each a Rig menu entry with charge cost, poses and sounds:

- **Tap or hold the palm blast** (`gn-charge`): Tap for quick bolts, hold to charge one big blast. _Our side:_ The palm blast is tap only (RigActives PALM_*). Add a held charge with the limb glow growing (already picked from The Awakening) and a bigger shot at full.
- **Gatling arm with heat** (`gn-gatling`): Six barrels spin up, pour bolts while held and overheat if you hold too long; vents hiss to cool. _Our side:_ Hold to spin, a heat bar on RigHud like RunningHot's, a forced vent at full heat that leaves you open.
- **Railgun** (`gn-rail`): Charge, then release a line that goes through everything in it. _Our side:_ An instant line, unlike the Unibeam's held beam: one shot, every creature along it hit, a scorched line (BeamScars). Long cooldown.
- **Sonic cannon** (`gn-sonic`): Hold to blast a cone of sound that throws creatures back and stuns them. _Our side:_ A held cone off both palms; MagicFx wave. A Resonance user caught in it hears it amplified (we already have Rig vs Resonance knockback).
- **Both-arm charge beam** (`gn-twin`): Both arms become cannons and fire one beam together. _Our side:_ A Unibeam variant at Mind level 5: both palms join the chest, wider and shorter, a braced wide stance.
- **Siege mode** (`gn-siege`): You plant your feet and an artillery cannon unfolds from the back; you can't move while it's up. _Our side:_ The barrage is a one-off (RigStrikes). Siege mode roots you and lets you fire shell after shell where you look until you fold it away. Strongest on the Siege frame.

**Where to start:** Palm blast is in RigActives (PALM_*): add the held charge there via a hook. Gatling heat bar on RigHud like RunningHot's. Railgun leaves BeamScars. Twin beam is a Unibeam variant (RigUnibeam). Siege mode builds on RigStrikes' barrage but roots you.

## Shared rules for every rig-beetle patch
- Source: Andy's picks (2026-11-18) from the Zemo&Baki "DC Universe" jar, which is one Blue Beetle armour mod. **All Rights Reserved: ideas only.** Own code, own poses, own art, no DC names (no scarab/Khaji-Da/Kord/Bug). Pick list: https://claude.ai/artifact/4hWigMxitF3zRsp1JYQsVX (db `picks/andy`). Reference dump on Andy's PC: `Mod Testing\zemo-dc\inventory.md` (names and text only).
- Everything is **Frostborn Rig tech**: plates slide, lock and fold; nothing grows or is alive (the symbiote's grown weapon arm and tendrils stay the symbiote's).
- New actives are **Rig menu entries** (RigMenuScreen / RigPicks pattern), so J or a no-power key can be set to them; don't invent new keybinds. Gate the stronger ones by Mind level (RigLevels) and whole set where it makes sense; every one costs Core charge.
- Poses go through RigBeats / RigPose (three beats: wind-up, snap with overshoot, settle) and RigSuit draws the plates moving into place. Effects through MagicFx only. Tuning numbers as `static final` at the top of each class.
- Files several patches touch: `RigActives`, `RigPose`, `RigSuit`, `RigClient`, `RigHud`, `RigMenuScreen`/`RigPicks`. Add your own class and keep your edits there to a few hook lines; rebase before PR.
- Run 2 patches at a time. Suggested pairs: (1 arm forms + 5 the Mind), (3 back rig + 6 senses and nova), (2 guns + 4 flight).
- Lore first: read `Lore.md` (the Frostborn rigs, Los Perrito's troopers). Anything needing a new line gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/powers/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging.
