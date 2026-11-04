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
