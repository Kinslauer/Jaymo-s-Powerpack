# Jaymo's Alchemy (Powers 0.17): an apothecary's craft

Andy, 2026-10-26: "these mods entail a lot of apothecary / alchemist possibilities i'd like my players to be able to
explore" (Thaumcraft, Thaumic Tinkerer). Then: "lets go all 5, and can the flux in thaumcraft be implemented with the
corruption?" Yes: flux is the bridge (see Flux below).

**Licences.** Thaumcraft (Azanor) is All Rights Reserved and closed source, last a 1.12.2 Forge beta. Thaumic Tinkerer
(github.com/Thaumic-Tinkerer/ThaumicTinkerer) has no licence at all, so all rights reserved; 1.12.2 Forge, a Thaumcraft
add-on. **Nothing of theirs is used**: no code, art, sounds, names of their items or their aspect list. Following
REA-Prompts' clean-room method, step 1 below writes down their *behaviour* as we understand it (from play memory and
public wiki descriptions, so each line is marked), and steps 2+ are our own design built from that spec only.

## Step 1: behaviour spec of the originals (what we take ideas from)
Marked **known** (well documented, stable across versions) or **guess** (from memory, may differ by version).
- **known** Every item and block carries a list of *aspects* (elemental building blocks, with amounts). Six primal aspects;
  the rest are combinations of two. Items without a set list get one derived from their crafting recipe.
- **known** A handheld scanner (the Thaumometer) reveals an item's/mob's/block's aspects the first time, which gives research
  points in those aspects.
- **known** Research is a book (the Thaumonomicon) with tabs of entries; unlocking an entry teaches recipes. In TC4 a
  hex-grid minigame linked aspects; in TC6 research costs points and "theories". **guess**: TC6 gated tiers by observation
  vs. theory points.
- **known** The crucible: a cauldron over heat, filled with water. Items thrown in dissolve into the aspect soup. Throwing a
  catalyst in while the soup holds a recipe's aspects turns the catalyst into the result. Emptying it or overfilling spills
  *flux*.
- **known** Essentia can be distilled out (alembics over an essentia smeltery) into warded jars, and piped.
- **known** The infusion altar: a runic matrix over a pedestal, more pedestals around with components; started with a wand.
  It drains essentia from nearby jars and the components one by one. Asymmetry and missing stabilisers raise *instability*;
  unstable infusions throw items off pedestals, zap people, spill flux or explode.
- **known** Flux (TC4: flux goo/gas; TC6: vis pollution) pools where magic is used. Enough of it spawns flux rifts and
  *taint*, a spreading corruption of land and creatures.
- **known** *Warp* is corruption on the player: temporary, normal and permanent. It causes warp events (whispers, fake
  mobs, blurred sight, a hunter) and unlocks forbidden research. Some potions/sanitizing soap remove the softer kinds.
- **known** Thaumic Tinkerer adds tools on top: ichor and ichor-cloth gear, foci (pulling, pushing, dislocation: picking up
  a block and its contents), gas lights, the spellbinding cloth that strips enchantments, and "kami" end-game gear.
- **guess** TT's enchanter used essentia to apply enchantments without XP.
- **UNKNOWN** exact numbers (aspect amounts, flux thresholds, instability maths). We pick our own.

## Step 2: our design
All in Jaymo's Powers, package `com.jaymospowers.alchemy` (client `client/alchemy`). Kingdoms is reached only by
reflection (as `ZFighterKingdoms` does); Kitchen only through `KitchenCompat`-style checks.

### Essences (shared core, done)
28 essences in `Essence`: 7 primals (Fire, Water, Earth, Air, and the apothecary's three principles **Salt**, **Sulphur**,
**Mercury**), then compounds of two (Life, Light, Metal, Frost, Motion, Spark, Rot, Venom, Crystal, Spirit; Plant, Beast,
Death, Mind, Shadow, Arcana, Wealth; Craft, War, Undeath, and **Flux**). Amounts are `EssenceList`s. Phials hold 10 of one
essence (`Phials`, the `jaymospowers:essences` component).

### 1. Lens, Codex and the essence table (track A)
- Every item has essences: a hand table (data `data/jaymospowers/alchemy/essences/*.json`, items and `#tags`), and for
  the rest a derivation from crafting/smelting recipes at server start (sum of ingredients ×0.75 / output count, at least
  1 of something). Mobs have essences by type/tag; blocks use their item.
- **Scrying Lens**: hold use on a block, mob or item (incl. the off-hand) for a second: first scan of each *thing* reveals
  its essences, discovers new essences, and grants insight. Repeat scans give nothing.
- **Apothecary's Codex**: a book screen with the six `Research.Category` tabs, entries on a grid with lines to parents,
  insight costs, unlock button. Forbidden entries (corruption-gated) show only once corrupted enough.
- Players start knowing nothing; the Lens and Codex are craftable from vanilla things.

### 2. Crucible, alembic, jars (track B)
- **Crucible**: an iron-and-copper pot; needs heat below (fire, campfire, lava, magma, our forge hearth) and water (bucket).
  Items thrown in dissolve: their essences join the pot (10% is lost as flux into the chunk). Pot holds 500.
