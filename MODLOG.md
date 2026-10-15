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
- **0.8.88, orc war camps:** ships Jaymo's Kingdoms 0.5.45 (see `kingdoms/MODLOG.md`): our own take on The Orcs! (no
  26.3 build, all rights reserved): orc camps rise in rough country near kingdoms, a dark oak stockade round the
  warchief's longhouse, tents, a forge, a fighting pit and a watchtower; grunts, rangers, a shaman and a warchief boss
  with a ground slam. Their war bands raid kingdoms; killing the warchief clears the camp.
- **0.8.89, fish loot instantly:** ships Jaymo's Kitchen 0.4.29 (see `kitchen/MODLOG.md`): clicking any fish corpse
  (vanilla or Alex's Mobs, beached flying fish included) drops its loot and the body is gone at once.
- **0.8.90, Your own blocks are no crime:** ships Jaymo's Kingdoms 0.5.46: breaking a block you placed yourself in a
  settlement (a chest, bed, crafting table, crops you planted), or opening a chest you placed there, no longer makes
  its people take you for a criminal. Blocks the settlement built still count.
- **0.8.92, Jaymo's Equestrian, stage 3 (the stables at work):** ships Jaymo's Kingdoms 0.5.47 (see `kingdoms/MODLOG.md`):
  stables get a stablehand (boards your horse for 4 emeralds a week, sells horses by land and wealth, buys them back)
  and a farrier (shoes horses, sells tack, the best only in wealthy kingdoms); realms with stables field better
  cavalry; a fallen knight's destrier or a raider's steppe pony goes wild for you to break in; rifling a caravan
  horse's saddlebags is theft; and stablehands and kings give horse quests (break one in, bring a stud, ride to
  another town against the clock). Stablehands and farriers have their own screen: right-click them.
- **0.8.94, Iron golems burn corpses:** ships Jaymo's Kingdoms 0.5.48 and Jaymo's Kitchen 0.4.31: every iron golem
  (village, kingdom or home-built) picks up corpses lying near it and carries them in its arms to the nearest fire pit,
  lit campfire or lava, then drops them in. Monsters at once, other corpses after 5 minutes untouched; never players'
  or golems' corpses. Golems never light fires, and fire pits on netherrack are no longer beaten out.
- **0.8.95, crawl anywhere:** any player can press Jump and Sneak together twice quickly to drop flat (the swimming
  pose) and stays flat while Jump is held, so one-block gaps are passable. Letting go stands you up once there's room;
  under a low ceiling vanilla keeps you crawling. Holding Jump doesn't hop while crawling; not in water, flying,
  gliding or riding. Combat Roll's double-tap Sneak ignores Sneak presses made with Jump down (`core/Crawl`,
  `CrawlPlayerMixin`, `CrawlJumpMixin`, `client/CrawlTaps`).
- **0.8.96, Quests are rare:** ships Jaymo's Kingdoms 0.5.49 (see `kingdoms/MODLOG.md`): only about one villager in
  twenty ever gives quests (on about half their days), kings have work only now and then, and a good name with a
  settlement raises both a little. Giving up a quest costs reputation with the giver's settlement. New: the
  Traveller's Journal (craft a book with a map): with one in your inventory, M opens your quests, a map with your
  quests and the settlements on it, and every settlement's opinion of you.
- **0.8.97, Pickpocketing:** ships Jaymo's Kingdoms 0.5.50 (see `kingdoms/MODLOG.md`): sneak up behind a villager (MCA's
  too) or a settlement's folk with an empty hand and hold right-click: your hand reaches into their pocket for 1-3 s,
  longer in bright light, on armoured or cheerful marks, shorter in the dark or on sad ones. Stay behind them till it's
  done to lift something; if they turn round on you they cry thief, which counts as a theft seen.
- **0.8.98, Darktide's shade:** a Darktide user who dies at night rises as their own shadow (drawn dark, shedding black
  dust, 10 health, full Flow), once a night. The shade can hide through the day and is flesh again at the next
  nightfall, but sunlight burns it away: the user dies for real, every item they carried is destroyed and only Shadow
  Ash drops. A shade's second death, `/kill` and the void are final; a held totem goes first (`DarktideShade`,
  `/powerdebug darktide shade`).
- **0.8.99, Butcher villagers:** ships Jaymo's Kitchen 0.4.32 and Jaymo's Kingdoms 0.5.51: right-click a butcher
  villager with a passive animal's corpse (carried or bagged) for a screen with their price; pay in emeralds and they
  carry it out back, chop it up and walk back with the cuts, 1x to 3x what you'd get yourself. How good a butcher is
  stays hidden until townsfolk gossip about them (their own village's, or far-off masters and botchers), or, if
  you're experienced with butchering (a Survival perk), you judge it from the cuts they hand you.
- **0.8.102, JEI cheat mode in creative:** JEI's cheat mode (click an item in its list to take it) starts off every
  session and its only default switch is Ctrl + left-click on JEI's wrench button, so clicking items in creative just
  showed recipes. Now it switches on whenever you enter creative (or join in creative) and off when you leave
  (`client/JeiCheats`). The server still decides who may cheat: JEI allows creative players and ops by default
  (its `jei-server.properties`).
- **0.8.103, Skill tree:** ships Jaymo's Kingdoms 0.5.52 (see `kingdoms/MODLOG.md`): press **K** for a skill tree with
  three pillars. Stealth grows as you pick pockets and steal unseen (shorter holds, more loot, slipping away), Survival
  as you harvest, eat and live through the night (slower hunger, healing, Last Stand), Strength as you fight, mine and
  chop (heavier blows, knockback, faster mining). Each level is a perk point. A court wizard or a bottle o' enchanting
  (sneak and drink it) gives your points back. Your best pillar shows over your head (Survivor, Rogue, Warrior) and
  villagers gossip about it. Forager, the first Survival perk, also lets you judge a butcher's skill from their cuts.
- **0.8.104, Court wizards are MCA villagers:** ships Jaymo's Kingdoms 0.5.53 (see `kingdoms/MODLOG.md`): a kingdom's
  court wizard is now one of its own elders, an MCA villager who has been grown for 8 days or more, who keeps their
  name, family and hearts and puts on the cleric's robe, the wizard's hat and staff. They live as villagers until a foe
  comes near, then fight and ward as the court wizard always has, and mend hurt townsfolk when idle.
- **0.8.105, Villagers grow old and die:** ships Jaymo's Kingdoms 0.5.54 (see `kingdoms/MODLOG.md`): every MCA villager
  and every realm's folk (kings, lords, guards, archers) grows old. Elders' hair goes grey and they walk slower; near
  the end it goes white. After 24-36 days grown they die peacefully in their sleep, leaving a corpse; with MCA their
  family mourns at the grave. A king or lord who dies of old age is succeeded by an heir the next day, a court wizard
  by the next eldest, and MCA's couples have children to fill the empty beds.
- **0.8.106, Medieval MCA clothes actually show up:** the MCA - Default Medieval resource pack (0.8.58) only switched
  on if it was already in `resourcepacks/`, which it isn't when the pack's jars are copied into a profile by hand, so
  villagers kept MCA's own clothes. `client/MedievalVillagers` now downloads it from CurseForge's CDN when it's missing
  (the same file Build Modpack uses, checked by SHA-1, never shipped by us) and switches it on. Its marker is now
  `config/jaymospowers-medieval-villagers-v2`, so installs where it was missing or got dropped get it once more. It
  retextures MCA's clothes by path, so villagers already in the world change too; nothing of ours overrides it.
- **0.8.107, Alex's Mobs out:** Alex's Mobs and its library Citadel leave the pack (manifest, server, profile). With
  Iris shaders on, their models threw black slabs and streaky lines across the screen that changed as you looked up
  and down; without them the artifacts stop. Build Modpack moves both jars out of your CurseForge profile into
  `removed-mods` (matched by mod id, `removedIdsFromProfile`), and the server's mods folder drops them on its next sync.
  Kitchen's Alex's Mobs fish stay in its `fish_corpses` tag as optional entries, so nothing breaks without the mod.
  Worlds that had Alex's Mobs creatures lose them (and their items) on load.
- **0.8.108, startup crash fix (Kitchen 0.4.33):** the game crashed while loading with "no payload type has been
  registered with name jaymoskitchen:butcher_deal". Kitchen registered the butchers' deal receiver before its payload
  types; `Payloads.register()` now runs first in `JaymosKitchen.onInitialize`.
- **0.8.109, Chunky for fast gliding:** Chunky 1.5.3 (Fabric, GPL-3.0; CurseForge 433175 / 8036332, Modrinth
  4Eotm6ov) joins the pack and the server. Run `/chunky radius 5000` then `/chunky start` once in the server console
  (it works through the world in the background and resumes after a restart); afterwards, gliding or flying fast
  loads saved chunks instead of generating them on the spot. Distant Horizons stays for the view past 32 chunks.
- **0.8.110, Naturalist, wilderness encounters and monsters of legend:** adds Naturalist 2.0.6 (Starfish Studios' own 26.3 Fabric build;
  code MIT, art all rights reserved, shipped unchanged by id) and ships Jaymo's Kingdoms 0.5.56 and Jaymo's Kitchen
  0.4.34. Naturalist's wildlife roams as it means to, but its predators (bears, lions, tigers, alligators, komodo
  dragons, rhinos, scorpions) never spawn on their own: now and then one finds a player out in the wild, well away from
  any settlement, as a random encounter fitting the biome (see `kingdoms/MODLOG.md`). Its elephants and mammoths stay
  out too, since Kingdoms has its own. Kingdoms also adds its own feral wolf packs, night-time coyotes, tarantulas
  and moose, rebuilt from Better Animals Plus's models and textures (CC BY-NC-SA 4.0, credited), which only come as
  encounters too. Kitchen corpses cover the new animals.
  Kingdoms also brings monsters of legend in the spirit of Fangs 'n Claws (its own code; models and textures from
  Mo' Creatures, GPL-3, credited): werewolves that turn at night and only really feel gold or silver, green, cave and
  fire ogres (some two-headed) that smash the ground, wraiths and flame wraiths, silver skeletons, mud golems, and
  mimics hiding as loot chests out in the wild. Villagers gossip about where one was seen, and now and then give a
  quest to hunt it down or to survive a night out where it prowls. A silver sword (+4 against undead) drops rarely.
  Two rideable horse breeds join them: the bat horse, which flies (hold jump to climb), and the fiery nightmare.
- **0.8.111, silver (Kingdoms 0.5.57):** silver ore (stone and deepslate) now generates in the Overworld, in veins of
  up to 9 from the bottom of the world to y 48, most common around y -8; it needs an iron pickaxe. It drops raw silver
  (Fortune works). Smelt or blast it into silver ingots, which craft into nuggets and blocks. Silver is a Jaymo's
  Tinkering metal: the smeltery melts ore and raw silver into two ingots' worth (molten silver), and the casting table
  and basin cast it into parts, ingots, nuggets and blocks. Silver parts have Tinkers' stats (300 durability, 5.5
  speed, 2.25 attack, mines like iron) and the Smite trait (extra damage to the undead). Textures are vanilla's iron
  ones recoloured by `tools/gen_silver.py`; the part and molten textures come from Tinkers' Construct (MIT) through
  `tools/tinkering_assets.py`. In Kingdoms, the silver sword is now crafted from two silver ingots and a stick and
  mended with silver ingots, and a Tinkering tool with a silver head counts as silver against werewolves. The bat
  horse and nightmare are now exceedingly rare: about once in 50 hours a player spends where one can turn up, and
  never while another wild one is about.
