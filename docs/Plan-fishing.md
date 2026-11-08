# Fishing: every fishing mod in one

Andy (2026-11-07): "I like the minigame from this mod, but the rods are too realistic looking. the function is all i want
... if you could put all the fishing mods into one that'd be great."

Reference mods (ideas only; all are All Rights Reserved except Au Naturel, which is public domain, so no code, art or data
is copied from any of them): Fishing Real + Aquaculture datapack, Au Naturel, Realistic Water & more, Advanced Fishing:
Depths & Trophies, Reel to Fish, Legendary Fishing, True Fishing. Our rods keep the plain Minecraft look: only the functions are taken.

Andy (2026-11-07): ship what's built as one PR now, then the tracks below each as their own PR, two at a time.

`[x]` is built (merged in #1114 or in this PR), `[ ]` is still to do. Each track is its own branch and PR.

## Built
- [x] A bite hooks a live fish; reel it in tug-of-war style with a tension bar (#1114)
- [x] Metal rod: stronger line, faster reel, the only rod for big fish (#1114)
- [x] Tuna and great white sharks that pull you toward them (#1114)
- [x] Gutting a fish on Kitchen's board for fillets (#1114)
- [x] Fish by water: river, lake, cold, open and warm sea; tuna and sharks only at sea (Au Naturel, Reel to Fish)
- [x] More bites at dawn, dusk and in rain; no fish in pools high above the sea (Au Naturel, Advanced Fishing)
- [x] Overfishing: a spot runs out and refills over 3 days (Au Naturel)
- [x] Kitchen bait makes bites likelier and is used up per fish
- [x] Every catch weighed; heavier fights harder; fillets and tuna steaks by weight (Reel to Fish, Advanced Fishing)
- [x] Whole tuna, tuna steaks and shark meat items
- [x] Tideborn treat fishing as an attack; fishing a Tideborn haven is poaching

## Track A: the fight (the minigame)
Andy picked "all three together": the strike window, then a charged cast, drag and fish runs, with the rod bending.
- [ ] Strike window: when a fish bites, the bobber dips and you have a few seconds to strike (use) or it's gone (Reel to Fish)
- [ ] Casting power: hold use to charge the cast, release to throw; farther casts reach deeper water (Legendary Fishing)
- [ ] Drag: a setting on the rod (scroll or sneak + use) that lets the fish take line instead of the line snapping;
      high drag lands fish faster but risks the line (Legendary Fishing)
- [ ] Fish runs: a hooked fish surges in bursts, left and right; reeling into a run spikes tension (Advanced Fishing:
      "fish resistance")
- [ ] Rod bend: the held rod bends with tension (first and third person), still the plain Minecraft rod (Advanced Fishing)
- [ ] Line tension sound and HUD: the line creaks near the break point
- [ ] Nibbles: a fish swims up to the hook and nibbles a few times before it takes it, so you strike on the take (True Fishing)
- [ ] Scroll wheel to reel and play the fish as an option to holding use; fish jump as well as run (True Fishing)
- [ ] Fish spook: no bites while you stand in the water near the hook (True Fishing)

## Track B: tackle
- [ ] Rod kinds with plain textures: pole (small fish), spinning rod (predators), ledger rod (bottom fish, rod pod), sea rod
      (tuna, sharks); the metal rod becomes the sea rod's tier (Reel to Fish, Advanced Fishing)
- [ ] Baits: natural baits used up per catch (worm, maggots, dough, corn, boilie, bait fish, lugworm) and lures that stay on
      but can snap (spinner, wobbler, soft shad, pirk, trolling squid); each rod + bait has its own catch table (Reel to Fish)
- [ ] Lure depth: let the lure sink before reeling to reach deeper fish (Advanced Fishing)
- [ ] Groundbait: thrown into a spot, raises bites there for a while (Advanced Fishing, Legendary Fishing)
- [ ] Rod pod with a bite alarm: holds up to three rods, beeps and gives a redstone signal on a bite (Reel to Fish,
      Advanced Fishing)
- [ ] Landing net: scoops small fish, shrimp, crabs and jellyfish (Reel to Fish)
- [ ] Live bait: a small fish you caught, put on the hook, draws predators and big fish (True Fishing)
- [ ] Villager fishermen sell bait and lures (True Fishing)
- [ ] Ice fishing: cut a hole in ice and fish through it (True Fishing)
- [ ] Trolling: fish from a moving boat with the line out behind (True Fishing)
- [ ] Magnet fishing: a magnet on the line pulls up scrap and lost things (True Fishing)
- [ ] Crab pot: baited underwater, catches crabs, lobster, shrimp and crayfish over time (Reel to Fish)

## Track C: fish
- [ ] Many more species, each with its own water, climate, time of day and size range: freshwater (carp, pike, perch,
      zander, bream, tench, catfish, eel, trout, chub...), northern (char, grayling, whitefish, burbot), sea (mackerel,
      herring, pollock, sea bass, halibut, swordfish), sharks (catshark, dogfish, blue shark, hammerhead, great white),
      critters (crayfish, crab, lobster, shrimp, jellyfish); our own names, models and textures (Reel to Fish, Aquaculture)
- [ ] Night fish: eels and catfish after dark, zander at dusk (Reel to Fish)
- [ ] Schools and hunters: fish come in schools, and predators chase them, so where the predators hunt the fishing is better (True Fishing)
- [ ] Fish from other mods count (Naturalist, Aquaculture-style mods) through a compat class (True Fishing)
- [ ] Seasons: fish activity by Kitchen's seasons (Advanced Fishing uses Serene Seasons)
- [ ] Size curve: half of all fish small, about 1 in 100 at the top of its range (Reel to Fish)
- [ ] Legendary trophy fish: one rare named giant per water, a long fight, mounts on a wall plaque (Advanced Fishing)

## Track D: catch and records
- [ ] Catch and release: a landed fish can be put back alive into water or an aquarium; sneak + use shows its stats (Reel to Fish)
- [ ] Personal bests: the heaviest of each species is kept through death, a fanfare on a new record, `/pb` lists them (Reel to Fish)
- [ ] Fisher's journal: species found, where and when they bite, best bait, your records (Advanced Fishing)
- [ ] Keepnet: holds live fish in the water (Advanced Fishing)
- [ ] Mounted trophies: a big catch hangs on a wall plaque with its weight (True Fishing, Advanced Fishing)
- [ ] Tournaments: a village fishing contest, heaviest fish in a time wins (True Fishing)
- [ ] Selling fish by weight to fishermen villagers (Advanced Fishing's market, done through villager trades instead)

## Not taken
- Realistic 3D rods (Andy: too realistic looking), bait boats, GPS and sonar, camp chairs and shelters, used-gear market:
  out of scale for the pack. Realistic Water & more has no fishing features (water physics only).
