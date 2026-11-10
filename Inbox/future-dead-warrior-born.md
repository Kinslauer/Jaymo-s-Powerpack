---
status: claimed
for: hermes
from: youth (Andy in #quill-class, 2026-11-09)
asked: 2026-11-09
thread: 1569356185964904578
claimed_by: claude-cloud
pr: 
suggested_owns: []
---

# The dead: Warrior-born in the future, old villagers in the Overworld, the draugr

**Andy's words:** "Yes husks zombies and player zombies all with mangled tails in various conditions (12+ at least) and
30+ different skins. Zombies and husks should have villager like skins on the overworld too, as they are meant to be
dead villagers from time past. Like the mca zombies already in the game, except these are explained as being much
older and much more deteriorated. Like the draugr, who are all undead warriorborn ancestors who lived on the overworld
before being trapped there forever by a powerful frostborn z fighter"

**What's wanted:** build it. Andy said **"Okay build all this"** on 2026-11-09, so the hold is lifted — this is real
work with a PR, not a plan.

1. **The future's dead are all Warrior-born.** Husks, zombies and the dead players alike carry mangled tails in at least
   twelve different conditions (bent, broken, half-cut, gone at the root, and so on) across thirty or more skins, so the
   horde reads as one people. The future already skins plain zombies per UUID; this is that, extended and with tails.
2. **The Overworld's zombies and husks become old villagers.** Villager-like skins, so they read as the dead of a world
   that has been losing this war for a very long time — much older and more deteriorated than the MCA zombies, which are
   the recently dead the village actually knew. The two should read differently on sight: one is grief, the other is
   history.
3. **The draugr are the Warrior-born's ancestors** — raiders who lived on the Overworld in the old age, trapped here
   forever by a powerful Frostborn Z Fighter, which is why they never leave the tomb.

**Where to start:** Powers `power/chrono/future/` (`Future` spawn mix, `FutureZombieRendererMixin`, the per-UUID skins
from `tools/gen_future_zombies.py`); Kingdoms `inhabitants/Draugr.java` and the Stormborn Tomb's spawners; the MCA
zombie path already in the pack, so the new Overworld dead read as older rather than as a second kind of fresh one.

## Notes

- Build it and open a PR: what a player sees, and a "not tested in game" line if it hasn't been played.
- Art is the bulk of this. Skins and tail states are generated scripts in this project — write the generator rather than
  painting by hand, and say where the files land. If the volume is too much for one PR, land the generator plus a first
  set of skins and say plainly what is still to come.
- Lore lives in `E:/Jaymo Vault Live/Lore.md` ("The future: Los Perrito", "The dead"). Nothing written for this may
  explain the old war: tails, skins and ruin are the tell, and villagers only gossip.