- **0.8.113, crawl roll:** while crawling, double-tap Left or Right (A / D) to roll one block that way, turning over once
  along your length. A roll puts out the fire on you and any fire you roll through (`Crawl.Roll`, `Crawl.ROLLING`,
  `client/CrawlRollPose`, `CrawlAvatarRendererMixin`).
- **0.8.114, Wind hover sway, aimable Tempest Kick, R-only take-off:** the hover pose is all damped springs: a smaller
  sway that settles after one soft overshoot, hips that twist after camera turns and lean into them, a slight tip with
  the look, sag on climbs, slow random gusts, and limbs that trail the body; first person rides it by about a degree
  with a small bob (`WindHoverSway`, `CameraMixin`). The Tempest Kick flies where you look as the leg snaps (in the
  air up or down; on the ground level or up). Flight toggles on R only: Sneak + double Jump no longer takes off.
- **0.8.116, combat animations:** the quarterstaff is held by the middle and its Better Combat combo twirls it: a
  propeller twirl in front, a sweep, a full-circle whirlwind with the staff twirling at your side, a jab, and a
  helicopter twirl overhead into a strike. Fourteen other Armory weapon types get their own combos (longsword
  half-sword thrust, glaive and war scythe whirls, dagger flip-and-stab, saber lunge, machete chops and more).
  `tools/combat_animations.py` writes the animations (`assets/jaymospowers/player_animations`) and the `combo_*`
  presets each weapon's attributes file names as its parent.
