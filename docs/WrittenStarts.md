# Written starts: a start you build by hand

Andy (2026-11-25): "is there a way i could curate a start like an adventure map? placing items and gear specifically how
i want? fostering a linear progression?"

A written start is a place you build yourself in creative, saved with a structure block, that a newcomer wakes in, and a
tale that walks them through it a step at a time, opening its doors as they go. It is a road on the life story's last
page, **Brought in from the dark** (`LifeStory.Start.WRITTEN`), open from day one. Code: `WrittenStarts.java` (raising the
build, its marks and gates), `Tales.written` (begins its tale), `tools/written_starts.py` (the example build).

## What one is made of

| Part | File | What it holds |
|---|---|---|
| The build | `data/<ns>/structure/<path>.nbt` | Everything you placed: blocks, chests and barrels with exactly what you put in them, signs, written books on lecterns, armour stands and item frames (save with entities on), and the marks below. |
| The start | `data/<ns>/written_starts/<id>.json` | Which build, which layer is the ground, how often it's drawn, which tale leads through it. |
| The tale | `data/<ns>/tales/<id>.json` | The steps, in order (`docs/StoryQuests-Plan.md` has every step kind), with `"start": {"by": "written"}`. |

`<ns>` is `jaymoskingdoms` for the ones in the mod; a world's datapack (`<world>/datapacks/<pack>/data/<ns>/...`) works the
same and `/reload` picks it up.

## Making one

1. **Build it** in a creative world. Put the gear in the chests yourself, write the signs and books, stand the armour
   stands. What you leave in a container is what every player who takes the road finds there.
2. **Mark it.** Place structure blocks in **Data** mode and type a name in each:
   - `spawn` where the player wakes (feet in that block). `spawn:north` (or east, south, west) to face that way, as the
     build was saved; it turns with the build.
   - `place:<key>` a spot the tale can send them to: `place:cellar`, `place:altar`.
   - `gate:<key>` what a step opens. **One** gate mark: the doors, trapdoors and fence gates touching it swing open, and
     iron bars touching it (and the bars joined to those) are taken away. Put it in the air beside the door or above
     the trapdoor. **Two or more** marks of the same gate: everything between them (a box, the marks at its corners) is
     opened, or taken away if it can't open: a rubble wall, a cobweb curtain. Iron doors and iron trapdoors make the best
     gates, since nobody can open them by hand.
   Every structure block in the build is taken out once it stands.
3. **Save it** with a structure block in **Save** mode around the whole build (turn on *Include entities* if you placed
   stands or frames), named like `jaymoskingdoms:written_starts/my_start`. The file is in
   `<world>/generated/jaymoskingdoms/structure/written_starts/my_start.nbt`; copy it into the mod's or a datapack's
   `data/jaymoskingdoms/structure/written_starts/`. If part of the build is underground (a cellar), save from the
   bottom of it and count which layer is the ground's top: that's `ground`.
4. **Write the start**, `data/jaymoskingdoms/written_starts/my_start.json`:

   ```json
   {
     "title": "What it is (for you; the log names it)",
     "structure": "jaymoskingdoms:written_starts/my_start",
     "ground": 3,
     "weight": 1,
     "tale": "my_start_tale",
     "bare": false,
     "shared": false
   }
   ```

   `ground`: which layer (from 0 at the bottom of the save) sits on the ground's top block. `weight`: how often this one is
   drawn when there are several. `bare`: true wakes them with nothing of the story's own kit (no bread, purse or class
   gear), only what the build and the tale give. `shared` and `at`: see "One place for everyone" below.
5. **Write the tale**, `data/jaymoskingdoms/tales/my_start_tale.json`, as any tale, with:
   - `"start": { "by": "written" }`: only a written start begins it; nobody offers it.
   - places `{ "at": "written", "mark": "cellar", "name": "the cellar" }`: the build's `place:cellar` mark, exactly
     there (`mark` left out: the place's own key). Other places work as in every tale (`other_town` for the nearest
     settlement, `wild` with a distance, ...).
   - `"opens": ["cellar"]` on any step: those gates open once the step is done.
   - Its cast are the start's people, set out at their places when the player comes near, as in every tale.

   `python3 tools/tale_check.py` checks it. A written start's tale needn't take days or end two ways.

## Where it stands

Every player who takes the road gets their own copy, raised from their own random setting-out point (as every start's
place is), out in the wild on dry, natural ground that rises and falls no more than two blocks under it, and at least 96
blocks from any settlement's seat: in the open between towns, which is the reason a shelter like this stands where it
does (give yours its reason in Lore.md). It's turned a random way. Its land is made while they wait behind the loading
screen; if no level site is found in time, they wake in the wild instead. Never in the Aether. A party mate who takes the
road after them wakes beside them in the same build, with the tale too. The build stays when the life ends; a new life
raises another.

## One place for everyone (a shared start)

Andy chose "both, chosen per start" (2026-11-25). With `"shared": true` the build is raised once for the whole world, and
every newcomer who draws that start wakes in the same one, each with the tale of their own:

- `"at": [x, z]`: its middle on that column, its ground layer on the ground's top there, unturned (as you saved it).
- `"at": [x, y, z]`: the same, with its ground layer at height `y`.
- no `"at"`: wherever the first newcomer's start search sets it down (as an own-copy start would be).

It is raised the first time someone draws it, not before. What earlier players took from its chests is gone for later
ones, and gates they opened stay open. An `"at"` can be anywhere you like: the 96-block keep from settlements is only
for starts that find their own place. Several starts can mix: shared ones and own-copy ones are drawn by `weight` alike.

## The one that ships: the way-hut

`written_starts/way_hut.json`, built by `tools/written_starts.py`, its tale `tales/the_way_hut.json`. A Hearthkeeper's
way-hut (Lore.md, "Way-huts"): one stone room, a cold hearth, a cot, the keeper's chest (bread, apples, flint and steel,
sticks, string) and a store under the floor behind an iron trapdoor (stone tools, torches, boots, potatoes, coal, kelp).

1. Read the keeper's slate on the board (`place:table`): the cellar's trapdoor opens (`gate:cellar`).
2. Go down to the store (`place:cellar`).
3. Keep the hut through the night (a vigil by the hearth, `place:hearth`).
4. Find the nearest town.

## Not in this yet

- Choosing between several written starts: with more than one, the server draws one by `weight`.
- Refilling a shared start's chests for each newcomer.
- An in-game author tool (marks and the JSON written for you).
- The road's card shows one text for every written start (`lang/parts/written-starts.json`), not each start's own.
- Fate never deals this road.
