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

## 0.21.61 (2026-11-07): One character menu on H
Andy wanted one menu for the whole character (the idea from Kageborne's "Shinobi Menu"; ideas only, none of its code
or art). Mockup approved first.
- **H opens the character menu** on the tab you had open last. Tabs down the left edge of every page: **Character**,
  **Powers**, then (with Jaymo's Kingdoms) **Skills**, **Journal & Quests**, **Faction**, then **Map & Waypoints**, then
  (Kingdoms) **Party**. Each tab is its own existing screen, so nothing was rewritten: `client/menu/MenuTabs` draws the
  tabs over whichever page is open (Fabric `afterExtract`/`allowMouseClick`, which a page's own widget rebuilds don't
  clear) and switches between them. The old keys still open their page, which has the tabs, so K and M open their tab.
- **Character** (`CharacterScreen`, new): you, turning to follow the mouse, and a sheet: power, fate, mastery, health,
  armour, level; with Kingdoms also heritage, class, career, renown, skill levels, world tier, patron and realm.
- **Powers**: the old H screen. Its own tabs (Overview, Abilities, Chrono, Mastery, Powers) moved from its left edge to
  its top edge to make room. The inventory's power tab still opens it.
- **Map & Waypoints** (`MapScreen`, new): the loaded land around you, zoom on the wheel, with Arsenal's remembered
  places and (Kingdoms) the settlements you know and your party as pins, and a list of them by distance. With Xaero's
  World Map installed, a World map button presses its open-map key.
- Kingdoms is optional: it adds its tabs, rows and pins by reflection (`MenuTabs.register`, `characterRows`, `pins`
  take only Java and Minecraft types). The key is now called "Character Menu".
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
## 0.21.59 (2026-11-07): Kingdoms 0.7.648, the Family farmhand's family stop being deadweight
- The family you wake with on the Family farmhand start now run from zombies (spotting them from 20 blocks), go
  indoors at dusk and stay in until morning, and only swing back, weakly, when a monster has them cornered.
- By day they work the farm: harvest and replant crops, shear sheep, milk cows (given buckets), breed the animals from
  the house chest's feed, and the child gathers eggs, wool and saplings; it all goes in the house chest. MCA's Work
  button (chores) is open for them too.
- Compile-checked; not played.
## 0.21.58 (2026-11-07): Chrono's echoes stop piling up (one echo per Chrono, for real)
Andy: "Whenver chrono uses his cheat death, an echo respawns, and the same echos will appear in the nether, meaning he can
spawn up to 30 down there waiting to kill him."
- Cause: "one echo per Chrono" only counted echoes in loaded chunks. An echo left behind (a cheat-death rewind throws
  you minutes back, often far off or into another dimension) stopped counting once its chunks unloaded, so the next
  timer brought another; the Nether blink-return and following through a portal didn't check at all.
- Now each Chrono's memory names their one echo, loaded or not (`ChronoMemory.echo`, saved). No new echo comes while it
  exists, a blinked-out Nether echo doesn't come back if another is out there, and any other echo of theirs outside the
  future fades as soon as it's loaded, so existing piles (the Nether's too) clear themselves. It's cleared when that
  echo really ends (killed, shattered, blinked, faded); with the owner offline that's noted in the overworld till
  they're back (`ChronoEchoes.ENDED`).
