---
status: in-review
for: any
from: coordinator (Zemo Baki cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BX7wxqnAirwqSZbVfgHyi5g
claimed_by: hermes-a
pr: 1765
suggested_owns:
  - src/client/java/com/jaymospowers/client/rig/RigFlightFeel.java
  - src/main/java/com/jaymospowers/rig/RigDive.java (new)
  - changes/powers/<branch>.md
---
# Rig patch 4: flight and falling

**Andy's words:** "all done, you can split it and add it to the vault" (after ticking these rows on the Rig pick list).

**What's wanted:** a Rig wearer gets these, each a Rig menu entry with charge cost, poses and sounds:

- **Pull up** (`fl-pullup`): Diving fast at the ground, the visor flashes a warning and the Mind shouts; the suit flares at the last moment if you let it. _Our side:_ On RigHelmView and RigMind: a warning under a time-to-impact, and the one-knee landing catches what it can.
- **Hyper thrust** (`fl-hyper`): A last gear past full flight, short and costly, with a burst and a crack. _Our side:_ Full flight has a sprint. Hyper is a few seconds on a double-tap of sprint while flying, heavy charge cost.
- **Bank into turns** (`fl-bank`): The body rolls into a turn and levels out after. _Our side:_ Rig flight has a barrel roll. Add a smooth roll into turns, eased by speed, for everyone watching.
- **Meteor dive** (`fl-meteor`): Rocket straight up, then crash down where you look. _Our side:_ Reuses Gravity's meteor burst look (GravityMeteorFx) at a Rig scale: a crater-less ring, no block damage, a big shake.
- **Other flyers look smooth** (`fl-others`): Other players' flight is smoothed on your screen so they never jitter. _Our side:_ I'd check how other Rig pilots look on a server first; if they stutter, smooth their pose and roll between updates.

**Where to start:** Pull up: a time-to-impact warning on RigHelmView + a Mind shout, RigLanding catches what it can. Banking: an eased roll into turns, seen by everyone (RigPose), alongside the existing barrel roll. Meteor dive reuses GravityMeteorFx's look at Rig scale, no block damage. 'Other flyers look smooth': check first how other pilots look on a server; only smooth if they stutter, and say what you found in the PR.

## Shared rules for every rig-beetle patch
- Source: Andy's picks (2026-11-18) from the Zemo&Baki "DC Universe" jar, which is one Blue Beetle armour mod. **All Rights Reserved: ideas only.** Own code, own poses, own art, no DC names (no scarab/Khaji-Da/Kord/Bug). Pick list: https://claude.ai/artifact/4hWigMxitF3zRsp1JYQsVX (db `picks/andy`). Reference dump on Andy's PC: `Mod Testing\zemo-dc\inventory.md` (names and text only).
- Everything is **Frostborn Rig tech**: plates slide, lock and fold; nothing grows or is alive (the symbiote's grown weapon arm and tendrils stay the symbiote's).
- New actives are **Rig menu entries** (RigMenuScreen / RigPicks pattern), so J or a no-power key can be set to them; don't invent new keybinds. Gate the stronger ones by Mind level (RigLevels) and whole set where it makes sense; every one costs Core charge.
- Poses go through RigBeats / RigPose (three beats: wind-up, snap with overshoot, settle) and RigSuit draws the plates moving into place. Effects through MagicFx only. Tuning numbers as `static final` at the top of each class.
- Files several patches touch: `RigActives`, `RigPose`, `RigSuit`, `RigClient`, `RigHud`, `RigMenuScreen`/`RigPicks`. Add your own class and keep your edits there to a few hook lines; rebase before PR.
- Run 2 patches at a time. Suggested pairs: (1 arm forms + 5 the Mind), (3 back rig + 6 senses and nova), (2 guns + 4 flight).
- Lore first: read `Lore.md` (the Frostborn rigs, Los Perrito's troopers). Anything needing a new line gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/powers/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging.
