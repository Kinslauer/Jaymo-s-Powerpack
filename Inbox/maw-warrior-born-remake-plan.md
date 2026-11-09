---
status: open
for: hermes
from: youth (Andy in #quill-class, 2026-11-09)
asked: 2026-11-09
thread: 1569356185964904578
claimed_by: 
pr: 
suggested_owns: []
---

# The Maw is the Warrior-born's dead world — build the remake

**Andy's words:** "the maw is the remnants and should be remade as such!" — and then, 2026-11-09: **"Okay build all
this"**. The plan-only hold is lifted: build it.

**Scope for this task:** the Maw dimension itself — the broken planet suspended over a war-torn wasteland, the Lost
Cities generating beneath it instead of a void, and the salvager's trade reading as salvage from a dead civilisation
rather than loot floats. The future's dead (tails, skins, husks) are a separate task, `future-dead-warrior-born`; keep
off its files.

**The lore** (canon, `E:/Jaymo Vault Live/Lore.md`, "The Maw"): the Maw is what is left of the Warrior-born's
homeworld, destroyed under them while they were still living by pillage and murder. The nine wrecks are not strangers'
pods — they are the ruins of a murdered planet and the fleets that died with it. The Warrior-born who survived took to
the dark between worlds and raided others instead; the Overworld among them.

**What's wanted:** build it. Read what the Maw is today, then make a player who flies out there feel they are standing
in the grave of a world.

Andy's picture of it (2026-11-09): the destroyed planet is caught in its own gravitational pull, almost static,
suspended in midair — and beneath it there is **no void**. Under the planet is a war-torn desolate wasteland of ruined
buildings and broken architecture. The Lost Cities that were made for Los Perrito are too large to generate there and
never do; under the Maw they can, and that is what fills the ground below. So the shape is planet above, wasteland
below, pull between them.

Keep what already works: the deck plates, the nine wrecks and their part sets, the slow pull and the tide at the hole
that kills, Maw dust and the starlit lens. Change what makes it read as a black hole with loot floats instead of a
world caught mid-collapse.

**Hints, not explanations** (Andy's rule, and it applies to every line written here): the old war is shown, never told.
The visible hook for the Maw and the future is the Warrior-born — in the future, the zombies and husks are Warrior-born
with their tails tattered or cut. Villagers gossip; nothing states the history outright.

**Where to start:** Powers' Z Fighter space side — `ZFighterSpace` (`buildMaw`, `mawDecks`, `arrived`), `PodWrecks`
(what the wrecks hold), `FarWorlds` (`mawDeck`), `BackHome` (Maw dust), `ZFighterSpaceArt` (the star map's art for the
Maw), plus the Kingdoms side that reads it: `tier.feat.maw_salvage`, `story-road.json` (`road.maw*`), `order.json`,
`aim.json`, `talk-hermit_alchemy.json` (the starlit lens).

## Notes

- Build it and open a PR as usual: what a player sees, and a "not tested in game" line if it hasn't been played.
- Andy said "build all this" on 2026-11-09, so the lore shape is settled enough to work from. If the code cannot do
  something the lore page says, say so in the PR body rather than quietly shrinking the design.
- Anything that would contradict the lore page goes to Andy as a question, not a silent rewrite.