- **Catalyst brewing**: throwing a catalyst while the pot holds a known recipe's essences makes the result. Recipes are
  data (`alchemy/crucible/*.json`, each names its research). Examples: Orichalc (alchemical metal), Quicksilver, Brimstone,
  Alchemical Salt, Everflame (a lasting light), Purifying Salts and Purifying Draught (the cures in track C),
  transmutations (iron nugget + Metal/Wealth -> gold nugget), seed doubling.
- **Phials**: right-click the pot with an empty phial to draw up to 10 of the essence there's most of.
- **Alembic** on top of a pot distils one essence at a time into **Essence Jars** beside it (each jar holds 250 of one
  essence, labelled by colour). Jars are filled/emptied with phials, keep their contents when broken.
- Spilling: breaking a full pot, pouring water out (sneak + empty hand) or the pot boiling over all dump its essences as
  flux.

### 3. Flux, blight and corruption (track C) — Andy's "flux with the corruption"
- Every alchemical act adds `Flux` to its chunk (crucible losses, spills, unstable infusions, essence enchanting, foci).
  Chunk flux slowly fades (fast with a Ward Stone in the chunk) and spreads to neighbours above a threshold.
- **Land (blight)**: over 300 flux, blight creeps from the source: grass and dirt become Blighted Soil, plants wilt into
  Blight Growth, fibre and Flux Goo puddles form; animals in it sicken (a Blighted effect) and turn hostile; over 800,
  Flux Wisps drift out at night. Blood Moons (Kingdoms, by reflection) double spreading and wisps.
- **People (corruption)**: standing in flux gives fleeting corruption; working with flux-heavy things gives lingering;
  forbidden lore and disasters give deep. Stages: whispers → fleeting sight of things that aren't there (sounds,
  particles, a mob at the edge of view) → unease (short Nausea/Darkness, food spoils faster) → a Wisp hunts you at night.
  Messages stay vague. Corruption also *opens* forbidden research (Research `corruption` gate).
- **Cures (circumstances)**: Purifying Salts (crucible) cleanse blight blocks and flux where thrown; a Purifying Draught
  takes away lingering; the Ward Stone drains a chunk; deep corruption only lifts by sleeping a full night in a bed in a
  chunk with no flux at all under a full moon (once per moon), or praying at a village bell after giving one (Kingdoms
  optional).

### 4. Infusion altar (track D)
- **Infusion Stone** on a **Pedestal**, plus up to 12 pedestals within 5 blocks around it at the same level or one up/down.
- Started by right-clicking the stone with an essence phial (or the Rod from track E). It drains the recipe's essences
  from Essence Jars within 8 blocks (one point every few ticks) and takes components off pedestals one by one, with
  particles streaming in.
- **Instability**: from asymmetry (an item with no mirrored partner), the recipe's own value, and fewer stabilisers
  (Salt Candles, skulls, Ward Stones nearby lower it). Events: an item flung off, a zap, a flux burst, a spill of lingering
  corruption, and a small blast at the top.
- Recipes are data (`alchemy/infusion/*.json`): ichor gear, the Rod's foci, Ward Stone, enchant infusions (an extra
  level past the table's cap at the cost of flux).

### 5. Essence enchanting and the artificer's gear (track E)
- **Essence enchanting**: at the enchanting table (our ESO port), any enchantment you know can be paid with essences from
  carried phials instead of its items (each enchantment maps to essences, e.g. Sharpness = Metal + War); it leaves flux
  and a little lingering corruption. The **Spellbinding Cloth** strips one enchantment from an item into essence phials.
- **Artificer's Rod** (spends essences from phials) with foci: Pull, Push, Lift (pick up a block with its contents and
  set it down), Ember (fire bolt), Frost. Each cast leaves a little flux.
- **Ichor** (from an infusion) and **Ichorium** sword, pickaxe, axe, plus an **Ichorcloth** hood/robe/leggings/boots set.
  Ichorium tools are better than netherite but every hit/block adds fleeting corruption.
- **Everflame lamp** (gas-light idea): places an invisible light where you aim.

## Build plan
Shared core first (done by the lead: `Essence`, `EssenceList`, `Phials`, `Flux`, `Corruption`, `Knowledge`, `Research`,
`Alchemy` with stubs for each part). Then tracks, two at a time (CLAUDE.md), each in its own package, lang part
(`lang/parts/alchemy_<track>.json`) and art generator (`tools/gen_alchemy_<track>.py`):
- Round 1: A (essence/, codex/) and B (crucible/).
- Round 2: C (flux/) and D (infusion/).
- Round 3: E (gear/, plus enchanting/ hooks).

Tuning numbers sit at the top of each class.

## Open questions for Andy (defaults chosen, change any)
1. Lens and Codex from vanilla things, no starting knowledge (default yes).
2. Deep corruption cure: a full night's sleep in a flux-free chunk under a full moon (default), or something else?
3. Ichorium stronger than netherite at the cost of corruption (default yes).
4. Should Kingdoms' court wizards trade alchemy things (phials, salts) later? (default: not now)