- **0.8.117, hide armour pieces:** every player gets a small eye button beside each armour slot in the inventory (right
  of the slot in survival, under it on the creative inventory tab). Click it to hide that piece: it stays worn and still
  protects, it just isn't drawn, for anyone (the choice is synced to every client, saved, and kept through death). It
  covers vanilla armour, Kingdoms' armoury sets (their layer reads the same render state), carved pumpkins and mob
  heads; an elytra still spreads when the chest is hidden. Hidden pieces come off the render state last
  (`HiddenArmorAvatarRendererMixin`, after the symbiote's engulfing), in `core/HiddenArmor` + `client/HiddenArmorClient`.
- **0.8.118, Chunky out of the pack:** on the Deck a new world hung for good at "Selecting global world spawn" (no
  error; every generation thread idle, the server thread waiting on the spawn chunk). Bisected on a fresh test server
  (E:\fresh-test, the pack's mods, a new world each time): Distant Horizons alone, and the whole pack without Distant
  Horizons, both start; Distant Horizons with Chunky alone hangs every time (both hook the server's chunk loading).
  The whole pack without Chunky started a new world (Done in 40 s). Chunky had no task queued, so nothing is lost;
  Build Modpack moves it out of the CurseForge profile. Existing worlds were never affected (their spawn is generated).
- **0.8.119, assassins (Kingdoms 0.5.61):** now and then someone pays for a ruler's death, or a player's. Villagers
  whisper of it for a day or two first (warn the ruler and he parries the blow). Then a stranger walks into town, throws
  off his cloak and stabs the king with a poisoned blade: cure him with milk, honey or a golden apple, or cut the
  assassin down for his purse and the sealed contract naming who paid. Rogues, players a realm hates and famous ones
  can be marked too, at night. `/kingdom assassin [realm]` sends one at once (ops).
- **0.8.120, hermit wizards on floating islands (Kingdoms 0.5.62):** the villager living on Moog's floating island is
  now a hermit wizard with a reason to be there: a stranded scholar, a mage hiding from the courts, the warden whose
  spell keeps the island up, or an exiled court wizard. They tell you when you first come near each day, give you a
  spell scroll for the climb, talk more about it when you chat with them, and can reset your skill tree like a court
  wizard. Kingdoms leave them alone.
- **0.8.121, kings' guards with their king's temper (Kingdoms 0.5.63):** a king's guards now treat players by his
  temper. A paranoid king's guards shove away anyone who hangs about him, then dog them, then walk them out of the
  city for 5 minutes. A tyrant wants you to kneel (sneak): stand tall and his guards slap and fine you, then throw you
  out. A warlord's guards won't have a drawn weapon near him, but salute the well armoured. A noble king greets you
  with bread if you're in his good books, and his guards only ask politely for room. A reveler hands out treats, and
  his guards toss pests away with a laugh. Nobody fights you unless you actually attack.
- **0.8.123, slower awakenings and callings:** a power now awakens after 3–5 Minecraft days of survival play
  (72000–120000 ticks; old saved countdowns get 2 days added once). Each power has a calling that picks it and cuts
  the wait (`core/Callings`): Wind, kill a Breeze (at once); Gravity, land a 40+ block fall (a day off); Chrono, sleep
  the night holding a clock (a day off); Web, survive a cave spider bite (a day off, once a day); Arsenal, kill with 5
  kinds of weapon (at once); Resonance, ring a village bell near midnight (a day off) or survive a Warden's sonic
  boom (at once); Darktide, stay at sea from dusk to dawn (at dawn).
- **0.8.124, no punching trees, no vanilla tools Tinkering makes (Kingdoms 0.5.65):** Andy's call: if Jaymo's
  Tinkering can make it, the vanilla recipe goes. Its materials cover wood, stone, copper and iron, and its tools the
  pickaxe, axe (hand axe), shovel and hoe (the mattock digs and tills) and sword, so the vanilla wooden, stone, copper
  and iron pickaxe, axe, shovel, hoe and sword recipes are off (Fabric `fabric:false` load conditions over vanilla's
  files, with their recipe-book advancements; written by `tools/tinkering_assets.py`). Then Andy: golden and diamond
  tools and armour go too (pickaxe, axe, shovel, hoe, sword, helmet, chestplate, leggings, boots), so netherite gear is
  loot-only (its smithing needs diamond gear). And no diamond weapon is craftable: vanilla's diamond spear, Armory's 15
  diamond weapons and round shield (`tools/armory_assets.py` skips them), and Balkon's WeaponMod's diamond knife,
  spear, halberd, battleaxe, warhammer, katana, flail, boomerang and musket bayonet (its data pack can load after ours,
  so `tinkering/Uncraftable` drops those recipe ids as the recipe map is built, `UncraftableRecipeMixin`). Their
  netherite versions are loot-only the same way. Tinkering's Diamond modifier stays. Other spears, iron/copper/leather armour, smelting and ingot/block crafting
  stay. Sora's Dagger no longer comes from a gold block and a diamond block: the iron dagger is gilded on a casting
  table under a block's worth (810 mB) of molten gold, and the gilded dagger gets diamonds set along its edge at a Tool
  Station with a breeze rod and 2 diamonds (`ToolStationMenu.embed`, checked before the weapon recipes). New diamond
  texture, a steel blade with a diamond edge (`tools/gen_soras_dagger_diamond.py`). Wood needs an axe (Kingdoms
  `Trees.needsAxe`): by hand a log is slow, drops nothing and won't chop, and a hint says to knap a hand axe (3 flint
  and a stick). Leaves broken by hand drop a stick 12% of the time, so the first stick doesn't depend on luck alone.

- **0.9.0, player classes (Kingdoms 0.6.0):** the first time you join you pick a class, for keeps: Thief, Warlord,
  Mage, Archer, Survivalist, Brute, Marksman, Sorcerer or Rider, each good with its own gear (found by item tags, so
  Armory, Balkon's and Tinkering weapons all count) and paying its own price in hearts or speed. No class starts with
  gear: a Mage learns the wizard's staff recipe from a court wizard or finds one. A Writ of Rebirth (rare loot, or a
  court wizard's once a week) lets you choose again. Heavy armour now weighs a little on everyone. The Mage casts
  through the staff, the Sorcerer imbues a blade (J), the Marksman's guns bite harder and reload quicker. Plan:
  `docs/Classes-Plan.md`. The Armory's cleavers are redrawn short and its quarterstaffs long, the stick running the
  whole sprite (`tools/gen_cleaver_staff.py`, run after `armory_assets.py`); the Tinkering cleaver takes the same
  short shape in every material (`tools/gen_tinker_cleaver.py`).

- **0.9.2, beg their pardon (Kingdoms 0.6.2):** when someone runs up and you miss what they said, right-click them
  within 45 seconds to beg their pardon (they say it again, and a question you walked off on is put to you again) or ask
  them to tell you more. The answer screen has "Could you tell me more?" too.
  Guards halt strangers far less often, and have four new encounters shaped by their king's temper: a pack search, a
  toll, a bout for a wager and a loyalty test, where a wrong answer can cost emeralds, good name, or get you arrested.

- **0.9.3, no floating buildings (Kingdoms 0.6.3):** structures from any mod no longer hover over the ground: a gap of
  up to 6 blocks under a building is filled with the ground below when the world generates (newly explored land only).

- **0.9.6, Distant Horizons out:** Distant Horizons left the pack (manifest, server, Deck mods and dev game). Build
  Modpack moves its jar out of the CurseForge profile into removed-mods. Its leftovers are harmless and can be deleted
  by hand: `config/DistantHorizons.toml`, the `Distant_Horizons_server_data` folder in the game folder, and
  `DistantHorizons.sqlite` in each world's `data/` folder on the server.

- **0.9.7, Xenoform, the Xeno-Band (all three stages; Kingdoms 0.6.4):** a new power from a falling star. On clear nights a glowing rock
  sometimes comes down near a player (like a Gravity meteor; at most one a day per world) and leaves a Fallen Star in
  its crater. Mine it (iron pickaxe): 15% a Xeno-Band, 7% the symbiote's goo, else raw iron, amethyst and XP. Put the
  band on and it never comes off: tap Z to turn its dial, hold Z to slam it and become the dialled alien for a random
  1–5 minutes (always 5 once mastered), with its body (size, health, fists, speed) and four moves on R G V B; fists only
  while changed. The alien's health is a second life bar: at zero you're knocked back into yourself at the health you
  had, and the band recharges. Hold X at a creature for 3 s to scan it and teach the band its alien. Three to start
  (one at random): Bulwark (iron golem: fist combo, ground clap, grab and throw, leap slam), Pyre (blaze: fire bolt,
  flame jet, fire nova, magma trail), Blur (horse: dash, wall run, whirlwind, chain strike). `docs/xenoform.md`.
  Five more to scan: Prism (shulker: shard fan, amethyst wall, reflect stance that sends arrows back, spike burst),
  Wisp (phantom: phase through walls, shroud, wail that scatters mobs, glide), Gremlin (silverfish: spring, sabotage
  levers/doors/redstone at range, bite that steals food, climb), Knell (warden: sonic boom, sense, shriek, burrow),
  Wyrm (ender dragon: dragon's breath cloud, held wings, gust, rift blink). Every knockout corrupts the band (25 of
  100): once corrupted, slams land on the wrong alien a quarter of the time and the band sometimes changes you on
  its own. Stage 3: the power ascends once all eight are scanned and it's mastered; then C as an alien is its
  Ultimate for the rest of the timer: Titan (Bulwark), Sunheart (Pyre), Afterimage (Blur), Geode (Prism), Revenant
  (Wisp, possess a mob), Swarm (Gremlin), the Tolling (Knell), Elder Wyrm. Each alien carries its own flaw while the
  band is corrupted (Blur's dash won't stop, Prism's stance leaks arrows, Knell's shriek wakes shriekers...). Jaymo's
  Kingdoms 0.6.4: townsfolk flee an alien and guards hunt it, unless that realm saw you change (`docs/xenoform.md`). A ninth alien, Nadir, the void given shape (a dark silhouette, a face of nothing, the xeno insignia
  on its chest): no creature carries it. Ascended and corrupted, two voices speak from the band, Hush and Clamour,
  in rounds that never end: conflicting questions that change each round, then a deed each (changes without a
  blow or ended by hand; lives as an alien or knockouts taken); satisfy both and Nadir may be slammed once, and when
  that change ends the next round begins. Its Beacon (R) picks any player on the server, lights them through the world, and a hold
  phases you to them across dimensions; its other moves wait on Andy.

- **0.9.8, rigid symbiote tendrils, two-web whip, tendril barrage, inhabited mobs:** Wall Crawl's walking tendrils are
  stiff jointed limbs (Doc Ock style) and the rest hold stiff while you move. The Web Whip needs both webs on its
  catch; in midair it's always the lasso and you hover for it; the seesaw only on living creatures. One creature held
  and left click held: every other tendril stabs it, harder the longer you hold. The behemoth's tendrils all thrash
  and lash at anything near. Shroud 5 s; the suit no longer lingers after death; the loose blob walks on rigid legs
  and inhabits nearby mobs and villagers (peaceful ones spat out, hostile ones grow strong, lash and waste away),
  settling into goo after 5 minutes alone. The big body grows in agony into a more feral stance and has a held-Jump
  overhead slam. Shift+Space dives at once on the wings. Web block catch pries 1.5 s; tendril pull 0.5 s and breaks
  spawners; takeover rips tear small craters. Fast landings roll, slow ones ease into the superhero landing. The symbiote talks more, remarks on your
  surroundings, and now and then walks you somewhere it wants to see. `docs/web.md`.
- **0.9.9, sword, axe and Sora's Dagger combos:** vanilla swords (wooden to netherite, copper too) now cut, twirl the
  blade like a wheel at your side and cut back, thrust, then spin a full circle with the blade out; vanilla axes cut
  twice, then flip the axe end over end overhead and chop down. Sora's Daggers use the Armory dagger combo (cut, flip
  and stab, backhand). New animations `sword_twirl_slash`, `sword_whirl`, `axe_flip_chop`; the vanilla files live in
  `data/minecraft/weapon_attributes`, all written by `tools/combat_animations.py`.
- **0.9.10, Combat Arts (Kingdoms 0.6.5):** the Strength skill tree gets a second page: combo chains with Momentum and
  Cleave, weapon finishers (Earthsplitter, Rend, Executioner), Charge and War Cry on Y, and Bladestorm. Strength now
  levels to 25. See `docs/Skills-Plan.md`.
- **0.9.13, Tool Station part outlines:** Andy: the empty slots show faint outlines of which part goes where, in
  Tinkers' layout (head top right, handle bottom left, binding or second head in the middle, extra top left). An
  empty station shows each tool's parts in turn (`GHOST_CYCLE_MS` 1.5 s); with some parts in, it cycles the tools
  those parts could make and outlines only what's missing. The tool shown is underlined in the recipe list. Nothing
  shows while a tool or a modifier is in. `ToolStationScreen.ghosts`.
- **0.9.14, predators eat their kills (Kitchen 0.4.35):** wolves, foxes, bears, zombies and the other scavengers
  (Kingdoms' and Naturalist's predators too) tear into bodies with a brutal worrying bite, the meat spills on the ground
  and they eat it from there. Players' kills are left for the player. See Kitchen's MODLOG.

- **0.10.2, sword stones spaced, Web sense charges and counters:** no two swords in stone generate within 3000 blocks
  in a dimension (`SwordStone.SPACING`, seed-derived cells). Spider-Sense dodges no longer need ascension: charges grow
  with abilities unlocked (1, 1, 2, 3, 4; 5 ascended). For 1.5 s after a dodge, right click counters with one of 9 web
  moves, or 9 tendril/maw moves in the symbiote suit (`WebCounter`). See docs/web.md and docs/arsenal.md.
- **0.10.1, Riptide Dash spends roll charges:** every Riptide Dash, and every flurry, spends one Combat Roll charge
  (none left: no dash), and a flurry that lands gives its charge back. The miss and flurry cooldowns stay as they were.
  Charges live in Combat Roll's client `RollManager`: `DarktideRolls` reports the count (`DarktideNet.Rolls`) and the
  server spends and refunds through `DarktideNet.RollCharge` (`CombatRollCompat.hasRoll/spendRoll/refundRoll`).
  Without Combat Roll, dashes cost no charge.

- **0.9.27, Darktide and Wind fixes, dismemberment (Kitchen 0.4.37):** a Darktide shade that dies (burned by the sun or killed again)
  leaves no body; risen shadows never target their master or each other (`DarktideRisenTargetMixin`, brain targets
  cleared each tick), though their arrows and explosions still hurt you; up to 15 shadows stored. Riptide Dash has no
  cooldown when it lands, 5 s when it misses, and a flurry 1 minute. Every
  Wind ability blows out fire it sweeps (`WindDouse`: fire blocks and burning things, the caster too; a Stormbound
  bolt still lights what it strikes). A player carried in Wind flight can slip from the wind's arms, each second at a
  chance set by mastery (3% with one ability, never once all are mastered, `WindFumble.CARRY_DROP_CHANCE_PER_SECOND`).
  Then dismemberment and Darktide blades: twin daggers rise with the hands in the R overhead hold (the pose is also put on
  as the body is submitted, where the held-item layers read the arms); the charged punch hits harder (2-10) and wider
  (0.9 hit radius, `DarktideMoves.PUNCH_*`). The katana G crescent is aimed: where you look on a target (legs, body,
  head) is where it cuts, and on humanoid mobs it severs that part 20% of the time (`Dismember.KATANA_CHANCE`). Any
  other blade (swords, axes, daggers, katanas, `#jaymospowers:severing`) severs 5% of the time on humanoid mobs and on
  every player (`Dismember`, `#jaymospowers:dismemberable`). A severed head or limb flies off as a `SeveredPart` drawn
  from the body it came off, and the body is drawn without it (`Dismembered`; corpses too, through Kitchen's
  `jaymoskitchen.missing.*` tags). Beheaded: a player dies and watches from their fallen head until they respawn;
  zombies and skeletons (`#jaymospowers:headless_undead`) fight on, a headless skeleton's arrows going wild; other mobs
  either drop dead or stagger about blind before falling (`STAGGER_CHANCE`). A lost arm drops what it held; lost legs
  slow hard. Players get all their limbs back on respawn.

- **0.9.24, townsfolk react to the strange (Kingdoms 0.6.11):** villagers and a realm's folk stare up at a meteor or a
  falling star and cry out, and run from where it lands; they stare at a power used in front of them and say what they
  saw (each power has its own lines; children want it again); they run from the symbiote's blob and anything it's got
  into, and guards hunt it (not a villager it's inhabiting, whom they only shout about); a player in the symbiote's suit
  or walking as a shade is treated like an unknown alien until the kingdom sees the change; strange moons get remarked
  on; and all of it turns up in gossip for three days. Powers marks these with entity tags (`core/Oddities`:
  `jaymospowers_odd:meteor|star|symbiote|shade|power/<id>`), which Kingdoms reads (`Oddities`).

- **0.9.22, the aliens' detail pass, the band on the wrist, the slam, the ending glow:** every alien's skin redrawn
  with more in it (plate seams and rivets, magma cracks and obsidian, stripes and hooves, facets and fractures, folds
  and fraying cloth, warts and a toothy grin, sculk veins and bone claws, scales and belly plates, nebula dust) and
  each wears the xeno insignia, the band's green hourglass, on its chest (Pyre's on its core, Knell's on its sternum);
  Gremlin is grey now. Skins are 64x128 so the extras no longer share texels with the left arm and leg; new parts:
  Bulwark's pauldrons, Blur's ears, Prism's shoulder shards, Gremlin's tail, Knell's jaw tendrils, Wyrm's tail fin.
  The band shows on a wearer's left wrist (dial glowing); holding Z lifts that wrist across the chest and the other
  fist drops onto the band as the change fires, in first and third person, and everyone sees a player slap the band as
  they change. Through a form's last ten seconds the alien pulses red for everyone (first-person arm too). The
  alien's first-person arm now actually draws (the renderer skipped the hidden body's arm). The flying aliens fly like
  Wind, leaning, banking and stretched out, and none of their flight costs hunger: Pyre's Flame Jet now goes where you
  look (Sunheart half again as fast), its arms swept back as jets, with open flames pouring from its hands and feet; Wyrm's wings beat hard and fold back
  along its body at speed; Wisp's glide is a ghost's drift (tipped forward, arms reaching limp, tatters streaming);
  Nadir flies like creative (double-tap Jump), over three times as fast, and is 20% quicker on foot. Xenoform has no affinity,
  never awakens by fate and isn't on creative's powers screen: the band is the only way to it (`Power.fated`; ops can
  still `/power set` it).

- **0.9.20, villagers' days (Kingdoms 0.6.9):** tag with a child is a real game (they run, you catch them, they
  chase you back); settlements have toddlers again; villagers keep daily routines (water, gardens, market, chats,
  campfires, housework, reading, sweeping, lunch, shopping, animals, eyeing strangers, dancing; kids play chase, hide
  and seek, skip stones and tag along); and they grow suspicious of you in their homes, up to calling it trespass.

- **0.9.19, Andy's power fixes (Kitchen 0.4.36):** falling corpses are caught by an empty hand from up to 2 blocks
  off, and no longer get stuck in blocks tumbling forever. Every web that sticks gives one tug: press Jump in midair and
  you haul up and in on it (`WebSwing.TUG_LIFT`/`TUG_PULL`). A Web Whip with both webs on its catch is one web gripped
  in both hands. Dropping a summoned flintlock drops its pair from the other hand. The Sword of Damocles is hauled back
  up into its rift instead of fading. The Cannon Battery's cannons come out half as fast (`GROW_TICKS` 16) at random
  spots behind you. JEI's cheat mode (click to take items) also switches on while you're opped, in any game mode.
  Then: R/G webs stick to creatures, players and corpses within 2 blocks of the crosshair (out to 32), which lights
  up gold when one's in reach; you swing from them and drag light ones after you (`WebAnchors.entityTarget`,
  `WebPower.tickEntityWeb`). Hanging still in midair with no movement keys, Jump and Sneak raise and lower you slowly
  (`WebSwing.HANG_REEL`). The limbs not on a web hang limp and trail the sway like the Wind hover's (`WebArms.LIMB_*`).