- Through a portal only that one echo follows you (the future's extra stalkers stay in the future).
- Built; not played.

## 0.21.62 (2026-11-07): real fish on the line, baited hooks, skipjack tuna, fish weights, shark meat

Andy: "The goal is more realistic fishing", then: "i need there to be real fish ... they have to ACTUALLY be there",
"you should have to manually and visually bait the hook", and "new tuna mob please. skipjack?". Ideas from Au Naturel
(public domain) and Reel to Fish (all rights reserved, ideas only); our own code and art.
- **Every bite is a real fish** (`FishBites`): a baited hook draws a fish swimming within 10 blocks. It turns and swims
  to the hook, nibbles a few times (the bobber dips and splashes), then takes it, and the fight is with that same fish.
  What you catch is whatever lives there (vanilla spawns: salmon in rivers and cold seas, cod at sea, tropical fish and
  pufferfish in warm seas; skipjack tuna at sea; great whites with Naturalist). A landed fish is gone from the water, so
  a spot is fished out when its fish are; one that gets away swims off at its own size. Tuna and sharks only go for the
  metal rod's hook. The old random bite, the fish spawned at the hook and the made-up fish stock are gone.
- Fish are keener at dawn and dusk (1.6x) and in rain (1.3x) (`FishWaters`).
- **Bait the hook by hand** (`HookBait`): sneak and use the rod with bait in your other hand (Kitchen's fish bait,
  bread, rotten flesh or a spider eye: tag `#jaymospowers:fishing_bait`). The bait hangs under the bobber when you
  cast (client `FishingHookRendererMixin`), the rod's tooltip says what's on it, and a fish that takes the hook eats it,
  caught or not. Nothing bites a bare hook (the hook reminds you every 10 s).
- **Skipjack tuna** (`Skipjack`, `SkipjackModel`, `SkipjackRenderer`): a new schooling fish, dark blue-violet over
  silver with belly stripes, in schools of 3 to 6 in every ocean but the frozen ones. Too big for a bucket; spawn egg.
- **Fish weight** (`FishCatch`, component `jaymospowers:fish_weight`): every catch is weighed round its kind's usual
  size (cod ~4 kg, salmon ~5, pufferfish ~0.8, tropical ~0.4, skipjack ~5, shark ~400) and shows it under the name. A
  heavy fish pulls harder (up to 1.4x) and looks bigger on the line. Weighed fish of different weights don't stack.
- **Tuna and shark meat**: a landed skipjack is a whole Raw Skipjack Tuna (edible, weighed); craft it into 3 Raw Tuna
  Steaks, or cut it on Kitchen's board for a steak per 1.5 kg. A landed shark is cut up on the shore into Raw Shark
  Meat, a piece per 50 kg (3 to 12). Steaks and shark meat cook in a furnace, smoker or campfire.
- **Tideborn** (with Kingdoms 0.7.650): every bite calls `KingdomsCompat.fished`, see the Kingdoms log.
- Built; not played.

## 0.21.63 (2026-11-08): the fishing fight (Track A of docs/Plan-fishing.md)
Andy picked "all three together": nibbles and a strike window, a charged cast, drag and fish runs, the rod bending.
Ideas from Reel to Fish, Legendary Fishing, Advanced Fishing and True Fishing (all rights reserved: ideas only, our own code).
- **Strike window** (`FishBite`): once a fish that swam to the baited hook has nibbled and taken it (`FishBites`), the
  bobber is held under with "strike!"; use within 2.5 s hooks it and the fight starts. Too late, it spits the hook (the
  bait's gone) and swims off; the hook rests 5 s. `FishBites` now calls `FishingReal.fishAtHook` instead of `startFight`.
- **Fish spook**: no bites while you stand in water within 6 blocks of the hook.
- **Charged cast** (`FishCast`, `FishingRodItemMixin`, `MetalFishingRodItem`): with no hook out, hold use to draw the
  rod back and let go to throw, 0.55x (tap) to 1.6x (1.5 s) the old throw. Sneak + use with no hook out cycles the drag.
  The metal rod no longer casts a `ClientLevel` to `ServerLevel` on the client.
- **Drag** (`FishRun`, `jaymospowers:rod_drag` component, 1-5, default 3): past the drag's slip point (45-95% of the
  breaking point) the reel clicks and line goes out instead of the line taking the strain; a tighter drag reels 0.7-1.3x
  as fast. Sneak + scroll with a rod in hand turns it; the rod's tooltip shows it.
- **Runs and jumps**: a hooked fish surges left or right for 0.75-1.75 s every few seconds (more often for strong fish),
  a third of runs start with a jump. Reeling into a run triples the tension it adds and gains nothing; easing off, the
  fish still pulls and takes line. The line snaps with a lead-break sound.
- **HUD and sound**: the tension bar is now a share of the breaking point (the metal rod's line no longer runs off the
  end), with the drag's slip mark, `<<`/`>>` for a run, and drag in the label; a cast bar while charging. The line creaks
  (crossbow-loading sound) above 75% strain, faster near the break.
- **Scroll to reel**: while a fish is on, each scroll notch reels for 3 ticks (holding use still works).
- **Rod bend** (`FishingRodFirstPersonMixin`, `FishingRodThirdPersonMixin`): the plain rod tips down with strain, swings
  toward a run and shakes near the break; drawn back while charging. Pose only, no new model. Third person shows for
  your own player (the fight state still syncs to its player only); `THIRD_PERSON_SIGN` flips it if it bends the wrong way.
- Lang in `lang/parts/fishing_fight.json`. Built; not played.

## 0.21.64 (2026-11-08): fishing tackle - rods, baits, lures, groundbait, trolling, magnet, landing net

Track B of `docs/Plan-fishing.md`. Ideas from Reel to Fish, Advanced Fishing, Legendary Fishing and True Fishing (all
rights reserved: ideas only, our own code and art). Andy: their rods are "too realistic looking, the function is all i
want", so every rod here looks like a plain Minecraft rod.
- **Rods** (`Tackle.Rod`): vanilla's rod takes any small fish. The **Cane Pole** (bamboo and string) takes small fish
  only (cod, pufferfish, tropical) but they notice bait 1.5x as fast and from 0.7x as far. The **Spinning Rod** works
  lures and draws from 1.2x as far. The **Ledger Rod** fishes bait on the bottom and draws from 1.5x as far. The metal
  rod is the sea rod: the only one tuna and sharks take, and it works lures too. Each new rod has a cast model.
- **Baits** (eaten by the fish that takes them): Earthworms, Maggots, Dough Bait, Boilies, Lugworms, plus bread, rotten
  flesh, spider eyes, Kitchen's fish bait, and live bait (a tropical fish on the hook draws tuna and sharks). Each draws
  some fish more than others (`Tackle.Bait.draws`): lugworms are sea bait for cod, boilies and dough draw salmon.
- **Lures** (stay on the line, spinning or sea rod only, 5-8% chance to snap off on a strike): Spinner and Wobbler for
  salmon, Soft Shad for cod, Pirk (a heavy jig for cod, tuna, sharks) and Trolling Squid (tuna, sharks).
- **Groundbait** (`FishingGear`): thrown at water, fish within 8 blocks notice bait twice as often for 2 minutes.
- **Trolling**: fishing from a moving boat draws from 1.5x as far and 1.5x as keen (2.5x with a Trolling Squid).
- **Fishing Magnet**: put on the hook like bait; it draws no fish but now and then snags scrap off the bottom (iron
  nuggets, chain, rails, a bucket, shears, an old sword, a rare trident: `loot_table/gameplay/magnet_fishing`).
- **Landing Net**: use it on the fish you're fighting once it's within 4 blocks to land it, or scoop a free tropical
  fish or pufferfish straight out of the water.
- **Fishermen sell tackle**: bait at levels 1-2, lures at 3-4, the spinning and ledger rods at 4-5.
- Rod, bait and lure tooltips say what each is for. All craftable from vanilla materials.
- Not in this round (still open in the plan): rod pod and bite alarm, crab pot (needs Track C's crabs), lure depth,
  an ice auger.
- Built; not played.

## 0.21.66: eyes that close as you pass out to turn
- Andy: "you pass out and then turn as well, make sure the eyes animate closed too". `Mortality.turnOut` (Kingdoms calls
  it for the blight run its course and a vampire bite at nightfall): out cold where you stand with the turning's colour at
  the edges of the black (`TURN_TINT`) and its own line ("Your legs give. Your eyes close by themselves...").
- First person (`DownedHud.lids`): the black of every knock-out now comes as eyelids, a lid from the top and one from the
  bottom with soft edges, meeting in the middle, and parting again as you come round; passing out to turn they take
  `TURN_CLOSE_TICKS` (2.5 s) to close. Others see it on the body (`EyesClosing`): out cold, the eyes flutter shut over
  1.5 s, each flutter shut a little longer, then stay shut. Compile-checked; not played.

## 0.21.67 (2026-11-08): The game starts again (#1144)

- Fix: every client crashed at startup ("Mixin transformation of com.jaymospowers.client.fishing.mixin.HookBaitState
  failed"). `HookBaitState`, the interface that carries a hook's bait on its render state, sat in the fishing mixin
  package, and Mixin refuses to load a plain class from a mixin package. Moved to `com.jaymospowers.client.fishing`.

## 0.21.68 (2026-11-08): Blood puddles no longer crash the game (#1145)

- Fix: the first blood puddle, footprint or wall mark on screen crashed the client ("Render pass color attachment
  count must match pipeline color target state count"). The blood decal pipeline (`BloodDecals.decalPipeline`) was built
  without a colour target; it now has the translucent one vanilla's translucent particles use.

## 0.21.69 (2026-11-08): shift-click a bauble to put it on (#1148)

- **Shift-click** something wearable (a bauble, a tool belt, a spell ring...; not lanterns or other placeable blocks, so sorting a stack of them still works) in the survival inventory, hotbar or offhand: one goes into a free bauble slot that takes it and the Baubles screen opens so you see it on. With every slot that takes it full, shift-click moves it as before and a short "No free bauble slot takes that" message shows. Other container screens (chests and so on) keep vanilla shift-click.
- **Sneak-use** in the air with a bauble puts it on and opens the Baubles screen too (a plain right-click still just puts it on). Lanterns, totems and other wearables with no use of their own do the same; on a block a lantern is still placed. Items with their own sneak-use keep it (the Bottled Fairy still lets its fairy out, tool belts keep theirs).
- All on the server (`InventoryMenu.quickMoveStack` mixin, `BaublesQuickEquipMixin`, and `BaublesQuickEquip`): it takes one from the clicked slot itself, so a double click or lag can only put on what's there; the screen opens the next tick, and only if you're still in your inventory with nothing on the cursor (then the bauble still goes on, the screen just stays). Opening it closes the inventory as vanilla does, so the 2x2 crafting grid goes back into your inventory.
- Built; not played.

## 0.21.70 (2026-11-08): skyfalls made before 0.21.4 dry up too

Andy: "I saw another skyfall earlier today, i need them all removed until at least the world teir remember?" Since 0.21.4
a new chunk gets no sky waterfall before the Dread tier, but chunks made earlier kept theirs. `aether/world/SkyfallCleanup`
checks every chunk that loads in a One Dimension world: water running down out of the Aether's floor is traced back up
through the Aether (at most 8000 water blocks) and each source feeding it is sealed with the commonest solid block beside
it (else air), so the fall drains away. A heavenfall chunk's fall is kept once someone online has reached Dread, as for
new chunks. Water that lands on the islands is untouched; a waterfall poured off an edge by hand before Dread goes too.
Built, not played.

## 0.21.71 (2026-11-09): Thrown bodies ragdoll, throws have weight, small animals ride on you (#1162)

- **Ragdoll in flight**: anything thrown from a carry goes limp the moment it leaves your hands. A thrown player drops straight into Jaymo's Kitchen's limp ragdoll (flies, tumbles, lands and gets up as after any knockdown). A thrown mob tumbles end over end along its flight, faster the lighter it is, with its limbs flailing and trailing. When it lands it rolls onto its side, front or back, lies there a moment and gets up. A cat, or anything as light, rights itself and lands on its feet (`ThrownFlight`, drawn through `CarryPose`).
- **Weight**: every thrown thing has a weight (`ThrowWeight`): listed types (a cricket 0.05 kg, a parrot 0.4, a cat 4, a person 75, a pig 110, a horse 500, a cow 600...), else its hitbox's bulk. A level throw sends a person about 3 blocks, a cat about twice as far, a cricket or mouse about three times as far, a cow about half as far. Strength still adds on top (brawn: Strength, size, Might, Packhorse, a Z Fighter's). Tuning is at the top of `ThrowWeight`.
- **Small animals ride on you**: sneak + right-click a parrot, cat, butterfly, cricket, mouse or rat and it climbs onto you instead of into your arms. A parrot, mouse, rat or butterfly sits on your left shoulder, a cricket on your head, and a cat lies draped across your shoulders. Your hands stay free to use items and fight. Sneak + right-click with an empty hand (at the air or a block) lets it down, and a hit of 3 hearts or more knocks it off. Everyone sees it, and in first person you see it at the edge of your view. Add more with the `#jaymospowers:rides_on_shoulder` tag (`#jaymospowers:rides_on_head` for the head) (`Perch`, `PerchRidingMixin`, `PerchClient`).
- **Riding a player works on servers**: vanilla refused to let anything mount a player (a rider is saved inside its vehicle, and players aren't saved with the world), which also stopped the aerbunny and the Xenoform riders (Bulwark, Croaker, Titan) from mounting a player. Now anything may, and while it rides a player it's saved on its own in its chunk, so an autosave or a crash keeps it (`PerchRidingMixin`).
- Built; not played. Not checked in game: the flight's look and distances, perching in multiplayer and across a portal, and the first-person view of a perched animal.

## 0.21.72 (2026-11-09): Mimic true-form model (#1158)

- **Mimic art**: adds an original player-copying horror Mimic's revealed form: a lanky ashen humanoid with a hinged toothy jaw, exposed ribs, clawed hands and dark clothing remnants. Includes editable Blockbench source, texture, and idle, walk, run, attack, transform, hurt and death animations for the Chrono echo replacement.
- Assets only; entity behaviour and renderer wiring are separate. Rendered and visually checked from front, both sides and back; built, but not tested on a live entity in game.

## 0.21.73 (2026-11-09): Riders bring downed horses round faster (#1161)

- A player tagged `jaymoskingdoms.rider` (Jaymo's Kingdoms' Rider class) gets a downed horse back on its feet in half the time and needs no treat for it ("Rider's know-how"). (`core/wounds/Downed`)
- Built; not played.

## 0.21.74 (2026-11-09): Blindness: Echolocation Power (#1159)

**What a player sees:**
- New **Blindness** power obtainable via the calling (survive a fatal fall into darkness with <2 hearts).
- **R (Echolocation)**: Sonar rings ripple outward from every nearby sound, briefly painting blocks and entities in cyan waves. Mastered cooldown: 25 ticks (1.25s).
- **G (One-Eye Blind, hold)**: Close one eye — that half of the screen blurs (**Borrado**) or darkens (**Escuro**), the remaining view stretches, and your aim drifts toward the blind side.
- **V (Blindside Swap)**: Toggle which eye is blind (left/right).
- **B (Deafen, hold)**: Silence your own footsteps and actions so other echolocators can't hear you.
- **Z (Variant Toggle)**: Switch between Borrado (blur) and Escuro (darken) for the one-eye effect.
- **C (Ascended, Void Sight)**: Total blindness becomes true sight — see entity outlines through walls, hear heartbeats, but the world stays dark.
- **C (Corrupted, Blackout)**: Your blindness spreads to nearby players; taking head/eye damage blinds everyone around you.
- **C (Both, Abyssal Echo)**: A massive pulse that blinds everyone in 32 blocks and gives them echolocation rings for 20 seconds (60s cooldown).
- **Global trigger**: When any player takes significant head damage (≥4 damage), nearby corrupted Blindness players trigger Blackout on themselves and others.

Not tested in game.

## 0.21.75 (2026-11-09): Xeno-Band Ultimates: seven new Ultimate forms (#1160)

Seven new Ultimate alien forms for the Xeno-Band, each with its own model, texture, abilities and big move (C):

- **Elder Wyrm** (Wyrm's Ultimate): grows to 2.0x, black scale plates, long spikes, barbed tail blade, swept-back horn crown, void purple seams. R: Void Breath (three clouds of purple fire ahead). G: Elder Wings (fly where you look, dive to slam). V: Tempest Beat (wide gust swatting projectiles). B: Void Rift (step through void up to 24 blocks). C: Endfire Volley (five dragon fireballs fanning out, each bursting into a cloud).

- **Copper Storm** (Duplex's Ultimate): copper golem grown to 1.6 blocks, patina-green plate, coiled rod, crown spikes, blue-white seams. R: Storm Split (two charged doubles step out). G: Bolt Rally (doubles swarm a target, first strike lightning). V: Arc Switch (trade places with a double, arc shocks between). B: Overcharge Merge (doubles merge back, heal, Speed II + Strength, shock ring if three+). C: Copper Storm (lightning on every double, shock radius, all doubles charged).

- **Ironpour** (Rivet's Ultimate): liquid-metal puddle standing up to player size, riveted steel plates, gear-tooth spikes. R: Overmeld (Meld from further, shell moves 1.5x stronger). G: Twin Lash (two metal whips drag different targets). V: Molten Pour (fast flowing dash, burns and slows what you pour through). B: Overdrive Ride (Ride from further, machine gets Resistance II + bolted plates, rams). C: Foundry Slam (surge up into pillar, slam both whip-arms down).

- **Worldmaw** (Gorger's Ultimate): armadillo grown to 1.6x, thickened stone plates, forked horn, wider maw, ember cracks. R: Deep Gulp (eat block + 8 around it on that face). G: Rubble Cannon (gut fires as rubble cone, up to 3 eaten blocks fly as falling blocks). V: Quake Charge (long run goring creatures, off ledge becomes slam). B: Spiked Roll (curl into spiked ball, roll at walk pace, hurt and bounce). C: Worldmaw (unhinge jaw inhale: blocks gulped, creatures dragged in and bitten, then Gut Blast at double damage).

- **Brinecrown** (Slipfin's Ultimate): axolotl grown long and plated, pearly scales, spined sail, frilled gill crowns. R: Tide Jet (longer wider Bubble Jet, slows, puts out fire). G: Undertow Slip (Slip that frees from holds, bursts water throwing foes). V: Torrent Dart (longer lunge with splash wake, bites every creature on the way). B: Bloom Regrow (Regrow with Regeneration III, also mends allies nearby). C: Brinecrown (downpour over 10 blocks for 12s: fire out, foes Slowed, waves push them).

- **Lodestar** (Polestar's Ultimate): lodestone alien grown to magnetic pole, heavy plates, curved prongs, four iron shards orbiting. R: Great Pull (drags iron-wearers in wide cone, loose iron flies in). G: Shock Repel (wide burst hurts and throws back iron, shoves rest, turns arrows back). V: Triple Lift (tear up to 3 iron blocks to orbit, each V throws one). B: Bastion Field (hold: arrows near fly back faster, iron-wearers inside pushed out). C: Lodestar (6s magnetic pole: iron-wearers dragged and held, loose iron flies in, C again throws all out).

- **Nightfall** (Lurk's Ultimate): tall stalker at 1.3x, bone-black plated arms, spined ridge, four eye domes. R: Mark of Night (up to 3 creatures within 64 blocks glow, lose track of you). G: Nightstep (step behind target or first mark within 32 blocks, strike doubled if unseen, G again chains to next mark). V: Terror (everything looking at you and all marks: Darkness, Slowness II, Weakness II, Blindness, monsters drop target and shoved). B: Deep Fade (unseen with Speed I, first strike doesn't break it, second does). C: Nightfall (16 blocks for 160s: everything else Blinded, glows on your screen alone).

Each Ultimate has its own model (Ultimate<Name>.java in client/power/xenoform/ultimate/) and its own 128x128 skin with glow map (tools/ultimates/<id>.py). Health bar sits on top of the base form's bar. All effects respect pvp and team protection. Nothing persists when the Ultimate ends, the player dies, changes dimension or quits, or the server stops.

## 0.21.76 (2026-11-09): Blood pools grow, drag trails as wide as the body (#1169)

- **Blood pools as it gathers** (Andy: puddles "should increase in size the more it accumulates"). Blood landing on or at
  the edge of a pool of the same blood (rain of blood, a body bleeding on one spot, repeated hits, drips) now soaks into
  it: the pool spreads out, lies thicker and darker the bigger it gets (up to a cap), and lies fresh again, instead of
  many small puddles stacking. A pool grown into another of the same blood runs together with it into one. Holds for
  the server's lasting stains and the client's splash pools alike; fewer decals lie about as a result. Heritage colours
  only pool with their own (magma keeps its glow).
- **Drag trails like the rest of the blood** (Andy: "a large body will drag and a single tiny trail of blood will be
  there"). A dragged or crawling body, and a Kitchen carcass dragged behind a player, now leaves a smear as wide as its
  hitbox (a man's broad, a cow's wide, a chicken's thin) made of overlapping blotchy pieces, with drops shaken off beside
  it, a denser blot where it turned, and a pool where it lay still. Dragging back over the same trail darkens and widens
  it rather than stacking pieces. A downed body's trail still thins as it weakens.
- Tuning: `BloodTrail.TOUCH` / `GROW` / `MAX_SCALE` / `DRAG_*`, `BloodSmears.TRAIL_WIDTH` and the step, drop, turn and
  stop constants, `BloodPuddle.THICK_*` / `POOL_*` / `DRAG_ALPHA`, `BloodClient.POOL_*`.

## 0.21.77 (2026-11-09): Dying mid-turn no longer locks you in the black (#1172)

- **The lock** (Andy: "I was locked in the revive screen"): a body rising turned (dying mid-turn, or a turned kill) wasn't spared like one lying left for dead, so a skeleton kept executing it in the black. Each death rolled a new mid-turn death and began the 14 s black again (the server log had five in 35 s). Now mobs spare and lose interest in any body that is waking, and a death while the wake is under way is turned away without restarting it (`Mortality#stillWaking`).
- **Safety**: a wake under way that runs 30 s past its moment (`WAKE_LIMIT`) is done where they lie, whatever held it up. A knock-out long enough to be a lying body's, with no wake behind it any more, is cut short every second, not only on joining.
- **Relogging** while rising turned: they're woken where they lie as they leave, the turning done, rather than losing the turn and coming back to the black.
- **Struggle up with a healing item** (Andy: "if you are too damaged to get up, you can struggle with a healing item in hand to get yourself back up"): downed, awake and too hurt to rise, hold use with a healing item (`#jaymospowers:self_revive`: golden apples, linen bandages, the healer's satchel, Draught of Vigour, Bone-Knitting Elixir; and any potion of healing, drunk or splash) for 4 s. The bar fills, the item is used up and you're up with at least 2 hearts (`HealStruggle`). Let go and the bar keeps its place; a blow breaks it off. The downed screen doesn't mention it: it still says only "Hold sneak to give up".
- Built; not played.

## 0.21.78 (2026-11-09): Heart crystal ore artwork (#1167)

- **Heart crystals**: original stone and deepslate ore artwork with matching clusters of small, deep rusty-red crystal hearts, plus a cut-crystal drop with a heart visible inside.
- Art and models only; registration, generation, drops and health mechanics belong to the ore-hearts task. Editable Blockbench projects are in `art/heart-crystal/`; no third-party art used.
- Built and visually rendered; not tested in game.

## 0.21.79 (2026-11-09): Blight mobs - elite variants of existing mobs (#1166)

- **Elites**: a small share of natural spawns come in blighted - more health, damage, speed, a little armour and a
  longer chase range, a green haze around them and a name over their head (Blighted / Corrupted / Plagueborn, then
  the Tainted / Risen / Hollow / Festering). The share scales with local difficulty, so a spot that has been fought
  over for a while sends harder ones.
- **Which mobs**: the pack's own monsters first (sky warden, zephyr, tempest, blue and golden swet, skephid,
  gravitite taluton, cockatrice, carrion sprout, flux wisp, frostbite, xeno stalker) plus the usual vanilla hostiles;
  the `can_be_blighted` entity type tag decides, so a data pack can change the list.
- **Drops**: the same as the plain mob - no extra loot yet.
- **Night**: no night weighting; the chance follows local difficulty only.
- **Not seen through walls**: they are ordinary mobs that path to you like any other, so no tracking through blocks.
- Built and the server starts with it; not played in game.

## 0.21.80 (2026-11-09): Chrono: Time Stop (sneak + C), the whole world frozen on vanilla's tick freeze (#1175)

- **Time Stop** (Timeless Chrono, sneak + C; plain C is still Chrono Punch): for 5 s the whole server stands still on vanilla's own tick freeze (`/tick freeze`). Mobs, arrows and other shots in mid-air, falling blocks, water, lava, fire, redstone, crops, the sun and the weather all stop, and every screen holds its particles and animations mid-motion. Only the Chrono moves and acts. 3 minute cooldown of its own (Chrono Punch keeps its 8 s).
- Every other player is held where time caught them: no gravity, no movement keys, no attacking, mining, building, using items or powers (they can still look around). Nothing hurts them while it lasts but the Chrono's own blows. The freeze is one switch for the whole server (vanilla has no per-area freeze), so the stop is too.
- The Chrono's blows don't land at once: each one thuds and is banked (64 at most), and they all land when time resumes, knockback and all (capped). Arrows they fire hang in front of the bow and fly on afterwards.
- Everyone's screen goes grey while it lasts (held players a darker blue-grey closing in from the edges), a clock ticks through it, and time resumes with a bright flash and a chime.
- It always ends: when its 5 s run out, or the Chrono dies, goes down, logs out, loses the power or turns spectator, or someone runs `/tick unfreeze`, and when the server stops. One at a time: it won't start while the server is already frozen (another Chrono's stop or `/tick freeze`). Held players' flying-kick counter is reset every tick, so nobody is kicked for floating.
- `ChronoTimeStop`, `ChronoTimeStopSync`, `ChronoTimeStopClient`; `StasisField`'s locks and `ChronoStunInputMixin` also hold Time Stop's players. Tuning: `ChronoTimeStop.DURATION`, `COOLDOWN`, `MAX_HITS`, `MAX_RELEASE_SPEED`.
- Built; not played.

## 0.21.81 (2026-11-09): Remove double-tap Sneak zoom gesture (#1186)

The double-tap Sneak (within 6 ticks) to activate OK Zoomer's zoom is removed. The Shift+Left Alt zoom gesture remains unchanged.

## What the player will see
- Double-tapping Shift no longer zooms the camera.
- Holding Shift + Left Alt together still zooms until Alt is released.
- Alt alone still rolls (dodges).
- The slide gesture (holding Sneak for 1.5s in a sprint) is unaffected.

## Changes
- `SneakZoom.java`: Removed `DOUBLE_TAP_TICKS` constant and `sinceLastTap` field. Removed the double-tap detection logic in `tick()`. The Shift+Alt zoom path and cling/bar/slide blocking logic remain.
- `Slide.java`: Updated javadoc to remove reference to "zoom's double-tap".
- `FreerunClient.java`: Updated javadocs in `trySlide()` and `slideWindup()` to remove references to the zoom double-tap.
- `docs/AI-HANDBOOK.md`: Updated Controls section to remove "tap then hold Shift" from the Zoom description.

## Not tested in game
Zoom behavior with OK Zoomer mod not verified in multiplayer.

## 0.21.82 (2026-11-09): Android mechs move with GeckoLib (#1195)

- **Four animated builds**: Warden, Bulwark, Strider and Reclaimer keep their original geometry, skins, optional equipment and glowing visor/core/lights on one shared Blockbench rig.
- **Original motion**: coolant-breathing idle, the old leg/knee/arm walk, target-following cannon aim, shot recoil, shoulder-shell fire, a double-fisted smash, hit flinch and a held collapsing death pose. Editable projects and inspected old/new renders are in `art/`; no outside art was used.
- Built and render-checked; not played in game. Two dev-server boot attempts stopped before world load on unrelated missing mod dependencies (Stormie's Spiders needing Fabric API, then It Takes a Pillage needing Resourceful Lib).

## 0.21.83 (2026-11-10): Animated flintlock guns: musket, blunderbuss and flintlock with fire and smoke (#1207)

- **Muskets, blunderbuss, flintlocks move now.** In first and third person, WeaponMod's musket (bayonet muskets too), blunderbuss and flintlock pistol, and the Void Armory's flintlock, are drawn by GeckoLib: they swing up into the hand when drawn, sway slightly while aimed, and on the shot the hammer snaps forward, a muzzle flash bursts (full-bright) and the gun kicks back and lifts before settling. On reload the gun tips muzzle-up and the ramrod goes down the barrel, then the hammer is cocked again. A fired gun shows its hammer down until it's reloaded. The musket is heavier and slower than the pistol; the blunderbuss kicks hardest.
- **New blunderbuss model**: bell-mouthed barrel, brass bands and butt plate, walnut stock, lock, hammer and ramrod (our own art).
- **Bayonet muskets** keep the rifle model with a socket bayonet whose blade is the tier's metal (wood to netherite).
- **Much more fire and smoke** (`armory/GunBlast`): every shot sends out a flash, an explosion puff, a cone of flame (pistol 14 + 7, musket 22 + 11, blunderbuss 34 + 17), thick smoke (16 / 24 / 36), fine smoke (20 / 30 / 40) and lava sparks (3 / 5 / 8), then smoke keeps rolling out of the muzzle and drifting up for 1 s / 1.5 s / 2 s. Counts and lengths are constants at the top of `GunBlast`.
- Inventory, ground and item-frame pictures are unchanged. Damage, range, reload time and dual wielding are unchanged.
- Code: `armory/GunBlast` (blast, synced `gun_shot` attachment), `mixin/WeaponModGunBlastMixin`, `client/armory/guns/*` (item model type `jaymospowers:animated_gun`), `tools/gen_guns_geckolib.py`, art in `art/guns/`.
- Built; not tested in game (no client run here, and `runServer` can't boot in the cloud session).

## 0.21.84 (2026-11-10): Knives carry their metal's effect (#1204)

Tinkering: a tinkered knife's head metal leaves its mark on a hit - cobalt poisons, slimesteel slows, manyullyn
weakens, queen's slime burns, hepatizon withers - at a low chance, so a knife stays a knife, not a wand. Added the
knife metals (materials, ingots, traits), the knife tool type, and the on-hit mark effect; repaired or recast blades
keep it, as it belongs to the tool's metal.

## 0.21.85 (2026-11-10): ore hearts - permanent half-hearts mined from deep deepslate (#1210)

- **Heart crystal ore**: a rare ore in deep deepslate drops a heart crystal; using one raises max health by one permanent half-heart, up to ten half-hearts (five full hearts above base), shown quietly as a faint extra run under the health bar.
- **A life in your pocket**: a found heart is lost on death, but each one burns to turn one death aside - your extra sure wake before the cause roll. With none left, the cause roll runs as today, and reincarnation clears the hearts and the extra health.
- **New**: `core.wounds.HeartCrystals` (the hearts attachment, the heart crystal item, both ore blocks, worldgen and loot tables), registered from `JaymosPowers` and listed in `registerAttachments()`. Hooks: `Downed`'s `AFTER_RESPAWN` re-applies the extra health on respawn, and `Mortality`'s death roll burns a heart via `tryBurnHeartOnDeath` to turn a death aside instead of spending one of the three sure wakes (the heart *is* the wake - only one increment).
- **Client**: `HeartHud` draws the faint extra run under the health bar showing the half-hearts held.
- **Compat**: stacks with the `crystal_heart` artifact (both raise max health) - say so in the PR body, and ask Andy if it reads as too much health.
- Not tested in game.

## 0.21.86 (2026-11-10): Saplings turn into sticks (#1213)

Any sapling (vanilla or Aether) can now be crafted into 2 sticks in the crafting grid. This makes splints accessible when you're far from trees - just break a sapling you can't plant and turn it into sticks for a splint.

## 0.21.87 (2026-11-10): The Omen animated by GeckoLib (#1219)

The Omens (red and blue) are now animated by GeckoLib instead of posed in code. They breathe while standing, walk with
the old stride, break into a leaning run when they chase, shuffle round on the spot when they turn, wind up and slam
both arms down in a smash, kick back with every cannon round, flinch when hit, and on death jolt as the hatch pops and
buckle into their model's own pose (red and blue each their own). Heads still follow where they look and the cannon arms
still come up on a target, now tilting with its height and turning a little toward it. Same models and skins
(excalibur_style still reskins them). Not yet seen in game.

## 0.21.88 (2026-11-10): Training weights: shadowbox in them to build Might, whatever your power (#1220)

Tiv: "they should build might for everyone, wearing them should turn your meditate into shadowboxing. non gravity or z fighters are REALLY slow and bad at it". Wearing either pair of Training Weights now turns the Meditate key (I) into shadowboxing for every player, not only a Z Fighter or a Gravity user (the key still needs the Meditation perk, as it always has). Every second of shadowboxing in them builds Might, Jaymo's Kingdoms' attribute: 10 points an hour for a Z Fighter or a Gravity user in the light pair (13.3 in the heavy pair, half again with a Weighted Gi on as well, the same multipliers a Z Fighter's training has), and always a tenth of that for anyone else, about one point an hour, so a hundred-odd hours to the top. Nothing about a Z Fighter's Training Points, ki or mastery changes. Someone shadowboxing in weights also counts as a partner for a Gravity user's shadowboxing.
- `Meditate#kind` (weights make anyone a shadowboxer) and its tick (`Training#weightsMight` every second settled in), `Training.WEIGHTS_MIGHT_PER_HOUR`/`OUTSIDER_MIGHT`, `ZFighterItems#wearsWeights` (`weightRate` and `LIGHT_RATE` now public), `KingdomsCompat#trainMight` (reflection on Kingdoms' `Stats.trainMight`; nothing without Kingdoms).
- Built; not played.

## 0.21.89 (2026-11-10): sleeping sets your broken bones (#1223)

- Tiv: "sleeping should set your broken bones. after hitting the sleep button and waiting, longer per break". A
  broken arm or leg no longer needs a splint, a cleric or a priest to be set: sleep in a bed does it. The splint stays
  for when there's no bed to be had.
- **How long**: each break takes 10,000 ticks of the clock asleep (`Breaks.SLEEP_SET_TICKS`). The times add up, one
  break after another: a whole night in bed (about 11,000) sets one break, two breaks take two nights, three take
  three. What a night doesn't finish carries over to the next sleep, even after logging out. A leg is set first,
  since two broken legs leave you crawling. The time is measured on the clock, so a night that's skipped or run by as
  Kingdoms' timelapse counts in full. A player sleeping while the others stay up takes the full night in real time.
- **Only real sleep counts**: Kingdoms' bed rest (lying down by day, sneaking into bed, or lying there before you
  press Sleep) sets nothing. Sleep at night (a bed used at night, or the Sleep button from rest) and the counting
  starts once you're fully asleep, the same rule as a sleep ending Battered or a chamomile sleep healing you. The
  morning wakes you as usual: the sleep doesn't run longer, a second night is needed.
- **A set bone shows as splinted**, as if a splint had been put on: the slats show on the limb, the break keeps only a
  quarter of its slowness and weakness, and it counts as tended when the game checks whether you're patched up (what
  lets natural healing run at full speed). The splint comes off as the bone knits, as before. Messages: "Your Left leg
  has set in your sleep"; on waking with a break still to go, "Your Right arm is 40% set: sleep in a bed again to
  finish setting it". The "broken" message now says a splint or sleep in a bed will set it.
- **The downed bed rest** (hauling yourself into a bed while downed) still sets every break after a minute, now
  through the same `Breaks.bedSet`, so those breaks show splinted too instead of hanging untended.
- `Breaks`: `SLEEP_SET_TICKS`, the saved `SLEPT` attachment, `bedSet`, the sleep counting (each tick while
  `isSleepingLongEnough`, and on `EntitySleepEvents.STOP_SLEEPING` for the morning a skipped night jumps to).
  `DownedStruggle` calls `Breaks.bedSet`. Lang: `wounds.slept_set`, `wounds.sleep_setting`.
- Built; not played.

## 0.21.90 (2026-11-10): F5 goes between first person and behind; the front camera moves to F6 (#1228)

Tiv: "F5 should only toggle between first and 3rd person, the front facing camera should be toggled on w/ f6."
- **F5** now goes between first person and the view from behind (Shoulder Surfing's over-the-shoulder view in the pack). It never lands on the view from the front. From the front view, F5 goes to first person.
- **F6** turns the camera round to look at your face. Press it again and you're back in the view you had before (first person or from behind). If the game doesn't know what that was, you go to the view from behind. It's in Options > Controls > Key Binds under Miscellaneous ("Toggle Front Camera"), next to Toggle Perspective, and can be rebound there.
- **Cutscenes**: while an ultimate's film, a beam battle's film, Time Flow's shoulder view or a finisher seen from behind has the camera, F6 does nothing. Each of these gives back the view it found when it ends, so the front view comes back after them and F6 still takes you back to the view you had before it.
- **Nothing else moved**: nothing in the pack was on F6. First-person Model's toggle is meant to start on F6, but on 26.3 it starts on a key code no key sends. Vanilla only uses F6 as F3+F6, which doesn't trigger key binds. So `KeyConflicts` is unchanged.
- `FrontCamera` (the key and the toggle), `mixin/FrontCameraCycleMixin` (vanilla's F5), `mixin/shouldersurfing/ShoulderSurfingFrontMixin` (Shoulder Surfing's F5; its "third person front" setting stays on so F6 still works), `ChronoFlowClient#holdsView`, `FinisherPose#holdsView`. Shoulder Surfing Reloaded is now a compile-only dependency. Lang: `lang/parts/camera.json`.
- Built; not played.

## 0.21.91 (2026-11-10): modelled amethyst heart crystal ore (#1230)

- **Heart crystal ore**: the flat red-heart textures are replaced by raised purple amethyst crystals: one large central crystal inside a smaller heart-shaped cluster, on both stone and deepslate.
- Bright violet/lilac facets make the ore read lit in the dark like amethyst; it does not emit light. Full opaque rock hosts keep ordinary neighbour culling and need no cut-out blockstate or item variant.
- Includes original editable Blockbench sources, deterministic generator and inspected front/side/back renders. Registration, worldgen and drops remain with the separate ore-hearts gameplay task.
- Not tested in game.

## 0.21.92 (2026-11-10): Blinded means dark: Blindness darkens your whole view (#1231)

- **Blindness is dark now.** Being blinded (Smoke's Smother and bombs, Xenoform's band flash, Z Fighter's flash, Cryo's snow, any Blindness from anything) used to pull vanilla's black fog in to 5 blocks and leave what was close fully lit. Now the whole view goes dark too: about three quarters dark in the middle, so what's right in front of you is barely there, closing to near black at the edges. It closes in over a third of a second and lifts with the effect's last second, as the fog does. It stacks on vanilla's fog (which still hides everything further off) rather than replacing it.
- The crosshair, hotbar and every other HUD piece stay on top of the darkness, so the Resonance ring, the Blindness power's echo rings and the power chips still show while blind. Hiding the HUD (F1) hides the darkness too, as it does vanilla's own screen overlays.
- Tuning: `client/BlindedHud` (`WASH`, `RIM`, `REACH`, `FADE_IN_TICKS`).
- Built; not played. How dark it looks in game, and how it sits with the pack's shaders (Iris, Complementary), hasn't been seen.

## 0.21.93 (2026-11-10): V works again: the voice chat menu moves off the power keys (#1232)

- **Echolocation (and every power's V) works again.** Simple Voice Chat opens its menu on V. That screen opened before the power key was read, so pressing V never reached your power: Resonance's Echolocation never fired (no outline, no ripple, no chime) and the voice chat menu popped up instead. The same went for every power's V, and for G (Voice Chat's group screen, when groups are on).
- Once per install, as the pack already does for other clashing keys (`KeyConflicts`, now v8): the **voice chat menu moves to F7**, and its **group** (G) and **hide icons** (H) keys are unbound; both are buttons inside the voice chat menu. Only keys still on those defaults are moved; your own rebinds are kept.
- Resonance's own code is unchanged: read against the 26.3 sources, the ping, its packet, the outline mixin (Sodium's culling lets outlined creatures through) and the heartbeat ring hold up. The ring still only shows while a creature is within 12 blocks.
- Built; not played. Simple Voice Chat's defaults are from its README and public source, not checked in the 26.3 jar.

## 0.21.94 (2026-11-10): The future's dead are Warrior-born: mangled tails, 32 skins, dried husks (#1234)

Andy: "husks zombies and player zombies all with mangled tails in various conditions (12+ at least) and 30+ different
skins".
- **Tails**: every grown zombie, husk and stranger's zombie player in the future drags a Warrior-born tail, mangled one
  of 16 ways: bent hard to one side, snapped and hanging from the break, cut through halfway, gone at the root, hanging
  limp, broken twice, the fur torn away in patches, the tip rotted to bone, charred and short, crushed flat, split into
  two strands, coiled tight, gone stiff, hanging by a strand, cut short with an old rag knotted on, broken with the
  bone through. Dead tails barely sway; a snapped one swings from its break. Each mob keeps its own, picked by its id.
- **Skins**: plain zombies there now wear one of 32 civilian skins (16 new outfits: leather jacket, tracksuit, chef's
  whites, paramedic, security, blouse and skirt, overalls, raincoat, dojo gi, bomber jacket, coveralls, lab coat,
  cardigan, puffer jacket, delivery uniform, fight trunks), and husks one of 32 dried skins of their own instead of
  the game's husk. About half carry a Warpainted's ochre bands or a Battle-scarred's old scars on the face.
- Babies, and a real player's zombie, are left as they were; nothing changes outside the future.
- `client/power/chrono/FutureDeadLooks` (skins, the tail's 16 conditions and its render layer), `FutureZombieRendererMixin`,
  new `FutureHuskRendererMixin`, `ChronoLivingEntityRendererMixin`. Art: `tools/gen_future_zombies.py` writes
  `textures/entity/future/civilian_<n>.png`, `husk_<n>.png` and `dead_tail.png`.
- Built; not played. The tail shapes were checked in a Python mock of the drawing code and the skins as images, not in
  game; check the tails' place on the back, the husks' skins and the zombie players' tails in the future.

## 0.21.95 (2026-11-10): The life story's starts in Powers' worlds are each player's own (#1235)

Tiv: "Every player should have their own start, it should search for another if they are already taken." Jaymo's Kingdoms now remembers every player's start. The starts Powers places itself take a check from Kingdoms of which places are nobody else's, and use it:
- **Pale Moon**: a crash site nobody else woke at (out to twice as far if the ring is full). **Rustfall**: a mine mouth nobody else escaped from (the same).
- **Verdant World**: the village nearest the Lookout that nobody else was raised in, looked past up to three taken ones; with none free, a spot of your own further out.
- **The Maw**: if the pad is someone else's start, a deck of your own (a pad and its pod) on a ring 70 blocks round the hole, past the wrecks and still in its pull. With all ten taken (39 blocks apart), the pad.
- **Raised by a demon master**: a demon hall nobody else was raised in; a taken one is passed over and the next raised.
- **Back from the afterlife**: a free spot on the Other World's platform when the arrival spot is someone else's start.
- **Lost in time**: the future's arrival square, else a street round it (16-96 blocks out, near the square's height). A newcomer who waited for the future to form is checked when they're sent.
- **Escaped the alchemist's lab, Fresh off the bench**: the room is built where nobody else started, out to twice as far if it must be.
- Kingdoms calls the new overloads: `ZFighterStarts.land(..., BiPredicate)`, `Future.strand(..., BiPredicate)`, `LabEscape.start(..., BiPredicate)` and `ZFighterAndroidStart.start(..., BiPredicate)`. Also `WildRoom.find`, `ZFighterDemonRealm.raiseHome` and `ZFighterAfterlife.strand` with a free check, and `ZFighterSpace.deck`. The old signatures stay. Tuning: `ZFighterStarts.FREE_TRIES`, `FREE_STEP`, `DEMON_FREE_STEP`, `MAW_DECK_RADIUS`, `MAW_DECKS`; `Future.ARRIVAL_FREE_*`; `ZFighterAfterlife.FREE_TRIES`, `FREE_REACH`.
- Built; not played. Not tried with two players on a server.

## 0.21.96 (2026-11-10): Xenoform: Asterion, the minotaur alien (#1238)

- **Asterion** (Tiv's ask): scanning Kingdoms' minotaur now teaches its own alien, a bull-headed brute with the
  minotaur's own build and moves, in a slate hide with the band's green glow and its great hammer. The minotaur is off
  Tusk's DNA list. 1.6x, 48 health, hammer blows of 8, a little slow, hard to knock about.
- **Charge** (hold R): head down, it runs where it faced and only leans a little to steer (never more than 12° off its
  line). It smashes through dirt, wood and leaves, whoever placed them; stone and anything else solid stops it dead. The
  first creature it meets is caught on its horns and carried in front: stunned at every impact, hurt harder for every
  block it's put through, and run through when a wall stops the charge (a torso wound that bleeds). Others in the way
  are flung aside. Nothing can hurt Asterion while it charges (and half a second after) except the carried creature's
  own blows: a carried mob swings back with whatever it holds, a carried player can just hit it. Let go to throw the
  catch off ahead.
- **Roar** (hold G): the longer it's held (up to 2.5 s), the further (3 to 14 blocks) and harder the shockwave that runs
  out when you let go; whoever it reaches is thrown back limp. You crawl while you hold it, and a long roar takes longer
  to come back.
- **Slam** (V): the hammer comes down and everyone round it is hurt and thrown off their feet. **Gore** (B): the horns
  into the creature in reach, a bleeding cut and a toss.
- **Big weapons**: Asterion swings its great hammer, or any Better Combat two-handed weapon in your main hand (claymores,
  war axes, glaives, spears, scythes...) with that weapon's own damage and moves; every other alien stays fists-only.
- Look: "Minotaur" by eanncrod (Blockbench Workshop, CC BY 4.0), the model Kingdoms' minotaur uses, baked with its
  animations and its texture repainted (`tools/gen_xenoform_asterion.py`; credit in `art/xenoform_asterion-CREDITS.md`
  and `textures/entity/bb/CREDITS.txt`). Its Ultimate is the plain fallback for now (Labyrinth).
- Classes: `forms/Asterion` (all its numbers at the top), `XenoForm.wields`, `XenoformHandsMixin`, `XenoDna`; client
  `AsterionModel`, `XenoformClient` (act clock, held weapon), `XenoformLayer`.
- Built; not played. Not seen in game: the model's size and animations, the weapon in its hand, the first-person arm,
  the charge through walls, carrying a player, and Better Combat weapons while changed.

## 0.21.97 (2026-11-10): The Maw remade: a broken world hung over a ruined country (#1240)

- **No black hole any more.** The Maw is a world in pieces, 44 blocks across, hanging in its own pull at y 190: scorched soil over rock over deep rock, cracked through into eight pieces that have drifted apart, so you see in to a smouldering heart of magma and crying obsidian. Inside the cracks the rock is fused in patches to orange and yellow glass (it still sheds Maw dust when broken), the rest showing the world's layers of soil, stone and deep rock. The piece facing the pad is gone, its rubble flung out on that side. Broken stone-brick walls stand on its top and hang upside down from its underside, rubble hangs all round it, and a trail of rubble the pull holds up runs down toward the ground.
- **No void under it.** About 100 blocks below lies a wasteland that runs to the horizon: low rolling ground of scorched coarse dirt, gravel drifts, burnt soul-soil patches and bare tuff, the odd canyon, ash on the air and the low groan of the broken world overhead. The Lost Cities (MIT, McJty; already in the pack for the future) build their ruins on it: big cities running into each other, nearly every building broken off low, rubble in the streets, bomb craters in and out of town, fountains run dry, no green anywhere. Nothing lives there, no spawners, and the chests were emptied long ago: the salvage is still up on the wrecks.
- **The nine wrecks** stay where the pod sets down, each with its part set, its wrecked pod, its veiled ore and rocks, its lantern and mast, and its stores of the same rare loot. The even ones are ships' hulks (a long plated deck, ragged bow and stern, a keel, a broken bulwark, chains hanging under it, the stores in a barrel); the odd ones are pieces of the world's own ground (a slab of soil and paving on a root of earth and rock, two walls of a house still standing at a corner, the stores in a chest).
- **Kept as it was:** the deck plates, the part sets, the slow pull (now to the world's heart) and the tide at the heart that throws you out and hurts you, Maw dust and the Starlit Lens, the airless Maw, the salvager's start on the pad.
- **The star map** draws the Maw as a world in pieces round a glowing heart, one piece gone, a slow ring of rubble round it (it was a black disc in a ring of fire). Its chart, its star map text and the Maw dust's JEI page read the new way.
- **Light and air:** ashen light and fog instead of the old purple, and the dimension is the Overworld's height (y -64 to 320) so the ruins have room under the world.
- Code: `power/zfighter/MawWorld` (the world, its ruins and rubble, the wrecks: tuning at the top), `power/zfighter/MawCities` (Lost Cities: the Maw's own profile, `RUINS`, handed to Lost Cities at each server start, its city feature on the Maw's biome alone), `ZFighterSpace` (the pad's height, the pull; `Kind.VOID` is now `Kind.WRECKS`), `ZFighterSpaceArt.maw`, `BackHome` (dust from the fused glass). Worldgen from `tools/gen_zfighter_space.py` (`maw_type`, `maw_ground`: our own density function for the ground, its noise, surface rule and noise settings; the biome keeps the id `maw_void`, so chunks saved under it keep it). The generator also writes the moon's surface rule and the pod's block state as they are in the repo again.
- **Worlds that already went to the Maw:** the broken world is built at its new height the first time anyone lands, and the Maw's pad moves up with it. The Maw's chunks that were already made stay as they were: void, with the old hole, ring and wrecks still floating at y 80. Chunks not made yet get the new ground. A new world, or the Maw's folder moved aside, gets all of it.
- Checked: both mods build; the Maw's dimension type, biome, noise settings, ground, noises, surface rule and dimension load through 26.3's own registry loader (vanilla plus these files), and the ground comes out at y 60-93 for three seeds (70-80 under the world); the Lost Cities profile's 28 fields set cleanly on Lost Cities' class; the nine wrecks clear the world, each other and the pad. Not played: not seen in game, no server booted.

## 0.21.98 (2026-11-10): Loose sticks on the ground

- **Loose sticks** (Tiv: "this mod has sticks on the ground you can grab. alongside the pebbles. we need the same so starting is more manageable"): small piles of 1-4 sticks now lie about the overworld beside the pebbles and loose rocks, so a start can find its first sticks (for the primitive knife and the flint hatchet) without felling a tree. A punch takes the whole pile (one stick each); a right click picks up one stick at a time. They're plain vanilla sticks, walk-through like the rocks, and pop off if the ground under them goes.
- **Where**: only on soil (grass, podzol, dirt, coarse and rooted dirt, mud, moss, mycelium), never on stone, sand, gravel, snow or in water. Short grass, ferns and leaf litter give way to them. Mostly under trees: up to 3 piles a chunk in woods, about one pile every three chunks on open grassland. Piles are mostly 1 or 2 sticks (45% one, 30% two, 17% three, 8% four).
- Only land generated from now on gets them; chunks already explored don't.
- Tuning: `LooseSticksFeature` (`OPEN_CHANCE`, `CANOPY_REACH`, `SIZES`) and `worldgen/placed_feature/loose_sticks.json` (tries per chunk). Classes: `ground/GroundSticks`, `LooseSticksBlock`, `LooseSticksFeature`; assets and worldgen JSON from `tools/gen_loose_sticks.py` (our own stick texture). Pebbles, loose rocks and the saplings-to-sticks recipe are unchanged.
- Built; not played, and no server was booted (the new block and feature are only checked by the build).

## 0.21.99 (2026-11-10): Wild garlic drives out parasites; the Great Ape steps over the ground instead of sticking (#1241)

- **Wild garlic clears a parasite** (Andy: "another way to clear parasites ... a player can actually reach"). Wild garlic,
  the common forest herb, now works like sweet berries: used on soil it's planted, used in the air it's eaten (even on a
  full stomach, as a remedy). Each clove takes a third off a parasite from foul water (Jaymo's Kitchen's), so three
  clear a fresh one. The first ones say "The raw garlic burns going down. The parasite squirms."; the last says "The
  garlic drives the parasite out." Hot chocolate and milk still clear it at once. Its tooltip and JEI page say so.
  `Herbal#garlicEaten` (tuning: `GARLIC_PURGE_TICKS`, `GARLIC_FOOD`), `KitchenCompat#purgeParasite` (finds the effect
  by id, no Kitchen classes), `Afflictions#consumed`, `Herbs`.
- **The Great Ape no longer gets stuck** (Andy's clips: an ape crawling through a forest and pinned on a hillside in a
  village). At 30 blocks tall he still stepped up only a player's 0.6 of a block, so every one-block rise, path edge
  and house wall stopped him and he could only hop. He now steps up to 6 blocks more as he grows, and still while he
  shrinks back; the wild ape (a Warrior-born with no Z Fighter power) steps up 5, like a villager's ape. Tuning:
  `ZFighterLineage.APE_STEP` / `WILD_STEP`; `ZFighterPower#refresh`, `ZFighterLineage#attributes`.
- The voices for the ape and the xeno forms are Jaymo's Kingdoms' (its own note).
- Built; not played. Not tested: eating garlic with Kitchen's stomach, the parasite messages, how the ape's step feels
  on a hillside and in a village, a tall step on the server (the attribute is synced, so client and server should
  agree).

## 0.21.100 (2026-11-10): Loose sticks: right click picks one up even with sticks in hand (#1249)

- **Loose sticks**: a right click on a pile now picks up a stick whatever you're holding. Before, it did nothing while you held sticks, which after the first pickup was most of the time. A stick can't be put back on a pile (it isn't a placeable block).
- Built; not played.

## 0.21.101 (2026-11-10): Hardrock patch 2 - Thirsty Bottles (#1253)

- **Thirsty Bottles** (Darkhax, all rights reserved; downloaded by id from CurseForge/Modrinth, never copied) joins
  packMods: filling a glass bottle drinks up the water block it came from, so a lone pool runs dry and cauldrons become
  the way to store water. Kitchen's waterskins follow the same rule.
- `docs/Hardrock-Plan.md`: Andy's 34 TFC Hardrock picks split into 8 patches; this is patch 2.
- Not played.

## 0.21.102 (2026-11-10): Hardrock patch 3 - river currents and splashes (#1253)

Andy's TFC Hardrock picks (after Dynamic Waters and Realistic Water Engine, our own code). Running water now has a
current: swimmers, boats (ships too), dropped items and mobs drift downstream on top of the game's own push, half again
as fast in rain and twice in a storm. Something big coming down into water (a giant, a breaching great whale, a ship)
sends out a ring of waves that rocks boats and pushes swimmers away. Rivers still freeze in winter through the seasons.

## 0.21.103 (2026-11-10): Hardrock patch 6 - dogs that learn (#1253)

Andy's TFC Hardrock picks (`dogs/Dogs`): a tamed wolf learns from what it does with you.
- **Hunter** (10 animals killed): bites 2 harder. **Tracker** (25 animals killed): follows the blood trail of a wounded
  beast or monster to it, as wild hunters do (never people or pets).
- **Guard** (10 monsters killed): 10 more health and 4 armour.
- **Pack Dog** (2000 blocks walked at your side): carries a 9-slot pack, dropped if it dies.
- **Herder** (20 minutes among farm animals): while you sneak near it, the animals round it walk to you.
- Sneak and use your dog with an empty hand to hear what it knows, and open its pack. You're told in chat when it learns.
- Compile-checked; not played.

## 0.21.104 (2026-11-10): Hardrock patch 7 - sleeping bag and paraglider (#1253)

Andy's TFC Hardrock picks (Comforts and the paraglider, our own versions). The **Sleeping Bag** (leather, wool and
string) lies flat on the ground and sleeps, rests and skips the night like a bed, but never moves your spawn, so a
night on the road leaves home where it is; it breaks at a touch to pack up again. The **Paraglider** (wool over sticks
and string) opens in mid-air with right-click, like the aercloud gliders: a slow sink, sneak to dive, steer by walking,
no fall damage. It has no time limit but costs a half-feather of stamina every second and folds when you run out.
Heat below lifts you: lit campfires, fires, lava, magma, lit furnaces and Advanced Chimneys chimneys up to 24 blocks
down make an updraft, and riding one costs no stamina.

## 0.21.105 (2026-11-10): Hardrock patch 4 - rock and ore (#1253)

Andy's TFC Hardrock picks ("make veins bigger and rarer... diamond and netherite become IMPOSSIBLY rare"; ideas only,
our own code and art, `tools/gen_rock_and_ore.py`):
- **Big, rare veins** (`ground/BigVeins`, `BigVeinFeature`): vanilla's iron, copper, gold, diamond and ancient debris ore
  blobs are gone. In their place a chunk now and then holds one big wandering vein: iron about 1 chunk in 24 (90 ore),
  copper 1 in 20 (110), gold 1 in 48 (60), our silver 1 in 40 (50). Diamond is 3 buried ore in about 1 chunk in 900,
  ancient debris 2 in about 1 in 1200. Rarities are in `worldgen/placed_feature/big_vein_*.json`. New chunks only.
- **Prospector's Pick**: forged at the Tool Station from a copper Prospector's Pick Head, a Tool Handle and a Tool
  Binding. Tapped on rock it says what ore lies within 12 blocks (the commonest two): nothing, traces, or a lot.
- **Cave-ins** (`ground/CaveIns`): mining stone under a roof shakes it 1 time in 100: dust falls for 2 s, then up to 8
  blocks of the roof come down as cobblestone. A **Support Beam** (2 logs, makes 3; set upright or on its side) within
  4 blocks holds the roof, even one put up while the dust falls.
- **Wooden Pan** (stick, bowl, stick): used on gravel or sand that water touches, now and then turns up a gold or
  copper nugget, rarely iron, or flint; the bank washes away in time. **Sluice** (5 wooden slabs, a trough): load it with
  up to 8 gravel or sand beside running water and it washes them by itself, a little better than a pan.
- JEI info pages for all four.
- Compile-checked; not played.

## 0.21.106 (2026-11-10): Hardrock patch 7 - how bad you smell (#1253)

Andy's TFC Hardrock picks ("if you stink you'll be caught"): `core/wet/Smell` keeps how bad you smell, 0 to 100. Just
living adds a little (filthy in about three days), sprinting more, and grimy armour adds up to 40 on top. Going under
water washes it all off, wading or standing in the rain slowly, and the scrub at a water cauldron (sneak, empty hand)
now scrubs you as well as your armour. Kept through death. Jaymo's Kingdoms reads it for mob senses and villager remarks.

## 0.21.107 (2026-11-10): Freecam, creative only (#1251)

- **Freecam** (MIT, shipped unchanged) is in the pack: press its key and the camera flies free of your body to line up shots, then snaps back. Picked from the Jujutsu Five list.
- Creative only: out of creative or spectator, a Freecam left on turns itself off with a line on the action bar. Freecam is client-only, so the rule runs on each player's own game.
- It also turns off while one of our films plays (an ultimate's film, a beam clash), so it can't fight the film camera.
- Classes: `client/compat/FreecamCompat` (new, by reflection, so nothing breaks without Freecam). `build.gradle` packModsNoCf entry (CurseForge id still to look up from the home PC).
- Built; not played.

## 0.21.108 (2026-11-10): skyfalls caught after their water starts running

Andy (2026-11-10): "Ive still been seeing skyfalls. And we have all been in the first world tier". The load check of
`SkyfallCleanup` only saw water already pouring when a chunk loaded; a chunk made before 0.21.4 at the edge of sight could
load before its spring had run, and pour afterwards. Now the chunks within 10 of every player in a One Dimension world are
checked again every 10 seconds, so a fall that starts later is sealed too. The Dread gate (`SkySpringFeature`) now fails
closed: with Kingdoms installed, a tier it can't read counts as not Dread (before, a failed lookup let heavenfalls through).
- `logs/latest.log` now says whether the cleanup is on (and the Aether band's heights) once per server start, and names any
  fall it finds but can't stop, with the reason ("no source found in the Aether above it", too big, or a heavenfall kept).
Built, not played.

## 0.21.109 (2026-11-10): Giants: sprinting into a tree throws it, ape stomp shake scales with size (#1260)

- **Sprint hits throw trees**: a giant (Great Ape or other scaled-up player or mob) sprinting into a tree tears it up and throws it ahead. With Jaymo's Kingdoms the whole tree is thrown as a falling tree (`FallingTree.take`/`hurl`); without it the trunk breaks and its logs fly forward. Same build rules as walking into trees (players only where they may build, mobs only with mob griefing). `GiantTrample.sprintHit`, tuning in `SPRINT_*` constants.
- **Great Ape stomp shake** scales with the ape's size (`ZFighterLineage`).
- Not tested in game.

## 0.21.110 (2026-11-11): Giants haul: a lumbering sprint that smashes through, trees knocked flying (#1261)

- **The haul**: a giant's sprint is now a lumbering haul. It stays on its feet, takes long heavy strides that cover a lot of ground, and hardly swings its arms. The ground shakes with every step. The Great Ape no longer lifts off and skims over the ground (`ZFighterApeHaul`, `ZFighterLineage.APE_HAUL_STRIDE`). Towerback hauls too, faster than its walk (`Towerback.HAUL_STRIDE`/`HAUL_SPEED`).
- **Smashes through**: a hauling giant breaks whatever stands in its way above the ground it walks on. A quarter of the broken blocks drop. Obsidian and anything harder holds (`GiantTrample.smash`, `SMASH_*`). A player giant only does this where they may build, and a mob giant only with mob griefing on.
- **Trees go flying**: a tree a hauling giant runs into is knocked off its roots whole and flies on ahead, tumbling, faster the bigger the giant (up to 3 at once). With Jaymo's Kingdoms this uses a tornado's uprooting (`Trees.uproot`). A tree too big to send flying topples as usual. Without Kingdoms the trunk breaks and its logs fly ahead (`GiantTrample.ram`, `RAM_*`).
- **Wild villager apes haul too**: one chasing its quarry from more than 16 blocks off breaks into the haul (`VillagerApe.HAUL_FROM`/`HAUL_FACTOR`).
- **Fix**: the tree check now looks just ahead of the giant from its feet up. Before, it looked only around eye height, so a tall giant missed trunks at its feet.
- Not tested in game.

## 0.22.0 (2026-11-11): Powerpack 0.22.0

- The pack moves up to 0.22.0, at Andy's call. Nothing else changes in this entry.

## 0.22.1 (2026-11-11): armour in vanilla's style

- **Armour looks like vanilla's now** (built-in Vanilla Style pack, on by default): the Zanite, Gravitite, Arkenium,
  Neptune, Burrukai plate, Beast pelt, Bog iron, Caveman and Ichorcloth sets get clean worn textures with solid
  outlines and vanilla's shading in place of the old speckled noise. Metal sets start from vanilla's iron armour,
  hide and cloth sets from vanilla's leather armour, each painted in its own colours; Neptune, Burrukai and
  Ichorcloth keep their gold trim, Arkenium a pale trim, Beast pelt and Caveman fur edges. Caveman keeps its
  ragged scraps cut.
- Item icons redrawn the same way for the six Aether sets (24 icons). Bog iron and Ichorcloth icons were already in
  this style and stay. Caveman's icons keep their own ragged shapes (hide cap, shoulder pelt, loincloth, foot
  wraps), redrawn with clean outlines and shading.
- **Placeholder gear art redrawn**: Traceur Boots and Gloves icons no longer have a black square behind them;
  Training Weights and Heavy Training Weights are a pair of weighted cuffs; the Weighted Gi is a shaded orange tunic
  with its belt and weights. Worn textures for the Traceur Boots, Training Weights, Weighted Gi and Sealed Helm are
  redrawn too. Scouter, Rig, Air Skin and Void Crown were already clean and stay.
- Generated by `tools/vanilla_style/armour.py`; Excalibur pack's graded copies refreshed. Asked for by Tiv.
- Textures only; not seen in game.

## 0.22.2 (2026-11-11): downed animations for every kind of body (#1274)

- Andy: "can every mob type get its own set of at least 2 downed animations?" Each downed mob now gets one of at least
  two ways to lie for its kind of body, picked by the mob (it keeps the same one each time it goes down):
  - **People**: crawl face down (as before) or shuffle along on their backs feet first, heels digging in and hands
    pushing at the hips; lying still, on their backs (as before) or curled on their side, knees drawn up and arms
    wrapped over the middle, shuddering in their fits.
  - **Machines built like people**: on their backs shorting out (as before) or sat slumped and powered down, legs out
    ahead, folded over them, faults jerking the limbs and snapping the head up. Other machines topple onto their side
    or pitch onto their front.
  - **Beasts** (wolves, horses, cows, bears, modded animals): on their side paddling (as before, now lifting the head
    and twitching the tail) or sprawled on the belly, forelegs out ahead and hind legs back, head on the ground, now and
    then pushing the front up and sinking back. A crawling beast drags itself on its forelegs instead of sliding along
    on its side.
  - **Bugs** (spiders, silverfish, bees): flipped on their backs with legs curled up and twitching, scrabbling and
    rocking to right themselves, or flat on the belly with legs splayed, jerking one by one; crawlers haul on their
    front legs.
  - **Birds and flyers**: fallen on the side, one wing beating feebly, legs drawn up, or belly down with wings spread
    flat, beating at the ground and hopping in their fits.
  - **Fish and water life**: flopping on their side in bursts that tire over a minute, or belly up (always in water,
    bobbing and drifting).
  - **Slimes**: melted flat and quivering, or squat and hiccuping with hops that go nowhere.
  - **Golems, creepers, blazes and other lumps**: toppled on the side shuddering, or pitched onto their front.
- Every pose turns the whole body first, so modded models we don't know still lie right; legs, wings, head and tail are
  posed where the model uses vanilla's part names. Fresh Animations still steps aside for them.
- `client/wounds/DownedPose` (kinds and styles, the person and machine poses), new `client/wounds/DownedBodies` (the
  rest). Tuning numbers at the top of both.
- Built (compileClientJava); not played.

## 0.22.3 (2026-11-11): sparks off clashing steel, splintering shields

Andy (2026-11-11): "when swords clash or when parries occur there should be particle sparks", and "splintering wood from
shattering shields", in real fights as well as the film.
- **Sparks** (`combat/ClashFx`, `client/combat/ClashFxClient`): our own spark, in place of vanilla's crit stars. A shower
  flies out from the point the steel met, mostly to the sides of the blow, falls, bounces once and cools from white through
  orange to dull red, with a small flash at the meeting point. Every parry throws one (the shared weapon and shield parry,
  a Darktide's blade), bigger for a perfect parry, and a guard that breaks throws one too.
- **Every blocked blow** shows what it hit (`BlockFxMixin`): a few sparks off a blade's guard or a metal shield, chips of
  wood off a wooden one, for players and mobs alike. Arrows chip wooden shields too.
- **Wooden shields break apart** (`combat/ShieldShatter`): a player's shield that runs out of durability bursts into
  tumbling shards that lie on the ground a moment. A mob's wooden shield breaks after 28 points blocked (an axe counts
  double), the mob reels, and it takes up a spare four minutes later once it's out of the fight. Metal shields never break
  this way. What counts as wood: the `jaymospowers:wooden_shields` item tag, else any shield with no metal or stone in its name.
- For the films and other mods: `ClashFx.sparks`, `ClashFx.splinters`, `ClashFx.met`, `ShieldShatter.shatter` on the
  server; `ClashFxClient.sparks` and `ClashFxClient.splinters` with no packet on the client. Sprites from `tools/gen_clash_fx.py`.
- Compiled; not played. The spark and shard sizes, counts and speeds are constants at the top of `ClashFx` and `ClashFxClient`.

## 0.22.4 (2026-11-11): symbiote suit takes a worn costume's shape

- **The costume is the suit** (Andy: "any symbiote user with a costume has the symbiote take that shape for its suit
  instead, so it can be called on and off at will like the default suit does"). Anyone bonded to a symbiote (any strain,
  web slinger or not) who puts a costume on no longer wears cloth: the symbiote takes the design. The X menu's Suit tile
  calls it and withdraws it. Withdrawn, you look like yourself and your armour shows; called, you wear the costume in
  its own colours, with its mask, cape, emblem and suit-editor pieces.
- **Forming**: called, the tendrils wrap and the goo covers you as before, then the costume folds over the goo part by
  part where the plain suit used to. Withdrawn calmly, it goes back the same way. Torn off (the menu toggle, a
  rejection, a fight for it) it turns back into the strain's plain suit and tears away as it always did.
- **Bigger forms**: Living Armor keeps the costume with its tendrils and weapon arm; the big suit swallows it and it
  comes back when the big suit goes.
- **Mask and identity**: the mask key and the X menu's Mask chip both lift the costume's head piece. A face-hiding mask
  or hood only hides who you are while the suit is called (`SecretIdentity` follows the Suit flag).
- **First person**: your sleeve is the costume's while the suit is called, bare while it is withdrawn.
- Unchanged: `/costume off` and the Costume Table still hand the costume back; dying still leaves it on your body;
  a host whose symbiote leaves wears it as plain cloth again.
- Classes: `costume/Costume` (`symbioteShape`, `shown`), `client/costume/CostumeClient` (`SHAPE`, `forming`, `laid`),
  `CostumeModels.Worn`, `WebSymbioteLayer`, `WebSymbioteHand`, `WebSuitGrowth.suitLaid`, `WebSymbiote.set/toggleMask/unmasked`,
  `BodyCover.ownArms`.
- Compiled; not played. Expect tuning on how the costume folds over the goo, and the jump from costume to plain suit
  when it is torn off.

## 0.22.5 (2026-11-11): shields break by durability, axes wear them faster

Andy (2026-11-11): "The shields break when their durability is gone, and axe hits deal more damage to its durability than swords".
- **Durability only** (`combat/ShieldShatter`): a mob's wooden shield no longer has a hidden strength of 28 blocked
  points. Every shield, a player's or a mob's, breaks when its durability runs out and at no other time. A wooden one
  still bursts into shards when it does, and a mob that lost its shield still reels and takes up a whole spare four
  minutes later.
- **Axes** (`ShieldShatter.axe`, `AXE_FACTOR`): a blow from an axe (any weapon that knocks a shield aside) wears a raised
  shield three times as fast as a sword's of the same weight, wooden or metal, whoever holds it. Weapon guards are untouched.
- Compiled; not played.

## 0.22.6 (2026-11-11): karma - a life is weighed when it ends (#1291)

Andy: "a karmic alignment system, meaning your actions are weighed against you after death ... If you were evil you
reincarnate in the nether, one last chance to continue your life, however it will be extremely difficult. Or if you were
just you end up in the otherworld, allowed the chance to respawn instead of reincarnating, so good karma pays off. (You
will still have to fight in the tournament as a non z fighter)".
- **The weight** (`core/wounds/Karma`): a hidden score per life, -100 to 100, never shown. Up: monsters (+0.15), the
  undead (+0.25), bosses (+4), outlaws (+0.3), getting the fallen up (a player +5, a villager +3), a good name with lawful
  settlements (Kingdoms). Down: killing a villager (-8, a child -14), a player (-12; +2 if they were wicked), someone
  else's tamed animal (-4), and whatever costs a settlement's good name. Crossing into just (25) or wicked (-25) shows one
  vague line above the hotbar. The Judge will say how your page reads (five answers, no numbers).
- **When a life ends for good** (the death-by-cause roll is lost, or a timer runs out), it is weighed first:
  - **Just**: you wake in the Other World with a halo, Z Fighter or not. The fortune teller takes no coin from you; win
    the tournament of the dead and she sends you back as you were (to where you fell), at the cost of half your weight.
    One who never trained is lent a Borrowed Blade (an iron sword) and meets spirit fighters with 60% of their health.
    Not won when the Other World lets go (30 minutes): the life is over and you are born again.
  - **Wicked**, once in a life: the screen stays black ("You don't wake. You sink.") and you wake in the Nether at 30%
    health and 6 food, 140 to 220 blocks from a lit portal (the nearest one standing within 128 blocks of the spot under
    where you fell, or one built there). Every 45 seconds a line says which way its cold draught blows from. Piglins and
    everything hostile within 24 blocks come for you every 5 seconds. A death down there is final, with no roll and no
    wake. Leave the Nether alive and the life goes on as it was.
  - **Anything between**: born again, as before.
- A new life starts at 0. `/karma [player]` and `/karma <player> set <weight>` for ops.
- Rides on `death_by_cause`; the Other World needs `afterlife=true`. Lore: "The weighing" under The dead in Lore.md.
- Compiles (Gradle); not played. The numbers are first guesses.

## 0.22.7 (2026-11-11): Wind: Spiral Dive

- **Spiral Dive (Jung):** a Wind user with 2 or more abilities unlocked who taps X in the air, looking steeply down
  (50 degrees or more) with 6 blocks of clear air below, plunges in a spin with a spiral of cloud winding round them,
  steering a little with their aim. It works out of wind flight (the flight is dropped). Where they land a ring of wind
  runs out along the ground: everything near is shoved away and lifted, harder after a longer fall (full at 30 blocks),
  for light wind damage (2 to 5), and fire in the ring is blown out. Allies are spared.
- **Costs:** one hunger point, a 20 s cooldown (longer in the cold) that is spent even if the ring catches nothing, and
  the landing leaves the diver winded: rooted, no walking or jumping, for 0.6 to 1.5 s. Taking flight again more than
  half a second into the dive pulls out of it, with no ring.
- Fall rules are untouched: the dive only sets the diver's speed (as the Thunder Dive does), and it needs the same 2
  unlocked abilities from which a Wind user's falls stop hurting.
- **Changed for fully mastered Wind users:** a Tempest Kick aimed steeply down from 6 or more blocks up is now the
  dive. When the dive can't start (too low, cooling down) the tap is the kick as before. The Thunderhead's Thunder Dive
  stays on C.
- The X key's description mentions it. No new key, packet or client code: the tap is read in `WindKick`'s handler.
- Classes: `WindSpiralDive` (new), `WindKick` (the tap), `WindPower` (`dropFlight`, registration, stop on downed and on
  losing the power, `/powerdebug wind cooldowns`). `docs/wind.md` has the numbers and test steps.
- **Not built and not played.** The session that wrote it could not reach the build's download hosts, so
  `gradlew build` has not been run on this change; the PR checks are its first compile. No HUD chip for the cooldown.

## 0.22.8 (2026-11-11): cobalt ore and the knife metals - the four ingots nobody could get (#1297)

- **Why**: Andy: "All items need crafting routes that make sense". Cobalt, Manyullyn, Queen's Slime and Hepatizon ingots
  had knives, stats and traits but no ore, recipe, loot or trade: survival could not get one.
- **Cobalt Ore**: blue spots in the Nether's netherrack (`Tinkering.COBALT_ORE`, feature `ore_cobalt`: veins of 5, 4 a
  chunk, y 8 to 118). Iron pickaxe; drops itself. Smelts or blasts to a Cobalt Ingot, or melts in the smeltery for two.
- **Alloys** (`Melting.ALLOYS`, cast into ingots on a casting table):
  - Manyullyn: 3 cobalt + 1 steel makes 4.
  - Queen's Slime: 1 cobalt + 1 gold + slime makes 2.
  - Hepatizon: 2 copper + 1 cobalt + amethyst makes 2.
- Four new liquids (`Molten.COBALT`, `MANYULLYN`, `QUEEN_SLIME`, `HEPATIZON`): ingots only, no cast parts, since their
  parts are knife parts made from the ingot at the Part Builder.
- Assets from `tools/gen_knife_metals.py` (the ore is vanilla's nether gold ore with its spots recoloured).
- Tinkers' own Manyullyn takes ancient debris and its Queen's Slime magma cream; ours use steel and slime so no new
  liquid is needed. Change the lines in `Melting.ALLOYS` to retune.
- Lore: a `(new)` Cobalt paragraph under "The Nether and the demons" for Andy to approve.
- Built (Gradle compileClientJava clean); not played, the ore's spawn rate is a first guess.

## 0.22.9 (2026-11-11): The Retaking - advancement tab as the story (#1296)

- The "Jaymo's Powerpack" advancement tab is now **The Retaking**: the five Knowledge kings run down the middle as the acts (The Hearth, The Old Stones, Lights in the Sky, The Dark Below, Legend), each act's milestones hang off the king before it. Side branches (cooking, ships, alchemy, forging, executions) are unchanged.
- New advancements: Odd Jobs (three town errands), The Long Defence (find the book), and one per king. Sworn, the monolith, the Aether and Not the Last moved to their acts. Reaching the Aether is awarded by Kingdoms now (same criterion, so it stays earned).
- Data and lang only; not played.
- The Z Fighter training screen's **Story button is gone** (Andy): the story is the H menu's Story tab for everyone now. Sagas already under way still finish; side jobs still come from masters and the Keeper. The sagas return later as the fighter's own road in the Story tab.

## 0.22.10 (2026-11-11): The Retaking - Where the Five Stood (#1299)

- New Act II advancement **Where the Five Stood**: find the ford below the old keep in the wood (awarded by Kingdoms).
- Data and lang only; not played.

## 0.22.11 (2026-11-11): The Retaking, Act III: Lights in the Sky (#1301)

- **Five new milestones** in the Jaymo's Powerpack advancement tab, hanging off the Ogre King: Lights Put Out (beat a raider band), Fallen Star (break open a fallen star or star wreck), Above the Sky (fly a pod), The Maw (set foot in it), Not Our Sky (free or conquer a far world). They count only once Act III has begun; one done earlier has to be done again.
- **Raider's Tally**: a beaten raider band leaves a short book at its pod, the worlds the band has taken in its own few words. It files in the lore codex.
- Powers leaves a `jaymospowers.retaking.<deed>` tag on the player (`PackAdvancements.deed`); Jaymo's Kingdoms turns it into the milestone. Hooks in ZFighterRaiders, FallenStarBlock, StarWreckBlock, ZFighterPodTrip, ZFighterSpaceMissions.
- Built; not played.

## 0.22.12 (2026-11-11): The Retaking, Act IV: The Dark Below (#1302)

- **Five new milestones** in the Jaymo's Powerpack advancement tab, hanging off the Skeleton Lord before the Piglin King: A Voice Behind You (a spirit wakes for you), Back in the Ground (end your Bane with its own marks), The Deepest Dark (walk into the Abyss), A Page in His Book (ask the Judge how your page reads), Leashed (beat the Grue in its own dark). They count only once Act IV has begun.
- **Muster-Roll**: every demon village chest holds the village's muster-roll for the sorcerer's battle: who went up, what they were promised, who came home, and that they have not been called again. Yet. It files in the lore codex.
- New `core/LoreBook` makes these written lore books (the Raider's Tally uses it too). Asking the Judge leaves the `weighed` deed tag.
- Built; not played.

## 0.22.13 (2026-11-11): sound - ImmersiveFX in, varied sounds, the web sense turns your head (#1303)

- **ImmersiveFX: Resurrected** 1.9.0 (AGPL-3.0, Fabric 26.3, client-only) joins the pack unchanged: footsteps for each
  kind of ground, armour, wading and swimming sounds, biome ambience heard from where it is, crafting and workstation
  sounds, a stomach growl, auroras, fog, jets, and a sound browser (Ctrl+I). **AmbientSounds 6 and CreativeCore leave**
  (Andy's pick), so there is one ambience system; the build moves them out of the CurseForge profile. Its blood,
  fireflies and waterfalls are left on beside ours (Andy: "the more blood the better").
- **Varied sounds** (`client/sound/Varied`, `VariedSoundEngineMixin`): every one-shot sound in the world plays one of
  seven pitch steps around what was asked (2% apart), never the step it had last time, so the same clink or hit doesn't
  repeat note for note. Loops, music, voices, menu sounds, note blocks, bells, horns, records and Chronokinesis' clock
  ticks are untouched.
- **Web sense glance** (`client/power/web/WebSenseGlance`): when an ascended web slinger's Spider-Sense picks up a new
  danger out of sight (a creature that has just turned on you, a creeper starting to swell, a skeleton drawing), the
  look snaps round to it: the first-person view swings and others see the head turn. It adds to the mouse, stops once
  the danger is in view, lasts at most 12 ticks and waits 50 ticks between glances.
- Compiled (`compileClientJava`); not played. ImmersiveFX has not been run beside Sound Physics Remastered, Subtle
  Effects or Iris yet, and its doubled effects (fireflies, waterfalls, footprints) may want turning down in its config.

## 0.22.14 (2026-11-11): The Retaking, Act V: Legend (#1304)

- Three new milestones in the Jaymo's Powerpack advancement tab, hanging off the Piglin King before the Wither: A Light in the Keep, The End's Dragon, The Second Defence (Jaymo's Kingdoms awards them).
- Built; not played.

## 0.22.15 (2026-11-11): TEST: ImmersiveVFX in place of Iris and Complementary (#1305)

- A trial Andy asked for ("We can test this to see if it runs better, if not it gets scrapped"). **ImmersiveVFX 2.0.5**
  (all rights reserved, alpha, Fabric 26.3, client-only, shipped unchanged) joins the pack: pixel shadows, shore foam
  and caustics, clouds and sky, fire, ground fog, sand winds, auroras, heavier rain.
- Its page says it does not work with Iris, so **Iris and the Complementary Unbound default leave** while it is in
  (the build moves Iris out of the CurseForge profile), and **Sodium goes from 0.9.2 to 0.9.3-beta.4**, which it needs.
- No code changes. `ShaderPreset`, `LowEndMode` and Kingdoms' dark-night Iris mixins all check for Iris and do nothing
  without it; the Aether veil's Sodium mixin compiles against the beta.
- What to look at in game: frame rate against the old shaders, dark nights (they were dimmed through Iris), the Aether
  staying hidden from the ground, `/lowend`, and whether Bobby and the other Sodium mods still load on the beta.
- To scrap it, revert this PR: the comments in `build.gradle` say which lines.
- Compiled; not run. Nothing here has been started with ImmersiveVFX or the Sodium beta.

## 0.22.16 (2026-11-11): Magic FX: every effect has a type (#1306)

- Andy: "Every effect should have a type, (beam, blast, miasma, etc) and go to the same asset for each one, like the
  beacon for beams". New `core/fx/MagicFx` with `client/fx/MagicFxClient`: **beam** (the beacon beam: core and glow),
  **blast** (a swelling shell round a white-hot heart), **ring** (a shockwave over the ground), **orb**, **sigil** (a
  turning circle of marks on the ground), and two particles, **mote** (a glint of light) and **miasma** (a soft dark
  puff). Only colour, size and time vary. Dark colours draw as murk in every type.
- Every recoloured redstone dust in the powers, alchemy, baubles and Aether guardians is now a mote, or miasma if its
  colour is dark (Darktide's ink, gravity's shadow, flux, blight, gas). 22 one-off dust clouds (transformations,
  surges, wave impacts, self-destruct) and 27 recoloured firework flashes are blasts. The god ritual's pad beams are
  real beams; the full-ki burst and milestones throw a ring.
- Left alone because they aren't magic: the symbiote's goo, Hive, redstone charging the Rig, attack tells, leaves, gun
  muzzle flashes, tournament fireworks.
- Rules for new effects and what is still to move: `docs/MagicFx.md`. Textures: `tools/gen_magic_fx.py`.
- Compiles (Gradle, `compileJava compileClientJava`); not played. Lines and circles drawn as rows of dust inside loops
  are rows of motes for now, and END_ROD / ELECTRIC_SPARK style particles are a later pass.

## 0.22.17 (2026-11-11): Magic FX second pass: sparks, runes and real beams (#1310)

- Two new effect types: **spark** (a jagged fork that jumps every tick, for lightning and charge) and **rune** (marks
  from the sigil's alphabet, for enchanting and written magic).
- Vanilla particles that stood in for magic (end rod, electric spark, enchant glyphs, portal specks, witch, glow, wax,
  dragon breath, tinted potion swirls) now use the typed looks: about 540 places across the powers. Machines, rigs,
  sentries and real portals keep the vanilla ones.
- Dotted lines and circles from the shared helpers are now real beams and rings: the Xeno form's beams and rings,
  Rivet's lines, the Z Fighter gift line, the Arsenal streak, and the Sun patron's column.
- `docs/MagicFx.md` lists the new types, the stock looks (`MagicFx.LIGHT`, `CRACKLE`, `GLYPH`, `VOID`, `HEX`, `GLOW`,
  `EMBER`, `PALE`, `MURK`) and `MagicFx.line` / `circle`.
- Built; not played.

## 0.22.18 (2026-11-11): the minotaur, a boss of the Sentry Ruins (#1311)

- Andy: the minotaur "should spawn in the aether as one of the dungeon bosses".
- With Jaymo's Kingdoms in the pack, about a third of newly generated Sentry Ruins (35%) have Kingdoms' minotaur in the boss room in place of the Slider. It keeps to its room. While it lives, a player in the room shuts the doors, and they open again once the room is empty. Its boss bar shows to the players in the room. Its death opens the doors, breaks the way to the boss's crates and unlocks the walls, the same as the Slider's. Without Kingdoms every ruin keeps its Slider. Ruins already generated keep the boss they have. `aether/dungeon/RuinBoss`, `RuinPiece` (boss room).
- Built (`compileJava`). Not played: no ruin with a minotaur has been seen in game.

## 0.22.19 (2026-11-11): The Retaking: the Chrono's Road (#1307)

- **The Chrono's Road** in the Story tab, for every Chrono: end your own echo for good; go forward into the future; find one of the last Omen pilots and let them talk; spend long enough outside time and sleep through to morning (the Stopped Clock). Steps already done (an echo ended, a clock held) tick themselves.
- The first pilot you talk to presses some folded **Drawings** into your hand: four sheets of a great clock and the sweepers' parts, the maker's name scraped off every one.
- `StoryRoads` now gives Jaymo's Kingdoms several roads (the Fighter's and the Chrono's); only the Fighter's has its own screen behind Open.
- Built; not played.

## 0.22.20 (2026-11-11): The Retaking: the Fighter's Road (#1307)

- The Z Fighter sagas are back, folded into the Retaking: the character menu's Story tab shows **The Fighter's Road** under the five acts, listing each saga (done, the step you're on with its count, ready to begin, or not yet). Its Open button brings up the saga screen (now called Sagas) to pick a difficulty, start or give up a saga, and see side jobs.
- New client `StoryRoads` is what Jaymo's Kingdoms' Story tab reads (by reflection) for a player's own road; other powers' and callings' roads will join it.
- Built; not played.

## 0.22.21 (2026-11-11): The Retaking: the Web Slinger's, Xenoform's and Arsenal's roads (#1314)

- Three more roads in the Story tab, each for whoever has the power (steps already done tick themselves):
  - **The Web Slinger's Road**: bond with a symbiote at a meteor crater; grow close enough (bond 60) that it stops fighting you; purify it white. At bond 60 it writes **What It Remembers** on a blank page for you.
  - **The Xenoform's Road**: hear a star-reader out; set foot on the Pale Moon; have your band reforged; tear the rival band off the band hunter, who drops his **Orders** as he runs.
  - **The Arsenal's Road**: claim a relic; mark a place; pull something from the future (the first time, a Los Perrito parade **Notice** comes through the rift with it); pin a weapon.
- New `PowerRoads` holds these roads and their saved, synced progress (`power_roads`).
- Built; not played.

## 0.22.22 (2026-11-11): sky villages, ruined villages and the sky lord's castle in the Aether (#1312)

Andy, on the Sky Villages mod's ideas (ARR, nothing of it used): "should all instead spawn in the aether proper, and be
filled with divine born", "Yes but now aether material", castle "in that scale, out of the relevant material".
- **Sky village** (`aether/structure/SkyVillages.village`, 47 by 47): a round green with paths and a flagged plaza, a well
  of light, four homes (two villagers each, bedrolls, a chest), a kitchen (Kitchen's stove, cabinet, pot and crates when
  Kitchen is installed; smoker, barrel, cauldron and hay if not), a workshop (crafting station, part and pattern chests,
  layer blocks), a windmill with cloth sails, two farm plots, and a mooring mast with a balloon skiff tied to it (a chest
  in its hold). Three palettes by the land: skyroot (highfields), wisproot (magnetic), greatroot and icestone (arctic).
- **Ruined village** (variants 3 to 5): the same village abandoned. Walls and roofs fallen in more the higher up, moss on
  the stone, vases where the furniture was, no skiff, three swets and two cockatrices living in it.
- **Sky castle** (`SkyVillages.castle`, 49 by 49): a two-thick curtain wall with a walk and merlons, four corner towers
  with ladders and cloth caps, an arched gate, a keep with the lord's hall and seat, rooms above, a walled roof and a
  spire. Eight villagers, one tagged as the lord.
- New plans `village` and `castle` on `jaymospowers:aether_structure`; one structure set `sky_villages` (spacing 30,
  separation 14, kept 6 chunks from outposts). Loot: `chests/sky_village/home`, `skiff`, `keep`. Data written by
  `tools/gen_aether_structures.py`.
- Villagers are plain villagers tagged `jaymospowers_sky_villager` (`jaymospowers_sky_lord`); MCA turns them into its own,
  and Kingdoms gives them their blood.
- The skiff hangs a step clear of the landing and, with Jaymo's Kingdoms installed, has a ship's wheel by the gap in
  her rail: use it to cast her off as a balloon ship. Kingdoms also crews her, names the castle's lord and wardens, and
  dresses the sky folk.
- Compiled; never generated in a world, so the layout has not been seen in game.

## 0.22.23 (2026-11-11): Divine-born: the feather sense, Alight, hover, and staying at the Judge (#1308)

Andy: "Divine born should be like z fighters but without the raging raw ki, it is controlled and elegant, they gain a
web like sense that allows them to dodge incoming attacks and counter, as long as they have feathers. They can directly
replenish feathers by using their heritage key."
- **The sense** (`divine/DivineSense`): every Divine-born, Z Fighter or not. A melee blow or a projectile that would
  land misses instead, for 2 feathers, and they drift a step aside. Nothing is pressed. Not more than once in 10 ticks,
  and not at all once the feathers are spent. Explosions, falls, fire and the like still hurt. A web slinger keeps the
  web sense instead.
- **The answer**: for 2 seconds after a slipped blow, their next melee hit on whoever threw it does 1.5x and gives
  2 feathers back (`DivineCounterMixin`).
- **Scaling** (Andy: "they deal more damage with the counters as they increase in level and they can train it for higher
  increases"): the answer gains 1% per experience level (up to +50%) and 15% per rank of practice. Practice comes from
  use: 1 for a blow slipped, 3 for an answer landed; five ranks at 40, 120, 280, 560 and 1000, each with a line over
  the hotbar. At best the answer is 2.75x.
- **Alight** (the heritage key `;`, sent by Kingdoms): they rise a little, hang still, feathers of light wind down round
  them and the row refills, one half-feather every 4 ticks (every 2 under open sky or in the Aether). Sneak, the key
  again, being hit or a full row ends it; 20 s before the next. The sense is shut while it lasts.
- **Hover**: Jump pressed again in the air and held slows the fall to a drift, for a half-feather every 8 ticks, with
  no fall damage (`client/divine/DivineSenseClient`).
- **Staying**: one of the dead whose page weighed just gets a new choice at the Judge, "Ask to stay". Saying yes ends
  the stay among the dead (no hold at the hall, no road home, no tournament) and keeps the page's weight. Their
  heritage does not change (Andy: "they just get the buffs and the halo"): they keep the ring over the head, get all of
  the above, and Kingdoms gives them the Divine-born's buffs. They are free to walk the Aether.
- All numbers are constants at the top of `DivineSense`. Compiled; not played.

## 0.22.24 (2026-11-11): Epic igloos and jungle temples; the Sentry Ruins are the Slider's again (#1320)

- **New in the pack**: Epic Structures: Igloo 1.0.5 (5 large and 7 small igloos, cellars under the large ones) and
  Epic Structures: Jungle Temples 1.0.3 (temples, large temples and temple ruins). Both are all rights reserved with
  modpack use invited, so they are linked unchanged, like Epic Villages, Dungeons and Witch Huts. They only add to
  chunks nobody has visited yet.
- **Sentry Ruins**: new ruins always hold the Slider. The minotaur has moved to the Epic dungeons under the Overworld
  (Kingdoms' `Labyrinths`). A ruin already built with a minotaur keeps it, and its doors and crates still work.
- Compiled; not played.

## 0.22.25 (2026-11-11): sky towns - whole towns on Aether islands (#1321)

Andy asked for Epic-village-scale towns in the Aether's style. Epic Villages itself is all rights reserved, so these are
our own.
- **Sky towns** (`SkyTowns`, structures `sky_town_highfields`, `sky_town_magnetic`, `sky_town_arctic`): laid out lot by
  lot around a paved plaza with a bell, a well and stalls. Each has a galleried great hall with a spire, a library, a
  smithy, a tavern, a workshop, a windmill, tall and low houses, terraced farms and a mooring mast with a skiff, built
  from the island's own stone and wood. A town needs at least 14 level lots, so it only stands on big islands.
- Rare: one try per 48 chunks, and never within 8 chunks of a sky village.
- The pieces name themselves (`great_hall`, `library`, `weaponsmith`, `farm`...), so Jaymo's Kingdoms reads a sky town
  like any Epic village and founds a kingdom there.
- `/place structure jaymospowers:sky_town_highfields` puts one where you stand.

## 0.22.26 (2026-11-11): Gremlin is a tiny bug-eyed imp (#1323)

- **Gremlin's look** (Andy: "very small with bug eyes"): the Xeno-Band's Gremlin is no longer a hunched knuckle-walker with
  a huge skull and floppy ears. It is a tiny upright imp about half a block tall: a slim grey body on thin legs with
  broad pale feet, thin arms, and a wide flat head with two big yellow-green eyes that bulge out past the sides of it.
  The eyes shine in the dark and blink.
- **How it moves**: it patters on quick little steps and throws its arms back at a run, its head wobbles and tilts when
  it stands still, a bite darts the head and drops the jaw with a swipe of the right arm, a leap throws the arms up, and
  sneaking it squats on splayed legs. The insignia is on its chest now (it was on its back).
- Its moves, size (a third of a player, fits one-block gaps) and health are unchanged. Swarm, its Ultimate, keeps its
  hunched plated build.
- `XenoFormModels.Gremlin` (new mesh and pose, `GLOW`), `XenoformLayer` (the eyes' fullbright pass), `XenoformHand`,
  `tools/gen_xenoform.py` (`gremlin()` and `gremlin_paint()`, seeded by itself; `xenoform_gremlin_glow.png` is new).
- The Gremlin is the shape of the Ocellan, the band's makers (Kingdoms draws the star forge's smith as one, robed).
- Compiled (`compileClientJava`) and rendered from the skin outside the game; not played.

## 0.22.27 (2026-11-11): the Verdant World's far country - mycotic forests, the pulse forest, the stone garden and a ringed giant in the sky (#1324)

Andy's Eden Ring picks, part 1. Eden Ring (paulevs) is MIT, so its art is used, with its licence in
`tools/eden_ring_license.txt`. No portal and no new world: these are regions of the Verdant World.
- **The Verdant World has regions now** (`dimension/verdant_world.json`): plains as before, with four new biomes laid
  out by climate. Only chunks made after this update get them.
- **Mycotic Forest and Old Mycotic Forest**: pink mycelium turf, mycotic grass, balloon mushrooms (thin stem, domed cap),
  and in the old forest huge branched ones on full logs with glowing lanterns hanging under their caps. A small balloon
  mushroom grows into a tree with bone meal. Lanterns come in 16 colours: craft one with any dye to recolour it.
- **Pulse Forest**: pulse trees, thin glowing stems carrying a big, a medium and a small cap, some on a glowing log trunk.
  All of them brighten and dim together. They stand like bamboo: cut the foot and the rest comes down.
- **Stone Garden**: turf worn through to moss-capped stone and gravel, wind-worn banded pillars and layered boulders.
- **Wood**: balloon mushroom and pulse tree logs and planks (planks also from stems). Stairs, doors and the rest of the
  sets come in a later part.
- **The sky** (`VerdantSkyClient`): a blue giant with a ring hangs in the same place over the Verdant World, faint by
  day and bright at night.
- Code: `eden/Eden` (blocks), `eden/EdenFeature` (ground, trees, rocks; tuning constants at the top),
  `eden/PulseTreeBlock`, `tools/gen_eden.py` (all models and data).

## 0.22.28 (2026-11-11): Boxing: Hand Wraps and the punch

- **Boxing, first part (Jung's plan, `docs/Boxing-Plan.md`).** Fist gear and the punch are in; nothing teaches a
  style yet, so for now only gamemasters can box (`/powerdebug boxing style|level|status`).
- **Hand Wraps:** two rags or cloths (Clean Rag, Torn Cloth, Hemp Cloth) in the crafting grid. A weak weapon by
  themselves; with a style they are what lets you box.
- **The punch:** with fist gear and a style, a left click inside 2 blocks is a punch for 1.5 at every level, with little
  knockback. Arrow Slit throws a one-two then hooks, Long Guard jab-jab-cross, Bastion the right jab.
- **Levels 1 to 10** make punches quicker (one every 0.8 s down to 0.35 s; the attack indicator shows it) and cheaper
  on feathers (one icon a punch down to four punches an icon), never stronger. From level 8 a boxer's punches land
  faster than the usual hit immunity allows.
- **Gassed:** out of feathers, a boxer punches once every 1.6 s for 45% less (2 at most) until the row is 30% back.
- **Names:** Arrow Slit, Long Guard and Bastion (Jung's names, kept by Andy).
- Classes: `boxing/Boxing` (styles, levels, the punch), `boxing/BoxingGear` (Hand Wraps, the `boxing_gear` tag),
  `boxing/BoxingDebug`. `docs/boxing.md` has the numbers and test steps.
- Not in it: evades, strong attacks, blocks, blur, the combo breaker, looks, cestus and bracers, teachers, HUD, hints.
- **Not built locally, not booted with `runServer`, not played.** The session could not reach the build's download
  hosts; the PR checks are its compile. It adds an item, so it needs a server boot before merging. The Hand Wraps
  texture is a placeholder.
- Review fix: the punch takes the left click only after every other rule (a downed or armless player, a finisher,
  Kingdoms' tag, the fight pit, captives), in a later event phase. Before, a boxer's punch skipped them all.

## 0.22.29 (2026-11-11): Boxing: the cestus and the bracers

- **Cestus** (leather, iron, steel): fist gear that makes a styled punch hit harder, 2, 2.5 and 3 against 1.5 in Hand
  Wraps. **Bracers** (leather, iron, steel): fist gear for blocking; they punch like wraps, and their block comes with
  the blocks patch. Like Hand Wraps, all of it lets a boxer use their style.
- **Making them:** leather ones in the crafting grid round Hand Wraps (2 leather for the cestus, 4 for the bracers).
  Iron and steel ones are forged like other metal weapons: Cestus Studs or Bracer Plates and a Tool Binding at the
  Tool Station. No gold or diamond.
- Classes: `boxing/BoxingGear` (the items and `punchBonus`), `boxing/Boxing` (the bonus in the punch),
  `tinkering/WeaponParts` (four new kinds). Art from `tools/boxing_assets.py` (placeholder, drawn for this).
- **Not built locally, not booted with `runServer`, not played.** It adds ten items (six gear, four heads), so it
  needs a server boot before merging. Untested in particular: the whole forging route for the new heads.

## 0.22.30 (2026-11-11): Boxing: evades, strong attacks, blur and the combo breaker

- **Evades:** while boxing, the Dodge key (Left Alt) is the style's evade in place of the roll. Arrow Slit slips left
  or right or bobs straight in, Long Guard pulls back, Bastion rolls the shoulder in place (or slips aside). A blow
  that would land within 0.4 s of the press misses, and the next evade is ready 0.7 s after it. Two evades cost a feather icon at level 10, one each at
  level 1.
- **Perfect evade and strong attacks:** an evade pressed inside the guard's perfect-parry window (5 ticks, 7 when
  locked on) earns the next punch: the uppercut out of a bob (4), the pull counter out of a pull (3, a block more
  reach and a hard shove), the roll straight out of a shoulder roll (5). A shoulder roll that isn't perfect lets half
  the blow through and earns a 2.5 straight. A strong attack costs one icon at every level.
- **Blur and daze:** those strong attacks blur a player's sight for 1, 1.5 or 2.5 s (a pale haze under the HUD; a key
  swaps it for a dark frame for players who get motion sick), not again for 5 s. Mobs are dazed for as long, the
  undead never, a boss 1.5 s at most.
- **Combo breaker:** six punches landed in a row on a player or a boss and they are shoved back; that boxer can't
  reach them for a second, and their blur clears.
- A click that comes too early is kept and thrown in its turn. A gassed boxer gets Mining Fatigue and can't evade.
- Classes: `boxing/BoxingEvades` (new), `boxing/Boxing` (the punch takes strong attacks, the breaker, the queue),
  `client/boxing/BoxingClient` (new: the evades' movement, the blur), `client/stamina/DodgeClient` (the Dodge key
  hands over to boxing). `docs/boxing.md` has the numbers and test steps.
- Not in it: **the blocks** (right click with fist gear), which need their own patch through `combat/Guard`; the
  breath puff, poses and idles; teachers; the HUD.
- **Not built locally, not booted, not played.** The haze is a wash of colour, not a true blur.

## 0.22.31 (2026-11-11): Boxing: fist gear worn on the hands

- **Hand Wraps, the cestus and the bracers are worn, not held**, like the Fang Gauntlets: holding them hides the item
  and draws the gear on the hands, for everyone and in your own first person. Wraps wind round the hand, a cestus adds
  a studded band over the knuckles, bracers run up the forearm. With the off hand empty, both hands wear it.
- Holding fist gear also raises the arms into the Fang Gauntlets' fists-up guard when nothing is swinging (the
  styles' own idles come later).
- Classes: client `armory/FangGauntletsClient` (boxing's shapes and textures beside the gauntlets', through the same
  mixins), `boxing/BoxingGear.wornIndex`. Textures `textures/entity/boxing/` from `tools/boxing_assets.py`
  (placeholder).
- **Not built locally and not seen in game.** The shapes' sizes and the textures' layout were set without a render.

## 0.22.32 (2026-11-11): Boxing: the block

- **Fist gear blocks on right click**, through the pack's weapon guard (the block, and the parry in its first ticks).
  What it stops is the gear's own, more with each step up: Hand Wraps 50%; cestus 55 / 60 / 65% (leather, iron,
  steel); bracers 65 / 75 / 85%. A sword's guard stops 60%.
- **A boxer's block costs by the blow**, whatever its weight: one feather icon, an icon and a half in the Arrow Slit.
  Better gear takes less: cestus 95 / 90 / 85% of that, bracers 90 / 80 / 70%. Without a style, fist gear blocks at the
  usual cost.
- **The combo breaker** no longer counts a punch that ran into a raised guard from the front.
- Classes: `combat/Guard` (two small hooks: the gear's share in the guard's component, the boxer's cost when a blow is
  blocked), `boxing/BoxingGear` (`blockShare`, `blockCostShare`), `boxing/Boxing` (`blockCost`).
- **Not built locally and not played.** Untested in particular: that fist gear raises the weapon guard at all (it
  should, being a plain weapon), and that every other weapon's guard is unchanged.

## 0.22.33 (2026-11-11): Pocket Arsenal pulls from anywhere

- **Someone's pack**: until the power is ascended, about 1 Reach in 25 with another player within 24 blocks goes into their inventory instead and comes back with one of their weapons, tools or armour pieces (never what they're holding, nothing stackable or placeable). They see "Something reached into your pack and took your ...". The thief gets it as an ordinary summon; the owner gets the real one back when that summon's time is up (75 s after the pull, or on their next login if they left), whatever happened to the summon (sent back early, broken, dropped).
- **Hazards**: about 1 Reach in 30 (not yet ascended) the hand closes on something that hurts: embers (fire), a pufferfish (poison and nausea), a wither rose (wither), a cactus, powder snow (freezing) or a lit charge that goes off in the hand (breaks no blocks). Nothing is kept.
- Once ascended (the Armory's pin) neither happens by accident: the rift answers to you. Instead, look at a player within 16 blocks and hold R for a second (motes gather on them) to reach into their pack for a random one of their things, returned the same way. Let go sooner and it's an ordinary Reach; look away and it's called off. Future handguns stay as they were (1 Reach in 600).
- `ArsenalAnywhere` (new), `ArsenalPower` (reach roll, pull, lender tick), lang `arsenal.json`.
- Built; not played. Not tested: the return after a server restart, the charge's damage on armoured players.

## 0.22.34 (2026-11-11): Rig: frames, Rig limbs, helm view, multi-lock, Core energy, flight feel, Forge hologram, sentry, paint (#1322)

- **Frames (`RigFrame`, stamped on each piece as `Rig.FRAME`):** the Rig Forge builds the suit in four frames. Standard is the suit as it was. **Scrap** is crude and heavy and costs about half (the first build you can afford): +1 armour, slower on the ground, thirsty in the air, shots weaker. **Shade** is dark and quiet with thin plates: -2 armour, a little quicker, hover drains less, and mobs notice you late (a quarter-ish of their sight range, less again sneaking; `rig/mixin/RigVisibilityMixin`, config `jaymospowers.rig.mixins.json`). **Siege** has thick plates (+4 armour, +2 toughness), a shoulder gun (the launcher fires two more missiles that burst bigger) and is slow in the air. Armour, toughness and walking speed are attribute modifiers in `RigPassives` (a share for each piece worn); air speed is read by `RigClient`, hover drain and shot damage by `RigActives`. Each frame has its own suit texture and glow colour (`tools/gen_rig_frames.py`).
- **Forge screen (`RigForgeBlock`, `RigForgeEntity`, `client/rig/RigForgeScreen`):** right-click the Forge with a free hand for a screen in the Costume Table's style: a preview of you in the whole suit in the picked frame and paint (drag to turn it), a button for what to build (the four pieces, a Rig arm, a Rig leg) and one for the frame with that frame's numbers under them, two rows of dye swatches for the plates and the trim (plus bare metal), what the build costs against what you carry, and Paint and Build. A new paint takes one dye of that colour from your pack and also repaints the pieces you wear. Salvage in hand still feeds the Forge (it holds up to 30 points, spent first), a dye in hand still paints (Sneak: trim), a water bucket washes the paint off, and a worn-down piece in hand is refitted.
- **Forge hologram (`client/rig/RigForgeRenderer`):** the suit stands over the Forge as a turning, flickering figure of light in the picked frame's build and the chosen paint; the piece (or limb) the Forge is set to build lights as salvage is fed in and shimmers when it can be built.
- **Paint:** two dye colours, plates and trim, chosen at the Forge. The trim is drawn as its own tinted, lit pass over the suit (`suit_trim.png`).
- **Rig limbs (`Prosthetics`, `RigLimbs`):** a Rig arm and a Rig leg, built at the Forge in a frame, fit through the existing prosthetic path (use on yourself where a limb is missing; stronger than iron, 0.9 / 0.95; a Rig limb remembers its frame and drops as an item with it when the real limb grows back). With **no suit worn**, G fires the arm's repulsor shot and V hops off the ground with the leg's thruster (in the air, a forward kick that hurts and throws). Frame flavour: Scrap hits weaker and throws harder, Shade is silent, Siege bursts where it hits and kicks hardest. They cost a little hunger, no charge. Drawn on the body (`ProstheticLayer`, `rig_limb.png`).
- **Helm view (`client/rig/RigHelmView`):** with the faceplate down, in first person, the visor draws over the world (a deliberate exception to the corner-HUD rule, Helm only): a horizon line that follows the view, height over the ground, altitude, speed and climb, corner brackets with a health bar on what you look at, arrows round the middle toward incoming harm (projectiles flying at you and hostile creatures close in that have you as their target), a small figure of the suit with each plate coloured by its wear, and the frame's name.
- **Multi-lock missiles (`RigActives.volley`, `RigNet.Volley`):** with no power, hold V (Helm worn, Mind level 2): after a moment the view marks the creature nearest the crosshair every tick it changes, up to six, with numbered brackets; letting go sends a missile to each (costs the launcher's charge plus 40 per extra target). A tap still fires the pair.
- **Energy into the Core (`RigCharge`, `RigActives.overcharge`):** a share of fire damage (4 charge per point) and magic damage (6 per point, also dragon's breath) to a suit that is worn goes into the Core, scaled by how many pieces are on, at most 60 a hit. A lightning strike still fills the Core and now **overcharges** it for 12 seconds: palm blasts and missiles cost nothing, hit 1.8 times as hard, burst 1.5 times as big and recover twice as fast; the HUD's charge bar and the helm view turn gold.
- **Flight feel:** hovering from the ground launches in a burst (a flame ring and blast, a screen shake, a hard lift that dies away over 8 ticks); the boot flames now swing with the steering (they trail opposite your heading and kick out against a turn); the palms-out hover and arms-back cruise poses were already there. A fall that would have hurt (4 damage or more) is caught by a full set with 40 charge to spare: **one-knee landing** (`RigLanding`, pose `RigMoves.KNEE` in `RigPose`), the ground cracks round you (block-breaking cracks that fade), the screen shakes for those near, and what stands close is thrown back and hurt. The jets, palm blast beam, missile trails and the other new effects go through `MagicFx`.
- **Sentry (`RigSentry`):** Sneak + the call key (or the Rig menu's new Sentry entry) with the suit on, Core at Mind level 3: the suit is parked as ever and an armour stand wearing a copy of it is left where you stood, which cannot be hurt or stripped. While you are online in the same dimension it shoots the nearest hostile creature within 14 blocks and whatever last hurt you within 40 (5 damage a shot, once a second), and faces its target. Calling the suit takes the stand away and the plates fly in from where it stood.
- **Plates fly in (`RigCall.FLYING_IN`, `RigSuit`):** a called suit's plates are scattered round and above you and each flies in and locks on in the build order (Core, Arms, Thrusters, the faceplate last) instead of swinging in from its folded place.
- **New keys:** none new. G and V (powerless) gain the limb actions with no suit on; V held marks a volley; Sneak + C leaves the suit as a sentry; the Rig menu has a ninth entry, Sentry.
- Built and compiled; not played. To check in game: the Forge (feed, the screen's preview, piece, frame, paints and Build, the hologram's turn, lighting and paint, building a Rig arm and leg); fitting a Rig arm / leg and G / V with no suit on; each frame's feel (Scrap speed and armour, Shade mobs noticing late, Siege missiles and slow air); hold V and sweep over several mobs; a lightning strike with the suit on (overcharge) and being burned or hit by magic (charge goes up); the launch burst and the flames swinging as you turn; falling 10+ blocks in the full set (knee landing, cracks); Sneak + C then walk away (the stand shoots mobs; C calls it back and the plates fly in); the helm view (faceplate down, first person: horizon, brackets, arrows, the wear figure). Unsure at runtime: particle velocity with count 0 for the MagicFx motes, the block-entity renderer's pose and brightness, the plate scatter distances, and the kneel pose's offsets.

## 0.22.35 (2026-11-11): Smoke and Flame Avatar track: the skull, the burning chain, Judgement, burning steeds (#1322)

Ideas only from another mod's fire rider (All Rights Reserved): every line of code and every texture here is our own.
Built and compiled, not played. Check in game: the skull fades in over the char between 55% and 85% flame and back out as
the bar drops; a Smoke user's flail and whip light what they hit; sneak + Z on a guilty player; a steed's ride over lava.
- **The skull under the fire**: as a Flame Avatar's flame bar burns high the face burns away to a bare skull wreathed in
  fire, and comes back as the flame drops (`avatar_skull.png`, drawn over the char in `FlameAvatarClient`).
- **Burning chain**: a Smoke user's flail and whip burn. Every blow sets the creature alight, the whip's wrap keeps it
  burning, and the flail's whirl no longer knocks things away: it wraps what it clips, drags it in and swings it round
  you. The head glows with embers (`Lash`).
- **Judgement**: an Emberborn who has become a Flame Avatar and has Smoke can seize a creature's eyes (sneak + Z, held, 16
  blocks) and let the harm it has done burn it, through armour, for up to 3 seconds. A player burns by the weight of
  their life (killing villagers and players, crimes against a realm, outlaw standing); an outlaw burns for half, an illager or anyone riding with a raid (not the ravager) for three quarters;
  everyone else feels nothing, and the move does not start (`AvatarJudgement`, `Karma.guilt`).
- **Burning steeds**: horses with a mane and hooves of fire, found at the Flame Avatars' camps in the Nether (Kingdoms
  raises two at each new camp and keeps up to three). They are tame, saddled, 35% faster, immune to fire, leave a trail
  of flame, and gallop over water and lava. Only a Flame Avatar or a Smoke user can ride one; anyone else who climbs on
  is set alight and thrown (`BurningSteed`).

## 0.22.36 (2026-11-11): Speedster track: gears, momentum, healing, rapid hands, held phase, rings, slow time, marked run (#1322)

Overclock (V) and Velox's Velocity (R) share one speedster core; everything below works for both unless it says otherwise.
Ideas only from another mod's speedster (All Rights Reserved): every line of code here is our own.
- **Speed gears**: scroll the mouse wheel while sprinting at speed to shift between 5 gears (the hotbar keeps the wheel at
  a walk). Gear 3 is the old speed; higher gears are faster, turn wider, burn more hunger and leave a longer streak. The
  gear shows on a chip bottom right above the power's own block (`ChronoGears`, `ChronoGearsClient`, `ChronoSpeedHud`, `ChronoScrollMixin`).
- **Momentum, skids, drift turns**: speed builds over the first strides and holds on a straight line; a sharp turn sheds
  speed (more in a high gear) and leans the body into it; letting go of every key slides you to a stop in a spray of the
  ground's dust (`ChronoMomentumClient`, lean drawn by `ChronoRunPose`).
- **Speed healing**: gears 3 to 5 close wounds as healing magic does (through `Mending`, so limb wounds are respected) for
  far more hunger than rest.
- **Running blow**: a melee hit at a sprint deals up to triple damage by speed and gear and shoves the target along your path (`ChronoRapid`, `ChronoRunBlowMixin`).
- **Rapid bows**: full draw after 4 ticks; crossbows load in a tick (`ChronoRapidBowMixin`, `ChronoRapidCrossbowMixin`).
- **Catch and hurl**: at speed, use while aiming at a shot in flight within 6 blocks plucks it from the air and holds it
  before you (a gold bar shows the hold); use again to throw it back at 2.2 times its speed.
- **Weapon snatch**: sprinting past an armed mob and pressing use takes its main-hand weapon (into your hand, your pack, or the ground). Never players.
- **Held phase (Overclock only)**: hold V with a wall ahead to vibrate through blocks for up to 5 s (`ChronoPhase`,
  `ChronoHeldPhaseMixin`). Floors hold, unbreakable blocks hold; let go inside a block and you are put out at the nearest
  clear spot; running dry inside one costs 2 hearts; a cooldown follows. Replaces the old blink.
- **Running rings**: running a tight closed circle (radius 2 to 6, round in about 2.5 s) raises a vortex that lifts the creatures inside, spins them and puts out fire (`ChronoRings`).
- **Slow time**: hold `.` at speed and the world drops to a fifth of its pace while you and other players move normally,
  for as long as a meter lasts (`ChronoSlowTime`, vanilla's tick freeze with a stepped tick; no real slowed tick rate, which would slow the runner too).
- **Marked run**: Sneak + `/` marks the place you stand (a column of light shows it); `/` later runs you there by yourself
  along a path around obstacles, within 128 blocks on loaded chunks; any movement key cancels (`ChronoSpeedKeys`, `ChronoMarkRun`).
- New keys (rebindable): slow time (`.`), mark / run to the mark (`/`).
- Built and compiled; not played. To check in game: the scroll mixin and gear shifts, the bow and crossbow mixins, walking through walls without rubber-banding (and the way out), the lean direction on turns, slow time's stepped tick, and the marked run's path on stairs and ledges.

## 0.22.37 (2026-11-11): Web Slinger: ledge launch, glide kick, hanging and web-up, perch, silent takedown, fear, cloth capes (#1322)

- **Ledge launch (Zip):** a Zip aimed at a roof edge or ledge (a side face with open air above its top within about two blocks of the aim point, or the top face of a block with a drop beside it) no longer stops at the wall: it hauls you all the way in and throws you up and over the lip, about three blocks above it, so the web wings can open straight away. `client/power/web/WebLedge` (tuning at its top), hooked into `WebSwing.startPull`, `tickPull` and `finishPull`.
- **Glide kick:** gliding on the web wings into a living creature at speed (0.85 blocks/tick or more) lands a both-feet kick: damage `3 + 5 x speed`, half again out of a dive, the target knocked down (`Downed.knockOut`, or a Darktide stun for what cannot go down) and you bounce back up into the glide. Everyone sees the kick pose (`WebPose.GLIDE_KICK`, drawn in `WebBodyPose`). `power/web/WebGlideKick`. Players are only kicked where PvP is allowed.
- **Hang upside down:** holding still (16 ticks, no movement keys) on a swing line anchored well above you (a ceiling, branch or overhang) turns you head down for everyone (`WebPower.UPSIDE`, `WebPose.UPSIDE`); Jump and Sneak still reel you up and down; moving, landing or letting go puts you upright. `client/power/web/WebUpsideClient`, packet `WebNet.Upside`.
- **Web-up on B:** while hanging, B on a creature, player or corpse below in reach (crosshair, else the nearest below) hauls it up to the ceiling over it. A downed or helpless one (or a corpse) is stuck flat against the ceiling in cobwebs (pinned for a minute); a standing one is left hanging upright on a line (`WebHitches`, so a player can still cut it by swinging) wrapped in cobwebs (`WebTraps.cling`, `ensnare`). Mobs and players. `power/web/WebHanging`.
- **Perch:** landing on a fence post, wall top, narrow top, webline plank or the edge of a drop settles you into a balanced crouch, arms out, with nothing sliding you off until you move, jump or swing (`WebPerchClient`, `WebPerch`, `WebPower.PERCH`, `WebPose.PERCH`). Spider-Sense reaches 1.5 times as far from a perch (`WebAscension.sense`).
- **Silent takedown:** Sneak + attack bare-handed from behind a creature that is not after you (within 2.6 blocks, in the rear of its facing), no hit streak needed: it is muffled and cocooned, down for 30 s, with no sound, and nothing nearby is alerted (`WebTakedowns.silent`; `WebTraps.ensnare` has a quiet form).
- **Fear of the strung up:** humanoid hostiles (zombie, skeleton, illager, piglin, witch) that see a strung-up or cocooned comrade lose their nerve once per sight: with Jaymo's Kingdoms loaded, by temperament (timid and gloomy flee, brave, hotheaded, proud and spiteful keep coming, the rest bunch up; `KingdomsCompat.temperament`, by reflection), else by a per-mob roll (40 flee, 30 bunch, 30 hold). `power/web/WebFear`.
- **Cloth capes:** the symbiote Mantle, the costume capes and the Archers' hooded cloak's hem hang as cloth: the cape is cut into hinged strips, each a damped spring following the one above, so it lags the body, swings out on turns, floats up in a fall and fans wider on a glide on the web wings. One tiny client-side state per wearer, stepped once a tick and interpolated at draw time. `client/costume/ClothCape` (used by `WebBodyLayer`, `CostumeModels`, `HoodedCloakLayer`).
- Built and compiled; not played. Unsure at runtime: the sign of the cape's turn swing, the pose offsets, the haul-up path.

## 0.22.38 (2026-11-11): Wisp: ghost tail, and a Phase you hold (#1322)

Ideas from Yo Fadda's Danny Phantom mod (All Rights Reserved: nothing taken, our own code; no new art).
- **Ghost tail** (`WispTail`): below the waist Wisp is now one tapering tail of 8 links in place of the block of tatters.
  Hanging in the air it curls back like a comma and wavers. Moving, it streams out behind, opposite to where you are going
  (back at a run, down on the way up, up on the way down, off to the side of a strafe), and a turn swings it wide. Every link
  follows the one above it a moment late, so a stop or a turn runs down the tail and the tip arrives last. The ki glow
  bends with it. Everyone sees it on every Wisp.
- **Phase is held** (R, `Wisp`, `WispPhaseMixin`): hold R for up to 6 s and no block stops you: walk through walls and
  doors, Jump to rise through a ceiling, Sneak to sink through the floor (the floor holds until you sneak; bedrock and
  barriers always hold). You float while phased, as on B. Nothing hurts you and your blows hurt nothing. Let go inside a
  block and you are put out at the nearest clear spot within 3 blocks, or where you last stood clear; run out of time inside
  one and that costs 2 hearts. Cooldown 1.5 s plus three quarters of the time you held it. It replaces the old blink through
  a wall up to 2 blocks thick.
- **Phased look** (`WispPhaseClient`): a phasing Wisp thins to almost nothing over dimmer ki, and flickers through its last
  second. Your own screen takes a pale wash that pulses as time runs out; with your eyes inside a block you see out of it
  and the world beyond is still drawn (vanilla and Sodium).
- Turning into Revenant ends a held Phase (Revenant keeps Wraithwalk).
- Built and compiled; not played. To check in game: the tail's direction on strafes and turns, walking and sinking through
  blocks with no rubber-banding, what you see from inside a wall with Sodium, and being put out when Phase ends inside stone.
- **Overshadow** (Sneak+G): slip into the creature you look at within 5 blocks and walk it as your own for 8 s. Your
  movement keys steer it (forward, back, strafe, sprint, jump), it faces where you look, and your swing strikes through it.
  G again lets go; 20 s cooldown. Revenant's Possession steers the same way now.
- **Ghost Ray** (Sneak+B, hold): a pale light gathers in your hand; let go for a bolt along your aim. A tap reaches 20
  blocks for 2 hearts; a full 1.5 s hold reaches 32 for 6 and is much wider. 1 to 3.5 s before the next, by how long you held.
- **Dread Wail shows and hits**: rings of pale light roll out 9 blocks ahead of you; creatures in that cone are thrown back,
  glass in it shatters (up to 32 blocks and panes), and you are slowed and weakened for 2.5 s afterwards.
- **Ghost breath** (`WispBreath`): a thin cold breath leaves a Wisp's mouth every couple of seconds while anything undead,
  or another Wisp, is within 14 blocks, walls or not. Nothing explains it.
- **Float on the move**: floating Wisps and Revenants tip forward with speed, roll into their turns and bring their arms
  out ahead at full speed, head lifted to see. The tail is never still: hanging, its curl tightens and lets out, and every
  few seconds the top flicks aside and the flick runs down to the tip.
- **Change rings for every alien** (`XenoChangeRings`): whenever anyone changes form (into an alien, between aliens, or
  back) two rings in their band's glow open at the waist and sweep up over the head and down to the feet. The band's flash
  still goes off as before.
- To check in game as well: steering a possessed creature (zombies, villagers, something four-legged), the ray's charge
  feel, and whether the rings read at the flash's brightness.

## 0.22.39 (2026-11-11): Horseman riding fixes in the pack

- **Horseman** (MIT, mortuusars) ships unchanged in the pack. While riding, right-click a fence to hitch your horse (it needs a lead in the new slot in the horse's inventory). Horses step up a little higher, stop jittering on stairs, keep more speed going downhill and in the air, and get caught on leaves less. You mine at full speed in the saddle. Horses fit in boats. A saddled horse stays within 16 blocks of where you got off. Shears take the chest off a donkey, mule or llama. A button switches between your inventory and the horse's. The horse fades out when you look straight down.
- Free rein stays as it was: Horseman only lets you look around while the horse is standing still.
- Horseman's Copper Horn can't be crafted (`tinkering/Uncraftable`). It calls a horse from anywhere and can teleport it, and the whistle already does this job.
- Built in the cloud; not played. The lead slot inside our restyled horse inventory screen still needs a look in game.

## 0.22.40 (2026-11-11): elemental powers have the weakest ki aura (#1333)

- **Smoke, Gravity and Cryokinesis users read weakest to ki** (Andy: "elemental power users have the weakest ki auras"): their scouter power level is 0.4 of anyone else's with the same health and attack, so ki sense shows them dimmer, as weaker than they are (`ZFighterScouter.ELEMENTAL_AURA`, `elemental`).
- Built; not played.

## 0.22.41 (2026-11-11): costumed gifted villagers, and a Cryo's cold house (#1332)

- **Costumed gifted villagers** (Andy: "npc costumed power users ... either wreaking havoc or trying to help their people"): a gifted villager can own a costume of their own (`core/gifted/GiftedCostume`), made for their power and side: a vigilante's in the power's bright colours, a villain's in black with red, purple, green or grey, always with a mask that hides the face, the power's own cut, mask or cape where it has one, and a name the folk would give them ("the Gale", "Black Ice", "the Undertow"...). Kingdoms decides who gets one and when it's worn.
- While suited they're drawn as a player body in that costume (`client/costume/GiftedCostumeClient`, `GiftedCostumeEntityRenderDispatcherMixin`), the costume's name over their head instead of theirs. The mask hides MCA's look and the villager's size and blood under it.
- **Everyday Cryo** (Andy: "putting fires out or keeping a food store or home cold"): a gifted Cryo villager's everyday use now smothers fire near them in frost and leaves the air round their home cold for 2 minutes (`power/cryo/CryoChill`). With Jaymo's Kitchen: a chest or barrel in that cold keeps food as a cold cellar does (needs this patch's Kitchen), and a player indoors there in a hot land is cooled by 20.
- Built; not played. Not seen in game: how the costume sits on a villager drawn as a player body, and whether any other mod's player layer minds a villager drawn that way.

## 0.22.42 (2026-11-12): the Verdant World's storm, sand and wind - Brainstorm, Lakeside Desert, Wind Valley (#1327)

Andy's Eden Ring picks, part 2 (Eden Ring's MIT art, `tools/eden_ring_license.txt`). Three more regions of the Verdant
World; only chunks made after this update get them.
- **Brainstorm**: cold grey upland of brain trees, each shot through with copper, iron or gold, with metal grasses
  underfoot (shears take them; now and then one drops a nugget). The trees build up charge: now and then a block of the
  crown lights and shocks every living thing within 4 blocks for 3 damage (`BrainBlock`). Anyone wearing a piece of
  iron, golden, chainmail, copper or netherite armour is passed by (item tag `jaymospowers:grounding_armor`). Some
  discharges show a bolt that does nothing.
- **Lakeside Desert**: hot dry country of sand and gravel with ponds, and aquatus on their shores. An aquatus head
  slowly turns the soil within 5 blocks of it to sand (`AquatusBlock`), so keep one away from a field. Its sapling grows
  with bone meal.
- **Wind Valley**: open grassland with a steady wind (`EdenWind`). Under the open sky it pushes you sideways, harder
  off the ground, and carries thrown and shot things and dropped items. It blows from one quarter for the whole valley
  and swings round once in five days. Sneaking braces against it.
- **Wood**: brain tree logs and planks.
- All numbers are constants at the top of `BrainBlock`, `AquatusBlock`, `EdenWind` and `EdenFeature`.

## 0.22.43 (2026-11-12): the Verdant World's builder's pieces - wood sets, Gravilite, the Gravity Compressor (#1329)

Andy's Eden Ring picks, part 3 (Eden Ring's MIT art, `tools/eden_ring_license.txt`).
- **Wood sets**: balloon mushroom, pulse tree and brain tree planks now make stairs, slabs, fences, fence gates, doors,
  trapdoors, buttons and pressure plates (the usual recipes).
- **Gravilite**: glowing blue shards stand on the ground all over the Verdant World, about one chunk in five (only
  chunks made after this update). Four shards make a Gravilite Block; a shard ringed with four glass makes a Gravilite
  Lamp (full light); a shard ringed with eight iron nuggets makes a Gravilite Lantern, which stands or hangs.
- **Gravity Compressor** (iron, a Gravilite Block, redstone, stone): given a redstone signal it lifts everything in the
  column above it, 2 blocks of column per level of signal (30 at full power). Living things float up and take no fall
  damage while in it; dropped items, arrows and carts are pushed up (`GravityCompressorBlock`).
- All numbers are constants at the top of `GravityCompressorBlock`, `Eden` and `EdenFeature`.

## 0.22.44 (2026-11-12): the Verdant World's small growth and the Diskwing (#1330)

Andy's Eden Ring picks, part 4 (Eden Ring's MIT art, `tools/eden_ring_license.txt`). Only chunks made after this
update grow them. Leaves take the grass colour of the biome.
- **Violum**: two blocks tall, glowing bloom (pulse forest, wind valley, stone garden).
- **Lonlix**: a leafy tuft, taken with shears (pulse forest, wind valley, lakeside desert).
- **Parignum**: a mat of leaves with small glowing flowers (mycotic forests, brainstorm).
- **Eden Moss**: a thin grey mat (stone garden).
- **Eden Vine**: hangs in strings under caps and ledges, climbable (mycotic forests, stone garden).
- **Symbiotic Mould** and **Glowing Symbiotic Mould**: tufts on the undersides of mushroom caps and brain trees.
- **Limphium**: a stem under a head of leaves; breaking it gives 1-2 Limphium Leaves (a poor snack) that dry in a
  furnace, smoker or campfire into Dried Limphium Leaf (3 food). Bone meal doubles the plant.
- **Diskwing**: a flat glowing glider that drifts over every Verdant biome, in four colours by biome (Eden Ring's
  model, skins and calls). Harmless, 10 health, drops nothing; it has a spawn egg (`Diskwing`, `DiskwingClient`).
- Numbers are constants at the top of `Eden` and `EdenFeature`.

## 0.22.45 (2026-11-12): the Aether's share of Eden Ring, and the field guide (#1331)

Andy's Eden Ring picks, part 5 (Eden Ring's MIT art, `tools/eden_ring_license.txt`). Only Aether chunks made after
this update get them.
- **Sunroot Grove**: a new Aether biome on the wetter half of the Gilded Ridge: sunroot trees (Eden Ring's auritis
  under our own name) with broad golden crowns, yellow moss, a golden oak now and then.
- **Sunroot wood**: log, planks, leaves, sapling, and the full set of stairs, slab, fence, gate, door, trapdoor, button
  and pressure plate.
- **Volvox colonies**: hollow balls of translucent Volvox round a glowing heart of Dense Volvox, drifting high in the
  Aether sky (y 165-250), about one chunk in 36.
- **Hollow islands**: drifting shells of holystone, turfed on top, with one to three ways into a chamber where
  gravilite shards stand and Eden vines hang; about one chunk in 80.
- **A Far Country Herbal**: the field guide, a ten-page written book of a traveller's notes on the Verdant World's
  far country. Crafted from a book, a Dried Limphium Leaf and Gravilite Shards.
- Numbers are constants at the top of `EdenFeature` and in `tools/aether_world_features.py`.

## 0.22.46 (2026-11-12): the Mad Tinkerer cheats death with his echoes (#1336)

- **The Mad Tinkerer can be killed now, but death doesn't take him**: the body falls, and 3 seconds later one of his
  echoes steps out of the air where he fell, in the Chrono echo's violet burst and pale shards, and carries on at his
  bench (same leaving time, same quest step; an operation in progress goes on under the echo's hands while you stand
  still). He shrugs it off in a line, and shimmers for a minute after.
- **He's in more than one place**: when he's killed, every other Tinkerer in the world flinches and mutters about it to
  anyone within 16 blocks.
- **Ask about the androids that turned**: a new talk option. He blames shoddy copies or the city's orders, and never
  admits anything.
- `TinkererEchoes` (new), `ZFighterAndroid.tinkerer` (one place that makes him, used by the wandering summon and the
  android start's workshop). Built; not played. Not saved: an echo still owed when the server stops never comes.

## 0.22.47 (2026-11-12): Frostborn troopers in the future (#1335)

- Chrono's future (Los Perrito) now has **Frostborn Troopers**: squads of 2-4 Frostborn in a whole Rig. They keep
  their distance and fire the Rig's palm blast, and their thrusters catch them when they fall.
- A three-sided war in the streets: troopers fight the androids, the mechs and the dead; androids and mechs shoot
  troopers too; and the dead, with nobody living in sight, go for the nearest android or trooper they can see.
- Troopers are hostile to players too: they shoot on sight, and hurting one draws its whole squad. They fight the
  Omens too, and the Omens fight them back.
- A beaten trooper sometimes leaves Rig Salvage (its Rig doesn't drop).
- Classes: FutureTrooper, FutureTrooperRenderer (new); FutureMachines (squads), FutureAndroid, FutureMech, FutureOmen, FutureFerals
  (targets); skin from tools/gen_future_machines.py.
- Built; not played.

## 0.22.48 (2026-11-12): Wind: airflow sense (#1277)

- **Airflow sense (Jung):** a Wind user with at least one ability unlocked feels the air that moving things stir.
  Round the crosshair a faint pale streak shows on the side each moving creature or player is on (ahead is up, behind
  is down), brighter the faster and nearer it is, thicker for something big, fading over about a second once it stops.
  It feels through walls, in the dark and the unseen, but gives a side only: no outline, no distance, no height.
- **What hides from it:** standing still or sneaking (it needs 0.08 blocks a tick; a sneak is about 0.065).
- **Reach:** 12 blocks with one ability unlocked, up to 24 with four. Half that with no sky overhead, three quarters in
  a dimension without weather, a quarter more in a thunderstorm, where the streaks wander up to 20 degrees off true.
  In the rain or the cold it loses soft movement (slow mobs). Under water it feels nothing.
- Always on and free: no key, no hunger cost, no text. Client side only (`WindAirSense`, run from `WindPowerClient`):
  no packets and no server code, so it only knows what the client is already told about.
- **For Andy to rule on:** in PvP a Wind user will know which side a walking or running player is on within 12 to 24
  blocks, through walls. Sneaking and standing still beat it.
- Not in it yet: a hunger cost, the Gale Barrier muffling it, an active pulse, tracking.
- **Not built locally and not played.** The session that wrote it could not reach the build's download hosts; the PR
  checks are its compile. `docs/wind.md` has the numbers and test steps.

## 0.22.49 (2026-11-12): Belly flops into water hurt (#1263)

- Falling into water from 4+ blocks now hurts unless you went in head first: a belly flop deals real fall damage scaled by fall height and impact speed (about 6 HP from 12 blocks, topping out at 8 HP); a cannonball - feet first, tucked - is the middle case (capped at 4 HP); a clean dive takes nothing (landing from a sky dive, spread flat, is a belly flop), and so does going in head first without Sneak (looking down, Leap of Faith's dive, a Wind user's dive). Short drops stay harmless.
- The entry pose shows on everyone in view: cannonball tucked into a ball, belly flop flat on impact with a short wince, clean dive with arms forward over the head - each eases out under water.
- Built; not played in game.

## 0.22.50 (2026-11-12): Undead seek real shelter from the sun (#1262)

- **Deadlier sunlight:** zombies, skeletons, strays and the other undead that burn by day now burn whenever the Sun's rays reach any part of them (feet, chest, head or the top of the body), not only when the sky is open straight above. A roof, a cave wall or a solid cliff shades them; leaves, glass, torches and fences let the rays through, so hiding under a tree no longer works. With Jaymo's Kingdoms the rays follow the Sun's real path across the sky. Nothing burns under the future's ashen sun.
- **Shelter search:** undead caught in the sun look for real shelter (a cave, somewhere underground, under a solid roof) up to 24 blocks away and walk there. If nothing is in reach they head away from the Sun and keep searching until they find some or the day ends. They don't dig.
- Not tested in game.

## 0.22.51 (2026-11-12): Blindness: the devout path - blindfold, carved eyes, Sky Sight (#1328)

- **The calling** (Blindness had none): Andy: "The especially devout opt to don a blindfold or carve out their eyes".
  The **Blindfold** (black wool between two string, worn on the head) blinds anyone without the power; a powerless
  survival player who keeps it on for 5 minutes in a row is called to Blindness (a day off the wait, once a day).
  **Carving out the eyes**: sneak and use shears on nothing; the first use warns, a second within 5 s carves them out.
  It costs 3 hearts (never the last), leaves a lasting scar (Carved-out eyes, until a new life) and a powerless player
  awakens to Blindness at once. Only the powerless (whom no other power has called) and Blindness holders can do it.
  Andy: "you must bandage your eyes to prevent bleeding out". The fresh sockets bleed as hard as a lost limb's stump
  and never stop by themselves: a bandage or rag on the head stops them, and then they've healed for good. Two dark,
  empty sockets show on the face from then on (for everyone; hidden under the blindfold or anything covering the face;
  at the stock eye spots, not found per skin). `Bleeding.holdOpen` lets a power keep a cut open like this.
- **The devout's sight**: a Blindness holder who wears the blindfold or has carved their eyes out sees a thinner dark,
  and living things within 16 blocks glow through it on their screen (pale gold; a dull red for the hostile).
- **Sky Sight**: the devout's Void Sight (C, ascended) lifts the view 16 blocks over them, looking almost straight down,
  eased in and out; a ceiling stops it short. The dark thins further and the glow reaches 32 blocks. Blackout and Abyssal
  Echo are unchanged.
- **The Unseeing** (Andy: "a feral cult, that believes in nonsense ... both the bandage and blindfold need to be
  visible, and the zealous unseeing will attack you when they go blind, as they start to lose their mind"): their own
  robed people (`Unseeing`; MCA draws the pack's villagers, so nothing could be laid on a villager's head), in four
  looks: a follower, blindfolded, gouged (linen over the sockets, blood showing) and zealous (the linen soaked, blood
  down the face). They're blind, so they hear you within 10 blocks, a sneaking player only within 3. A gouged one turns
  zealous one to two Minecraft days after the cut and attacks anyone it hears who still has their eyes (a blindfolded
  or eyeless player is left alone). Hit one and the others near it strike back. Right-click a sane one for a line of
  their creed; they never trade.
- **A service of the Unseeing** (Andy: "you should be able to stumble on a service of the members performing this on
  one of their followers"): now and then at night, out in the wild in the Overworld (not near a village), 22-32 blocks
  from a survival player, three blindfolded Unseeing stand round a follower between four lit candles. They chant three
  lines, then the eldest does one of the two rites: ties a blindfold on, or carves the eyes out (shears, a cry, blood)
  and binds the sockets in linen. The follower thanks them. A player within 20 blocks at the end is tossed a blindfold
  "for when you are ready". Then they live round the spot. Killing any of them breaks it up. At most once every 3 days a
  player (about one night in five). `BlindnessService`.
- **Enclaves** (Andy: "Villagers who have done this start to get shunned by others, meaning you will find unseeing
  enclaves in the wild, in random caves"): in a cave (no sky, below y 50), now and then 16-30 blocks off, 4-7 Unseeing
  (the eldest blindfolded, the rest blindfolded or gouged, a third of the gouged already zealous) round a lit campfire,
  with candles and a few grey and black carpets for beds. They keep within 12 blocks of it. At most once every 5 days a
  player, never two within 96 blocks. `BlindnessEnclaves`.
- `BlindnessDevotion`, `BlindnessDevoutSight` and three client mixins (`jaymospowers.blindness.client.mixins.json`);
  `Scars.Kind.CARVED_EYES`.
- Built; not played. The Unseeing skins are a first try (plain robes). The worn blindfold's texture is a plain dark band (first try).

## 0.22.52 (2026-11-12): The server starts again: the sky village keep's loot

- The server refused to start (2026-11-11 21:33, "Failed to load datapacks": `Unknown registry key ... jaymospowers:zanite_sword`):
  the sky village keep's chest (`loot_table/chests/sky_village/keep.json`) named a Zanite Sword, which doesn't exist; Zanite
  has a shortsword, and the chest gives that now.
- New `tools/check_data_refs.py`, run by the PR checks: a recipe category the recipe book doesn't have, or a recipe,
  loot table or advancement naming an item of ours that doesn't exist, now fails the PR instead of stopping the server.
  Not built here.

## 0.22.53 (2026-11-12): Blindness is a condition, Resonance gains its gifts (#1339)

Andy: "blindness should stay as is, just no longer considered a power, meaning you can go blind from battle, or malicious
intent. resonance gains the new abilities. so the two stay, but resonance is buffed, and you can awaken resonance while
blind with the fork."
- **Blindness is no longer a power.** Anyone can lose an eye, or both: a heavy blow to a bare head (8%, a shot 20%; a
  helmet protects), someone using shears on you while you're downed (sneak, twice), or your own hand. One eye gone darkens
  that side of the view; both, all of it. Sockets bleed until the head is bandaged and show on the face.
- **Sound paints the dark.** The blind now really do see by sound: every step, blow, door and falling thing within 32
  blocks opens a ring on the dark where it came from. (The old power promised this and only drew a ring when you pressed R.)
- **Awaken Resonance in the dark**: blind and without a power, strike a Tuning Fork. Keeping the blindfold's 5 minute
  vigil and striking it with the blindfold still on works too. Whoever held Blindness holds Resonance when they next join.
- **Resonance, buffed**: Pulse outlines whatever it hits for you for 5 s. Hold B for **Quiet**: you make no sound at all
  (a tap is still Hush). A holder who can't see hears the living through the dark, and an ascended one rises into **Sky
  Sight** with sneak + V. Shriek blacks out everything within 8 blocks.
- **Sound you can see**: Pulse, Echolocation, Shriek and Sonic Boom draw typed rings and a beam in Resonance's violet.
- **The Unseeing fight by sound**: zealots scream a Pulse at you from up to 7 blocks, and no power answers within 10
  blocks of one of their services while its eldest lives.
- Gone: the Blindness power's keys (One-Eye Blind, Blindside Swap, Deafen, Void Sight, Blackout, Abyssal Echo), which did
  little or nothing; what they promised is in the condition and in Resonance.
- Classes: `Blind` (new), `ResonanceQuiet`, `ResonanceSight` (new), `BlindnessEcholocation`, `BlindnessPowerClient` and
  `BlindnessEcholocationClient` (rewritten), `Scars` (two eye scars), `Wounds` (head hits). `docs/blindness.md` (new),
  `docs/resonance.md`.
- Compiled (main and client); not played.

## 0.22.54 (2026-11-12): Darktide overhaul, part 2: looks (#1339)

Andy's picks from the Darktide picklist, the looks half. Three new Magic FX types, for any power to use, and Darktide
redrawn with them.
- **New effect types** (`docs/MagicFx.md`): **wave** (a wall of water that rises, rolls, curls and breaks), **stroke** (one
  ink brush mark, heavy at the start and dry at the end, for any cut) and **pool** (a flat sheet with ripples).
- **Black Tide is a real wave**: dark water 2.5 blocks high (4 at spring tide) with a pale crest, rolling with the hit
  itself, breaking at the end and leaving the ground marked. Ascended, pools lie along its path.
- **Every cut is an ink stroke**: Dark Strike, Crescent (the whole circle at spring tide), Ink Cross, the riposte, Fang
  Barrage, Pounce, the flurry, Riptide Dash and Stillwater's cuts. The vanilla sweep and the dotted arcs are gone.
- **Undertow**: a dark pool where you sink and where you'll rise, a shadow over the ground between them, spray as you burst out.
- **Stillwater** is a mirror of still black water with slow ripples instead of a dotted circle.
- **The tide shows on you**: at high tide water lies at your feet and your eyes shed a pale light; at spring tide the
  water is wider, ink rises off your shoulders and your blade paints a stroke behind each swing.
- **Maelstrom**: waves roll in from every side as the dome rises and out as it comes down; the ground under it is water.
- **No vanilla stand-ins left**: squid ink is miasma, rain splash is pale spray off a ring, a parry is a pale ring (with
  steel sparks when a blade turned it), the shade and the risen shed miasma. Gifted villagers' Darktide kit uses the
  same stroke, pool and wave.
- Classes: `DarktideLooks` (new: colours, `cut`, `stab`, `spray`, `ink`, `pool`), `MagicFx` and `MagicFxClient` (the three
  types; the `Shape` packet carries a stroke's bend), `DarktideFx` (mirror, tide on the body), every Darktide move class.
  Textures from `tools/gen_magic_fx.py`: `textures/fx/wave.png`, `stroke.png`, `pool.png`, `ripple.png`.
- Compiled (main and client); not played. The eyes' light is drawn as pale glints at the eyes, not a change to the skin.

## 0.22.55 (2026-11-12): Darktide overhaul, part 1: power (#1339)

Andy: "Darktide needs an overhaul to bring it up to power and graphical fidelity with the other powers." His picks from the
picklist; this is the power half, the looks follow.
- **Flow is the only cost.** Dark Strike, Crescent, Ink Cross, Undertow, Pounce and Fang Barrage are ready again in a second or
  two; only Black Tide (15 s), Stillwater, the flurry and the C moves keep a real cooldown. Pounce costs 15 Flow.
- **Flow no longer drains in daylight**, trickles in at 1 a second anywhere, and is kept when you log out.
- **Damage**: Black Tide 6 to 12, rolls 16 blocks and pushes harder. Crescent +4 to +8, 3 to 5 foes. Undertow bursts for 8 and
  throws foes back, for everyone and not only the ascended. Riposte 6 to 10. Stillwater 10 + 2 a second held (was 6 + 1), its
  field full in 3 s. Drown 2 to 4 a second, and foes hang helpless in it.
- **The tide**: from 50 Flow it's high tide and moves reach a quarter further. At a full bar it's spring tide: the next Black
  Tide is twice as wide, the next Crescent cuts all the way round, the next Undertow drags those beside you under with you.
- **Maelstrom** (ascended, hold C): a 12-block dome of dark water for 10 s. You swim through it with Flow held full; everyone
  else in it is slowed, can't get out and drowns, then it crashes down for 16. Hold C again to bring it down early. 2 minutes.
  Tapping C (raise a shadow) now acts when you let the key go.
- **Blade of Dark Water**: guarding, G or X with both hands empty draws a katana of dark water (an iron sword's blow), so the
  whole kit works without WeaponMod or a blade of your own. It can't be dropped and runs back to water 15 s after its last use.
  Every parry now counts toward ascending, not only a katana's.
- **At home in water**: in water or rain you're a fifth faster on foot and swim like a dolphin, Undertow is free, and you can
  surface anywhere rain is falling.
- Classes: `DarktideMaelstrom`, `DarktideBlade` (new), `DarktideFlow`, `DarktideMoves`, `DarktideTide`, `DarktidePower`,
  `DarktideState` (Flow in the save, the `TIDE` attachment, the Maelstrom's cooldown slot). `docs/darktide.md` updated.
- Compiled (main and client); not played.

## 0.22.56 (2026-11-12): ImmersiveVFX test ended - Iris and Complementary Unbound are back (#1340)

- **ImmersiveVFX is out of the pack** (Andy, 2026-11-11: "revert the immersive vfx changes"). `build.gradle` only.
- **Iris and the Complementary Unbound default shaders are back**, and **Sodium is back on 0.9.2** (the Iris build in the pack requires exactly that release).
- Build Modpack moves ImmersiveVFX and the Sodium 0.9.3 beta out of your CurseForge profile (into `removed-mods`) and adds Sodium 0.9.2 and Iris back. The profile never downgrades a mod on its own, so the beta is moved out by file name.
- Friends who imported the pack while the test was in need the new pack zip (or to delete the ImmersiveVFX and Sodium beta jars and add Iris and Sodium 0.9.2).
- Compiles against Sodium 0.9.2; not run.

## 0.22.57 (2026-11-12): The big suit: fury, brute moves, goo forms and a symbiote that answers back (#1346)

- **Fury.** The big suit builds heat from blows given and taken (bar on the HUD). At full it hits harder, reaches further and stands taller, and a roar at full lets it all out in one bigger blast. The longer you refuse the big suit, the more fury it starts with. The roar now scares mobs off and slows players near it.
- **The slow walk.** With its tendrils drawn in, the big suit never runs, walks faster, steps up full blocks and is almost impossible to shove.
- **Close combat.** With something held in the left hand: swing it by the ankle into others, or pound it into the ground. A well-timed counter catches the attacker and slams them. A friend can be carried over the shoulders and thrown. Sprint and G on a big beast latches on and rides it while it is bitten.
- **Brute moves.** G at a block rips a chunk loose to hold overhead and throw. Sneak and G at the ground tears a line of earth up under your foes. A long sprint becomes a shoulder ram that flings the first thing hit and bursts through wood, glass and leaves. G near a tree (with Kingdoms) lifts it to throw.
- **Goo forms** (new keys, rebindable): Puddle (P) melts you flat so blows pass over and gaps let you through, Spit (O) blinds and slows and leaves a slowing patch, Goo Wall (;) throws up a short-lived wall, or a dome over you and friends while sneaking.
- **Scent.** The black strain smells living things through walls, the wounded in red.
- **It answers you.** Start a chat line with `~` and only the symbiote hears it; it replies about its hunger, its mood, what you are fighting, and more.
- **Grudges.** It remembers who hurt you, nags when they are near, and trusts you more when you settle one. Shown on its Status page.
- **Meeting it.** The first time the bond is whole, a short skippable film shows it face to face, where you stand.

Nothing here has been played in a client yet; expect tuning.

## 0.22.58 (2026-11-12): the armed body and special cuts - stance, draw, flip, parry lines, swatted shots (#1341)

Andy's picks from Star Wars: Galaxy in a Cube (All Rights Reserved, 1.20.1 only: ideas only, every pose, name and line
of code is ours). Patches 1 to 4 of 6 of the SWGC swordplay picks.
- **Armed stance** (`client/combat/ArmedPose`, `combat/Armed`): with a weapon in hand the whole body changes. Standing,
  the weapon shoulder turns forward, the blade is held out ahead, the other hand goes back, the feet set apart and the
  weight shifts slowly. Walking, the blade is carried low at the side; running, it trails behind and you lean into it.
  A two-handed weapon is held in both hands before the body; a polearm stands upright at your side and comes across
  the body on the move. Picks, shovels, hoes and fist gear aren't weapons here. Better Combat's swings are untouched:
  the stance lets go for any swing, guard, item use or other pose of ours.
- **Draw flourish**: taking a weapon into the hand plays a half-second draw, from the far hip for a one-handed blade
  and over the shoulder for the big ones, with a ring of steel (a stand-in sound until the sound patch). A swing or a
  guard cuts it short.
- **Armed crouch**: sneaking with a weapon sinks into a low guard, the blade levelled ahead.
- **Mid-air flip** (`stamina/Dodge`, `DodgeClient`, `DodgeRollPose`; Andy: "tied to weapon forms but activated by dodging
  in midair, after a jump", "the camera should always follow"): Dodge pressed in the air off a jump, a weapon in hand
  and Weapon Forms learnt (any form from a teacher, or the Strength perk), turns a somersault the way your movement keys
  point (forward with none held). Once a jump, 4 feathers, a small push, and it evades like a dodge. Only those who fly, are stronger or bend their own weight get a lift from it (Web Slinger, Gravity, Darktide, Wind, Z Fighter, Smoke, anyone who may fly, Strength, Might or great size). In first
  person the view turns over with you. A dodge pressed while falling without those still queues the landing roll.
- **Parry lines** (`client/combat/ParryPose`): a raised weapon guard is carried across the body, and when a blow meets
  it the guard goes to the line it came on: high over the head, low across the legs, or hanging point-down across the
  far side (blows at your own height take high and hanging turn about). A plain block rocks you back; a parry barely
  does. Everyone sees it (`Armed.MET`, synced), and fighters who parry you snap to the line too. Shields and the
  powers' own guards keep their poses.
- **Batting shots back** (`combat/Swat`): a perfect parry made with a blade (not a shield) knocks an arrow back at
  whoever loosed it, at half the speed it came with. Never a gun's ball or anything tagged unparryable.
- **Darktide swats every shot** (Andy: "darktide can block unparryable projectiles. it animates him swinging away at it
  whenever they impact the guard"): each shot Darktide's guard meets, balls and tagged ones too, is seen swung at, with
  sparks off a katana or a knife. What the guard stops and sends back is as it was.
- **Special cuts, the body's side** (for Kingdoms' new blade forms): `Armed.CUT` (synced) has `ArmedPose` draw the Wheel
  Cut (the blade circling overhead, then across), the Figure Eight and the throw. `combat/BladeThrow` hurls any sword as
  a tumbling `ThrownWeapon` that sticks where it lands, or flies back to the hand when the caller says its thrower can
  call it.
- **Fighting styles, the trades** (`combat/BladeStyle`, for Kingdoms' schools): a player set in a style pays feathers
  for the swings that land, pays more or less for a blow a weapon guard blocks (`Guard`), swings faster or slower (an
  attack speed modifier) and gets feathers back faster or slower (`Feathers`). Nobody has a style until Kingdoms sets one.
- **Two blades** (`combat/TwoBlades`, `Armed.dual`): with a one-handed blade in each hand the stance is wider and
  squarer, the off hand carrying its blade like the other; a raised guard crosses the two; and every third swing that
  lands, both blades cut together, the second for 0.6 of your attack on the same foe. Better Combat's off-hand swings
  are untouched.
- **Guards at attention** (`client/combat/AttentionPose`): a humanoid mob with a polearm that is standing with nobody
  to fight holds it upright at its side; one with someone to fight (or, with Kingdoms, a guard challenging you) levels
  it in both hands. Their swings are as they were.
- **The Twinblade** (`armory/Twinblade`; Andy: "an ancient warrior born design, not often wielded by many, and the
  schematic is found in the stormborn dungeon"): the Twinblade Schematic turns up in the Stormborn Tomb (35% in the
  boss's hoard, 10% in the last chest). Use it to learn the joining (the sheet is used up). Then sneak and use with two
  swords of the same make, one in each hand: they become one Twinblade that strikes for the sword's damage plus 1, a
  little slower, lasts as long as the two together, and is held, swung and guarded with like a quarterstaff (it takes
  Kingdoms' Mill Charge). Sneak and use it with the other hand empty to part the two again, the wear shared. No recipe.
  Their enchantments sleep while joined, and a Twinblade that breaks takes both swords with it. Our own sprite
  (`tools/gen_twinblade.py`).
- The stance and the guard are poses of ours, so Entity Model Features (Fresh Animations, Touch'n Motion) pauses on a
  body while they're on, as it does for the Fang Gauntlets' guard.
- Compiled; not played. Every angle is a first pass set without seeing it in game: the knobs are at the top of
  `ArmedPose` and `ParryPose`.
- **Cuts you can see** (Andy: "the cuts are real and can be healed with bandages"): a bleeding part now shows red
  gashes on the body, for everyone who looks (players and humanoid mobs; `DressingsLayer`, the bleeding parts are now
  synced to all). The mark is the wound itself: a bandage or rag covers it, and it is gone when the cut closes. Armour
  and costumes hide it as they hide dressings. Our own texture (`tools/gen_cut_marks.py`).
- **Our own blade sounds** (`combat/BladeSounds`, `tools/gen_blade_sounds.py`): five swings each for light, medium and
  heavy weapons, six clashes of steel and three draws, one picked at random each time with a slightly bent pitch. They
  play for the draw, the arrow swat, the both-blades cut, a thrown blade, and steel meeting steel on a guard. Better
  Combat's swing sounds are untouched. Synthesised, not yet heard in game.
- **Reel what Lift holds**: while Gravity's Lift holds a creature, a chunk or anything else, the scroll wheel pushes it
  out or reels it in, a block a notch, from 1 block to 16 (`GravityReel`). Heavier things follow more slowly. The
  hotbar stays put while you hold something (sneak and scroll is still the stance wheel).
- **Lift tears any block loose** (Andy: "it certainly should be able to grab ANYTHING"): the block you aim at comes
  whatever it is made of, obsidian included; only what can't be broken at all and blocks with something inside (a
  chest, a furnace) stay. The rest of the chunk still grows with the power. Harder chunks hit harder when thrown: up
  to three quarters more at obsidian's hardness.
- **Great-helm view** (`client/GreatHelmView`): in first person, a helmet tagged `jaymospowers:great_helms` (the
  Paladin's Great Helm to start) narrows the view to the visor's slit and you hear your own breath, quicker while you
  sprint or are hurt. `/helmview off` turns it off. Breath synthesised (`tools/gen_helm_breath.py`).
- **Our own title splashes**: the title screen's yellow line now comes from 45 of the pack's own lines
  (`assets/minecraft/texts/splashes.txt`). A first draft for Andy to cut and add to.
- **The Z Fighter's ki weapon is the energy blade** (Andy, asked who should carry one: "z fighters"). It has its own
  sounds now: it flares out and draws in, hums while it is out and sings on every blow that lands
  (`tools/gen_ki_blade_sounds.py`). With it out he stands armed (the blade one-handed, the scythe and lance as
  polearms). A parry made with it out strikes an arrow back at whoever loosed it. Cost, damage and reach are unchanged,
  and its blows still count as fists.

## 0.22.59 (2026-11-12): licence - All Rights Reserved

- **Licence**: Jaymo's Powers is now All Rights Reserved instead of MIT (`LICENSE` at the repo root, shipped in the jar
  as `LICENSE_jaymos`, and in the modpack zip as `LICENSE.txt`). Friends Andy gives the pack to may play it and host it;
  nobody may re-upload or pass it on without his say. Ported MIT parts (Iron's Simple Blood, Enchanting System Overhaul)
  keep their own licences in `licenses/`.
- Nothing changes in game. Built; not played.

## 0.22.60 (2026-11-12): Boxing: learning a style and raising it

- **A style is now learned and raised in play** (the teachers are Kingdoms'). A teacher sets a task: 15 hostile mobs
  killed with fist gear in hand; back with the teacher the style is learned. Another style only once every known one
  is mastered.
- **Levels come from fighting in the style**: each punch landed on something that has hit you in the last 5 seconds
  is a point, 80 a level; a guard's drill done while boxing adds 12. Only an opponent who hits back counts.
- **At levels 3, 6 and 9** the style waits for a teacher's task, done with fist gear in hand: 5 armoured zombies,
  then an orc, then a camp's chief or a bandit.
- **HUD:** while boxing, the style and its level show in the bottom right corner.
- Classes: `boxing/BoxingTraining` (new: the tasks, points, levels and the lesson Kingdoms asks for), `boxing/Boxing`
  (a landed punch tells it), client `boxing/BoxingClient` (the HUD line).
- **Not built locally and not played.**

## 0.22.61 (2026-11-12): Aliens: new moves for twenty Xeno-Band bodies (#1350)

Most of these sit on a sneak-press or a Z tap of a key the alien already had, so nothing old is lost.

- **Bulwark**: a fury bar that fills with blows given and taken (harder hits, less knockback at full); a long sprint becomes a charge through wood and stone; sneak + V with something held is a suplex.
- **Tusk**: sneak + R is a rampage that fells trees and gores everything in its path.
- **Towerback**: R with nothing to grab tears up a slab of ground to throw.
- **Gorger**: while rolled up, V bowls along and rebounds off walls harder each time, G is a rising strike, and fireballs are batted away.
- **Prowl**: Z tap puts the claws out (longer reach, pounce latches onto the prey's back for swipes, a stab and a kick); R while sprinting is a lunge; cuts close fast, lost limbs stay lost.
- **Howl**: sneak + V picks one scent: its trail hangs in the air, the bleeding show through walls, and Howl hits that prey harder.
- **Lurk**: takes the colour of the wall or ground it touches, fades when crouched and still, and builds a stronger first blow on whoever it watches unseen.
- **Talon**: sneak + R in the air is a takedown dive, harder the further it fell, with a wingbeat on landing.
- **Tail sweep**: Prowl (sneak + G), Howl, Slipfin and Gnasher (Z tap), and Warrior-born Z Fighters with the tail out (sneak + Z in the air) stun everyone within 2 blocks.
- **Ooze**: sneak + G lashes a long arm in an arc, sneak + R wraps and drags, sneak + B is a mallet slam, and shots bounce off the jelly.
- **Lumen**: a charge that makes shapes of light. Z tap picks the shape, sneak + V makes it: beam, dome, grabbing hand, missiles, a great thrown block, and a wall, bridge or cage of short-lived light blocks.
- **Mirage**: sneak + V wears the face of a creature or player you look at; its kind leave you alone until you strike.
- **Wisp**: V while phasing readies a hand that passes through armour; Z tap thins out (half damage, a blow sinks you through the floor to rise behind the attacker).
- **Cortex**: reads whoever struck it on the HUD; sneak + G pushes with a thought, sneak + V fills a head with visions, and holding sneak + B stares enemies into walking to its feet.
- **Fuse**: Z tap smothers a lit creeper, primed TNT or a fire; hurt taken fills a bar to let go with sneak + B, and a last-resort shell saves it once below a third health.
- **Rivet**: Z tap is a pulse that stalls machines and seizes creatures; sneak + Z opens iron doors and flips levers, buttons and lamps.
- **Hive**: B sends out five flyers that share its health. Z tap changes formation (close in, surround, strafe, scout), B again fires one as a missile, sneak + B calls them in as a shell.
- **Murk, Slipfin, Gnasher**: under water the shoal comes to fight (Murk sneak + B, Slipfin sneak + G); Murk's Z tap is a tentacle that grabs and throws; Gnasher's sneak + V is a whirlpool and the drowned side with it while it holds a trident; Slipfin's sneak + R is a dome of water that stops shots.

Nothing here has been played in a client yet; expect tuning.

## 0.22.62 (2026-11-12): Boxing: stances, poses and breath

- **Each style has its own moving stance** (third person, with fist gear and a style): the Arrow Slit sways behind a
  high, tight guard; the Long Guard paws the air with its lead hand; the Bastion stands southpaw with its lead arm low
  and shrugs its shoulder now and then. You can read a boxer's style from how they stand.
- **Blocks, punches and evades have poses**: three different blocks; the arm goes out by the punch (jab, cross, hook,
  uppercut); a lean, a duck, a pull or a shoulder turn for the evades.
- **Tiring and gassed show**: a faint puff of breath now and then with four feather icons or fewer, clear puffs and
  heaving shoulders when out. No sound.
- Classes: client `boxing/BoxingPoses` (new), `armory/FangGauntletsClient` (hands a boxer over to it), `boxing/Boxing`
  (`LOOK`, the breath), `boxing/BoxingEvades` (`MOVED`).
- **Not built locally and not seen in game.** Every angle was set without a render; expect to tune the knobs at the top
  of `BoxingPoses`. First person shows none of it.
- Other players see a boxer's stance too: the poses go by the style everyone is told and the fist gear in hand, since
  a player's levels are told only to that player.

## 0.22.63 (2026-11-12): Your game starts again: the Sodium beta leaves your profile

- Andy's game wouldn't start (2026-11-12 00:10, "Sodium 0.9.3-beta.4 is incompatible with Iris 1.11.7"): ImmersiveVFX's
  test (2026-11-10) brought Modrinth's Sodium beta as `sodium-2MZWfUts.jar`; when the test ended and Iris came back, the
  profile kept it (it never downgrades a mod, and the removal list only knew CurseForge's `sodium-fabric-0.9.3` name).
  `removedFromProfile` now lists `sodium-2MZWfUts` too, so Build Modpack moves it out and puts the pack's Sodium 0.9.2 in.

## 0.22.64 (2026-11-12): no walking on a missing leg - cane, crutch, and hauling up a block on your belly

- Andy: "you shouldnt be able to walk upright with no leg, unless you make a cane or crutch". A player with a leg gone
  and nothing fitted in its place drops to a crawl and stays there (as with two broken legs). A wooden or iron leg
  still walks as before.
- **Walking Cane** (a plank over three sticks: `PS / .S / .S`) and **Crutch** (six sticks and a leather pad:
  `SLS / SSS / .S.`): held in either hand, one stands you up on the leg you have left (you rise by yourself when it
  comes to hand; put it away and you go down again). The cane walks at the pace a missing leg already cost (55%), the
  crutch at 70%. With both legs gone and bare neither helps: fit a leg.
- A legless crawl is hauled by the arms, so it keeps 85% of a crawl's pace with one leg gone and 65% with both
  (the walking penalty used to carry over into the crawl).
- Andy: "crawling players should still be able to haul themselves up one block". Every crawling player (by the key,
  the stance wheel, broken legs, a missing leg, or downed and awake) now goes up a full block by crawling into it, then
  moves at 30% pace for 16 ticks and pays a little food.
- Classes: `core/wounds/Crutches` (items, `legless`, the tick), `Breaks.crawlOnly`, `Crawl.tickHaul`. Art and JSON from
  `tools/gen_crutches.py`. Transformed Xenoforms and creative players are not held down.
- Compiled; not played. The third-person hold (the stick hanging from the fist to the ground) is set by eye from the
  numbers and may need its angle tuned.

## 0.22.65 (2026-11-12): downed enemies take executions lying down

- Andy: "downed enemies should react to the executions, i just watched jung wild slam a few zombies and they popped up after, instead of being beaten into the ground".
- **No more popping up**: a downed mob only looks laid out; to the game it still stands, so Jaymo's Kitchen started its corpse upright and let it topple. Anyone who dies downed (a finisher, a symbiote execution, a mob's execution, a takedown, bleeding out, the third hit) now hands the corpse how it lay (`DownedLie.markBody`, called from `Downed.finish`): crawlers face down, the rest on their backs or side, head the same way. Same for one tripped by a bolas and finished there.
- **Reactions while it plays** (`ExecutionReact`, drawn through `DownedPose`): the body braces before and between blows (arms over the face, or over the back of the head face down), each crushing blow (Wild Slam, Skull Crusher, rock, flail, staff, ki, the symbiote's stomps and pounds) drives it into the ground and it bounces off, limbs flung up; a blade or spear pins it rigid and quivering; a cut or shot jerks it once; the neck snap, garrotte and the symbiote's chokes, bites and tearing are fought, hands at the grip and heels kicking. Animals, bugs and birds get the drop and bounce and kick with their own legs. No sit-ups while being finished.
- Needs Jaymo's Kitchen's matching change for the corpse to start lying (older Kitchen: reactions only).
- Built; not played.

## 0.22.66 (2026-11-12): prone haul up a block is a held struggle

- Andy: "the one block climb while prone should be activated by holding right click and animating a struggle up the block, longer and differently animated if you only have one hand. if you let go or are interuppted during the struggle then you ragdoll. there is a third faster animation if you are perfectly healthy."
- **Hold Use to haul up**: lying on your belly against a rise about a block high (0.55 to 1.1) with room to lie on top, hold right click. Your hands reach up onto the top, you drag yourself up the face with your legs scrabbling, a knee comes over the edge and you slide in on your belly on top. Crawling into a block no longer steps you up it by itself (the step is vanilla's again).
- **Three ways up** (`ProneHaul.style`): perfectly healthy (full health, nothing broken or lost, not downed) is quick and clean, 0.8 s with a strong knee onto the edge; the usual is a hard 1.6 s pull with the arms shaking; with only one working arm (the other broken or gone) it's a 2.8 s one-armed heave, rolled onto the good side with the dead arm hanging, that slips back part way and has to heave again. No working arm, no haul. Downed, each takes half as long again.
- **Let go or get hit and you fall**: release right click part way, or take damage, and the grip goes: you ragdoll back off the face (Kitchen's limp body when it's there).
- While you're lying against a rise you could go up, right click is the haul's (no item used or block worked), unless the crosshair is on a mob or something a click is for (a door, a chest...). Downed players can haul too.
- Code: `core/ProneHaul` (rules, path, server), `client/ProneHaulClient` (input and movement), `client/ProneHaulPose` (drawn over `CrawlPose`), `ProneHaulUseMixin`; the old automatic haul step in `Crawl` is gone. `Breaks.workingArms` added.
- Built; not played.

## 0.22.67 (2026-11-12): Daggers harvest alike; no free knives or arrows from RPG Series skills

- **Rogues & Warriors' daggers** (Flint Dagger and the rest) now cut what the Tool Station dagger cuts: everything a hoe mines (hay, moss, sponge, sculk, wart blocks...) at their material's speed, plus a sword's cuts. Before they only had the sword's (armory/DaggerHarvest).
- **Fan of Knives** no longer makes up its blades. It throws up to three of your own knives: the one in your hand first, then the same kind from your pack, then any other knife or dagger (never Sora's Dagger or Ashfang). It never throws more than you carry; with only the held one, one knife flies. Each knife shows as itself and lands to be picked up, like Impale's spear (compat/SpellThrows).
- **Shattering Throw** (Warrior) throws the weapon itself, which leaves your hand and lands to be picked up, as Impale does.
- **Rain of Arrows** (Archers) spends one of your arrows (pack or worn quiver) for each arrow that falls, as many as you carry up to its usual count, and won't cast with none. **Barrage** won't cast unless you carry its three arrows (Spell Engine made up any it ran short of).
- **Throw Net** (Rogue) throws one of your new **Throwing Nets** (5 string, crafted in an X) and won't cast without one. Andy: "make a net item crafted from string".
- Creative casts take nothing, as before. Built; not played.

## 0.22.68 (2026-11-12): blood on doors swings with the door

- Andy: "the blood decals that splashed on the door in the second clip did not travel with the door as it opened, so they floated in midair".
- **Doors**: a blood splat or run on a door now swings with it when it opens or closes, turning about the hinge to wherever the door now stands (the side that faced out ends up facing into the doorway, as on a real door). Each wall mark remembers the block it lies on (`BloodWallMark.follow`, `swing`).
- **Nothing left to lie on**: a wall mark whose wall is gone goes with it instead of hanging in the air: the block broken or replaced, a fence gate opened, a trapdoor flipped. Puddles on the ground already settled onto whatever was under them.
- Built; not played.

## 0.22.69 (2026-11-12): hits and pickups heard again - downed hits land, ImmersiveFX leaves the pack

- **Hitting a downed body sounds and looks like a hit** (`Downed.blowLands`): a player's blow on anyone downed (every
  humanoid, villagers, tamed animals) takes no health, so vanilla only played its soft no-damage swish and the only
  answer was a quiet, low player grunt. Now each blow that counts a wound gives the thud of a full swing (arrows keep
  their own hit sound), the red hurt flash and flinch every client sees, and the body's own hurt cry at its own voice
  (a vindicator grunts as a vindicator). The killing blow (three hearts or more, or the tenth wound) gets the thud and
  flash, and the death cry follows as before. Andy: "attacking the vindicator didnt have any impact or reaction sounds".
- **ImmersiveFX: Resurrected leaves the pack** (Andy: "having both is making everyone lag so you can remove the
  immersive"). It and Sound Physics Remastered both put their own echo and muffling filters on every sound: ImmersiveFX
  stands down beside a sound physics mod, but it looks for the old id `soundphysics`, not Remastered's
  `sound_physics_remastered`, so both ran. That was the likeliest reason pickups went silent and all sound dropped out
  while sprinting, and the two together cost frames. The build moves it out of the CurseForge profile
  (`removedIdsFromProfile`). AmbientSounds 6, which it replaced, is not brought back; Sound Physics keeps the echoes.
- Built; not played.

## 0.22.70 (2026-11-12): Z Fighter techniques, brute clashes and new moves for fire, frost, storm, weight, water and sound (#1351)

**Z Fighter**
- Four new techniques to buy with TP on the Techniques page: **Eye Beams** (sneak + V held, on a heat gauge; let go at once for a blinding shot, R while held for a spin, cold and freezing for the Frost lineage, and into the face of someone you hold), **Aerial Charge** (G while flying: carry the first foe into a wall or the ground; aimed steeply down it is a power dive), **Thunderclap** (G: a shockwave, or boxed ears up close, which deafens a player for a few seconds) and **Great Breath** (sneak + V held: a gust that pushes everything back; cold for the Frost lineage).
- **Hold Fast**: every Z Fighter hits harder the closer they are to going down.
- **Bracers**: guarding soaks damage into a bar that pays out as a damage boost when the guard drops, left click while guarding clashes them for a knockback, and arrows that hit the guard are sent back.
- **Great ape**: a held punch with nothing to grab tears up a slab of ground to throw; G cracks the earth in a line and B in rings.
- **Clash of blows**: two great blows that meet lock for a moment and one side gives. Great ape, Bulwark's charge, the big suit's ram and a combo finisher can all clash.
- **Sun and moon**: Verdant fists are stronger in sunlight and a little weaker at night; Warrior-born are stronger under the night sky, more so at full moon. Sneak, stand still and look straight up to drink it in.
- **Kicks**: a tornado kick joins the strings, spinning kicks pull the target in, and sprinting into a string opens with a flying kick.
- **Throws**: a thrown body bowls into others; a thrown friend lands soft.
- **Charged throws**: hold a rock or dagger throw longer to charge it with ki so it bursts where it lands; sneak on release sets rocks circling you.

**Fire and frost**
- **Emberborn and Pyre** run hot: a heat bar that climbs in a fight, white-hot hands that wear armour down, a vent (double-tap sneak), and a burn and burst at the top. Sneaking still with empty hands braces: blows are soaked and blasted back, and a shot that hits you is caught and returned stronger. Fire you start jumps between foes, and what it kills bursts.
- **Pyre** (sneak + R rays, sneak + V flame ring, sneak + B fire wall, Z tap sky column) and the **Flame Avatar** (V tap wall, V hold ring) get the new fire moves.
- **Cryokinesis and Rime**: a cold gaze that freezes after six seconds, an icicle volley that bleeds and an ice ball that roots (sneak variants of their keys).

**Storm, weight, water and sound** (sneak or sprint variants of the usual keys)
- **Wind**, in a storm mode: Bolt Rush, Bolt Clap, Bolt Slam; in Thunderhead also a lightning dome, a blink along a bolt and a ring of tornadoes; sneak + Z turns the sky (clear, rain, thunder); hits change with the weather.
- **Gravity**: Binding Bands (sneak + B) pin up to five; Trading Places (sneak + Z) swaps with a target, blinks and leaves a copy, or drags what you hit to your feet; Last Footing snaps you back to safe ground once when badly hurt; in Collapse, sneak + C is ten seconds where nothing stops you.
- **Darktide**: a water dome, a whirlpool, a held water beam, a bleeding trident jab, lightning on a thrown trident, a shoal that fights for you in water and drowned that side with a trident bearer.
- **Resonance**: a held shriek, a cry that lifts you in mid-air, a sound-barrier punch and a panic cry when hit.

Nothing here has been played in a client yet; expect tuning.

## 0.22.71 (2026-11-12): the tundra - bare old rock and the lichen woodland (#1358)

- **Two new Overworld biomes** (Andy picked all of Klei_Wright's tundra concept; `docs/Tundra-Plan.md`). Not a
  dimension: they take the driest part of what was Snowy Plains, in new chunks only.
  - **Tundra**: no stone or soil. Deepslate runs from bedrock to the surface under thin grass, yellow moss and gravel.
    **Greenstone belts** (strips and patches of smooth basalt) cross it from the surface down, with pockets of
    **banded iron ore** in them, some round a heart of raw iron blocks; iron ore is also far commoner underground here
    than anywhere else. Boulders, lichen on the bare rock, low red, orange and yellow shrubs. No trees. It never
    snows or rains, but snow motes drift under a pale sky, and its water freezes to white ice. Polar bears, foxes, rabbits.
  - **Lichen woodland**: the belt between the tundra and everything else, with a grass colour halfway between, yellow
    moss patches, and **dwarfed spruce** no taller than a man.
- **Spruce saplings grow dwarfed** in both biomes (there is no dwarf sapling).
- How: `tundra/world/TundraWorld` splits Snowy Plains' dry cells as the Overworld biome list is built
  (`TundraPlacementMixin`); the ground is the material rule `jaymospowers:tundra`, run from our copy of
  `minecraft:overworld` (one added line: another pack replacing that file would switch the tundra's rock off);
  `TundraFeatures` has the boulders, veins, lichen, shrubs, moss, white ice and dwarfed spruce.
- Compiled; not played. Numbers that are guesses until seen in a world: belt width and spacing, vein count, how much of
  the surface is grass, and how much of Snowy Plains the two biomes take.
- **Klei_Wright's own art** for the tundra's blocks, ground and hanging lichen and the phosphor icon ships as its own
  resource pack inside the mod, `resourcepacks/klei_tundra` (CC BY-NC-SA 4.0, his files unchanged, credited in its
  `CREDITS.txt`), on by default over our own art; switch it off in Resource Packs to see ours. His muskox art is not
  used: it fits his model, not ours. Not seen in game: whether each of his textures sits right on our models.

## 0.22.72 (2026-11-12): the tundra's blocks, plants and living lichen (#1358)

From Klei_Wright's "Tundra game drop" concept (`docs/Tundra-Plan.md`, track A). Package `com.jaymospowers.tundra`;
every texture is painted from nothing by `tools/gen_tundra.py`, the models are our own.
- **Banded Iron Ore**: iron in smooth basalt, in pale bands. Stone pick or better; 2 to 4 raw iron (plain ore gives 1),
  Fortune as on any ore, silk touch keeps the block. Smelts and blasts into an iron ingot.
- **Dwarfed Spruce Log** (a 10-wide pillar) and **Dwarfed Spruce Post** (6 wide): placed along any axis like a log,
  stand in water, an axe strips them. 2 logs or 4 posts craft into 1 spruce log (the stripped ones into a stripped
  spruce log). They burn, and count as logs (`minecraft:logs_that_burn`).
- **Stunted Red Shrub, Orange Shrub, Yellow Shrub**: small bushes on dirt, grass, moss, yellow moss and gravel. Shears
  or silk touch to keep one.
- **Yellow Moss** and **Yellow Moss Carpet** (2 moss -> 3 carpets); hoe-mined like moss, and plants root in it.
- **White Ice**: opaque white ice that never melts, as slippery as ice. A pickaxe gets it back.
- **Sprouted Glow Lichen**: bone meal on vanilla glow lichen sprouts it (same faces). By day, with sky light 9 or more,
  it breaks the rock it clings to down a step (stone, deepslate, basalt, smooth basalt, granite, diorite, andesite,
  tuff, calcite, cobblestone, cobbled deepslate, sandstone -> gravel; gravel -> sand 60% / coarse dirt 40%; coarse
  dirt -> dirt), or seeds a plain glow lichen onto another such block within 8. With no rock left in reach it stops;
  with 12 lichen within 8 it stops seeding. Never ores, never a block that holds something. `LichenLife`; the rock
  lists are the block tags `jaymospowers:lichen/to_gravel`, `to_soil`, `to_dirt`.
- **Shears** on a sprouted lichen snip it (it only glows from then on); on vanilla glow lichen they make **Snipped
  Glow Lichen**, a lichen that never spreads. Builds are safe.
- **Succession**: yellow moss creeps onto dirt and coarse dirt beside it while glow lichen is within 4 blocks; under
  open sky it sometimes raises a shrub (stunted red where rock is still under the moss, else orange or yellow), no
  more than 3 within 5 blocks. `YellowMossBlock`.
- **Phosphor**: the glow ink sac is renamed Phosphor (English) and redrawn; any glow lichen smelts into one.
- **Glow lichen's look**: vanilla glow lichen (and ours) grows tufts and crusts on a floor and hanging strands under
  a ceiling.
- Changed on purpose: bone meal in the hand no longer spreads vanilla glow lichen, it sprouts it (a dispenser still
  spreads it).
- Not built: yellow moss layers through the saw (the layer list is generated from wooden and stone blocks, not a
  one-line registration); a claims check for the lichen (Powers has none to ask).
- Built and compiled; not played. JEI showing the Phosphor recipe is unchecked.

## 0.22.73 (2026-11-12): Chrono, Blindness and Rig additions, arrowheads, a belt of throwables, shield moves, three draughts, two pacts and trained hands (#1355)

**Chrono**
- **Calculation**: parries, perfect parries and blows that pass through you in Time Flow feed a bar. Full, every blow you land finds a weak point (more damage, may open the most hurt limb). Sneak + G spends it: for a few seconds blows and shots aimed at you miss and you slip aside (falls, fire, magic and blasts still land).
- **Afterimage decoys** (sneak + V while overclocked): copies of you stand about, more in a higher gear. Hostile creatures go for them, are slowed when they pop one, and now and then you change places with a copy.

**Blindness**
- **Radar sense**: someone who can't see is no longer blinded by flashes, smoke or darkness thrown at them. Hostile creatures near you are felt and shown, and one behind you or out of sight is called out with a red mark on that side of the screen and a low knock.
- **Pursuit**: a melee blow marks the creature and opens a cut; while the mark lasts you sprint faster toward it. It shows in gold on the radar.

**The Rig** (all from the Rig menu, Sneak + J, and the new Wrist Table screen)
- **Drones**: one for each level of the Mind, up to five. Formations (close in, surround, strafe, scout), a shell that takes blows for you, one sent as a missile, and duties when there is nothing to fight (fetch items, mend you, patrol a route of up to four marks).
- **Wrist Table**: a map of the land the suit has seen with drones, enemies and patrol marks, a list of creatures studied (read with the Scrying Lens, or five of a kind felled), and every drone order with what it does.
- **Decoy**: a lure that every enemy turns on while you go unseen, or three images that copy your moves.
- **Storing field**: keeps up to three parried blows (a perfect parry at double strength) and lets them all go at once.
- **Bank shot**: look at a wall with an enemy near it and a faint line shows the bounce; arrows, thrown things and the palm blast turn off that wall toward the enemy.

**Arrowheads** (they ride in the quivers the pack already has)
- Fourteen heads fitted to plain arrows at the Fletching Station: bola, net, caltrop, harpoon, grapple, smoke, flare, whistle, barbed, broadhead, fire, flash, drill and boomerang. Iron heads are forged from blanks; every route shows in JEI.
- **Quiver: Next Arrowhead** (mouse button 5 unless changed) brings the next kind to the top of a worn quiver (needs Jaymo's Kitchen for its quiver).
- **Trained archers** (by arrows loosed): drawing in the air slows your fall, sneak + full draw looses a second arrow behind the first, and a full draw loosed in the air becomes a volley on the spot you aimed at.
- Arsenal's rift arrows can carry a fitted head.

**Belt of throwables** (leather, iron, steel; worn in the belt slot)
- Eight throwables: smoke pot, flash powder, sneezing powder, bleeding dart, acid vial, choking vial, caltrops and bear trap. Click them onto the belt to load it. **Belt: Throw** is mouse button 4 unless changed; sneak + throw picks the next kind.

**Shield moves** (shield up)
- Attack throws the shield (it ricochets and comes back; a round shield throws best), sneak + attack slams it into the ground, sprinting charges behind it, and standing still or sneaking braces: nothing from the front moves you and arrows are turned back.

**Alchemy**
- **Skin Draught**: drink, then touch stone, iron, glass, ice, wood, slime, honey, sponge, obsidian, sand, wool or a bell to take its skin for a while, each with a gain and a cost.
- **Dampening Draught** (also as splash, lingering and tipped arrows): shuts a power away for a few minutes. It always wears off. Great foes shrug most of it off.
- **Draught of Release** (late research): drunk twice, slowly, it gives a power up for good and leaves it in a sealed phial a powerless person can drink. A Xeno-Band comes off instead. It will not take on a Z Fighter, a bonded symbiote or anyone bound by a pact.
- **Two pacts**, for a holder whose power is already corrupted, one for a person, never undone: one is offered by a demon master; the other comes to those who carry a Maw void shard long enough.

**Everyone**
- **Matching colours**: wearing the same costume design as a companion within 12 blocks steadies you both (a little toughness and slow mending).
- **Boxing**: with fist gear, trained boxers get a loose-joint chance on any punch (style level 2), a pressure point on sneak + punch that deadens an arm or leg for 10 seconds (level 4; on a player's arm it can shut two ability keys for a moment), a knee kick on sprint + punch (level 5) and a sweep on sneak + strafe + punch that puts a person on their back (level 7).
- **Planted stance**: sneak with any guard up for a second to plant. Blows are cut by 60%, nothing stuns or moves you, and you barely walk. A parried melee blow hands part of its weight back to the attacker. Jump while guarding (not sneaking) to step up and back out of reach.

**Changed on the way**
- Sneaking now counts as standing still for the shield brace.

Classes: `power/chrono/ChronoCalculation, ChronoDecoys`; `power/blindness/BlindnessRadar, BlindnessPursuit`; `rig/RigPicks, RigDrones, RigTable, RigStudy, RigDecoy, RigField, RigBank`, `client/rig/RigTableScreen`; `gear/` (arrowheads, `QuiverCycle`, `ArcheryMoves`, belts, throwables, `shield/ShieldMoves`, `GearJei`); `alchemy/picks/SkinDraughts, Dampening, GivingUp, Pacts`; `costume/Rally`, `boxing/PressurePoints`, `combat/Stances`. Art from `tools/gen_gear_picks.py` and `tools/gen_alchemy_picks.py`.

Compiled only; nothing was played in a client. Least sure: the sweep's back-fall pose, the shield throw and charge feel, drone flight, the bank-shot bounce, the skin overlay on modded bodies, and mouse side buttons on mice that lack them (rebind in Controls).

## 0.22.74 (2026-11-12): A ticking charm and one holder per power (#1364)

- **Ticking Charm** (charm slot; clock, copper, string and amethyst): it clicks faster the nearer you are to the Irradiated Giant's monolith or a blighted wolf. Sound only. Needs Jaymo's Kingdoms to have anything to hear.
- Honey and wild garlic ease the blight sickness, and the Gaunt and its heart have alchemy essences.
- **One holder per power**, off by default: `/power oneholder on|off|status` and `/power oneholder free <power>` for operators. While on, a power someone already holds cannot awaken in, be granted to or be drunk from a phial by anyone else, until its holder dies for good, gives it up or an operator frees it. Z Fighter lineages and Xenoform are exempt.

Classes: `baubles/TickingCharm`, `core/OneHolder`, hooks in `compat/KingdomsCompat`, `Afflictions`, `Powers`, `PowerCommand`.

Compiled only; not played.

## 0.22.75 (2026-11-12): ground sticks look like sticks; firepits and bonfires

- **Loose sticks on the ground** were the rock pile's chunky boxes wearing a mostly see-through texture, and the block draws solid, so they showed as dark slabs. Each stick is now a thin rod (about 1 pixel thick, 6-11 long) lying at its own angle, with lengthwise bark, pale end grain and a side twig on two of them; a 2-4 pile crosses them over each other. Piles no longer shift off the block centre. The outline box hugs the sticks. The creative-tab Loose Sticks item has a model now.
- **Laying sticks**: right click the top of bare ground (soil, sand, gravel, stone) with a stick to start a pile, or sneak + right click to lay one on anything; right click a pile with a stick to add one (other items pick one up).
- **Firepit** (TFC style, one block): right click a stick pile with rocks to set a ring of stones round it, one rock a click; 4 sticks in a full ring of 8 rocks becomes a Firepit: stones in a circle, ash, a stick teepee, the fire inside. A campfire underneath (campfires tag, vanilla campfire block entity): flint and steel, a flint-and-rock strike or a fire charge lights it, it cooks four foods, smokes, burns, a shovel or water puts it out, Kitchen's spit goes on top, sitting by fires counts it. It burns fuel: a stick 22.5 s, a plank 45 s, a log about 1 min 52 s (TFC's times), up to 15 sticks' worth; right click to feed it. Out of fuel it dies to ash and won't take a light until fed; rain puts it out. Empty-hand right click takes a ring stone back off a pile.
- **Grate**: right click a firepit with iron bars, or sneak + right click with 4 sticks, to lay a grate over it; Kitchen's cooking pot and frying pan go over a lit firepit only on a grate (firepit added to `jaymoskitchen:heat_sources`). Sneak + empty hand takes the grate off (a pan on it pops off). A stick grate chars through in time (10% per stick of fuel burnt).
- **Bonfire**: nine unlit firepits in a 3x3 merge into one Bonfire: a big ring of boulders round the 3x3, ash, a log teepee and a two-block fire. It starts with the pits' fuel, burns it 9x as fast, gives full light, and burns harder; uses on any part go to the centre; breaking any part breaks it all (each part gives back its 8 rocks).
- Breaking a firepit gives back its 8 rocks (as plain stone rocks), up to 4 unburnt sticks and its grate. `ground/FirepitBlock`, `BonfireBlock`, `BonfireEdgeBlock`; assets from `tools/gen_loose_sticks.py`. Built; not played.

## 0.22.76 (2026-11-12): collisions - wall slams, wall bumps, head bumps, sore knuckles, horse crashes

- Andy's BONK! picks (ideas only; BONK! is All Rights Reserved). New `core/collide/Collisions` (+ `CollideMoveMixin` on `Entity.move`, `client/collide/CollisionsClient`).
- **Wall slams**: anyone knocked back or blown into a wall at speed is hurt by how hard they hit it (from 0.55 blocks/tick: 1 damage, about 5.5 at 1 block/tick, up to 20), with a thud, the wall's dust and a second of Slowness II. A slam of 6 damage or more knocks a player limp (Kitchen ragdoll). Mobs are judged on the server, players on their own client.
- **Running into walls**: a flat-out sprint straight into a wall at body height (not a step, not a glancing brush) bumps you, at most half a heart, with a jolt. Freerun moves, ledge grabs, wall slides, crouching, swimming, ladders and riding never count.
- **Head bumps**: jumping into a ceiling knocks your head (a quarter heart on a plain jump, more the faster you go up). Andy: "hitting hard enough can ragdoll you": 0.7 blocks/tick or faster (a boosted jump, a launch) knocks you limp.
- **Sore knuckles**: an empty fist that punches or starts to dig a block that needs a tool (stone, ore, metal) bruises that arm by half a point, once per punch; so does punching anyone with 8+ armour, a Stoneborn, or a `jaymospowers:tough_hide` mob (golems, ravagers, armadillos, turtles, shulkers, wardens, guardians, hoglins, sniffers, skeletons, withers, the dragon). Fist gear spares you, and so does being Stoneborn. Logs still splinter (Splinters).
- **Riders thrown**: a galloping horse (0.4 blocks/tick into a wall) throws its rider forward limp; both are hurt. `jaymospowers:crash_proof`, no-gravity mounts and anything called a dragon never crash.
- Built; not played.

## 0.22.77 (2026-11-12): wound marks - hits leave a mark of what made them, fresh cuts drip

- Andy's Cruor picks (ideas only; Cruor is All Rights Reserved). New `core/wounds/WoundMarks` (synced attachment, fed from `Wounds` after each routed hit) and `client/wounds/WoundMarksLayer`.
- **Wound marks by weapon**: a hit of half a heart or more leaves a mark on the part it was routed to, on the side it came from: a blade a cut (dark line, raw edges), claws and teeth three raking cuts, arrows, bolts, cactus and berry bushes a puncture with a short run of blood under it, fists, blunt weapons, falls and wall slams a bruise that yellows as it heals, a blast specks and soot over up to three parts. Up to 10 marks a body. On humanoid skins (players, MCA folk, zombies) each lies on its own part and side; on other creatures somewhere on the skin. Bloodless bodies (skeletons, Stoneborn) get none.
- Marks fade in four steps over their life (bruises 6 minutes, the rest 8 to 10) and go as soon as the part has mended whole. Painted into the skin as a cutout picture of just the mark pixels, so the blood coat still shows round them. Not kept over a restart (as blood coats aren't).
- **Fresh wounds drip**: a fresh cut, claw or puncture that isn't already bleeding properly (Bleeding) drips a few drops onto the ground for 30 seconds, about every 1.5 s at first and every 7 s by the end, leaving lasting stains (BloodTrail).
- Not yet on corpses. Built; not played.

## 0.22.78 (2026-11-12): stumps and smears - lost limbs leave stumps, cut-off parts leak, bodies smear where they land

- Andy's Cruor picks (ideas only).
- **Stumps**: a humanoid's lost arm or leg is drawn as a short stump at the shoulder or hip (`Dismembered.STUBS`, 28% of the limb) instead of vanishing. Its end is raw and red while it bleeds (Bleeding's stump bleed, until cauterised), and capped once closed: scar pink, puckered to a seam (`WoundMarksLayer`). A limb with a prosthetic shows the prosthetic as before.
- Andy: "the unarmed start starts with the lost limb capped": its long-healed limb never bleeds, so its stump is capped from the start.
- **Cut-off parts leak**: a severed head or limb lying on the ground keeps leaking from the cut for 30 seconds, slower and slower (every second at first, every 4.5 s at the end), into a small lasting pool, then stops (`SeveredPart`).
- **Ragdoll impact smears**: a corpse that hits the ground or a wall hard (thrown, blown, dropped; it lost 0.4+ blocks a tick in one step) leaves a blood blot where it struck and throws a few drops, bigger the harder it hit (`BladeBlood.impact`, through Jaymo's Kitchen's new `CorpseImpacts` hook). Bloodless bodies don't.
- Built; not played.

## 0.22.79 (2026-11-12): Blood that lingers, rots and stinks

- Blood on you stays until you wash it off properly: a player's blood coat is saved and no longer wears off by itself.
- Blood ages: fresh, dried after about five minutes, rotten after a day. It darkens as it dries and turns brown-green as it rots, on your body as well as on your gear.
- Dried blood washes off at less than half the usual rate, and rotten blood at a sixth. Water and rain never take the last of rotten blood off: scrub it at a water cauldron or with a wet sponge.
- Old blood smells, and rotten blood smells worst of all. It adds to how bad you smell, so beasts notice you sooner and villagers remark on it.
- Rotten blood draws flies, on you, on mobs and on corpses.
- Heavy hits throw more blood back on whoever struck, and splash anyone standing close.
- Classes: core/blood/Bloodied (coat age, stages, washing, reek, noticed, splash-back), core/wet/Smell, client blood/Flies, client wet/ArmourGrimeTextures.
- Built; not played.

## 0.22.80 (2026-11-12): Blood in the world: surfaces, water, lava, drips, sounds, a setting

- What blood lands on changes it: wool, carpets, hay and moss soak it into a small dark stain that lasts; dirt, grass, sand, gravel and snow drink it into a dull patch that is soon gone; glass and ice let it smear thin and wide.
- A fresh pool on a thin floor (planks, slabs, trapdoors, leaves, wool, hay, scaffolding) with open air under it soaks through and drips from the underside for half a minute.
- Blood falling into water turns into a drifting cloud that spreads and fades. Blood falling into lava hisses away in a puff of smoke.
- Freshly bloodied weapons drip now and then for about a minute, more often the bloodier they are, on anyone carrying one.
- Landing blood makes a quiet wet splat.
- New Blood button in Video Settings: Light, Normal or Heavy. It changes how many drops hits throw and how long splashes lie. Trails and stains that others can track stay the same for everyone. F3 shows the setting and counts the stains, pools and drops nearby, under the particle count.
- Classes: client blood/BloodSurfaces, BloodSettings, WeaponDrips, BloodPuddle, BloodDrop, BloodClient; mixins BloodVideoSettingsMixin, BloodDebugLineMixin.
- Built; not played (the two mixins are untested in game).

## 0.22.81 (2026-11-12): zombies run for the nearest shade (#1374)

- **Nearest shade first**: an undead caught in the sun now heads for the closest spot the sun can't reach. That can be inside a house, under an overhang, in a cave, or in the shadow a wall, house or cliff throws on open ground. A house next door now beats a cave down the hill. Tree shade still doesn't count, since sunlight gets through leaves.
- **Only shade it can reach**: a spot behind a shut door is skipped, so zombies no longer stand at a closed house while they burn.
- **Zombies break off a fight for shade**: a zombie chasing you in daylight runs for shade if there's some close by. Once it's out of the sun, it comes straight back for you. With no shade nearby it keeps fighting and burns. Skeletons and strays behave as before here.
- Classes: core/sun/SeekShelterGoal (wall shadows within 12 blocks, reachability check via a real path, fleeMidFight), mixin/SunShelterZombieMixin (goal now priority 1, ahead of the spear and melee attacks).
- Built; not played.

## 0.22.82 (2026-11-12): Boxing: the three stances copied from fight footage

- (Jung, outside Wind) The three styles' stances, blocks and shoulder roll are reshaped after fight footage Jung sent
  ("Every Boxing Guard Explained in 8 Minutes": its peekaboo, long guard and Philly shell parts). Third person only,
  as before.
- **Elbows:** a boxer's arms now bend (the pack's own limb bending, as the crawl and the climb use). A guard is an
  upper arm and a folded forearm: gloves up on the cheeks, or a forearm across the belly. A straight punch
  straightens the elbow as it goes out, a hook keeps the forearm folded across, an uppercut rises with the forearm
  up. The worn wraps, cestus and bracers turn with the forearm.
- **Arrow Slit** (peekaboo): both gloves up on the cheeks, a forward crouch with the chin down, the knees bent with the feet
  flat. The sway is in the shoulders and head; the hips and feet stay put. Its block pulls the gloves in tighter and crouches lower.
- **Long Guard**: the lead arm out at shoulder height, all but straight, the rear glove by the chin, the body upright, a
  little back, stood at an angle from the feet up. Its block puts both arms out ahead with the hands together (it crossed the forearms before).
- **Bastion** (Philly shell): side on with the lead shoulder forward and held up by the chin, the lead forearm across
  the belly, the rear glove by the cheek, the weight back, the head tipped to the rear shoulder
  with the chin tucked. Its block turns further side on with the shoulder higher. Its shoulder roll now also sways
  back and tips the head away.
- **A base under each style**, after reference pictures Jung sent (Bivol's long guard, Mayweather's Philly shell, a
  peekaboo crouch): feet apart, the lead foot ahead and the rear one behind, knees bent, feet flat. The Arrow Slit
  is low and wide with its gloves up at the cheeks; the Long Guard long and balanced, bladed from the feet up, chin
  tucked; the Bastion upright on soft knees, its lead forearm low across the belly.
- When the body turns the shoulders go round with it, and when it leans the legs stay under the hips (neither did
  before). A cross now brings the rear shoulder forward; it turned the wrong way.
- `client/boxing/BoxingPoses`: every angle is still a knob at the top. Not built locally; not played, the angles are
  set from the footage without seeing them on a model.
- **Worn fist gear fits the hand:** wraps, cestus and bracers sat only just proud of the arm, inside a skin's sleeve
  layer, so they showed in patches. They now stand clear of it all the way round, are narrower on slim arms, and
  bracers cover the hand like a glove, from the wrist to its end, so they turn with a bent forearm.
- The Arrow Slit's back is slightly rounded (the spine bends halfway down, the same way the limbs do), and its head
  stays up rather than tucked.
- **Punches are a boxer's own, not the game's arm swing**, after a jab and cross lesson Jung sent ("The Punches: Jab
  Cross (1 & 2)"). A jab snaps the lead arm straight out at chin height with its shoulder up and turned in behind
  it, the rear glove staying on the cheek. A cross sends the rear arm straight out as the rear shoulder and hip turn
  through and the rear knee turns in, the lead glove coming home to the cheek. Both go out fast and come home slower.
- **Hooks reach**, after a hook lesson Jung sent ("Throw a Perfect Lead and Rear Hook"): the arm level at shoulder
  height, the elbow open past a right angle, swung from out wide to land ahead on the centre line (it folded tight
  across the chest before), the hips and shoulders turning with it and the foot on that side pivoting. The Arrow
  Slit's flurry hooks are a lighter, shorter swing; the full hook is the one a slip to the side sets up.
- The punch's pose now starts when the server says a punch was thrown, so a punch queued by an early click shows
  when it lands (it showed nothing before). A click that hits nothing shows a jab.
- First person: a boxer's arms stay in the guard and the punching arm goes out from it (the lead hand for a jab or
  lead hook, the rear for the rest); before, the game's own swing took over.

## 0.22.83 (2026-11-12): Our own immersive sounds - footsteps, armour, water, jets, stations, drops, stomach, beds, sound browser

- What ImmersiveFX: Resurrected gave the pack before it came out for lag (#1361), built as our own and kept cheap. Nothing of it is used (AGPL, recorded sounds): 247 takes in 86 sounds are synthesised by `tools/gen_immersive_sounds.py` (sounds/immersive/, events `immersive.*` at the top of sounds.json).
- **Lag guard** (`client/sound/ImmersiveSounds`): every extra plays on this side only, never farther than 20 blocks from the camera, at most 6 a tick, and none while the sound engine's channels are over 70% full (read once a tick from its own "Sounds: x/y"), so they can't crowd out real sounds.
- **Footsteps by material** (`Footfalls`, `FootfallLevelMixin` on ClientLevel's sound play): every step sound the game plays gets a layer from the ground (`SoundKinds`: grass, stone, wood, sand, gravel, snow, metal, carpet, mud; dirt counts as soft earth, snow layers and carpets on top count), a harder one running, a push-off jumping, a landing as heavy as the fall. Sneaking takes the step (the game's own and ours) to 50%, the stance wheel's low crouch to 35%, crawling to 25%.
- **Armour by material**: on each step the worn set sounds as it's made (cloth, leather, chain, scale, plate, heavy, bone, crystal), louder the more of it is worn; soft sets every other step. Tinkering's bog iron (plate), Arsenal's Void Crown (crystal), the Rig (heavy), caveman hide, ichorcloth, Kingdoms' Ronin/Kintsugi/Warlord/Paladin/Gladiator/Heavenly/Reverent/Primal and the Aether's sets are named; other mods' armour goes by its equip sound, then words in its id. Replaces Ambience's single armour clink.
- **Wading and swimming**: sloshing while wading, strokes while swimming (quicker sprint-swimming), a deep plunge and bubbles diving in fast.
- **Steam, fire and bubble jets** (`Jets`): steam hisses off water touching lava, some open lava pools burst into flame spouts now and then, a few sea floor spots 3+ blocks deep bubble steadily. Scanned a slice a tick (14 blocks out, 6 up and down, 24 slices); which pools and floors is fixed by the spot. Particles off on Minimal.
- **Workstation sounds** (`Workstations`, `WorkstationSlotMixin`): each station sounds as it opens (crafting table, anvil, loom, stonecutter, smithing table, furnaces, grindstone, cartography, brewing stand, enchanting table, the Tinkers tables, the smeltery), taking a result sounds like the work (by what the item is made of at a crafting table), a furnace sizzles each time an item is done while you watch, and the stations worked in the world sound as they're used: crucible and alembics, smith's anvils and forges, forge hearth, lapidary wheel, and Kitchen's pots, pans, stoves, boards (a chop with a knife), presses and grindstones.
- **Dropped items** (`ItemDrops`): an item sounds once as it first lands after falling: metal rings, wood thuds, coins clink, stone clacks, glass tinks, gems tick, paper flaps, bone clacks, soft things thump.
- **Stomach** (`core/StomachSounds`, server, heard by everyone near): at 6 food or less the stomach growls every 30-90 s (to half that, and louder, when empty); at Kitchen's thirsty point a dry swallow now and then. Never androids or the Divine-born, nor anyone asleep, creative or spectator. `KitchenCompat.water` added.
- **Beds**: a rustle as anyone lies down or gets up (sleep or Kingdoms' bed rest), and now and then as a sleeper turns over, more often while the night runs by as a timelapse.
- **Sound browser** (`SoundBrowserScreen`, `SoundTuning`, `SoundTuningEngineMixin`, `SoundBrowserOptionsMixin`): Music & Sounds > Sound Browser... (or a key, unbound at first) lists every sound heard in the last minute, newest first, and any you've turned down; search finds any sound. Click the bar to set a volume (5% steps), the name to mute, right-click to reset. "Thin repeats" (on) drops a sound's starts past 6 in half a second (never music, records, voices, menus or loops). Saved in config/jaymospowers-sounds.txt. Covers our power sounds too.
- Built; not played. Synthesised takes not listened to in game yet.

## 0.22.84 (2026-11-12): Dynamic Waters rivers replace Streams Reflowing

- **Rivers**: the pack now ships our own 26.3 port of AdamNew's Dynamic Waters (MPL-2.0, source in `ports/dynamicwaters`,
  credits and every change in `ports/dynamicwaters/PORT.md`). Rivers run downhill from the mountains to the sea, carve
  valleys and gorges, push swimmers, boats and dropped items along with the current (faster in rain and storms), freeze
  over in the cold and break up into drifting ice floes. Its config screen is in Mod Menu.
- Streams Reflowing is gone from the pack build, and so is `core/StreamsPreset` (it only set Streams' world preset).
  `water/Currents` stays for running water outside the rivers and for splashes; Dynamic Waters pushes its own river
  channels.
- Needs a new world: old worlds keep their old rivers and get odd seams at new chunks.
- Built and boot-tested on a dedicated server; not played.

## 0.22.85 (2026-11-12): Ice Caves, crevasses and brittle ice from blue ice (26.4)

- **Ice Caves** (`jaymospowers:ice_caves`): a new cave biome under the colder lands (temperature below -0.3), from about
  25 blocks under the surface down to the deepslate depths, like the lush and dripstone caves. The driest cold ground
  (the tundra and dry snowy plains) keeps its old rock. Where a lush, dripstone or sulfur cave would be, that cave wins.
  New chunks only.
  - The rock is packed ice with bands of calcite (material rule `jaymospowers:ice_caves`, noise
    `jaymospowers:ice_cave_gradient`). Large icicles of packed ice hang from the ceilings and rise from the floors,
    sometimes joined into columns. Icicles hang in patches (a few standing under them), snow and ice crystals lie on the
    floors, and there is no glow lichen. No lava lakes or lava springs; water springs, geodes and dungeons stay.
  - Ores come out in the ice inside a pocket of stone (deepslate below Y 0): every ore feature (ours, vanilla's and
    other mods', `OreStandInMixin`, `ScatteredOreStandInMixin`), Powers' great veins (`BigVeinFeature`) and Kingdoms'
    world tier veins (`GroundChanges`).
  - Spawns: the usual cave monsters plus strays (they spawn here without sky, `StraySpawnMixin`) and Frostbites.
    Mineshafts, trial chambers, ruined portals and strongholds can generate here. Frozen peaks music, icy fog.
- **Crevasses** (`jaymospowers:crevasse`, the 26.4 feature's settings): winding cracks carved into ice cave floors,
  narrowing at their ends and towards their bottoms, with a bed of powder snow down in them and roofed at floor level
  with brittle ice. They are only carved where the walls hold, so they never break into another cave.
- **Brittle ice from blue ice**: water with blue ice beside it (north, south, east or west) freezes into brittle ice
  when it is placed, flows or something next to it changes (`BlueIceWaterMixin`).
- Brittle ice: what may cross it is now the entity tag `#jaymospowers:does_not_break_brittle_ice` (Frostbites, strays).
- Classes: `frost/IceCaves`, `frost/IceCaveFeatures`, `mixin/icecaves/*`.

## 0.22.86 (2026-11-12): Rivet mans cannons; waking in your ship's berth

- **Rivet**: B on a cannon (Kingdoms' guns, on land or aboard a ship) melds him into it: he lays it where he looks, Space fires, and he reloads from the gunpowder and shot he carries. Sneak or B again leaves (`KingdomsCompat.meldGun`).
- **Death wake-up**: if your spawn is a berth aboard a Kingdoms ship that's still afloat, you wake there ("You wake in your own berth...") (`Mortality.BERTHS`).
- Built; not played.

## 0.22.87 (2026-11-12): fishing - new species, legends, rod pod, ice fishing, crab pot, journal, contests (Tracks B leftovers, C and D)

- **30 new species** (docs/Plan-fishing.md Track C), each with its own model, texture, raw and cooked item, spawn egg,
  water, climate, depth, hours, seasons and size range: carp, pike, perch, zander, bream, tench, catfish, eel, trout,
  chub; char, grayling, whitefish, burbot; mackerel, herring, pollock, sea bass, halibut, swordfish; catshark, dogfish,
  blue shark, hammerhead, great white; shrimp, crayfish, crab, lobster, jellyfish. Real fish (`WildFish`) that spawn
  in schools and carry their own weight (it sets their size); walkers and the jellyfish are `Critter`s (crabs walk
  sideways, all rest by day, a jellyfish stings). Big sharks have no item: cut up on the shore into shark meat, and
  their jaw is kept (it mounts on a plaque). Models and art generated by `tools/gen_fishing_species.py`.
- **Night fish, schools and hunters, seasons**: each species feeds at its own hours (eels and catfish at night, zander at
  dusk), bites more in its seasons (Kitchen's `Seasons`, by reflection) and in rain; hungry predators chase smaller fish,
  and where they hunt, fish round about are 1.8x keener for a minute (`FishSchools`). A fed predator stops hunting.
- **Size curve**: weights fall away like an exponential: half in the bottom fifth of the range, about 1 in 100 in the
  top tenth (`FishCatch.curve`).
- **Other mods' fish** (`FishCompat`): any fish entity (or `#jaymospowers:hookable_fish`) is hooked like ours; one whose
  name says what it is (a "catfish", a "trout") fishes and lands as our species, anything else drops its own loot.
- **Legends** (`FishLegends`): every 256-block water (fresh and salt apart) has one named old giant of a kind that lives
  there, 1.3-1.65x its kind's top weight, wary, rising only to someone fishing deep water there; it fights 1.4x harder
  and twice as long. Landed and kept, the water has none for 120 days; put back, it's the water's legend again.
- **Lure depth** (`LureDepth`): a lure on a spinning or sea rod sinks 0.05 blocks a tick while you wait, a ledger rod's
  bait 0.15, down to the bed; the depth shows on the action bar, and fish only take bait near their own depth.
- **Ice fishing** (`IceFishing`): the Ice Auger bores a hole in ice over water (hold use 2.5 s); it stays open 3 days
  (`BiomeFreezeMixin`), a hook landing on the ice beside it drops in, and fish crowd under it (1.5x keener).
- **Rod pod** (`RodPodEntity`): rest up to three baited rods; each casts ahead and fishes like a held rod. A nibble
  clicks; a take rings the bell, lights the alarm and gives a full redstone signal for 8 s: pick that rod up with an empty
  hand to strike.
- **Crab pot** (`CrabPotEntity`): sink it, bait it with meat or fish; crabs, lobsters, crayfish and shrimp walk in
  (nearby ones are drawn to it; others come in over time, three times as often at night). Six at most, a bait lasts three.
- **Catch and release** (`CatchRelease`): a landed fish is alive for 2 minutes (kept alive in a keepnet or a crab pot in
  water); use it on water to let it go, at its weight, and it grows on. Sneak and use to look it over.
- **Personal bests and the Fisher's Journal** (`FishLog`, `JournalScreen`): every catch is written down (count, released,
  best weight with its day, bait and water, the hours each kind bites for you), kept through death; beating a best plays
  a fanfare. Read it in the Fisher's Journal (book + any fish) instead of a `/pb` command.
- **Keepnet, trophy plaque**: the keepnet holds nine live fish in the water; the trophy plaque mounts any landed fish or
  a shark's jaw on a wall, with its name, weight, length, angler and day.
- **Fishermen** (`Fishermen`, `FishermanScreen`): Kingdoms' "Talk about fishing" (or hand a fisherman a fish) opens their
  screen: they buy fish by weight (live ones 1.25x, a legend 3x; each 40 kg sold in a village in a day cuts the price),
  paid in the local coin with Kingdoms, else emeralds with the change kept. Every 4 days a contest runs sunrise to noon:
  enter, fish within 128 blocks of the village, your heaviest counts against three of the village's own anglers; the
  winner gets 8 emeralds' worth and an Angler's Ribbon. They also tell you of the water's legend.
- Landing net now scoops the new small species and critters, alive and tagged.
- Built; not played.

## 0.22.88 (2026-11-12): Fix: the game crashed on startup with the gun mod (tundra biomes)

- (Jung) The game crashed while starting ("No value registered for key jaymospowers:tundra" and
  "jaymospowers:lichen_woodland", from TaCZ's entrypoint). The tundra's two biomes were put into the Overworld's biome
  list from the moment the game loaded, and TaCZ builds a vanilla-only set of registries during startup, where those
  two biomes don't exist yet.
- The Overworld's list now stays vanilla's until every mod has started (`TundraWorld.place`, called from the server
  and client entrypoints), the same way the demon realm's Nether biomes already do. Worlds load their registries
  later, from the data files, so the tundra still generates in new chunks.
- The new ice caves biome had the same fault ("No value registered for key jaymospowers:ice_caves" would have been next)
  and waits for `IceCaves.place` the same way.

## 0.22.89 (2026-11-12): crawl key unbound; mobs can drag the downed

- The Crawl key starts unbound: the Down arrow goes to Kingdoms' Come order (Andy). Sneak + scroll down still takes you prone, and Crawl can be bound again in Controls. Existing worlds keep whatever key their options already hold.
- Carry: a mob can now take someone downed by the shoulder and drag them, drawn exactly like a player's drag (Carry.npcDrag, npcLetGo, npcDragging, holderOf). Jaymo's Kingdoms' field medics use it to pull the fallen out of a fight.
- Carry: a mob can also lift someone downed over its shoulder (Carry.npcLift, fireman hold, too big ones refused), drawn like a player's shoulder carry. Jaymo's Kingdoms' healthy field medics use it.
- **Sulphur and saltpetre**, the two minerals of true black powder (Andy). Sulphur ore lies deep in the overworld's stone and deepslate and all through the Nether's netherrack, thickest in the basalt deltas; yellow lumps of it lie loose on cave floors beside lava and magma, on the Nether's floors and round the rims of hot springs. Saltpetre ore lies in the stone and sandstone under deserts, badlands and savanna, with a thinner scatter in any cave; white crust lies loose on dry sand and terracotta and on the floors of dry caves. A shallow vein of either shows at the surface as a loose piece of the mineral. Any pickaxe mines them. Three saltpetre, a sulphur (or brimstone) and a charcoal craft four gunpowder. Alchemy reads them too (sulphur and salt).
- **Nitre bed**, the old way to grow saltpetre (Andy). Craft one from three dirt, two wheat and a bone meal, set it under a roof and feed it manure (Jaymo's Kitchen's), rotten flesh, bone meal or wheat; droppings and scraps that land on it are worked in by themselves, so a bed in a barn feeds itself. Fed and roofed, its top slowly whitens; turn it with a shovel to speed it up. Rain falling on an open bed washes the nitre back out, and under open sky it doesn't grow. When it's white, a shovel scrapes off 3 to 5 nitre earth, which you boil down into saltpetre in a furnace or over a campfire. A bed left alone just waits.
- Built; not played. New world chunks only: places already explored have none.
