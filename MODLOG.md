# Jaymo's Powers: MODLOG

A superpowers mod for Minecraft Java 26.3 (Fabric). It's the centrepiece of a modpack with Jaymo's Kitchen.

## Design
- **Getting a power:**
  - **Survival:** each player's power awakens on its own after a random 1–3 Minecraft days of survival play
    (24000–72000 ticks; the timer is saved and kept through death). Hints appear 5 minutes and 1 minute out.
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
- **0.1.0, the core:**
  - Power API, registry, synced attachment, key packets, survival awakening, `/power`, powers screen, HUD badge
    and the anti-kick mixin.
  - The four powers were built in parallel by four agents, one git worktree each, then merged.
- **0.1.0, the four powers (merged):**
  - Wind (Viltrumite-style hover / cruise / sonic flight, channelled buffs).
  - Arsenal (pulls Balkon's WeaponMod: Legacy + vanilla weapons as 75 s summons, barrage, stash).
  - Web (rope-physics swing, zip, web shot, yank).
  - Chrono (rewind, stasis, overclock, time bloom).
- **The modpack:**
  - Mods: Fabric API 0.162, Jaymo's Kitchen 0.4.11, Balm, Cooking/Pantry for Blockheads, Balkon's WeaponMod: Legacy
    (+ Architectury, Cloth Config), Better Combat (+ Player Animation Library), JEI (+ MezzConfig), Nature's
    Compass, Xaero's Minimap, Sodium and Ok Zoomer. The extras were matched by file hash to the friends' server.
  - `gradlew packageModpack` (or `Build Modpack.bat`) writes the CurseForge import zip and `modpack/mods`, and
    syncs `Serverfiles/mods`.
  - Epic Fight can't be included: it is Forge/NeoForge only, up to 1.21.1.
- **Server (`Serverfiles/`, not in git; it holds the playit secret):** the old kitchen server, converted:
  - mods synced to the pack, `allow-flight=true`, a new motd and window titles
  - backups in `mods-backup-kitchen/` and `world-backup-before-powers/`
  - shares port 25565 and the playit tunnel with `Desktop\Minecraft Server`, so run only one at a time
- **Smoke tests:**
  - The dedicated server boots with 65 mods and stops cleanly.
  - The dev client loads 78 mods and joins a copy of the server world. No mixin or registration errors.
- **0.1.1:**
  - Wind: impact damage, HUD moved, hover ragdoll sway.
  - Web: two independent webs, ragdoll arm posing.
  - Chrono: HUD moved.
  - The server lives in `Desktop\Serverfiles`.
- **0.1.2:**
  - **Mastery** (`core/Mastery`):
    - A natural awakening unlocks only R. The rest unlock at 30, 90 and 180 points.
    - Points come from ability use (max one per 5 s) and from playing with the power (one per minute).
    - Creative and `/power set` give all four abilities. `/power level <players> <1-4>` sets the count directly.
  - H does nothing in survival until the power has awakened.
  - Wind realism pass and Tailwind ground gust. Web slingshot and web wings.
  - **Fall damage always applies outside creative** (user request):
    - Wind's and Web's fall waivers and Web's safe-fall passive are removed.
    - Controlled descents (Wind flight, web wings) slower than 0.5 b/t don't build fall distance, like vanilla's elytra
      rule.
    - Core: for power holders, fall distance resets while moving up, so falls are measured from the apex.
- **0.1.3:**
  - Wind: steep turn-rate falloff at speed. A snap turn at speed causes a 2 s ragdoll tumble, then recovery into a
    hover in the recovery pose (from the user's reference image).
  - Tailwind charges for up to 5 s. A full charge on the ground is a TNT-strength gust with a 180 s cooldown.
- **0.1.4:**
  - Web: charged web blast, glide lean, 2-block wing rule with no cooldown, slingshot lockout and aim toward the look.
  - `Power.unlockOrder()`: charged abilities unlock last (Wind R,V,B,G; Web R,G,B,V). `Mastery` follows each power's
    order.
- **0.1.5:**
  - Chrono: Stasis stops players too (held in place, no actions) for 3 s, and it's the last unlock.
  - Arsenal: Barrage blades land as pickupable weapons for the caster.
- **0.1.6:** Arsenal Reach Through is now animated: a portal opens, the arm reaches in, and the weapon is drawn out.
- **0.1.7:** Web Yank (B) holds its target on a web lead while held: you can drag it or let it hang, and it snaps when
  stretched or when the target fights back.
- **0.1.8:** Web Yank released while aiming at a block hitches the creature there (lead-style, barely able to move,
  breakable). Held or hitched creatures ragdoll.
- **0.1.9:** Web: no tug on cast, Jump thrust along the arc, pendulum body sway while swinging, wings and lines follow
  the posed body.
- **0.1.10:** Web Zip moved to its own key, Z (rebindable); G is only the off-hand web.
- **0.2.0, Ascension & Corruption** (core `Fate` + four agents, merged):
  - **Core:**
    - `core/Fate` attachment: ascended / corrupted / purified flags and a 0-100 meter.
    - The ultimate key C, sent only while ascended.
    - `Power.onLevelUp`, `onAscended`, `onCorrupted` and `onPurified` hooks.
    - Fate on the HUD badge: gold rim, cracks, a purple sliver.
    - `/power ascend|corrupt|purify|resetfate`.
    - Per-power `/powerdebug`.
    - Design rule: everything is discovered in play, the messages stay vague, and cures are circumstances,
      never items.
  - **Wind:**
    - Weather factor (sun 0.6 ... thunder 1.3).
    - Storm overuse corrupts it into Stormbound: lightning on every cast, 2x gales, -25% speed.
    - Cure: hover above Y 192 through 60 s of thunderstorm.
    - Ascension: 5 Breezes killed in flight → Gale Heart → Tempest + Cyclone Lance.
  - **Arsenal:**
    - Enderman contact fills the meter; the End corrupts outright.
    - Void Arsenal: end-gateway portals, aura, +30% damage with Darkness/Wither, water hurts, blinks.
    - Cure: help kill the Ender Dragon.
    - Ascension: Dimensional Key (bastion loot) → Armory weapon pin + Hundred Blades.
  - **Chrono:**
    - An Echo spawns 5-25 min after every level-up. It looks like its owner, copies their habits, is hostile to
      everything, and dies only in the Nether.
    - Ascension: 10 min of Stasis/Rewind, then sleep → Stopped Clock → Timeless + Paradox Cut.
  - **Web:**
    - Symbiote in rare meteor craters: black webs and suit, stronger webs, a hold-X radial menu with 6 abilities,
      raw-meat hunger, takeover.
    - 6 fire/noise exposures → rejection; you need a new crater.
    - Ascension: 2000 blocks swung → Spider-Sense + Web Strike.
  - **Lead fix:** world hazards pass through the Echo outside the Nether; only attacks (and the void) shatter it.
  - **Tests:** dev client joined a world with no mixin errors; the dedicated server booted and stopped cleanly.
- **0.2.1, lag pass** (following Jaymo's Kitchen's corpse fixes):
  - Hitch lines: the hitched creatures are found once a tick, instead of every entity being checked every frame.
  - Spider-Sense: "is it on?" is worked out once a tick, not per entity per frame.
  - Chrono echoes: they look up their mined and placed block kinds once per habit change, not on every try.
  - Audit result: the other tick paths were already throttled or skip early, and synced data is only sent on change.
- **0.3.0, Gravity and affinities:**
  - **Gravity, the 5th power:** Lift, Weightless, Gravity Well and Crush, plus Event Horizon / Singularity
    ascension and Collapse corruption with a Levitation cure. See `docs/gravity.md`.
  - **Affinity:** the first time a player is in survival without a power, they choose which power will awaken in
    them, or "let fate decide". The choice is final; `/power affinity <players> <power|fate|none>` changes it. Esc
    doesn't dodge the screen, and H reopens it if the choice wasn't made. The awakening still takes 1-3 days.
  - **Web:**
    - Wings give Feather Falling IV (-48% fall damage while they're spread, and for 0.5 s after).
    - Zip lets go 4 blocks short and flings you upward; sneak to ride it in and perch as before.
    - A player caught on the Yank line or hitched to a block can break free: swinging cuts the line, and straining
      past it for 1.5 s snaps it. They're also pulled more gently than mobs.
  - **Powers screen:** goes to three columns when the screen is wide enough.
- **0.4.0, starters, Wind reworks, keys:**
  - **Starters:** a natural awakening now begins with only a weak starter (Mastery level 0); the four abilities
    unlock after it, at 20/60/120/200 mastery.
    - Wind (passive, kept): a cloak of wind giving +10% speed, and slow falling while holding Jump in the air.
    - Gravity (passive, kept): loose items within 4 blocks drift to you, and +0.06 jump.
    - Chrono (R, gives way): Nudge, Slowness I on a target or Speed I on yourself (sneak), 15 s cooldown.
    - Arsenal (R, gives way): Rummage, junk or (10%) a stone weapon from a smaller rift; it lasts 30 s, 30 s
      cooldown.
    - Web (B, kept): the webline. Hold B on one block and let go on another: walkable web planks for 60 s, up to
      24 blocks long. Once Yank unlocks, B aimed at a block still strings weblines.
  - **Power Mode key (Z), core:** Web Zip moved onto it. A one-time key fixer (`config/jaymospowers-keys-v1`) moves
    these off the power keys:
    - Xaero waypoint B→N and enlarge-map Z→M
    - Pantry B→K
    - Ok Zoomer C→Left Alt
    - vanilla quick actions G→J
    - the hotbar save/load activators are unbound
  - **Wind:**
    - **Stormbound B is Call Lightning:** 6-9 damage, 1.5-3 hunger, 1-2 self static; 6 s cooldown.
    - **Z modes:**
      - **Stormbound mode:** an electric shroud. Melee stuns (Slowness IV 0.8 s and a jolt) and adds static; 5
        stacks shock the target, rooting it for 2.5 s with 3 damage. Self-zaps: 6%/s for 1.5 damage, 35% per
        cast for 3 damage. Costs hunger.
      - **Tempest mode:** a wind ring that deflects projectiles, and +25% to abilities and flight speed.
      - Ascended and corrupted together gives both.
    - **Air brake:** S at speed holds the palms out with a gust ahead and bleeds momentum to a hover; others see
      the pose.
  - The powers screen shows the starter line, plus corrupted key text (`.key.ability_N.corrupted`).
- **0.4.1:**
  - **H menu:** outside creative it shows only your own power. The affinity choice is switched off
    (`Affinity.ENABLED`), so awakenings are random again.
  - **Gravity:**
    - The starter now includes Lift on R.
    - Once R is unlocked, right click while lifting chokes the target: it's pulled to arm's length by the throat,
      the arm reaches for its head, and it takes 1 damage/s.
    - A second right click slams it straight down under extra weight. Damage is 4 + 3 per ability (+4 ascended);
      from 3 abilities it breaks the block it hits (hardness 1.5, 4 abilities 3.0, ascended 5.0).
  - **Web:**
    - The reticle shows only when something's in reach.
    - Weblines are drawn as one line between their ends, the same line as the swings. The planks are invisible
      but still walkable; the ends sync through a level attachment.
    - The local player now sees their own webline line while stringing it.
  - **Chrono:** Overclock slows everyone within 6 blocks (Slowness II, refreshed as you move).
- **0.4.2, pack update:**
  - Jaymo's Kitchen 0.4.13, with its corpse lag fixes.
  - Distant Horizons 3.3.4 (CurseForge 508933 / 9005667), the same file the Kitchen pack uses.
    - Its jar sits in `libs/`, git-ignored and copied from the Kitchen profile.
    - It goes to the server and the mods folder, and the CurseForge import downloads it by id.
    - It isn't loaded in the dev game.
  - `profileMods` now also swaps the Kitchen jar and Distant Horizons into the CurseForge profile.
  - The dedicated server boots with both.
- **0.4.3:**
  - **Wind:** armour weighs flight down (`WindArmor`). Leather and chainmail are free; per piece, copper and turtle
    0.5, gold 0.75, iron 1, diamond 1.5, netherite 2, and other mods' armour 0.75. Each point takes 6% off top flight
    speed (at least 40% is left), shown as "-N%" by the flight readout.
  - **Guns:** WeaponMod's flintlock, musket (and bayonets) and blunderbuss get 3D models in hand.
    - The models are voxelised from their own sprites by `tools/gen_guns.py`: wood 3px deep, metal 2px, dark parts
      2.5px.
    - They live in an always-on built-in pack (`resourcepacks/held_guns`) that overrides `assets/weaponmod/items/*`
      with a display-context select: flat in gui/ground/fixed, 3D elsewhere. The reload condition is kept.
  - **Living Armor tendrils** (`WebTendrilLayer`): 6 five-segment cords on the back and shoulders.
    - They grow thicker and longer over 10 s of armour, sway and ripple, and whip forward over the shoulders with
      each arm swing.
    - Everyone sees them; they're posed per wearer through a `Model.setupAnim`.
  - **Arsenal relics** (`ArsenalRelics`): a random pool weapon with a glint, found in chests.
    - Chance: 8% in dungeons, mineshafts, temples, outposts, shipwrecks, ruined portals and mansions; 25% in
      bastions, end cities, ancient cities and stronghold corridors.
    - An Arsenal user who carries one absorbs it into the pocket dimension (up to 5). The first stored relic leads
      every Reach.
    - Dropping, Return, expiry or death sends it home with its wear. A worn-out relic is gone; for an owner who's
      offline, it waits on the Overworld.
    - Test with `/powerdebug arsenal relic`.
  - **Gravity:** Lift takes Jaymo's Kitchen corpses (`NoGravity` while held; Kitchen 0.4.14 respects it).
  - **Kitchen 0.4.14:** explosions destroy corpses, and lava, fire and lit campfires ignite them.
  - **H menu:** scrolls with the mouse wheel when it's taller than the screen.
  - **Distant Horizons:** tuned via `dhTuning` in build.gradle, for the pack config, the server and the profile.
- **0.4.4:**
  - **Living Armor:**
    - You become 1.25× bigger (SCALE attribute, so you no longer fit 1×2 gaps).
    - The suit is drawn on an inflated player model (CubeDeformation 0.7, wide/slim) whenever the armour is up.
    - The tendrils are bigger: 8 tendrils of 7 segments, 3px thick, 4.5px per segment.
    - Tendrils no longer react to your swings. Every 2.5 s they pick the nearest Enemy within 5.5 blocks and strike
      it (synced `web_symbiote_strike`). The strike rears back, lashes out over the shoulder at the target and eases
      back over 40 ticks, hitting for 6 at tick 18.
  - **Rampage:**
    - After 60 s of Living Armor, each takeover check has a chance of a rampage: +12%/min, capped at 60%, hungry or
      not.
    - It lasts 6-10 s and goes from target to target, players included (not creative).
    - Every 3rd strike on a player is a bite: +8 damage, heals you and feeds the symbiote. Mobs at 10 HP or less are
      devoured.
  - **Consume:** eats the Jaymo's Kitchen corpse you're carrying, whatever its size; heal and feed scale with its
    size. Powers compiles against the Kitchen jar (`compileOnly`); `KitchenCompat` only touches it when it's loaded.
  - **Arsenal:**
    - Relics go into the Pocket Stash (claimed: `arsenal_relic=false`). When nothing is out, Reach brings the first
      relic as the first of the three summons; a pinned weapon now works the same way instead of coming with a
      second weapon.
    - Barrage rifts open in turn, 10 ticks apart, and each weapon slides out of its rift over 9 ticks before firing.
  - **Wind:** once mastered, flight only tumbles when you look 150°+ off your path at speed.
- **0.4.5:**
  - **Distant Horizons removed:** from this pack (manifest, server, profile) and from the Kitchen setup. The jars were
    moved into `removed-mods` in `Desktop\Minecraft Server`, `Serverfiles` and the Kitchen CurseForge profile. The
    Powers profile's copy moves out on the next build with the game closed (`removedFromProfile`).
  - **Suit:**
    - The plain suit is drawn on a model inflated by 0.12, just over the skin; drawn on the skin itself it z-fought
      and seemed invisible.
    - The cape is hidden under the suit or Living Armor.
    - Symbiote lines (suit on) are 2.6× thicker and drip black goo and obsidian tears.
  - **Tendrils (suit on), `WebTendrils`:**
    - R and G are the right and left tendrils. Held on a creature, player, item or corpse within 9 blocks, they grab
      it and hold it in front of you, weightless (gravity modifier or NoGravity). Releasing throws it at 2.4 b/t; its
      first knock hurts for max(4, speed×6).
    - Aimed at nothing grabbable, they swing at 0.3× a web's range (`WebSymbiote.swingScale`).
    - Cords are drawn as a bundle of three thick black lines (synced `web_tendril_grips`).
    - The radial Tendril Grab snatches from 20 blocks and hurls by itself after 2.5 s.
  - **Consume:** a 40-tick meal. A head layer opens jaws that chomp 4 times; the food is held still, and a carried
    corpse jolts hard every 3 ticks (Kitchen's `jolt`), with bites, ink and blood dust. Then it's gone, and you heal
    and the symbiote feeds.
  - **Tether and hitches:** Yank holds at 3 and snaps at 4.5. Hitches are 1-3 long (1-2 when tied to a ceiling) and
    snap 1.5 past their length; attacking still breaks them.
- **0.4.6:**
  - **Badge names:** `Power.statusSuffix(player)` comes first. Web shows Symbiote/Venom while bonded. Otherwise the fate
    names apply: Wind is Stormbound/Tempest/Thunderhead, Arsenal Void Arsenal/Armory/Void Armory, Gravity
    Collapse/Event Horizon/Collapsing Horizon, Chrono Timeless.
  - **Web:**
    - **Floatier:** gravity is -20% for web users (`web_float`). Off the web, near an arc's apex (|vy| < 0.12),
      75% of gravity is handed back for up to 8 ticks.
    - **Weblines:** planks carry `height` 1-16, topped at the line's height in that block, so inclines climb in
      ≤0.6 steps without jumping.
    - **Symbiote cords:** 3 twisted strands drawn as 6-sided tubes (debugQuads, shaded, purple sheen) that taper.
      Every line's anchor is an 8-spoke cobweb with two rings.
    - **Charged jump (suit on, instead of the wings):** hold Jump to coil, up to 30 ticks; the plain jump is held
      back by `WebJumpMixin`. Releasing on the ground leaps 0.45-1.65 up and up to 1.1 along the look. Held through a
      fall, the charge keeps and fall damage is waived (`ChargeHold`).
    - **Tendril aim:** grab aim boxes are inflated by 0.7, and the crosshair turns red on a grabbable target.
    - **Lines:**
      - Hitches are multi-line (`HITCH` is a list of `Line(anchor, dark)`). One line is out per slinger, five once
        mastered; the oldest is dropped.
      - More than 2 lines on one creature ensnares it like Web Shot.
      - Mastered B has no cooldown.
      - Lines strung with the suit on are black ropes with dark particles.
    - **Instinct (bonded):** every 10 ticks it checks for danger: fire, a long fall, an attacker, or ≤8 HP.
      - With the armour off, 45% it raises Living Armor by itself.
      - With it on: it catches a fall within 4 blocks of the ground, smothers flames and shoves you clear of fire,
        shoves you from an attacker or snatches and flings it (50/50), and sometimes catapults you while
        sprinting.
    - **Consume:** also restores hunger (0.75 food per point healed).
  - **Gravity:**
    - The choke holds at 0.75 + half the target's width.
    - The slam rises for 10 − abilities ticks (min 4) at 0.75, then drives down at 2.2 + 0.7 per ability.
    - Weightless weighs everything within 6 blocks: gravity ×2.5, jump −35%, Slowness I, refreshed every 5 ticks.
  - **Kitchen 0.4.15:** an empty hand bags any small corpse, sneak + empty hand carries any corpse, and bagged
    corpses keep their kind.
- **0.8.21, Minecraft Comes Alive joins the pack:** human villagers who talk, take gifts, have moods and
  personalities, marry you and raise your children, and village ranks up to monarch. Its newest release is for 26.2,
  so the pack ships a jar built from its authors' 26.3 branch (`libs/mca-fabric-7.8.0-dev.3badc42+26.3.jar`, an
  override like Kitchen; see `mcaJar` in `build.gradle`). Swap it for the CurseForge file (project 535291) once a
  Fabric 26.3 one is out. Its Chat AI and TTS are off unless someone turns them on in its config. Jaymo's Kingdoms
  0.4.2 ties into it (hearts count as reputation, faction rank counts as village rank;
  see `kingdoms/MODLOG.md`).
- **0.8.23, Tempest Kick looks like a real kick:** a roundhouse (the body pivots so the kicking hip faces the
  target, leans hard away, and the leg snaps straight out to the side at head height, holds, pulls back and drops)
  or, one kick in three, a spinning back kick (dwit chagi: the body spins its back to the target, pitches forward,
  drives the leg straight out behind, looks over the shoulder, and spins on round to face it again). The fists stay
  up in a guard by the face instead of being flung out. Same timing, crescent and damage (see `docs/wind.md`). In the
  cold the kick winds up only 1 s longer (was 3 s), with white wind swirling round you and a blowing wind sound.
- **0.8.25, Gravity's weight:** Lift reaches 60 blocks at full mastery, catches players more easily and marks the
  crosshair when something is in reach. The Gravity Well bursts out as a 16-block bubble that shoves lumps of the
  ground up out of the earth (they lie 8 s, Lift can take them, then crumble). Players and humanoid mobs under Crush
  or a Well fight the weight, drop to their knees in one of 4 poses, and get up when it lifts. The earthboard is
  anyone's with Weightless (held to ride until mastered, toggled after), tears up a creeper-sized crater, climbs 19
  blocks with a struggle at the top, and drops whole for 8 s when you step off (hold V on it to ride it again). The
  meteor bursts into debris (iron ore, obsidian, blackstone...) with less fire; the symbiote rarely rides in a lump.
  `core/BlockCluster` gained lingering debris (`linger`, `REST`, `BOARD`). See `docs/gravity.md`.
- **0.8.27, Wind balance:** a Wind mode (Z) eats a full hunger bar in about 2 minutes (`WindModes.MODE_EXHAUSTION`
  0.01 → 0.04 a tick; the Thunderhead's 0.025 → 0.05), and Cyclone Lance (C) hits for 3 hearts, up to 6 in a
  thunderstorm, instead of 7 to 9 (`WindTempest.LANCE_DAMAGE` 14 → 6, `LANCE_DAMAGE_MAX` 12).
- **0.8.40, Gravity's Well around you, and the shoulder view:** G is no longer aimed: the bubble bursts out around
  you and stays on you 4 s, weighing down and kneeling everything that touches it (no pull). Tapping B forces
  everything around you to its knees, and inside your Well also launches you about 14 blocks straight up; holding
  B is the Crush where you aim, or Collapsing Horizon's ring raining on the target (the Cave-in is gone). The meteor tears through roofs and anything else
  it falls through before it explodes. With Web or Gravity, F5 flips between first person and an over-the-shoulder
  view with the crosshair (no front view). See `docs/gravity.md`.
- **0.8.49, /power affinity works again:** `/power affinity <player> <power>` now makes that power the one that
  awakens 95% of the time (`Affinity.CHANCE`); the other 5% fate picks any power. Before this the command saved the
  affinity but awakenings ignored it, because the choice screen was switched off. The screen stays off.
- **0.8.51, Web wall run:** sprint and leap alongside a wall (forward held) and you run along it for a moment, rising
  a little then dropping slowly; Jump kicks you off it, up and away, still carrying the run's speed. The kick climbs with
  mastery (0.48 to 0.72 up, a vanilla jump is 0.42) and jumps to 0.92 once ascended; runs last longer too (22 to 50
  ticks). Runs chain wall to wall, but not back onto the same wall face until you land. The run itself doesn't count
  toward fall damage. Everyone sees the Sands of Time run (`WebWallRunPose`): feet on the wall, body slanted out
  and pitched into the run, long pumping strides, the wall-side hand skimming the wall, the outer arm swinging.
  Numbers at the top of `power/web/WebWallRun`; the client side is `WebWallRunClient`.
- **0.8.52, carrying blocks:** anyone can sneak + right-click a block a player placed, with empty hands, to lift it
  out of the world and carry it in both arms, like a body: right-click sets it down ahead of you, sneak + right-click
  throws it, and it lands as a falling block (or breaks into its item if it can't stand where it lands, spilling
  whatever was in it). Chests, furnaces and the like come along with their contents. It slows you a little (none
  for Gravity users). Natural terrain never counts: placed blocks are remembered per chunk (`CarryBlock.PLACED`,
  from `CarryBlockItemMixin`). Not liftable: two-part blocks (doors, beds, tall flowers, double chests), liquids,
  unbreakable blocks, grass/fire, or a spot you couldn't break. `core/CarryBlock`; the block rides as a tagged
  display, and a stray one (crash mid-carry) falls back into the world on load.
- **0.8.54, heavy iron golem corpses (Kitchen 0.4.27):** an iron golem's corpse is solid iron. Only Gravity's Lift
  can move it (drawn in, it lands in your arms as usual); nobody can carry or pocket it by hand, the symbiote's
  tendrils can't grab it (so it never hunts iron golems), and nothing butchers it: an axe gets "nothing to
  butcher" and the thrash can't tear it apart. It doesn't burn: searched, it drops its loot, falls apart (shuddering, slumping and sinking as iron chips crack off) and disappears. Kitchen's new
  `jaymoskitchen:heavy_corpses` tag lists the heavy ones; Powers checks it in `KitchenCompat.isHeavy`.
- **0.8.56, Distant Horizons back, and Iris shaders:** Distant Horizons 3.3.4 for 26.3 (CurseForge 508933 / 9005667,
  Modrinth TGgEbP9A) is in the pack again, from its Modrinth maven, so the CurseForge import, the server and the
  profile all get it. Far terrain is drawn as simplified chunks well past the render distance; the server builds them
  and sends them to players. It's no longer in `removedFromProfile`. Iris Shaders 1.11.7 (CurseForge 455508 /
  9015108, Modrinth vTN4NRGW; it needs the pack's Sodium) lets everyone load shader packs (Options → Video Settings →
  Shader Packs); it's client-only, so the server ignores it. Distant Horizons draws its far terrain through Iris
  shaders that support it. Both stay out of the dev game (`dev: false`).
- **0.8.58, medieval MCA villagers:** the pack now brings the resource pack **MCA - Default Medieval** (by DE4THR4SH,
  CurseForge 667175, file 8868890): MCA's humans get vanilla-style medieval clothes for every profession, gender and
  age, with Steve/Alex-like faces and hair. It's in the CurseForge manifest (the import downloads it into
  `resourcepacks/`; the file is all rights reserved, so it's never copied into the pack), Build Modpack downloads it
  into your own profile, and on first launch `client/MedievalVillagers` switches it on once (delete
  `config/jaymospowers-medieval-villagers-v1` to do that again). Its newest file says 1.21.1, so the resource pack
  screen marks it "made for an older version"; it only retextures MCA's skins, and 1144 of our MCA jar's 1145 skin
  files sit at the same paths. Not on the server (textures are client-only).
- **0.8.59, carried containers are set down, not thrown:** a carried chest, furnace or anything else with a block
  entity is only ever set down: sneak + right-click puts it down like a plain right-click (`CarryBlock.holdsContents`).
  Plain blocks can still be thrown.
- **0.8.61, Jaymo's Armory (weapons after Age of Weapons):** 113 new items in their own creative tab, rebuilt for 26.3
  after Age of Weapons - Reforged (by XxRexRaptorxX, MIT; Forge/NeoForge only, newest 1.20.4). Its textures and hand
  poses are reused under its license (`textures/item/armory/LICENSE-AgeOfWeapons.txt`); nothing past the age of sail.
  `armory/Armory` registers them; `tools/armory_assets.py` writes their models, recipes, Better Combat attributes and
  tags from the unpacked AoW jar.
  - Wooden to netherite (netherite by smithing, like vanilla): dagger, hatchet, skewer, round shield, longsword,
    glaive, lance, war axe, war scythe, flanged mace, broadsword, claymore, cleaver, ninjato, saber, machete.
  - One-offs: primitive spear, primitive knife, bone knife, hand axe, wooden and stone cudgels, throwing stick, wooden
    and iron quarterstaffs, caveman hide armor, recurve bow, harpoon, kunai, throwing stars.
  - Daggers, hatchets, the primitive spear, the throwing stick and the harpoon are drawn back and thrown like a trident
    (`ThrowingWeaponItem`, `ThrownWeapon`) and lie where they land for the thrower; kunai and throwing stars (stacks of
    16) fly at a right-click. The harpoon swims through water and drags its catch back.
  - The lance, couched on a moving mount, adds up to 8 damage at a gallop and throws the target back. The flanged
    mace knocks a raised shield down for 4 s (war axes and hatchets as any axe). The machete cuts leaves, vines and
    plants fast. The round shield blocks like a shield and comes up quicker. The recurve bow's arrows fly 30% faster.
- **0.8.64, goblin villages and loot goblins:** ships Jaymo's Kingdoms 0.5.31 (see `kingdoms/MODLOG.md`): goblin
  villages behind a palisade fort, the goblin mask to walk in among them, and loot goblins ported from Loot Goblins
  (LGPL-3.0).
- **0.8.65, riders sit in the saddle:** ships Jaymo's Kingdoms 0.5.32 (see `kingdoms/MODLOG.md`): mounted guards,
  archers, caravan riders and coachmen no longer float above their horses.
- **0.8.66, the symbiote's roar and wall tethers:**
  - Near death, before it takes over and runs, the symbiote roars (2 s): you're held hunched and shuddering while
    the armour pieces itself together over you (the suit, then the tendrils flailing, then the big body; whatever you
    already wore stays), then it throws its head back, maw wide open, arms flung out, and roars: everything within
    6 blocks takes 4 hearts and is hurled away, every screen nearby shakes. Resistance III while it roars. The roar
    replaces the opening Spike Burst. (`WebSymbiote.startRoar`/`tickRoar`/`roarBlast`, pose `WebPose.ROAR`.)
  - In the big body (full bond), the radial menu's Spike Burst is the same roar ("Roar" on the menu).
  - Tendril Grab at a block: the tendrils lift you a block off the wall over half a second, and each of the six
    lower ones bites into its own spot around you, cracking the block there (one a tick). You hang on them like
    elastic: the movement keys lean you up to 1.4 blocks away, the cracks deepen with the strain, and they haul you
    back when you let go. Jump kicks off, the grab again lets go, and so does any other radial ability. The two
    shoulder tendrils stay free: R grabs and Shift+G tears a chunk while you hang there, even without Living Armor.
- **0.8.69, Overhauled Overworld:** adds William Wythers' Overhauled Overworld 3.0.1 and Cristel Lib 3.1.13 to the
  pack. Every vanilla biome is redecorated (new trees, undergrowth, boulders, sub-biome patches) but keeps its id, and
  the terrain and biome map are untouched, so Epic villages (kingdom capitals), Grim and ATi ruins, Moog's roads and
  Kingdoms' biome checks all work as before. Cristel Lib is no longer swept out of the profile with Towns and Towers.
- **0.8.70, Jaymo's Equestrian (riding):** ships Jaymo's Kingdoms 0.5.34 (see `kingdoms/MODLOG.md`): six horse
  breeds with hidden stats foals inherit, five gaits on the scroll wheel, a stamina bar, courage and spooking, new coats;
  war elephants and woolly mammoths in Mo' Creatures' look (GPL-3.0).
- **0.8.72, Mobs work themselves loose:** ships Jaymo's Kingdoms 0.5.36 (see `kingdoms/MODLOG.md`): any mob that stops
  getting closer to where it's walking hops toward its next step, then takes a fresh path, then gives the walk up;
  our own folk turn up where they were going in a puff instead.
- **0.8.73, Jaymo's Tinkering, stage 1 (tools from parts, after Tinkers' Construct):** Tinkers' Construct has no
  Fabric or 26.3 build (Forge/NeoForge, newest 1.20.1), so this is our own version in `tinkering/`, with Tinkers'
  stats and its textures and GUI art reused under its MIT license (`textures/item/tinkering/license_tinkers_construct.txt`).
  `tools/tinkering_assets.py` writes the sprites, models, recipes, tags, loot tables and lang from the unpacked
  TConstruct 1.20.1 jar. Its own creative tab.
  - **Pattern** (2 planks + 2 sticks, 6 of them), **Part Builder** (2 patterns over 2 logs), **Tool Station** (pattern +
    crafting table). Like the pack's crafting tables (Jaymo's Kitchen), what you leave on a table stays there
    (`StationEntity`), drawn lying on its top (`StationRenderer`); one player works a table at a time ("Someone else is
    using this table."), and a broken table drops what's on it unless someone has it open (then closing hands it back).
  - **Part Builder:** a pattern and a material in, pick one of 10 parts on Tinkers' screen, take it out (pattern used
    up). Costs in material units: pick head, small axe head, adze head, small blade 2; hammer head, broad blade 8;
    large plate 4; tool handle, binding 1; tough handle 3. A plank or ingot is 1, a log 4, a bone block 3, a block
    of iron or copper 9; the rest of the last item is lost. The side panel shows the part's stats and trait.
  - **Materials** (`TinkerMaterial`, Tinkers' numbers): wood (crimson and warped look different), stone (granite,
    diorite, blackstone looks), flint, bone, copper, iron for any part; leather, string, vine for bindings only.
    Heads give durability, mining speed, tier and attack; handles add or take a percentage; bindings only their trait.
    Iron and copper go straight into the Part Builder until the smeltery comes (stage 2).
  - **Tool Station:** parts in any slots make the tool: pickaxe, hand axe (strips logs, knocks shields down), mattock
    (axe + shovel, tills), sword, sledge hammer (3x3, sneak for one block; Smite II), cleaver (Severing II). A tool
    plus its head's material repairs it (a unit repairs half the tool over its main part's cost, so a pick head's
    material 25% a unit, Cultivated +50%); a tool plus one part it uses swaps that part in. Big tools are big in hand.
  - **Traits**, one per part, stacking: Cultivated (wood), Stonebound (stone: faster and softer as it wears),
    Jagged (flint: harder as it wears), Piercing (bone: +1 armour-piercing damage), Dwarven (copper: faster below
    y 64), Magnetic (iron: pulls drops in for 2 s), Tanned (leather: never more than 1 wear at once), Stringy
    (15% no wear), Solar Powered (vine: 25% no wear in sunlight).
  - **Tools never vanish:** at 0 durability they're broken (no mining rules, no attack, broken-head look) until
    repaired (`mixin/TinkerToolWearMixin`). Enchanting works as for vanilla tools for now; modifiers arrive in stage 2.
  - Next: stage 2, the smeltery (melting, casting, alloys, ore doubling) and modifiers with tool levels; stage 3, the
    pack tie-ins (Kingdoms smiths, Powers weapons).