- **0.9.17, imbued blades proc (Kingdoms 0.6.8):** a Sorcerer's imbued blade bursts with its element on one blow in
  twenty rather than every one (the rest spark), and a lightning burst blinds for a moment; air lifts on every second
  blow instead, for air combos. Thrown bolts always burst.

- **0.9.16, thread-safe structure palettes (Kingdoms 0.6.7):** a worldgen crash (two threads opening the same
  structure piece at once) fixed.

- **0.9.15, lunar events and Bad Omen grudges (Kingdoms 0.6.6):** now and then the night's moon is a Blood Moon
  (zombies and skeletons climb out of the ground around you, no sleeping), a Super Blood Moon (twice over, a huge red moon), a Harvest Moon
  (crops grow all night) or a Blue Moon (luck, extra experience and gems). Bringing Bad Omen into a settlement wrecks
  your name there, and if it survives the raid it almost never forgets (`kingdoms/MODLOG.md`).
- **0.9.18, crawl looks and held rolls:** a gesture crawler no longer swims on dry land: they drag themselves along
  elbow over elbow with the legs trailing, and a roll stretches the arms past the head with the legs together while the
  body turns over. Rolls are quick (a block every quarter second, about walking pace) for safe, quiet movement, and holding A or D after a roll keeps you rolling that way, a block at a time (`client/CrawlPose`,
  `CrawlModelFeatureRendererMixin`).
- **0.9.23, Camera Utils with the F7 preset:** Camera Utils joins the pack (client-only, downloaded by id). F7 flips to
  Andy's third-person view (3.4 blocks back, 0.1 up, 0.9 to the side, tipped 3 degrees, GUI shown), set once per
  install before Camera Utils reads its config (`client/CameraPreset`, a preLaunch entrypoint); F6 is its other custom
  view and Right Alt + F6/F7 edits them. Its zoom is taken off Z, the Mode key (`KeyConflicts` v2; OK Zoomer still
  zooms). While one of its views is on, the Web/Gravity shoulder view stands aside.
- **0.9.26, natural disasters, stage 1 (Kingdoms 0.6.12):** storm cells drift across the land (rain and lightning
  only under them), and every few days a supercell puts down a tornado (or a monster) that lifts and flings everything
  near it and tears up the land, whole trees included; dust devils in deserts. Storm bell, weathervane and barometer. Wind users ride a
  tornado's winds and shove one off course with the Cyclone Lance (`kingdoms/MODLOG.md`).
- **0.10.3, vampires and werewolves, stage 1: vampires (Kingdoms 0.7.0):** one Elder Vampire per world, an ordinary
  villager by day who hunts anyone (kings too) at night; the bitten who survive turn at the next nightfall as feeders,
  abstainers or hunters of their own kind; the Elder flies as a bat. Stage 2: the wolf's curse in villagers and folk,
  who change under the full moon (a grotesque change, each beast its own size, build and coat). Stage 3: players turn
  by a vampire's bite or take the wolf's curse by ritual (bone, campfires, full moon); the change tears 30% of your armour's durability
    (it breaks or falls off); the cure for both is a rare priest's quest. Plan: `docs/Vampires-Plan.md` (`kingdoms/MODLOG.md`).
- **0.10.4, cursed werewolves' coats (Kingdoms 0.7.1):** cursed villagers' beasts are mangy, scarred or patchy, with
  the torn remains of their clothes on, dyed by their trade (`kingdoms/MODLOG.md`).
- **0.10.5, grabbable severed parts:** a severed head or limb can be picked up (right-click it with an empty hand, or
  sneak + right-click) and dangles from your right hand as you walk; right-click drops it, sneak + right-click throws
  it, the same as carrying a body. A beheaded player's view goes along with their carried head.
- **0.10.9, rarer tornadoes (Kingdoms 0.7.2):** only one supercell in ten can put down a tornado now, so one comes
  about every 60 in-game days instead of every 6. The other supercells still bring their storm (`kingdoms/MODLOG.md`).
- **0.10.10, sense bolts, takeovers see it through:** the counter's text is gone; a dodge flashes three faint lightning
  bolts over your head (top of the screen in first person) while you can counter. Symbiote takeovers no longer end on
  a timer: it keeps the wheel until its prey is dead (or gets 64 blocks away), its look is had, or its kill is carried
  and eaten, unless you break free, which is far harder now (80 presses, 6 fewer per ability earned, fading faster).
  Hunts no longer give up after 20 s. A corpse in a tendril's grip goes into your arms on a plain right click (no Sneak).
  See docs/web.md.
- **0.10.12, jump cuts a zip, dive slam:** pressing Jump at any point of a Web Zip (or Yank-to) lets go of the line,
  keeping the speed it gave you. Coming down straight out of the wings' freedive lands in a superhero landing that
  breaks the ground and hurls everything near. See docs/web.md.
- **0.10.13, Z Fighter (Andy's idea, our own take; no Dragon Ball mod code or art):** a martial artist who fights
  with fists and ki (`power/zfighter`, `docs/Abilities.md`). Ki meter (100, +5 per foe that ever beat him) charged on
  R (hold), spent on Ki Blast (G), Ki Wave (V hold), Flash Step (B) and ki flight (Z, creative-style, drains ki).
  Fists only: a weapon won't strike in his hand (`ZFighterPlayerMixin` on `Player.attack`); strong fists, 5-hit
  combos with a finisher, ki from punches and from eating. Eats a lot (constant exhaustion, more for everything).
  No foe fells him twice: whatever kills him (kind of creature, or player) is saved in `zfighter_defeats` and does
  50% less to him and takes 50% more from him (`ZFighterLivingEntityMixin`), and each new victor raises his ki and
  fists. Super is the ascension, through hardship (`zfighter_hardship`: clutch recoveries, defeats, rematches, great
  beasts; 40, everything mastered, near death mid-fight): he breaks through and goes Super; then C toggles it (gold
  aura, fists +60%, ki x1.5, faster; burns ki and food). Calling: 20 monsters killed with bare fists while powerless.
  No corruption yet. `/powerdebug zfighter ready|hardship <n>|foes [clear]|tp <n>|mastery <form> <percent>`.
  Andy's follow-ups (same release): the 50% edge lasts only for the rematch (`zfighter_grudges`; killing that foe settles
  it, the growth stays). Training after DragonMineZ's idea, our own build (`ZFighterTraining`, X opens the screen):
  Training Points from blows, kills, time, walking and meditation buy 5 stats; forms Super 2 and Super 3 bought with TP,
  25% mastery of the form below and hardship; per-form mastery (25% next form, 50% free entry, up to +15% power and
  -25% drain); C up a form, sneak+C down one; Limit Break (3 tiers, sneak+X) stacks on a form and burns health.
  Andy's "add all of it" (stage 1, same release): Rage before the first Super (`ZFighterBattle.rage`); techniques bought
  with TP and set on G/V from the training screen's Techniques page (Ki Disc, Homing Volley, Solar Flash, Ki Barrier,
  Ki Bomb; `ZFighterKi`, `ZFighterTechniques`); the Spirit Bomb past Super 3 (hold C in Super 3; gathers from living
  things within 48, hurts only foes, 3 min); Hermit Beans (crop, jungle temples) and Training Weights (`ZFighterItems`);
  Z Fighters within 32 share a quarter of each other's TP. Flight rebuilt Wind-style (Andy): hold Z and scroll to take
  off and step through 5 arm poses (`ZFighterFlightClient`, `ZFighterFlightPose`, synced `zfighter_flight`), ki only,
  charging works in the air; `WindPose` lays him out at cruise speed but leaves his arms to his pose.
  Stage 2, gravity training: Gravity's Z is now the Training Field (`GravityTrainingField`), a minute-long dome of heavy
  gravity where they aim; a Z Fighter in it, or under a Well's pull or Crush's pin (`GravityPower.KNEEL`), trains 2x
  and his blows count as heavy for mastery; the Gravity user earns a mastery use per minute a Z Fighter trains there.
  Stage 3, training activities: the Sparring Dummy (`ZFighterSparring`: a vanilla mannequin wearing your skin, driven
  from the server; it never kills you, yields for a TP prize) and the Training Post block with four client-side drills
  (`ZFighterDrillScreen`, paid by `ZFighterDrills` with a per-drill rest).
  Stage 4, lineages (`ZFighterLineage`, picked once on the training screen; a Writ of Rebirth read while sneaking unmakes
  it): Earthling (TP 1.25x, Limit Break half price and half burn), Warrior-born (tail drawn by `ZFighterTail`, sneak+Z
  wraps it; defeats count double; out under a full moon with the tail out he's the Great Ape: 3x size, +100% fists,
  +40 health, a mouth beam on V, no techniques, forms or flight until the moon goes), Verdant (regenerates, +1.5 reach,
  Giant/Great Giant/Titan forms that grow him instead of Super, sneak+B heals the one you look at) and Frost-born (Second
  and Final forms free to hold, then Golden). Each lineage has its own aura colours.
  Andy's follow-up: the guard and parry (`ZFighterGuard`: right click held bare-handed, half off frontal blows for ki,
  a parry in the first 6 ticks staggers the attacker and makes the next punch a counter; crossed arms drawn by
  `ZFighterGuardPose`), and Web and Gravity's F5 shoulder view (`ShoulderCamera.active`).
- **0.10.14, server start fix:** the Hermit Beans block loot table was in the old format
  (`minecraft:block_state_property`), which 26.3 no longer knows and can't upgrade, so a server wouldn't load its data.
  It now uses 26.3's `match_block` condition and `modifier` (like vanilla wheat).
