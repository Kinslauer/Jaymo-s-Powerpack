# Jaymo's Powers: MODLOG

A superpowers mod for Minecraft Java 26.3 (Fabric). It's the centrepiece of a modpack with Jaymo's Kitchen.

## Design
- **Getting a power:**
  - **Survival:** each player's power awakens on its own after a random 3–5 Minecraft days of survival play
    (72000–120000 ticks; the timer is saved and kept through death), sooner through the power's calling (`core/Callings`). Hints appear 5 minutes and 1 minute out.
    The awakening shows a title, a totem sound and particles.
  - **Creative:** press **H** for the powers screen and pick any power, or remove yours.
  - **Commands** (gamemasters): `/power set <players> <wind|arsenal|web|chrono>`, `/power clear <players>`,
    `/power awaken <players>` (a random power right now), `/power get <player>`.
- **Controls:** four shared ability keys, **R, G, V and B** by default, rebindable under Controls → Powers. **H**
  opens the powers screen, which lists what each of your keys does right now (R G V B, Z, C, X): the lines follow
  your fate (ascended, corrupted, both; Web's symbiote) and mastery, from lang `power.jaymospowers.<p>.key.<slot>`
  plus `.ascended`/`.corrupted`/`.ascended_corrupted` or the power's own `statusSuffix` (see `client/KeyGuide`).
- **Powers:** Wind Control, Pocket Arsenal, Web Slinger, Chronokinesis. Each has its own file in `docs/`.

## Architecture
- `api/Power` is the server half: `onGained`, `onLost`, `tick`, `onKey`, `allowsFloating`, `skipsMovementCheck`.
  The client half is `client/PowerClient`: `tick`, `onKey`, `renderHud`, `onActiveChanged`.
- `core/Powers` holds the registry and the `jaymospowers:power` attachment. The attachment is persistent, kept on
  death, and synced to all clients, so `Powers.of(player)` works on both sides. The same class runs the awakening.
- Key presses go to the server (`KeyInput` packet, held state in `PowerInput`) and to the local `PowerClient`.
- `mixin/ServerGamePacketListenerImplMixin` stops the "floating too long" kick and the "moved too quickly"
  check while a power says so. Flight and swinging need this on dedicated servers.
- Each power lives in its own packages:
  - `power/<name>` and `client/power/<name>`
  - its own mixin configs, `jaymospowers.<name>[.client].mixins.json`
  - its own lang file, `lang/parts/<name>.json`, which `processResources` merges into `en_us.json`
- Each power has its own `init()`.
- Sound (0.15.7, after Resonance's idea of conditions-driven ambience; LGPL, no 26.3 build, nothing copied):
  `core/Soundscape` stacks layers of variants at uneven beats and echoes them in caves; the flier's side of a flight
  is `client/sound/PowerLoop` beds plus `Accents` that react to speed, banking, braking, rain, water and altitude.

## Environment
- Same toolchain as Jaymo's Kitchen: JDK 25, `GRADLE_USER_HOME=E:\gradle-home`, Loom 1.18, Fabric API 0.161.0+26.3.
- Build output goes to `E:\jaymospowers-build\<checkout folder>`, kept out of OneDrive.
- The dev game also loads Jaymo's Kitchen (`libs/jaymos-kitchen-*.jar`), Balm and Cooking for Blockheads as
  `localRuntime`.
- The decompiled 26.3 sources are in `E:\mcsrc-26.3`.
- Launchers:
  - `Play Jaymo's Powers.bat` runs the dev client.
  - `Build Modpack.bat` gathers every jar into `modpack\mods` for CurseForge profiles and the server.

## Log
Entries before 0.20.0 are in `docs/modlog/powers-0.1-to-0.19.md` (grep there for older history). New entries go at the end of this file.

- **0.20.0, Coins replace emeralds (Andy's Custom Trade Currency pick, `docs/Otectus-Plan.md` patch 3; Kingdoms 0.7.498,
  Kitchen 0.5.56):** Andy chose "Combine: culture coins replace emeralds". The economy lives in Kingdoms
  (`docs/Coins-Plan.md`): the culture coins are the money, a copper bit is the small change and emeralds are a gem sold
  to a money changer. Powers reaches it through `core/KingdomsMoney` (a MethodHandles bridge; amounts in milli, thousandths
  of an emerald, `KingdomsMoney.worth(n)`; emeralds as the fallback without Kingdoms).
  - Z Fighter prizes, raid and invasion shares, the Riches wish, patrol bounties, space-mission pay, the hermit, the
    afterlife teller, the costume table and the bone-setting priest all pay and charge in the coin of the land, from
    purses and bundles too, and name the price in that coin.
  - **Purse on the hip:** a coin purse in the belt bauble slot (`Baubles.belt`, tag `jaymospowers:hip_shown`) is drawn on
    the right hip with the lantern's sway (`WornLanterns`); it gives no light. Web's symbiote grabs coins as valuables.
  - The melting gap log skips coins (realm silver and the like are money, not metal).
  - Not compile-checked yet at this entry; not played.

- **0.20.1, Kingdoms 0.7.501, tails calm down when walking:** Andy: "they should pulse and slap like they have their own
  mind, but they swing violently whenever you walk". The cause: the sway wave ran at `age * (rate + walk speed)`, and with
  the age in the thousands every change of walk speed threw the phase round, so the tail thrashed. Warrior-born tails
  (`ZFighterTail`) and the Great Ape's tail now run on fixed clocks: a lazy wander of two slow waves, a curl that comes and
  goes, and now and then a slap (a few quick whips to the side). The walk only calms them by half, lifts them a little and
  adds a small swing on the stride (`walkAnimationPos`). Beastfolk tails in Kingdoms get the same (see `kingdoms/MODLOG.md`).
  Not compile-checked in the cloud (pure math on fields already used there); not played.
- **0.20.2, Kingdoms 0.7.502, a start in the Nether can make its first tools:** Andy: "add rare pebbles and flint to the
  nether so those starts are possible for emberborn [...] after trying that start you can only advance by stealing axes from
  pigmen". `ground/NetherRocksFeature` lays a small cluster of loose rocks on one of the Nether's floors in about one chunk in
  three (demon biomes too): netherrack rocks on netherrack and nylium, blackstone on blackstone, basalt on basalt and soul
  ground, and 30% of piles are loose flint (75% on gravel). The three rocks are new `GroundRocks.Variant`s (vanilla textures,
  4 craft into their block, knap like plain stone). Flint now knaps on netherrack (`FirstTools.hard`, `Knapping.hard`), and
  crimson/warped roots, nether sprouts and weeping/twisting vines give plant fibre to a knife (`fiber_plants` tag). Kingdoms
  adds the sticks (wart blocks) and the avatar camps' welcome; see `kingdoms/MODLOG.md`.
  - Not compile-checked yet at this entry; not played.
- **0.20.3, shield bang:** With a shield raised in the off hand and a weapon in the main hand, left click bangs the
  weapon on the shield (`combat/ShieldBang`, `client/combat/ShieldBangClient`): a loud clang, the weapon arm swings and
  a vibration goes out for sculk and wardens. Hostile mobs within 24 blocks that aren't already fighting walk to the
  noise; wild animals, bats and fish within 16 run from it. Neutral mobs and anything tamed, ridden or leashed ignore it.
  One bang per click, half a second apart. Left click with the shield down still attacks as before.
  Holding the click instead charges a **shield bash** (a bar fills over 1.5 s; 2 feathers): let go and you step in and
  throw the shield forward, striking, shoving and staggering what's in front of you and knocking raised shields aside,
  harder the longer the charge. A metal weapon banged on a metal shield (metal by name, or the
  `jaymospowers:clanging_metal` item tag, which holds the iron-rimmed vanilla shield) rings like a bell for the symbiote:
  hosts within 12 blocks flinch as from a bell (`WebSymbiote.noise`), loose symbiotes run (`WebSymbioteBlob.startle`).
- **0.20.4, Kingdoms 0.7.505, attack tells are easy to see and hear:** Andy: "the particle effect for attack indicators
  should be more visible, and a sound should play like for time flow. maybe a star or flash in the respective color". The
  old tell (a few pale dust specks and sparks) got lost on a face. `combat/AttackTells.glint` now draws our own
  `jaymospowers:tell_glint` particle (`Glint`: colour + size; client `combat/TellGlintParticle`, sprites
  `textures/particle/tell/tell_glint_0-1`, dark-rimmed so it reads on pale faces and sky): a full-bright four-point star in the tell's colour (white 0.8 blocks, red 1.1)
  that pops past full size, settles, turns slowly and holds through the 6-tick wind-up before shrinking away, a small white
  star at its heart (`tell_glint_core`, no rim), and a ring of dust in the colour. It's sent forced (shows on reduced particles, out to 48 blocks).
  With it plays Time Flow's warning ping from the mob: a high note-block bell for a parryable blow, a lower bell with a
  bass drum under it for a heavy one. Every tell uses it: goal- and brain-driven mobs, Burrukai, Bladeshroom Hunters, future
  gunners, and Kingdoms' fighters, crewmen and gunners (through `PowersCompat.tellGlint`).
  - Tuning: `GLINT_SIZE`, `HEAVY_GLINT_SIZE`, `CORE_SHARE`, `PING_*`, `DRUM_*`, `GLINT_RANGE` in `AttackTells`; `LIFE`,
    `OVERSHOOT`, `SPIN` in `TellGlintParticle`.
  - Compile-checked in the cloud (Gradle `compileClientJava`); not played. Preview:
    `art-overhaul/attack-tells/tell-glint-preview.png` in the project files.
- **0.20.5, graded swords no longer crash the server (Kingdoms 0.7.506):** an Expert, Perfect or Master grade multiplied every mining
  rule on a sword-like item, and the instant-mine rule (bamboo, `Float.MAX_VALUE`) overflowed to Infinity, which the
  game can't sync or save. The first such item (a graded WeaponMod copper boomerang) crashed the server when its owner
  moved it in their inventory. `Grades.bake` now caps a scaled speed at `Float.MAX_VALUE`. Kingdoms 0.7.506 builds again:
  a merge left `HandshakePose` with two `NOD` constants, so the handshake's head tilt is now `SHAKE_NOD`.
- **0.20.7, gem sockets (Patch 5)** (`jewellery/sockets/`, plan `docs/Sockets-Plan.md`): Andy picked the idea of
  Immersive Apotheosis; this is our own version (none of its code or art).
  - Forged weapons and armour get gem sockets by grade: Expertly and Perfectly Forged one, Masterwork two, Poorly and
    Well Forged none. Tinkering tools and jewellery take no gems.
  - Socketing: piece in the main hand, a cut gem in the off hand, sneak-use a Smithing Anvil (iron or stone). With
    tongs in the off hand the last gem comes out whole (tongs wear 8); with an empty off hand it is prised out and
    shatters (use twice to confirm).
  - Each gem adds its own bonus (the jewellery gem's attribute) times its grade and cut: full on a weapon in the main
    hand, half on worn armour. The bonuses are item modifiers baked by `Grades.bake` through the new
    `Grades.ATTRIBUTE_PASSES` (ids `socket_<slot>_<n>`), so a broken piece loses them until repaired. If a piece's grade
    drops, gems past its count go dormant.
  - Tooltip: sockets filled, each gem and its cut grade, empty sockets, dormant gems. One data component
    (`jaymospowers:gem_sockets`), no attachments, no new items or packets. Kingdoms only gets loading hint 86 (0.7.508).
  - Sockets: a prise warning now belongs to one piece. Using the anvil on a different socketed piece warns again instead of shattering its gem.
- **0.20.8, Kingdoms 0.7.509, Kitchen 0.5.57, patron gods part (a)** (plan `docs/Patrons-Plan.md`; see `kingdoms/MODLOG.md`):
  Powers only gets the Hearthkeeper's Gentle Hands: `MedicineSkill.ticks` also reads Kingdoms'
  `Patrons.blessing(player, "gentle_hands")` by reflection (a fifth off revives and treatments). Not played.

- **0.20.9, Ultimates patch 0 (PR #909): each Ultimate is its own alien (`docs/Ultimates-Plan.md`), starting with Bulwark's Titan** (Andy: "this is how ultimates should work").
  - **Framework:** an Ultimate is a `XenoUltimate` (its own body, stats, R G V B and C, never on the dial), reached through `XenoForm.evolved()`. As the Ultimate, C is its big move and holding C drops you back. It has its own health on top of the base alien's (a thin bar on the HUD shows the base alien's held health): emptied, you fall back to the base alien, stunned, and can't go Ultimate again that change. A fifth HUD chip shows C. Aliens with no evolved Ultimate yet keep the old 0.18.93 buff until their patch.
  - **When:** on an unforged band C doesn't change you; the Ultimate comes on by itself once per change as a last stand, when a great foe (a vanilla boss, anything with a boss bar, `GreatFoes`; Kingdoms' bosses through `KingdomsCompat`) would finish you or take you under a quarter health. A reforged band changes in on C at will too (Andy's pick), burns charge twice as fast as the Ultimate, and still gets the last stand.
  - **Film:** the new MORPH shot letterboxes and zooms on each changing part as it grows into place, then pulls back. The first time you become a given Ultimate it's the long, exaggerated cut (5 s, crackles and a roar). Players within 32 who aren't fighting see the same film; everyone else just sees the change in the world.
  - **Titan:** Bulwark's Ultimate, a plated four-armed stone giant (2x, +15 hearts): Quake Combo, Wallbreaker Clap, Boulder Hurl, Meteor Slam, and Earthsplitter on C. Model `client/.../ultimate/UltimateTitan`, skin from `tools/gen_xeno_ultimates.py`.
  - Debug: `/powerdebug xenoform ultimate on|off|break`. Compile-checked (no new errors against master); not played.
- **0.20.10**: no Powers change; pack bump for Kingdoms 0.7.510 (chopped logs crack deeper instead of thinning, chop meter).
- **0.20.12, Kingdoms 0.7.512, pink fairies:** Andy wanted Paxo's Pink Fairy as a super rare bauble (that mod is All
  Rights Reserved and Forge 1.20.1 only, so this is our own, `baubles/Fairies`, `PinkFairy`, `BottledFairyItem`). A pink
  fairy, a tiny rose-lit sprite with flapping wings, comes very rarely to the flowers of a forest, meadow or cherry grove
  at night near a player out there (1 in 550 every 10 s, roughly one night in ten spent in the woods; never two within
  128 blocks). She darts away from anyone who comes within 7 blocks without sneaking; creep up and catch her in an empty
  glass bottle. Killing one curses you with Bad Luck II for 20 minutes; day fades her away. The Bottled Pink Fairy is an
  epic, glinting bauble for the amulet or charm slot: +2 luck, +1 heart, a faint rose glow about you, and once she turns
  a death aside (6 health, Regeneration II for 10 s, before you would go down; never the void or /kill), then flies free
  and you keep the empty bottle. Sneak + use lets her go for good. Art: `tools/gen_fairy.py` (animated item textures).
  Kingdoms (`FairyWonder`): villagers who see one worn or held stare and remark on her (envy, awe, old tales; children
  beg to hold her), once a day each, lifting an MCA villager's mood; given to an MCA villager she is the dearest gift
  there is (60, above a nether star's 40; `data/mca/gifts/fairy.json`).
  Andy's follow-ups: the fairy flits round inside the bottle (8 frames); the bottle cracks when she saves you (glass
  breaking, shards, a Cracked Fairy Bottle left instead of a glass bottle); and villagers wear her too: one given a fairy
  (MCA's gift lands in their inventory) puts her on, she glows about them and saves them once the same way. Worn fairies
  hang on the chest for all to see, players' and villagers' alike (`Fairies.WEARS`, synced; `client/baubles/WornFairies`,
  a layer on every humanoid renderer). She also fits the belt slot, hung at the hip like the coin purse (`#jaymospowers:hip_shown`).
- **0.20.13, brewing quality (Patch 7)** (`alchemy/brewing/`, `alchemy/cauldron/CauldronStirring`, plan
  `docs/Brewing-Quality-Plan.md`): Andy picked the idea of Immersive Brewing; this is our own version (none of its code
  or art).
  - **Mortar and Pestle:** up to four of a brewing ingredient (`#jaymospowers:mortar_grindable`), five strokes on the
    timing bar, and the batch comes out Coarsely Ground, Finely Ground or Ground to Dust (`jaymospowers:ground` on the
    vanilla item, so it still works in stands and recipes). Coarse or Fine can be ground again.
  - **Timed stirring:** the cauldron's six stir clicks are now six turns on a timing bar (Perfect, Good, Miss), the
    floating ingredient still swirling round each turn.
  - **Graded brews:** each ingredient is a step scored on its grind and its stir; the brew's mean gives Crude, Plain,
    Fine, Expert or Masterwork Brew on bottling (`jaymospowers:brew_grade`). Durations x0.75 / 1 / 1.25 / 1.5 / 2
    through vanilla's `potion_duration_scale` (so splash and lingering keep it), drinking x1.15 / 1 / 0.9 / 0.8 / 0.6
    through the stack's own `consumable`, Masterwork glints. Stand-made potions are ungraded (Plain). Tipped arrows don't
    carry a grade.
  - **Brewer's Alembic** (`jaymospowers:brewers_alembic`, research Distilling Brews): over heat, 2-3 bottles of one
    brew become one fewer, a grade better, after 60 s, once per brew. The essence Alembic is unchanged.
  - Tuning: `BrewQuality` (bars, grind and grade thresholds), `BrewGrade` (multipliers), `MortarEntity`,
    `BrewersAlembicEntity`, `CauldronStirring`. No new attachments, mixins or keys. Kingdoms and Kitchen untouched.
    Built, not played.
- **0.20.14, Kingdoms 0.7.513, patron gods part (b)** (plan `docs/Patrons-B-Plan.md`; see `kingdoms/MODLOG.md`): Powers only gets
  `Wounds.healPart` reading Kingdoms' `Patrons.blessing(player, "wounds_slow")` by reflection (`MedicineSkill.blessing`): the
  Hearthkeeper's trial and scar on one who turned from it slow every part heal. Not played.

- **0.20.15**: no Powers change; pack bump for Kingdoms 0.7.514 (the skill web: one radial K screen, six bridge perks).
- **0.20.16, Zsolt's RPG Series (patch 0)** (plan in project files `rpg-series-study/RpgSeries-Plan.md`): Andy chose to ship
  Wizards, Archers and Rogues & Warriors (all rights reserved, unchanged, downloaded by id) with Spell Engine (GPL-3.0),
  Spell Power (LGPL-3.0), Runes and the small MIT APIs (armour models, ranged weapons, structure pools, bundles) in packMods.
  - **No Trinkets:** `compat/SpellEngineCompat` hands Spell Engine every worn bauble as a spell source and as an ammo
    container, so a quiver worn in the body slot feeds bows. Spell Engine's ring, necklace, trinket and quiver tags join
    our ring, amulet, charm and body tags. The worn list now syncs to its wearer. No slot for spell books: spells are
    learned on Kingdoms' skill web or from a trainer (Kingdoms 0.7.516), and the Spell Binding Table's recipe is dropped.
  - **Forged:** Rogues' dagger, sickle, double axe, glaive and Archers' spear (stone, iron, gold) come from new heads
    (`WeaponParts`: rogue_dagger_blade, sickle_blade, double_axe_head, war_glaive_blade, ranger_spear_tip, drawn in
    `tools/weapon_parts_assets.py`); their grid recipes and the diamond ones' are dropped (`Uncraftable.RPG_SERIES`).
    Flint daggers and spears stay in the grid.
  - **Keys:** Spell Engine's "use the item, not the spell" key moves off Left Alt (OK Zoomer) to mouse side button 2
    (`KeyConflicts` v6). Class gates and skill-web perks come in the next patches. Built, not played.
- **0.20.16, parry sound** (Andy): every guard parry (`Guard.parried`) rings steel instead of the pitched anvil, from
  "Updated Parry Sound - MedievalWeapons" (MIT, CurseForge 522501) whose two clips are Eelke's CC0 freesound 523521
  (`sounds/guard/parry_1|2.ogg`, event `jaymospowers:guard.parry`); a quiet shield thud stays under it. Z Fighter
  bare-hand blocks keep their punches.
- **0.20.16, Bag of Holding** (Fuzs, MPL-2.0, 26.3.5, Item Interactions bundled) in packMods; its three grid recipes are
  dropped (`Uncraftable.RPG_SERIES`) because Kingdoms' Mages make bags with the Enchanted Storage perk (0.7.517).
- **0.20.17, Carry On carries blocks** (Andy: "We need to swap our block carrying for this"): Carry On 2.12.1 (LGPL-3.0,
  Tschipp; official Fabric 26.3 build, shipped unchanged, Modrinth `d0jTyKNs`, CurseForge 274259/9420425) is in
  `packMods`. Sneak + right-click a chest, furnace, barrel or any other block with something in it, empty hands, to lift
  it with its contents; right-click to set it down (its own config, `config/carryon-common.json`; `pickupAllBlocks` off,
  so anvils no longer lift; crafting tables still do, since Kitchen gives them a block entity).
  - Our own block lifting is gone: `CarryBlock.carriable` is now only the space pod's parts (they're set onto a pod or a
    salvage rack, which Carry On can't do). The placed-block tracking (`CarryBlockItemMixin`, `markPlaced`) is removed;
    the `carry_placed` chunk attachment stays registered so old chunks load quietly. Duplex's fetch and the Bound Block
    still use `CarryBlock.liftableAny` / `takeOut` / `place` / `land`.
  - Bodies stay ours (`core/Carry`). `core/CarryOnCompat` reads Carry On's state by reflection and
    `mixin/carryon/CarryOnPickupMixin` (`@Pseudo`, optional `jaymospowers.carryon.mixins.json`) makes its
    `PickupHandler` never lift an entity, never lift while our carry, a Kitchen corpse, being carried, thrown or downed
    has your arms, and never lift a `#jaymospowers:carry_on_no_lift` block (pod parts, salvage rack, smeltery controller
    and drain, kiln load, sentry spawner, infusion matrix; optional: Kingdoms' bounty board, pulley and plot stake,
    Kitchen's windmill and waterwheel). With a block in Carry On's arms you can't pick up a body or a corpse (a
    `UseEntityCallback` phase ahead of ours), grab a ledge, take a part off a salvage rack, and our first-person arm
    poses step aside; it counts as carrying for Z Fighter strength training.
  - Not played.
- **0.20.18, Kingdoms 0.7.518, patron gods part (c)** (plan `docs/Patrons-C-Plan.md`; see `kingdoms/MODLOG.md`): **The patrons' weapons** (`armory/PatronWeapons`): Hearthguard (blows heal you and those near),
  Anvil-Father's Hammer (blows strip armour), Tidecaller (use: drag foes in), Huntsman's Spear (marks prey), Gravelight
  (burns the undead), Sunbrand (use: the sun on a foe), Ashfang (use: smoke and a leap back, then a deep cut), The Plain
  Word (strips blessings, shows the unseen). Heads from Kingdoms' champions only (`WeaponParts.PATRONS`: never cast or
  forged), put together at the Tool Station, graded. Stronger for that patron's sworn (Kingdoms'
  `Patrons.blessing(player, "patron_weapon/<key>")` by reflection, `KingdomsCompat.blessing`), cold while you're in
  its trials.
  The Sunbrand's Judgement stops at the first block in the way, so it can't mark a foe behind a wall. Not played.
- **0.20.19**: no Powers change; pack bump for Kingdoms 0.7.519 (diplomacy and elections: envoys, treaties, elected rulers and regencies).

- **0.20.21, Ultimates patch 1: six more Ultimates, each its own alien** (`docs/Ultimates-Plan.md`; Titan from 0.20.9 is the pattern). Each is a `XenoUltimate` with its own body, health bar, R G V B and C, a model in `client/.../ultimate/`, a 128x128 skin from `tools/ultimates/<id>.py` (all run by `tools/gen_xeno_ultimates.py`), and a film part order. The base aliens lost their old 0.18.93 Ultimate-only code and now return the new form from `evolved()`; their models and skins are unchanged.
  - **Pyre -> Sunheart** (1.25x, +10 hearts): R Sunray (held beam, 16 blocks), G Sun Jet (hover, Sprint streaks as a comet that ignites what it passes), V Blaze Nova (7 blocks, 8 damage, knockback, burn), B Lava Wake (8 s of fire and magma underfoot that turns back), C Corona (10 blocks, 10 damage and burning, blinds players, melts snow and ice, ring of fireballs).
  - **Velox -> Standstill** (1.15x, +8 hearts): R Overdrive, G Thousand Blows (12 blows), V Vortex (lifts and holds, then throws outward), B Flicker Storm (up to 8 foes, then back), C Standstill (8 s, 24 blocks, Speed III).
  - **Prism -> Geode** (1.5x, +12 hearts): R Shard Storm (7 shards), G Rampart (curved amethyst wall), V Mirror Root (hold; sends projectiles back, 30% of melee returned), B Spike Forest (cuts and slows, clusters for 3 s), C Geode (8 blocks, 5 s).
  - **Wisp -> Revenant** (1.2x, +6 hearts): R Wraithwalk (5 thick), G Veil (+6 first strike), V Banshee Wail (16 blocks), B Drift (hold, slow flight), C Possession (15 s, a swing strikes through the host).
  - **Gremlin -> Swarm** (tiny, +5 hearts): R Pounce, G Haywire (flips levers, doors and gates from 24 blocks), V Leech Bite, B Scuttle (ceilings, one-block gaps), C splits into 5 glowing gremlins and swaps with them.
  - **Knell -> The Tolling** (1.6x, +18 hearts): R Sonic Lance (24 blocks, 14 damage), G Deep Sense (hold, 40 blocks), V Deep Shriek, B Undertow (come up where you look, 24 blocks), C The Tolling (7 tolls, 10 blocks).
  - Wired: `XenoFormModels.ultimate()`, `XenoformHand` arm poses, lang, `gen_xenoform.OWN_ULTIMATES`; Excalibur pack copies regraded. Compile-checked; not played.
- **0.20.22**: ChoiceTheorem's Overhauled Village (CC BY-NC-ND 4.0, unchanged, 26.2 Fabric build) joins the pack: its villages and outposts spawn in the shared pools, settlements in its villages are havens, and towns take its trade buildings (Kingdoms 0.7.521).
- **0.20.23, Wisp floats upright** (Andy: "it should float upright instead of an angled glide"): Wisp's B is now Float, not Glide. Held, it hangs in the air upright and drifts where you steer with WASD (0.22 a tick, 0.3 sprinting), Jump rises and Sneak sinks; on the ground it walks as usual until Jump lifts it, and letting go drops it (still no fall damage). The new `XenoformState.FLY_FLOAT` draws it upright with a slow sway, arms hanging loose and the tatters hanging below, and sneaking to sink no longer crouches it (`BillowPoseMixin`). Revenant's Drift floats the same upright way. Keen and Talon keep their tipped glide. Compile-checked; not played.

- **0.20.24, Kingdoms 0.7.522, Stegonaut, Atroxiia, Varasuchus, Ignivorus, Aetherion:** the first three lesser dragons rebuilt from Saint's
  Dragons (its MIT code; our own art): a plated walker of the jungles and plains, a frost wyrm of the snow with a spear
  for a tail, and a crocodile dragon of the beaches and swamps; the last two swim. Plus Ignivorus, a fire dragon on the
  Kintsugi wyvern that flies about the Nether: fireball volleys and lines of magma pillars; and Aetherion, a star dragon over
  the Aether with a starlight beam and a Cataclysm of falling stars (Kingdoms' MODLOG has the rest).
- **0.20.25**: no Powers change; pack bump for Kingdoms 0.7.523 (talks that matter: hidden trust, respect and warmth,
  branching talks, promises villagers hold you to).

- **0.20.26, death by cause** (Jungxvv's ideas, Andy's answers in the plan doc, 2026-11-04): a player's death is now a roll on what put them down (`core/wounds/Mortality`). Lose it and the character is over: the body stays where it fell, they wake in the Judge's hall if the afterlife is on (everyone now, not only Z Fighters) and are born again through the life story with every start unlocked, their power, afflictions, scars and breaks gone with the dead (`Mortality.endLife`, Kingdoms' `LifeStory.reincarnate`). Win it and the death is turned away: out cold, then they wake out in the wild (40 to 120 blocks off, never in a town; a spared boss defeat 300 to 800; drowned on the nearest shore; a fall where they landed) with an injury that fits.
  - Odds (final): boss 70% (0% when you're under a quarter of its strength), zombie 90%, skeleton 50%, blast/creeper 20%, venom (spiders, snakes) 0%, poison 70%, wither 0%, fire 50%, drowning 20%, fall 10%, lava, sea creatures, the void and beheading 100%, other environment 50%, players and anything else 50%. Executions are final when a player or a boss does them. A second defeat while a survivor still has an open injury (unset break, arrow in them, a timer) is always final (`Mortality.SURVIVOR`).
  - Injuries: zombie = torn arm and body, festering at once; skeleton = arrow lodged in a broken leg (it works free once the leg is set or splinted); blast = both legs and an arm broken, 1 in 4 lose a limb; fall = both legs broken.
  - Timers (`Afflictions`): venom 2 days (honey, Potion of Cleansing), poisoned blood 10 days (honey, Cleansing, any healer), water in the lungs 20 days (a healer; 40% of drinks go down the wrong pipe, a day sooner each time), withering half a heart of max health a day (milk, honey, a healer; it grows back half a heart a day). "A healer" is anything that eases a fever: town healers, infirmaries, clerics and court wizards, Restoration, shrines. Each worsens in four stages with its own lines.
  - Scars for life (`Scars`): burns (-3 armour, -2 toughness), weak lungs (4 half-feathers of stamina held back, `Feathers.addPenalty`), frostbite, lightning scars, wasting, crushed ribs.
  - Taken captive (Andy: "if you are kidnapped by your sentient murderers ... theyll have it hidden or even wear it"): left for dead by an orc, goblin, ogre, pirate, raider, illager or outlaw, you wake chained in their camp instead (Kingdoms' `Captors`, through `Mortality.CAPTORS`). Every other wake keeps your gear.
  - Server settings in `jaymospowers-server.properties`: `death_by_cause=true`, `final_odds=1.0`. Kingdoms tells it its bosses (boss bars); Kitchen tells it of every drink. Compile-checked; not played.
- **0.20.27, turned by what killed you** (Andy: "there's a chance that the death method that killed them turns them into one of those specific hollows, like enthralled by a vampire, or maybe zombified by a witch"): before the roll for your life, whatever killed you may make one of its own of you (`Mortality.TURNINGS`; Kingdoms' `Hollows` decides who and what). Turned, the death is never final: you rise where you fell (the drowned on the shore) with no new injury, as a Hollow of the matching look. Not for executions, beheadings, lava or the void. Compile-checked; not played.
- **0.20.28, Cube Animals and wild spawns** (Andy: "add all the ones we dont have, but keep the snake, and shark as a different variants ... same with elephants. remove the encounters, its better when the predators can naturally spawn"): Cube Animals (CurseForge 1550917, file 9411257; CC0 in its jar, shipped unchanged) joins the pack. Its antelope, barn owl, bison, capuchin, caracal, chameleon, cricket, eagle, fennec, lynx, marmot, mouse, reindeer, sawfish, snow leopard, toucan and walrus spawn as it has them; its rattlesnake, shark and elephant spawn beside Naturalist's and Kingdoms' as other breeds; its black bear, crocodile, komodo dragon, moose, piranha, red and roe deer, snail and tiger are kept out (we have them). Wilderness encounters are gone: Naturalist's predators and elephants spawn naturally, and so do Kingdoms' beasts, monsters of legend and wind dragons (Kingdoms 0.7.526). Compile-checked; not played.
- **0.20.29, more Cube Animals breeds** (Andy: "add the deer too and the croc, and the bear"): Cube Animals' red deer, roe deer, crocodile and black bear now spawn beside Naturalist's deer, alligator and bear (Kingdoms 0.7.527). Still kept out: its komodo dragon, moose, piranha, snail and tiger. Compile-checked; not played.
- **0.20.30, the Aether over the Overworld over the Nether** (Andy: "something like this is why i wanted no new dimensions, is this possible? only replacing the end being above with our aether?", about One Dimension): new worlds can be one stacked level. One Dimension (CodxIO) with CustomChunks, Planeshift and CodxLib, all shipped unchanged (packMods, CurseForge only, kept out of the dev game), puts each realm in a band of the Overworld's level, each made by its own generator with its own rules: fly up out of the Overworld and you come out under the Aether's islands, dig through the Overworld's floor (bedrock turns to obsidian) and you fall out of the Nether's sky, seeing the next world through the seam, with no portal and no loading screen. The End and our other worlds (the future, the moons, the Arsenal's pocket...) stay dimensions of their own. Worlds made before, or with it turned off on the create-world screen, are unchanged.
  - `compat/OneDimensionDefaults` writes the pack's default order to `config/one-dimension.json` the first time (Aether, Overworld, Nether; no loop, no gap, separate skies, the End and our worlds left out, on for dedicated servers too). A file that's already there is left alone.
  - `compat/Stacked`: in a stacked world every band answers `level.dimension() == OVERWORLD`, and `server.getLevel(NETHER)` is the old, separate Nether nobody walks in. So "which realm" is asked by height (`Stacked.realm/in/at`, through CustomChunks' API by reflection) and "go to the Nether" gets the level that holds it and shifts its heights (`Stacked.level/shift/players/minY/maxY/within`). Without CustomChunks every answer is the vanilla one.
  - `mixin/StackedHeightMixin`: `Level.getHeight(type, x, z)` in a stacked world answers the ground of the band being ticked, the Overworld's outside one, not the top of an Aether island overhead (every mod's villagers, camps, meteors and spawn searches ask it).
  - Wind's rise and the fall home from the Aether no longer jump in a stacked world: you fly or climb up and fall out. The first time in still tells the lore; falling out of the Aether gives the same Slow Falling (`WindAether.stackedTick`). The afterlife stays in the separate Aether, reached by dying as before.
  - By layer now: Emberborn Nether rifts (to the Nether band, its own floor heights), the demon realm's villages, army and layer names, the demon-master start and its ward, Rig wrecks in the Nether, Feathers' Nether heat, Gravity's corruption, Chrono echoes' Nether rules, Sunheart's ice, the Flame Avatar's Nether count, the Gravity meteor's fall height.
  - Compile-checked; not played. Things to watch: anything else that asks `getMaxY()` for the top of the Overworld now gets the Aether's top; SwordStone's Laevateinn (worldgen) may not pick the Nether band; under the Nether's floor is void.
- **0.20.31, Kingdoms 0.7.529, Golemworks:** Some realms are magically attuned and keep golems: gilt lamp wardens and
  verdant menders alongside iron golems, and a throne golem that serves only the realm's king or queen: it folds into a
  giant throne for them, takes every blow meant for them, and rises to fight once it has taken more than 20. Anyone with Golemancy can make them. See Kingdoms' MODLOG 0.7.477.
- **0.20.32, server start fix and CurseForge ids:** ChoiceTheorem's Overhauled Village (4.1.1, a 26.2 build) stopped the
  26.3 server loading: 105 of its structures use `minecraft:simple_state_provider`/`weighted_state_provider` (gone in 26.3)
  and `lithostitched:limited` (not in Lithostitched 2.0.6). It's out of the pack (its line is commented out in build.gradle)
  and Build Modpack drops it from the profile (`removedIdsFromProfile`); put it back once CTOV has a 26.3 build. The
  CurseForge manifest had failed on its `cf: null` too. Ten Modrinth-only mods got their CurseForge [project, file] ids
  (matched by file name) and moved into packMods, so the CurseForge import now downloads them: First-person Model, Not
  Enough Animations, EMF Compat: NEA, Sounds, Lithium, FerriteCore, C2ME, Structory, Structory: Towers, Homesteads.
  Still Modrinth-only (no matching CurseForge file): Runes, Grim Kingdoms VE, Repurposed Structures, MidnightLib, Ji AFK
  Cinematic, Dungeons and Taverns (CurseForge stops at 6.0.2). Not built here.
- **0.20.33**: no Powers change; pack bump for Kingdoms 0.7.530 (quests to 272: more for every trade, town troubles,
  and town works that players build together).
- **0.20.34, One Dimension x Lithostitched new-world hang:** a new world hung at "Selecting global world spawn": chunk
  [0, 0] failed at terrain with a NullPointerException in Lithostitched 2.0.6's `fillBands` (head of
  `NoiseBasedChunkGenerator.buildTerrain`), which calls `carverBiomeRegion.registryAccess()` though vanilla marks it
  nullable. One Dimension's `StackedChunkGenerator` (CustomChunks) passes null there on purpose for each layer. New
  `mixin/lithostitched/LithostitchedBandsMixin` (MixinSquared 0.3.7, MIT, jar-in-jar) cancels only Lithostitched's handler
  when the region is null; vanilla still gets its null. Keeps both One Dimension and Enderscape (which needs
  Lithostitched). Signature checked against the shipped jars; not built or played here (the cloud can't reach Fabric's maven).
- **0.20.35, Kingdoms 0.7.531, Stegonaut moves:** the Stegonaut's poses redrawn after the Bastionodon animation set
  (ground eating in three parts, bite, grazing, grumbles, flinch, doze, gallop, swim stroke, death), none of its files used
  (its rig is Saint's Dragons'). See the Kingdoms MODLOG.
- **0.20.36, the high sky (Andy, 2026-11-04):** A new top band of the One Dimension stack, `jaymospowers:high_sky` (192 tall,
  open air), over the Aether: the default stack is now high sky, Aether, Overworld, Nether (`compat/OneDimensionDefaults`; a
  `config/one-dimension.json` still holding exactly the old pack order gets the high sky put on top; new worlds only).
  `power/zfighter/ZFighterHighSky`: thin air, where only ki keeps you breathing (a Z Fighter spends 1 ki a second, anyone else
  or a fighter run dry chokes, 2 damage a second after 3 s; inside a pod is fine), and raiders fly there (no gravity, they fly
  at their foe). Pod trips out of the stacked Overworld are ambushed there 35% of the time from the second trip on, for a
  pilot who can fly on ki (`ZFighterPodTrip` phase AMBUSH): the pod is struck and hangs smoking, pilot and passenger are thrown
  out flying, a tyrant and 2-4 raiders come in. Beat them and fly back to the pod to go on; sneak by it sooner to run for it
  (the pod lands hard, parts shaken loose); die (or leave) and the pod falls to the ground below, shaken; after 3 minutes the
  band breaks off. Client `client/power/zfighter/sky/HighSkyClient`: a dark dome round the camera with stars by day, the sun,
  the Pale Moon, Rustfall and Glacia close and big, and the planet's curve about 15 degrees below level, its seas, lands and
  clouds sliding past as you fly, lit by the sun, with a blue haze along its edge; drawn on the sky (54 blocks out), so it
  never hides anything nearer, and fading out near the band's bottom. Atlas `textures/environment/high_sky.png`
  (`tools/gen_high_sky.py`). Fixes found on the way: a raider band's non-chief raiders were taken for leftover tournament
  champions and removed on their first tick, and were added to the band after the load check ran; both fixed
  (`ZFighterRogue.raider`, `ZFighterRaiders.raider`). Compile-checked against the 26.3 jars; not played.
- **0.20.37, Kingdoms 0.7.532, story figure tails:** tails on the life story's figure no longer hang down between the
  legs. See the Kingdoms MODLOG.
- **0.20.38, sitting knees bend** (`MeditatePose.bendKnees`, via `KneeBend`): Andy: "the knees should bend, because if
  not, you sit with the legs open". Cross-legged now folds each shin back across in front of the other leg; knees up
  draws the knees up with the shins down to the feet; seat poses hang the shins down from the knee (legs crossed lifts
  the right thigh over the left). Leaning back is unchanged. Sitting while still holding Sneak no longer also gets the
  crouch's legs (`CrouchPose.otherPose`). Compile-checked; leg shapes checked in a box sketch, not seen in game.
- **0.20.39, Kingdoms 0.7.533, gap jumps and dug-out taverns:** villagers and other humanoids jump gaps their path
  stops short of, and Dungeons and Taverns' taverns (and any surface building saved with voids for rooms) no longer
  generate packed with earth. See the Kingdoms MODLOG.
- **0.20.40, Kingdoms 0.7.534, medieval villagers again:** Andy (2026-11-04): "the outfits seem to be broken again,
  they arent wearing medieval attire anymore". His clip showed MCA's own green plaid shirt (`neutral/mason/0`), which
  the MCA - Default Medieval resource pack draws as a tan tunic, so the pack had dropped out of his selected packs while
  its once-only marker said it was done. `PackResourcePacks` now keeps it on: at any launch it's found off it's switched
  on again (and kept on the accepted older-format list so the game won't drop it on load);
  `config/jaymospowers-medieval-villagers-off` keeps it off for anyone who'd rather. The Nobodies wear plain rags now,
  not sailors' stripes (see the Kingdoms MODLOG). Not compiled or played yet.
- **0.20.41, Hematic-style blood (Andy, 2026-11-04):** Hematic (litewer) is a custom licence (no ports, no reuse of its
  code or art) and Forge/NeoForge only, so these are our own, built on our blood (`core/blood`, `client/blood`):
  - **Blood on bodies, armour and weapons** (`core/blood/Bloodied`): whoever bleeds gets a coat of their own blood
    (synced attachment `jaymospowers:blood_coat`, drawn over the skin by `client/blood/BloodCoatLayer` from
    `textures/blood/body_blood.png`, only where the skin has pixels; a player's outer layer stays clean). Their armour
    takes some, and the weapon that drew it (a blade, or a heavy blunt blow) and whoever swung it up close get some
    too (item component `jaymospowers:blood`: amount, colour, when). Armour gets it painted into its texture and icon
    the way grime is (`ArmourGrimeTextures`, `armour_blood.png`); any bloody item's icon gets it; a flat held, dropped
    or framed item gets it as one more layer over its own (`BloodItemOverlay` + `BloodItemOverlayMixin`). It darkens
    as it dries (5 minutes). Water washes it (under water all of it, wading boots and greaves, rain slowly), a coat
    wears off by itself in a few minutes, and ArmourGrime's hand washes (cauldron scrub, wet sponge, dunking the item)
    take blood off too.
  - **Bloody footprints** (`BloodTrail.print`): walking through a fresh pool (under ~3.5 minutes old) wets your feet
    for 10 prints, a stride apart, left and right, pointing the way you went, fainter as they wear off. They're the
    walker's, so trackers can follow them. Drawn flat by `BloodPuddle` (`textures/particle/blood/print_*.png`).
  - **Blood over edges and on walls** (`client/blood/BloodWallMark`): a drop that flies into a wall splats on it (a big
    one may run down from it); a pool that spreads over the edge of a drop-off pours down the side (up to 2 blocks of
    wall) and drips off the end a few times. Same life and drying as the pool that fed it.
  - **Arrows and bullets bleed** (`BladeBlood.shot`): arrows and bolts spit blood back where they go in; bullets
    (any mod's projectile named bullet, pellet, musket, round, shot) also burst out the far side and on along the path,
    with stains laid behind. The Void Armory's flintlocks and the future's guns call it themselves.
  - **Magma blood** (tag `blood/magma`: magma cubes, striders): bright orange, full-bright as it flies, glowing where it
    lands and smoking, then cooling to a dark crust over 45 s (`BloodParticles.MAGMA`).
  Masks and sprites are ours (`tools/blood_textures.py`). Compile-checked against the 26.3 jars; not played.
- **0.20.42, Kingdoms 0.7.535, commandments for every patron:** every patron now has ten commandments in a book like
  the Truth's (its ten kept as they were); breaking your own patron's costs favour and is counted in the book, and the
  Truth's villagers refuse orders that would break theirs. See the Kingdoms MODLOG.
- **0.20.43, Kingdoms 0.7.536, Atroxiia pulled for now:** Andy: it had no walk animation. It no longer spawns, is out
  of `/kingdom dragon`, and any already in a world disappear; the code and art are kept for when it's fixed.
- **0.20.44, Kingdoms 0.7.537, Zoological Guide by recipe:** Cube Animals no longer hands out its Zoological Guide on
  first join; craft it from a book, a feather, an ink sac and a bone (shapeless). See the Kingdoms MODLOG.
- **0.20.45, sky waterfalls kept rare (Andy, 2026-11-04):** "i like the idea of a waterfall from heaven, but i found this as
  soon as i spawned since adding the one dimension, and it just spoils the reveal too early". The Aether's springs pour off the
  sides of islands; on its own the water fell into nothing, but with One Dimension the Overworld is underneath, so it fell the
  whole way down as a column from the sky. `aether/world/SkySpringFeature` (`jaymospowers:sky_spring`, the old spring config
  with a new type) floods where a new spring's water could run and drops the spring if it would leave the bottom of the Aether
  band, unless it is in a heavenfall: one chunk in about one of every three 2048-block squares, never within 3000 blocks of
  the world spawn or 0, 0. Springs that land on a lower island or in a cave, and the Aether as its own dimension, are unchanged.
  New chunks only; water already falling in a world stays.
- **0.20.46, Kitchen 0.5.60, no freezing or overheating in heaven:** Andy: "you should not freeze or overheat in
  heaven". At the Other World (`ZFighterAfterlife.inOtherWorld`: within 64 blocks of the Judge's platform in the Aether,
  which covers the hall, road, pavilion, ring and tent) Kitchen's body heat is held neutral (`KitchenCompat.registerTemperate`
  hooks Kitchen's `BodyHeat.addTemperate`). The rest of the Aether keeps its height cold.
- **0.20.47, Kitchen 0.5.61, no corpses in heaven:** Andy: "player corpses dont spawn in the otherworld (heaven) not
  the aether, I died and a zombie tried attacking the judge". At the Other World (`ZFighterAfterlife.inOtherWorld`) nobody
  leaves a body: `KitchenCompat.registerNoCorpseZone` hooks Kitchen's `Corpses.addNoCorpseZone`, so a player who dies there
  drops their hotbar with the rest, and a body already lying there fades (dropping what it held) instead of getting up as a
  zombie. Monsters are still allowed there (Andy: "monsters are fine, since users can train here").
- **0.20.48, Kingdoms 0.7.540, attributes 0-100 by world tier + character screen layout:** Andy (2026-11-04): "UI needs a
  rework, the text is overlapping. And character level points should scale way further, making stats go from 0/100 and
  scale accordingly, giving users a reason to world tier up." See the Kingdoms MODLOG.
- **0.20.49, Kingdoms 0.7.542, Skeleton Lord turned the right way round:** Andy (2026-11-04): "his head is facing the
  complete wrong way". The whole model was drawn back to front (face on his back, ponytail in front); the renderer now
  gives it a half turn. See Kingdoms MODLOG 0.7.542.
- **0.20.50, Chrono cheats death:** Andy (2026-11-04): "I want Chrono to have the strongest potential, starting with the
  ability to cheat death, utilizing [Respawn Recap] ... revert them the maximum, deleting items they gained ... no drops no
  corpse, no death. They can always use a writ or interact with the judge to rewrite their story." Then: "limit it to
  chrono, and they can still be executed", "they have to press R, if they dont they still die", "the Chrono is NOT told
  anywhere that they can revive here, it just is possible during the regular death animation or wakeup or whatever
  happens, they have a window now, no warning", "make it 4s". `power/chrono/ChronoCheatDeath`: once Rewind is learned, a
  checkpoint every 5 s wherever you stand safe (on the ground or swimming, nothing burning or falling, 3+ hearts).
  Nothing is ever shown for it. A death (after the downed state and death by cause have decided) is held back 4 s: the
  player gets the real death screen and message (health 0 sent, held unseen and unhurt at 1 server-side), and R inside
  those 4 s rewinds them. Otherwise (time runs out, Respawn clicked, leaving) the death goes ahead exactly as decided
  (`Downed.letDie`, `Mortality.letThrough` so it isn't rolled again): corpse, drops, final death. Left for dead by death by
  cause, the same 4 s as they go out: R cancels the waking (`Mortality.cancelWake`) and rewinds. The rewind goes back to
  the oldest checkpoint in reach: 1 min, +30 s for each further ability, +30 s per Mastery rank, +2 min Timeless (7 min at
  most). Wounds undone, a final death undone, health (at least 3 hearts), hunger and effects as then; of every item kind
  you keep at most what you carried then (by item, so worn tools still count) and experience never comes back higher,
  while what you lost meanwhile stays lost, so nothing is doubled. Executions (`Downed.execute`: finishers, hostiles', the
  symbiote's, Z Fighters'; now flagged by `Mortality.executing` until the blow is dealt) and /kill still kill once the
  blow lands, but (Andy: "you can still use the R, before the fatal damage like the cancel, and the executioner will be
  perplexed and react in their own way") R works the whole time an execution is coming or under way: you're rewound out
  from under it, a hostile executioner stops over the empty ground, turns about and gives voice its own way
  (`MobExecutions.executioner`, `ChronoCheatDeath.perplexed`; a humanoid one says so too, Kingdoms 0.7.543: "I was just
  about to... kill you?!"), and a player's finisher stops as when anyone is spared. Writs and
  the Judge work as before. Respawn Recap (MIT, DialingSpoon, 1.2.1-26.3, client-only) ships unchanged in packModsOverride
  and is Chrono's alone (`client/mixin/respawnrecap`, @Pseudo): its replay plays only as a rewind (never for a real death,
  and with no respawn at its end), it keeps screenshots only while you're a Chrono, and `client/compat/RespawnRecapPlugin`
  gates its Gui handler so the death screen opens as normal otherwise. Gradle compile only, not played.

- **0.20.51, Kingdoms 0.7.548, Kitchen 0.5.63, guards finish the downed; less blood, more when hurt; no redstone blood:**
  Andy (2026-11-05), on a clip of a guard over a downed zombie: "it seemed as though this guard couldnt execute, and he
  kept losing he weapon. also the blood effects can be toned down, maybe emitting more as the enemy is lower on health
  ... the redstone like particle effect needs to go as well. it does not ever resemble blood." Executions of downed mobs:
  `MobExecutions.FINISHES` names who walks over to a downed mob (after `MOB_GRACE_TICKS`) and kills it with the same
  wind-up and blow as a hostile's on a player, minus the film and shakes; Kingdoms (`PowersCompat.guardsFinishMonsters`)
  sends its guards and archers to downed monsters (and a hotheaded or spiteful one to anyone it's fighting). Kind and
  devout guards no longer spare downed monsters (`Temperaments.spares`). The weapon: Kingdoms' `Rearm` no longer watches
  MCA villagers, whose weapon MCA itself hands out each fight (often a fresh one, not from the pack) and puts away between,
  so every pause cried "My weapon!" and sent the guard off after swords on the ground. Blood: a blow's or shot's drops are
  0.3x on someone at full health rising to 1.2x at none (`BladeBlood.hurtShare`, downed counts as none), the cap 10, and
  under 40% health anyone near a player drips now and then, leaving a trail. The plain drop sprite and the still fleck
  (both a scatter of single pixels, i.e. redstone dust) are gone: sprays pick only spatters, spurts and swipes, and a fleck
  comes as a small spatter for three in ten (`BloodClient.FLECK_KEEP`); `BloodFleck` removed. Kitchen's redstone-block and
  red-dust blood (corpse cuts, gibs, scavengers, the chopper, hangings, grates) is Powers' blood through `KitchenBlood`,
  and Kingdoms' sharks and great whale and the Aerwhale bleed the same. Andy (00:23Z): "skeletons currently drop bone
  pngs when hit, they should be debris splinter particles if anything": those were Subtle Effects' (entities config,
  damage taken), switched off by `compat/SubtleEffectsCompat` (its two public fields, at server start and every 10 s on
  the client since the server's config syncs on join); a struck skeleton or skeleton horse throws bone-block chips
  instead (`BladeBlood.splinters`). Gradle build only, not played.

- **0.20.60, the Aether hidden until you're past the clouds:** Andy (2026-11-05), on a clip looking up from a hillside:
  "The aether should NOT be visible so easy from ground level especially, maybe it only reveals once you are past the
  cloud layer?" In a One Dimension world the Aether band is ordinary terrain over the Overworld's sky, so its islands'
  undersides showed from anywhere. `client/aether/AetherVeil` reads the Aether band's floor and the Overworld band's
  shift from CustomChunks' synced `StackLayout`, and while the camera is under the Overworld's clouds (192.33 + shift;
  it hides below the clouds' bottom and shows above their top, so it flips inside the cloud) nothing at or above that
  floor is drawn: Sodium's culling frustum gets the floor as a ceiling (`client/mixin/sodium/AetherVeilViewportMixin`
  wraps every `Viewport`'s frustum in `SodiumVeil`, Iris' shadow pass included, and asks Sodium to cull again when it
  flips), and the game's `Frustum.isVisible` leaves out anything whose box starts at or above it (mobs, aerwhales,
  item drops). Ordinary worlds and the separate Aether dimension are untouched. Sodium is now `compileOnly`, behind an
  optional mixin config. Gradle compile only, not played.

- **0.20.53, Kitchen 0.5.64, downed animals fall on their side:** Andy (2026-11-05): "downed animals should fall to their
  side, i think they're trying to animate like humanoids". `BodyPlan` took any untagged mob taller than 1.3x its width for a
  person, so Cube Animals' roe deer (0.9 by 1.5), red deer, reindeer, moose, antelope and elephant, Kingdoms' moose and the
  Moa went down on their backs like a man and were hurt in arms and feet. Wildlife (creature, ambient and water categories,
  `BodyPlan.wild`) is never guessed a person now; tagged humanoids such as the wandering trader still are. Cube Animals'
  kinds are tagged too (quadruped, flyer for eagle/barn owl/toucan, arthropod for the cricket, simple for its fish and
  snail). Kitchen's `RagdollShapes` makes the same call for corpses (four-legged ragdoll, or a box if slight), and its
  ragdoll_quadruped tag lists Cube Animals' big beasts. Not played.
- **0.20.54, rain puddles that show, rain washes blood:** Andy: "rain doesn't seem to be pooling water" and "rain should
  wash away blood". `client/wet/WetClient`'s rain puddles (0.19.63) only pooled under trees, beside trunks, or a fifth of
  the time in two-sided hollows, and grew only when a random try landed on the same block again, so an open field had next
  to none. Now flat open ground pools too (`FLAT_CHANCE` 0.1, never a ledge or sand), hollows at 0.6, and every puddle the
  rain still reaches is fed every 5 s (`feedRain`) so it grows full and stays until the rain stops. Blood: the server's
  stains shrink by `BloodTrail.RAIN_WASH` (0.03 a half-second) under open rain (`BloodTrail.wash`; a big pool goes in
  about half a minute, prints in a second or two), and passing pools and wall runs (`BloodPuddle`, `BloodWallMark`) fade
  over 5 s once rain falls on them. Bodies already washed in rain (`Bloodied.RAIN_WASH`).
- **0.20.55 (Kitchen 0.5.65), corpses stay bloodied:** Andy (2026-11-05): "the corpses should stay bloodied, if they were
  upon death the blood stains should just stay drawn and dry like normal". Kitchen's new `Corpses.ON_BODY` hook hands
  each new body (mob or player) to Powers, which copies the dead body's blood coat onto the corpse as
  `Bloodied.CORPSE_BLOOD` (amount, colour, laid now; saved with the corpse, synced). It never wears off and darkens over
  `DRIES_IN` (5 min) like blood on gear. Drawn through Kitchen's new `CorpseDressing.BODY` hook (mob bodies and MCA
  looks, by the usual `BloodCoatLayer`) and `EXTRACT`/`SUBMIT` (a dead player in plain skin). Rain and water wash a
  corpse's blood off at the living body's rates (Andy: "rain should wash away blood"). Gradle build only, not played.
- **0.20.56, Kingdoms 0.7.549, Big Cannons booms:** Andy liked Create Big Cannons' screen shake and sounds. Its code is MIT
  and its sound files carry no licence, so these are our own code and our own sounds (synthesised by
  `tools/gen_blast_sounds.py`). `client/sound/Blasts` (hooked at `SoundEngine.play` by `BlastSoundEngineMixin`): every
  blast sound (the explosion sound from any mod, the dragon fireball, our cannon report) reaches you at 17 blocks a tick,
  so a far blast flashes first and its boom and shake come a beat later. OpenAL air absorption dulls its high end with
  distance; past 20 blocks a long low rumble (`blast.far`) rolls in, heard up to 150 blocks (260 for the biggest), and
  within 40 a sharp crack with a patter of debris (`blast.crack`) sits on top. An explosion shakes by its size, any
  other blast sound by how loud it was played, both when the sound arrives; server shakes from beyond 24 blocks wait for
  it too. `client/sound/ShellWhistle`: cannonballs, the Arsenal cannonball, demolition bombs and anything whose id says
  cannonball, shell, mortar, bomb, meteor or boulder whine as they fly past, louder the faster, rising in pitch as
  they come at you. `/shake <0-200>` or `/shake off` sets each player's own shake strength (kept in
  `config/jaymospowers-client.properties`). Bodies blown apart (Kitchen gibs) give a small shake of their own.
  Compile-checked; not played.

- **0.20.57, Kitchen 0.5.66, small critters go like fish:** Andy (2026-11-05): "crickets and any new small animals should
  work like fish, if they drop an item the corpse disappears." Crickets, mice, snails, butterflies, bees and any other
  creature kind under 0.5 blocks both ways (or in Kitchen's new `small_corpses` tag) are looted in one click and gone,
  like fish. Rabbits, chickens, frogs and babies still leave a body. Not played.
- **0.20.58, Impale throws the spear itself:** Andy (2026-11-05), on the Iron Spear's Impale (RPG Series): "using this
  should actually throw your spear". The spear now leaves your main hand when Impale flies (`compat/SpellThrows`,
  through `SpellEngineCompat`; Spell Engine's own projectile still does the charged damage and knockback). Where the
  spell ends the spear carries on as a real `ThrownWeapon`: it sticks in a block it hits, glances off a creature and
  drops at its feet (no second hit), or arcs down after its full range, and lies there for its thrower to pick up like
  a trident (1 durability per throw). Never lost or doubled: if the spell ends somewhere unloaded it goes back to your
  pack (or your feet); on server stop it's put down where it was. In creative it stays in hand and the thrown one is
  creative-only pickup. Gradle build only, not played.
- **0.20.59, Skitter's spider legs and wall climbing:** Andy (2026-11-05): "skitter and the spiders need the same legs,
  and you also cant climb any surfaces in spider mode". Skitter's spider shape now has the pack's spider legs (Kingdoms'
  `SpiderLegsMixin` on the game's `SpiderModel`): the game's leg rest poses and step (both sides on the same beat,
  mirrored, gait 0.6662), each leg kneed (8-long thigh + child shin, `KNEE_LIFT` 0.45, `SHIN_BEND` 1.0, `STEP_BEND`
  0.9), the same idle twitch and abdomen breathe/sway; Skitter keeps its sneak crouch, bite rear and wall-climb leg
  run. Climbing: the server used to push it up a wall, which the client's own movement overruled, so nothing climbed.
  New `SkitterClimbMixin` makes `onClimbable` true on both sides for a Skitter pressing into a wall (or in the air
  beside one), like a spider: walk in or hold jump to climb, sneak to hold still, let go to slide slowly down. The
  server keeps the wall pose (`XenoformState.CLIMB`) and the step sounds. Ceilings had the same fault (Andy: "they
  didnt work"): the cling is now held by the clinging player's own client (`SkitterCling`, before each move), same rules
  as before; the server keeps the `CEILING` state everyone draws. Gradle build only, not played.

- **0.20.60, Kingdoms 0.7.551, Bulwark's four arms take turns, lower cutscene cameras:** Andy (2026-11-05): "attacking
  with bulwark should alternate between all arms, same with the combo attack. and all the cutscenes should be a little
  lower. you cant see the user well." Every client counts each Bulwark's swings (`XenoformClient.BULWARK_ARM`), and each
  new swing (a plain attack, a Fist Combo blow, the clap, a throw) is thrown by the next arm in turn: upper right, upper
  left, lower right, lower left; Titan the same. The combo's blows also alternate hands in first person instead of
  picking at random. Every `UltimateCinematic` shot (abilities and ultimates; not the change-in morph film, already at
  body height) and every beam-clash shot sits `SHOT_DROP` (0.6 blocks, times the caster's size) lower, never under
  `SHOT_FLOOR` (0.5) over the caster's feet; the crane rises to 7 blocks, not 9. Kingdoms' spirit-clash camera sits at
  0.9 over the pair's feet (was 1.5) and the handshake's wide shot 0.2 over the hands (was 0.5). Not played.

## 0.20.61 (2026-11-05): leg cleaves and waist cuts

- Andy: "is it possible to cleave both legs off once I'm strong enough? ... it should proc more often on zombie humanoids
  than anyone else", then "why not splitting a torso clean off too". `Dismember.cleave`: when an axe or greatsword blow
  of 10+ damage cuts off one leg (the usual overkill), it goes through the other leg too at 25% (zombies 60%), times
  Grim Hand and Rubber-folk. `Dismember.halve`: an axe or greatsword blow of 16+ to the torso (a player's aim, a mob's by
  chance) that kills or downs may cut the body in half at the waist, both legs off at once, at 20% (zombies 50%); a halved
  body dies instead of going down (`Downed`, as a beheading). A zombie can be halved by any such blow and crawls on.
  Tuning at the top of `Dismember` (CLEAVE_*, HALVE_*). Andy (01:02Z): "rubber folk can survive any of these
  dismemberments ... as long as they absorb within 10s of a torso chop, they will start to regenerate. it is slow": a
  halved Rubber-folk goes down instead (`Downed`), tagged `jaymospowers_rubber_split`; no swallow within 10 s
  (`Dismember.SPLIT_TICKS`) and they die (`Dismember.tickSplit`). Kingdoms' swallow swaps it for
  `jaymospowers_rubber_mending`; no one gets up while a leg is still missing (`Dismember.pulledApart`, `Downed.getUp`).
  Beheading still kills. Gradle build only, not played.

## 0.20.63 (2026-11-05): shift-scroll ledge grab

- Andy: "shift scrolling infront of a ledge or single block opening will grab onto it when you get to crouch height,
  this should prevent the other checks and issues trying to use this feature. this should also let you climb fences,
  or other blocks that arent quite 2 blocks tall." The crouch grab now comes only from Sneak + a scroll down (the notch
  down to the crouch, or on from it) while on the ground facing a block 0.7 to 1.95 over your feet
  (`StanceScroll.scroll` -> `LedgeClient.scrollGrab` -> `Ledge.crouchSpot`); walking into the block, settling into the
  crouch or pushing forward from the knee no longer grabs. What it does: climbs up onto the top when the crouched body
  fits there, pulls in on the belly under a one-high gap (as before), or vaults over a fence, wall, gate or bars and
  lands on the far side (needs ground within `Ledge.VAULT_DROP_MAX` = 3 blocks). The notch is the climb's, so the stance
  stays as it was. Cactus, magma and barriers are never climbed (`data/jaymospowers/tags/block/ledge_no_vault.json`).
  `Ledge.Grab` carries a vault flag so the server holds a vault by `Ledge.vaultable` instead of `holdable`. Gradle
  build only, not played.
- **0.20.64, explosions disconnected you:** every explosion sound threw a NullPointerException in `Blasts.onPlay`
  (0.20.56), which runs at the head of `SoundEngine.play` and called `getVolume()` before the engine resolves the sound
  (`getVolume` reads the resolved `Sound`). Thrown inside `handleExplosion`, it dropped the client with "Network Protocol
  Error" whenever a creeper or TNT went off nearby. `onPlay` now calls `getOrResolve` first (cached, so the engine plays
  the same variant). Checked against the 26.3 client jar; not built here.

- **0.20.65, zoom on Sneak, dodge on Alt** (Andy: "double tap and hold shift should be default zoom, and alt should be
  roll"): tap Sneak, press it again within 6 ticks and keep holding, and OK Zoomer zooms until you let go
  (`client/SneakZoom` holds OK Zoomer's Zoom key down by name, so its smooth zoom, scroll steps and settings still
  apply). The double tap no longer dodges; the dodge is Left Alt only (`DodgeClient`). OK Zoomer's own key came off
  Left Alt (and C) once per install (`KeyConflicts` v7), so Alt was zooming and dodging at once before this. No zoom
  while clinging, bar-hanging, sliding or from Web's double-Sneak yank; a fast run's held Sneak is still the slide.
  Built, not played.
- **0.20.66, blood on bodies sat under the skin; stains looked like they splattered:** Andy's clip (2026-11-04) showed
  blood on a player only through the gaps in their sleeves and jacket. `BloodCoatLayer` painted the base layer only, and a
  player's downloaded skin couldn't be read at all, so it got a plain whole-skin mask. `ArmourGrimeTextures` now reads a
  downloaded skin from its `DynamicTexture`, and for a body in the player's layout (`PlayerModel`: players, MCA folk)
  paints each outer-layer pixel (hat, jacket, sleeves, trousers) with the blood of the body pixel under it (`under`), so it
  lies on top of the clothes. The "splattering" stains were the viewer's own cut dripping from chest height past the
  first-person camera, landing in view right on the trail ahead: `BloodDrop` no longer draws a drop falling within
  0.45 blocks (sideways) under a first-person camera. Its stain still lands; others still see the drips. Built.

## 0.20.67 (2026-11-05): blasts blow bodies apart

- Andy: "explosions (dependent on proximity) have a higher chance to gib mobs and players on death and dismember
  survivors." New `core/BlastGore`: while a vanilla blast hurts things (`BlastGoreMixin` on `ServerExplosion.explode`)
  it knows the centre and radius, and each body's closeness is 1 minus its distance over twice the radius (vanilla's
  reach), times the root of radius/4 (TNT counts in full, a ghast fireball about half). Other mods' blast damage with no
  vanilla explosion uses damage/40 instead.
- **Killed by it:** gibbed at 0 below closeness 0.25, rising to 95% at the centre (`GIB_FROM`, `GIB_MAX`). A person's head
  and every limb fly off from the blast (`Dismember.blastOff`, quiet blood: 6 flecks a part, no extra sounds; skeletons
  shatter instead), and Kitchen bursts the mob's body into chunks (tag `jaymoskitchen.gib`; `jaymoskitchen.no_gib` keeps
  it whole, replacing Kitchen's flat 12-damage rule). A gibbed player leaves just a torso corpse and respawns with only
  the parts they had lost before the blast (`Dismember.restore`). Not gibbed: 60% of the survivor chance to lose a limb
  onto the corpse.
- **Downed rules kept:** a humanoid the blast would kill still goes down unless it gibs them (`Downed`, checked before
  the halving). A gibbed Rubber-folk loses all four limbs but keeps the head and goes down split, mending with a swallow
  within 10 s as after a halving (`Dismember.split`). Beheading still kills them.
- **Survivors** (standing or just downed, 3+ damage): lose a random arm or leg at 0 below closeness 0.3 rising to 50%
  (`LIMB_FROM`, `LIMB_MAX`), a second at 40% of that; never the head. Rubber-folk x2, Will's Grim Hand for whoever is
  behind the blast. Tuning at the top of `BlastGore`. Gradle build only, not played.
- Andy, of royals being gibbed: "noone but stoneborn are exempt". Stoneborn (Kingdoms tag
  `jaymoskingdoms_heritage_stoneborn`) are never gibbed (`BlastGore.stone`); they can still lose limbs. Everyone else,
  royals included, can be.

## 0.20.68 (2026-11-05; Kingdoms 0.7.557, hint 101): armour wears fast, absorbs more, breaks without vanishing

- Andy: "armor should have far less durability across the board, absorbing more damage from attacks, but when it does
  break it doesnt disappear, it remains repairable but you start taking full damage ... players and npc alike. axes and
  other weapons like maces deal more armor damage ... repairs are not possible on the battlefield."
- `core/ArmourWear` (tuning at the top): every humanoid armour piece (any mod's, not elytra) keeps 30% of its durability
  (`DefaultItemComponentEvents`); worn armour counts 1.4x armour and 1.25x toughness against a hit (vanilla's 80% cap
  stays); axes, maces and `Fractures.blunt` weapons wear it 2.5x faster.
- At 0 a piece stays at full damage (`ArmourWearStackMixin`): vanilla already skips a broken stack's attributes, and
  `ArmourWearEnchantMixin` quiets its enchantments. Tooltip says "Broken: repair it". Forged (graded) pieces keep
  `Wear`'s own Broken rule.
- NPCs' armour now wears too (vanilla `LivingEntity#hurtArmor` is empty; `ArmourWearLivingMixin`). With no anvil, a
  mob mends 10% a piece every 5 s once 30 s out of a fight with no target (`ArmourWearMobMixin`).
- In a fight (hurt or hitting within 10 s, or a hostile mob after you within 16 blocks): the anvil won't hand over a
  repaired armour piece, and Mending takes no XP. Grindstone and crafting-grid combining are not blocked.
- Old worlds: forged pieces baked before this keep their old maximum until re-baked. Gradle build only, not played.

## 0.20.69 (2026-11-05): rocks barely hurt zombies and Hornkin
- Andy: "Rocks should deal .5 damage to zombies and pigmen/hornkin". A thrown rock hits every zombie (the game's `Zombie`
  family: husks, drowned, zombie villagers, zombified piglins, plus MCA's and Kitchen's zombies) and every Hornkin
  (`jaymoskingdoms:hornkin*`, `zombified_hornkin`) for 0.5 instead of 3 (`ThrownRocks.SHRUGGED_DAMAGE`, `shrugsOff`); an
  unsuspecting one can still be knocked out. Kingdoms' NPC rocks follow the same rule (Kingdoms 0.7.558). Not played.

## 0.20.72 (2026-11-05): pack bump for Kingdoms 0.7.561

- Kingdoms 0.7.561: a downed king lifted by a player no longer gets a fresh set of plate each time his armour is shed
  (the "duplicating" royal armour). No Powers code changed.

## 0.20.73 (2026-11-05): one tool or weapon on the body at a time
- Andy: "only one tool or weapon should show at a time, in whatever slot". YDM's Weapon Master hung every hotbar item on
  the back and belt at once. `client/mixin/weaponmaster/WeaponMasterOneItemMixin` (optional, skipped without it) lets
  each body's pass of its layer draw only the first item it reaches, the lowest hotbar slot not in hand, for your own
  body and other players'. Its quiver still shows. `MAX_SHOWN` at the top. Gradle build only, not played.
- **0.20.74, Shift + Left Alt zooms** (Andy: "shift + left alt should make you zoom"): holding Sneak and the Dodge key
  together zooms with OK Zoomer until either is let go (`SneakZoom`), beside the double-tap-and-hold Sneak. Alt pressed
  while Sneak is down no longer dodges (`DodgeClient`); Alt alone still rolls. Built, not played.

## 0.20.75 (2026-11-05): NPC speech bubble fix, orc lines
- Pack bump for Kingdoms 0.7.562: haven guards' rally cry showed a raw lang key instead of the line; villagers, guards
  and goblins now greet, warn off or shout at the orcs they meet (OrcSightings). Not played.

## 0.20.76 (2026-11-05): weapons parry again
- Andy: "parry doesnt seem possible with weapons anymore, is that because of the weapon arts?". Not the arts (the skill
  bar leaves right click on a guarding weapon). Two other holes:
  - Right click on a foe in reach opened MCA's chat (orcs, guards, bandits are MCA villagers), so the guard never went
    up. With a guarding weapon in the main hand, right click on a foe (a monster, a mob that's swinging at you, the one
    you're locked onto) now raises the guard; sneak + right click still talks (`Guard.guardsAgainst`, `GuardOverTalkMixin`
    on `Player.interactOn`). Fabric's right-click-on-someone handlers run before it, so executions, reviving, paying off
    and the rest come first.
  - Tool Station weapons (swords, weapons, tools) never had the guard: their item class has its own right click, which
    `Guard.guardable` skipped, and `ToolStats` stripped it. They get it now (also keeps right click off the skill bar's
    first form for them); ones already made pick it up when carried. A Tool Station dual-wield dagger in the off hand still
    swings on right click. Broken ones don't guard. Not played.
## 0.20.77 (2026-11-05): Kingdoms 0.7.565, no fighting the unseen

- Packs Kingdoms 0.7.565: NPCs stop swinging at a werewolf's hidden person and fight the beast; a Quiet One orc is seen
  again once his keeper is gone. Not played.

## 0.20.78 — the Judge always offers rebirth
- Andy: "you should always be able to reset your character at the Judge, the option shouldnt be greyed." The Judge's
  "be born again" (`ZFighterAfterlife.talk`) was greyed unless you were one of the dead (`among`) and `mayBeReborn` held
  (not a visitor who came alive, not a life already ended). Now it is always open: the dead are let go and born again as
  they wake; a living visitor is born again on the spot (`reborn`, Kingdoms' `LifeStory.reincarnate`).
- **0.20.79, Shift + Left Alt zoom, take two** (Andy: "shift + alt doesnt seem to be zooming", while double-tap-and-hold
  Shift did): `DodgeClient.keyDown()` now also reads Left Alt (and the Dodge key's own key) straight off the keyboard
  state, so the zoom no longer depends on the Alt key event reaching the key mapping while Shift is down. Built, not played.
- **0.20.80, Kitchen 0.5.69, no zombie players in heaven, take two** (Andy: "the zombie players still reanimate in heaven,
  theyre supposed to disappear"): the pack shipped `libs/jaymos-kitchen-0.5.57.jar`, older than Kitchen 0.5.61's Other
  World rule, so any pack built without swapping that jar by hand had bodies lying and rising at the Judge's hall. The
  pack tasks (`modpackMods`, `curseforgePack`, `serverMods`, `profileMods`) now build Kitchen from `kitchen/` and ship
  that jar (`buildKitchen`, like Kingdoms); `libs/` is only compiled against and run in the dev game. Powers now logs a
  warning if Kitchen has no Other World rule. Kitchen 0.5.69: a zombie player already standing in the Other World fades
  in souls, dropping what it held and carried. Built, not played.
- **0.20.81, splinter flint in your hands** (Andy: "you should be able to shift click flint in mid air with a rock off
  hand to splinter it without causing a fire"): with flint and a rock in your two hands, sneak + use splinters one flint
  into two shards (as knapping it on a block does), whether you aim at the air or at a block, and never strikes fire; the
  rock is kept. Plain use on a block still strikes fire as before (`FirstTools.splinter`). And (Andy: "you also should
  only be able to throw rocks from the offhand, and after a small charge. it no longer places unless you have it in the
  main hand"): a rock in the main hand only sets down and is never thrown; a rock in the off hand is never set down, and
  is thrown after holding use for half a second (`ThrownRocks.CHARGE`, 10 ticks), at the air or at a block, with a soft
  wind-up sound as the hold starts. Built, not played.

## 0.20.82 (2026-11-05): coin purse hangs outside the hip
- Andy: "they dont hang, ON the hip they are inside". The worn coin purse hung 3.4 pixels out from the body's middle,
  inside the leg. It now hangs at 4.9 (a pixel further over leggings or a chestplate), and swings out freely but only a
  little in toward the leg (`WornLanterns.PURSE_X`, `PURSE_OVER_ARMOUR`, `PURSE_ROLL_IN`). Kingdoms 0.7.568 goes with it.
  Built, not played.

## 0.20.83 (2026-11-05): two sure wakes, then Reincarnate starts a new life
- Andy: "you should always get a black screen wake up roll for your first two deaths ... when jung was shot he instantly
  got the respawn screen". That was the death-by-cause roll: shot by a skeleton while downed, 50% final, and it lost. Now a
  life's first two deaths (`Mortality.SURE_WAKES`, counted in `Mortality.WOKEN`) are always left for dead (or turned):
  the black screen, then waking out in the wild hurt. Only losing your head, the void, or an execution by a player or a
  boss can end it sooner. From the third death on the cause rolls as before. A Chrono rewind of a wake gives it back.
- "can you change that button to say reincarnate instead of Respawn": a final death's button reads Reincarnate
  (`DeathScreenReincarnateMixin`; `Mortality.FINAL` is now synced to its player).
- "you should get reprompted completely, like with a new character creator, new quests skills, etc. EVERYTHING resets":
  `Mortality.wipe` takes every Powers/Kingdoms/Kitchen attachment off the player as the new life starts (skills, trees,
  levels, quests, reputation, world tier and knowledge, class, heritage, story, patron, curses, mastery, affinity, the
  Z Fighter's training, missing limbs...). Kept: the roads and heritages their deeds unlocked, and what holds items
  (backpack, baubles, stashes, worn costume, post, crew) (`Mortality.OUTLIVES`). Then Kingdoms 0.7.573 opens MCA's creator
  anew and retells the life story (`LifeStory.reincarnateAnew`). The Judge's rebirth is unchanged.
- Andy: "what about the other possibilities? kidnapped, taken in by a village or nearby villagers", then "yes add this":
  a new wake, taken in (`Mortality.SHELTERS`, asked after the captors; Kingdoms fills it). They wake on a bed in the
  friendly settlement, with every wound bandaged and every break splinted (`Treatment.tendAll`), line "You wake to low
  voices and the smell of herbs." Built, not played.

## 0.20.84: carcasses drain where you can see them (Kitchen 0.5.70)

- Andy, on Jung's deer clip: "the deer was clipping through the butcher table, i didnt see any blood draining either".
  Kitchen 0.5.70 bleeds a carcass on the ground or a table from its throat, beside the body, instead of from under it,
  so the drops and Powers' pooled stains show. The deer clipping was the stale Kitchen 0.5.57 the pack shipped before
  0.20.80 (deer got a man's ragdoll, fixed in Kitchen 0.5.64 and 0.5.68). Built, not played.
## 0.20.85 (2026-11-05): seven-mods picks, wave 1
- Andy's picks from the "Seven mods" picklist (Better Advancements, Worldbound and others), all shipped unchanged:
  Better Advancements and Fancy Toasts, It Takes a Pillage, Mutant Monsters, Eternal Nether, Moog's Nether Structures
  and Moog's Mineshafts Reimagined, Epic Structures: Witch Huts, Hopo Better Ruined Portals, Particle Rain, Biome Blends,
  Crops Love Rain, Macaw's Doors, Diagonal Windows, Beautify: Refabricated, Lanterns Belong on Walls, Simply Tents,
  Critter Armory, Kaffee's Dual Ride and RightClickHarvest (with JamLib). Mineshafts Reimagined and RightClickHarvest
  have no 26.3 file on CurseForge yet, so they're in `packModsNoCf`.
- Mutant Monsters, zombies only (Andy: "any zombie but only if they have the model"; the Mutant Zombie is its only
  zombie): Powers' data adds every Overworld, Nether, End and Aether biome to its `without_mutant_creeper/skeleton/
  enderman_spawns` tags, so only the Mutant Zombie spawns on its own. Chemical X can't be had (Andy: "chemical X should
  not be acquirable"): its four brewing recipes, the only way to make it, are dropped (`Uncraftable.PREFIXES`).
- Built, not played.

## 0.20.86: a guard whiffing at a foe out of reach; cut drips out of the chest (Kingdoms 0.7.577)

- Jung's clip, Andy: "the blood was clipping and not following the model", then "the NPC was trying to attack, it kept
  swinging and activating the hit sound ... not dealing any damage nor did the skeleton take any". The white burst at
  the villager's chest was its attack tell glint (`AttackTells`, 0.20.4), bloomed by his shaders, once per swing. MCA's
  melee task asked for a blow from further off than a blow reaches, and `MobCombat.plainBlow` (a weapon Better Combat
  doesn't know, or fists) started the swing anyway, so a guard after someone past a nearer body (it was shouting
  "Trespasser!") glinted and whiffed (`PLAYER_ATTACK_NODAMAGE`) over and over. `plainBlow` now starts only at a foe in
  reach and sight, as `attackTick` already did (Kingdoms 0.7.577).
- A cut's drip (`BladeBlood.drip`) left from inside the body and, since 0.20.51, opened into a spatter star there. A drip
  now leaves from just outside the body's box (`DRIP_OUT`) and the client draws a no-stain spray as one small falling drop
  (`BloodClient#DRIP_DROP`, `BloodDrop.drip` held on its sheet's first frame). Built, not played.
- **0.20.87, GeckoLib for Simply Tents:** the Deck's server refused to start ("Incompatible mods found": Simply Tents 3.5
  needs GeckoLib 5.5.6 or later, and 0.20.83 shipped it without). Added GeckoLib 5.5.7 for Fabric 26.3 (Modrinth
  kSxHvs99, CurseForge 388172/8945630, same file) to packMods. Not built here.

## 0.20.88 (2026-11-05): the "Jaymo's Powerpack" advancement tab (Kingdoms 0.7.579, Kitchen 0.5.71)
- Seven-mods plan, patch 3. Andy's note: all of our advancements on one screen, and a line made for the pack. Until now
  our mods had only recipe unlocks. There's now one tab, `data/jaymospowers/advancement/pack`, which lays out on Better
  Advancements' big map.
- The main line follows a playthrough: A Life Begun (life story told) > Journeyman (a job's first rank) > Sworn (a
  patron) > Remnant (within 48 blocks of a monolith) > Giant-felled > Above the Clouds (entering `jaymospowers:aether`)
  > Not the Last (two kinds of Giant) > The Last Giant (all four kinds; Better Advancements shows the count).
- Side branches: Awakened (a power), Home Cooking and Cooked to Perfection (Kitchen), Cast Off (aboard a ship) and Thar
  She Blows (a great whale), First Insight (alchemy research), Forged (a Tinkers tool in hand), Executioner (a finisher).
- Titles are plain and descriptions only hint; no lore is stated. Most criteria are `minecraft:impossible`, awarded from
  code by id: `core/PackAdvancements` (power, research, tools; `Downed.execute` for executions), Kingdoms'
  `PackAdvancements` (story, rank, patron, monolith, ship, whale and Giant deaths; at a Giant's fall everyone within 96
  blocks gets it), and `Kitchen.award` for dishes.
- The tab's background is a dark tile in our panels' colours (`textures/gui/advancements/backgrounds/pack.png`), with
  an Excalibur-graded copy (grade.py's new `EXTRA` list keeps it on rebuilds). The vanilla frames are left alone so the
  other tabs don't change. Built, not played.

## 0.20.89 (2026-11-05): underground bits: ore tells and arrow heads (Kingdoms 0.7.580)
- Seven-mods plan, patch 4. These are Caverns & Chasms' ideas (Abnormals licence, so ideas only), built with our own code
  and art.
- **Ore tells** (`ground/OreTells`, `worldgen/placed_feature/ore_tells.json`): once a chunk, stone touching an ore vein
  is often Seamed Stone (Seamed Deepslate in deepslate and tuff), cracked with rusty veins. Where a vein lies 2 to 12
  blocks under dirt or grass, the ground over it turns to Rocky Dirt. They drop cobblestone, cobbled deepslate and dirt;
  silk touch keeps the block. Excalibur-graded copies are made.
- **Arrow heads** (`tinkering/ranged/ArrowHeads`): there are no new arrow items (Andy's note). A Tinkers arrow's head
  decides:
  - **Blunt** (a wood or rock head): it deals half its damage and never the last of someone's health. It knocks back,
    dazes (Slowness II and Nausea) and bounces off. Like a thrown rock, it can knock out someone who didn't see it
    coming (40% on the head, 12% elsewhere, 10 to 30 s).
  - **Large** (a pig iron, steel or star metal head on a bone or blaze rod shaft): it hits 1.4x as hard and leaves the
    bow at 0.75x speed, so it drops sooner.
  - The arrow's tooltip says which it is. `ThrownRocks.unsuspecting` is now public.
- Kingdoms 0.7.580 adds the mice and the mime. Built, not played.

## 0.20.90 (2026-11-05): the rock throw winds up and lets go
- Andy: "the player's rock throw should have an animated windup and release". While you hold use with a rock in the
  off hand, the throwing arm draws up and back over the shoulder, the body turns that side away and the other arm
  reaches out at the mark. It holds there, trembling a little, until the rock goes. Then the arm whips over and through
  across the body, the other arm swings back, and the pose eases out over a short follow-through. Letting go early
  lowers the arm again.
- In first person the rock is drawn back up beside your head and thrown forward out of view. The hand dips and comes
  back up (with the next rock, if you have one).
- Andy: "change the 3s charge to 1.5s". The charge is now 1.5 s (`ThrownRocks.CHARGE`, 30 ticks). It was set to half
  a second, but the server counted it from the client's repeated use, and at the air the client never sends a use that
  does nothing on its side, so the charge only ran when you aimed at a block and felt much longer. Now your client counts
  the hold and tells the server when the rock goes (`ThrownRocks.Charge`); the server checks it against its own count.
- Everyone sees the pose (`ThrownRocks.THROW`, drawn by `client/armory/RockThrowPose`). Your arm moves the moment you
  press, and the rock leaves as the arm comes over. Tune it in the constants at the top of `RockThrowPose`.
  Built, not played.

## Powers 0.20.92: Chrono cheat death works when hurt, downed or out cold
Andy: "chrono cheat death did not work. i was spamming R at the get up screen" (clip: downed, out cold, left for dead,
then a final death, R pressed throughout).
- Checkpoints needed three hearts or more, so after a long fight at low health (or with wounds holding your most health
  down) there was nothing to go back to, and every death and waking silently went ahead. Checkpoints now ignore health
  (the rewind still gives back at least three hearts). With none inside the window it takes the newest older one, and
  with none at all you rise where you fell, mended.
- Downed, R now cheats death whenever Rewind itself can't answer: out cold, on its cooldown, or an execution coming.
  Awake with Rewind ready, R is still the plain downed Rewind. (`ChronoCheatDeath.downedOpen`, `ChronoPower.rewindReady`.)
- Still no prompt anywhere (Andy's rule); R is read straight off the keyboard, so it works under any death or black screen.

## 0.20.93 (2026-11-05): orc cutscenes you can read (Kingdoms 0.7.582)
Andy: "you dont get enough time or visibility on the orc cutscenes to view what they say, remove any FOV modifiers for
the cutscene duration, (I was an overclocked chrono and it was way zoomed out) and pan into the text box and character,
sacrificing the view of the character near the end to get high enough to view the whole chat bubble."
- New film shot, `UltimateFilm.SPEAKER` (drawn in `UltimateCinematic.speaker`): from over your shoulder in to the
  speaker's face, seen from your side; a slow push framed between their face and the words over their head; then, from
  62% of the way through, up level with the words so the whole bubble is in frame and the face drops out of the bottom.
  Tune it with the `SPEAKER_*` constants at the top of `UltimateCinematic`.
- While your own film runs the field of view is your plain FOV setting (`mixin/UltimateCinematicFovMixin`): no
  Overclock/Speed/sprint widening, zoom or under-water squeeze. It applies to every film, not only the orcs'.
- A film may now run up to 10 s (`UltimateFilm.MAX_TICKS` 100 to 200) so a spoken line can be read through.
  Built, not played.
## 0.20.94 (2026-11-05): reincarnation fixes: no wall, nothing kept, black till ready, one Judge
- Andy: "the judges keep multiplying for some reason, and reincarnating just spawned me in a wall, it should wipe
  absoluteley everything on your character, and black screen until it's ready". From his clip: a final death still went
  to the Other World; the Judge's "born again" sent him home to a spot inside a wall while his life was still over, he
  suffocated, died for good again and was sent back to the hall, round and round.
- A final death no longer goes to the Other World (`ZFighterAfterlife` AFTER_DEATH/AFTER_RESPAWN): Reincarnate respawns
  you and the new life starts. Going home from the hall never puts you inside blocks any more (`Mortality.safe`).
- Black until it's ready: from the respawn until Kingdoms says the new character is made (story, MCA's creator,
  heritage, class: `LifeStory.making`), the screen is black (`Mortality.REBORNING`, drawn by `DownedHud`; 10 min at most).
- Everything goes: every Powers/Kingdoms/Kitchen attachment with no exceptions (unlocked roads and heritages, backpack,
  baubles, stashes, costume are no longer kept), plus inventory, ender chest, experience, effects and spawn point.
- The Judges: the hall looked for its people as soon as the chunk's blocks were in, before its creatures loaded, so a new
  Judge (and master, teller) was made at every arrival. Now only once the creatures are loaded, and extras are sent away.
  Kingdoms 0.7.583 goes with it.
- Andy: "the red overlay on the respawn screen should be black as well, the screen should cut to black whenever you
  die, and the slow entry of either the reincarnate screen or the scenario will load, this will give you a chance to
  read what happened as it should say if you were kidnapped or in the wilderness". The death screen is black, not red,
  and its words and buttons come up slowly out of the black (`DeathScreenBlackMixin`, 2.5 s). A death turned away goes
  black at once and says what happened large in the middle (going dark, then the wake's own line: in chains, taken in,
  out in the wild by cause...; `Mortality.WAKE_LINE`), and the world comes back slowly (`DownedHud.WAKE_OUT_TICKS`, 4 s).
  A new life's black says "That life is over. Another begins." Built, not played.

## 0.20.95 (2026-11-05): cut in two, you bleed out (Kingdoms 0.7.584)
Andy: "I also didnt bleed out after being cleaved in two, that should happen within 10s".
- A halving that left health over (the chop wasn't the killing blow) just took both legs and you lived on. Now anyone cut
  in two bleeds out within 8 s (`Dismember.HALVE_BLEED_OUT_TICKS`), pouring blood meanwhile, and dies of it through
  `Downed.letDie` (so the death rolls on its cause as usual). A killing halving still kills at once.
- Rubber-folk (who mend with a swallow) and zombies (whose upper half crawls on) are left out, as before. Built, not played.

## 0.20.96 (2026-11-05): the Aether sky (seven-mods plan, patch 10)
- Andy, "go ahead" on the last seven-mods patch. All new code is in `aether/sky` (The Upfall gave the idea; none of its code).
- **Updraft vents:** holystone vents with a pale grille, set into island tops (about one chunk in 10, the upper islets one
  in 16). Each breathes an unseen column of rising air up to 110 blocks (`UpdraftBlock`), with cloud motes. It carries you
  fast up to the next layer; sneak to rise slowly and step off. A block set in the column cuts it there; break it and the
  column climbs again. Craftable: holystone round a cold aercloud and a zanite gemstone. Mined with a pickaxe.
- **Light and heavy islands** (`SkyPockets`): the sky is cut into 160-block stretches. About one in 7 is light: you jump
  45% higher, fall at less than half the pull and take no damage from 8 more blocks, and motes drift up round you. About
  one in 9 is heavy: a shorter jump, a harder pull, and dust settling. Only on the islands (Aether height 60 up). They're
  attribute modifiers, so your own movement feels them.
- **Upfalls:** rare columns of water pouring up into an island's underside, 10 to 25 blocks long. They lift you slowly
  and put out fire.
- **Gas caves:** about one 96-block stretch of the Hestveil caverns in 3 hangs with green haze. Without a **Sky Mask** in
  the head bauble slot it poisons you, and sometimes sickens you. The mask is crafted from leather, two glass panes,
  string and charcoal.
- **Four sky biomes**, cut from the edges of the weirdness range (`tools/gen_aether_world.py`, data regenerated):
  - **Hanging Roots** (highfields woods, low weirdness): cave vines with glow berries, hanging roots and spore blossoms
    trail from every underside.
  - **Mistsea** (top of the lowland lakes): thick white fog (2 to 40 blocks), drifting ash, and thin sheets of cold
    aercloud lying in the open sky.
  - **Rustworks** (magnetic, low weirdness): heaps of weathered and oxidized copper with grates, chains and bent rods
    standing out of them, among ferrosite pillars.
  - **Gilded Ridge** (top of the upper lakes): golden oaks, raw gold and glint ore boulders.
- **The sky warden** (`SkyWarden`): one floating arena in each 1280-block square of the world, none within 900 of 0, 0. It
  is a holystone-brick disc 25 across with six pillars and a ceiling 14 up, set 80 blocks over the ground. You reach it by
  an updraft vent beside it with a landing. The warden is the sentry golem's frame at 2.5x, pale with cyan lights, 260
  health, with a boss bar. It wakes when you come within 14 blocks. It clubs you, marks a spot that a bolt hits a second
  later (step off it), and breathes a gust that throws everyone back. Below half health the arena's gravity flips:
  everyone in it falls up onto the ceiling and fights from there, and the warden flies, quicker with its bolts. When it
  dies, or you leave the arena, gravity rights itself and you get 10 s of slow falling. With nobody left it goes home and
  heals. It drops a gravitite block and a lot of experience.
- **The watched vault** (`VaultHeartEntity`): a small holystone-brick room set into the Hestveil rock, with a vault
  keystone in its floor, a chest (`chests/watched_vault`: zanite, ambrosium, golden amber, glint, gold, amethyst, now and
  then an echo shard) and 4 to 6 amethyst clusters. While anyone is near, the chest and the crystals move to another
  spot in the room whenever nobody is looking at them. An open chest stays put.
- The world generator's spring feature now writes `jaymospowers:sky_spring`, as the data already said. `check_aether_world.py`
  knows the new biomes and every Java feature type. Built and checked, not played.

## 0.20.97 (2026-11-05): taken-over bodies face the way they walk (Kingdoms 0.7.585)
Andy: "takeovers need to face your character toward its walking direction, kind of like how free rein works, if you are
looking forward the camera stays forward, but the model is drawn moving in whatever direction".
- While a symbiote takeover, the Great Ape's fit or the wild ape has you, your body turns to the way it is being carried on
  every screen, instead of strafing or walking backwards after your camera. The head still turns toward where you look,
  up to 60 degrees. The camera and aim are unchanged. (`client/TakeoverFacing`, `TakeoverFacingMixin`,
  `TakeoverFacingHeadMixin`.)
- The ape fit (`ApeRage.FIT`) now syncs to everyone, so other players see the ape facing its way too.
- Kingdoms 0.7.585 does the same for the werewolf beast, blood fever, feral curse and Grue holds. Built, not played.
- **0.20.98, Shift + Alt zoom lasts while Alt is held** (Andy: "releasing shift shouldnt stop your zoom but alt does"):
  the Shift + Left Alt zoom no longer ends when Shift is let go, only when Alt is (`SneakZoom`). The double-tap-and-hold
  Shift zoom still ends with Shift. Built, not played.

## 0.20.99 (2026-11-05): the Sky Mask breathes in space; gravity users turn the sky islands over
- Andy: "the sky mask lets non attuned breathe in space as well". A Sky Mask in the head bauble slot now gives you air
  on the airless worlds (the Pale Moon, the Maw; `Breath`) and in the high sky over the Aether (`ZFighterHighSky`), with
  no reserve to run out. In the high sky a Z Fighter with ki still breathes on ki first; the mask covers anyone without
  it, or a fighter run dry. Its tooltip says so.
- Andy: "gravity users can stretch their weightlessness to these islands, swapping the gravity with his toggle for all
  players and mobs while he is there". While a gravity user's Weightless (V) is on and he stands on a light or heavy
  stretch of the Aether sky, the stretch turns over: light becomes heavy and heavy becomes light, for every player and
  every mob in that 160-block stretch (mobs only feel the stretches while he turns them). When he switches it off or
  leaves, it rights itself within a second (`SkyPockets.swap`). Built, not played.

## 0.20.100 (2026-11-05): future guns' own executions
Andy: "future guns should all have an execution, choosing from a single point blank shot to the head, or three shots with a
semi or full auto weapon. it should use ammunition accurately and animate a one handed blast."
- A future gun (TaCZ's own modern guns) no longer plays the flintlocks' two-handed Point Blank. Its execution is drawn one-handed:
  the gun swung up in the gun hand, the arm straight down at them, the off hand at the side, the muzzle kicking up on each shot.
- **Head Shot** (`Finishers.Kind.FUTURE_SHOT`): one round into the head. Guns worked by hand between shots (bolt, pump,
  break-open and single-shot: AWP, Kar98, M700, M95, Springfield 1873, Lonetrail, M870, SPAS-12 and both double barrels,
  `FutureGuns.MANUAL_ACTION`) always do this, and so does any gun with fewer than 3 rounds left.
- **Three Shots**: a self-loading gun with 3+ rounds puts two into the chest and the last into the head. On semi
  (`FUTURE_BURST`) they come a beat apart; set to full auto or burst (`FUTURE_AUTO`) they're ripped off at once.
- Every shot takes one real round out of the gun (the chambered one first), plays TaCZ's own report and hit sound for that gun
  and its muzzle flash (none with a suppressor). An empty gun still runs them through with the bayonet.
- **Snap Shot** (`FUTURE_WILD`), Andy: "an empty gun fires once and can miss ... not empty, someone without executioner".
  Without the Executioner perk a loaded future gun no longer slams: it fires one round one-handed, and 40% of the time
  (`Finishers.WILD_MISS`) the shot goes into the ground beside their head and they live. Unloaded, it's still the Wild Slam.
- Built (compileClientJava), not played.

## 0.20.103 (2026-11-05): hold a charged rock; momentum; throws follow your aim (Kitchen 0.5.76, Kingdoms 0.7.587)
- **Rock throw, held** (`ThrownRocks`, `RockThrowPose`), Andy: "you can hold a full charged rock before you throw, and each
  you charge afterward is faster to charge due to momentum". A full charge no longer throws by itself: the arm stays drawn
  back (`READY`, trembling) while use is held, and the rock goes the moment you let go. Let go early, open a screen or pause,
  and nothing is thrown. Each throw takes `MOMENTUM_STEP` (5) ticks off the next charge, up to `MOMENTUM_MAX` (4) in a row,
  so 1.5 s comes down to 0.5 s; one throw's worth fades for every `MOMENTUM_FADE` (40) ticks between a throw and the next
  wind-up. The server keeps its own count (the client says `READY` every `KEEPALIVE` ticks while held). Others see the
  wind-up at its real pace (the charge rides in the synced value).
- **Aimed throws** (`Carry.fling`), Andy: "throwing held items and mobs should follow the cursor, if you look high the
  trajectory is higher, and smaller things can be flung further w/ higher strength stat". Sneak + right-click throws of
  anything carried (people, animals, mobs, blocks, severed parts; overhead heaves too) go along your look at `THROW_SPEED`
  (heave `HEAVE_SPEED`) with a little lift, times `lightness` (by bulk against a person's: 0.5x to 1.8x) and +20% per point
  of brawn (Kitchen's `Hauling`: Strength effect, size, Kingdoms' Might/Packhorse...; up to 5). Kitchen's corpse toss
  (`CorpseEntity.toss`) does the same. Kingdoms: hint 105 tells of the hold and release.
- Built, not played.

## 0.20.104 (2026-11-05): Homesteads' workstation advancements no longer come free
Andy, on a new character: "i got all these achievements but did not do them yet".
- Homesteads' five workstation advancements (Nice Tan!, A bouquet for you!, Honey, I placed a thing, Got wood?, Your
  prospects are looking up) used a placed_block field 26.3 no longer reads, so the first block anyone placed earned all
  five. An always-on pack (`resourcepacks/homesteads_fixes`) gives each 26.3's location match on its own workstation
  (`compat/HomesteadsCompat`); Homesteads' jar is untouched.
- Anyone who already has them from the bug has them taken back once, on their next join.
- Andy also chose "Raised by a demon master" (Emberborn) in the One Dimension world and woke in the overworld wild: the
  start found no demon village spot and fell back without a word. In a stacked world the search now starts straight
  under spawn (the Nether band isn't an eighth as far), and if no demon biome or village spot can be found the master's
  hall goes up on the nearest Nether floor instead of giving up; each fallback is logged (`ZFighterStarts.demon`).
## 0.20.105 (2026-11-05): Ore pebbles instead of rocky dirt

- Andy: "right now it isnt a scrying eye that could tell theres something there, ANYONE could tell, it should instead be
  a chance that a pebble spawns where these blocks would have". Rocky dirt no longer generates. Where a column's dirt or
  grass sits 2-12 blocks over a coal, copper, iron or gold vein, there's now a 40% chance (`OreTells.PEBBLE_CHANCE`) of
  a lone pebble on top: half faintly stained the ore's colour (Andy: "they should all be faint"; gives a plain rock), half
  a plain-looking rock that gives a nugget when picked up (`NUGGET_SHARE`; one coal for coal). All are named "Rock".
  Other ores show nothing on the surface. Seamed stone underground is unchanged.
- Rocky dirt already in the world turns back into dirt on a random tick, and it's out of the creative tab.
- New chunks only for the pebbles. Built, not played.
- **0.20.106, giants grab and throw trees (Kingdoms 0.7.588):** Andy: "giants should also be able to grab and throw the
  trees with their grabs". The Great Ape's grab (held punch) and Towerback's R, with no one in reach, close on a tree
  within the hand's reach instead (`GiantTrees`, which calls Kingdoms' `FallingTree.treeNear/take/hold/hurl` by
  reflection): a fallen tree, or a standing one torn out whole, up to 4 logs per block of the giant's height. It comes up
  to the hand over the hold, then where the grab would slam its catch down it's thrown along the giant's aim (0.4 b/t
  times the root of its height in people, more with brawn as carried throws, at most 2.5), tumbling, striking what it
  flies through, and landing as a fallen tree. Player giants only where they may build, as their trampling. Built, not played.

## 0.20.109 (2026-11-05): nobody talks to you while you're down, dead or reborning (Kingdoms 0.7.591)
- Andy: "make sure all the new features work wth all the old, im thinking specifically of the chat interactions and
  sevtech style world generating as you progress". Audit of the recent merges against villager talk and the world tiers.
- New `core/OutOfPlay.is(Entity)`: down or out cold, carried, left for dead (waking somewhere), black while a new life
  is made (`Mortality.REBORNING`), or held in Chrono's death window. Kingdoms reads it by reflection before anyone walks
  up to talk. The Chrono window stays exactly like any death: before this a villager's question could open its screen
  over the death screen there. Built, not played.
- **0.20.110, adrenaline at low health (Kingdoms 0.7.592):** Andy: "adrenaline should kick in if you are low health and
  fighting or running ... damaged beyond repair on your limbs, (not head or Torso) and still sprint, and attack, and stand
  yourself up, but only for 10s after being hit below half health. or within 40% of whatever death threshold."
  `core/wounds/Adrenaline`: a player hit by an attack (not fire, falls, hunger) who is left at half health or less, with
  head or body at 40% or less (the parts that down or kill at zero), or downed, has adrenaline for 10 s from that hit;
  going down's own first 10 s rush counts too. While it lasts broken arms, legs and feet hinder nothing: sprinting, two
  broken legs stand instead of crawling, broken arms swing/dig/shield/draw at full strength, powers use the limb as sound,
  and a downed player's broken or red arms don't stop the sneak rise (a rise started on it is finished on it). Head and
  body still count; severed limbs are still gone. One heartbeat only the player hears as it kicks in, no text. When it
  ends the breaks come back (crawl again on two broken legs). Tuning at the top of `Adrenaline`. Built, not played.
- 0.20.110 adrenaline's screen (Andy: "when it wears off you get the max thirst effect screen effects, (not max thirst)
  and a lessened vein and slight letterbox while the adrenaline is rushing"), `client/wounds/AdrenalineHud`: rushing,
  thin black bars (6% of the screen each) slide in top and bottom and the water-thirst veins show lightly at the edges,
  throbbing with a 150 bpm heart; wearing off, the bars slide away and the veins come in as at no water (Kitchen's
  DryVeins, every layer), held 3 s and fading over 7 s. Water untouched. Art copied from Kitchen's dry veins (ours).

## 0.20.111 (2026-11-05): passing out from hunger and thirst (Kingdoms 0.7.593)
- Andy: "players can pass out at low thirst or hunger, their fov slowly closing to implicate something is wrong,
  increasing as hunger/thirst lowers, eventually they could be doing anything and fall over, the screen slowly
  letterboxing fully shut, as they collapse over, losing control even as they walk and falling to the floor flat on their
  face, but ultimately ragdolling in any position. their FOV stays with the head as usual."
- `core/wounds/Fainting`: food or water (Kitchen's thirst) at 6 or less darkens the screen's edges inward, deeper the
  emptier you are (`client/wounds/FaintHud`; no FOV modifier, only the edges, so the view stays with the head). At 3 or
  less each second has a 1-in-240 (at 3) to 1-in-40 (at nothing) chance, doubled sprinting or swimming, that you go:
  4 s swoon where Adrenaline's letterbox slides fully shut, the darkness closes in, the head droops and sways, and the keys
  stop answering (`FaintInputMixin`: whatever you were doing goes on, slowing and weaving). Then you tip forward onto your
  face as Kitchen's ragdoll (held limp through `KitchenCompat.registerRagdollHolds`) and lie as it lands for 13 s, black,
  first person in the ragdoll's head; a blow wakes you at once. The bars open over 3 s as you get up; no fainting again for
  60 s. Not while downed, carried, in Chrono's window, left for dead, reborning, on adrenaline, riding, asleep, creative.
  Fainting players are `OutOfPlay`, so villagers don't walk up to talk. Only a slow heartbeat, no text, no Chrono hint.
  Tuning at the top of `Fainting` and `FaintHud`. Built, not played.
- **0.20.112:** `Fainting.thirst(Player)` and `Fainting.bringRound(ServerPlayer)` for Kingdoms 0.7.594's villagers who
  come to a fainted player.

## 0.20.113 (2026-11-05): Kitchen 0.5.77, small animals cracked with a rock and roasted on a campfire
- Ships Kitchen 0.5.77: a loose rock cracks open a small animal's body (crab, chicken, rabbit...), and one laid on a lit
  campfire roasts whole into cooked meat. See kitchen/MODLOG.md.

## 0.20.114 (2026-11-05): the demon master start's search no longer stalls the load
Andy: "there is still a long black screen on load, and the game cant seem to spawn me in the nether". The start looked
for a demon biome with a 3D search over the level's whole height, which in a One Dimension world is every band stacked
(about 1,400 blocks), on the server thread while you wait (you stood at world spawn meanwhile). It now sweeps once at the
Nether's own y 64 (`ZFighterStarts.demonBiome`) and logs how long it took and what it found, beside 0.20.104's fallback warnings.

## 0.20.115 (2026-11-05): Chrono leaves a wake running on water
- Andy: "chrono should leave a wake when they run on water"
- `ChronoSpeed.wake`: sprinting across water under Overclock (and Velox's Velocity, which runs on the same code) kicks
  spray back and up off your heels and lays a V of foam behind you like a boat's. Each tick foam is put down along the
  stretch you just ran and pushed out to both sides, so older foam sits further out and the V opens behind you, with a
  churned line down the middle; it's vanilla's wake particle (the fish-to-bobber one), which rides the surface and
  shrinks away by itself in 0.4 to 2 s. A soft splash every 6 ticks. Corrupted/ascended fates mark one foam point and
  one spray drop a tick (clones, glass), not all of them. Lava keeps its own sparks and smoke. Tuning: `WAKE_*` at the
  top of `ChronoSpeed`. Built, not played.

## 0.20.116 (2026-11-05): Kingdoms 0.7.600, wolves stalk you; fewer wolves and walruses
- Ships Kingdoms 0.7.600: wild wolves (the game's at night or when you're hurt, our feral ones always) smell players out
  (wind, sneaking, rain, your footstep trail), shadow them silently from cover, freeze and back off when looked at, and
  rush as a pack with one growl from behind or from hiding. Wolves spawn at a quarter (feral a fifth) and walruses at a
  fifth of their old rate, in smaller groups. See kingdoms/MODLOG.md.

## 0.20.117 (2026-11-05): Chrono's cheat death never ends in a new life, and never rises where it fell
- Andy: "chrono used the cheat death revive, but it didnt respawn, it instead reincarnated. they should revert as far
  back as possible everytime, and if not, at least 12 blocks away."
- The bug: a death decided final was held for R, but death by cause's own tick saw a living player marked final inside
  the 4 s window and began the rebirth (the black screen, then the life ends 2 s later), so the R's replay ran and the
  life story came after it anyway. `Mortality` now starts and finishes no rebirth while `ChronoCheatDeath.held`, and
  `undoFinal` lifts the rebirth's black too. Every rewind (a held death, a waking, or slipping a downing or an
  execution) undoes a final death, not only a held one: a second death while out cold could be decided final under
  the waking's window.
- Where you come back: the oldest checkpoint your power reaches, as before. If that checkpoint (or the "as you are now"
  fallback when there is none) is within 12 blocks of where you fell, you're stood on safe loaded ground 12-20 blocks
  away instead, facing away from whoever was executing you when there was one (`ChronoCheatDeath.away`,
  `Mortality.ground` made public). Built, not played.
## 0.20.118 (2026-11-05): every melee weapon parries; Kingdoms 0.7.601 gives each a weapon skill
Andy: "is battleaxe supposed to not parry? and it also has no moves, neither does the ninjato, they should all integrate
with the weapon arts". Not meant to: WeaponMod's melee weapons have their own right click, so the weapon guard skipped
them, and the katana and battleaxe raised a block pose that blocked nothing.
- The battleaxe and katana now guard and parry on right click like any weapon. The halberd and spear do too, and sneak +
  right click does what right click did (the halberd's reach switch, the spear's throw), as the knife's throw already
  works (`KnifeGuard.guards` / `ownUse`, `WeaponModMeleeUseMixin`, the guard poses). The patron weapons you strike with
  (Hearthguard, Anvil-Father's Hammer, Huntsman's Spear, Gravelight, Plain Word) guard too (`PatronWeaponItem.active`).
- Deliberately still no guard, because right click is their own move: the warhammer (its smash; it guards while that
  cools down), the flail (whirl), the rapier (lunge), the boomerang and throwing weapons, vanilla spears (charge),
  the tridents, Tidecaller / Sunbrand / Ashfang (their gifts), and the Arsenal's relic and stone swords.
- Pairs with Kingdoms 0.7.601: every melee weapon gets an RPG Series weapon skill on the skill bar. Built, not played.

## 0.21.0 (2026-11-05): Kingdoms 0.7.602, no taking a mounted villager's reins
- Ships Kingdoms 0.7.602: players can't climb onto a horse (or any living mount) a villager, guard or carter is riding;
  bring the rider down or throw them first. See kingdoms/MODLOG.md.

## 0.21.1 (2026-11-05): Kingdoms 0.7.603, an incoming-blow cue for PvP (and every foe)
- Andy: "is it possible to add an indicator for pvp? as of right now it is hard to parry or defend at all"
- `combat/IncomingBlows` (+ `compat/mixin/IncomingSwingMixin`, `client/combat/IncomingBlowsClient`): a player's Better
  Combat swing now warns the one it's aimed at (the swinger's lock-on, or anyone in the weapon's reach + 1.5 inside 50°
  of their look): the white star at the swinger's weapon hand and the warning bell, for that player only (no PvE spam).
  The warhammer slam warns the same way.
- Every blow coming at you, a player's or a mob's (Powers' wind-ups, Kingdoms' fighters and gunners), also draws an arc
  of five dots round the crosshair on the side it comes from (top: ahead, bottom: behind), closing on the crosshair as
  the blow comes in. White: parry it. Red: too heavy, block or dodge. Gold for the last `Guard.PERFECT_TICKS` (+2 locked
  on): raise the guard while it's gold for a perfect parry; any time before that is still a parry. The close is brought
  forward by your own ping (up to 4 ticks). It flashes as the blow lands.
- No chat text, no FOV change. Tuning: constants at the top of `IncomingBlows` and `IncomingBlowsClient`. Built, not
  played.

## 0.21.2 (2026-11-05): the minimap maps the layer you're in, and the Aether veil says what it read
- Andy: "the minimap shows the aether instead of the overworld". In a One Dimension world the Aether's islands are the top
  blocks of the Overworld level, and Xaero's Minimap reads each column from its top block down, so under an island it drew
  the island. `client/mixin/xaero/XaeroMinimapBandMixin` (optional config `jaymospowers.xaero.client.mixins.json`) gives
  its writer the band you stand in as the world's top and floor (`AetherVeil.mapTop`/`mapBottom`, from CustomChunks'
  synced layout): columns are read from your band's top down to its floor, and auto cave mode only counts a roof in your
  own band (islands overhead no longer flip it on). Up in the Aether the map is the Aether's, with void between islands.
  Checked against Xaero's Minimap 26.6.1 (the pack's) and 26.6.2 bytecode.
- Andy: "players can still look up and see the aether". The veil (0.20.60) was checked against CustomChunks 1.0.1's
  client layout (`StackLayout.of`, `Layer.minY/shift`), Sodium's `Viewport` and the 26.3 `Frustum`, and nothing wrong
  turned up from the code alone. It now logs what it read once per world ("Aether veil: band floors ..., veil on/off",
  or that no layout reached the client), so `logs/latest.log` shows why if it still shows. Built, not played.

## 0.21.3 (2026-11-05): the demon master start always wakes you in the Nether
Andy (singleplayer, One Dimension): "it didnt work". His log: a demon biome was found at once, but no village spot near
it had a floor, and the wider fallback searches generated chunk after chunk for minutes while he waited. Now only the
nearest few spots are tried (`ZFighterDemonRealm.FLOOR_TRIES`), then the hall goes up right where the biome was found,
carved out of the rock at the Nether's y 64 if there's no floor (`raiseAt`, which logs the column it found).
- **0.21.4, no skyfalls before Dread (Andy, 2026-11-05):** "okay, remove skyfalls until dread". The heavenfall chunks of
  0.20.45 now get their sky springs only while a player on the server has reached the Kingdoms world tier Dread
  (`SkySpringFeature.dreadReached`, checked once a second); before that no Aether spring may pour onto the Overworld at all.
  A heavenfall chunk made before Dread stays dry; they are 3000+ blocks from spawn, so nearly all are made later. New chunks only.
- **0.21.5, the Iron Saber's knuckle bow on the right side:** Andy confirmed the bow sat on the wrong side of the fingers;
  `tools/blockbench/iron_saber.py` now passes `flip=True` to `display()` (half round the blade in every hand view, as the
  rapier does). Not seen in game.

## 0.21.6 (2026-11-05): Kingdoms 0.7.605, the Family farmhand start, a day-one start list, and an advancement for every locked start
The village start is now the Family farmhand: you wake inside a lone farmhouse (Moog's Voyager barn, else a Homesteads
farmstead) and live with the family there. Day one offers only the Family farmhand, Rock bottom, Banished, Shipwreck,
the heritage starts and Let fate decide; every other start opens with a deed of its own and a "Road Opened"
advancement. Fate now picks from every start, locked or not, by a fixed percentage table. See Kingdoms' MODLOG 0.7.605.

## 0.21.7 (2026-11-05): carry the left for dead to safety; live animals in your arms
Andy: "your body should persist in the world until come to, that way players can pick each other up and carry each other
to safety. if you do not press anything, you will not get the respawn roll". A death turned away (`Mortality`) no longer
moves you to your wake spot after 3 s: your body lies where it fell, out cold, until you hold sneak 2 s to come to
(`COME_TO_TICKS`, the wake roll as before: wild, captured, sheltered) or someone brings you round. Carried, the black
says "You're being carried"; put down somewhere with no hostile within 12 blocks (`SAFE_RANGE`), a plain held use
revives you (as for the downed, food and all): no roll, but the cause's injury and borrowed time all the same. Mobs lose
interest in a lying body and none executes it. Logging out while lying: you come to where you lie.
Andy: "you can pick up live animals, but not all of them like it, snakes for example will bite you". Any live animal up
to 0.8 x 1.0 blocks lifts by hand (`CarryTemper`); wild grown ones in the new tags fight free: `bites_when_held` (wolf,
fox, Naturalist's snakes; a snake's bite poisons 7 s), `scratches_when_held` (cat, ocelot), `squirms_when_held`
(chicken, rabbit, parrot, frog). Tamed ones and the young stay put.
## 0.21.8 (2026-11-05): gravity's Lift throw winds up and whips through
- Andy: "gravity users throw the rocks the old way" / "gravity users throw instantly and farther". A left click to throw
  what Lift holds now draws the main arm back over the shoulder for `GravityPower.HURL_WIND` (7 ticks, about a third of
  a second), then lets go with the same whip and follow-through as a thrown rock (`ThrownRocks.hurling`/`hurled`, the
  `MAIN_ARM` flag on `ThrownRocks.THROW`, drawn by `RockThrowPose`). In first person the bare main arm whips through
  (FirstPersonArms, Kindle's throw keys). The throw keeps its power-throw speed (Andy's pick). Dropping the hold during
  the wind-up throws nothing. Built, not played.
- Andy: "when a gravity user throws a rock, it flies faster and further and throws instantly... like a pebble". A gravity
  user skips the rock charge: the off hand rock goes the moment use is pressed (whip and follow-through only), at 3.0
  speed instead of 1.5 and straighter (`ThrownRocks.GRAVITY_SPEED`/`GRAVITY_SPREAD`, `ThrownRocks.gravity`). The usual
  10 tick cooldown still spaces throws.
- **0.21.9, worldgen threads looping forever (server froze twice):** the 2026-11-04 20:16 and 2026-11-05 15:00 watchdog
  crashes had every C2ME worker spinning in `Object2ObjectOpenHashMap.get` inside
  `ChunkGeneratorStructureState.generatePositions`, chunks stuck at structure_starts for minutes. Vanilla fills a
  generator's structure positions once, on first use, with an unlocked check-fill-flag; One Dimension's per-layer
  generators fill lazily, so C2ME's workers filled the same map together and corrupted it. New `StructureStateLockMixin`
  does vanilla's same steps inside a lock. Checked against the 26.3 jar; not built here.
## 0.21.10 (2026-11-05): gravity users harness the rocks they carry (B+G) and throw them (sneak+R)
- Andy: "a gravity user should utilize rocks and pebbles for more interactions with its kit". Carrying more than 8 loose
  rocks (any kind, anywhere in the inventory), B+G (hold B, tap G) gathers them into a rock ball over your right
  shoulder, bigger the more you carry (counted up to `GravityRocks.ROCK_CAP`, 32). B+G again splits it into a ring of up
  to 16 rocks orbiting your waist; once more and they go back in the pack. No rock is used up. Drop below 9 rocks, go
  down, or lose the power and they fall back. The held B never lands its Crush or Wave.
- Ball: 2.5 s (`BALL_DELAY`) after you hurt something, if you're still within 10 blocks, the ball flies at it and back
  and lands one lump of 0.2 per rock (6.4 at 32 rocks).
- Orbit: after you hurt something, up to 8 rocks (`VOLLEY`) peel off one by one, 3 ticks apart, each hitting for 0.2 and
  a bleed tick (`Bleeding.BLEED_DAMAGE`, 0.5, to the body; nothing bloodless bleeds). Foes that come within the ring
  (monsters, anything after you, whoever you're fighting) are clipped for the same, at most once a second each.
- One follow-up at a time: hits while the ball or a volley is out send nothing more. Every rock of the ball and ring is
  the rock item's own model, the kind you carry most of (`client/power/gravity/GravityRocksLayer`). Built, not played.
- Andy: "you should be able to throw the rocks with shift R, a radial pulse from the orbit and a directional throw with the
  ball, that explodes in pebble debris on impact breaking things like leaves and glass". With rocks out (B+G), sneak+R
  throws them instead of starting Lift:
  - Ball: flies where you look (`GravityRocks.THROW_SPEED` 1.6, a slight drop) with the main arm's whip, and bursts on
    the first block or creature it meets: 0.3 per rock to what it hit, half that to everyone else within 2.5 blocks, a
    bleed tick each, and a shove. Leaves and glass (panes, stained, tinted) within 2 blocks smash (`shatters`), where
    you're allowed to build.
  - Orbit: the ring flies outward to 7 blocks over 7 ticks; every foe it passes takes 0.2 per rock and a bleed tick and
    is pushed away.
  - The thrown rocks leave the pack and land as items (around the burst, or out at the ring's rim) to be picked up or
    pulled in again; creative keeps them. B+G gathers a new ball from what's left. Built, not played.

## 0.21.12 (2026-11-05): right-click ledge grab

- Andy: "you should be able to grab a ledge that you can see over manually with right click and an empty hand, im
  stuck right now in the nether". `LedgeClient.clickGrab`: a fresh press of Use with an empty main hand, on the ground
  or in the air, facing a ledge (`Ledge.find`: edge 1.1 over the feet up to 0.45 over the eyes) catches it and hangs,
  then Jump climbs as usual. Lower things climb as Sneak + scroll does (`scrollGrab`). The click is left alone when the
  crosshair is on a mob or a block with a menu or switch (block entities, doors, trapdoors, buttons, gates, levers,
  beds). Gradle build only, not played.

## 0.21.13 (2026-11-05): looks over the skin on MCA's body (Kingdoms 0.7.609)
- Andy: "is anything else drawn on the player model instead of where it needs to be?" (after the vampire's eyes). With
  MCA a player is drawn as MCA's villager body; two Powers looks were still drawn on the renderer's plain model, posed
  on their own, the eyes where the Mojang skin's eyes are. Now, through `client/McaLooks` (MCA's classes only in
  `McaSkin`; Powers compiles against the MCA jar for it): Wind's Thunderhead white eyes (`WindStormEyes`) light MCA's
  own eyes on MCA's face body, and Ember's Flame Avatar char and veins (`FlameAvatarClient.Look`) ride MCA's skin body
  and pose. Without MCA, nothing changes. Built, not played.

## 0.21.14 (2026-11-05): Bear Minimum, and the grizzly mini-boss (Kingdoms 0.7.610)
- Bear Minimum 1.0.5 (Apache-2.0) in packMods, unchanged: shy black bears, warier brown bears, bear meat, tougher polar
  bears. Kingdoms adds the grizzly mini-boss, drawn after its brown bear (see Kingdoms 0.7.610).
- The grizzly and Bear Minimum's bears are in `blood_trackers`, `furry` and `body/quadruped`.

## 0.21.15 (2026-11-06): Saint's dragons animated (Kingdoms 0.7.612)
- Atroxiia has a real walk and is back in the game; Varasuchus, Atroxiia and Ignivorus get their missing motions
  (bites, sweeps, runs, flinches, deaths, idle fidgets, sleep). See Kingdoms 0.7.612 and
  `docs/SaintsDragons-Animation-Spec.md`.

## 0.21.16 (2026-11-06): Atroxiia rare and hunting monsters (Kingdoms 0.7.613)
- Atroxiia spawns about 400 times less often and fights hostile mobs near it. See Kingdoms 0.7.613.

## 0.21.18 (2026-11-06): who's in a film (Kingdoms 0.7.619)
- `UltimateFilm.filming(ServerPlayer)`: whether a player is in a film just now (their own, or bars from someone else's),
  kept on the server for each film's full length. Kingdoms' Bane waits for it to end before it turns up.

## 0.21.19 (2026-11-06): the Banes' weapons (Kingdoms 0.7.621)
- Three found-only weapons for Kingdoms' Banes (`armory/BaneWeapons`): the Gravesworn (Crossguard and Tough Handle), the
  Grudge (Tough Handle and Tool Binding) and the Hornscythe (Tool Handle and Tool Binding). Their iron heads come only from an
  ended Bane, never shaped, cast or forged, like the kings' heads; JEI says where they come from.
- `jaymospowers:bane_element` holds the weapon's element; the Tool Station carries it (and the "Taken from" line) from the
  head onto the weapon. Kingdoms does the bursts. Art: `tools/bane_weapons_assets.py`, our own.

## 0.21.20 (2026-11-06): crowds take turns, counter and turn (Kingdoms 0.7.622)
- **Crowds take turns (`combat/Crowds`):** at most three melee foes come at a player at once. The rest wait in a ring
  about 6 blocks out, circling and shouting now and then, and can't strike or close in (`mixin/CrowdWaitMixin`,
  `AttackTells.windUp`). A turn lasts 5 s, then the next nearest gets one. Archers, bosses (100+ health) and anything too
  big to circle never wait.
- **Counter one, turn to the next:** a parry while another foe's blow is coming turns you to face him. Parries within 3 s
  of each other chain, each link making the riposte 15% harder, up to +60%.
- Spec: `docs/Combat-Feel-Spec.md`.
- **0.21.21, "Resource reload failed" (MCA's blur shader):** joining could fail a resource reload, and Minecraft then
  switched off every selected resource pack (Fresh Animations, FA+Player, so Touch'n Motion had no model). MCA's
  `sirben` post effect (`mca:post/blur_centered`) was GLSL without `layout(location = ...)` on its in/out, which 26.3's
  SPIR-V shader compiler rejects ("SPIR-V requires location for user input/output"). Patched that one shader inside
  `libs/mca-fabric-7.8.0-dev.3badc42+26.3.jar` (MCA Reborn is GPL-3) the way vanilla 26.3's `post/box_blur.fsh` does it:
  `GL_ARB_separate_shader_objects` and location 0 on `texCoord` and `fragColor`. Not run here.

## 0.21.22 (2026-11-06): hit streaks and shield vaults (Kingdoms 0.7.623)
Kingdoms Rivals patch 8, ideas only from the Arkham and Shadow of War games.
- **Hit streak** (`combat/HitStreak`): blows you land without being hit back count up (shown from 3). At 8, with the
  Executioner perk and a weapon in hand, sneak and hold attack on a foe still standing: he's dropped where he stands and
  finished at once. A blow taken, 5 s without landing one, or the quick kill ends the streak. Nothing wider than 2, taller
  than 3 or with 100+ health is dropped.
- **Vault a shield** (`combat/ShieldVault`): Andy: "jump plus alt key or roll bind in mid air". Jump, then press Dodge
  (Left Alt) in the air with a foe's raised shield within 3 blocks ahead, and you go over him (no roll) (Freerun's vault look) and land behind; he reels for a second. Anyone more than a third taller or over
  twice as wide is "too big to vault".
- **Shields** (`combat/Shields`): Yank on a mob with a shield tears it away instead of pulling him; a stun (Darktide's,
  Time Flow's, a parry's) knocks loose Kingdoms' armoured ogres' shields.

## 0.21.23 (2026-11-06): more for Chrono and Web (Kingdoms Rivals patch 9)
Ideas only from the Arkham and Shadow of War games. One new input: **sneak + attack, bare-handed** (`combat/Takedowns`;
a weapon in hand keeps it the finisher's hold and the quick kill).
- **Chrono, in Time Flow** (`chrono/ChronoTakedowns`):
  - Takedown at 8 combo: time stops round the foe in front of you, a beatdown, and he's downed. At 16 the nearest other
    goes down too. Spends that much combo.
  - Shadow-strike blink: hit a foe with something thrown or shot and he's marked for 10 s. Sneak + attack at him (out to
    24 blocks) blinks you to his back for a blow. Costs 3 combo.
  - A strike at one already down finishes him and the combo goes on. A strike at a raised shield is a time-slowing
    flourish: the bearer is stunned 2 s, guard down (an armoured ogre drops his shield).
- **Web** (`web/WebTakedowns`), whose combo is the hit streak (`combat/HitStreak`, now counting to 32):
  - Wrap-and-slam at 8: webbed and slammed down, downed. At 16, two. Spends that much of the streak.
  - String up: from 2.5+ blocks above a foe, sneak + attack snatches him up and leaves him hanging from your ledge.
  - A Spider-Sense counter that throws a foe into a wall pins him there in webbing for 5 s.
  - A plain punch on one already down, 3+ into a streak, finishes him and counts on.
  - Web Shot and Yank keep the streak alive.
- **Both:** a dodge roll or a shield vault keeps the combo and the streak from timing out.

## 0.21.24 (2026-11-06): takedowns on the skill bar (Kingdoms 0.7.625)
- Andy: "chrono in time flow should get the display, but for fist users across the board the menu can show while you
  have an empty hand and are locked on." `Takedowns.moves` / `Takedowns.cast` put patch 9's moves on the RPG skill bar
  (through Jaymo's Kingdoms' Weapon Forms bar, Spell Engine's keys 1, 2...): in Time Flow, always, Takedown and Shadow
  Blink; with an empty hand and locked on, Web's Takedown (wrap-and-slam, or string-up from a perch). They go at your
  lock-on foe, else the one in your sights. Sneak + attack still works as before. A blink at an unmarked foe says so.

## 0.21.25 (2026-11-06): all of Web's bare-handed moves on the skill bar (Kingdoms 0.7.627)
- Andy: "can all of web's unarmed moves go to the bar? symbiote included?" `WebBarMoves`: with an empty hand while locked
  on, Web's bar (fixed order, so a slot keeps its key; nine at most):
  - Any web slinger: Takedown, Finish (a downed foe: a symbiote execution while suited, else a quick finish with 3+ in the
    hit streak), Counter (in Spider-Sense's window after a dodge).
  - Without the symbiote: Webbed Fists.
  - Bonded: Weapon Arm (forms and strikes), Grab (seize a foe up to a cow's size; holding one, maul it; tendrils full, a
    combo), Tendril Grab, Spike Burst (a roar in the big suit), Consume, Shroud. The Suit and Wall Crawl stay on X.
- Each goes at the lock-on foe, else the one in sights, by the same checks as its own key. Every old key still works.
## 0.21.26 (2026-11-06): Felled in the pack (Kingdoms 0.7.628)
- Felled (all rights reserved, Raiiiden; CurseForge 1715901 / file 9305152) and Zarith (1720181 / 9284445) join `packMods`
  (dev: false). Kingdoms hands the felling stroke of a player's chopping to it (`FelledCompat`).

## 0.21.27 (2026-11-06): Jung's wind moves revamped
- Jung's revamp of four Wind moves (`WindMoves` server shots and poses, `WindMovePose` client body, synced `wind_move`):
  - **Gust** (G held on the ground): holding wheels both arms round in front, drawing wind in from all round; release is
    a one-armed push flowing into an uppercut, and a spiralling gust leaves with the uppercut, runs forward and curls
    upward, carrying and lifting everything it catches (same damage curve, now along the gust's run). It is bigger with
    mastery (up to +60%) and ascension (+30%). At full mastery it takes up the element round about, as the Cyclone does
    (fire, water, snow, sand, storm, or an Imbue's), with that element's effects on what it hits.
  - **Zephyr's Breath**: on release a spiral of wind lifts the caster two blocks and spins them for 2 s, limbs loose and
    trailing; the breath's effects (and holding for more) are as before. Not while flying or riding.
  - **Gale Barrier**: both arms flung out at shoulder height while it gathers; a sphere of wind bands forms round the
    caster and fades over 3 s. The barrier itself lasts as long as ever (the lingering swirl is gone).
  - **Cyclone Lance**: a 1.2 s wind-up (lead arm drawn back, the other aiming, wind spiralling in), then a bigger spiral
    shot with a line of wind down its middle flies out (3 blocks a tick); hit radius 0.6 to 1.1, same damage, stun and
    Stormbound bolts.
## 0.21.28 (2026-11-06): the downed and the dead bleed out (Kingdoms 0.7.630, Kitchen 0.5.79)
- Andy: "blood should pool under the downed and corpses until they bleed out, that way blood sorcerers and vampires
  have finite resources." `core/blood/BleedOut`: every body holds so much blood (a man's 3.0 in stain size, scaled by
  width x height, 0.3 to 12). The downed pour it into a pool under them once a second (`BloodTrail.pour`), 60% of it by
  the time they bleed out; it is their own trackable blood. Back up, it comes back over 10 minutes. A Kitchen corpse of
  anything that died bleeding (downed, cut or bloodied) takes over what was left and pours it out over 90 s, the pool
  spreading wider as it fills, until it is empty; carried, dragged or hooked bodies hold it. A full pool spills beside
  itself, so exactly the body's blood lies there for blood magic (`BloodTrail.draw`) and vampires to draw, then it is gone.
- Andy: "dragging and walking through puddles should smear more than the regular trails." `BloodTrail.soak` takes blood
  out of the round pools under a point. A body dragged (or crawling) through a pool soaks up to 0.35 a step and smears
  it out after, up to 0.3 wider a step, in the pool's colour (`BloodSmears`). A foot set in a pool while moving carries
  off 0.05 (0.1 sprinting) per check; the next steps lay skids along the way it went (1.4x a print, wider for what it
  carries) before the plain prints. The blood is moved, not made. `Tending.GROUND_DRINK` + `drinkAtGround`: hands and
  knees, leaning 58 degrees, head down and bobbing (TendPose), for Kingdoms' vampires lapping.
- Andy: "the kneel we have on crouch right now needs a revamp too so change them all." `client/KneelLegs`: every kneel
  is now built from the leg's two halves (KneeBend): the knee-down leg's thigh straight down (0.12 back) with the shin flat
  behind (knee 1.33), the planted leg's thigh level ahead with the shin straight down (knee 1.57), the hips one thigh
  (6 px) lower than standing. Used by the low crouch's kneel (`CrouchPose`), tending (`TendPose`), drinking (both knees
  down, arms straight to the ground), and, through `KneelLegs.remap` of their old-style keys (front about -1.15, back
  about 1.35), every finisher kneel, the web executions and Gravity's crush. Knees asked for with `want` are bent at
  draw time (`TendModelFeatureRendererMixin`).
- Andy: "dragging should bloody dirty and clean the armor they wear respectively." Each step a body is dragged or crawls
  (`BloodSmears`): through a pool, what it soaked up bloodies its coat and armour (`Bloodied.dragged`, 90 points a unit,
  chest and legs most; a corpse's own blood is topped up and freshened); over dirt, mud or sand its armour is scraped
  dirty (`ArmourGrime.dragged`, 0.6x a walking check per step, even dry earth); through water it's washed (12 a step,
  a corpse's blood too).
- Andy: "i'd like players to pull dirty beaten armor off enemies." Enemies' armour already keeps the dirt and blood they
  fought in when they die (it's saved on the body) and comes off worn down (Kitchen's `wear`). Now a dragged corpse's
  armour also takes the drag's blood, dirt and washing, gathered over 4 steps at a time and written onto the body through
  Kitchen 0.5.79's `CorpseEntity.reworkArmour` (`KitchenCompat.reworkArmour`, looked up by name; `Bloodied.dragArmour`,
  `ArmourGrime.dragArmour` / `washArmour`).

## 0.21.29 (2026-11-06): splinters from punching trees; ore crushing and washing (Andy's picks from Gemini's GregTech start)
- **Splinters** (`ground/Splinters`): hitting a log with an empty main hand drives a splinter into that arm (0.5 health
  through Wounds, so it shows on the H screen), and again every 1.5 s of keeping at it. Creative is spared.
- **Ore crushing** (`tinkering/ore/HammerRecipe`, type `jaymospowers:hammer_crafting`): raw iron, copper, gold or silver
  + any hammer (`#jaymospowers:hammers`) in a crafting grid = 1 crushed ore; the hammer stays, worn by 1 (a Tinkering
  hammer stops at broken). An ore block mined with a hammer drops crushed ore in place of its raw ore (Andy).
- **Ore washing** (`tinkering/ore/OreWashing`): crushed ore used on a water cauldron washes up to 8 for one level of
  water; thrown into one it washes after 2 s; left in running (not still) water it washes after 10 s, free. A cauldron
  holding a brew is left alone. Each washed ore has a 15% chance of a byproduct nugget (iron: copper, copper: gold,
  gold: silver, silver: copper).
- Washed ore smelts at full speed in a plain furnace (raw and crushed ore stay slow); the smeltery melts crushed and
  washed ore as raw ore (doubled). Raw bog iron (Kingdoms) isn't crushable yet.

## 0.21.30: Blood Gaze
- `FrostBeam`: a player tagged `jaymospowers_blood_gaze` (Kingdoms' vampire skill bar) gets the Frost-born beam out of both
  eyes, blood red, for `GAZE_TICKS` (20): same reach, damage, hit cooldown and wound roll, red dust instead of snow, no
  freezing water or putting out fire. Built, not played.
- **0.21.31, the game wouldn't start (Zarith x Lithium):** startup crashed in Mutant Monsters/WeaponMod's first explosion
  class load: Zarith 0.2.0's `ExplosionMixin` @Redirects `Level.clip` in `ServerExplosion.getSeenPercent` (trees block a
  blast's sight), and Lithium's `world.explosions.entity_raycast` @Redirects the same call, so Zarith found nothing and
  its `defaultRequire: 1` failed the class. Powers' fabric.mod.json now sets Lithium's `mixin.world.explosions.entity_raycast`
  to false through `custom."lithium:options"` (Lithium reads it from any mod; server and every client get it with the
  jar). Lithium's other explosion speedups stay on. Not run here.

## 0.21.33: held by the throat isn't laid down
- `DownedPose` skips a downed body a Kingdoms vampire holds up by the throat (`KingdomsCompat.grabbed`, reflection on
  `BloodGrab.grabbed`), so it stays in the hand (Kingdoms 0.7.635 poses it hanging there). Built, not played.

## 0.21.34: only the downed lose their heads
- Andy: "decapitations should only ever happen to downed players". `Dismember.playerHeadMayGo`: a player's head comes off
  only while they're downed (`Downed.isDowned`). Every beheading goes through `Dismember.lose`, so crits and overkill
  (`Wounds`), the axe finisher, mob chop executions, Frost Needle, and Kingdoms' archer wall pins all keep the head on
  a standing player; the blow still lands, and a killing one downs them as usual. Mobs and villagers are unchanged. A
  blast that blows a player apart (`BlastGore`) still takes everything. Gradle compile clean; not played.

## 0.21.35: Kitchen's quiver fits the body bauble slot
- `baubles/body` tag takes `jaymoskitchen:quiver` (Kitchen 0.5.82 feeds bows from it there and draws every worn quiver,
  the RPG Series' too, animated on the back). Data only.

## 0.21.36: the sprint slide needs a 1.5 s hold
- Andy: "sprint slide should be activated after 1.5s of holding, it still activates too easy". `Slide.HOLD_TICKS` 4 -> 30
  (1.5 s). While Sneak is held in a run short of that, `FreerunClient.slideWindup` keeps the Sneak bit off the movement
  (`StanceInputMixin`), so you keep running at full pace (sprint and fast run kept); a tap or a short hold does nothing and
  letting go early just runs on. Held the full 1.5 s, the slide starts as before (belly dive out of a fast run, back slide
  out of a sprint; still held at the end = prone). The server's `Slide.valid` no longer checks Sneak (the client keeps it
  off through the hold). `SPRINT_GRACE_TICKS` stays 8. Compile-checked against the 26.3 jars; not played.

## 0.21.37: every worn bauble shows on the body
- Andy: "Would this trinkets' system animate any other baubles? If so i want them to be integrated" (picked: Artifacts'
  ported baubles and our own starters). No other pack mod relied on Trinkets for drawing (scanned all 123 downloaded jars),
  but our Artifacts port never drew anything on the player. Now 49 baubles do: hats, hoods, masks and the circlet on the
  head; scarves and pendants at the neck; charms and belt gadgets at the belt; gloves, claws, gauntlets, bracers and rings
  on the hands (ring slot 1 right hand, slot 2 left); shoes on both feet; the featherfall cloak down the back (hides the cape).
- `Baubles.LOOKS`: each slot's item id, synced to everyone (`Baubles.looks`). `client/baubles/WornLooks` reads
  `worn_looks.json`, bakes a player-model layer per look (wide and slim arms, and a left-hand ring variant), and draws each
  worn look; scarf tails, pendants, charms, hanging pouches, feathers, jester horns and the cloak swing with the stride and
  idle; parts scale out over the matching armour piece.
- Our own boxes and textures (REA, docs/Worn-Looks-Spec.md): `tools/gen_worn_looks.py` writes the json and
  `textures/entity/worn/*.png`. Lanterns, the coin purse, the hip bottles, the lantern pole, fairies and quivers keep their
  own drawing.
- Andy: "Can we not port their looks too?" The 25 baubles that kept their Artifacts look (villager and angler's hats,
  scarves, necklaces, pendants, the neck charms, cloud bottle, skull, antidote, crystal heart, chorus totem, claws, fire
  gauntlet, vampiric glove, golden hook, withered bracelet, hare boots, kitty slippers, snowshoes, spikes, rooted and
  strider shoes) now use Artifacts' own worn models, textures and renderers (MIT, ported into
  `client/baubles/artifacts/worn/`, textures in `textures/entity/wearable/artifacts/` with the licence): `WornArtifacts`
  draws them from `Baubles.looks`, gloves and claws on both hands, belt charms at the front of the right hip. The
  cloud bottle, skull and antidote leave `#jaymospowers:hip_shown` (no more flat sprite at the hip). Our own looks stay for
  the 7 starters, the sky mask and the 16 we repainted medieval, whose Artifacts models are the modern things we renamed
  away from. Gradle compile clean; not played.

## 0.21.38: a traditional kneel, posed with the weapon; climbing hand over hand
- Andy: "the newest kneel has both knees bent in front of the character, it should look more like a traditional kneel,
  posing with a weapon in hand like a hand on the sword's pommel, blade in the ground, similar with an axe, or a low ready
  with a rifle or bow". `CrouchPose` (the low crouch's kneel): the knee on the ground is now behind the hip (`BACK_LEG`
  0.40, shin flat behind, `BACK_KNEE` 1.09), the front knee a touch above the hip with the shin straight down
  (`FRONT_THIGH`/`FRONT_KNEE` 1.65), hips sunk 6.5 px (`SINK`). The hands pose by `CrouchPose.Style`, read from the main
  hand through `Finishers.kind`: `PLANT` (sword, greatsword, axe, blunt) puts it point or head down in front of the knee
  with both hands stacked on the pommel or haft end; `UPRIGHT` (spear, polearm, staff) stands it beside the knee; `BOW`
  and `RIFLE` (gun, crossbow) hold it at a low ready, arrow or muzzle toward the ground ahead. The weapon is turned in the
  hand by `CrouchPose.grip` (from `FinisherItemInHandLayerMixin`); the off hand joins only when empty; left-handed bodies
  are mirrored. Aiming, drawing or swinging from the knee still leaves the arms to vanilla. The other kneels (tending,
  finishers, executions) keep `KneelLegs`.
- Andy: "a better climbing animation, moving hands and feet interchangeably". The cling (freerun climb and webbed fists)
  no longer borrows Not Enough Animations' ladder pose (`NeaLadderMixin`, `WallClimbs` and the nea mixin config are gone).
  `WebParkourPose.climbLimb`: each hand grips (`HAND_HOLD` 0.6 of the cycle) and stays on its spot while the body rises,
  then lets go and reaches up to the next hold; the hands half a cycle apart, each foot stepping up with the opposite hand
  (`FOOT_HOLD` 0.45), knees turned out. Keyed to the distance climbed (up, down or across, `CLIMB_CYCLE` 1.2 blocks), so
  it holds still while you hang and runs backward climbing down. Two-bone reach (`reach`) bends the elbows and knees at
  draw time (`bendLimbs`, from `TendModelFeatureRendererMixin`). Worked out on preview renders
  (`art-overhaul/kneel-climb`); compile-checked, not played.
## 0.21.39: every held bar keeps its progress
- Andy: "the bar should stay where it is even after you stop scrolling, the same way tree chopping works, every meter
  should work this way, especially washing rags" (throws and combat charges excepted). New `core/HoldProgress` keeps how
  far each player got at each held job (not saved). Let go, step away or lose the target and the bar stays; the next hold
  at the same job carries on. A job done empties it, and so does a hold broken off by blows (a revive or Second Wind broken by hits, a treatment spoiled by a hit).
- Washing a rag (`RagWashing`): the wash counts its own ticks instead of the use time, so a rag half washed stays half
  washed. Treating (`Treatment`): kept per patient, part and remedy. Reviving (`Downed`): kept per body and per downing.
  Giving up: kept for the downing.
- Getting up by yourself is the one exception (Andy: "they fall everytime they let go, that one should restart the meter
  because the animation would break if not"): let go part way up and you drop back to the ground (a thud, and
  `CrawlPose.sink` draws the body falling from where the rise had got to, quicker as it goes, over 7 ticks), and the
  next hold starts from the ground. Andy: "unable to restart the getup until a second after you hit the ground", with
  "slight particle dust as you thud into the ground, larger dust for larger characters": a second's wait after landing
  ("Winded from the fall"), and `Downed.thud` puffs the ground's own dust along the body, more and wider by the body's
  width squared (a great ape or a giant raises a cloud); a rise knocked down by blows thuds the same way. Second Wind (`SelfRevive`) and coming to where you lay
  (`Mortality`): kept. Lifting someone (`Carry`): kept per target.
- Not changed: throws (rocks, daggers), finishers, the stake struggle, spell and power charges, the knock-out mash, and
  minigame bars (smithing, brewing, jewellery). Compile-checked; not played.

## 0.21.40: sliding down the wall when the grip goes
- Andy: "There should be a fluid transition to an animation while sliding after you lose grip". `WebParkourPose`: a
  freerun cling moving down (only the slip does) eases a `slip` share in (`SLIP_EASE` a frame) and blends the climb into a
  slide: both arms up and clawing (`SLIDE_CLAW`), elbows a little bent, legs straight with the toes scraping and kicking a
  knee up in turn, body pressed into the wall (`SLIDE_LEAN` 6 degrees), head back looking up. The drop doesn't count as
  climbed distance, so the climb's limbs hold still under the blend instead of running backward. Compile-checked, not played.

## 0.21.44: Sora's Dagger stages keep their own look
- Fixes what #1096 (Sora's Dagger 2.0 models) broke. The gold, diamond and tempest daggers point back at their own models
  (`soras_dagger_<stage>` and `_reverse`); only the base dagger uses 2.0, since 2.0 has no per-stage textures yet.
- `soras_dagger_2_0_reverse` was a copy of the forward model, so a reverse grip looked like a normal one. It now flips the
  blade the way the old reverse did (third person X -170, first person X -60), with the translation worked out so the
  grip (model point 8,5,6) stays in the hand.
- The 2.0 models (and the wood variant) get a `ground` pose (scale 0.4) so a dropped dagger isn't full size.
- Removed the stale `libs/jaymos-kitchen-0.5.38.jar` (#1096 added it; master builds Kitchen 0.5.57). Compile-checked; not played.

## 0.21.45: dying mid-turn rises you turned
- Andy: "players that get infected and are turning into hollow, shouldnt reincarnate the way everyone else does, if they
  die before fully turning, then they dont get a prompt on death, just a longer black screen, an appropriate vignette, and
  a wakeup as a full turned hollow whichever they may be." `Mortality.MID_TURNS` (Kingdoms fills it: the Hollow's blight,
  a vampire's bite before nightfall) is asked before `TURNINGS`. Such a death is never final (beheaded, the void, lava or an
  execution aside), skips the come-to bar, doesn't use up a sure wake, and lies `TURN_DARK` (14 s) in the black with the
  line "Something in your blood keeps moving after your heart stops", then rises where it fell (the drowned on the shore)
  as what it was becoming, with no new injury ("The turning is done").
- `Mortality.TURN_TINT` (synced, not saved) carries the turning's colour; `DownedHud` draws it as a slow breathing ring
  at the edges of the black. Chrono's R window on a waking is unchanged (a rewind clears the tint). Compile-checked; not played.

## 0.21.46 (2026-11-07): up to 3 weapons on the body; the tool belt is a bauble and shows them all
- Andy: "players should animate up to 3 weapons on them by default, and wearing the toolbelt animates everything like
  before. it should go in the baubles slot."
- Weapon Master draws up to 3 hotbar items on the body (the lowest slots not in hand; was 1 since 0.20.73), and all of
  them while a Traveler Tool Belt is worn (`client/mixin/weaponmaster/WeaponMasterShownMixin`, `MAX_SHOWN`).
- The tool belts (all six) fit the Baubles belt slot, and only a belt worn there counts: its radial menu, tool swap
  and hip model all read that slot (`mixin/toolbelt/ToolBeltHelperMixin`, `compat/ToolBeltCompat`). A belt carried in
  the inventory no longer counts, so move it to the belt slot. Smoke's vapour sheds it from the slot.
- Andy: "lanterns and coin purses can work from inside the belt". The tool belt takes lanterns and coin purses
  (`ToolBeltHelperMixin` on `isItemValid`), and one carried in a worn tool belt works as if worn in the belt slot: it
  hangs at the hip, a lantern lights the way, and Kingdoms pays from and pickpockets lift the purse (`Baubles.belt` and
  `setBelt` read and write the first purse, else lantern, inside the tool belt; `Baubles.beltSlot` is the slot itself).
  Gradle build only, not played.

## 0.21.48 (2026-11-07): the Ranger and Netherite Ranger hoods get the hooded cloak, each in its own colours
- The Archers' ranger_armor_head and netherite_ranger_armor_head now work with the hooded cloak from #1095: the cloak's collar, back panel and mantles render on the wearer, the mask key lowers the hood onto the back and raises it again, and Kingdoms sees the wearer as hooded while it's up.
- `HoodedCloakLayer` picks a per-hood cloak texture (`hooded_cloak.png`, `hooded_cloak_ranger.png`, `hooded_cloak_netherite_ranger.png` from `tools/gen_hooded_cloak.py`, coloured from each hood's own texture in the Archers jar).
- `cloak_hoods.json` now lists exactly the three Archers hoods.
- Compile-checked; not played.

## 0.21.49 (2026-11-07): downed, every right-click is a struggle; doors push open, beds let you mend
- Andy: "while downed, you cannot interact with doors or other right clickable things", then: "every downed right click
  should be a 3s struggle ... making the door push open slowly for every second holding, right clicking a bed while downed
  hauls you into it, so you can recover there without the need for meds or another player, it just takes time, more the
  more you are hurt" and "you get back up whenever you want, but you recover the whole time".
- Downed but awake, hold right-click on a block: a 3-second struggle bar fills (it keeps its place if you let go), then
  the block works as if your hand were empty (doors, trapdoors, gates, buttons, levers, chests). What you hold is never
  used; cakes stay off. Attacking, items and powers stay dead; passed out or knocked out, nothing works. Locks and other
  mods' gates still apply.
- **Doors** (with Spooky Doors, now in the pack): the door swings open a little more for every moment you push; an open
  one is pushed shut. Without Spooky Doors it opens when the bar fills.
- **Beds**: the struggle hauls you up onto the bed. Lying there you don't bleed out or pass out, the pain eases, and your
  most hurt part mends every 2 seconds (1 health point), all the way to whole; after a minute a broken bone sets itself.
  The worse you're hurt, the longer it takes. Hold sneak to get up whenever you're well enough; you rise with what you've
  mended. Crawl off and the rest ends.
- **Cuts in bed** (Andy: "the bleeding can hold up to two cuts by holding still. any others will still bleed. taking into
  account each bleeding entity's mB"): lying still, you hold two open cuts shut (a severed stump counts as two); every
  other cut still pours your blood, each at the downed rate of your own body's blood. Shift about and they all bleed.
  Bled dry in bed, you pass out where you lie.
- **Stemming the bleeding** (Andy: "animate the hands moving to stem the bleeding"): holding still in bed, your hands press
  on the cuts they hold: under the chest, at the neck, back on a thigh, across to the other arm. Everyone sees it
  (`DownedStruggle.HOLDING`, synced; `CrawlPose.stem`, tuning `STEM_*`).
- **Who bleeds** (Andy: "make sure stoneborn and certain hollow don't bleed, and any other relevant race. (emberborn should
  have the magma blood)"): with Kingdoms 0.7.645, Stoneborn, Bio-built, and the Skeleton, Wraith and Reaper Hollow have no
  blood: no cuts, no pools, no smears. Emberborn bleed magma. `BladeBlood.BLOOD` (new) lets another mod decide a body's
  own blood; `Bleeding.bleeds` and a body's corpse now ask it.
- `DownedStruggle` (new): the struggle and the bed rest; tuning at the top. `compat.SpookyDoorsCompat` (new) calls Spooky
  Doors' public API by reflection. `Downed`: the use-block refusal moved to the FIRST phase and lets an awake main hand
  through, a LAST-phase handler runs the struggle; `awake()`, `holdClock()`; rising after rest keeps the mended health.
  `Wounds.restPart`/`restedHealth`; `BleedOut` pours a resting player's blood by `DownedStruggle.bleedingCuts`. `DownedKeysMixin` keeps `key.use` live with the crosshair on a block. Spooky Doors
  26.3.0.2 joins `packModsNoCf` (needs Balm, already in the pack).
- Not built locally (no JDK 25 here; GitHub's PR checks build it); not played.

## 0.21.50 (2026-11-07): who bleeds what, settled; blood on the ground lies still; no redstone look
- Andy: "the emberborn should have the same type of cooling magma blood that the cubes have, and so should the magmavein
  stoneborn. all zombies should get the rotten blood, and the rest of the blood changes are good to go too. just no
  redstone textures ... and the blood that falls on the ground shouldnt randomly sputter or splat or move unless directly
  interacted with. right now you will see it erupting from trails on the ground like it was bubbling or something."
- **Kinds of blood**: every zombie (zombies, husks, drowned, zombie villagers, zombified piglins, zoglins, anything in
  `#minecraft:zombies` or `#c:zombies`, and any mod's mob that is a `Zombie` in code) bleeds dark, rotten blood
  (`BladeBlood.ROTTEN`, tag `blood/rotten`). Magma cubes really bleed the glowing, cooling magma now: they were in both
  `blood/dark` and `blood/magma` and dark won. New colours for Kingdoms' heritages: `GOLD` (Divine), `SAP` (Verdant).
- **Corpses bleed the body's own blood**: a new body keeps the blood it died with (`BleedOut.CORPSE_OWN`, saved), so an
  Emberborn's corpse pools magma, a zombie's rotten blood, and a bloodless heritage's corpse doesn't bleed when hit, blown
  apart, dragged or hung (`BladeBlood.bleeds/color(Entity, EntityType)`).
- **Blood that lands, and magma** (Andy: "the magma should bubble as it cools throwing off those popping lava particles, the
  BLOOD should not. it can still move and fall and drip, like water does through blocks, but over ledges. this way a
  bleeding body above a ledge can drip a pool up to 20 blocks below it"): magma blood keeps its lava pops and smoke as it
  cools; blood still pours down ledges and drips off them. Blood now falls up to 20 blocks to the ground (was 6), its
  stain showing once the drop could have got there (`BloodTrail.FALL`, `DROP_FALL`). The red "bubbling" on trails was most
  likely the stump bleed's vanilla lava drips, which splash and sizzle where they land; those are gone (below).
- **No redstone dust for blood**: the Z-Fighter's blood-for-ki spills blood flecks, not red dust.
- **Skeletons don't bleed when cut apart** (Andy: "skeletons should not bleed"): losing a head or limb used to throw gore
  and bleed from the stump whatever the body; a body with no blood now loses it in chips of bone, with no stump bleed, and
  its severed head or limb never drips (`SeveredPart` keeps the body's blood).
- **Kitchen's blood is the body's own** (Andy: "double check that", with Kitchen 0.5.86): `BladeBlood.bodyBlood(Entity)`
  gives Kitchen a living body's or a corpse's blood, or none; a bloodless carcass on a meat hook no longer drips or pools.
- **A stump spurts** (Andy: "the decapitation bleed effect looks just like lava drips, it can stay but the particle should
  be red, and it should shoot multiple at once, in larger spurts as they fall if they do, less if they stay standing"): the
  vanilla lava drip is gone; every few ticks a fresh stump throws several drops of the body's own blood out of it (up from a
  neck), 2 small ones while the body stands, 5 bigger, faster ones once it has fallen (`Dismember.spurt`, tuning `SPURT_*`).
- Built; not played.

## 0.21.51 (2026-11-07): ParCool's running and falling moves, our own
- Andy picked these off the ParCool comparison checklist ("Go to their tutorial on github and make a checklist of
  everything we dont have yet"). Our own code after ParCool's ideas (alRex-U, LGPL, Forge-only): nothing of its code or
  art. Everyone gets them; they cost Feathers. New package `freerun/moves` (`RunMoves` registers them all).
- **Long jump** (`LongJump`): in a fast run, tap Sneak as you jump (up to 4 ticks before, 3 after): a long, flat leap
  (about 7 blocks), legs reaching ahead; 3 feathers. A tap only: a held Sneak is still the slide, two taps the zoom, and
  the air flip lets that tap go.
- **Charge jump** (`ChargeJump`): stand still on the ground and hold Sneak; after 8 ticks a pale bar fills over the
  Feathers row (1.5 s to full). Jump leaps up to about twice as high; moving lets it go. 1-4 feathers. Not for a web
  slinger: Web keeps its own (`WebSwing#tickChargeJump`). A double tap (the zoom's) never charges.
- **Trick jump** (`TrickJump`): a jump out of a fast run flips on its own a quarter of the time (front flip mostly,
  back flip sometimes), in the air flip's pose. Free; never on a long jump. The Sneak-tap air flip is unchanged.
- **Safety tap** (`LandingRoll.safetyTap`): the dodge at touchdown with no direction held (from the air, or just after
  landing a fall of 2.5+ blocks) is now a crouched landing instead of the forward roll: 60% of the fall damage off, 3
  feathers. Hold a direction to roll as before (all of it off, 6 feathers).
- **Sky dive** (`SkyDive`): 6+ blocks into a fast fall, press Jump to spread out flat: falls at most 1.5 blocks a tick
  (a plain fall reaches about 3.9), steers much faster; Jump again ends it. 2 feathers. Only if, two ticks after the press,
  you're still plainly falling, so a cloud in a bottle's jump, wings or a flight keep their Jump. Fall damage is unchanged.
- **Fast swim** (`FastSwim`): 2 s of sprint swimming breaks into a faster swim (a pull the way you look, up to 0.55
  blocks a tick) with a butterfly stroke and dolphin kick (`SwimStrokePose`) and a bubble trail; a feather every 1.5 s.
- **Hiding** (`Hiding`): hold Sneak and walk into a leaves block or a hay bale at your feet for 12 ticks and you squeeze
  in, sunk so it covers you. Mobs drop you as a target and barely see you (only one right beside you notices), and your
  name tag is hidden. Jump climbs out; so does taking damage, the block going, or using or swinging anything. Inside hay
  you don't suffocate, aren't pushed out, and can see out (mixins in `freerun/moves/mixin` and the client's).
- Poses for everyone (`RunMovesPose`): the long jump's sail, the safety tap's crouch, the sky dive spread flat.
- The traceur gloves and boots come in a second PR.
- Built; not played.

## 0.21.52 (2026-11-07): ParCool track A: ledge shimmy, castaway, wall slide, moving along bars
- Andy's picks from the ParCool checklist ("Go to their tutorial on github and make a checklist of everything we dont have
  yet"), track A. Our own code after ParCool's ideas; nothing of theirs copied.
- **Ledge shimmy**: hanging from a ledge, A or D moves you sideways along the edge, slowly (`Ledge.SHIMMY_SPEED`), hand
  over hand; it stops where the edge ends, steps up or down, or something is in the way (`Ledge.shimmySpot`). The server
  hears each new block held (`Ledge.Shimmy`). Everyone sees the hands shuffle and the body rock (`LedgePose`).
- **Castaway**: hanging, Jump + Back kicks you off backwards, out and up (`Ledge.CASTAWAY_OUT/UP`, like the vertical wall
  run's eject). Back alone still lets go, after a 3-tick wait for the Jump (`CASTAWAY_WAIT_TICKS`).
- **Wall slide** (`WallSlide`, `WallSlideClient`, everyone): falling beside or facing a wall, hold right-click with an
  empty hand to press against it and slow the fall to 0.15 blocks/tick. Each tick on the wall sheds half a block of the
  fall, and 1.5 s on it takes all of it. Ice, glass and leaves are too slippery. 1 Feather (half-icon) to press on, then
  1 a second. Bare hands burn on a long slide (half a heart every 2 s) unless guarded: `WallSlide.addHandGuard(Predicate)`
  is the hook for track B's traceur gloves.
- **Moving along bars and chains**: in Bar Swing, the movement keys (W/S, or A/D across) move you hand over hand along a
  run of bars, chains or fences at the same height (`Freerun.BAR_MOVE_SPEED`, `Freerun.BAR_MOVE`); Jump still swings off
  and Sneak drops. The swing calms and the hands alternate while moving (`BarHangPose`).

## 0.21.54 (2026-11-07): traceur gloves and boots (ParCool track B part 2)

- **Traceur gloves** (wrist bauble, leather and string): bare hands no longer burn on a long wall slide —
  `WallSlide.addHandGuard` now registers the traceur gloves (`freerun/gear/TraceurGloves`).
- **Traceur boots** (leather): more safe fall distance softens landings, and while worn they carry the freerun leaps
  farther: long jump speed x 1.10 (`LONG_JUMP_BONUS`), charge jump +0.05 blocks a tick (`CHARGE_JUMP_BONUS`), and the
  safety tap takes 85% of the fall damage off instead of 60% (`SAFETY_CUT` plus `SAFETY_CUT_BONUS`). Worn boots draw
  their own equipment texture (`equipment/traceur_boots.json`), and the gloves' and boots' descriptions now show as
  item tooltips.

Built; not played.

## 0.21.55 (2026-11-07): blood decals and overlays stop fighting their surfaces
- Blood on the ground, on walls and on weapons no longer z-fights with the surface it's drawn on: puddles, footprints and wall marks sit a clear hair above blocks, and blood on swords and axes is drawn a clear step off the item's own faces. The flicker is stopped by a depth bias on the decals' own pipeline (`BloodDecals.DECAL_LAYER`: the translucent particle pipeline with a depth bias), not by the lift, so decals stay flush on slopes, slabs, stairs, carpets, snow layers and at block edges; the lift stays tiny (`DECAL_LIFT 0.002`, `BloodPuddle.java`).
- Puddles and wall marks at the edge of view distance fade out over the last blocks instead of popping (`DRAW_DISTANCE_FADE`, `BloodPuddle.java`, `BloodWallMark.java`).
- Audited the other flat overlays: blood on creatures and items (`BloodCoatLayer`, `BloodItemOverlay`), armour grime (`ArmourGrimeMixin`), wound overlays (`client/wounds`), Kingdoms Armory 3D pieces and `WornModel` over armour (`kingdoms/src/client/java/com/jaymoskingdoms/client/WornModel.java`) — they draw through cutout/entity layers or screen-space HUDs, not decals over surfaces, so nothing to change there; `WornModel` follows EMF/Fresh Animations poses through `keepBody`/`setupAnim`, and Kingdoms' `ArmorPoseLivingEntityRendererMixin`/`EmfWornPose` only keep the body's pose. Kitchen's corpse overlays come through `BloodCoatLayer`'s reflection hooks (`client/blood/BloodCoatLayer.java`) — nothing to change there either; its files stay out of this PR.
- Built; not played.

## 0.21.56 (2026-11-07): Critter Armour fits under Fresh Animations
- Andy: "the new armor isnt mapped properly to the mobs": Critter Armory's pig, sheep and horse armour was drawn in
  the vanilla pose while Fresh Animations re-animates the animal models, so the armour slid off the animated body.
- client/compat/CritterArmoryFreshAnimationsCompat (EMF checks in CritterArmoryEmf) plus mixins on PoseLayersLivingEntityRendererMixin,
  QuadrupedModel (pigs, sheep) and AbstractEquineModel (horses, donkeys, mules; head and neck, body, legs, not the tail)
  keep Fresh Animations's animated body pose on the render state, only when EMF animated it that frame (not paused), just before the render layers, so Critter Armory's
  armour models take it (Powers 0.21.56). Only active when both Entity Model Features and Critter Armory are loaded;
  Critter Armory is linked, never edited.
- Built; not played (Fresh Animations isn't in the dev environment).

## 0.21.57 (2026-11-07): realistic fishing - a bite hooks a live fish, reel it in tug-of-war style

- **Realistic fishing**: a bite hooks a live fish entity (cod, salmon, pufferfish, tropical fish) instead of dropping
  vanilla loot, and you play a tug-of-war: hold use to reel, tension climbs while you do and falls when you ease off,
  the fish takes line back while you ease, and the line snaps at full tension. Stronger fish pull tension up faster
  (`FishingReal`, `FishingHookMixin`, `FishFight`). A wooden rod only ever hooks cod and salmon.
- **Metal fishing rod** (`MetalFishingRodItem`): forged through Tinkering (an iron rod head cast at the smeltery, handle
  and binding at the Tool Station). Its line holds to 1.25 tension, it keeps gaining on a fish at higher tension and
  reels faster, and it casts and retrieves like a vanilla rod (`FishingHookMixin` also keeps its hook from being
  discarded for not holding a vanilla rod). Only it hooks the big fish.
- **Big fish**: on the metal rod a bite may be a tuna (a cod grown 2.4x, lands as 3 raw cod) or, with Jaymo's Kingdoms,
  a great white shark (the Naturalist shark Kingdoms' whaling sharks use, brought up at the hook by
  `KingdomsCompat.sharkDrawnIn`, held without AI and unkillable while on the line). Both fight harder and pull you
  toward them while you reel; the shark is the hardest (a 45 s fight) and lands as 6 raw salmon. Without Kingdoms a
  shark bite is a cod.
- **Cleaning a catch** (Jaymo's Kitchen 0.5.88): a cod or salmon laid on a cutting board and cut with a knife now
  gives 3 fillets and bone meal (was 2), so a tuna or shark catch cleans into many.
- Fixed: the startup crash (the metal rod item had no id set) and a shark bite that fought a tropical fish.
- Built; not played.

## 0.21.60: Sora's Dagger 2.0 for every stage
- Andy's four Sora Dagger 2.0 Blockbench models are in: the base dagger is Iron, the gold stage Gilded, the diamond stage
  Diamond Edged, the tempest stage Tempest (`models/item/soras_dagger_2_0_<iron|gilded|diamond|tempest>` and
  `_reverse`, textures `soras_dagger_2_0_<stage>_hilt` and `_blade`). Stage logic is unchanged; each stage's item
  definition points at its own model, with the reverse grip on custom_model_data flag 0 as before.
- Converted by `tools/blockbench/sora_2_0.py` from `tools/bbmodels/sora-dagger-2-0-*.bbmodel`. It fixes what the #1096
  conversion got wrong: faces now name their texture slot (`#0`/`#1`; the full paths showed the missing texture),
  UVs are scaled to 0-16 (they were raw 128-pixel values), and the tilted cubes keep their rotations.
- Each model keeps its own hand, GUI and item-frame transforms; on the ground all four use #1099's 0.4 pose, and the
  reverse grip uses #1099's reverse hand transforms (the hand transforms are the same in all four models).
- Tempest's 1240px hilt texture is cropped to what the model uses and saved at 512px, so the item atlas keeps its
  mipmaps.
- Removed the old `soras_dagger_2_0`, `_reverse` and `_wood` models and their textures (the Gilded and Iron models
  replace them; nothing else used them).
- Built; not looked at in game.