- **0.8.74, Sorcery:** ships Jaymo's Kingdoms 0.5.37 (see `kingdoms/MODLOG.md`): our own take on Iron's Spells. Barbarian
  camps keep shamans and necromancers (the dead near a necromancer rise as his thralls), kingdoms keep court wizards (a
  tyrant a court necromancer), and slain casters drop one-use spell scrolls. About 40% of kingdoms don't believe in
  magic: they keep no court mage and prosecute anyone reading a scroll within their walls.
- **0.8.75, Jaymo's Tinkering, stage 2 (smeltery and modifiers):** metals now come from a Tinkers-style smeltery
  (`tinkering/smeltery/`), and tools take modifiers and level up.
  - **Seared blocks:** grout (clay ball + sand + gravel, 2 of them) smelts into seared bricks; 4 make a block of seared
    bricks. Seared glass (bricks around glass), smeltery controller (8 bricks), drain, faucet (2), casting table and
    basin (7 each).
  - **Smeltery:** the controller sits in a wall around an open space up to 7x7 and 8 high, on a seared-brick floor,
    walled with seared bricks, glass, drains and the controller (corners don't count). One melting slot per inside
    block (up to 24), a lava-bucket fuel slot (bucket back out below), and the pool on Tinkers' GUI art: liquids stack
    bottom first, hover for amounts, click one to pour it first. Items thrown in get pulled into free slots;
    creatures inside cook (2 damage) into blood. An ingot is 90 mB, a nugget 10, a block 810.
  - **Ore doubling:** raw ore and ore blocks melt into 2 ingots (copper ore 4). Vanilla metal gear, buckets and shears
    melt back into ingots; tinker parts melt back into their cost.
  - **Alloys** form in the pool on their own: rose gold (3 copper + 1 gold), amethyst bronze (copper + amethyst), pig
    iron (iron + blood + honey), slimesteel (iron + slime).
  - **Casting:** a faucet on a drain (right-click, or a redstone pulse) pours the bottom liquid into the casting table
    or basin below. Gold poured over an ingot, nugget or part turns it into a gold cast of that shape; casts make
    ingots, nuggets or parts of any castable metal. The basin makes metal, amethyst, slime and honey blocks.
  - **Metals leave the Part Builder:** copper, iron and the alloys are cast only. New materials: seared stone (Part
    Builder, Searing: more damage to things that burn), rose gold (Enhanced: +1 modifier slot), amethyst bronze
    (Crumbling: faster on blocks that need no tool), pig iron (Tasty: eats itself when you're hungry), slimesteel
    (Overslime: slowly repairs itself).
  - **Modifiers** at the Tool Station, a tool plus the items: Haste (redstone block, max 5), Sharpness (quartz block,
    5), Luck (lapis block, 3; Fortune, or Looting on swords), Silky (cobweb + gold block), Fiery (blaze rod, 2),
    Knockback (piston, 2), Reinforced (obsidian, 3), plus Diamond (+500 durability, diamond tier), Emerald (+50%
    durability) and Netherite (needs Diamond: +20%, netherite tier, fireproof). They're written as the vanilla
    enchantments. Enchanting tinker tools is off now.
  - **Tool levels:** each hit or block mined is 1 xp; level 2 at 100 xp, doubling, up to level 10. 3 modifier slots,
    +1 per level after the first.
  - Next: stage 3, the pack tie-ins (Kingdoms smiths sell parts, Powers weapons take modifiers).
- **0.8.76, Jaymo's Tinkering, stage 3 (pack tie-ins):** smiths sell tinkering, and our own weapons take modifiers.
  - **Smiths sell parts and casts** (vanilla trade data in `data/jaymospowers/villager_trade/tinkering/`, added to the
    `minecraft:villager_trade` level tags, so kingdoms' smiths and MCA villagers sell them too): toolsmiths an iron pick
    head and an ingot cast (journeyman 2), a rose gold pick head and a pick head cast (3), an amethyst bronze hammer head
    and a pig iron tough handle (4), a slimesteel large plate (5); weaponsmiths an iron small blade (2), a rose gold
    binding and a blade cast (3), a pig iron broad blade (4), a slimesteel tough handle (5); armorers a plate cast (3);
    masons 4 seared bricks for an emerald (2). They're mixed in with the vanilla offers, so not every smith has them.
  - **Armory weapons take modifiers** at the Tool Station (weapon plus the items): Sharpness, Luck (as Looting), Fiery,
    Knockback and Reinforced; the rest say they don't go on a weapon. They level up from hits like tinker tools (3 slots,
    +1 a level) and show their level and slots in the tooltip (`Armory.isWeapon`, `Modifier.takes`).
  - The Tool Station's side panel now says why a modifier won't go on (no slots left, at its max, doesn't fit).
  - Next: Andy's call.
- **0.8.77, Skin tones by climate:** ships Jaymo's Kingdoms 0.5.38 (see `kingdoms/MODLOG.md`): new MCA villagers lean
  darker in hot, dry lands and paler in the snow. Some families are newcomers from a nearby climate or immigrants from
  a far one, look it, and sometimes talk about home when you chat with them.
- **0.8.78, Townsfolk come to you:** ships Jaymo's Kingdoms 0.5.39 (see `kingdoms/MODLOG.md`): villagers, children and
  guards walk up and start things (questions, quests, accusations, requests, bargains, gifts, news); right-click to
  answer.
- **0.8.82, Alex's Mobs:** adds Alex's Mobs (Fabric) 26.3-1.0.0 and its library Citadel (Fabric) 26.3-1.0.0, both
  LGPL-3.0 and pulled from CurseForge by id (cursemaven): about 90 animals and monsters across the overworld, Nether
  and End. It has no horses; Jaymo's Equestrian still owns those. Its elephant spawns alongside ours.
- **0.8.83, Golems keep quiet and fight fires:** ships Jaymo's Kingdoms 0.5.42 (see `kingdoms/MODLOG.md`): iron golems
  never speak, and they beat out fires in settlements.
- **0.8.84, No rainbow hair:** ships Jaymo's Kingdoms 0.5.43 (see `kingdoms/MODLOG.md`): MCA villagers no longer get
  rainbow hair or random dyed hair; they keep natural colours. MCA's Homosexual trait is off too.
- **0.8.85, Symbiote fixes (Andy's list):**
  - **Wall tethers:** your view is held facing away from the wall (80° either side; level or below on a ceiling,
    `WebWallLookMixin` on `Entity.turn`). You sway on the tethers toward where you look (0.7 blocks along the wall,
    `WebSymbioteClient.sway`) and the bites slide a little after you; the spring is softer and bouncier.
  - **First person:** with First-person Model, your own suit's head, the big body's mask and the maw aren't drawn
    (`WebSymbioteClient.ownFirstPerson`), so they don't cover your view, once the bond reaches 60. Under that the mask
    still covers it, so you're looking out through it, not quite in control (`WebSymbiote.MASK_CLEAR_BOND`).
  - **Armour under the suit:** the plain suit and the big body both engulf worn armour (an elytra still shows).
  - **Wall Crawl walks on tendrils:** four tendrils come out (even without Living Armor) and walk you along walls and
    ceilings, Doc Ock style: each plants its tip ahead, then lifts and arcs over to plant again, in diagonal pairs,
    bowed out from the surface (`WebTendrilWalk`, worked out on every client). The old climb cords now show only from
    your own eyes in plain first person.
  - **V with the symbiote:** no pellet; V only charges the black blast.
  - **Dread vignette by bond:** a line or a feeding is fainter at a strong bond, stronger at a weak one; under bond 60
    a faint vignette lingers, breathing and blinking darker more often as the bond drops. Full strength is kept for a
    weak bond's own lines (ignored, neglected, at the edge, leaving you) and every takeover (`WebSymbioteDread`).
- **0.8.86, Jaymo's Equestrian, stage 2 (bond and tack):** ships Jaymo's Kingdoms 0.5.44 (see `kingdoms/MODLOG.md`): a
  horse trusts each rider on their own account (it bucks strangers and comes to a whistle at 60), learns trail, racing,
  jumping and war to level 5 with tricks (rear, sliding stop, side-pass, war kick, kneeling), gets hungry and thirsty,
  wears down unshod hooves, and wears war and racing saddles, a bridle, a dyeable blanket and chain or plate barding,
  drawn in 3D. Keys: N whistle, J trick. Every horse, skeleton horse and zombie horse is now Mo' Creatures' horse
  model (GPL-3, credited), with our 36 coats painted onto its texture and its armour over them.
- **0.8.87, Longer tendril Crush:** the Crush combo finds a creature under your crosshair from 12 blocks, up from 6
  (`WebTendrilCombos.CRUSH_REACH`).
- **0.8.87, Escape leap after a takeover's meal:** fed on a kill of its own, the symbiote often coils a full charged
  jump during the meal or just after it and leaps you clear: 60% of the time, and always in a village or with 3 or
  more creatures or people within 12 blocks. It leaps the way they're fewest, over open ground (straight up if boxed
  in); the movement keys don't answer while it coils (`WebSymbiote.escape`, `WebNet.Escape`, `WebSymbioteClient.tickEscape`).
- **0.8.88, Jaymo's Equestrian, stage 3 (the stables at work):** ships Jaymo's Kingdoms 0.5.45 (see `kingdoms/MODLOG.md`):
  stables get a stablehand (boards your horse for 4 emeralds a week, sells horses by land and wealth, buys them back)
  and a farrier (shoes horses, sells tack, the best only in wealthy kingdoms); realms with stables field better
  cavalry; a fallen knight's destrier or a raider's steppe pony goes wild for you to break in; rifling a caravan
  horse's saddlebags is theft; and stablehands and kings give horse quests (break one in, bring a stud, ride to
  another town against the clock).