- **0.10.15, Z Fighter flight and stances (Andy's test notes):** the flight arm poses only show while sprint-flying
  (`ZFighterFlightPose.cruising`: the local player's own cruise, others by speed); hovering or standing, a Z Fighter
  looks like himself. Taking off is a burst of ki that throws him about 8 blocks up (`ZFighterFlightClient.TAKEOFF_BURST`)
  with a ring of dust and a flash of aura. Charging ki now has a stance per lineage and form (`ZFighterChargePose`,
  synced by `ZFighterPower.CHARGE`). Charging in the air keeps his speed; only at 30% health or less does it slow him
  (`ZFighterFlightClient.LOW_HEALTH`).
  Each lineage's own V (`ZFighterKi.SIGNATURES`): Crimson Cannon (Warrior-born), Spiral Lance (Verdant, through walls),
  Frost Needle (Frost-born). Super hair (`ZFighterHair`, Earthling and Warrior-born, synced by `ZFighterPower.FORM`),
  ground debris rising under every form and an aura that grows with each form.
  The V beams are drawn like beacon beams (`ZFighterBeams`, sent by `ZFighterNet.BeamFx`) instead of only particles.
- **0.10.16, brittle skeletons:** skeletons (strays, wither skeletons, bogged and parched too) lose heads and limbs three
  times as often: 15% a blade hit, 60% to the katana's aimed slash.
- **0.10.17, the quarterstaff rides the gale (`WindStaff`):** in a wind sorcerer's hand (a Kingdoms Sorcerer of the air
  element; read by name through `Classes.of`), the staff's five Better Combat swings each carry a gale: Gust, Wind
  Blade, Vortex, Piercing Gale, Downdraft (a slam from the air bounces you up). Right click is the Staff Vault (sneak:
  straight up), landing soft. See docs/wind.md.
- **0.10.18, tidier Z Fighter training screen, a 30-block Spirit Bomb:** the X screen fits small screens (it shrinks to the window and is no
  taller than 236): three tabs (Stats, Forms, Techniques) instead of two crowded pages, striped rows, and long text cut
  with "..." instead of running off. Descriptions, form requirements and how Limit Break works show in full in a box
  at the bottom when you point at a row. The lineage picker wraps its question and descriptions.
  The Spirit Bomb grows up to 30 blocks across (radius 15, was 6) and is drawn as a ball of light (`ZFighterSpirit`,
  sent by `ZFighterNet.SpiritFx`): a see-through pale blue shell with energy streaming round it and a white-hot heart.
  When it meets the ground it grinds down into it for 2 seconds, the earth spraying up round it, then bursts: a blast
  of power 3 + its radius (18 at full) with a ring of 8 more round it, hurting foes out to 3 + 2 per block of radius.
- **0.10.19, Z Fighter stage 5 (`ZFighterWishes`, `ZFighterSites`, `ZFighterWishScreen`):** seven wish orbs in shrines
  300-2000 blocks out, a Wish Radar, and the Eternal Dragon (all seven, at night: one wish of six, then the orbs scatter
  and are stone for three days). The Tower (500-800 out; Hermit sells beans, seeds, the radar) climbs to the Lookout in
  the sky, whose Keeper blesses once a day. Its doors lead to the Time Chamber (`jaymospowers:time_chamber`, flat white,
  2x training, fast hunger) and the Verdant World (`jaymospowers:verdant_world`, green-sky plains with villages).
- **0.10.20, Z Fighter flight poses back (`ZFighterFlightPose`):** the laid-out flight body and the arm pose picked with
  Z + scroll come on whenever a Z Fighter is actually flying fast (0.7 blocks a tick across or diving, off again under
  0.35), for his own screen as well as everyone else's, not only while the client's sprint-cruise flag is set. Hovering,
  taking off and standing he still looks like himself, arms included.
  The Great Ape stands about 30 blocks tall (scale 17, was 3; `ZFighterScaleMixin` lifts the scale attribute's cap from
  16 to 20) and grows into it over 4 seconds, swelling smoothly with dust, smoke and a heartbeat, then roars at full
  size; ending, he shrinks back over 2 seconds.
  The Great Ape has its own body (`ZFighterApe`, skin from `tools/gen_great_ape.py`): the player is hidden and a shaggy
  ape stands in his place in his pose (furred head with muzzle, brow and red eyes, barrel chest, long arms, short legs,
  swinging tail). Grown at least halfway, he stomps: monsters and anything hunting him under his feet take 14 every
  quarter second and are pressed into the ground.
  Super hair covers his own: the skin's hat layer is hidden while it shows, and the cap reaches past where that layer
  sat (0.7 out), with a fringe above the eyes, the whole back of the head and over the ears.
- Gravity's Z dome is now called the **Immense Field** (was the Training Field): key guide, messages and docs.
- **0.10.27, Sora's Dagger removed:** per Andy, Wind's dagger is gone: the item (all four stages), its recipe, Flurry,
  textures, Better Combat dagger preset, the casting-table gilding and the Tool Station diamond setting. Wind keeps
  every ability; daggers already in a world disappear when it loads.
- **0.10.27, daggers ride the gale (`WindDagger`):** wind sorcerers now fight with daggers the way they do with the
  quarterstaff: the four dagger swings carry Gale Cut, Wind Stab, Crosswind and Cyclone Thrust, every hit leaves a
  puff, and right click is Sora's Dagger's old Flurry (10 slashes on the wind, then a burst). See docs/wind.md.
- **0.10.28, the Flash's speed on Chrono's Overclock (`ChronoSpeed`):** while Overclock runs, sprinting is far faster
  and steps up whole blocks, holding forward into a wall runs you up it, sprinting crosses still water, and V again at
  a wall phases you through it (up to 4 blocks). See docs/chrono.md.
- **0.10.28 / Kingdoms 0.7.3, Shield Throw:** a Strength Combat Arts perk (3 ranks): Y with a shield hurls it; it
  ricochets between foes and comes back. Relic shields always come home; a plain one drops where a wall stops it.
- **0.10.29, Fresh Animations: Player Extension (`client/PackResourcePacks`, `client/EmfCompat`, `client/PoseTakeover`):**
  the pack gets FreshLX's player animations (CurseForge 1281029, file 8245230, `FA+Player-v1.1.zip`; modpacks may
  include it, never redistribute it, so it downloads by id like the medieval villager pack) and the two mods that run
  it, Entity Model Features and Entity Texture Features (LGPL-3.0, client-only). It's switched on once per install
  (marker `config/jaymospowers-fa-player-v1`), downloaded from CurseForge's CDN first if missing. `MedievalVillagers`
  became `PackResourcePacks`, a list of packs (the medieval marker stays v2). FA moves the limbs' pivots as well as
  turning them, so while any of our own body poses is on a player (Wind flight/kick/cyclone/sway/tumble/swing, crawl
  and roll, freefall, carry, Gravity walk/board/kneel, Darktide moves and crawl, Web wall run/body/arms/ragdoll and
  the symbiote's slam/charge/roar/feeding, Chrono flow/reel, Xenoform flight/slap, Z Fighter flight/charge/guard,
  Arsenal's reach and flintlock aim) EMF's pause hook stops FA for that player; Better Combat swings already pause it
  (EMF's own Player Animation Library check).
  The Xenoform aliens move in Fresh Animations' manner (`XenoMotion`): with FA on, EMF copies the player's FA pose
  onto each form's body and limbs like armour, and the forms' own extras now follow the body instead of running on a
  bare sine. A per-entity follower tracks turn rate (springy, the trail a beat behind), fall speed, air time, a
  landing jolt and breathing. The Wyrm's wings breathe and bounce with steps, flare open when dropping without flying,
  slam down on landing and bank out of turns; its tail swings behind turns (tip later), lifts as it falls, whips down
  on landing and droops crouched. Blur's and the Gremlin's tails do the same; Pyre's flames stream back with speed;
  Wisp's tatters and Nadir's mantle billow as they drop; the Gremlin's ears bounce and flop; Bulwark's lower arms
  breathe and swing out of turns; Knell's jaw tendrils hang straight down whichever way it looks.
  The Wyrm's wings are bigger, each in two halves (10 + 10 long, 14 deep; was one 14x8 sheet): flying they spread
  about 40 across and the outer halves flex behind each stroke; on the ground they hang from the shoulders and wrap
  round the body like a cloak, the outer halves folded across the chest, breathing and swaying with the steps, and
  open again as it leaves the ground (`Wyrm.WRAP_*`). Its tail is longer, three joints (13 + 10 + 8) running down from
  the back and levelling out near the ground, each joint swinging a beat after the last (`Wyrm.TAIL_REST`). New
  texture regions from `tools/gen_xenoform.py` (wings at 0,90; tail at 0,106 and 32,106).
  Web parkour (`WebParkour`, our own take on ParCool's moves; see docs/web.md): sprint-vault over one-block
  obstacles, wall cling (Sneak in the air facing a wall; A/D shimmy; slips after a hold that grows with mastery), wall
  jump (Jump against a wall or from a cling; W from a cling climbs), and a sprint slide (Sneak while sprinting).
  Everyone sees the poses (`WebParkourPose`, attachment `web_parkour`); Fresh Animations steps aside for them.
- **0.10.31, Z Fighter stage 6 (`ZFighterFusion`, `ZFighterMasters`, `ZFighterRaids`, `ZFighterTournaments`, `ZFighterRogue`,
  `ZFighterKingdoms`):** the fusion dance (Z then C facing a partner: one body for 60 s, the partner spectating from
  inside, control swapping at 30 s, stats added plus 10%, 10 min cooldown). With Jaymo's Kingdoms (read by reflection only): masters (each
  kingdom's eldest villager; a dark master in each barbarian camp) teach a technique or form step once a day, by
  reputation and the Rogue's path; tyrants (a new `rogue_fighter` entity) raid kingdoms by night every 5-7 days and
  share 6000 TP by damage; tournaments every 3-4 days in a ring beside a kingdom's seat, players and champions in a
  bracket, never to the death. Rogue skins from `tools/gen_rogue_fighters.py`. `/powerdebug zfighter raid|tournament`.
  Corruption (`ZFighterCorruption`, `ZFighterMark`): each dark master's lesson fills a third of Fate's corruption
  meter, so the third corrupts. Corrupted, a crimson mark glows on the forehead in every form (over Super hair's
  fringe), and fists and ki power are 15% stronger. Sneak+C in base form toggles Corrupted mode: Super 1's fists,
  speed and ki power with no ki drain, bleeding half a heart a second (ends at one heart), 35% life steal on every hit,
  and 3 Combat Roll charges whose rolls blink (up to 8 blocks towards the target, behind it within 5, ahead with none).
- **0.10.37 / Kingdoms 0.7.5, timelapse night and bed rest:** our own take on Sleeping Overhaul 2 (Forge only): the
  night runs by as a timelapse (the whole world sped up, villagers at work; drag to look around in bed) instead of skipping, leave bed any time, and rest in bed awake by day or sneaking (immune
  to poison and withering), with a Sleep button at night. See kingdoms/MODLOG.md.
- **0.10.42, sailing (pack mods):** Shippy Ships (sailboat, cog, caravel, galleon) and Old Cannons join the pack with
  Ingenium API, all by Caesius_Leo, whose licence allows modpacks with credit. Valkyrien Skies, Eureka and Valkyrien
  Pirates have no build past 1.20.1; Haubna's ocean physics are Physics Mod Pro (paid Patreon), so neither ships.
  With them, Kingdoms 0.7.7 brings sailing stage 1, our own ships built from blocks: put a helm on any hull you build
  on water and cast off (bellied sails on yards, buoyancy by weight, walkable decks and holds, chests usable afloat, drop anchor).
- **0.10.43 / Kingdoms 0.7.8, flintlocks in TaCZ:** TaCZ: Refabricated (the unofficial Fabric 26.3 port of Timeless
  and Classics Zero, beta) joins the pack with Forge Config API Port, used only for our own guns: a musket, a
  blunderbuss and a flintlock pistol, a gunpack of ours (`assets/jaymospowers/custom/jaymos_flintlocks`, from
  tools/gen_tacz_guns.py: models, texture, sounds and muzzle-loading animations, nothing of TaCZ's art) that
  `armory/TaczGuns` hands to TaCZ. They and their musket balls and shot are made at TaCZ's Gun Smith Table; TaCZ's
  modern guns, ammo, attachments and attachment bench are uncraftable (`tinkering/Uncraftable`). Kingdoms'
  musketeers carry the TaCZ musket. Arsenal's Void Armory flintlocks become a TaCZ gun too (void palette, no
  recipe), one in each hand: a click fires the main hand's, the next the off hand's (brought round into the main hand
  once the first is spent, `ArsenalFlintlock.tickTacz`), and Q is the reload: it drops the pair and a fresh pair
  comes up out of the rift. TaCZ is on Modrinth only, so the CurseForge import zip carries its jar
  (untouched, credited; its art is CC BY-NC-ND 4.0).
- **0.10.44 / Kingdoms 0.7.9, King's Roads:** our own settlement roads (Via Romana and Countered's Settlement Roads
  don't run on 26.3, and Via Romana's licence bars a port): roads planned between nearby realms and villages, paved
  in the land's style as chunks load, with bridges, lanterns and milestones; cabs, rickshaws, rulers and caravans keep
  to them. Milestones offer a coach ride along the road or a dearer teleport, and a Road Charter links signs on roads
  you build yourself (Via Romana's idea, our own way). See kingdoms/MODLOG.md.
- **0.10.47 / Kitchen 0.4.39, ragdoll physics:** our own Physics Mod-style ragdolls for corpses (Physics Mod itself is
  closed, ARR and client-only with no API; see kitchen/MODLOG.md). Severed parts roll along the ground as they move,
  rock to rest on a spring (a limb lies along the ground, never on end), and are kicked along by anyone walking into them.
- **0.10.48, Z Fighter fists first and tail fear (Kingdoms 0.7.10):** Z Fighters can swing weapons again, but a weapon
  (or a bow's arrow) hits for 35% unless their Kingdoms class is trained with it (`ClassPerks.trainedWith`, read by
  reflection: Thief light blades, Warlord two-handers, Brute bludgeons, Archer bows, Marksman guns and crossbows, Sorcerer
  swords, Mage staffs, Rider anything from the saddle). A Warrior-born with the tail out frightens townsfolk (`Tails`,
  by the `jaymospowers_tail` tag): they cry werewolf and run, until the player reaches 25 MCA hearts with them (tail
  wrapped) and picks "Explain your tail" in their chat menu; each villager has to be told.
- **0.10.49, sounds, ambience and detail:** Sounds (imb11; new screen, item and block sounds), AmbientSounds 6 (biome,
  cave, water and weather ambience, in place of Dynamic Surroundings, which stops at 1.21.1), Subtle Effects (sparks,
  snoring, dust clouds and more) and A Good Place (blocks animate into place) join the pack with their libraries. Sounds
  awaits its CurseForge id (`packModsNoCf`). Particular stops at 1.21.4, so `client/Ambience` remakes its ideas from
  vanilla particles and sounds: spray, mist and a rumble where waterfalls land, fireflies over flowers and grass in warm
  biomes at night, dust deep in dark caves, splashes sized to what fell in, and a quiet clink of armour as people walk.
- **0.10.50 / Kingdoms 0.7.11, physical falling trees:** felled trees fall to their heavier side, hurt what they hit by
  how fast they're moving, anger the bees of any nest on them, and land as real blocks (logs on their side, leaves
  withering). Physical Falling Trees' ideas in our own code (the datapack is CC BY-NC-ND and stops at 26.2). See
  kingdoms/MODLOG.md.
- **0.10.51, flight never outruns the terrain:** fast flight (any power, elytra, a ridden mount) looks a second ahead
  each move; where the chunks haven't arrived or haven't been drawn yet it slows to a speed that reaches them no sooner
  than they're ready, with a "terrain loading" note, and picks up again as they load (`client/TerrainAhead`). Before,
  players flew into land the game hadn't shown and stuck on invisible blocks. Chunky is back to pre-generate the world
  from the server console, so most of the map loads from disk instead of being generated under a flyer.
- **0.10.55 / Kingdoms 0.7.13, roaming caravans:** pillager caravans (minor, and major under the ominous banner with a
  ravager) and merchant caravans (a wandering trader, trader llamas, pack mules, a nearby realm's guards as escort) now
  pass by players out in the wild, along King's Roads where they run. Pack animals carry chests of the land's goods.
  Pillager Caravans' ideas in our own code (it stops at 26.1 and has its own licence). See kingdoms/MODLOG.md.
- **0.10.60, tracks in the snow:** Soft Imprints (MIT) joins the pack: everything that walks or rolls over snow, snow
  layers, sand and red sand leaves footprints and trails shaped to its body (players, mobs, horses, carts and
  caravans), which slowly fill back in. Client-only and works with Iris shaders.
- **0.10.63, layered storm clouds (Kingdoms 0.7.16):** storm cells are drawn in layers from the same number of puffs: a
  flat dark base, a tower over the middle that builds up as the cell gathers (thunderstorms and supercells), and a
  pale anvil spread over a supercell's top, leaning downwind. Each tornado hangs from a turning wall cloud
  (`kingdoms/MODLOG.md`).
- **0.10.65 / Kingdoms 0.7.17, Andy's immersion mods:** Shoulder Surfing Reloaded (MIT) is the third-person camera for everyone: F5 goes
  to an over-the-shoulder view with the crosshair (Right Alt free look, O swaps shoulder, arrows and Page Up/Down move
  the camera), set to Andy's old Camera Utils F7 numbers (3.4 back, 0.9 right, 0.1 up) and kept coupled to the body so
  powers fire where the crosshair points (`client/CameraPreset`). It refuses to load beside Camera Utils, so Camera
  Utils left the pack, and our own Web/Gravity/Z Fighter shoulder view (`ShoulderCamera` and its mixins) went with it;
  Chrono's Time Flow lets Shoulder Surfing hold the camera. Added: Stormie's Spiders (the maintained Spiders 2.0 fork),
  Nether Portal Spread and Traveler Tool Belt (gigaherz's Tool Belt is NeoForge-only); `client/KeyConflicts` v3 moves
  the belt off R to Caps Lock, Shoulder Surfing's free look off Left Alt (OK Zoomer) and swap shoulder off U (werewolf
  shift). Kingdoms 0.7.17 remakes Realistic Torches and Total Darkness (see kingdoms/MODLOG.md). Turned down: Mo'
  Bends (fights Fresh Animations/EMF and our poses), Immersive Portals (1.21.1 at newest), Better Third Person (1.21.8,
  and Shoulder Surfing refuses to load beside it). Also added: Fancy World Animations (MIT; doors, chests, levers and
  more animate, lanterns sway) and Ji AFK Cinematic (idle cinematic camera; in packModsNoCf until CurseForge has its
  26.3 file). Kingdoms 0.7.17 also gives spiders biome kinds (Spider Overhaul's ideas). Not yet seen in game.
- **0.10.66, servers start again with TaCZ:** TaCZ's gun smith table, statue, target and three workbench block loot tables use 1.21's
  `block_state_property`, which 26.3 can't read (and Moog's on-the-fly upgrader doesn't convert), so a server stopped
  loading its data. Powers ships 26.3-format copies in an always-on built-in pack (`resourcepacks/tacz_fixes`, only
  with TaCZ loaded; `armory/TaczGuns`). TaCZ's jar itself is untouched.
- **0.10.67 / Kingdoms 0.7.18, sailing stage 2 (Andy: "wind, oars, cannons, and the in world build kits"):** ships sail
  by the world's wind (in irons head to wind, best on a reach), row with oars, fight with ship's cannons (broadsides
  from the helm, round shot holes hulls, a ship holed at the waterline founders and lies on the bottom as a wreck), and
  come as Rowboat, Sloop and Brig build kits laid out as frames on the water. After AlekiShips' ideas (MIT, Forge
  1.20.1 only); none of its code or art (`kingdoms/MODLOG.md`).
- **0.10.67, Gale Stream and Smoke Jet** (Andy: "give wind and smoke a held button constant aimed particle projection in
  their respective styles that push sails. wind can also use it to clear smoke and put out fires"): Wind's **Gale Stream**
  is sneak + hold X (`wind/WindJet`): pale wisps and breeze whorls along where you look, 18 blocks, widening; it blows
  smoke on and thins it, snuffs fire blocks and burning things, shoves creatures along, costs hunger and stops at 6
  food. Smoke's **Smoke Jet** is hold X (`smoke/SmokeJet`; X was free): dark smoke driven along your look, 14 blocks,
  laying the real gas thickest at its end and driving smoke on, a gentler shove. Both put a tag on the player
  (`jaymospowers_jet:wind` / `:smoke`) that Kingdoms' ships read (`sailing/Jets`): every sail in the stream adds way
  along it, a gale like a strong wind, smoke half that (tested: a gale moved a furled sloop 6 blocks in 8 s).
- **0.10.67, smoke sim crash fixed:** `SmokeField.step` could send smoke 8 ways (up, five sides, two downwind) into
  arrays of 7, which crashed the server (ArrayIndexOutOfBounds) when smoke sat in open air with room all round. Found
  testing the Smoke Jet; the arrays hold 8 now.
- **Kingdoms 0.7.18, cannons break blocks** (Andy: "cannons should break blocks. no mercy"): ship round shot that hits
  land smashes every block within 1.6 of where it strikes, except unbreakable ones and obsidian-tough ones (blast
  resistance 1200+); 30% drop as items (`ShipShot.breach`). Keep cannons (`Cannon`) are unchanged.
- **0.10.68 / Kingdoms 0.7.19, neighbours** (Andy: "make villagers have more interactions with each other dependent on
  their relationship with each other ... negative interactions at lower reputation, positive interactions, higher
  reputation"): every pair of grown villagers has a regard for each other (-100..100; starts from the pair's own draw,
  MCA personalities, partners and kin) that drives frequent meetings: fights, arguments, shoves and snubs when low;
  greetings, chat and jokes in the middle; gifts, meals, hugs, courting and (with MCA) marriage when high. Each meeting
  moves the regard, so friendships and feuds grow. See `kingdoms/MODLOG.md` 0.7.19.
- **0.10.70 / Kingdoms 0.7.21, grudges** (Andy: "Villagers will set each other up at low rep, maybe giving you a quest
  to beat them up or lure them into a beating"): villagers who come to hate another (regard -50 or less after a
  quarrel) may offer you a grudge quest: give their enemy a 3-blow beating, or walk up to them so they follow you and
  lead them to the giver's hiding spot on the outskirts, where the giver ambushes them. See `kingdoms/MODLOG.md` 0.7.21.
- **0.10.83 / Kingdoms 0.7.24, vampire looks** (Andy: "vampirism should give you the sirben visual modifier"; Sirben is
  an MCA trait that changes your screen; "do both of those"): a vampire player's screen wears MCA's Sirben effect
  (edge blur, red tint) all the time. At night their screen edges also beat blood red and warm-blooded creatures
  within 24 blocks glow red through walls (40 when starving). Everyone sees a vampire player's ashen face and hands,
  and at night their fangs and red eyes glowing where their own skin's eyes are. See `kingdoms/MODLOG.md` 0.7.24.
- **0.10.86, poses fixed, the band's dial on the scroll wheel, dragon wings for Wyrm** (Andy: "holding z and scroll
  should navigate the watch. letting go should slam"; flight poses, web wing gliding and the Xeno-Band in first person
  broken; holstered items still walking in mid-air; "can they use smaller versions of the ender dragon wings?"):
  - Fresh Animations (through Entity Model Features) ran its player animations again as each model was finally drawn,
    after our poses, and its pause check only knew which player was posed while the renderer was still on them. It's
    now answered by entity (`PoseTakeover.posedEntity`), so every pose of ours (Z Fighter flight, Web's wings and
    swings, Wind, the rest) stays ours.
  - The renderer animates the model once more for its layers, and layers that hang things off the body (YDM's Weapon
    Master's holstered weapons, the Xeno-Band) read that plain walking model: our poses now go on there too
    (`ModelPoses`, `PoseLayersLivingEntityRendererMixin`), and the walk stops in any flight or glide.
  - Xenoform: hold Z and scroll to turn the dial (the hotbar stays put), let go to slam; as an alien, hold Z to turn
    back. The hand stays poised while you scroll and comes down as you let go. The band now shows on your first-person
    left arm whenever it's in view (slim arms get a narrower band), and no longer floats where First-person Model hides
    your body's arm.
  - Wyrm's wings are the ender dragon's own boxes at a fifth of the size, drawn from the game's dragon texture
    (`WyrmWings`); they beat, fold, flare and wrap round the body as before.
- **0.10.87 / Kingdoms 0.7.25, no shader message** (Andy: "no shader disable message"): dark nights still turn
  shaders off until dawn, but no longer say so on screen. See `kingdoms/MODLOG.md` 0.7.25.
- **0.10.97 / Kingdoms 0.7.28, creative ship kits build her whole** (Andy: "is there a way to autocomplete a boat in
  creative?"): a Rowboat, Sloop or Brig kit used in creative lays the ship finished (oak planks and rails, spruce masts,
  sails, helm, oars, empty guns, chests), bailed dry and ready to cast off; sneak to lay the bare frames as before.
  Torches and lanterns aboard a ship now light the deck and water around her as she sails (invisible light blocks that
  follow her; Andy: "cant we wimulate them with our lighting?") (`kingdoms/MODLOG.md`).
  Wind's Gale Stream is renamed **Wind Whistle** (Andy: "rename wind's gale stream to wind whistle, and keep the
  animation as if he is leaned over, blowing and his arms are flying backward due to the force of the wind"): held, the
  Wind user leans over from the hips with feet braced, blows the wind from the mouth (the wisps now start there), and
  both arms are flung out behind, fluttering in it (`client/power/wind/WindWhistlePose`, drawn for everyone through
  `WindPose`, so `PoseTakeover.posed` keeps EMF/Fresh Animations off it).
  Ship's Plans (Andy: "can we make our own ship kits?"): sneak-use them on the helm of a ship you built to draw her,
  then use them on open water to lay her again (frames in survival, built exactly in creative); kept after use.
- **0.10.111, the future** (Andy: "what if we made a new dimension, that was just this map, with feral zombies like the
  ones we made for corpses? I want to simulate going to the future in an apocalypse."): Chronokinesis sneak+B, **Fast
  Forward**, leaps you and every player within 4 blocks to `jaymospowers:future`, a lifetime from now: **Los Perrito
  City by Uospag** (https://www.curseforge.com/minecraft/worlds/los-perrito, CC BY 4.0; credited in chat on each
  player's first arrival), ruined. Sneak+B there (1 min recharge) brings you back to where you left, and a golden Time
  Rift beside the arrival point takes anyone home, so friends without Chrono are never stranded. The map is 764 MB, so
  it isn't in the jar: the first leap on a server fetches it from CurseForge (or uses `LosPerrito2.0.1.zip` dropped in
  the server or world folder), unpacks its region/entity/POI files into the dimension's folder and moves its 5,800 map
  arts above every map id the world has used (the city's item frames are pointed at them as they load); the leap shows
  progress until it's ready. Its chunks are 1.19.2 saves the game upgrades as they first load. Each city chunk decays
  the first time it loads (`FutureRuins`): windows smashed out, lights dead, lawns gone to dirt, dead bushes, moss on
  the roads, vines down the walls, cobwebs under roofs, and an ashen biome (`future_ruins`: grey sky, dead grass, murky
  water) around an empty sea like the map's own. Feral hordes (`FutureFerals`): packs of zombies, husks and zombie
  villagers, 10 per player by day and 20 by night, 35% faster, tracking from 48 blocks, drawn to anyone running within
  40 or walking within 16 (sneak to slip by); nothing burns in the future's sun (`FutureSunMixin`). They're vanilla
  zombies, so Kitchen's scavenging and rising corpses work on them as on any other. `/future go|back|fetch|status` for
  gamemasters. Andy, after: "you can only access the sneak + B after killing your echo. inside the dimension echos will
  spawn with actual guns from Tacz, and the city will be further populated with randomly skinned corpse zombies and
  randomly skinned zombies with tacz guns": Fast Forward opens only once your echo has died for good (killed in the
  Nether, or in the future); until then sneak+B says so. A Chrono in the future with no echo has one turn up 30-90 s
  later, carrying one of our TaCZ flintlocks (the pack has no modern guns), and there it can be killed. 45% of each
  horde are Kitchen's corpse zombies (zombie players) wearing random default skins, and 30% of those carry a flintlock.
  Gunners fire traced shots with smoke, flash and bang (musket 9 dmg to 32 blocks, pistol 6 to 18, blunderbuss 6x2.5 to
  10; reloads 3-5 s; no ammo), and drop the gun 10% of the time (echoes never) (`FutureGuns`, `FutureKitchen`).
  Andy, then: "yes i only want the modern guns in the future, but dont load them anywhere but that dimension. not even
  in jei or creative. also spawn up to 4 echos at the same time in the future": TaCZ's default (modern) gunpack loads
  again, exported by us without its gun smith table index (ours stays the Gunsmith's Bench), its recipes and its
  bonus-chest pistol (`TaczGuns.trimDefaultPack`). Future gunners carry its guns now (51 of them; RPG, M320 and minigun
  left out): pistols in pairs, SMGs in bursts of 6, rifles 3, machine guns 10, shotguns 8 pellets, snipers 12 dmg to
  64 blocks, with TaCZ's own report for each gun; a dropped gun comes loaded and gunners leave rounds 35% of the time.
  Modern guns, ammo and attachments are in no creative tab and so not in JEI (`TaczGuns.hideModern`); the resources
  load everywhere (Minecraft can't load a pack for one dimension). Up to 4 of a Chrono's echoes stalk them in the
  future at once, a new one 30-90 s after there's room. Andy, last: "they should stay available if you manage to get
  them out of the future, that gives you a reason to go" and "They CAN follow you home, make sure you arent followed.
  There is no longer a portal so the only way out is death or with a chrono": guns you bring back are yours; echoes
  within 24 blocks when you leave follow you home with their guns and keep firing (a relearn no longer swaps a
  gunner's gun out); the Time Rift is gone, so the way home is a Chrono's sneak+B or dying. `docs/chrono.md`.
- **0.10.116, soft leaves** (Andy: "i need this https://www.curseforge.com/minecraft/mc-mods/the-realistic-leaves-mod";
  it only exists for 1.7.10 on the PML loader, so this is our own take, nothing of it copied): leaves are soft.
  Players, mobs, animals, items and boats push into and through them instead of standing on them; inside, they drag
  (you wade through hedges and settle down through a canopy at about two blocks a second), a fall into a tree is
  cushioned, and holding Jump climbs up through them. Arrows and other projectiles still hit leaves, and falling sand
  and gravel still land on them. Anything moving through leaves rustles them, knocks bits off and shakes real falling
  leaves loose (vanilla sounds and particles drawn on each client, so Iris is fine). Tuning at the top of `core/Leaves`
  and `client/LeafRustle`.
- **0.10.129, pirates and navies** (Kingdoms 0.7.42; sailing plan stage 3, Andy: "resume stage work"): ships with
  crews of their own. Pirate coves rise on coasts near coastal kingdoms, with a pirate ship offshore (sloop or
  brigantine, black flag) that patrols, runs down every ship that isn't a pirate's and fires broadsides; kill her
  captain and gunners and her hold opens, a share per player. Coastal kingdoms worth 1000+ emeralds build a harbour,
  a shipwright and a warship (a frigate from 2000) that fights pirates, and send merchantmen to other realms'
  harbours. Pirates raid coastal settlements by landing parties, kings ask you to sink a pirate or escort a
  merchantman, and pirate holds carry Pirate's Charts to treasure wrecks. See `kingdoms/MODLOG.md` (0.7.42).
- **0.10.130, natural disasters stages 2 and 3** (Andy: "okay resume stage work while we test", then "next stage?"): Kingdoms 0.7.43 adds the
  land's own disasters: sandstorms with drifting sand, blizzards that freeze you, wildfires settlements fight with
  buckets, floods after long rain that drain again, and hail; then earthquakes, struck settlements (news, repairs, tempers,
  relief quests) and forecasts (court wizards, the Journal, a new Almanac). See `kingdoms/MODLOG.md`.
- **0.11.1, character creation can't be skipped** (Andy: "force character creation on spawn"): Kingdoms 0.7.45. A new
  player makes MCA's character (look, then where they start) first, then picks a class; neither screen can be shut
  or put away until it's done, and they can't be hurt or hunted meanwhile. See `kingdoms/MODLOG.md`.
- **0.11.4, Yank moves swing the whole upper body** (Andy: "the web yank overhead spin should animate with more
  gravity, swaying the upper body around as he spins, keeping the legs in a stable pose"): on any Yank web (the Yank,
  its lead, and every Web Whip: overhead, seesaw, lasso) the torso turns at the waist with the weight, twisting after
  the catch, bending into a slam and back under a haul overhead, leaning into it as it goes round, lagging and
  swinging past it on a soft spring. Head and shoulders ride round with it (the web still leaves the hand), a free arm
  swings with the chest, and the legs plant a wide staggered stance (giving way to walking). Knobs: the "heave" block
  at the top of `WebArms`.
- **0.11.5, village life** (Andy: Village Life's features in our systems; that mod is All Rights Reserved, so remade):
  Kingdoms 0.7.46. Festival days at the square (dancing, a feast from the storehouse, a musician, fireworks in the
  realm's colours; a lord can call one with `/kingdom festival`), villagers' own cats and dogs, couriers
  (`/kingdom deliver <player>`), and hunger when the storehouse is bare plus evening weariness. See `kingdoms/MODLOG.md`.
- **0.11.12, everyone is a villager** (Andy: "lock everyone to make a villager instead of vanilla or player"):
  Kingdoms 0.7.47. MCA's character screen no longer offers the player-shaped or vanilla body; every player is drawn
  and sized as an MCA villager, old saves included. See `kingdoms/MODLOG.md`.
- **0.11.14, gun arms hold still in first person** (Andy: "the arms animate in the gun pov"): TaCZ draws a gun's arms
  with the vanilla first-person hand, and Entity Model Features laid Fresh Animations' first-person arm moves over
  them, made for where the vanilla hand sits, so the arms swung off around the view. With a TaCZ gun in either hand
  Fresh Animations now leaves first-person arms alone and only the gun's own animations move them
  (`client/armory/TaczFirstPerson.holdingGun`, paused from `client/EmfAccess`).
- **0.11.14, flintlock animations load again** (Andy: "muskets have no reload animation"): TaCZ couldn't load any of our
  flintlocks' animations (musket, blunderbuss, pistol, Arsenal's pair): the shot's empty `sound_effects` list threw as
  it was read (Andy's log: "Failed to load gun animation runtime jaymospowers:musket_display"), so the reload, draw,
  inspect and shot animations all fell back to nothing. The shot has no sound list now (`tools/gen_tacz_guns.py`).
- **0.11.17, Smoke moves animated; Vapour as a mode** (Andy: "smoke needs animations on every move"; "vapor mode should
  be toggleable, activating when you enter sprint"; then Shift for full vapour, no fall damage, a harder Jet, double
  smoke): `SmokeMoves` syncs the move being made and `SmokeMovePose` gives each Smoke move a pose (in `ModelPoses` and
  `PoseTakeover`). R arms Vapour Form; a sprint takes off into a cruise that holds top speed until S brakes it to a
  hover. Shift (mastered) toggles whole-body smoke and no longer sinks. No fall damage for Smoke users (a second of
  smoke legs on landing). The Jet hurts and throws back; every move puts out double smoke (`SmokeField.MOVE_SMOKE`).
- **0.11.24, YDM's Weapon Master key off** (Andy: "disable YDM weapons master keybind for all"): its only key, Open
  Settings (`key.weaponmaster_ydm.opengui`, default Y, the Combat Arts key), is unbound once per install for every
  player (`KeyConflicts`, marker `jaymospowers-weaponmaster-keys-v1`). Players can still bind it again in Controls.
  The narrator's Ctrl+B hotkey is switched off once per install too (Andy: "disable narrator hotkey";
  `jaymospowers-narrator-hotkey-v1`); Accessibility Settings can turn it back on.
- **0.11.25, Time Bloom heals nearby creatures** (Andy: "chrono heal should heal nearby mobs as well"): while Chrono's
  Time Bloom (B) runs, every living thing within 5 blocks of you (players, villagers, animals, monsters; not armour
  stands) mends the same `BLOOM_HEAL` every `BLOOM_HEAL_EVERY` ticks you do (`ChronoPower.bloomHealNearby`,
  `BLOOM_HEAL_RADIUS`).
- **0.11.26, lanterns stay in the offhand:** a lantern held in the offhand (for LambDynamicLights' light) was set down
  whenever a right click had no use in the main hand. Lanterns (`minecraft:lanterns`: plain, soul, every copper one) are
  now only placed from the main hand (`core/OffhandLanterns`).
- **0.11.27, no more walking on water:** every player could run across water. Chrono's Overclock water run asked the
  client whether its player was overclocked: "last seen" starts at Long.MIN_VALUE, and now minus that overflowed into a
  big negative number, which passed the "within 2 ticks" test, so every client counted as overclocked and any sprint
  ran on water (since 0.10.96 gave the water run its standing shape). Now only 0 to 2 ticks ago counts
  (`ChronoSpeed.overclocked`).
- **0.11.29 / Kingdoms 0.7.51:** emeralds in bundles pay for Kingdoms' bargains, tolls, stables and the rest; what a
  villager says in your own conversation is only shown to you (`kingdoms/MODLOG.md`).
- **0.11.30 / Kingdoms 0.7.52:** every villager speech bubble is seen by all players near, MCA conversations included
  (`kingdoms/MODLOG.md`).
- **0.11.31 / Kitchen 0.4.41, recipes in JEI; left click shows them:** JEI now shows how Tinkering's things are made
  (`client/jei/TinkeringJei`): Part Builder (pattern + material, every part of every shaped material), Tool Station
  (each tool in each head material), Casting Table (parts, ingots and nuggets poured on casts; gold over an item makes
  its cast), Casting Basin, Smeltery melting (what melts into how much of what) and alloying. Kitchen's mill machines
  have pages too (`kitchen/MODLOG.md`). JEI's cheat mode is kept off (`JeiCheats`; the server allows no JEI cheats), since
  with it on a left click asked for the item, was refused and did nothing; JEI's mouse keys are set once more (v2):
  left click shows the recipe, right click the uses (`KeyConflicts`).
- **0.11.32, the counter flashes white:** when a Spider-Sense dodge opens the counter window, your screen's edges flare
  white, then glow faintly in time with the sense bolts until it shuts, in any camera (`WebSenseBolts.edges`).
- **0.11.33, a tied-off lead doesn't leave a line behind:** releasing B ties Yank's lead to the block you look at, and
  the client told that apart from a plain let-go by the creature having one more hitch line. With one line out, tying a
  new one takes the old one off, so the count stayed the same and a fading web line was left behind as well. Now a new
  tie point is what counts (`WebSpentLines.hitchAnchors`), given up to 10 ticks (`LEAD_WAIT`, was 4) to arrive. The
  line falls and fades only when the tie breaks or runs out (or the old one is taken off for the new).
- **0.11.34, slingshot lines pull tight:** while a slingshot is drawn, both lines tighten slowly from their slack to
  stretched taut at a full draw (`WebSwing.SLING_STRETCH` 0.6 blocks past taut, so they thin and hum), for everyone:
  the drawn length is sent like any rope change. How they look only; the swing's rope is unchanged.
- **0.11.45 / Kitchen 0.4.43, hearty meals (our own Spice of Life: Valheim Edition, which is LGPL but only for
  1.19.2/1.20.1):** Kitchen's `stomach/Stomach`. You start at 3 hearts; each food you eat fills one of 3 stomach slots
  (plus a drink slot for potions, milk, honey) and adds its own health (its nutrition, min 2) and regeneration for
  180 s per nutrition point (5-40 min). The same food can't fill two slots, and a full slot only makes room in its last
  20%. Meals add at most 34 health. All three slots full: 10% faster walking. The hunger bar stays as the fuel for
  sprinting and powers, but no longer heals (`StomachFoodDataMixin`); meals heal you after 10 s unhurt. Kitchen dishes
  count 1.25x, Perfect 1.5x. Z Fighters get 2 extra slots (`KitchenCompat.extraStomachSlots`). HUD right of the hotbar;
  food tooltips show a meal's worth.
- **0.11.46 / Kingdoms 0.7.54:** village fires get stone hearths and chimneys when villages generate, and golems only
  burn corpses at open, safe fires, so villages stop burning down (`kingdoms/MODLOG.md`).
- **0.11.48, no portal corruption messages:** Nether Portal Spread still spreads the nether round lit portals, but its
  chat lines (the "corrupted energy" warning, the coal-block fade and portal-broken lines) are off: `core/PortalSpreadQuiet`
  sets its three `sendMessageOn...` options false whenever a server starts, whatever its config file says.
- **0.11.49 / Kingdoms 0.7.55:** tailors (shepherds) change your clothes for a fee that varies by tailor and
  friendship, in MCA's editor on its clothing page only (`kingdoms/MODLOG.md`).
- **0.11.50, Bobby:** Bobby 5.2.16 (LGPL-3.0, client-only; CurseForge 409301 / 9077335, Modrinth J7YnfCss) keeps the
  chunks you've seen and draws them past the server's view distance, so explored land stays visible further out. It
  doesn't help with land nobody has visited (Chunky pre-gen does that). Works with Sodium and Iris; in cached areas the
  fast-flight slowdown (`TerrainAhead`) doesn't kick in, since those chunks are already there. C2ME was looked at and
  left out: its 26.3 build is an early alpha that rewrites worldgen.
- **0.11.51, enchanting overhaul (Enchanting System Overhaul by CmdrJane, MIT, ported to 26.3 as our own):**
  `enchanting/`. The enchanting table opens our table (`EnchantingTableMixin`): no XP and no random rolls. Right-click
  an enchanted book to learn what's in it (kept through death; the book burns up once it taught you everything). At
  the table, pick any known enchantment that fits the item (or a plain book, to pass it on) and pay its items in the 4
  material slots, one level at a time (`EnchantRecipes`: ESO's costs; density, breach, wind burst, lunge ours; other
  mods' enchantments use the default price). Items hold 2-6 enchantments by material (`Enchanting.limit`), +1 with a
  curse; the anvil can't go past that (`AnvilLimitMixin`); loot keeps at most 3 (books 10). Tinkering tools stay out.
- **0.11.52, Super hair remodelled:** the old cap-and-sticks hair is gone. Each Super fighter gets one of two sets for
  good (`ZFighterHair.style`): Swept (locks raked back, close together) or Wild (long locks thrown up and to one side,
  a huge billowing Super 3 mane). Locks are tapering box chains drawn joint by joint, so they blow back when moving
  or flying (harder when cruising) and flutter and straighten while charging. Models live in
  `tools/zfighter_hair/hair.js` (which also renders mock-ups); `export.js` writes `ZFighterHairStyles`. Texture:
  `tools/gen_zfighter_hair.py`.
- **0.11.53 / Kingdoms 0.7.56:** monsters see farther (Farsighted Mobs remade) and creepers stalk you, freezing
  while you look at them (Stalker Creepers remade) (`kingdoms/MODLOG.md`).
- **0.11.56 / Kingdoms 0.7.57:** Attributes on the K screen (PlayerEx remade) and a Heritage step in character
  creation (Origins remade) (`kingdoms/MODLOG.md`).
- **0.11.63 / Kingdoms 0.7.59:** `/kingdom skills` commands for ops (`kingdoms/MODLOG.md`).
- **0.11.64 / Kingdoms 0.7.60:** Sorcerer fire bursts spread to nearby creatures; ice bursts freeze, hurt and chill
  (`kingdoms/MODLOG.md`).
- **0.11.71:** Andy: "remove the metal knife recipes, all weapons should be forged", the Tinkers way. Stone and metal
  weapons (Armory's, Balkon's WeaponMod's knives/spears/halberds/battleaxes/warhammers/katanas/flails/boomerangs,
  vanilla spears, kunai, throwing stars, harpoon, iron quarterstaff) lose their crafting recipes. Each has its own head
  or blade (Claymore Blade, Knife Blade...): shape it from stone at the Part Builder (arrows page to the weapon heads),
  pour gold over it on a casting table for its Weapon Cast, pour iron, gold or copper over the cast for a metal head,
  then put the head, a Tool Handle and a Tool Binding together at the Tool Station (`tinkering/WeaponParts`; textures cut from
  the weapons' sprites by `tools/weapon_parts_assets.py`). Kunai take just a binding, throwing star blanks nothing.
  Wooden weapons, round shields and the musket bayonet stay craftable.
