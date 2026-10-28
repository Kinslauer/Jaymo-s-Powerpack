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
- **0.8.83, Golems keep quiet and fight fires:** ships Jaymo's Kingdoms 0.5.62 (see `kingdoms/MODLOG.md`): iron golems
  never speak, and they beat out fires in settlements.
- **0.8.84, No rainbow hair:** ships Jaymo's Kingdoms 0.5.63 (see `kingdoms/MODLOG.md`): MCA villagers no longer get
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
- **0.8.86, Jaymo's Equestrian, stage 2 (bond and tack):** ships Jaymo's Kingdoms 0.5.64 (see `kingdoms/MODLOG.md`): a
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
- **0.8.88, orc war camps:** ships Jaymo's Kingdoms 0.5.65 (see `kingdoms/MODLOG.md`): our own take on The Orcs! (no
  26.3 build, all rights reserved): orc camps rise in rough country near kingdoms, a dark oak stockade round the
  warchief's longhouse, tents, a forge, a fighting pit and a watchtower; grunts, rangers, a shaman and a warchief boss
  with a ground slam. Their war bands raid kingdoms; killing the warchief clears the camp.
- **0.8.89, fish loot instantly:** ships Jaymo's Kitchen 0.4.29 (see `kitchen/MODLOG.md`): clicking any fish corpse
  (vanilla or Alex's Mobs, beached flying fish included) drops its loot and the body is gone at once.
- **0.8.90, Your own blocks are no crime:** ships Jaymo's Kingdoms 0.5.66: breaking a block you placed yourself in a
  settlement (a chest, bed, crafting table, crops you planted), or opening a chest you placed there, no longer makes
  its people take you for a criminal. Blocks the settlement built still count.
- **0.8.92, Jaymo's Equestrian, stage 3 (the stables at work):** ships Jaymo's Kingdoms 0.5.67 (see `kingdoms/MODLOG.md`):
  stables get a stablehand (boards your horse for 4 emeralds a week, sells horses by land and wealth, buys them back)
  and a farrier (shoes horses, sells tack, the best only in wealthy kingdoms); realms with stables field better
  cavalry; a fallen knight's destrier or a raider's steppe pony goes wild for you to break in; rifling a caravan
  horse's saddlebags is theft; and stablehands and kings give horse quests (break one in, bring a stud, ride to
  another town against the clock). Stablehands and farriers have their own screen: right-click them.
- **0.8.94, Iron golems burn corpses:** ships Jaymo's Kingdoms 0.5.68 and Jaymo's Kitchen 0.4.31: every iron golem
  (village, kingdom or home-built) picks up corpses lying near it and carries them in its arms to the nearest fire pit,
  lit campfire or lava, then drops them in. Monsters at once, other corpses after 5 minutes untouched; never players'
  or golems' corpses. Golems never light fires, and fire pits on netherrack are no longer beaten out.
- **0.8.95, crawl anywhere:** any player can press Jump and Sneak together twice quickly to drop flat (the swimming
  pose) and stays flat while Jump is held, so one-block gaps are passable. Letting go stands you up once there's room;
  under a low ceiling vanilla keeps you crawling. Holding Jump doesn't hop while crawling; not in water, flying,
  gliding or riding. Combat Roll's double-tap Sneak ignores Sneak presses made with Jump down (`core/Crawl`,
  `CrawlPlayerMixin`, `CrawlJumpMixin`, `client/CrawlTaps`).
- **0.8.96, Quests are rare:** ships Jaymo's Kingdoms 0.5.69 (see `kingdoms/MODLOG.md`): only about one villager in
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
- **0.11.71, tournament arenas:** the ring was any spot 14 blocks from the seat at whatever height the ground had
  there (a hillside, a cliff's edge, a roof, the river), so fighters were set down inside the hill or over a drop and
  fell out of it. Now the land 18-44 blocks round the seat is searched (16 ways at 5 distances) for the flattest plain
  ground (no water, lava, houses or roads; at most 4 blocks from lowest to highest), and a floor is laid there: smooth
  stone edged in stone bricks at the ring's line, dips filled, grass, trees and snow cleared to 5 above (trees to 10).
  Fighters stand on it. The old spot is used only if nowhere will do (`ZFighterArena`).
- **0.11.73 / Kingdoms 0.7.65:** the whole realm on alert in a war: bells, sandbags round the seat, civilians hiding
  and guards at the sandbags when a war party comes (`kingdoms/MODLOG.md`).
- **0.11.74 / Kingdoms 0.7.66:** night gifts for vampires and werewolves: dash, groundpound, leap, howl, kin-sense,
  bat wings, lifeleech, frenzy, wall run, dhampirs, wolfbloods, crypts and dens (`kingdoms/MODLOG.md`).
- **0.11.75 / Kingdoms 0.7.67:** two-handed weapons (any Better Combat `two_handed` weapon) reach 25% further, their
  swing box is 35% wider and taller and their arc 25% wider (`core/TwoHanded`, `compat/mixin/TwoHandedReachMixin` on
  `getRangeForItem` for client and server, client `TwoHandedSweepMixin`); mobs get the same reach and arc.
- **0.11.76 / Kingdoms 0.7.68, secret identities (costumes stage 2):** a masked costume (full mask or helmet) shows
  its name over your head to other players (`CostumeClient.extract`), and Powers tags the wearer for Kingdoms
  (`costume/SecretIdentity`: `jaymospowers_masked:<key>`, `jaymospowers_mask_name:<name>`; the key is a stable hash
  of the design's name and look). Kingdoms keeps the costume's own reputation and crimes (`Identities`; see
  `kingdoms/MODLOG.md`). Masked, the costume's name ("Masked Stranger" if unnamed) is your display name everywhere:
  chat, death and join messages, the tab list (resent when the mask goes on or off), the name tag
  (`mixin/CostumeNameMixin` on `Player.getDisplayName`, `CostumeTabListMixin` on `ServerPlayer.getTabListDisplayName`).
- **0.11.77:** Andy: "remove the metal knife recipes, all weapons should be forged", the Tinkers way. Stone and metal
  weapons (Armory's, Balkon's WeaponMod's knives/spears/halberds/battleaxes/warhammers/katanas/flails/boomerangs,
  vanilla spears, kunai, throwing stars, harpoon, iron quarterstaff) lose their crafting recipes. Each has its own head
  or blade (Claymore Blade, Knife Blade...): shape it from stone at the Part Builder (arrows page to the weapon heads),
  pour gold over it on a casting table for its Weapon Cast, pour iron, gold or copper over the cast for a metal head,
  then put the head, a Tool Handle and a Tool Binding together at the Tool Station (`tinkering/WeaponParts`; textures cut from
  the weapons' sprites by `tools/weapon_parts_assets.py`). Andy: the big weapons (claymore, battleaxe, warhammer,
  halberd) take a Tough Handle, and swords (longsword, broadsword, claymore, saber, ninjato, katana) a new Tinkering
  part, the Crossguard, in place of the binding. Kunai take just a binding, throwing star blanks nothing.
  Wooden weapons, round shields and the musket bayonet stay craftable.
- **0.11.79 / Kitchen 0.4.44, player corpses wear their villager look:** with MCA everyone is a villager, so a dead
  player's corpse is drawn as the villager MCA draws them as (genes, skin, hair, clothes), by MCA's own renderer,
  lying and ragdolling like any villager's body (Kitchen `client/corpse/McaLooks`, which asks
  `MCAClient.getVillagerRendererData` and only touches MCA when it's loaded; `CorpseEntity.placeBody`). Their severed
  head and limbs come off that villager too (`SeveredPartRenderer`). Without MCA, a player body, or before the look
  arrives from the server, it's the player skin as before. Zombie players are that villager too
  (`ZombiePlayerRenderer.extractLook`): drawn by MCA's renderer at full infection (green-grey), walking, holding and
  swinging as the zombie does, arms held out (`ARMS_OUT`, `ZombiePlayerModel.armsOut`). Only players the game knows
  (online, or their look already sent) are asked for: the future's zombie players have made-up ids.
- **0.11.80, the future's dead:** Kitchen's zombie players are rarer in future hordes (12% of a pack, `FutureFerals.CORPSE_SHARE`),
  always carry a gun (one that can't be armed isn't spawned) and shoot far worse: a zombie's stray is 3x the gun's
  own plus 4 degrees (`FutureGuns.ZOMBIE_STRAY_SCALE` / `ZOMBIE_STRAY_ADD`; echoes and androids aim as before). The
  rest of the horde is 40% husks (`HUSK_SHARE`; only the hordes' own, tagged feral, are let into the future) and
  plain zombies in our civilian skins.
- **0.11.81 / Kingdoms 0.7.69, power costume sets and tailors (costumes stage 3):** Andy: "each power adds its own
  costume set to the table once you have it". New cuts, masks and capes, offered at the table only while you have
  the power (`costume/CostumeSets`; the server refuses to sew them otherwise; a costume already sewn stays wearable
  and drawn whoever has it): Web: Webbed Suit (web lines, web gloves) and Web Mask (hides the face); Wind:
  Windswept and the Gale Cloak (three tails); Gravity: Dense Plate (shoulder pads, a dark core); Chrono: Clockwork
  (gear-toothed hems, a dial); Arsenal: Bandolier Harness; Resonance: Resonant (sound rings); Darktide: Tidal (darker
  towards the feet) and the Tide Cape; Smoke: Ash Wraps and Ash Veil (hides the face); Z Fighter: Training Gi and
  Headband; Xenoform: Carapace. New enum values go at the end, so saved designs and identity keys are unchanged.
  Textures from `tools/gen_costume.py` (patterns may now return a shade). Tailors: `/costume tailor <player> <price>
  [<tailor>]` (gamemasters; Kingdoms runs it as the server) opens the designer in a paid mode
  (`costume/CostumeTailoring`): no materials, the price in emeralds (bundles count) taken when sewn, within 5 minutes
  and 8 blocks of the tailor; one costume per order.
  Costumes now follow the body they're drawn over (Andy: "the costumes clip over every character, it doesnt move
  consistently at all"; `client/costume/CostumeBody`): the costume re-ran the plain animation when drawn, so under
  Fresh Animations (EMF) it stayed in the vanilla walk while the body moved; the body's EMF pose is now kept on the
  render state just before the layers (`CostumePoseLivingEntityRendererMixin`, only when EMF animated it and isn't
  paused, as Kingdoms' armour does) and the costume takes it. It also takes the model the renderer drew that player
  with (MCA swaps it per player), MCA's villager body's wide arms (not the skin's slim ones), its head size (masks
  scale with it) and its bust (the suit has one that copies MCA's; the emblem moves out in front of it).
- **0.11.82, the Rig (stage 1):** Andy's AI armour suit, for everyone (`rig/`, `client/rig/`, `lang/parts/rig.json`,
  art from `tools/gen_rig.py`). Four armour pieces: Helm (head: the HUD, bottom-right above the power chips: charge,
  actives and cooldowns, the target under the crosshair to 32 blocks, the Mind's warnings; it hides the face like a
  costume mask, keyed by the Helm's name), Core (chest: the charge, `rig_charge`, and the Mind), Arms (worn in the legs
  slot, the frame that drives the arms: bare-fisted punch boost, Sneak + right-click lifts a heavy iron-golem corpse
  through `KitchenCompat`), Thrusters (boots: jump boost). The actives need the whole set and charge: Hover (a toggle;
  Jump rises, Sneak sinks, ends on landing or empty; no fall damage builds up at under 0.5 b/t), Palm Blast (an
  instant beam, 6 damage) and the Shoulder Launcher (two mini-missiles that burst without breaking blocks). Keys: no
  power: R/G/V, B for the Mind's report; with a power the power keys stay the power's and J uses the selected active,
  Sneak + J picks the next. Each power adds one augment (Wind: hover drains less; Gravity: heavier punch; Chrono:
  faster recharge; Web: palm blast slows; Arsenal: one more missile; Resonance: harder palm knockback; Darktide: the
  Mind senses hunters farther behind; Smoke: palm blast blinds; Xenoform: longer palm reach; Z Fighter: punch
  knockback). Charge: stand on a redstone block (trickle), right-click one empty-handed (drains it, a big gulp),
  lightning (full), a lit Kingdoms smithy forge by block id (`RigCharge.FORGE_IDS`: the blast furnace, and
  `jaymoskingdoms:forge` should Kingdoms get one). The Mind (Loyal, Dry or Grim, picked once on first wearing, kept on
  the Core) only warns this stage, rate-limited on the action bar: low charge, low health, a projectile or blast about
  to hit, lava or the void under a fall, a hostile hunting you from behind. The Rig Forge (crafted: iron, salvage,
  redstone blocks, an anvil, a blast furnace) builds pieces from salvage points (Rig Salvage 3, Fallen Rig Scrap 1) plus
  iron and a second material, and refits damaged pieces. Dead Rigs lie only in Los Perrito (one island chunk in 28,
  guarded by two androids, sometimes a mech); Fallen Rigs, fallen explorers who sought a way off Earth (their logs on
  salvaging), only in newly generated Nether (1 in 220) and End (1 in 160) chunks. Stage 3 (later): levels, lock-on,
  scans, the takeover save, flight, calling the suit.
- **0.11.86 / Kingdoms 0.7.72, vassals set apart from kings:** Andy: "vassals should be differentiated from kings". A
  kingdom made a fiefdom now has its king step down to lord in place (circlet, diamond, purple bar, lord's title) and
  its folk take the overlord's arms; lords keep a smaller escort (4); a fiefdom's audience names its overlord. See
  kingdoms/MODLOG.md.
- **0.11.87, the Rig (stage 2): the Mind grows up.** Mind levels 1-5 on the Core (`rig_xp`; `Rig.LEVEL_XP` 0/100/300/
  700/1400; creative counts as 5), learned while the Core is worn (`RigLevels`): kills (hostile 5, player 25, anything
  with 150+ max health 120), wrecks salvaged (40), time worn (1 a minute) and danger warnings proved right (8: still
  alive 10 s after an urgent or low-health warning; dying clears it). Each level the Mind remarks on in its own voice
  (Loyal/Dry/Grim) and names what it unlocked; the Helm's HUD shows "Lv" and a thin progress strip, the Core's tooltip
  the XP. Level 2, **lock-on**: hold the Rig key (J) on a creature (the one nearest the crosshair within 32 blocks, in
  sight); held on nothing it lets go. The lock glows hot pink through walls on the wearer's screen only, with brackets
  round it and a LOCK row on the HUD; palm blasts bend to it when it's within ~37 degrees of the look, missiles home on
  it; it breaks past 48 blocks, on death or without the Helm. Level 3, **scans** (Helm + Core; no power: hold B, the
  tap still reports; with one: Scan in the J cycle; 80 charge, 10 s cooldown): hostiles (red) and players (gold) glow
  on your screen for 10 s, chests and ores within 12 blocks are marked on the HUD through walls, and the Mind sums up
  in chat and names the target's (the lock, else the nearest hostile) weakness by vanilla tags (Smite, Bane, Impaling,
  freezing, water for endermen...). Level 3, **suit calling** (`RigCall`): no power: C (the ultimate key, idle without
  a power); with one: Suit in the J cycle, or J with nothing worn. It parks the worn Rig (the pieces blast off; kept on
  the player in the `rig_parked` attachment, through death: the suit wasn't on you) and calls it back: sparks stream
  in from the sky behind you, then the pieces lock on boots first, a clank and a jolt on screen each; armour in the way
  goes to the inventory. Level 4, **takeover save** (`RigTakeover`, whole set): a hit that leaves you under 3 hearts
  with 150 charge and the save ready: the screen flashes a red frame, your view snaps to the threat, the thrusters
  throw you away from it (the last attacker, else the nearest hostile or projectile), absorption II for 15 s, and the
  hover catches you after 14 ticks; then 5 minutes' cooldown (kept on the Core, `rig_takeover`; the HUD's Save cell
  counts down). Level 5, **full flight** (whole set): double-tap Jump while hovering (again: back to hover); fly where
  you look (0.85 b/t, Sprint 1.1), Jump/Sneak climb and dive, strafe on A/D; 9 charge every 5 ticks, a little hunger,
  and a sudden stop against a wall hurts by the speed lost squared. The barrel roll works on the Rig's thrusters too
  (`FlightRoll.flying`), and the Rig won't hover while a power's flight is on (`FlightRoll.powerFlying`). The H screen
  lists the Rig's keys after the power's while a piece is worn or the suit is parked; each line appears only once the
  Mind's level unlocks it (Andy: nothing shows until you find it).
- **0.11.89, the Backpack:** Andy: "i need a backpack that animates on the player and can be placed on the ground. it is
  only broken by strong explosions". Our own (`backpack/`, `client/backpack/`; Traveler's Backpack has no 26.3 Fabric
  build). Crafted from leather, string, a chest and wool; four rows. Right-click the air to wear it (`Backpack.WORN`,
  server-only contents; `SHOWN` synced for drawing), ` opens it, sneak+` takes it off into the hand. Drawn on the body
  part as the body model holds it at the layers (Fresh Animations, our poses), plus its own spring sway (swings out as
  you start, jump and fall, rocks with each step, flap bounces). Right-click a block to set it down: unmineable,
  immovable, wither/dragon immune, 3,600,000 blast resistance; `BackpackExplosionMixin` tears apart set-down backpacks
  in reach of a block-breaking explosion of power 6+ (`STRONG_BLAST`: charged creeper, end crystal), spilling the
  contents and 3 leather. Sneak-right-click with an empty hand picks it up onto your back. Dying with one on sets it
  down where you fell (kept on with keepInventory). Textures from `tools/gen_backpack.py`.
- **0.11.90 / Kingdoms 0.7.73, downed guards:** Andy: "guards, when killed dont drop as a corpse, instead they get downed,
  and either crawl or otherwise fall into a downed animation, they can be disarmed but not looted". Kingdoms only
  (`Downed`): a killing blow downs a realm's guard or archer instead; they crawl away or lie on their backs, can be
  disarmed by right-click, and leave no corpse and no drops. Left alone they get back up after a minute; once a player
  hits them they bleed out 2 minutes after the last hit, and the 10th hit kills. Mobs, fire and falls can kill them
  too, and sneak + right-click carries one over your shoulder (right-click throws, sneak + right-click sets down).
- **0.11.91, symbiote ceilings, held weapon arm, bladed barrage:** Andy: "Wall crawl should operate the same way for ceilings, also the left click for symbiote weapons should be a click and hold, starting the symbiote tendrils coursing and bulging through the suit before collecting into the arm and forming into the blade/mace. The stabbing while holding a mob should animate the spears like this" (Venom image, hooked blade tips). Sneak under a ceiling slams the tendrils up into it like a wall; the weapon arm gathers while left click is held (lumps over the suit, down the arm, the weapon grows out of the hand) and strikes on release; barrage stabs wear spear and hooked blade tips. See docs/web.md.
- **0.11.99, Wall Crawl tendrils stay out:** Andy: "holding space while in wall climb makes the tendrils un animate and not appear... make it so the tendrils only go away when deactivated or on cooldown". The tendrils are now drawn whenever Wall Crawl is on.
  Also Andy: "allow me to charge jump while using wall crawl": in the suit, holding Jump on the walking tendrils coils a charged leap (instead of climbing) and letting go flings you.

- **0.11.102, Z Fighter lineages are races anyone can be born to:** Andy: "add all zfighter lineages to the heritage screen and remove it from the zfighter's x ... so anyone can be any of those races". The X screen has had no lineage pick since 0.11.76; the lineage is the heritage. `ZFighterLineage.everyone` now gives players who aren't Z Fighters their race from the heritage too: a Warrior-born has the tail (and the villagers' fear of it; wrapped or let out with Kingdoms' `;`, tag `TOGGLE_TAG`), a Verdant is green, mends (health, bones, limbs) and reaches 1.5 further (`race_reach`). Ki things stay with the power: forms, techniques, the Great Ape, healing others, Earthling TP. New Earthling heritage (Kingdoms) maps to the Earthling lineage. Andy then: the Verdant's longer reach "is an animated up to 6 block stretch ... a quick punch activated on left mouse hold that grabs items if aimed at one", and the Frostborn ice prison "should be frostborns left mouse hold": `RaceMoves` + `RaceHoldClient` (bare hand, not at a block; a Z Fighter's hold combo keeps anything within plain reach). The stretch strikes the first living thing (a normal fist blow) or snatches the first item within 6 blocks, every 10 ticks while held, the right arm drawn out along the look (synced `verdant_stretch`); the passive +1.5 reach is gone. A Frost-born's hold (0.6 s) asks Kingdoms for the ice prison through the tag `jaymoskingdoms_frost_prison`; `;` is now only the Warrior-born tail. Andy also: "way too many dismemberments and decapitations, reduce the rate across the board by 50%": `Dismember.RATE` 0.5 multiplies every sever chance (blades 2.5%, katana slash 10%, players 2.5%, skeletons still 3x, symbiote scythe 22.5%). Same release: Emberborn fireproof and sapped by the cold, Frostborn ice prison on `;`, heritage cards scroll (Kingdoms 0.7.75).
- **0.11.104, the Rig drawn as a layered suit that builds itself on:** Andy: "the rig looks good, it just clips a bit
  currently with fresh anims, and it should materialize on the user, peice by peice, one large component unfolding and
  mechanically interlocking onto the user. also make a more layered rig, add depth and detail". On players the Rig is
  now its own model (`client/rig/RigSuit`, `RigSuitMesh` generated with its textures by `tools/gen_rig.py`; the
  vanilla armour layers stay for armour stands and mobs): an under-suit with glowing seams, main plates, raised trim,
  vents, the spine unit on the back (lit cells, side pistons, exhaust ports, wings), pistons and caps at the elbows
  and knees, a lit chest reactor, visor, buckle, palm rings and heel nozzles, with a glow pass. It copies the wearer's
  pose each frame and, under Fresh Animations, the pose EMF gave the body (`RigSuitPoseLivingEntityRendererMixin`, as
  the costume does), so it no longer clips. Each piece builds on plate by plate when it's put on (by hand or a call),
  each plate swinging or sliding in, overshooting and locking with a clunk and an iron clank (a piston thump on the
  heavy ones): the Core's spine unit lands and unfolds its wings, the under-suit spreads from it, the chest halves swing
  round from the back and meet, the reactor slots in and the lights flicker up, then abdomen, shoulders, upper arms,
  collar; the Arms close the hip harness and lock the buckle, then thighs, gauntlets, elbow and knee joints; the
  Thrusters raise the shins and clamp the boots; the Helm comes last, its faceplate swinging down. Off (or parked) it
  comes apart backwards, 1.6x quicker. A suit call now lands the Core first, then Arms, Thrusters, Helm (`LAND_AT`).
  Hidden pieces, costumes and Xeno forms still hide it; a backpack hangs clear of the spine unit. Tuning: the
  constants at the top of `RigSuit`; plate timings and approaches in `gen_rig.py`'s `plate(...)` calls.
- **0.11.104, blood on every blade, and the barrage from the outer tendrils:** Andy: "the Shift + R living tendril LMB
  hold attack should animate from the left and right most tendrils, not the center like it does now. add the blood
  effect to every bladed weapon kill and impact", "on every weapon not just symbiote". The barrage's stabs now come
  from the outermost pair, left and right by turns (walking: the outer spares; `WebTendrilLayer.BARRAGE_FREE`). Every
  blow from a blade (`Dismember.blade`: swords, axes, anything named like a blade, held or thrown, any mod's) sprays
  blood, more the harder it lands (`core/BladeBlood`); so do the powers'
  own cuts (`Dismember.cut`: Darktide's katana, the symbiote's scythe), the symbiote's spear, whip-blade, hook claw
  (and its bleed), the barrage, and now Spike Burst and the roar's spikes (Andy: "add that too").
  Only a creature crushed to death bursts with gore and a squelch (Andy: "burst should only be for if an enemy is killed
  by a crush, like with gravity's modes and meteor impact. Or killed by a great ape stomp"): any death by Gravity's
  weight (a fall after Lift or Crush, the slam, Collapse, Skyfall, the Singularity, a meteor), one slammed into a wall
  by Push, and the Great Ape's stomps and footfalls (`BladeBlood.crushed`). Bones, golems and spirits don't bleed (tag `jaymospowers:bloodless`). Tuning: the
  constants at the top of `BladeBlood`.
- **0.11.105, the symbiote's giant moves, eats by size, and fights you for control:**
  - Andy: "symbiote consume large head should use the animated tongue we made recently, so it appears before the final
    chomp and retracts". The Consume maw now draws the big body's animated tongue (`WebHulk.tongue`, shared): it tastes
    the air as the maw swells out, lashes at the meal `TONGUE_LEAD` 7 ticks before the last bite and is back in the
    mouth as the jaws snap shut.
  - "it should animate swinging at the elbows and knees ... the tounge should only lash out periodically": the giant
    has real elbows and knees (`elbow`/`knee` parts from `tools/gen_web_hulk.py`, `WebHulk.Model.limbs`): forearms
    trail each swing, knees fold through the stride and tuck under in the hunch, coil and sitting, the arms sway
    standing. Its tongue stays in and flicks out every `TONGUE_EVERY` 140 ticks and once after a meal.
  - "verdant stretch should go on cooldown for 2s after use": `RaceMoves.STRETCH_COOLDOWN` 40.
  - "Consumes should also give you varying hunger shanks and therefore hearts dependent on the size of the kill": meals
    go by the creature's standing size (`WebSymbiote.mealSize`): 1-16 food (a chicken 3, a cow 7, a ravager 16), what
    won't fit a full bar heals you (`MEAL_OVERFLOW_HEAL`).
  - "large suit charge jump doesnt seem to work": the charged leap only checked the suit flag, which the big body from
    Living Armor doesn't set; `WebSymbiote.leapBody` now gates it (client and server).
  - "Spending too long in hot or cold climates will make the suit takeover and pathfind until he finds a new one":
    after `CLIMATE_TICKS` 2400 in a hot (base temperature >= 1.5) or cold (<= 0.15) biome, with a whisper at half and a
    dread 20 s before, it takes over and walks you, leg by leg, to the nearest temperate land biome, then lets go.
  - "getting over halfway twice in a takeover comeback disables the suit for a minute with a scathing remark ... it
    should animate you ripping out of the suit as the bar fills ... the tongue should also animate upon the suit
    equipping manually. it should be a lot (x2) quicker when the symbiote takes over itself. it also has a small chance
    to give you control over the living armor suit after a takeover with a snide remark": pushing the struggle bar past
    half twice (`LOCKOUT_SURGE`, re-armed under `LOCKOUT_REARM`) tears you free and locks the suit out for
    `SUIT_LOCKOUT_TICKS` 1200 with a jab (`lockout.*`). As the bar fills (synced `STRUGGLE`) the big body comes apart
    from the head down and the suit peels off face, arms and chest with tendrils clawing back, the view jolting with
    each heave. Takeovers engulf you twice as fast (`SELF_TAKEOVER_SPEED`). After a takeover, `TAKEOVER_GIFT_CHANCE`
    10% it leaves you its Living Armor big body for a minute (`gift.*`). Put on by hand (`DONNED`), the suit or big
    body lashes its tongue once (`WebHulk.DON_LASH_AT`).
- **0.11.107, Tendril Grab drawn like Wall Crawl:** Andy: "the tendril grab should attach you to the wall the exact same way visually that the wall crawl does, but it should still hold you where you attach and rubberband you back and forth like before." The tethered tendrils are now jointed limbs bitten into the wall, as Wall Crawl's walkers; the elastic hold is unchanged.
- **0.12.0, a look for every heritage:** packs Jaymo's Kingdoms 0.7.79 (every heritage but Heartlander and Warrior-born gets marks that glow in the dark, glowing eyes for most, and Tideborn gills and fins; see `kingdoms/MODLOG.md`). No Powers code changed.
- **0.12.3, the wild Great Ape, crippling, Tideborn wet and dry:** Andy's notes. A Warrior-born who's no Z Fighter may still turn under a full moon with the tail out (`ZFighterLineage.WILD_CHANCE` 50%, rolled once a night): a smaller ape (`WILD_SCALE` 13 to the Z Fighter's 16), berserk the whole time (it walks his own client at the nearest living thing in 40 blocks, players included, and swings), at most 5 minutes (`WILD_MAX_TICKS`). Any Great Ape is crippled by blows to the legs (from below `LEG_SHARE` 40% of his height): a Z Fighter's by 25 hits or one blow of 15 hearts, the wild ape's by 15 or 10 hearts. Crippled, he lies flat and can't move (6 s Z Fighter, 8 s wild), then can't be crippled again for 10 s. A blade hit while he lies there cuts his tail off: he shrinks back, and the tail grows back in 3 days (`TAIL_CUT`, saved). Packs Jaymo's Kingdoms 0.7.80 (see `kingdoms/MODLOG.md`).
- **0.12.5, nothing pokes through a costume or the suit:** Andy: "there is a lot of zfighting and clipping issues using
  a warrior born with web power and symbiote should hide your tail and all costumes should hide as much underneath".
  `client/BodyCover` works out on the render state (last, `BodyCoverAvatarRendererMixin` at priority 2300) what a
  costume or the symbiote's suit covers: the skin's outer layer goes under it (the costume's suit at 0.3 and mask at
  0.55 flickered against the jacket's 0.25 and the hat's 0.5), and the layers that hang off the body check it: the
  Warrior-born tail and its belt, the Xeno-Band (body); Super hair, Verdant antennae (a mask over the whole head, a
  hood); the corruption mark, storm eyes (a mask that hides the face). The symbiote covers all of it. Kingdoms 0.7.81
  reads the same answers for the heritage looks.
- **0.12.6, Clumps:** Andy asked whether Clumps helps with lag. Added to the pack (MIT, 26.3 Fabric build 26.3.2): XP orbs merge into one entity whatever their values and are picked up all at once, so fewer orb entities to tick and draw. No Powers code changed.
- **0.12.7, costume mask button:** Andy: "costume mask button. all costumes should have a button". Every costume with a head piece (mask, hood, helmet, goggles, headband, web mask, ash veil) can take it off and put it back on while the rest stays on: the Costume Mask key (**'** by default, rebindable), the Mask button at the Costume Table (and the tailor's screen), or `/costume mask`. Unmasked (`Costume.UNMASKED`, saved, kept through death, synced), everyone sees your face and real name: no secret identity, and the Kingdoms masked tags drop. A costume always goes on masked. Flips under `MASK_COOLDOWN` 10 ticks apart are ignored. With a Rig Helm and no costume head piece, the same key flips the faceplate (`RigHelm.toggleFace`, now public). The symbiote's removal is separate and not in this patch.
- **0.12.7, the symbiote fights to stay on (built on PR #292's tear-off):** Andy: "symbiote's removal is different, it's activation is added to the radial menu, the first few removals at low bond animate like a fight or struggle ... eventually showing just the face ... for a short variable duration until mastery increases. upon first acquisition the suit lay dormant ... for up to two days ... Every message should pulse with tendrils along the vignette". New `WebSymbioteBond`.
  - **Removal fight:** taking the suit off from the radial menu's centre while bond < `FIGHT_BOND` (70) is a struggle the first `FIGHTS` (3) times per bond, with the takeover's own inputs (`STRUGGLE`, new `WebNet.Peel` locks input). The bar drives `WebSuitTear` back and forth (clawing at the chest, the mask sliding off and back); past 40% it heaves back. Win: only the face comes free (`FACE`) for 3–8 s at no bond up to 20–80 s at full bond, then the suit flows back. Lose after `FIGHT_TICKS` (20 s). Past that, the plain tear-off.
  - **Dormancy:** the first bond ever (`MET`) leaves it dormant for 35–100% of 48000 ticks (a quarter of that at full bond), saved across relog (`DORMANT`). No radial powers (refused with a whisper, tiles shaded with minutes left), no reach/pull/Zip bonus, hunger, hunting, instinct, neglect/climate takeovers or gifts; the near-death survival takeover still fires. It whispers every 900–2100 ticks and ripples up the body. `/powerdebug web symbiote dormant <ticks>`.
  - **Tendril vignette:** every `whisper()` sends an urgency (`WebSymbiote.urgency`: 0.2 whispers … 0.75 takeovers, 0.9 hardening, 1.0 near-death panic) and `WebSymbioteDread` pulses 6–26 tendrils in from the screen edges, brighter and wilder with urgency, with a glowing rim at 1.0.
- **0.12.7, web: rapid shooter Web Shot, Yank that holds, a lead that climbs, rolls:** Andy: "the web yank and lead still needs to attach to enemies ... make the web shot tap fire as fast as you can tap the button ... web an enemy up after 5 hits ... only ... with web shooters equipped and spare web in the canisters. organic web disables V, but webbed fists last 30s instead of 10. you also now only get webbed fists and web wings before level 1. but you keep everything ... improve the web lead stair logic ... Web now has up to 5 roll charges, and sense counters use one per reaction. The symbiote should deal alot more damage per second to a host that isnt a web user." Then: "disable webbed fists when the suit is on."
  - **Web Shot (V):** one pellet per tap (`PELLET_COOLDOWN` 2-tick floor); 5 hits within 3 s each (`WebStrength.shotHit`, `SHOT_WRAP_HITS`) cocoon the target. Every pellet, bomb and blast uses one web from a shooter cartridge (`WebShooters.shotHand`); with no shooters or dry ones V clicks and says why (the symbiote's black mass is exempt).
  - **Fists** last 600 ticks (`FISTS_TICKS`) and are off under the symbiote suit, big body or Living Armor (`WebStrength.suited`).
  - **Levels:** level 0 has only webbed fists and web wings; B strings weblines from level 1 and keeps doing so when Yank unlocks (B at a block = webline), so nothing is lost. H screen shows `.early` lines (`KeyGuide`).
  - **Yank / lead (`WebLead`):** Yank takes the creature nearest the crosshair (`AIM_MAGNET` 1.5) instead of needing dead-centre aim; a held mob's own hits and swings, daylight burning and a single punch no longer snap it (`MOB_SNAP_TICKS` 8). Players on the lead still fight free. Anything on the lead gets +0.65 step height and is hauled up ledges to 3 blocks (`CLIMB_*`).
  - **Rolls (`WebRolls`):** with Combat Roll, a web slinger has 5 charges (`ROLL_CHARGES`); each Spider-Sense counter spends one, none left = no counter.
  - **Foreign hosts (`WebInhabit`):** mobs the roaming symbiote takes waste `FOREIGN_HOST_DPS_MULT` 3.5x faster; peaceful ones are spat out hurt but alive.
- **0.12.7, the symbiote changes one form at a time; a taller behemoth; a new maw; takeovers that bound and zip:** Andy: "make it slightly easier as bond increases. however the body isnt drawn underneath the struggle animation ... I'd also like the tounge to be kept to the large suit ... he needs a thinner waist and legs that extend out to the current upper body bulk", "fix the old large head eating animation to be more like the new style", "the head clips and animates seperately", "it first stops at black suit, then another activation brings you to normal", "the symbiote should jump and zip to get out of the area faster during takeovers".
  - **One form per press:** the suit toggle in Living Armor or the big body shrinks back to the plain suit and stays (a whole bond no longer re-swells it); the next press tears it off (fought at low bond). The fight is easier with bond (`FIGHT_PRESSES_MIN` 12, `FIGHT_FADE_AT_BOND`, `FIGHT_SHOVE_AT_BOND`).
  - **Body under the fight:** with MCA models `WebSuitGrowth` hid every MCA part while the suit "covered" you, including during the tear; `bared()` keeps the body. The suit settles back over `FIGHT_SETTLE_TICKS` instead of popping; the tear-off no longer leaves the goo layer hidden.
  - **Tongue** only on the big body (and the consume maw).
  - **Behemoth** (`tools/gen_web_hulk.py`): about 3.7 blocks tall (was 3.05), longer torso, 9-wide waist, longer legs with thick thighs; elbow/knee hinges moved. Its head is placed on the neck in the torso's frame (`WebHulk.neck`), turns at most 0.45 rad, and no longer swings into the shoulders.
  - **Maw** (`WebMawMesh`, from the same tool; `gen_web_maw.py` removed): the behemoth's head grown huge, gaping wide, lashing its tongue and snapping shut on every bite.
  - **Takeover bounds and zips:** getting you away (flee, tour, climate walk) it crouches and bounds up to 12 blocks along the path every 30 ticks (safe landing, no fall damage), and zips a cord 5–18 blocks ahead every 50 ticks (`WebTakeoverPath.ZIP_*`, high ground when fleeing). Any takeover leaps gaps and water, zips out of pits and up cliffs, and jumps when stuck. Each shakes your view; a zip darkens the screen.
- **0.12.9, world tiers:** Andy asked for Apotheosis's world tiers (its code is MIT, its art and text all rights
  reserved; its only builds are NeoForge 26.1). Packs Jaymo's Kingdoms 0.7.82, which has our own world tiers (see
  `kingdoms/MODLOG.md`). No Powers code changed.
- **0.12.10, symbiote fix list + heartier meals:** Andy's list.
  - **Sneak+R/G tear-out:** the chunk only tears loose if the key is still held when the struggle ends (checked on the
    server against `PowerInput` every tick, not only on the release packet). Let go any time before that and the line
    just drops, the terrain whole (Andy: "it only breaks if you follow through"). A tendril-torn chunk is kept (`BlockCluster.keep`): it lies where
    it falls until something moves or breaks it (no crumbling, no max age). Still not saved across an unload.
  - **Wall Crawl tendrils need Living Armor:** `WebSymbiote.crawlTendrils` (Crawl on and armoured). Wall Crawl without
    Living Armor is the plain Web wall climb/cling.
  - **Suit key in the big body at full bond** sheds only the big body: Living Armor's tendrils stay out over the suit
    (`Live.armorSlim`, until the armour comes off); the next press brings that down as before.
  - **Tongue after a meal:** meals are tracked in every form, and it licks its chops 3 times after the maw lets go
    (`WebHulk.MEAL_FLICK*`), on the big body or (only then) on the plain suit (`submitSuitTongue`).
  - **Low health is a share of max health:** `DEFEND_HEALTH` 0.3, `DESPERATE_HEALTH` 0.5, `INSTINCT_LOW_HEALTH` 0.4
    (`WebSymbiote.lowHealth`), so Warrior-born or unfed bodies with few hearts aren't always "near death".
  - **Takeover camera:** your view is dragged to the way you're carried (`VIEW_DRAG` 0.2/tick); look more than 40
    degrees away and it tears it back with a jolt (`WebSymbioteClient.wrenchView`).
  - Packs Jaymo's Kitchen 0.4.46 (heartier meals, see `kitchen/MODLOG.md`).
- **0.13.5, the tallest size is the Verdant's:** packs Jaymo's Kingdoms 0.7.87 (only a Verdant can take MCA's Size slider to the top; see `kingdoms/MODLOG.md`). No Powers code changed.
- **0.13.6, Nightborn blood:** packs Jaymo's Kingdoms 0.7.88 (Nightborn and vampires lean together: Old Blood turning, a long thirst, the Elder's offer, starlit looks, night-deep gifts, born dhampirs, the cure's trace, Blood Moon kin; see `kingdoms/MODLOG.md`). No Powers code changed.
- **0.13.7, the bomb glider (Strains C):** Andy: "make it just dynamite it drops." Finished from his paused draft.
  Kingdoms unchanged (0.7.88).
  - **The glider** (`armory/BombGlider`): our own bat-winged iron board, ridden standing (`BombGliderAvatarRendererMixin`).
    Crafted from 4 phantom membrane, 3 iron and a redstone block (`M M / MRM / III`, unlocked by holding a membrane), set
    down from its item on a block. Right-click to step on; sneak-right-click, or the inventory key on board, opens its
    hold (`BombGliderMenu`/`BombGliderScreen`): a fuel slot with a burn gauge and a 16-stick rack. Health 20; broken, it
    drops its item and everything in its hold.
  - **Flying:** the server flies it from the rider's keys (no controlling passenger, so no flying kick on the server).
    It cruises on its own (0.55 b/t; W 0.85, S 0.3), A/D turn 4.5 degrees a tick, Jump climbs, Sneak dives (the client
    sends Sneak in the air as the dive bit, `BombGliderInputMixin`, so it doesn't dismount; the sprint key is ignored
    aboard). Landed, Sneak steps off; Sneak+Jump bails out mid-air. Fuel: a blaze rod burns 1200 ticks, a lava bucket
    6000 (the bucket back to the rider); a hum and flame puffs while it burns. Out of fuel it only glides down (0.12 b/t).
  - **Dynamite:** left click (`BombGliderAttackMixin`) or R (ability 1; your power doesn't get R while riding) drops a
    stick, falling with the glider's speed. WeaponMod's dynamite through `BombGliderCompat` when it's loaded, else TNT
    on a 40-tick fuse, lit in the rider's name. A fuse is stretched to last the fall plus 10 ticks (`fallTicks`), so a
    stick dropped from high up never bursts in the air. 8 ticks between drops; all checked on the server.
  - **No fall waiver:** sinking faster than 0.5 b/t builds toward a hard landing that hurts the rider as a fall (past
    3 blocks); flying into a wall hurts by the speed lost, squared (x24).
  - Art: `tools/gen_bomb_glider.py` (the 128x64 skin and the item sprite).
- **0.13.8, beam clashes:** a held Wind Whistle (Wind, sneak + hold X) and a Smoke or Soot Jet (Smoke, hold X) that meet
  head-on lock into a clash instead of passing through (two of the same stream too). A churning knot of wind and smoke
  grinds where they meet, both streams drawn into it, with a roar, rings and a light shake; everyone near sees it.
  - **The struggle:** a balance from -1 to 1 moves the knot along the line between the two. Each side pushes steadily
    (more when ascended, more per mastered ability) and harder by mashing X (short releases count as taps); a little
    waver keeps no two clashes alike. Both are rooted and slowed and burn hunger; a "Clash" panel at the bottom right
    shows the balance. Soot blinds near the knot, and the other user once it's close.
  - **Endings:** the knot reaching you overwhelms you (6 damage, thrown back; wind spins you, smoke engulfs and blinds
    you); letting go or looking away lets the other stream roll through (2 damage); a stalemate past 8 s detonates the
    knot (4 damage to both, everyone near thrown, both streams cool down 5 s).
  - All in `core/BeamClash` (numbers at the top) and `client/BeamClashClient`; each beam joins with one call
    (`WindJet.init`, `SmokeJet.init`), so other beams can join later.
- **0.13.9, Ki Bomb and Ki Disc fixed; the disc cuts through:** learning a technique (bought on the training screen or
  taught by a master) never put it on its key, so G kept firing the Ki Blast and sneak+V the Ki Wave until "Use" was
  pressed on the Techniques page, and before V unlocked the Ki Bomb just said "locked". Learning one now puts it on G
  or V at once (a chat line says which), and a learned quick technique works on G before G unlocks, as the Ki Bomb
  already did on V. Characters who learned them before this still press "Use" once.
  - **The Ki Disc cuts through everything:** 60 blocks at 1.3 b/t; every creature its edge passes is hit once (7,
    scaled by ki power, no longer missing one it starts inside); every block in a 1-thick, ~1.2-wide slice breaks and
    drops. It shatters on anything as hard as obsidian, unbreakable blocks, block entities (chests, furnaces), blocks
    the thrower may not break (spawn protection, adventure mode, claims) or an unloaded chunk. Fluids don't stop it.
    Numbers at the top of `ZFighterKi`.
- **0.13.10, a villager's ape:** packs Jaymo's Kingdoms 0.7.89 (villagers are born into heritages; see
  `kingdoms/MODLOG.md`). A Warrior-born villager with a wrapped tail, outdoors at night under a full moon, has a 10% chance
  once a night to turn into the wild Great Ape a non-Z-Fighter player becomes (`VillagerApe`: +80 health, 14-damage
  fists; ends at dawn, after 10 s out of the moonlight, or after 5 minutes). Brought down the same way (blows to the
  legs, then a blade while it lies there), its tail is cut for good and it never turns again; its lines change to
  match. A villager cut at birth never turns. The ape body hides the villager's (`ZFighterHumanoidMobRendererMixin`), and
  `ZFighterLineage.race(...)` now reads any entity's heritage.
- **0.13.18, ledge grab and climb-over, for everyone** (no power needed): "if they can see over it with a jump they can
  grab it, with an animation hanging from the edge, then jump over." Hold forward and Jump at a wall about two blocks
  high: as your eyes come level with its top (the edge at most 0.1 above your eyes, at least 1.1 above your feet,
  within 0.35 of your body, facing it within 40 degrees, rising or at the top of the jump) you catch it and hang,
  square to the wall, both arms up on the edge, legs dangling, swaying a little from the hands. A fresh press of Jump
  pulls you up and over in 0.8 s (pull up, right knee then left onto the edge, stand), for 0.6 exhaustion. Sneak,
  Back, looking more than 100 degrees away, or 20 s hanging lets go. The top needs room to stand (the whole climb path
  clear, straight up from the hang then across) and a solid enough top (0.45 of a block's area; never fences, walls,
  gates, cactus, magma, bars: the `jaymospowers:ledge_no_grab` tag). Not in creative flight, an elytra, water, lava, on
  a ladder, riding, carried or carrying (players, animals, blocks, Kitchen corpses), crawling, meditating, or in a
  power's own movement (Wind/Z Fighter/Xenoform/Smoke/Rig flight, web lines and wings, parkour moves, wall runs, Wall Crawl, Darktide moves and stuns, Time Flow's stun,
  Gravity's Weightless/rock flight/wall walk/forward fall, held or lifted, Chrono's wall run; the server also refuses
  while the power keeps you floating or skips the movement check). Grabbing resets your fall. Everyone sees the pose
  (`ledge_hold`, synced); the hands grip the top of your first-person view and press down through the climb, the
  camera rising with the body. The client moves itself (`client/LedgeClient`, `LedgePlayerMixin` in place of the
  travel) along a collision-free path, so the server sees ordinary movement, and `LedgePacketListenerMixin` keeps it
  from kicking a hanging player for floating (and for 1 s after). Clicks are swallowed on the ledge, so Better Combat
  doesn't swing. Mobs don't use it. Numbers at the top of `core/Ledge` and `client/LedgePose`; see `docs/carry.md`.
  - **A hit knocks you off:** hit while hanging or climbing (a creature, a projectile or a blast) and you ragdoll and
    fall, thrown clear of the wall (`Ledge.knockedOff`, the carry ragdoll `Carry.THROWN`).
- **0.13.19, Z Fighters grow slower and hit softer:** Andy: scaling down across the board, taking longer, more training
  and more varied. A fully trained Z Fighter is about a third as strong; getting there takes about 4x as long.
  - **Per level:** Strength 0.067 fists (was 0.2), Toughness 0.2% (0.6%), Vitality 0.134 health (0.4), Ki Power 0.5%
    (1.5%), Energy 1 ki (3). Forms keep about a third of their bonus (Super 3: +53% fists, ki x1.4, +15% speed); Limit
    Break, rage, corruption and the per-defeat bonus cut likewise; one punch tops out at 9; ki techniques at full about
    x1.9 (was x4.34): Ki Wave ~34, Ki Bomb ~38, Ki Disc ~11, Spirit Bomb ~228. A fresh fighter is unchanged.
  - **TP earns a quarter** from every source (hits, kills, time, travel, shadowboxing, posts, sparring, tournaments,
    raids, wishes, blessings); stats cost 30 + 15 per level bought (was 20 + 10). Form mastery gain halved.
  - **Stats train themselves** (`ZFighterGrowth`): each stat has a bar filled by fitting training (Strength: heavy
    gravity, heavy blows, hard blocks, carrying, heavy shadowboxing; Toughness: hits taken while healthy, guarding and
    parries, heat and cold; Vitality: sprinting, swimming, climbing, walking; Ki Power: ki hits, holding beams,
    shadowboxing; Energy: charging, draining and refilling ki, ki flight). A full bar is a level (same cap of 50; free
    of the TP price). Each method tires through the day (halving every 12 points, floor 5%), so mixing is fastest.
    Bars on the training screen; `/powerdebug zfighter train <method> <points>|newday`.
  - Existing characters keep their bought levels; their bonuses shrink to the new per-level numbers.
- **0.13.20, villagers' heritage looks fixed:** packs Jaymo's Kingdoms 0.7.92 (the racial marks no longer z-fight MCA
  villagers' skins; an Earthling's fist glow lights only near death, with a random 5-minute buff; villagers call you by your
  MCA creator name or costume name, never your username; see
  `kingdoms/MODLOG.md`). No Powers code changed.
- **0.13.21, Training Points are spent again:** Andy: "right now you can purchase any skill with 0 tp, you should still
  require it as a resource for techniques." Every buy on the training screen (stat levels, Super 2/3, Limit Break tiers,
  techniques) skipped its TP price in creative, where TP is never earned, so the counter sat at 0 while every Learn
  button worked. Now each one checks and spends its TP on the server, creative too, and the client greys out Learn and
  "+" when you can't afford it. Creative still skips a form's mastery and hardship (not its TP); levels a stat's own
  training raises stay free. For creative testing, `/powerdebug zfighter tp <n>`.
- **0.13.22, the Ki Disc is thrown overhead:** Andy: "Ki disc should animate like a disc that you throw from above
  your head, and thrown" (the Destructo Disc). G now winds up first (`ZFighterDisc`): the throwing arm goes straight up
  and a flat, thin disc of ki forms over the palm, lying level and turning, growing from a sliver to its full 1.2 blocks
  across over 0.8 s (16 ticks), with motes drawn in round its edge and a rising whine (beacon hum climbing in pitch,
  amethyst resonance over it, a chime when it's full). You're slowed as when charging. Then the overhand throw: the arm
  comes over and down in 4 ticks, the body leaning into it and the shoulders turning through, the other arm flung back;
  2 ticks in, the disc leaves the hand from above your head, aimed at what's under your crosshair, and the arm holds
  low for 5 ticks before easing out. Everyone sees the pose (`zfighter_disc_throw`, synced; `client/ZFighterDiscPose`)
  and the disc: a ki orb flagged `Orb.DISC`, drawn by `ZFighterOrbs.disc` as a level disc (white-hot heart, see-through
  blade fading to the edge, a bright rim and saw teeth) spinning slowly in the hand and fast in flight, with a high
  whir as it flies. The cutting is unchanged (60 blocks; stops at obsidian-hard, unbreakable, block-entity or
  forbidden blocks). The 12 ki is paid when the wind-up starts; dying, losing the power, being silenced or turning
  into the Great Ape mid-wind-up lets it fizzle. G's cooldown covers the wind-up (18 ticks + the old 20). Numbers at
  the top of `ZFighterDisc` and `ZFighterDiscPose`.
- **0.13.23, the symbiote hangs, hurries and protects:** three of Andy's symbiote requests, plus its Mask.
  - **Hanging, not walking the air** ("symbiote walks in midair while using wall crawl tendrils, he should just hang
    there, legs swaying"): carried on Wall Crawl's tendrils the vanilla walk cycle kept stepping under the body, since
    nothing posed the legs off a wall. Now whenever the tendrils hold you with nothing under your feet (carried between
    them in the open, on a ceiling, hung off a latch with no wall beside you; `WebTendrilWalk.hanging`) the walk stops and
    `WebHangPose` dangles the legs: a slow pendulum (`HANG_SWAY_RATE`), the left leg `HANG_PHASE_GAP` behind the right,
    swinging harder and trailing back for a few seconds after you were moving (`HANG_SWAY_MOVING`, `HANG_SETTLE`).
    Climbing a wall keeps its walk. Everyone sees it.
  - **Takeovers go flat out** ("too many times it is just walking away"): its target not yet on your client (a
    sightseeing marker placed the same tick the takeover was sent) made the client drop the takeover outright; now it
    keeps driving along its path for up to `TAKEOVER_LOST_GRACE` ticks. It never slows for your hunger any more (it
    sprints and bounds you hungry or not); corners and steps only ease it to `WALK_SPEED` 0.3 (still past a sprint),
    the sprint held. Chasing something more than `BOUND_CHASE_RANGE` (8) off it bounds along the way at it, and past
    `ZIP_CHASE_RANGE` (12, `WebTakeoverPath`) it zips at it too, as it already did when running or sightseeing.
  - **It always fights for you** ("it should ALWAYS fight back if you are low health"): at or under `PROTECT_HEALTH`
    (30%) with something on you (it hurt you lately, or a hostile has you as its target, within 16 blocks) it seizes you
    at once, whatever its hunger, bond, mood, dormancy or a lockout, and goes for the attacker, hardening a shell over
    you (`PROTECT_SHIELD` absorption, at most every `PROTECT_SHIELD_COOLDOWN`) with your screen reddening and lurching;
    each one down, it turns on the next, and lets go once nothing has been after you for `PROTECT_CALM_TICKS`. The old
    near-death defence (roar, Shroud, run) is now its answer when the fight is hopeless (`PROTECT_HOPELESS_HEALTH` 12%,
    or `PROTECT_HOPELESS_CROWD` 4 after you), straight into the run when the roar's on cooldown. Wrestling it off still
    works, but under attack it only holds back `PROTECT_SUPPRESS_TICKS` (5 s) before it seizes you again (never a
    lockout). New lines `web_symbiote.protect.*`.
  - **The Mask** ("asked for a Mask option before"): the radial menu has a seventh slot (a head icon) that takes the
    suit's head off so your own face shows (your MCA villager's head, skin, face and hair, with MCA), the rest of the
    suit staying on; picked again it flows back. Synced to everyone and saved (`WebSymbiote.UNMASKED`). Unmasked, the
    suit no longer covers the head or face (`BodyCover`), so Kingdoms' heritage looks and eyes show. The costume mask
    key (') flips it too when you wear no costume mask. The suit was never a secret identity (`SecretIdentity` only
    reads costumes and Rig Helms), so nothing changes there. The suit toggle's little mask in the middle moved under
    the bond line, where it no longer hides behind the text.
  - **The Mask fights you at a weak bond** (Andy: "it should still be a struggle at low bond like the rest of the
    suit"): under the suit fight's bond (70), taking the mask off starts the same struggle bar as taking the suit off,
    every time; win and it comes off until you put it back. Sulking, locked out or still settling in, it won't let go
    at all. Putting it back on is never fought (`WebSymbioteBond.resistMask`).
- **0.13.25, ledge hands in first person fixed:** Andy's clip: hanging from a ledge, the view was full of huge grey arm
  faces, and after the pull-up both arms stood up from the screen's bottom corners, shoulder-end on. The first-person
  arms were turned on top of vanilla's own arm placement, and the hang's tilt went the wrong way, swinging them back
  through the camera. Now vanilla's placement is undone and the arms are placed straight in the view (`LedgePose`
  `FP_*`, numbers at its top): hanging, they rise from below the view to the hands on the edge near its top, shifting
  their grip; climbing, the edge sinks through the view and the hands go down with it, out of sight before you stand.
  Third person is unchanged.
- **0.13.26, costumes no longer flicker on MCA bodies:** Andy's clip: a black costume with the MCA clothes flickering
  through it. With MCA a player's clothes are drawn on their own body copy at 0.0625, their outer layer (jacket,
  sleeves, pants) at 0.3125 and its hat layer at 0.5625; the costume's suit sat at 0.3 and its mask at 0.55, a hair under
  each, so they fought. The suit now stands at 0.35 (over the clothes, still under MCA's hair at 0.375, so long hair
  falls over it) and masks at 0.62 (over the clothing hat and the hair) (`CostumeModels`, numbers at its top). Without
  MCA it's the same costume, a breath looser.
- **0.13.27, every Z Fighter has a ki colour of their own:** Andy: "every zfighter should spawn with a random ki color".
  Gaining the power rolls one from twelve bright hues (sky blue, deep blue, white, gold, orange, red, pink, purple,
  violet, green, lime, cyan); Z Fighters from before get theirs the first time they're about. In base form it colours
  their ki blasts, Homing Volley, Ki Disc, Ki Wave (and the lineages' signature beams), Ki Bomb, Ki Barrier, the charge
  orb and aura, and the wave's charge bar; a form's own colour (Super's gold and the rest) still takes over in it, and
  the Spirit Bomb keeps its own. Saved, kept through death, synced to everyone (`ZFighterPower.KI_COLOR`,
  `KI_PALETTE`). `/powerdebug zfighter kicolor [reroll|<hex>]` shows, rerolls or sets it.
- **0.13.28, ki beams travel and clash:** Andy: "the crimson cannon works, just make it travel slower and beam battle
  clash with every other one", then "make all the lineage beams work with this". Every lineage's Ki Wave (Earthling and
  plain: the Ki Wave; Warrior-born: the Crimson Cannon; Verdant: the Spiral Lance; Frost-born: the Frost Needle) and the
  Great Ape's mouth beam are no longer instant (`ZFighterWaveBeam`): let go, the head shoots out from the hands (the
  Ape's mouth) at its own speed, a new `speed` on each `Signature` (Ki Wave 2.5 blocks a tick, Crimson Cannon 1.2, slow
  and heavy enough to see coming, Spiral Lance 2.0, Frost Needle 4.5; the Ape's 1.8). It's drawn from the hands to the
  head all the way, smoothed between ticks, with a ki orb on the head and a hum following it (`ZFighterNet.BeamHead`,
  `ZFighterBeams`); what the head reaches is hurt, pushed and chilled once, and it bursts where it stops (its reach, a
  block unless it's the Spiral Lance, or a clash), then fades as before. Damage, ki costs, Super colours, blasts and
  griefing rules are unchanged. While it's out the fighter holds both arms out along it (`zfighter_wave_firing`, synced;
  `client/ZFighterFiringPose`), slowed as when charging, and V does nothing else.
  - On its way it's a beam in `BeamClash`, so it locks head-on with Wind's and Smoke/Soot's streams and with any other
    fighter's beam (any lineage, the Great Ape's too). `BeamClash.Beam` takes an optional `Shape` for such fired beams:
    they meet where their head has got to, every V press is a push tap (no hold-and-release), only looking away lets go,
    and their strength multiplies the steady push: 0.6 to 1.2 by charge, 30% of the fighter's ki power on top, and 0.7
    to 1.2 by the ki left (clashing drains 0.3 ki a tick; the Great Ape pushes at 1.3). The head sits on the knot while
    they grind. The existing outcomes apply: the loser's beam bursts at the knot (shaking the ground only), a winning ki
    beam travels on past the knot, and a stalemate detonates both. Ki overwhelming someone burns them for 4 more and sets
    them alight; the knot churns sparks and end rods for a ki side, and the HUD says "Mash V". Wind's and Smoke's own
    code is untouched (their 4-argument `Beam` still works).
  - Rogue fighters' blasts are still instant (BeamClash is for players). Numbers at the top of `ZFighterWaveBeam`,
    `ZFighterKi` (speeds) and `BeamClash` (the ki outcome).
- **0.13.31, move names match the move:** Andy: "Not every move name is consistent across zfighters, for instance the V
  overlay says ki wave always." The V charge bar's label was the fixed "Ki Wave"; it now names whatever is on V: the
  lineage's own wave (Crimson Cannon, Spiral Lance, Frost Needle) or the Ki Bomb (`ZFighterLineage.techniqueKey`, as
  the training screen already did). The V key's description in the powers menu names the lineage waves too, and the
  Crimson Cannon's description no longer calls it violet (it takes your ki colour since 0.13.27).
- **0.13.29, Super 2 and 3 within reach:** Andy: "super 2 and 3 dont seem to be possible to acquire". The slower growth
  of 0.13.19 cut TP to a quarter (about 180 an hour from time alone, half a point a hit), but Super 2 and 3 still cost
  4000 and 12000 TP: some 22 and 67 hours. They now cost 1500 and 4500 (`ZFighterTraining.FORM_TP`), still slower than
  before 0.13.19; the mastery and hardship they need are unchanged. And creative players earn TP now (and share it
  with friends nearby), since buying costs TP there too since 0.13.21: creative testers were stuck at 0.
- **0.13.30, a Verdant's hold combo stretches, and the race move goes on sneak beside a power:** Andy: "verdant zfighter should
  do the hold combo on left click, using stretches throughout to extend range", then "verdant swing stretch is over
  ridden by symbiote click hold and other powers". Before, a Verdant Z Fighter's left hold was split: at a creature in
  plain reach it was the hold combo, at anything else (air, a foe further off) the race's stretch punch (one every 2 s).
  Now his whole hold is the hold combo (`ZFighterStretchCombo`, hooked into `ZFighterCombo`), and it stretches: a blow at
  a foe out of plain reach shoots the arm (the right leg for kicks, the left arm for the jab) out to it, up to 7 blocks
  (`STRETCH_RANGE`), and 30% of the other blows (never the opener) stretch anyway for show (`FLAIR_CHANCE`). A
  stretched blow takes 1.2 to 1.5 times as long (a different amount each time), winds up with a small slime jump, lands
  at half for 85% of the same blow's damage with the same knockback (thrown along the line from him), strings slime
  along the limb and splats at the far hand, and snaps back with a high slime squish and a bobber reel-in. The finisher
  is always a double-arm stretch slam (both arms raised overhead and brought down onto the foe, full finisher damage,
  a heavy slime thud, a cloud and a shake); the tier 4 dragon fist stays one arm, stretched up. The foe stays locked as
  far as the arms reach (lunges after nothing past 7 blocks), and a Verdant lunges at 35% (`LUNGE_SHARE`) and only leans
  into a stretched blow (`STEP_SHARE`); plain blows reach no further than anyone's. Everyone sees it
  (`zfighter_combo_stretch`, synced; `client/ZFighterStretchPose`): over the combo's own move the striking limb swings
  onto the line to the foe and is drawn out long (its `yScale`, as the stretch punch's arm), then snaps home. Other
  lineages' combos are unchanged. A Verdant who isn't a Z Fighter keeps the stretch punch as it was.
  - The rule for left click (Andy: "Swap the lineage click hold ability to secondary when you have a power or any
    other click hold, for example, stretch should be the shift click instead"; `RaceHoldClient.secondary`): with no
    power, a Verdant's or Frost-born's bare-handed left hold (not at a block) is the race's, as before. With a power
    (other than the Z Fighter's, whose hold combo the race belongs to), or another power's left-click hold wanting the
    click (`RaceHoldClient.powerWants`; today the symbiote's weapon arm), the plain left hold is the power's and the
    race's move is sneak + left hold: the stretch punch and the ice prison. So the symbiote's weapon arm (`WebMorph`,
    which had swallowed them) keeps the plain hold. A Verdant Z Fighter's whole hold is his stretching combo and a
    Frost-born Z Fighter's ice prison keeps its place in his. Single-click power actions (Gravity's throw, the
    tendrils' combos, Web Cut, Chrono Flow's strike) aren't holds and are unchanged; a symbiote takeover still has the
    hands. Numbers at the top of
    `ZFighterStretchCombo` and `ZFighterStretchPose`.
- **0.13.31, canoes (Kingdoms 0.7.96):** Andy linked Canoe Boats (by CanineGray, CC BY-NC 4.0, made for 26.3): every
  boat is drawn as a canoe, through Entity Model Features and Entity Texture Features, which the pack already has. It
  joins `packResourcePacks` (CurseForge 1086847, file 8944107) and `PackResourcePacks` switches it on once per install,
  fetching it from Modrinth's CDN (the same file, SHA-1 checked) if the import skipped it. Build Modpack's own download
  of a resource pack now encodes spaces in file names as %20 (this is the first with spaces). Kingdoms 0.7.96:
  Survivalist hearts grow with level, and a Warrior-born's low-health fury scales and shows (kingdoms/MODLOG.md).
- **0.13.32, Better Weapons' moves on our forged weapons:** Andy wanted JMB's Better Weapons (MIT, Fabric 1.20.1)
  "compatible with our tinkers". The pack already forges seven of its eight weapons at the Tool Station (the Armory's
  dagger, longsword, glaive, flanged mace and cudgels, WeaponMod's halberd and battleaxe, our quarterstaff, vanilla's
  spear), so what came across is what they do, in our own code (`armory/ArmoryHits`, numbers at its top): cudgels
  (20%) and flanged maces (25%) now and then stun what they hit for 1.5 s (Darktide's stun, now public: a mob stops
  thinking, a player loses their movement keys), with a clang and stars; glaives and WeaponMod's halberds now and then
  hook a rider off its mount (30%), slowed a second; a quarterstaff held up (right mouse) blocks from the front like a
  shield over a 70° arc, the staff taking the wear. A wind sorcerer's right mouse is still the Staff Vault, and the Wind
  power's gust no longer tears a quarterstaff out of the hand as if it were a shield. Forged weapons are the same items,
  so they do it too.
- **0.13.36, Web Whip on Sneak; one pair of hands in first person:** Andy: "Web swing grab attacks should be activated by
  holding shift while two lines are attached" and "all the counters and moves should be better animated in first person.
  Right now there are doubled hands". With both swing webs within 3 blocks of each other on one creature or on terrain,
  Sneak starts the whip, or pries the terrain loose while held (let go early: cracks stay, the attack ends); Sneak + R
  or G no longer does. Small catches still reel in and far-apart webs still slingshot. In first person with
  First-person Model, the vanilla hands (and the extra carry/ledge arms) aren't drawn while a move poses your body's
  arms, and First-person Model's hiding of an item-holding body arm is undone for the move. See docs/web.md.
- **0.14.3, hoods hide you at a distance, and the assassin's robe:** Andy: "id like any hooded costumes to add to
  stealth, while not completely concealing identity like it does now. This outfit is a good base for the assassins"
  (a white and red hooded robe). Two new pieces at the Costume Table, anyone's (no power set): the **Assassin's Robe**
  cut (a fitted robe crossed at the chest, a secondary-colour sash and buckle, a layered shoulder mantle, leather
  bracers on the forearms and long coat tails split front and back, hanging from the waist to the knees) and the
  **Peaked Hood** (a deep hood with a pointed peak over the brow, its underside in shadow, and a cowl over the
  shoulders). Our own design, drawn by `tools/gen_costume.py`; `tools/costume_preview.py` draws it on a player, front,
  side and back, in white/red and black/grey (`art-overhaul/assassin/`). A hood (the plain one or the peaked one) that
  is up leaves the face and the real name showing, but someone else's name tag is only drawn within 8 blocks
  (`CostumeClient.HOOD_NAME_RANGE`), and the wearer carries the entity tag `jaymospowers_hooded:<key>` (the same
  identity key the masked tag uses; `SecretIdentity.HOODED`, `Costume.hooded`) for Kingdoms' stealth to read. The
  costume's tooltip says so. Numbers at the top of `CostumeModels` (tails 11 px from 9 below the shoulders, the brim,
  bracers, mantle and cowl's growth).
- **0.14.3, freerunning (Kingdoms 0.7.101's Stealth page 2):** Andy picked "Full freerunning". The moves are ours
  (`freerun/Freerun`, client `client/freerun/FreerunClient`, numbers at the top of `Freerun`); the perks are
  Kingdoms', read by reflection on `Skills.rank(Player, String)` like Meditate (nobody has them without Kingdoms) and
  synced to every client (`Freerun.PERKS`) for the poses. Climber (cling to tall walls, climb 0.05/0.07/0.09, grip
  3/6/10 s then slip; the ledge grab takes over at the top, `LedgeClient.catchFromClimb`), Freerunner (a sprinting
  jump vaults anything up to 1.5 high at full speed; rank 2, a wall kick, up 0.5 out 0.45, once per wall per airtime),
  Sure Footing (narrow tops at full speed, 75% less knockback, arms out; without it, with Kingdoms, no sprint and 30%
  slower there, Andy's call), Leap of Faith (no fall damage into hay, water or leaves; a head-first dive looking down), Bar Swing
  (catch a fence, bar or chain over your head and hang, swinging; Jump flies off forward 0.9, up 0.5) and Wall Runner
  (1.5/2.5 s along a wall, kick off with Freerunner 2). Every move is checked on the server (rank and geometry), costs
  hunger and keeps your speed; a cling, hang or wall run stops a fall counting, a vault or kick doesn't. Each move tags
  the player `jaymospowers_freerun` for Kingdoms' Stealth experience. Web slingers keep their own parkour: the two
  stand aside for each other (`WebPowerClient.parkourBusy`, `FreerunClient.active`). The web's vault, cling, kick and
  wall run poses draw the freerun moves too (`Freerun.MOVE`, `Freerun.WALL`); new `BalancePose` and `BarHangPose`.
  Block tags `jaymospowers:freerun_narrow`, `freerun_bars`, `freerun_soft_landing`, `freerun_no_climb`.
- **0.14.5, Smoke's particles are geyser steam:** Andy: regular and ascended smoke should use "the geyser particle
  effect" (potent sulfur over lava), "far thicker", the gas staying for both and corrupted smoke keeping its particles;
  then "just use the particles and not their specific properties". New `jaymospowers:smoke_steam`
  (`SmokeParticles.STEAM`, client `SmokeSteam`): vanilla's `geyser_poof` sprites, moving like smoke (keeps its
  velocity, slows and rises a little), smoky grey and translucent. It replaces the campfire smoke of Smoke Jet, Vapour
  Form's tail, spill, landing, hits and start, Smoke Step and Smoke Bomb; Vapour Form's start throws a column of it
  up. Corrupted (not ascended) users keep the old particles. See docs/smoke.md.
- **0.15.1, Wounds phase 1 (core):** Andy (2026-10-19): "I NEED this worked in with our corpses and cripplings, remove our
  downed system for a universal one written like this https://modrinth.com/mod/first-aid-new". The first of five phases
  (`docs/Wounds-Plan.md`); no debuffs and no downed state yet, and Kingdoms' `Downed` is untouched. Written without a
  compiler: build it before anything else.
  - **Body parts:** `core/wounds/BodyPlan` gives each living thing parts that share its max health: humanoid (head, body,
    two arms, two legs, two feet), quadruped (head, body, front and hind legs), arthropod, flyer and simple (body only,
    left to vanilla health). The plan comes from the new entity type tags `jaymospowers:body/humanoid`, `quadruped`,
    `arthropod`, `flyer`, `simple`, else from the shape (taller than 1.3 times its width is a person). Bosses (`c:bosses`,
    the Ender Dragon, the Wither) and armor stands keep plain health.
  - **Health stays real:** the `jaymospowers:wounds` attachment (`Wounds.PARTS`, a fraction per part, synced to the creature
    itself, saved, dropped when everything is whole, not kept through death) is only how vanilla health is spread.
    Healing of any kind (regeneration, food, Kitchen's stomach) fills the most hurt part first; health lost without a
    routed hit comes off the limbs first and never kills (`MIN_VITAL_UNROUTED` 0.05). Players are brought in line every
    `PLAYER_RECONCILE_TICKS` (10).
  - **Where hits land** (`HitRouting`): a player's swing on a person by the part their crosshair crosses (`LimbBoxes`, a
    leg low down is the foot); other blows by the height, side and facing of the hit point (`HEAD_FROM` 0.75, `BODY_FROM`
    0.4, `FOOT_BELOW` 0.12, `SIDE_SHARE` 0.25, `FRONT_SHARE` 0.2). Falls land on the feet (front and hind legs, legs,
    wings), explosions spread over every part by share, drowning on the head, fire, magic and lightning on the body.
    A part at zero spills the rest on (limb to body, foot to leg to body, body to head, head to body).
  - **Death:** head or body at zero kills through a new `jaymospowers:wounds` damage type ("died of their wounds") dealt
    like Dismember's decapitation, so Kingdoms' downing and other powers' death cancels still apply; if the death is
    cancelled the vital part gets `MIN_VITAL_AFTER_SAVE` (0.1) back.
  - **`/wounds show|part <part> <health> [target]|heal [target]`** (gamemasters), and a body silhouette bottom-right
    (`client/wounds/WoundsHud`: parts coloured by fraction, white flash for 8 ticks on a loss, fades 3 s after all whole).
- **0.15.1, Wounds phase 2 (downed, for everyone):** Andy (2026-10-19): "remove our downed system for a universal one".
  Kingdoms' downed guards (its 0.7.73-0.7.93) move here as `core/wounds/Downed` (spec `docs/Wounds-Phase2.md`; numbers
  at its top). Written without a compiler: build it before anything else.
  - **Who goes down** instead of dying: players (not creative or spectator), villagers, anything Kingdoms marks with the
    scoreboard tag `jaymospowers_downable` (its folk), and tamed animals (a tame `TamableAnimal`, a tamed horse, donkey,
    llama or camel). Monsters, wild animals and bosses die; so does anything beheaded (or already headless), anything
    killed by what ignores invulnerability (the void, `/kill`), and anyone with a totem in hand (the totem goes first).
    Every other mod's death save (Darktide's shade, Xenoform, sparring, Kingdoms' Last Stand) runs before it.
  - **Down:** `DOWNED_HEALTH` (6), head and body given a little back (`Wounds.settle`). The `jaymospowers:downed`
    attachment (game time, crawler bit; saved, synced to all) and the tag `jaymospowers_downed`, which Kingdoms reads.
    Mobs lose their AI (`DownedMobMixin`); half (`CRAWLER_CHANCE`) drag themselves from whoever felled them. Players
    are held in a slow crawl (`Crawl`, `PLAYER_CRAWL_SLOW` -50%, Jump doesn't stand them up), drop whatever they
    carry, and can't attack, use, build, break, pick anyone up or press a power key; their screen greys
    (`client/wounds/DownedHud`).
  - **While down:** fire, falls and mobs hurt out of what's left and can kill (that death counts; while down, Wounds
    leaves the parts to follow the health). A player's blow is a wound instead, the `MAX_WOUNDS`th (10) kills. A mob
    or tamed animal no player has hit gets up after `RECOVER_TICKS` (1 min) at `RECOVER_HEALTH` (30%); once a player
    has hit it, it bleeds out `BLEED_OUT_TICKS` (2 min) after the last hit.
  - **Players don't bleed out into a respawn** (Andy: "Lie until roused or give up"): after `BLEED_OUT_TICKS` they pass
    out (`jaymospowers:downed_out`; they lie still, the screen darker, a slow heartbeat) until brought round, or they
    hold sneak for `GIVE_UP_TICKS` (5 s; a bar over the hotbar) to give up: a death of their wounds credited to whoever
    felled them. Logging out keeps it; respawning, `/kill` and the void clear it.
  - **Bringing one round:** sneak + hold use on a downed one (Kingdoms' 0.7.93 revive, for everyone): a tap picks them
    up (`Carry.pickUpDowned`; anyone downed is carryable, a carried one can't struggle free), held for `REVIVE_TICKS`
    (3 s) it gets them up for the least filling food you carry; a tamed animal takes one of its own treats instead
    (`Downed.TREATS`: a wolf bone or meat, a cat fish, a horse golden carrot, apple or hay...). Kingdoms' villagers still
    tend the downed: they set the tag `jaymospowers_revive` and the body gets up on its next tick.
  - **Zombies and skeletons fight on:** when Wounds' lethal rule fires on one because its body (not its head) gave out,
    `Wounds.UNDEAD_FIGHT_ON` (60%) it fights on (tag `jaymospowers_fights_on`); then only its head kills it.
  - **Pose** (`client/wounds/DownedPose`, from Kingdoms): people lie on their backs or crawl face down as before; any
    other body (a wolf, a horse) lies on its side. Downed players keep `Crawl`'s pose.
- **0.15.1, Wounds phase 3 (cripplings):** Andy (2026-10-19): "I NEED this worked in with our corpses and cripplings".
  Broken limbs, sever on overkill, and the old cripplings rerouted into Wounds (spec `docs/Wounds-Phase3.md`; numbers at
  the top of `core/wounds/Breaks` and `Dismember`). Written without a compiler: build it before anything else.
  - **Broken parts** (`core/wounds/Breaks`): a limb a routed hit takes to zero is broken (arm, leg), sprained (foot) or
    grounded (wing): the `jaymospowers:wounds_broken` bitmask (a bit per `BodyPart`; saved, synced to all, not kept
    through death) and the scoreboard tag `jaymoskitchen.broken.<part>` for Kitchen's corpses. Effects are transient
    modifiers refreshed every `REFRESH_TICKS` (20), ids `jaymospowers:wounds_*`: an arm -30% attack damage, -25% attack
    speed, -30% digging per arm, no shield or bow from its hand, `ARM_DROP_CHANCE` (25%) the hand drops what it held as
    it breaks; both arms (or one broken and one gone): a player can't swing, dig, place or use anything, but food, drink,
    potions and medicine (anything with a `FOOD` or `CONSUMABLE` component) still go down. A leg -30% speed, -40% jump,
    no sprinting; a player's two legs: crawl only (`Crawl`, the key and Jump don't stand them up); a mob's every leg:
    -70% speed, no jump. A sprained foot -10% speed each while at zero; a sprain is never capped and needs no setting:
    it heals as any part does and clears above `BROKEN_CAP` (a hard fall doesn't send you to a priest). Wings: the flyer is pulled down and loses its flying speed. A severed
    part takes Dismember's own effects instead, never both.
  - **Healing:** an unset break (arm, leg, wing) never heals past `BROKEN_CAP` (25%) and a severed part not at all; the surplus goes to
    other parts, and health nothing can hold is refused (vanilla health is held to the parts' total). A break is set by
    a priest (Fractures' 2 emeralds), a respawn, a Hermit Bean, Verdant's healing, Chrono's Bloom or Wind's Zephyr's
    Breath (`Wounds.mendAll`); then it heals normally and the break clears above the cap. No more knitting by itself
    (phase 4 brings the splint). Breaks from a fall come set: they heal by themselves, the limp or crawl lasting only
    until the leg is back above the cap. A set bone broken again comes unset. A severed leg's foot never heals either.
    Known cost: healing that's refused (vanilla regeneration onto capped parts) still spends the food and saturation it
    took. `/wounds break <part> [target]` and `/wounds mend [target]`.
  - **Fractures folds in:** a blunt blow's break is `Wounds.breakPart` (the limb's health gone, marked broken). Its own
    `broken_bones`, `bones_mending` and `bones_mended_by` attachments, limp and empty-hand are gone (the old saved limp
    modifier is taken off on load); the head daze and the skeleton shatter stay. `Fractures.heal`, `startMend`, `broken`
    and `breakBone` still work, delegating. The client's hanging arms and legs read Wounds' broken parts.
  - **Sever on overkill** (`Dismember.severOnOverkill`, called by Wounds): a blade's blow on a limb already broken, or
    one that takes the head past zero, for more than `SEVER_OVERKILL` (half) of that part, cuts it off at
    `min(1, SEVER_BASE (0.6) * scale)`; the old chances are the scale (`RATE` times the blade's chance over a plain
    blade's: a sword 0.3, Darktide's katana 1, skeletons three times). The flat random sever is gone; a killing blow is still tested, so heads and limbs still come off onto the corpse
    (Fabric's after-damage event never sees a killing blow, so the blow and the parts before it are noted in a last
    `ALLOW_DEATH` phase after Downed's, and tested in `AFTER_DEATH` once the death has gone through). A power's own cut
    (`Dismember.cut`) lands its blow on the part it aimed at. A severed limb stays at zero and broken; regrowth sets it.
    A headless zombie or skeleton now dies only when its body gives out.
  - **Cripplings rerouted:** Darktide's leg strikes (G Crescent low, Ink Cross, Fang Barrage, any low Darktide hit) wound
    the struck leg by `DarktideStatus.LEG_WOUND` (half of it) through `Wounds.hurtPart` instead of the 5 s crawl, so two
    strikes break it; Pounce's three stabs count a broken leg as crippled. The timed crawl stays for bodies Wounds
    doesn't part. The Great Ape's leg-crippling (a timed knock-down of a transformation) and trips and stuns stay as they were.
- **0.15.1, Wounds phase 4 (medieval medicine):** bleeding, festering, pain and the medieval kit (spec
  `docs/Wounds-Phase4.md`, track A; Kingdoms' clerics are track B). Numbers at the top of `core/wounds/Bleeding`,
  `Treatment` and `Pain`. Written without a compiler: build it before anything else.
  - **Bleeding** (`core/wounds/Bleeding`): a blade, a projectile or a zombie's claws dealing `BLEED_FROM` (2) or more to
    a part opens a cut (`jaymospowers:wounds_bleeding`, a bit per part, synced to the target). Every `BLEED_TICKS` (4 s)
    a cut drips and costs the part `BLEED_DAMAGE` (0.5; a stump `STUMP_BLEED` 1) as `jaymospowers:bleeding` damage, and
    a cut closes by itself after `BLEED_STOP_TICKS` (30 s); a stump never does. A bleeding part heals no further than
    `CUT_CAP` (half). Not while downed, not in creative, never the bloodless. Kingdoms' tending (tag
    `jaymospowers_bandaged`) stops all bleeding and binds the worst part.
  - **Festering:** a dirty cut (a rag, a zombie's claws) may fester (`FESTER_CHANCE` 5% each bleed tick): the part
    can't heal past where it stood, a fever every `FEVER_TICKS` (Weakness, the body hurt, a sickly shimmer), and a limb
    left festering `FESTER_LIMB_TICKS` (a day) rots off (a stump, bled shut). Strong spirits or honey (sneak + use,
    looking down, or on another) clean it.
  - **The kit** (creative tab Medicine, `core/wounds/MedicineItems`, icons by `tools/vanilla_style/medicine.py`):
    linen bandage (stops a cut, heals 3 over 10 s), rag (stops it, heals 1.5, dirty), splint (a broken arm or leg is
    usable and heals; `Breaks.splint`), needle and thread (8 uses: stitches a cut, stings 1, the part heals past half),
    a red-hot blade (heat it on a lit campfire, fire or lava: seals even a stump, burns 2), smelling salts (4 uses: a
    downed body up at once with 2 health, Speed then Nausea; a passed-out player 40% and the whole vial), jar of leeches
    (1 health for long numbness), willow-bark tea (numb after 10 s, for 3 min), poppy syrup (numb 6 min, wakes the
    downed; two at once knocks you out; addictive). Recipes in `recipe/medicine/`; poppy syrup needs Kitchen's strong
    spirits.
  - **Treatment** (`core/wounds/Treatment`): use on yourself or another opens the body screen
    (`client/wounds/BodyScreen`: the silhouette, each part's state, greyed where the medicine can't help); pick a part
    and hold still. Sneak + use treats the worst part while sneak is held. Moving, a hit, or the medicine leaving the
    hand spoils it. Use on a downed body treats it before Downed's pick-up.
  - **Pain** (`core/wounds/Pain`): numb (tea, leeches, poppy, or Kitchen drunk 4+) ignores the effects of breaks.
    Poppy addiction rises 20 a dose, decays always; past 40 and 5 min without, withdrawal: Weakness, Nausea pulses,
    the shakes (`client/wounds/MedicineHud`).
  - **Downed:** a knocked-out body (`Downed.knockOut`) comes round on its own; `Downed.rouse` and `wake` for the salts
    and the poppy.
- **0.15.1, Wounds phase 5 (polish):** Andy (2026-10-20): "last phase". The last phase of `docs/Wounds-Plan.md` (spec
  `docs/Wounds-Phase5.md`; track A the client feel, track B the rules, tuning, docs and JEI). Written without a
  compiler: build it before anything else.
  - **A limp** (`client/Dismembered`, `GAIT_*`): a broken leg drags or swings short, the good leg's steps shorten
    and the body dips on the bad step, seen by everyone; a splinted leg limps half as much, a sprained foot half too.
    Only on foot (not swimming, riding, crawling or downed), humanoid models only.
  - **Pain on screen** (`client/wounds/PainHud`): head or body under 35% with the pain not dulled (`Pain.DULLED`, now
    synced to its owner each second), the screen edges darken and slowly throb; any part losing health flashes the
    edges red for 6 ticks, stronger for a bigger drop (none in the first second after joining); head or body under
    15% on your feet, a heartbeat that quickens (18 down to 11 ticks) with a faint red pulse. Smelling salts bring a
    sharp gasp (`MedicineHud`, `GASP_*`). None of it in creative or spectator.
  - **Freerunning respects breaks** (`freerun/Freerun.canMove`, from PR #339): no freerunning move at all on a broken
    leg out of a splint, or with both arms broken (or gone); one broken arm or a sprained foot rules out only the moves
    that hang on the grip, the climb (cling) and the bar swing (`Freerun.canMove(player, kind)`, `gripMove`). Wounds'
    `Breaks.legsUsable`, `armsUsable` and `gripUsable` read the synced bitmasks, so the client (`FreerunClient` doesn't
    try those moves, and lets go of a climb or a bar when an arm breaks or a foot sprains) and the server (refuses and
    ends them) agree. A splinted leg freeruns; a splinted arm still counts as broken for the grip.
  - **`jaymospowers_crippled`:** anything with a broken leg (any of its legs, splinted or not, severed included) carries
    this scoreboard tag, put on and taken off with `Breaks`' refresh (every second; `Breaks.CRIPPLED_TAG`). For a later
    Kingdoms patch: its hidden blade's 10x on a Crippled foe (Kingdoms 0.7.101) could read the tag too. Kingdoms is
    untouched.
  - **Tuning:** `Bleeding.FEVER_TICKS` 400 → 1200 (a fever every minute, was every 20 s): a festering part can't heal,
    so a festering chest bled a player down from one dirty cut within a minute or two; the plan's fever is "slow
    damage". Everything else in phases 1-4 is unchanged; the open questions went to Andy (stump bleed, festering
    chance, head cuts, the sever chance on a broken limb).
  - **JEI:** a Medicine page (`client/jei/MedicineJei`, a second `jei_mod_plugin` on `SimpleCategory`): each kit item,
    honey, Kitchen's strong spirits (when loaded) and a red-hot blade, with what it treats and how it's used, the
    numbers read off `Treatment` and `Pain`. Lang `jei.jaymospowers.medicine.*` in `lang/parts/wounds.json`.
  - **Docs:** a Wounds section in `docs/Abilities.md` (body parts, downed, breaks, bleeding and festering, every
    medicine with its recipe, addiction, Kingdoms' clerics and taverns, `/wounds`), and a Wounds entry in
    `docs/HANDOFF.md` (the event order learned, the shared scoreboard tags). Phase 5 marked built in
    `docs/Wounds-Plan.md`.
- **0.15.2, the rapier (with Kingdoms 0.7.105's shields, clash and tiered gear):** Andy picked all five of Just-In
  COMBAT's ideas (Forge 1.20.1, All Rights Reserved: ideas only, our own code and art). The Armory gets a rapier in
  every tier (`Armory.WEAPONS`, `armory/RapierItem`; sprites, models, Better Combat's `rapier` preset, the wooden recipe
  and the netherite smithing from `tools/gen_rapier.py`, which runs after `armory_assets.py`). A little lighter than a
  sword and quicker (+2 damage over the material, -2.0 speed). Right-click lunges: 1.1 blocks a tick forward, and the
  first foe in front of the point within 7 ticks is run through for 1.6x your attack damage (1.5 s cooldown, some
  hunger, a point of durability). Mobs holding one lunge at their target from 2.8 to 6.5 blocks off (3.5 s between).
  Forged like the rest: a Rapier Blade (1 ingot) with a crossguard and a rod at the Tool Station
  (`tinkering/WeaponParts`, sprites cut by `tools/weapon_parts_assets.py`). Numbers at the top of `RapierItem`.
- **0.15.4, Sharpness cuts (the fifth Just-In COMBAT idea, on Wounds):** a weapon with Sharpness counts as a blade for
  Wounds' cuts, and each level lowers the damage a hit needs to open one by 0.3 (2.0 plain, 0.5 at the least):
  `Bleeding.SHARPNESS_BLEED_FROM`, `SHARP_BLEED_FROM_MIN`.
- **0.15.5, feathers and the dodge, in place of Combat Roll:** Andy asked for Feathers and Elenai Dodge 2 to replace
  our dodge and its HUD. Both are Forge only (to 1.20.x); the maintained fork, Feathers of Fatigue, is NeoForge 26.3 and
  GPL-3; Elenai Dodge 2 is CC BY-NC-SA. Powers is MIT, so it's our own, from their ideas only (`docs/dodge.md`, the
  spec and sources). `stamina/Feathers`: a row of 20 above the hunger bar, armour holds back 0.35 a point, regrowth 1
  per 10 ticks after a pause that grows with spends in a row, halved by cold and by hunger at 6 or less, 6 red strain
  feathers past empty (repaid first, faster at rest), exhaustion past the strain until a third is back, 4 held back
  in the Nether without Fire Resistance, a sleep refills it, and golden bonus feathers from powers. `stamina/Dodge`:
  Left Alt or double-tap Sneak hops you 0.85 along your movement keys (back with none), ground only, 6 feathers, a
  10-tick cooldown, 5 ticks where melee and projectiles miss, 0.4 hunger. Darktide +12 golden (Riptide Dash costs a
  dodge, a landed flurry refunds it, evasion Flow on our dodge: `DarktideDodges`, was `CombatRollCompat`), Web +20
  (each counter a dodge's worth), Corrupted mode +12 and every dodge blinks, Chrono +12 (Andy: the powers that had extra dodges get extra feathers). The client-side charge packets
  (`darktide_rolls`, `darktide_roll_charge`, `web_rolls`, `web_roll_charge`) are gone. HUD art from
  `tools/gen_feathers.py`. Combat Roll is out of the pack.
- **0.15.8 (Kitchen 0.5.1), ragdolls:** Andy wanted Sable-style ragdoll corpses and Ragdoll Reactions for players.
  Kitchen does the physics (see `kitchen/MODLOG.md`, `docs/ragdolls.md`); Powers only ties in, by reflection so it
  still builds against the old Kitchen jar: `KitchenCompat.isLimp`, no struggling free of a carry or catching a ledge
  while limp, no going limp while carried, Lifted, Gravity-held or webbed (`KitchenCompat.registerRagdollVetoes`), the
  limp pose counts in `PoseTakeover.posed`, and the symbiote looks for a fresh corpse within 5 blocks (was 3), since
  ragdolls tumble.
- **0.15.9, Better With Mods batch 1 and everything metal melts (Kingdoms 0.7.108, Kitchen 0.5.2):** Andy picked
  BWM ideas (MIT, 1.12 Forge; our own Fabric code, plan in the "Better With Mods: what fits our pack" doc) and asked
  that "ALL metal items added to the pack ... melt right back down into their crafted material's metals".
  `tinkering/smeltery/MeltingRecipes` works out every crafted item's metal from the recipe book after each reload
  (lowest recipe wins, fixpoint, base metal forms excluded), reads `data/*/tinkering_melting/*.json` for
  uncraftable items (Kingdoms ships its paladin, gladiator and ronin sets and barding), and syncs the list to
  clients for JEI. `Melting.Result` can hold several liquids (a paladin helmet gives iron and gold); the smeltery
  waits for room for all of them. Fixed entries: horse armour 9 ingots, nautilus armour 7, chainmail 3/5/4/2,
  bell 4 gold, chipped/damaged anvil 20/10. `LOG_GAPS` logs metal-named items that still don't melt (netherite:
  no liquid yet). Also: the Mining Charge (`armory/MiningChargeBlock`; digs a 3x3x3 hole into the block it's on,
  harvesting drops, `CHARGE_DAMAGE`; WeaponMod dynamite recipe or a gunpowder one through Fabric load conditions),
  Arcane Scroll drops (`enchanting/EnchantDrops`, per-mob table in `data/jaymospowers/enchant_drops/`, Looting
  raises the chance), enchanted-book tooltips naming the gear an enchantment fits (`EnchantSlotTooltip`), and
  `core/ExplosionLog` (one log line per player-caused explosion).
- **0.15.11, free rein riding:** ships Jaymo's Kingdoms 0.7.110: in third person the camera turns freely and WASD turns
  your horse toward it, like Better Mount Steering (which has no 26.3 build). Also fixes a broken swing mixin that
  kept the game from starting.
- **0.15.12, fast run, slide, wall transitions, pole climb (after ParCool's ideas):** Andy liked ParCool's "breaking
  into a faster sprint after time spent running, and the climbing mechanics, like wall transitions, and running".
  ParCool is LGPL and Powers is MIT, so nothing was copied: these are our own moves, folded into `freerun`.
  - **Fast run** (`freerun/FastRun`, everyone): 40 ticks of sprinting gives +30% speed (Freerunner 1: +40%,
    Freerunner 2: after 25 ticks). It ends on a stop, a turn of 60°+, a hit, hunger ≤ 6 or water. Attacking keeps it.
    It costs a little hunger and no feathers, and shows a lean (`RunPose`) and dust.
  - **Slide** (`freerun/Slide`): in a fast run, hold Sneak for 4+ ticks (a tap is left for the dodge). You slide for
    15 ticks in the crawl pose, under 1-high gaps, and can jump out of it. The server confirms it before the pose
    changes.
  - **Vertical wall run:** jump at a wall in a fast run (or a sprint with Wall Runner) to run 2.5 blocks up it (Wall
    Runner: 3, then 4). At the top, Ledge's grab takes over; otherwise, with Climber, you cling.
  - **Wall transitions** (Climber, `freerun/FreerunWalls`):
    - a side wall run into a cling;
    - from a cling, Jump + Back/Left/Right hops to a wall up to 3 blocks off;
    - clings follow inside and outside corners;
    - from a ledge hang, Sneak + Back drops to a ledge 1-3 blocks lower;
    - hold Sneak + Forward at an edge for 6 ticks to hang off it.
  - **Pole climb** (everyone; tag `freerun_poles`): press Forward into 2+ stacked fences, walls, chains, end rods,
    bamboo or lightning rods. Climb with Forward/Jump (faster with Climber), slide down with Sneak, leap off with
    Jump + direction.
  - `FreerunClient.active()` covers slide, vertical run and pole, and `holding()` covers poles, so the dodge stands
    aside.
  - The server checks height, rise and fall rates, cooldowns and corners for each move.
- **0.15.16, Combat Roll's leftover HUD:** Andy saw the old roll indicators (Combat Roll's blue arrow and key hint
  beside the hotbar) still showing next to the feathers. Combat Roll left the pack in 0.15.5, but nothing took the jar
  out of the CurseForge profile, so it kept loading. `combat_roll` joins `removedIdsFromProfile`: the next Build Modpack
  moves it into `removed-mods`. The dark segmented bar above the hotbar is vanilla's empty experience bar.
- **0.15.19, free rein with Shoulder Surfing:** ships Jaymo's Kingdoms 0.7.113. Andy found the movement keys didn't
  steer the horse under free rein: Shoulder Surfing kept turning you back to its camera. Under free rein it now leaves
  the horse to the keys and its camera follows the mouse.
- **0.15.22, Emberborn Kindle (Kingdoms 0.7.117):** Andy: "expand on emberborn, with a left click hold ability, and
  synergies merging smoke power into fire somehow". Anyone of Emberborn blood holds left mouse bare-handed: flame pours
  from the palm in a cone growing 3 to 5 blocks, setting creatures alight through `core/Flames`, lighting campfires,
  candles and TNT, melting snow and ice, never setting blocks on fire; heat builds to white-hot in 3 s, and letting go
  at full heat throws a fireball (2.5-block burst, no block damage, 5 s cooldown). Cold halves it, rain only hisses.
  With the Smoke power: their body is a heat source for their smoke, the flame leaves soot, and flame meeting their own
  smoke sets off a flashover that races through the cloud. Plain left hold for Smoke users, sneak + hold with other
  powers (`RaceHoldClient`). All in `power/ember` and `docs/Ember-Ice-Plan.md`. Smoke puffs marked keep-others never
  take another owner's block (`SmokeField.add`).
- **0.15.22, Cryokinesis (ice power):** Andy: "smoke instead of fire" for the ice synergy. A new power (`power/cryo`,
  `docs/cryo.md`): R Ice Slide (skate with inertia; water ahead freezes into a frosted-ice bridge; wall crashes hurt by
  speed), G Frost Breath (8-block cone that slows and freezes, ices water for 30 s, puts out burning creatures), V Ice
  Shards (a fan of 5), B Flash Freeze (3 s packed-ice shell, never into a creature), X Ice Wall (5x3, 15 s, never into a
  creature), Z Snow mode (slow and blind, no hurt), C Blizzard (20 s whiteout, 16 blocks). C answers once every
  ability is mastered (`Power.ultimateAnswers`), since Cryokinesis has no fates yet. Frost-born users take no freeze
  damage and are stronger where it snows. Every placed block is recorded by `CryoIce` and melted back. Freezing fog:
  breath or Blizzard on smoke freezes it in place (`SmokeField.freeze`), slowing and chilling those inside, dropping
  snow under open sky; fire, lava or Kindle's flame and fireball thaw it. Known gap: ice records live in memory, so a
  server crash (not a normal stop) leaves the ice standing.
- **0.15.29, hidden blade visuals (Kingdoms 0.7.122):** Andy: the hidden blade should animate for players and be held
  in front so it's visible, not off to the side and lost in the costume. Springing it raises the arm into a guard ahead
  of you and runs the blade out of the housing; a blow with it is a straight thrust; the bracer stands clear of costume
  sleeves. Details in the Kingdoms MODLOG.

- **0.15.30, seasons reach the powers (Kitchen 0.5.6, Kingdoms 0.7.123):** Kitchen 0.5.6 brings the pack's own seasons,
  and Andy asked for every power to feel them ("all of em"). `core/Seasons` reads Kitchen's calendar by reflection, so
  it does nothing without Kitchen 0.5.6, and it only counts in temperate lands. Cryokinesis: `CryoPower.strength` is
  x1.2 in winter and x0.85 in summer. Ember: winter counts as chilled (`EmberKindle.chilled`), and in summer Kindle,
  the fireball and Flashover do x1.2 fire damage (`EmberKindle.seasonal`). Smoke: in winter `SmokeField` fades at
  x0.6 and rises at half rate, so it hangs low. Symbiote: its hunger drains 1.5x faster in winter. Darktide: in winter
  `DarktideFlow.night` starts 1500 ticks sooner and ends 1000 later. Client `SeasonAura` gives every power user an
  occasional seasonal mote (cherry petals, fireflies on summer nights, poplar leaves, snowflakes), and wraps a Wind
  user in a spinning whirl of them that thickens in flight. Side effect: Kitchen's winter chill makes temperate lands
  cold enough to snow, so `WindCold.isCold` (Wind's longer wind-ups and cooldowns) counts them in winter too.
- **0.15.31, downed means down:** Andy: "when players are downed, they should not be able to use powers, or really do
  anything but crawl" and "you shouldnt be healing to full health on the ground with no assistance". The server already
  dropped power-key presses, but the client still ran its own half and some 80 other ability packets went through.
  Now, while the local player is down, `DownedKeysMixin` makes every acting key read as up (Jump, Sprint, attack, use,
  pick, drop, swap hands, and every `key.jaymospowers.*`): held abilities let go, only moving, the A / D roll, Sneak
  (give up), looking, chat and menus are left. On the server the downed are a `Powers` silencer, `WindFlight.canFly`
  says no (Wind, Z Fighter and Smoke's vapour flight end), and going down calls the new `Power.onDowned` (Gravity,
  Chrono, Cryo, Darktide, Web, Smoke, Arsenal, Z Fighter, Wind and Resonance end what's running; cooldowns kept).
  `DownedHealMixin`: nothing heals the downed, whatever the source. Goes with Jaymo's
  Kitchen 0.5.7 (slower stomach regen that stops at 60%, the rest needs treatment; its jar still needs building into
  `libs/`). Breaks (set by a splint or a priest) and stumps (cautery, or villagers binding them) already needed tending.
- **0.15.32, bandit masks hide the face:** ships Jaymo's Kingdoms 0.7.125. Kingdoms' bandit mask and bandana (found
  by id, `costume/BanditMask`) hide the face like a costume: the wearer goes by "Bandit" over their head, in chat and on
  the tab list, or "Pirate" in or on the water, in a boat or on a ship, or on a sea, beach or river biome. They're
  tagged `jaymospowers_masked:bandit_<uuid>` (one identity per player), which Kingdoms reads like a costume's.
- **0.15.33, the symbiote's falls, meals and blob legs:**
  - **Falls:** bare of the suit, its catch looked 4 blocks down only every 10 ticks, so at speed it almost never saw the
    ground coming and you took the full fall. Now any bond it isn't sulking over takes no fall damage at all
    (`WebSymbiote.takesFalls`; the suit and Living Armor as before), its tendrils reach for the ground each tick
    (`reachForGround`, looking 3 ticks of the fall ahead) and goo splashes out on landing. Sulking, only the Cushion
    keeps a fall from killing you.
  - **Players are food:** with Jaymo's Kitchen, its hunger hunt goes after other players like any creature
    (`huntable`), kills them (a downed one has 10 hearts to get through) and eats the corpse; a player's corpse is
    `edible` now, and its hotbar spills on the ground first (`KitchenCompat.spill`). It still never feeds on anything
    living. Creative players and the white strain's targets are unchanged.
  - **Downed players have 10 hearts** (`Downed.DOWNED_PLAYER_HEALTH`, was 3): Andy wanted damage, not a count of ten
    hits, to finish them. A player's blow on a downed player is now ordinary damage out of those 10 hearts; downed
    mobs still count ten wounds.
  - **Its temper:** a player's body it would eat on its own (Living Armor feeding itself, a hunt), it stops over for 1.5
    seconds first (`HESITATE_TICKS`) with a warning and your screen darkening: throw it clear and it lets it be.
    Consume (you asking) doesn't wait. Asked the same thing again within 30 seconds (a flint struck, a locked ability,
    the suit while it sulks, an inedible corpse...: `nag`), it answers annoyed with more stress; a third time it's fed
    up and storms off with you 20 blocks (`stormOff`). A second flint is ripped from your hand and hurled well away.
  - **Letting go when stuck:** walking you somewhere (storming off, a sight, out of the heat or cold) and making under a
    block of headway in 3 seconds, it lets go of you there and then (`letGoIfStuck`) instead of holding on until the
    takeover ran out or you fought free.
  - **Blob legs:** a fleeing or seeking blob outran its tendrils (two could step at a time, 5 ticks a step), so they
    stretched metres behind it; and a blob tracked again restarted its clock under the same id, freezing every leg
    mid-step. Moving fast, tendrils now plant further ahead, step quicker and all at once; any stretched past reach
    lets go at once, and a restarted clock resets the legs (`WebBlobRenderer`).
- **0.15.34, costumes cover the MCA body for real:** Andy: "costumes still clip, almost all of the villager skin
  underneath is visible in motion". 0.13.26's looser suit didn't reach the cause. With MCA a player's body is drawn by
  MCA's own layers (skin, face, clothes, hair), each posed from a copy of the body taken as the layers start; when the
  models are finally drawn, our poses that add to a limb (Chrono's reel, Wind's kicks, a carry's give, Z-Fighter
  combos...) land on that copy a second time, and MCA never draws the model's root (Gravity's lean), so the suit and
  the skin went different ways. And MCA's animation model keeps its bust off the root, so the suit never had one while
  MCA drew its own through it. Now: the parts of the body the suit's textures cover whole aren't drawn under it, on the
  renderer's body and MCA's layers (`CostumeCover`, a hide just before each model is drawn, shown again after); the gi's
  bare forearms, the ragged cut's holes and the like still show the skin. Over an MCA body the costume takes the very
  pose MCA's layers use and drops the root as they do (`CostumeBody.mcaPose`, `Worn.beforeDraw`), and the suit's bust
  follows MCA's again (`CostumeBody.bust`). Heads untouched (masks have eye holes); first person unchanged.
- **0.15.36, Better With Mods batch 2: smithing (Kingdoms 0.7.128, Kitchen 0.5.8):** batch 2 of the BWM port (MIT,
  our own code; plan in the "Better With Mods: what fits our pack" doc). Andy: steel gets both a netherite tier and a
  trait; ore smelting in the furnace is slower.
  - Brick kiln (`tinkering/kiln`): no kiln block. A space walled on 3+ sides and the top by bricks, nether bricks or
    mud bricks, with heat under it (campfire/fire 1; soul campfire, lava, magma, a lit Forge Hearth 2), fires what you
    right-click into it (`KilnLoadBlock`/`KilnLoadEntity`, sneak for the stack). 8 crack stages, each 20 + 60*(8-S)/8
    ticks for S the summed 3x3 heat. Clay ball -> brick, clay -> terracotta, Unfired Pot (4 clay) -> decorated pot,
    log -> charcoal (+25% a second), netherrack -> nether brick (heat 2), grout -> seared brick (2), Crucible Steel
    Blend (2 iron + coal) -> steel ingot (2). Static table in `Kiln`; no JEI page yet.
  - Furnace: raw ores and ore blocks (`jaymospowers:slow_smelt`) take 4x as long (`SlowOreSmeltMixin`,
    `FURNACE_ORE_SLOWDOWN`). Blast furnace and Kitchen untouched.
  - Bellows (`tinkering/forge`): pump by right-click (a little hunger) or a Wind Whistle gust (`WindJet` hook); for 5 s
    the fire it faces counts +1 heat, and a smeltery controller or drain it faces melts twice as fast
    (`SmelteryEntity.blow`). Forge Hearth: coal/charcoal (up to 8) at heat 2, lit by flint and steel or a fire charge,
    or bare-handed by an Emberborn. Its fuel is lost if broken.
  - Steel: `Molten.STEEL`, alloyed 1 iron + 1 pig iron (the smeltery can't hold coal) or fired from the crucible blend.
    Ingot, nugget, block (c: tags), melting and casting like the other metals. `TinkerMaterial.STEEL`: 1500 durability,
    speed 7, attack 3, netherite tier, trait Tempered (25% of wear doesn't land, not stacking; +1 damage a level
    through armour on armoured targets). Art from `tools/gen_steel.py`, which must be re-run after
    `tinkering_assets.py`/`armory_assets.py` (likewise `tools/gen_smithing.py`).
  - Broadhead Arrow (`armory/BroadheadArrowItem`): 3 steel nuggets + stick + feather -> 4. 4 damage and a bleeding
    body cut from the recurve bow, 60% from any other bow or a dispenser. Flies as a vanilla arrow.
  - Linen bandages take `jaymospowers:bandage_cloth` (white wool or Kitchen's hemp cloth).
  - Not built with Loom or played yet; javac 0 errors on all three mods.
- **0.15.39, Gravity's B reworked (Gravity Wave, Inversion):** Andy: "rework my gravity B skill it would be a cone AoE
  infront of me shooting out a cone of gravitational waves, could carve up terrain as well, R + B = Pull target in and
  punch them in the gut, sending them away while inverting their insides causing bleed and a heavy slow status". Held
  and let go, B is now the Gravity Wave (`GravityWave`): a fan rolling out the way you look (4-16 blocks with the charge,
  35° wide, 14° tall) that hurts, throws and slows what it passes and carves the ground (softer blocks readily, harder
  with charge, claims respected). B's tap (force kneel, and the G+B launch) is unchanged; Collapsing Horizon's ring now
  rains where the wave ends. R+B, B while Lift holds a creature, is Inversion (`GravityGutPunch`): yanked in, gut-punched
  away, a Wounds cut on the body plus 6 s of inner bleeding, Slowness IV and Weakness. Tuning at the top of both classes.
- **0.15.41, the pack's dead cleared and robbed:** ships Jaymo's Kingdoms 0.7.132 (bearers, graves, mourning, looters,
  judgement for a looter carried to a guard) and Jaymo's Kitchen 0.5.9 (corpses can be robbed; `libs/`).
  Andy: "ALL HUMANOIDS go down, if they werent decapitated or otherwise prevented from the downstate": `Downed.downable`
  now downs every mob with the humanoid body plan (`BodyPlan.HUMANOID`: zombies, skeletons, illagers, witches, piglins,
  endermen, Kingdoms' folk, modded humanoids by shape), not only villagers, tagged folk and tamed animals. Beheading,
  `/kill`, the void, a totem and bosses still end it as before.
- **0.15.42, smoother bodies (Kitchen 0.5.10, Kingdoms 0.7.133):** Andy saw the animation preview and said "do all this,
  add a better crawling and downed animation too". New `client/PoseBlend`: each pose gets a weight per player on a
  slightly bouncy spring (it eases in and out, nothing snaps), and springs on how far the pose moves each part from
  vanilla's, so a jump between keys follows through with a small overshoot. Real time, not frames; a pose ending keeps
  its last shape while it eases out; entries go once eased out or 3 s unseen. It can also bend the upper body at the
  hips (forward, turned, tipped) and lower it, so poses use the torso, head and legs, not just the arms.
  - Ember's Kindle: the shoulder draws back first, then the arm pushes out with the body turned in, the free arm braced
    and the feet set; it trembles more as the heat builds; let go at full heat it whips over into the fireball throw.
  - Freerun: the run's lean eases in and out. The slide is no longer the swimming pose: you drop in on bent knees and
    lie back on one hip, lead leg out, a hand trailing the ground, the other arm up, eyes ahead, and pop back up into
    the run (vanilla's swim tilt is switched off for it, as for a carried body).
  - Carrying: the carrier bends at the hips and knees to pick up and set down, and walks heavier, swaying, the arms
    giving on each step.
  - Time Flow's jab, quick jab, hook, uppercut, lunge and barrage coil, then turn the hips and shoulders through with a
    step, overshoot a little and recover to a guard held between blows; the other moves are eased the same way.
  - Gravity's Lift grip: the arm's aim eases onto the target with the chest turned to it, a lean, the free arm out.
  - Ledges: the catch swings the body in under the hands and settles. Z Fighter's guard and wave beam ease in with a
    stance under them.
  - Crawling (`CrawlPose`, the gesture crawl and downed players): an army crawl, arms sweeping ahead along the ground
    and pulling back in turn, the opposite knee driving, the body rocking, the head lifting to look ahead; a broken
    limb drags (Wounds' breaks are synced to everyone) and the rest work harder. Downed players breathe hard, rest the
    head when still, try now and then to push up and sink back, and bounce as they hit the ground; passed out, they lie
    limp with the head turned aside. Downed humanoid mobs (`wounds/DownedPose`) fall with weight (the knees go, then a
    faster drop and a small bounce), crawl the same way, and on their backs breathe unevenly, clutch their worst wound
    (a broken arm or leg, else the chest) and try to sit up.
  - First person (Andy: "the big ones get first-person arms"): your own bare arm now plays Kindle (drawn back, pushed
    out palm-first trembling with the heat, whipped forward on the throw), carrying (both arms rise in under the load
    as what you held sinks away, bob with your steps, and sink out when you let go), Time Flow's blows (guard, coil,
    punch and recoil on the third-person timing, the barrage's alternating fists, the push and the counters' palm
    strike), Lift's grip (the arm reaching after the held creature, straining) and Cryokinesis (breath hand to the mouth
    then thrown out, the shard sweep, the two-handed freeze push, the wall's palms driving up, the Blizzard's arms
    raised). They ease in and out on the same weights as third person (`client/FirstPersonArms`,
    `PowerArmsFirstPersonMixin`); only an empty hand is posed, so held items, Better Combat's swings and the view bob
    are untouched, and with First-person Model the body's own arms show the poses as before.
- **0.15.42, Cryokinesis poses and livelier weapon animations:** part of the animation upgrade Andy approved (wind-up,
  follow-through, the whole body moving, eased springs).
  - Cryokinesis had no body poses; now every move has one, seen in third person by everyone (`client/power/cryo/CryoPose`).
    Ice Slide is a skater's crouch: torso low and forward, legs apart pushing off in turn as you speed up, arms out for
    balance, the whole body leaning into a turn (from the slide's own velocity for you, from movement for others).
    Frost Breath braces the legs and pushes the chest and head forward; the hand goes to the mouth, then is thrown out
    along the breath, trembling. Ice Shards is a wind-up and a fanned sweep across the body with a step and a twist,
    carried through. Flash Freeze draws both hands to the chest and pushes them out; the Ice Wall dips low and drives
    both palms up. The Blizzard raises both arms overhead and spreads them, swaying and shivering while it rages. The
    held poses come and go on small springs, so they ease in and out. The server now tells everyone which move is being
    made through a small synced `CryoMoves` attachment, like Smoke's.
  - Our 8 Better Combat weapon animations (sword twirl slash and whirl, axe flip chop, dagger flip, polearm whirl, the
    three staff twirls) are re-keyed in `tools/combat_animations.py`: a wind-up before the hit and a follow-through
    after, the head looking toward the blade, the torso in on every move, and a step or weight shift on the legs (the
    sword slash, axe chop, dagger flip and front staff twirl had none). Eased curves replace the straight ones; only the
    stretches inside a spin stay straight, so a twirl doesn't stop at every quarter turn. The hit's keyframe and every
    begin/end/stop tick are unchanged, so Better Combat's damage timing is the same. `tools/check_player_animations.py`
    checks every file parses and every key sits within the animation.
- **0.15.45, ships Jaymo's Kitchen 0.5.11 (Better With Mods batch 3, mills):** shafts, windmills and waterwheels that
  work Kitchen's horse machines, the village windmills of Epic Structures turning for real, a potter's wheel that
  throws clay into Tinkering's Unfired Pot (`jaymospowers:unfired_pot`, looked up by id), a bloody chopper, manure
  and fertile farmland. No Powers code changed. Details in the Kitchen MODLOG.
- **0.15.46, poses laid once on MCA bodies:** Andy: "down state doesnt look write, looks like the animation is drawn
  twice again, down arrow crawling is the same, and so is the slide". Since 0.15.42 every eased pose goes through
  `PoseBlend`, which adds its offsets to the model it's given. With MCA a body is drawn by MCA's layers, whose models
  take a copy of the already-posed body in their own `setupAnim` (`receivesDeferredAnimationPose`), and a costume over
  them takes the same copy; our model-feature hooks then added every pose a second time. `PosedCopy` spots those models
  (MCA's flag, read by reflection; `CostumeModels.Worn` when over an MCA body) and `PoseBlend.lay`/`bend` leave them.
- **0.15.48, the hidden blade's guard (Kingdoms 0.7.135):** Andy: the blade still wasn't held up in front. Fresh
  Animations (through Entity Model Features) was laying its idle arms back over the guard; now the guard goes on after
  every animation and EMF pauses for a player with a sprung blade. The guard is held still (only the walk sways it),
  and in first person your bare arm comes up into the guard with the blade pointing ahead. Details in the Kingdoms MODLOG.
- **0.15.51, Velox replaces Blur, and a new first-person band slam:** Andy asked whether Alien Evolution (KonaDolomite,
  All Rights Reserved, Palladium 1.20.1) could improve the Xenoform, "primarily add XLR8 in place of Blur", and to use
  its first-person slam. Nothing of it is copied: an original speedster and our own slam. Blur is now **Velox**
  (`forms/Velox.java`; the id stays `blur`, so bands keep it): 80% faster, runs on water while sprinting
  (`ChronoWaterRunMixin` asks `Velox.runsOnWater`); R held is a Speed Burst (+120% speed, whole-block steps, climbs
  walls on contact, up to 6 s); G Rapid Punches (6 hits of 2 on what you aim at); V Cyclone (everything within 6 swept
  round you and lifted); B Chain Strike as before; the Ultimate is Standstill (6 s: everything living within 24 nearly
  frozen, projectiles hang at 5% speed and fly on after, you get Speed II; again every 30 s). Corrupted, a burst that
  ends runs on until a wall or a creature. A new body and skin: crest, a visor that drops over the eyes at speed, snout,
  ball feet, longer tail, dark blue with white chevrons. Every Xenoform area or aimed move now spares your own pets and
  scoreboard teammates (`XenoForm.spared`). The first-person slam is rebuilt (`XenoformSlamView`): forearm up across
  the view with the band face up, the dial pops as a glowing cap, the palm drops onto it, a dip, a view kick and a
  green burst. Compile-checked against the 26.3 jars only; not yet run in game.
- **Duplex, a duplicating alien from copper golems:** Andy asked for a "Ditto" Xeno using copper golems. Like Velox it
  takes no borrowed name: **Duplex** (`forms/Duplex.java`, id `duplex`, scanned from a copper golem). 0.9x, 18 hp, fists
  3. R Split: a double steps out (`XenoDouble`, 6 hp, fists 3, never saved; it pops when you stop being Duplex or leave),
  up to 3; doubles go for what you hit and what hits you, and a loyal one's death costs you 2. G Rally (aimed target
  within 16, or come back), V Switch (trade places with the aimed or farthest double), B Merge (doubles pop back into
  you, 3 health each). Ultimate Copper Storm: fills to 3 doubles, visual-only lightning on each, 6 damage within 3 of a
  double, doubles charged (Speed II, Strength, Glowing) for 20 s, up to 6 doubles meanwhile; again every 20 s.
  Copper body with seams, rivets and verdigris, a swaying
  lightning rod, pale cyan eyes (`XenoFormModels.Duplex`, `gen_xenoform.duplex`, `XenoDoubleRenderer`). A complete band
  is now 9 aliens; anyone already ascended stays ascended. Compile-checked against the 26.3 jars; not yet run in game.
- **Duplex's name counts, and corruption is just the wrong alien (same release):** Andy asked that the name change as you
  split (Duplex, Triplex, Quadruplex) and that corruption be only "a different xeno" across the board, with no per-alien
  method for now. Duplex is called by how many of you there are (`Duplex.title`: shown over the hotbar on each split,
  merge or death, and on the HUD clock, which counts your doubles from their synced owner); rogue doubles are gone.
  Every alien's corruption twist is removed (Bulwark, Pyre, Velox, Prism, Wisp, Gremlin, Knell, Wyrm), with their
  `CORRUPT_*` constants, `Live.runaway`/`wingStall`, the chat line on changing and the `.corrupt` lang keys. A corrupted
  band still gives the wrong alien on 1 slam in 4 and changes you on its own; `XenoForm.corrupt` is an empty per-tick
  hook for later. Compile-checked against the 26.3 jars; not yet run in game.
- **0.15.53, R belongs to the dragon (Kingdoms 0.7.137):** steering one of Kingdoms' new dragons (Notus first), the
  first ability key no longer fires your power: R is the dragon's fire breath. Told by the mount's id
  (`jaymoskingdoms:*_dragon`), so Powers still loads without Kingdoms (`JaymosPowersClient.steeringDragon`).
- **0.15.54, Wind lands softly, wobbles early, and the storm wakes the Stormborn (Kingdoms 0.7.140):** Andy's list:
  - Falls: "i have a clip where the user slowly descended and still took the damage. It shouldnt work this way past
    mastery 2, and even then the damage should scale lower related to the speed." From 2 unlocked abilities a Wind user
    takes no fall damage at all; before that a fall's damage is scaled by how fast they were really coming down
    (the server's own fastest sink of the last few ticks, easing off as they slow): nothing below 0.45 b/t (the cloak's
    drift, a hover sink), in full from 1.5 b/t (`WindFall`, `WindFallMixin` on `calculateFallDamage`).
  - Ragdolls (Kitchen's living ragdolls): flying, or within a second of it, a Wind user can't be knocked limp, so
    catching a fall with the flight never ends in a ragdoll (a Kitchen veto, `WindFall.holdsUp`). Lying limp, R only
    takes off from mastery 2, and doing so lifts you straight out of the ragdoll; before that the wind won't answer
    until you're up (client and server both, `WindFall.canTakeOff`).
  - The cloak's drift before mastery 2 is unsteady: arms windmilling out for balance, legs kicking, the body rocking
    (`WindWobble`, synced by `WindModes.WOBBLY` so everyone sees it).
  - No blossom petals in a Wind user's seasonal whirl (`SeasonAura`: spring gives Wind none).
  - Kingdoms 0.7.140: the Stormborn heritage is earned now, woken when a Wind user's power is corrupted.
  Compile-checked against the 26.3 jars; not yet run in game.
- **0.15.56, ships Jaymo's Kingdoms 0.7.143 (Better With Mods batch 4, building):** timber trim, benches and tables,
  wattle panels, strung plot stakes, earth slabs with half-step King's Roads, wind chimes and a rope lift. No Powers
  code changed. Details in the Kingdoms MODLOG.
- **0.15.57, ships Jaymo's Kingdoms 0.7.144 and Kitchen 0.5.12 (Better With Mods batch 5, atmosphere):** Gloom with
  Blood-Moon-only damage, the beacon watch-light, skittish herds, fish bait and the exploding pot. This finishes the
  BWM port. No Powers code changed. Details in the Kingdoms and Kitchen MODLOGs.
- **0.15.58, the Aether II's Holy Isles; Wind and Arsenal ways in:** Andy tied the Aether into the pack's lore (Kingdoms
  0.7.145's monoliths and Giants are its last remnants), wanted Wind to be the way there, as Chrono's Fast Forward is to
  the future, and then: "i need you to do whatever you can to replicate that dimension [The Aether II] for us. all our
  same entry methods, just with the proper dungeons mobs and blocks." So `jaymospowers:aether` is now a clean-room remake
  of The Aether II's Holy Isles (names, numbers and behaviour only; every line of code, JSON, texture and structure is
  ours, painted and built by `tools/gen_aether*.py`). See `docs/aether.md`.
  - **Ways in:** a Wind user with B unlocked who flies to the top of the Overworld rises into the Aether
    (`power/wind/aether/WindAether`) with everyone within 4 blocks; falling out of its bottom drops anyone home at the
    same x/z, 48 blocks above the ground on Slow Falling. Arsenal users mark places (Sneak+R) and open standing portal
    pairs to marked or recent places from a radial menu (Sneak+G), up to 3 pairs, each lasting a Minecraft day
    (`ArsenalPlaces`, `ArsenalStanding`). Emberborn Arsenal rifts now and then (30%) tear into the Nether instead of the
    pocket (`ArsenalEmber`); after the first escape, Sneak+Z toggles pocket/Nether.
  - **The world:** y -32 to 384, big lowland islands with caves and the Hestveil caverns, a broken archipelago above
    y 128, a cloudbed below; 16 biomes in four zones (Highfields, Magnetic, Arctic, Irradiated) with their trees, ores,
    aerclouds in six colours, ice spikes, ferrosite spires, moss, lakes, huge magnetic shrooms and moa nests; day and
    night like the Overworld.
  - **Blocks:** about 380 new: undershale, agiosite, ichorite, ferrosite and their building families, arctic snow and
    ice, arkenium/glint/corrobonite/quartz ores, scatterglass, cloudwool in 17 colours, greatroot/wisproot/amberoot and
    guardian woods, ten tree types, flowers, fruit, arilum, moss, mushrooms, vases, campfires, the altar.
  - **Mobs:** 11 monsters (Zephyr, Tempest, Cockatrice, Blue and Golden Swet, Aechor Plant, Carrion Sprout, Skephid,
    Arkenium and Gravitite Taluton, Bladeshroom Hunter); 8 new animals (Kirrid, Burrukai, Taegore, Skyroot Lizard,
    Prismallard, Glitterwing, Shroudwing, Aerwhale) and the Moa, Phyg, Flying Cow, Sheepuff and Aerbunny brought to the
    Aether II's rules (valkyrie wings to ride, moa nests, eggs and stamina).
  - **Dungeons and structures:** the Sentry Ruins with the Slider boss (pickaxes only), sentry traps and spawners,
    mimic crates; outposts (campfire respawn), camps, watchtowers, animal dens, Veradexian ruins, library and aqueduct,
    the ancient henge, Brexallen ruins, the undercloud mineshaft, irradiated remnants, the infected guardian tree.
  - **Gear:** skyroot/holystone/zanite/arkenium/gravitite tools and hammers, six armour sets with set abilities, four
    aercloud gliders, the ambrosium altar.
  Compiles against the real 26.3 jars; a dev server boots, generates the Aether and places its structures. Not yet
  playtested in a client.
- **0.15.59, the Z Fighter's ki aura:** a faint aura of his own ki colour now hugs every Z Fighter standing in base
  form, more often and bigger the further his max ki has grown (a step every 40 past 100, up to 4); a form's aura roars
  a little harder with power too, and the base-form charging/flight motes take his ki colour instead of the lineage's.
  Charging to full ki flashes the aura once: a blast round his feet, a ring of ki, the ground torn up and thrown out,
  the TNT boom and a light shake. A Warrior-born screams into every form (a yell, a roar under it from Super 2 up, and
  one of three lines per form in nearby chat). Tuning at the top of `ZFighterPower`. Combo animations untouched.
  Compile-checked against the 26.3 jars; not yet run in game.
- **0.15.64, the band's slam on your own body in first person:** Andy's clip showed the slam's drawn first-person
  arms floating over his MCA body (First-person Model draws it). With First-person Model showing your body, the slam now
  plays on the body's own arms (MCA skin and all) and the drawn hands are gone (`XenoformFirstPersonMixin`): the forearm
  comes up level across the chest, rolled so the dial faces up at you (`XenoformSlap.FP_WRIST`, slerped), the right
  hand hangs above it and comes down on the dial, and the band's dial pops up and glows on the body's band
  (`XenoBand.Layer`). Its arms are shown while it plays (`XenoformLivingEntityRendererMixin`). Without First-person
  Model the drawn hands play as before.
- **0.15.64, Duplex the size and build of a copper golem; doubles look like Duplex:** Andy asked that Duplex and its
  doubles be a copper golem's size and build, and reported the doubles drawn as the default Steve skin. The Steve skin
  was the game's own: `EntityRenderDispatcher.getRenderer` hands every `AvatarRenderState` to the player renderer, so
  `XenoDoubleRenderer`'s own model and texture were never used and the state's default skin showed. The double's render
  state is now filled like a transformed player's (form `duplex`, body hidden via `XenoformClient.hideBody`) and
  `XenoformLayer` draws it exactly as it draws its owner. `XenoFormModels.Duplex` is rebuilt on `CopperGolemModel`'s
  boxes (8x5x10 head with the nose, 8x6x6 body, 3x10x4 arms to the ground, 4x5x4 legs, rod and ball tip) plus the belt,
  at the golem's own 16 px and drawn up from the feet by `FormModel.renderScale`. `Duplex.SCALE` 0.55 (was 0.9) gives a
  copper golem's ~1 block box; `XenoDouble` takes the player's box at that `Attributes.SCALE`, so player and doubles
  are the same size. New skin layout in `gen_xenoform.duplex`; the first-person arm is placed at the player's shoulder
  for every form (`XenoformHand`). Compile-checked against the 26.3 jars; not yet run in game.
- **0.15.63, ships Jaymo's Kingdoms 0.7.149:** players go by the character name they picked at the start (MCA's
  creator) in place of their username: chat, death and join messages, name tags, the tab list, Jade and Xaero's
  Minimap (details in the Kingdoms MODLOG). Powers: Xeno doubles and Gremlin clones take the player's display name.
- **0.15.62, ships Jaymo's Kingdoms 0.7.148:** outlaws and disguised players are spared by barbarian
  camps and pirate coves, and with MCA their raiders and deckhands are MCA villagers (details in the Kingdoms MODLOG).
- **0.15.61, ships Jaymo's Kingdoms 0.7.147 (Nether faction stage 3):** Hornkin portal raids on towns, Nether warlord
  bounties, Hornkin trophies and the Emberborn gold truce (details in the Kingdoms MODLOG). Powers: Andy's ruling,
  Z Fighter tyrant raids (`ZFighterRaids`) now only come to a kingdom with a player near it in world tier Borderlands
  (the second) or higher (`ZFighterKingdoms.worldTier`, read from Kingdoms' `WorldTiers`).
- **0.15.69, the symbiote saves you when you go down:** Andy's asks. Go down with a symbiote and it gets you up at half
  health, says how close that was (naming what felled you), leaps as high as it can, zips away from the fight and hands
  you back the controls; it refuses only starving with a bond under 30. It now comments on what you're fighting (18
  kinds). It never stops moving underwater (Wall Crawl tendrils, sprint-swimming takeovers), and takeovers stall less:
  quicker stuck checks, a ledge or wall between it and its prey counts as stuck, wall climbing, circling its prey, pressing
  on with no path. Taking the suit off is now only sometimes fought (60% at no bond to 30% at bond 70), up to 3 times a
  bond. Takeovers are in the plain black suit, the big body only 10% of the time (3% in non-web hosts, 40% for the anti
  one). See docs/web.md.
- **0.15.72, Thunderclap and Sunflare, ships Jaymo's Kingdoms 0.7.154 (Stormborn and Dawnborn buffs):** Andy's
  picks. The two heritages get a left hold of their own, like the Emberborn's Kindle (`power/heritage/HeritageMoves`,
  held through `RaceHoldClient`, sneak + hold beside another power). Hold bare-handed 1.5 s to charge (a bar over the
  crosshair), let go when full. Thunderclap (Stormborn, open sky only): lightning where you look, out to 48 blocks
  (the first creature or block); a Stormborn is immune and charged by it, so it can be called at your feet. 60 s
  cooldown, 20 s in a thunderstorm. Sunflare (Dawnborn, by day): a flash 6 blocks round that sets the undead alight
  for 8 s, blinds other mobs 5 s and heals other players and your pets 2 hearts; 60 s cooldown. Charging, a Stormborn
  raises a hand to the sky and a Dawnborn cups both hands out in front.
- **0.15.74, the dodge rolls again:** Andy: "shift double tap doesn't animate a roll anymore". It never had one of
  its own: the roll was Combat Roll's animation, and our dodge (0.15.5) only hopped once Combat Roll left the pack.
  Now a dodge (double-tap Sneak or Left Alt) tucks into a ball, drops low and tumbles one full turn the way it goes
  (forward roll, back roll, or over the shoulder to either side) over 0.5 s (`client/stamina/DodgeRollPose`, tuning at
  the top). Everyone sees it: the server syncs the roll (`Dodge.ROLLING`), and your own client draws it at once. Your own
  body in first person (First-person Model) stays upright. In first person the view tumbles with the roll and dips
  toward the ground (Andy's ask; `VIEW_ROLL` turns it down, 0 switches it off).
- **0.15.80, ships Jaymo's Kingdoms 0.7.160 (no usernames left on show):** Andy: "usernames should only show up for
  commands, it still appears in scoreboard and over characters and it shouldnt. especially when playing duplex".
  Anyone who kept their username in MCA's creator now gets a character name (details in the Kingdoms MODLOG). Duplex's
  doubles (and any Xeno double) follow the name their owner shows each half second, so a name given or a costume put on
  after the split shows on them too.
- **0.15.83, Chrono's Rewind gets you up; no Overclock fall damage:** Andy: "chrono should be able to use R when
  downed, this will revert him as it should". R stays live while you're down, but not after you've passed out (`Power.usableDowned`, let
  through by `DownedKeysMixin` and `PowerEvents`). While you're down, Chrono's history holds still. A Rewind then takes
  you back to the 3 s before the fall, on your feet (out of any carrier's arms), with the health you had then (at least
  30%), and not battered (`Downed.undo`). Andy: "chrono should not take fall damage when using overclock": no fall
  damage while Overclock runs.
- **0.15.85, OmniCrossbow:** OmniCrossbow (LGPL-3.0, by ThePotatoArchivist; 26.3 build 1.1.4) joins the pack as an
  untouched jar (`packModsOverride`): any item loaded into a crossbow from the off hand flies its own way. Jaymo's
  Kingdoms 0.7.162 makes it Survival's Make Do perk, for every class, and takes the Omni enchantment out of the game.
- **0.15.86, symbiote hesitation:** Andy: "change the hesitation on eating players back to 3s. the messages are good
  maybe add a few more variations to the pool". A player's body in your arms makes the symbiote stop for 3 s again
  (`WebSymbiote.HESITATE_TICKS` 60) before it eats; 9 hesitation lines (was 3) and 4 for throwing the body clear (was 1).
- **0.15.88, downed: limp body, arms dragging (Kitchen 0.5.13):** Andy: "legs and torso should go limp when downed ...
  animate arms dragging you while downed instead of the float we do now". Downed, the legs trail limp and the torso
  only sways a little with each pull; the arms haul you hand over hand, the chest lifting with each pull, fast enough
  that the slow downed crawl reads as dragging (`CrawlPose`, also downed mobs that crawl). Holding Sneak while downed (to
  give up) no longer bends the upper body into the ground (vanilla's crouch); it plays the push-up struggle instead.
  Andy: "crippling should only activate if the legs are broken, not just on any hit": Kitchen's leg hits now knock down
  and lame only a creature with a broken leg (Wounds' break tags, checked at the end of the tick).
- **0.15.89, downed in water: limp and floating (Kitchen 0.5.14):** Andy: "downed players underwater are able to swim
  as of right now, this should change. you should just ragdoll, and float". Downed in water (`Downed.adrift`), the
  moving keys go dead as well (`DownedKeysMixin`), and Kitchen's ragdoll holds you limp (`KitchenCompat.registerRagdollHolds`,
  Kitchen's new `LivingRagdolls.addHold`): the body floats up and bobs at the surface until you're up, given up, carried
  out or washed ashore (then back to the downed crawl). Without Kitchen you just drift up (`CrawlTaps`). Sneak still
  gives up.
- **0.15.93, blood (Iron's Simple Blood):** Andy asked for Iron's Simple Blood (Iron431, MIT, NeoForge only, no 26.3
  Fabric build), merged into our own blood (`BladeBlood`) rather than shipped as a mod, plus "the scaling splatter /
  puddle for heavier blunt impacts, like the anvil or mace impacts". Its particles are ported in `core/blood`
  (`BloodParticles`: a `Spray` of drops and a `Puddle`) and `client/blood` (`BloodDrop`, `BloodPuddle`, `BloodDecals`:
  the puddles' own particle group through Fabric's `ParticleGroupRegistry`, its render state a `QuadParticleRenderState`
  handing vanilla ready-made vertices so a puddle lies over the block tops under it); its sprites are in
  `textures/particle/blood`, notice in `licenses/LICENSE_simple_blood`. Our rules stay: blades bleed, crushing
  bursts (now with pools), the bloodless never bleed. New: a heavy blunt blow (`BladeBlood.heavyBlunt`: a falling anvil
  or block, a mace's smash, a `Fractures.blunt` weapon, 6+ damage) sprays half again a blade's drops and lays 1-5
  pools that widen with the damage; killing blows bleed too (`ALLOW_DEATH`, since `AFTER_DAMAGE` misses them); Wounds'
  open cuts drip small drops that leave a trail (`BladeBlood.drip`). Blood colours by tag (`tags/entity_type/blood/`:
  ender, dark, slime, bug). Not playtested; compile-checked against the 26.3 jars.
- **0.15.94, wounds first, hearts after (Kitchen 0.5.15):** Andy: "everything should go through the wounding system
  first, for example if a certain threshold (30%) damage to your limb goes through, then it should affect your hearts
  ... regeneration should only kick in at full speed when you are fully patched up". Every hit now lands on the body
  parts before vanilla takes any health (`WoundsHurtMixin` and `WoundsPlayerHurtMixin` around `setHealth` in
  `actuallyHurt`, `Wounds.takeHit`). The first 30% of a part's pool (`Wounds.HEART_SPILL_FROM`) is bruising and costs no
  hearts; past it the hearts drain so that a part at zero has cost its whole share (`Wounds.hearts`; never more hearts
  than before for the same damage). Head or body at zero still downs or kills. Meals and food raise deep wounds only to
  the bruise line; bruises mend over 3 minutes (`BRUISE_MEND_TICKS`). Until you're patched up (`Wounds.patchedUp`: each
  part hurt past bruising dressed or splinted, nothing bleeding, festering or broken unset) natural healing runs at
  `UNPATCHED_REGEN` (a quarter): vanilla food (`WoundsRegenMixin`) and Kitchen's meals (the `jaymospowers_unpatched` tag).
- **0.15.95, ships Jaymo's Kingdoms 0.7.164 (dark nights keep shaders on):** Andy: "my players want shaders". Dark
  nights no longer switch Iris shader packs off: the sky light each pack gets is scaled by the moonlight instead, so a
  new-moon night stays dark with shaders on (True Darkness Compat's idea, remade). See Kingdoms' MODLOG.
- **0.15.97, Velox's Velocity (R), after Chrono's Overclock:** Andy: "Velox should have a velocity button like
  Chrono's overclock, giving him the same toggle speed wall run and streak, just colored light blue". The held Speed
  Burst on R is now **Velocity**, a toggle: on, Velox is +100% faster (`Velox.VELOCITY_SPEED`) and runs on Overclock's
  own speed code (`ChronoSpeed.tick` with the new `ChronoSpeed.SKY` palette): Overclock's sprint boost and whole-block
  steps, the wall run held against the wall you face (`ChronoSpeedClient`, now also ticked by `XenoformPowerClient`),
  water and lava run while sprinting, Shift for walking speed, no fall damage, and the speed-force streak
  (`ChronoStreak`) in light blue: afterimages of the raptor (`XenoformClient.submitGhost`), sky-blue bolts, a blue dust
  trail. R again switches it off, then 2 s. `ChronoSpeed.FLASH` now holds the streak's colour (an int, not a boolean).
  No phasing through walls: that stays Chrono's.
- **0.15.98, finishers on the downed:** Andy: "every weapon should be able to right click and hold a downed enemy, and
  then do a unique finisher animation". Hold use (not sneaking) on someone downed for 0.4 s and you finish them with a
  move for the kind of weapon in your main hand (`core/finisher/Finishers`, read off item tags and names so forged and
  other mods' weapons fall in): sword (Run Through), greatsword (Headsman's Stroke), dagger (Mercy Stroke), axe
  (Executioner, the head comes off first), mace/hammer/club/pickaxe (Skull Crusher), spear/trident (Pinned), polearm
  (Reaping), staff, bow (Point Blank), crossbow, and anything else or bare hands (Neck Snap). You stand rooted facing
  them; blows land on the move's beats (sound, Simple Blood's blood or a crush, the body jolting, screen shake) and the
  last one is the death, credited to you (`Downed.execute`). Stops, sparing them, if they get up, are carried off or
  end up out of reach. Drawn for everyone by `client/finisher/FinisherPose` (keyframes eased on `PoseBlend` springs,
  the body turned to them with a step in, the weapon turned in the hand by `FinisherItemInHandLayerMixin`); your own
  is watched from behind, first person given back after. Not on tamed animals. Medicine in hand still treats; a
  red-hot blade only counts as medicine with sneak. A tap on a downed guard still has Kingdoms disarm them.
- **0.15.99, treating each other:** Andy: "players should be able to use medical items on each other, to bandage and
  splint/ heal their team. you can hold right click with the item on them and it will play and kneeling interaction
  animation, then apply after a short hold. using meds also increases your survival skill". Treating another body
  (PR #366's channel) is now a hold: hold use on someone with medicine and, past a tap (`Downed.TAP_TICKS`), you drop to
  one knee at their side and treat their worst part (salts, leeches and poppy: the whole body); let go and it stops
  (`Treatment` hold sessions). A tap with a bandage, rag, splint or needle opens the body screen to pick the part, as
  before. The kneel is the synced `Tending` attachment, drawn for everyone by `TendPose` (one knee down, right foot
  planted, both hands working at the wound; bent well over someone lying down, upright for someone standing), and a
  food revive of a downed one kneels too. Every treatment finished still gives Kingdoms Survival experience
  (`MedicineSkill.treated`). Medicine in hand means treat; a weapon on a downed enemy is a finisher (0.15.98) (a red-hot
  blade is medicine only with sneak).
- **0.15.102, Duplex's swarm and fetching:** Andy: "duplex' clones should deal more damage the more attackers in the
  swarm, and be more aggressive, surrounding the target and attacking one by one, right after one another, making it a
  violent onslaught. He also needs another ability, where you can select a block you are aiming at, and a clone will
  come pick it up and carry it, works for every solid block unlike our current carry system". Doubles fight as a swarm
  (`XenoDouble.SwarmGoal`, replacing the melee goal): each double on a target takes its own place in a slowly turning
  ring round it, and they strike in turn, one right after another (every 7 ticks less one per double, never under 3),
  each with a lunge, crits and sparks; every blow gets 35% more damage per extra double (3 doubles: x1.7, 6: x2.75)
  and isn't stopped by the last one's hurt time. They hunt (`Duplex.quarry`): idle doubles join a mate's fight, go for
  whatever hurts you or is hunting you or a double, what you hit in the last 5 s, or the nearest hostile you can see
  within 12 (not calm neutral mobs). Sneak + G, Fetch: the nearest free double walks to the solid block you aim at
  (16 blocks) and carries it off on its head (any block with a body, not just `CarryBlock.carriable`; never liquids,
  two-block blocks, double chests or unbreakable ones; only where you could break it; a chest keeps its contents);
  sneak + G again and it sets it down where you aim, turned as if you'd placed it. A carrying double doesn't fight and
  follows you; it lets the block fall if it pops, merges or dies, or the server stops. `CarryBlock` gained public
  `takeOut`, `liftableAny`, `land`, `place(..., Direction)` and `facing(..., Direction)` for it.
- **0.15.103, the band's hologram:** Andy: "the xeno band first person drawing is a bit cluttered, it should also display a
  hologram projection from the band showing which xeno is selected". While you hold Z as yourself, the dialled alien
  now stands over the band's dial as a small see-through green hologram (`XenoHologram`): upright on screen whatever the
  wrist's roll, turned to face you with a slight sway, flickering, red while the band would refuse the change
  (recharging, or a hidden alien not yet earned). It rises with the dial's cap and sinks as the hand comes down, so it
  follows the scroll live. Decluttered: the poised right hand waits up and off to the right instead of right over the
  dial (`XenoformSlamView.R_POS_POISED`), and the worn band's texture lost its dark outline round every face and its
  grain (lit top row, shaded bottom row, one small round dial; `tools/gen_xenoform.py band_worn`). Not playtested;
  compile-checked against the 26.3 jars.
  Behind the hologram, light fans up out of the band (`textures/entity/xeno_holo_glow.png`, a soft gradient on a card
  facing you), tinted and flickering with it, red with it. And the band now flashes when you change shape (slamming in,
  or turning back for any reason): everyone within 10 blocks who can see it is blinded for half a second
  (`BandFlash`). Players get a bright green whiteout on screen (`XenoNet.Flash`, `BandFlashHud`; weaker further off or
  looking away), creatures get Blindness, drop their target and stand dazed (Darktide's stun). You aren't blinded by
  your own band.
- **0.15.104, Nadir's Rift, Graviton and Command:** Andy: "can you add the rift, graviton, and command mechanics to
  Nadir?" (from The Awakening, which is all rights reserved, so these are from-scratch takes on its ideas). G is the
  Rift (`nadir/NadirRift`): tap at a creature for Recursive Drop (floor and sky maws, 7 passes of 2 damage), tap at
  nothing to blink 16 blocks, hold to Surface, sneak + G for the Void Pocket (`NadirStash`, 27 slots). V is the
  Graviton (`nadir/NadirGraviton`): Heavy Hand at a creature (tap yank / hold slam and pin; sneaking, shove / launch),
  a Weightless zone or (held) an Event Horizon black hole at nothing. B is the Command (`nadir/NadirRules`,
  `NadirRulesScreen`): three WHO + WHAT + WHEN rules, two on at once, saved on the player. Nadir's scratch is
  `Live.nadir` (`NadirState`). Details in `docs/xenoform.md`.
- **0.15.105, ships Jaymo's Kingdoms 0.7.165:** the character name typed in MCA's start-up creator now sticks the
  first time (details in the Kingdoms MODLOG).
- **0.15.106, ships Jaymo's Kingdoms 0.7.166:** hoods and the goblin disguise name you on the tab list and over your
  head too, and a bandit mask's name reaches the tab list (details in the Kingdoms MODLOG).
- **0.15.107, the band shows, and its hologram keeps its size:** Andy's clip: "the watch is obscured, the pov is drawn
  way too large. and it isnt visible from third person". With First-person Model your own arm carries the band right
  under your eye, so the hologram and its glow (sized for the drawn slam's wrist about a block away) filled the screen.
  They're now scaled with the band's distance from your eye (`XenoHologram.REF_DISTANCE`), so they look the same size
  either way. And the band was hidden under costumes and the symbiote suit (it skipped any covered arm, first and third
  person): it's now drawn over them, grown round the arm to 5.5 pixels (`XenoBand.GROW`), and further over a chestplate's
  sleeve (`ARMOUR_GROW`). Not playtested; compile-checked against the 26.3 jars.
- **0.15.108, a kinder ledge grab, the prone climb, and staying down after a slide:** Andy: "make the ledge grab more
  forgiving, and allow for climbing into one block gaps by going prone while holding a ledge, it animates you climbing up
  and onto your belly. along with the slide, holding the slide keeps you in the prone stance". The catch: forward alone
  (no Jump held) is enough, the edge may be up to 0.45 above your eyes (was 0.1), the wall 0.5 off (was 0.35), any
  facing that's nearer the wall than its corners (45°, was 40°), and you can already be falling at 0.6 b/t (was 0.2).
  Ledges under a gap one block high can now be caught (`Spot.stand` / `Spot.onBelly`). Hanging, the Crawl key (or Jump
  where there's no room to stand) pulls you up and in onto your belly (`Ledge.PRONE_*`, 20 ticks): the crawl begins with
  it, the flat body lifted so the eyes stay put, and you end it crawling; it used to drop you off the ledge, and where
  there's no room to lie it now does nothing. `LedgePose` draws the arms pulling and reaching ahead, a knee to the edge,
  and the tip forward into vanilla's crawl tilt so `CrawlPose` takes over smoothly. A slide that ends with Sneak still
  held leaves you prone (`CrawlTaps.goProne`); let go of Sneak to get up (once there's room), or use Jump or the Crawl key
  as usual. Not playtested.
- **0.15.109, blood doesn't twinkle (Kingdoms 0.7.169):** Andy: "the blood particles currently twinkle like redstone,
  they should just stay static if anything". All the blood still drawn as vanilla dust (which shimmers through its
  frames and shades like redstone) is now `BloodParticles.Fleck` (client `BloodFleck`): one of Simple Blood's drop
  sprites in a steady colour that drifts down and fades. Covers severing and stump drips (`Dismember`, `SeveredPart`),
  the symbiote's meals, Web's morph and tendril blood, Gravity's gut punch and a cut tail; the redstone-block gore
  flecks in `BladeBlood.crushed` and `Dismember` are blood flecks too. Kingdoms (Rend, vampires and Night Bleeders,
  blood scent, the Lycan tear) gets it through `PowersCompat.blood` (Powers' `jaymospowers:blood_fleck_red` by id,
  plain dust without Powers). Compile-checked; not playtested.
- **0.15.110, ships Jaymo's Kingdoms 0.7.170 (finishers: bare hands tap, weapons from Combat Arts):** Andy: "make the
  execution only possible with bare hands, and only activated after tapping the target three times. this is to prevent
  accidental neck snaps. Also make all the other executions unlocked in the combat arts tree replacing the current
  executioner function". The neck snap now takes an empty hand and three quick taps on the downed (each a fresh press,
  within a second of the last; a hold counts for nothing), and anything that isn't a weapon finishes nobody. The
  weapons' finishers (still a held use) need Kingdoms' Combat Arts perk Executioner, which no longer boosts a combo's
  finisher (`Finishers.weaponsUnlocked`; everyone in creative, everyone if Kingdoms isn't loaded).
- **0.15.111, hitting a corpse splatters its blood (Kitchen 0.5.17):** Andy: "attacking corpses when they are on the
  ground should splatter blood especially with blades". Kitchen tells Powers of every blow or arrow that lands on a body
  with flesh on it (`CorpseEntity.addOnHit`, registered by `KitchenCompat.registerCorpseBlood`), and
  `BladeBlood.corpse` bleeds it in its creature's colour: a blade sprays half again a living body's drops (that fly,
  fall and pool) plus flecks; a heavy blunt blow sprays and pools as on the living; anything else (a fist, an arrow)
  throws only a few flecks. Never a skeleton's, an iron one, one on fire or a bloodless creature's. Tuning in
  `BladeBlood.CORPSE_*`. Compile-checked; not playtested.
- **0.15.114 (finishers: point-blank shot, slow neck snap, crippled targets, grabbable heads):** Andy: "the pointblank
  finisher should use a bullet and actually play the blast". Guns (our TaCZ flintlocks, TaCZ's own, the Void Armory
  flintlock, another mod's by name) get their own finisher, Point Blank (`Finishers.Kind.GUN`): it needs a round loaded,
  spends it (`TaczGuns.spendRound`), and fires with the gun's own TaCZ shot sound (`TaczGuns.gunSound`), muzzle flash,
  flame and smoke. The bow's is renamed Loosed Close and spends an arrow; the crossbow spends its bolt (or an arrow).
  Andy: "neck break should take 5s to finish": the neck snap is a 5 s hold, strained twice, snapped at tick 100.
  Andy: "crippled enemies should be executable too": `Finishers.helpless` takes Darktide's crawl, both legs broken or a
  leg cut off as well as downed; a crippled one is held still while it plays, and `Downed.execute` kills it the same
  way. Andy: "heads from decaptiations should be grabable like they are otherwise": a click (empty hand or sneak) on a
  body or corpse that a severed part lies in takes the part (`Carry.partInView`). Compile-checked; not playtested.
- **0.15.115, Complementary Unbound is the pack's shader pack:** Andy: "can we make these the default shaders?"
  Complementary Unbound r5.9.3 (by EminGT, Complementary Development; CurseForge 385587, file 8884656, made for 26.2
  and 26.3 on Iris) joins the CurseForge import by id (build.gradle `packShaderPacks`, into shaderpacks/), and Build
  Modpack fetches it into your own profile from CurseForge's CDN like the resource packs. Once per install,
  `client/ShaderPreset` (a preLaunch entrypoint, before Iris reads its config) sets `shaderPack` and `enableShaders`
  in `config/iris.properties`; after that the player's own choice stands (marker `jaymospowers-shader-preset-v1`).
  Its licence (Complementary License Agreement 1.2) lets modpacks include it only unchanged, added through
  CurseForge's or Modrinth's own systems, never uploaded directly, and, because it's on by default, credited on the
  pack's description page. So its settings stay at Complementary's defaults (no settings file: "simple variable
  changes" count as modifying it), and the CurseForge page needs a credit line. Dark nights: Complementary takes sky
  light from `gl_MultiTexCoord1`, which Kingdoms' Iris hook (0.7.164) dims, so ambient light follows the moon; direct
  moonlight inside the shadow distance comes from the shadow map, so faces turned to the moon may keep a faint glow on
  a new moon. Compile-checked; not seen in game.
- **0.15.116 (finishers: an unloaded gun uses the bayonet):** Andy: "with no ammunition it should just run though with
  the blade". A gun with no round in it plays the sword's Run Through instead of refusing (`Finishers.move`). Bows and
  crossbows still need an arrow. Compile-checked; not playtested.
- **0.15.117, crushed spiders (Kitchen 0.5.18):** Andy: "large blunt impacts or slams on spiders should crush them,
  leaving no corpse to grab, but only a random amount of legs that disappear after a time". Kitchen does the crushing;
  Powers marks the deaths it should crush with Kitchen's `jaymoskitchen.crushed` tag (`core/Crushing.mark`, only on a
  creature the blow just killed): a heavy blunt blow's kill (`BladeBlood.heavyBlunt`: a hammer or club for 3 hearts or
  more), anything `BladeBlood.crushed` bursts (Gravity's weight and wall slams, the Great Ape's stomp), a blunt, staff
  or greatsword finisher, Gravity's slam, Gravity's meteor, Bulwark's Leap Slam, Nadir's Heavy Hand slam, the symbiote's body
  slams and dive, its thrash's splash and the Behemoth's tendril slam. Compile-checked; not playtested.
- **0.15.118, ships Jaymo's Kingdoms 0.7.172 (TaCZ flintlocks taken out):** Andy: "i dont like the tacz flintlocks,
  you can disable those". Our flintlock gunpack for TaCZ (musket, blunderbuss, flintlock pistol, void flintlock, musket
  ball, shot) is gone, and with it the Gunsmith's Bench: TaCZ's gun smith table is uncraftable and in no creative tab.
  `TaczGuns.trimDefaultPack` deletes the copy TaCZ exported into `.minecraft/tacz/jaymos_flintlocks`, which it would
  otherwise keep loading. TaCZ itself stays, for the future's modern guns. The Void Armory's pair is always our own
  Flintlock item now (`ArsenalFlintlock`); the old TaCZ creative-tab icons (`TaczTabIconMixin`), the future's old
  flintlock gunners (`FutureGuns.FLINTLOCKS`) and `tools/gen_tacz_guns.py` are gone. TaCZ flintlocks already in a
  world don't work any more. Parse-checked only (no game jars in this session); not playtested.
- **0.15.121, smashing dead spiders (Kitchen 0.5.20):** Andy: "you should still be able to smash them when they are
  corpses". `KitchenCompat.registerCorpseBlood` adds a second corpse hit hook: a heavy blunt blow
  (`BladeBlood.heavyBlunt`) on a corpse tags it `jaymoskitchen.crushed`, and Kitchen bursts a spider's body into loose
  legs. Compile-checked; not playtested.
- **0.15.124, the enchantment glint with shaders:** Andy's game crashed with Iris on ("Close the existing render pass
  before performing additional commands" while uploading `enchanted_glint_item.png`): Iris first asks for the glint
  textures inside a render pass, and loading a texture there is refused in 26.3. The client loads both glint textures
  at startup now (`JaymosPowersClient`, `CLIENT_STARTED`).
- **0.15.128, blown apart (Kitchen 0.5.21):** Andy: "creepers should not leave corpses when they explode, and other
  explosions should blast other corpses apart into a mess." Kitchen throws the chunks; `KitchenCompat.registerCorpseBlood`
  hooks Kitchen's `Gibs.addOnBurst`, and `BladeBlood.blownApart` bursts the body's blood wider than a crushing (40
  drops, 3 pools, 40 flecks of gore, in its own blood colour). Compile-checked; not playtested.
- **0.15.130, no lying stuck at no health:** Andy: "after Jung was hit by a meteor he was stuck on the ground unable to
  revive even after giving up or being killed with commands." His health was at zero but he never died: something in
  the death chain turned the death away without giving health back (Fabric's `ALLOW_DEATH` leaves it there), and a body
  at no health ignores every hurt, so giving up and `/kill` did nothing and a relog kept it. `DownedLimboMixin` catches
  it as the blow lands (a downable player goes down instead), and `Downed.limbo` checks every tick for any other way in
  (a save loaded at zero; one who quit on the death screen is marked `DIED` and left alone): the death they were owed
  comes, death screen and all. Each catch logs a WARN with the damage type, to find what turned the death away. Builds;
  not playtested.
- **0.15.131, Tinkers' missing tables and weapons:** Andy asked whether Hephaestus (the Fabric Tinkers' Construct port)
  could replace our Tinkering. It's MIT but stops at 1.20.1 (Mantle + Porting Lib), so its missing pieces were ported
  into ours instead (Tinkers' code and textures, MIT; `license_tinkers_construct.txt`). Details and numbers:
  `docs/Tinkering-Tables.md`, `docs/Tinkering-Weapons.md`.
  - **Tool Forge** (Tinkers' anvil): 5 part slots; any tool of more than 3 parts is built only here (the Tool Station
    says "Needs a Tool Forge"), so the sledge hammer and cleaver move to it. Recipe: 3 metal blocks over a Tool
    Station over 3 seared bricks. **Crafting Station** (3x3 that keeps its grid), **Pattern Chest** (patterns and casts)
    and **Part Chest** (parts and weapon heads), 54 slots each.
  - **Side inventory and tabs:** a chest next to a Part Builder, Tool Station, Tool Forge or Crafting Station shows in a
    panel at the left of its screen (Pattern/Part Chests first; chests, barrels and shulker boxes, never a locked one),
    and tabs along the top hop between touching tables.
  - **Ten melee tools/weapons:** dagger (off-hand swing on right click), kama and scythe (harvest and replant,
    shear), broad axe (fells trees), excavator (3x3 shovel), vein hammer (ore veins), pickadze, war pick, minotaur axe
    (sprint charge), battlesign (blocks, then bonks). New parts: broad axe head, tough binding. Area breaking is one
    code path (`TraitEffects.areaBreak`, `ToolAreas`) and respects spawn protection; sneak breaks one block.
  - **Ranged:** longbow and crossbow (draw speed, velocity, accuracy from their limbs, grip and string), tinker
    arrows (4 a build), javelin, throwing axe, shuriken. New parts: bow limb, bow grip, bowstring, arrow head, arrow
    shaft, fletching; new materials feather, leaves, blaze rod.
  - Compile-checked (0 errors, JEI included); not playtested.
- **0.15.132, the creative inventory opens again:** Tinkering put its ranged weapons in the creative tab at their crafted
  yield (4 arrows), and the creative tab only takes stacks of 1, so opening the creative inventory crashed the game. One
  of each now.
- **0.15.135, nothing overlaps:** Andy: "some UI elements overlap". Pocket Arsenal's ascended HUD drew Hundred Blades
  on top of the R row (its bar ran through the name); Hundred Blades and the pinned weapon now sit above the first row.
  The Tool Forge's recipe list ran past the bottom of the window and JEI's item list and search bar drew over it: the
  Tinkers tables' info panel (`tinkering/SidePanel`) now stops at the window's bottom and scrolls with the wheel, with a
  thin scrollbar, and every table screen tells JEI where its side inventory and info panel are (`TableAreas`, through
  `TinkeringJei.registerGuiHandlers`) so JEI lays itself out around them.
- **0.15.137, no Minecraft skin under the MCA body:** pack bump for Kingdoms 0.7.178 (Shoulder Surfing's see-through
  close-camera view drew your Minecraft skin under MCA's villager body; MCA's bare body is also hidden under clothes
  that cover it).
- **0.15.140, respawning after a downed death:** Andy: "Jungxvv just got the same bug." 0.15.130's catch asked vanilla's
  `dead` flag whether a player had died, but a player's `die` never sets it (`ServerPlayer.die` doesn't call up to
  `LivingEntity.die`), so every death looked stuck: the blow that killed a downed Jung (a zombie) put him back down with
  health, the death screen stayed up, and the server turned his Respawn away (it only respawns players at no health)
  and, as `die` had marked his client unloaded, most likely left him untouchable till a relog (`/kill` did nothing). Now only `Downed.DIED` (set as a player
  dies, at the end of `die`) says they died; the accessor is gone, and a player alive with `DIED` still on (anyone this
  caught) has it cleared. Builds; not playtested.
- **0.15.143, a giant's camera:** Andy wanted big forms' camera to work like the Size Changing Mod (KProject; All
  Rights Reserved, so only the idea), where a giant can still left-click to attack. The Great Ape's view from behind sat
  ~7 blocks off to his right while his hits were aimed from his eyes, so the crosshair and what he hit didn't line up.
  Now every player grown 1.6 times or more (`core/GiantReach.GIANT_FROM`: the Great Ape, a Behemoth...) seen from
  behind gets `client/GiantCamera`: back 3.4 + 0.75 a block of height grown, up 0.1 + 0.12 a block, 0.9 to the right
  (almost straight behind a giant), clipped by walls, in vanilla's F5 view and Shoulder Surfing's. The crosshair picks
  along that camera's line from level with his eyes (`GiantPickMixin`, after vanilla's pick), so a click lands on what
  it's over when it's within his reach of his eyes, as the server checks. `GiantReach` grows reach with size: +3 for
  hitting and +4.5 for blocks per 1 of scale past 1 (at most +30; the ape keeps his own reach for hitting). Spirit
  Bomb's pull-out and Time Flow's shoulder view still take over. First person stays in the (scaled) eyes. Compile-
  checked; not playtested.
- **(same patch) beam battles as a film:** Andy: beam battles should zoom out so you see what's going on, like the AFK
  cinematic (Ji AFK Cinematic; Ji Mods Source License forbids derived copies, so the idea only). `client/BeamCinematic`:
  for the two in a clash, black bars slide in over the top and bottom (12% each), the hotbar, crosshair and status bars
  clear (the clash panel moves into the bottom bar), and the camera (`BeamCinematicCameraMixin`, priority 2000 so it
  has the last word) cuts every 50-90 ticks between six shots: a wide profile drifting along the beams, a slow orbit
  round the knot, over each fighter's shoulder, low under the knot, and a crane overhead, all clipped by walls. Aim
  still follows the mouse (looking away still lets a ki wave go); the view holds 30 ticks past the end, then the old
  view comes back. Compile-checked; not playtested.

## 0.15.147
- Kingdoms 0.7.184: guards make way for quest carriers whatever their ruler's temper, and walk you to the king or queen
  when you ask for an audience (if your good name allows).
- **0.15.147, Earthling ki glow for Z Fighters only:** packs Jaymo's Kingdoms 0.7.184 (Andy: no glow or ki effects on
  the Earthling heritage skin unless a Z Fighter, and the costume clipping was back): the surge's glow, sparks and
  sounds only for Z Fighters, and no heritage marks or glow under a costume. See `kingdoms/MODLOG.md`. Andy then: "Can
  you add the wraps to the gi?" An Earthling's gi wears the wraps itself, over its wristbands and in its own pose, with
  a Z Fighter's ki glow over them while it flares (`client/costume/KingdomsWraps`, asking Kingdoms'
  `HeritageLooks.earthlingWraps`).

## 0.15.147: powers are handed down through a lineage (Kingdoms 0.7.184)
- Kingdoms' Lineage (living on as your child) takes your power away and hands it down: `Affinity.passDown` (called by
  reflection from Kingdoms' `PowersCompat`) clears the power, restarts the 3-5 day awakening and stores it as
  `Affinity.INHERITED`. Andy: "the power you had is given a high affinity to your child, they still have to awaken it
  themselves". At the awakening an inherited power wins over a calling and an affinity, fated or not, and is then cleared.
- Compile-checked; not playtested.
- **0.15.147 / Kingdoms 0.7.184, rescues, ranged tactics, royal days:**
  - Symbiote: each time you go down it picks one of three rescues, never the last one used: the leap and zip; the
    plain suit forming over you (up with Regeneration II for 5 s and all its abilities and the suit for 30 s, whatever
    the bond); or a tendril lash that hurls back, hurts and slows everything after you. Its satiety now feeds your
    hunger bar (a point for 2 hunger every 2 s while it has over 25), and a rescue feeds an empty bar, so a fed
    symbiote no longer lets you starve down again and again. See docs/web.md.
  - Kingdoms: ranged attackers that keep missing or can't see their target change tactics (`RangedTactics`: step
    aside, find a clear line, close in or back off, flank, take cover, or fight hand to hand); a cove's gunners and
    bombardiers come ashore as MCA villagers keeping their pistols and dynamite, camp raiders as villagers throw rocks
    again, and camps tend only their own downed. Kings and queens choose plate, robes or nightwear through the day
    (`RoyalDress`) and keep a day of court, round, meal, audience, leisure and bed. See docs/Kingdoms-Plan.md.
    Compile-checked; not playtested.
- **0.15.149, Lost Cities in the future:** Andy picked shipping The Lost Cities (MIT, McJty; github.com/McJtyMods/LostCities)
  unchanged and pointing it at the Chrono future. McJty's own 26.3 Fabric build (26.3-12.0.0-fabric, CurseForge
  269024/9277225) is in `packMods`; it needs Forge Config API Port, already there. `future/FutureCities` registers its
  `ancient` profile for `jaymospowers:future` on each server start (by reflection; Lost Cities forgets between starts)
  and adds its city feature to the `future_ruins` biome only. The overworld is untouched unless a world is made with a
  Lost Cities profile. The future's generator is now overworld noise with the fixed `future_ruins` biome instead of a
  flat sea, so beyond Los Perrito the land runs on as ruined city; with Lost Cities loaded those chunks decay like the
  island's (`FutureRuins`). Lost Cities' own bed-and-skulls trip to its `lostcities:lostcity` dimension now needs
  bedrock under the bed. Details in docs/chrono.md. Not built or playtested here (the cloud can't reach Fabric's Maven).
- **0.15.147, Touch'n Motion:** Andy asked whether Touch'n Motion's animations, the passive and idle ones above all,
  could be brought in. It's GPL-3.0 (STRadaT, source github.com/victorkozhokin/emf-compat), has a Fabric 26.3 build, and
  is an add-on for exactly what the pack already runs: Entity Model Features with Fresh Animations: Player Extension.
  So it ships unchanged (CurseForge only: `curse.maven`, project 1734204 file 9205898) with its client library EMF
  Compat: Core (GPL-3.0, Modrinth oOuwcnpx, CurseForge 1605113/9205859). Copying its code into ours would make our
  mod GPL, so nothing is copied. Idle: a look at creatures nearby, signs read, a hand on a wall beside you, hands on a
  fence top, hands through crops, feet on stairs and slabs, fright at a creeper's hiss or thunder, shaking off water.
  It also does hands to doors, levers, chests and animals, mining swings, fishing, rowing and riding, each switchable
  in its config (Mod Menu → EMF Compat Core). Also added: EMF Compat: Not Enough Animations (GPL-3.0, Modrinth
  Xs6WnTQm), so NEA's eating, maps and rowing show under Fresh Animations; it sits beside NEA in `packModsNoCf`.
  - Our poses still win. Touch'n Motion runs inside EMF's animation, so a player EMF pauses for one of our poses
    (`PoseTakeover.posed`) gets none of it. Its core lifts EMF's pause for a player one of its addons holds a pose for
    (Touch'n Motion's riding pose), which would have brought Fresh Animations back over ours. So `EmfCompat.hold` also
    puts EMF's own per-player pause (`pauseAllCustomAnimationsForEntity`) on a posed player, which the core never
    lifts, and takes it off when the pose ends. It only removes pauses it put on. Compile-checked; not playtested.
- **0.15.148, the flail and the leather whip (Web Whip's mechanics on weapons):** Andy: "Can these mechanics replace
  the flail? And maybe add a single leather whip?" (`armory/Lash`, synced `Lash.State` player attachment; the head's
  path is worked out the same on both sides, so `client/armory/LashRenderer` draws the chain/lash from the hand with
  no lag). Balkon's flail (`mixin/WeaponModFlailMixin`, `WeaponModMeleeUseMixin`) no longer throws its ball on
  right-click or on every hit: hold use and the ball whirls on its chain over your head, faster over 1.5 s, clipping
  what it passes; let go and it's hauled overhead and brought down where you look (to 4.2 blocks), up to 1.7x the
  flail's damage at full spin, splashing whoever's beside, dust and a jolt. The new leather whip (3 leather, string,
  stick) cracks out to 7 blocks (4 damage); keep holding to wrap and hold what it cracked; let go to haul a light
  catch (up to pig/player size) over your head into the ground (6 damage), or yank a heavy one off its feet toward
  you, slowed 2 s. No blocks broken (towns). Then Andy: "add a flail and whip execution, the whip should also allow
  you to climb up a wall rappel style, trusting that you hooked the edge of an up to 7 block wall": `Finishers.Kind`
  FLAIL (Ball and Chain: whirled overhead, two blows, the ball and chain drawn by LashRenderer) and WHIP (Garrotte: cracked
  round the neck, hauled tight three times), everyone's, Executioner or not. Whip cracked at a wall and held: hooked over
  a top up to 7 blocks above your feet (`Lash.HOOK`); you walk up it (client-side, `LashRenderer.climb`) and over the top,
  Sneak lowers you, letting go drops you (fall damage as ever). Compile-checked; not playtested.
- **0.15.151, the Flame Avatar:** Andy: an Emberborn who spends too many of their years in the Nether is consumed by
  their own fire and becomes an avatar of flame (the end of failing to control it). `power/ember/FlameAvatar` counts
  an Emberborn's time in the Nether (net: a quarter of it cools off elsewhere) and at 7 in-game days the fire takes
  them (warnings at 50/75/90%); `/power avatar <targets> on|off|progress` for ops. An avatar has at most 3 hearts and a
  20-point flame bar that takes hits first (the body flares and sets the attacker alight), refilled by fire, lava and
  eating, slower in the cold, drained by rain and fast by water (which harms once it's out); going down in water undoes it (Andy): on the hit that would drop them they revert to plain Emberborn. `AvatarMoves`: R Fire
  Fist (giant fireball, 4.5-block blast, sets fires, 4 flame), G Flaming Pillar (tap round you, hold at the cursor; throws up and
  away; damage dealt comes back as flame), B Ignition Switch (tap lights what's in front for 3 flame; hold detonates
  everything burning near). No block is broken. Plain keys without a power, sneak + key beside one. Look
  (`client/power/ember/FlameAvatarClient`): char over the skin and clothes, emissive lava veins that dim with the
  flame, vanilla's fire round the body, embers; textures from `tools/gen_flame_avatar.py`. MCA avatars with camps of their own fight Hornkin and piglins in the Nether (Kingdoms 0.7.187). Compile-checked;
  not playtested.
- **0.16.0 / Kingdoms 0.7.196 / Kitchen 0.5.23, classes up to Sorcerer:** Andy: Sorcerer seemed the strongest class, so bring the rest
  up to it and add a boon across the board. Every number lives at the top of `ClassPerks` (Mage's in `Spellcraft`).
  Raised: Thief blades +35% and 25% faster swing; Warlord two-handers +35%, heavy armour weighs x0.25 and gives +1
  armour a piece; Mage casts 1.75x a scroll and loses one heart instead of two; Archer +35%; Survivalist hunger x0.6,
  falls -40%; Brute bludgeons +40%, -6% speed (was -8%), 0.4 knockback resistance; Marksman guns +40%, no heart lost,
  crossbow bolts +30%; Rider horse +25%, mounted blows +30%, -3% on foot. Sorcerer's numbers are the bar and stay.
  New boon each (`ClassPerks.dealt`, now handed the target): Thief Backstab (light blade on a foe facing away x1.5),
  Warlord Cleave (40% of a two-handed blow to up to 3 foes within 2.5 blocks, bystanders spared via `CombatArts.foes`),
  Thief also Prowl (sneak 50% faster, Andy asked for a second), Mage Echo (1 staff cast in 4 leaves no cooldown), Archer Headshot (arrow at the head x1.5) and pins, players included (Andy's second pass): a fully drawn killing shot of 12+ with a wall within 1.5 blocks behind (along the flight) holds the body there while it dies and tags it `jaymoskitchen.pinned.<yaw>`, so Kitchen 0.5.23 hangs its corpse upright on the wall (`CorpseEntity.pin`, client `Pinned` pose) until it's picked up or the wall goes; 1 headshot in 4 with a wall behind the head takes the head off and nails only the head there (Powers `Dismember.beheadPinned`, `SeveredPart` PINNED, by reflection in `PowersCompat`); a leg shot (bottom 37.5%) pins the legs to the ground 2 s (4 s fully drawn: no walking or jumping) and tags the arrow `jaymoskingdoms.leg_pin`, so Kitchen's `LegShots` doesn't cripple as well, Survivalist Second Wind
  (8 s unhurt: mend half a heart every 3 s, fed or not), Brute Stagger (bludgeon blows give Slowness II 1.5 s),
  Marksman Steady Aim (shots from a crouch +25%), Rider Barding (the horse you steer takes 30% less), Sorcerer Kindred
  Element (own element's harm halved: fire, freezing, lightning, drowning, explosions for earth, falls for air). Class
  cards show each boon as a new row. Compile-checked status in the PR; not playtested.
- **0.16.3 / Kingdoms 0.7.197, ground rocks and RLCraft's start (`ground/`):** Andy: "the world should be populated with
  random rocks [...] and flint", then "use the intro mechanics from RL Craft [...] remove craftable planks, and add a
  flint hatchet". Loose rocks (stone, mossy, granite, diorite, andesite, deepslate, tuff, sandstone, red sandstone)
  lie 1-4 to a block like candles, in the stone found under them (else the biome's), on the surface, river beds and cave
  floors; loose flint (1-3) mostly on gravel and by water. Punch to break (drops one a stone), right click picks one up;
  4 rocks craft into their block. Flint right-clicked on top of a hard block knaps into 2 flint shards; shard over
  stick = Primitive Knife; a knife cutting grass gives plant fiber, 3 make plant string (4 make white
  wool); shard + string + stick = Flint Hatchet (was Hand Axe, 3 flint + stick). Every crafting recipe named for planks
  is dropped (`Uncraftable.planks`); right click a log's top with any axe for planks (2 crude, 3 copper-grade, 4 iron+).
  Assets: `tools/gen_ground_rocks.py`.
- **0.16.1, flail and whip executions need Executioner:** Andy: "should only work w the execution trait, and untrained
  slams w the whip damage it." Ball and Chain and Garrotte now follow the Executioner perk like every other weapon's
  move; untrained, both are the Wild Slam, and a whip counts as improvised (`Finishers.improvised`), so it loses ~12% of its
  durability a blow. Compile-checked; not playtested.
- **0.16.2, the flail's chain comes off the hand:** Andy: "i need a live line connected from the flail as soon as you
  right click. it should all animate together coming from the hand because right now the flail floats above the head
  and should be a far smaller flail head. It can look more like this one does, but keep our timing and effects" (the
  Flails mod, MIT). The whirl now goes round the held-out hand at waist height (`Lash.WHIRL_DROOP`, `Lash.armHand`), not
  over the head; a new `LashPose` holds the flail arm out (wrist turning with the ball), raises it overhead for the slam
  and brings it down with the ball, and the chain hangs off that same hand. The client draws the whirl from the moment
  use is pressed (`LashRenderer.shown`) instead of waiting for the server. The chain is now interlocking iron links like
  the Flails mod's, and the ball is under half its old size. Ball and Chain's ball goes round the raised hand too. The
  flail no longer uses the trident's raised-arm animation. Timings, damage and sounds unchanged. Compile-checked; not
  playtested.
- **0.16.11, physical flail and whip:** Andy: "the flail chain is completley disconnected from the stick and it looks
  jarring. all the flail heads should match the material they are made from. and the whip should be able to pull from
  the extent of it's range, not just 7 blocks. also the rappelling should only pull you up as you walk, so no auto
  gliding to the impact point. these weapons should be physics based so the entire thing is accurately rendered in hand
  and in world" (the Whip mod, MIT; nothing copied). The chain and the lash are now Verlet ropes stepped every client
  tick (`LashRenderer.Rope`: gravity, drag, fixed links, resting on blocks), starting at the handle's tip taken from the
  item as it's really drawn (`LashSockets`, via four small client mixins; first person corrected for the hands' own fov).
  The head is pinned to `Lash.head` while the weapon acts, so timing and hits are unchanged, and free otherwise (an idle
  ball hangs off the handle, an idle lash trails from the grip, a let-go head swings back). In the hand Balkon's flail
  shows its bare handle (`WeaponModFlailHandMixin`) and the whip its grip (new `leather_whip_handle` model). The chain is
  vanilla's iron chain texture; the ball is spiked and textured with its tier's block. The whip reaches 10 blocks (Andy's
  pick), hooks any wall top in reach, and yanks harder from further off. Rappel: you hang on the lash; walking climbs it,
  Sneak lowers. Compile-checked; not playtested.
- **0.16.12, the Frost Beam is held:** Andy: "https://modrinth.com/mod/laser-eyes this has a good idea of
  what i want from the beam" (Laser Eyes, an ARR Viltrumite addon: ideas only). `FrostBeam` is one hair-thin white beam from
  the right hand (`HAND_OUT`, `HAND_SIDE`, `HAND_DOWN`; Andy: "keep it as one beam from the hand. but keep the new
  design"), held for as long as the left mouse is (the client sends `FrostBeam.Keep`
  every `RaceHoldClient.FROST_KEEP` ticks; unheard for `KEEP_TICKS` it stops), sweeping with the crosshair. A target under it is hit once per `COOLDOWN`/`Z_COOLDOWN` with the same damage and 40% wound roll.
  Water it crosses becomes frosted ice that melts after `FROST_TICKS`; fire it lands on goes out; no block breaking.
  `ZFighterBeams` style 3 (`HELD`): full width at once, a small glint at the hand and where it lands. While it's
  held the click doesn't dig (`RaceHoldClient.frostBeaming`, `ZFighterAttackMixin`). Compile-checked; not playtested.
- **0.16.16, armour and costumes move with Touch'n Motion:** Andy: "iron armor leggings are not animated on my
  character", and the same for paladin legs and costumes; all moved again without Touch'n Motion, which Andy wants kept.
  Touch'n Motion lays its stance, foot grounding and reaches on the body as EMF animates it and copies that only onto
  armour EMF hands the body's pose to. `TnmFollow` (via `TnmFollowModelFeatureRendererMixin`, just before each model is
  drawn) gives every other humanoid model drawn on a player that pose through Touch'n Motion's own copy
  (`TouchNMotionHook.Drawn.put`, by reflection), keeping the model's own visibility and scale. Skipped for the body model
  itself, while one of our poses is on (EMF and Touch'n Motion are paused then), a downed body, a whip hand and your own
  first-person body. Compile-checked; not playtested.
- **0.16.21, nursing a wound:** Andy: with a festering body wound he kept touching his head instead of his body, and
  far too often; the reaction should go to the spot, and come now and then. None of our code played that touch (the
  fever only gives Weakness and 1 body damage a minute), and nothing in Fresh Animations or Not Enough Animations reacts
  to wounds. The likeliest source is Touch'n Motion's helmet gesture (a hand pushed down on the head, twice), which plays
  whenever the item in the head slot changes. New `client/wounds/WoundNursePose`, for everyone: every 35-70 s (first one
  8-20 s in), a wounded player puts a hand to the wound for 2.6 s. Fingers go to the temple for the head, a hand presses
  the side with a bend for the body, the other hand holds a hurt arm, a hand goes on the thigh with a bend for a leg.
  Festering wounds come first, then breaks, then bleeding. Festering and bleeding sync only to their owner, so other
  players see it for breaks only. It never plays during any other of our poses, attacking, using an item, riding,
  swimming, sprinting, flying, sleeping or downed, or on your own body in first person. It counts in
  `PoseTakeover.posed` (Fresh Animations pauses for it); `posedOtherwise` lets it step aside for every other pose. Hooked
  in through Tend's mixins and `ModelPoses`. Tuning at the top of the class. Compile-checked; not playtested.
- **0.16.24, flail and whip: first person, swipes, a taut rappel, item pulls:** Andy: "the whip and flail look great in
  third person, but they are still completely disconnected in first person ... add a far less powerful aoe swipe with
  both that follows your swing ... the whip tether should pull you taut, as if you were hanging from it ... every jump
  should let you pull closer ... also allow the whip to pull items in". First person: with First-person Model your own
  body (and the weapon in its hand) is drawn in the level, so the handle's tip came in as a third-person one and the
  rope fell back to a screen-corner guess; `LashSockets` now stamps each capture with its frame and your view takes the
  newest (the vanilla hand on a tie). Swipe: a left-click at 60%+ charge (air swing via `handlePunch`, or a blow via
  `AttackEntityCallback`) sends the head round in front of you (`Lash.SWEEP`, 6 ticks, tilted with your look, 2 blocks
  for the flail, 4.2 for the whip) on the physics rope; what it passes takes 0.22x the flail's blow or 1.5 from the
  whip, times the charge, never the one you struck; no cooldown. Rappel (`LashRenderer.climb`): the lash is a rope of
  set length from the lip, so off your feet gravity hangs you on it and you swing in against the wall; each jump hauls
  in 1.4 blocks with a kick out; hands at the lip, jump or walk in to go over the top; Sneak lets you down, Sneak + jump
  bounds down; on the ground it pays out and never drags you. Walking no longer climbs. The hook lasts up to 60 s and
  the server won't call it flying. Items: the crack aims at a dropped item near its line when nothing alive is there,
  and items where it lands are flung back to you (the fishing rod's toss). Tuning at the top of `Lash` and `climb`.
  Compile-checked; not playtested.
- **0.16.24, SevTech-style starter routes (Kitchen 0.5.27, Kingdoms 0.7.205):** Andy: "integrate more starter recipe
  routes [...] Leather needs tanning [...] add another salt acquisition method (maybe the rocks that spawn in water are
  salt crystals) [...] a way to strike and start flame like flint and steel but with a rock in one hand and the flint in
  the other". Powers: 8 plant fibres in a ring craft string (`recipe/ground/string_from_plant_fiber`). Rocks that
  worldgen lays in water are now `salt_crystals` (calcite-textured pebbles, give Kitchen's salt); rock piles got a
  `natural` state (default true, false when a player sets one down) and a natural pile that lies in water turns to salt
  on a random tick, so the already-generated (Chunky) world gets salt too. Flint in one hand and a rock in the other,
  right click a block: sparks, and 35% of strikes light it like flint and steel (fire on the face, or a campfire or
  candle); a light breaks the rock and chips the flint to a shard 1 time in 3 (`FirstTools.strike`, in a late
  UseBlockCallback phase so Kingdoms' arson watch sees it first; knapping skips when you're holding a rock). The
  leather chain itself is in Kitchen 0.5.27 (`tanning/Hides`); `tinkering/Uncraftable` also drops `minecraft:leather`
  (4 rabbit hides), since Naturalist's data pack puts it back over Kitchen's switch-off. Fixed on the way: every rock,
  flint and salt pile dropped one item however big it was (the loot tables used the pre-26.3 `functions` key, which is
  silently ignored); `tools/gen_ground_rocks.py` now writes 26.3's `modifier`/`match_block` like vanilla's candles.
  Checked on a dev server with a fake player: all the hide, wool and fibre recipes (blade and waterskin come back
  worn, an empty waterskin doesn't match, a water bucket leaves its bucket), the rack drying 4 salted hides, a
  waterlogged worldgen rock turning to salt (a player's staying rock), salt/rock/flint pile drops, striking a fire on
  stone, a knife peeling an oak log. Not seen in a client (the rack's drawing, textures, JEI).
- **0.16.34, Restoration and mana (Kingdoms 0.7.212):** `core/wounds/Mending` is healing magic's way into a body
  (Kingdoms calls it by reflection): `mend` (into the worst parts first, as a dressing heals), `closeCut` (never a
  stump), `easeFever` (weakness lifted, dirt washed out), `restore` (bones set, festering cured, cuts closed, up if
  downed, healed whole) and `rekindle` (a downed one up from afar). Nothing but `restore`/`rekindle` reaches the
  downed. The rest (the Restoration Sorcerer, healing scrolls, healers, mana and attunement) is in Kingdoms' log.

## 0.16.36: every Warrior-born's tail and Great Ape their own
Andy: "the warrior born tails should vary a bit between races, and so should the ape forms, all these things are
randomized per character so no two are the same". `client/power/zfighter/WarriorLooks` rolls each character's tail
(5-8 segments, length, thickness, droop, curl, sway speed and phase) and Great Ape (head, arm length, bulk, tail
length) from their UUID, with a fur shade picked and nudged per character. Kingdoms' heritage variant (read by
reflection) sets the fur's family: Clanborn brown, Battle-scarred dark with a kinked (often shortened) tail,
Warpainted red with ochre bands. Players and villager apes alike. Sketch: project files
`art-overhaul/heritage-variants/warrior-tails-apes.png`. Not seen in game.
- **0.17.0, Jaymo's Alchemy (Kingdoms 0.7.215):** an apothecary's craft in `alchemy/`, our own remake of Thaumcraft's and
  Thaumic Tinkerer's ideas (both all rights reserved: nothing of theirs used; behaviour spec and design in
  `docs/Alchemy-Plan.md`). Everything holds 28 essences (`Essence`: four elements, salt/sulphur/mercury, compounds).
  - `essence/` + `codex/`: a hand table plus recipe derivation for every item and creature; the Scrying Lens (scan to
    discover essences, earn insight) and the Apothecary's Codex (research tabs, unlock with insight).
  - `crucible/`: crucible over heat (things dissolve, catalysts transmute; 10% lost as flux), alembic, essence jars,
    Orichalc, quicksilver/brimstone/alchemical salt, Everflame, transmutations.
  - `flux/`: per-chunk flux fades and spreads (twice as fast on Kingdoms' Blood Moons); over 300 it blights the land
    (soil, growth, withered leaves, goo, sick animals that turn on you), over 800 flux wisps at night. Corruption on
    players (fleeting/lingering/deep) with whispers, sights, unease and a hunting wisp; forbidden `whispers` lore. Cures:
    Purifying Salts, Purifying Draught, Ward Stone, a full-moon night's sleep in clean land for the deep kind.
  - `infusion/`: pedestals, the Infusion Matrix, instability (asymmetry less salt candles/skulls/ward stones) with
    flings, zaps, bursts and a blast; Ichor, Runic Amulet, infused enchanting (one level past the cap).
  - `gear/`: essence payment at the enchanting table, Spellbinding Cloth, the Artificer's Rod with five foci (Pull,
    Push, Lift into a bound block, Ember, Frost), Ichorium tools, Ichorcloth robes, the Lantern of Everflame.
  Compile-checked against the 26.3 jars, not yet seen in a client.
## 0.17.3: gifted villagers (Kingdoms 0.7.218)
Andy met a Stormborn purist king and wanted villagers able to "acquire and use powers as well" rather than removing
him. A player's power runs off their keys and client, so villagers hold a **gift** instead (`core/gifted`): the `gift`
attachment (power, corrupted, fighting style, strain; saved and synced), and each power's own `GiftKit` in its package
(`<Name>Gift`: 4-5 moves, an everyday use, a ruler's ultimate). `GiftedAI` picks a move every half second while they
have a foe (their target, their brain's attack target, or whoever hit them in the last 10 s); bold villagers open with
their strongest, wary ones get away first; a gap between moves, and each move its own cooldown. After 300 moves each has a
small chance to corrupt the gift (Wind's opens lightning). Out of a fight they use it in everyday life (Andy: "they should
use everything they can"). A gifted ruler whose throne room is attacked unleashes their ultimate (`Gifted.unleash`, at
most every half day), nearby players seeing the film's bars (`UltimateFilm.witnessed`); Gravity's Meteor only below 30%
health (Andy: "the meteor is a last resort"). Kits: Wind, Cryo, Pocket Arsenal, Gravity, Web Slinger (never the
symbiote), Smoke, Z Fighter, Darktide; Chrono, Resonance and Xenoform have none. Who awakens is Kingdoms' business
(`Gifted.give/power/corrupted/unleash/giftable`, read by reflection). `/power gift <targets> <power> [bold|steady|wary]`,
`clear`, `corrupt`, `ready`, `unleash` for testing. A player who kills a gifted villager (Andy: "It should corrupt you, but
also boost your mastery gain or xp"): +15 corruption (+25 if the gift was corrupted) and 30 mastery points past the
allowance (`Mastery.grant`), or 60 experience with nothing left to learn or no power. Not compiled in the cloud session (no game jars), not seen in game.
- **0.17.6, heritage prejudice (Kingdoms 0.7.221):** Tideborn, Stoneborn, Warrior-born and Sylvan are looked down on by
  most townsfolk, the less human the worse (prices, hearts, good name, guards, talk), and some villages are havens of non-humans only, with a human vs non-human rivalry; villagers now pack up and move away on their own, and rulers reform (or dig in) to keep them; no Powers changes. See Kingdoms' log.
- **0.17.8, five more life-story starts (Kingdoms 0.7.223):** `Future.strand(ServerPlayer)` sends a "lost in time"
  newcomer alone to the future's arrival square (no leap home: death or a Chrono's leap), or, while it isn't in place,
  starts its download and holds them still and unhurt where they stand (`WAITING`, with the percentage) until it is; `Powers.awakenAs(player, power)` (split out of `awaken`) gives that start
  Chrono at once with only its first ability. Not seen in game.
- **0.17.9, creeds and great wars (Kingdoms 0.7.224):** every realm holds a creed (Crown, Chivalry, Conquest, Faith, Coin, Old
  Blood); clashing creeds build tension, faster the richer the realms, until war is inevitable; wars between wealthy realms
  become great wars with coalitions, sellswords, upkeep, rebellions and creed-shaped peace terms; speakeasy bookies take bets on wars and on bouts in a new fight pit dug off each den; players can fight there too (gear held at the ropes, real blows, a ringside attendant who offers to rag or bandage
  your wounds and splint breaks). Powers: `Treatment.tendAll` lets another's hands dress every hurt part and splint every
  break at once. See Kingdoms' log.
- **0.17.10, players on the throne (Kingdoms 0.7.225):** a player can wear a realm's crown, with a villager steward on
  the throne in their name: by usurping (a lord who slays the ruler in their hall), by marriage (a ruler's player spouse
  inherits), by conquest (a lord who takes an enemy seat or slays its ruler in a war claims it), or by founding a realm
  (`/kingdom crown found [name]`, 256 emeralds). No Powers changes. See Kingdoms' log.
- **0.17.11, rarer Eurus and Aeolus (Kingdoms 0.7.226):** the disguised wind dragons turn up far less often and never
  before the world's first Giant has fallen. No Powers changes. See Kingdoms' log.
- **0.17.12, gifted villagers part 2 (Kingdoms 0.7.227):** Andy: "Not all the villagers are weaker, maybe 1 in 80 is an
  extremely gifted wielder", Wind folk "should fly when they see fit", "they should also be able to use resonance", and
  the gifted "should keep their power usage to a minimum and hide it from their friends and family", weighted by temperament.
  - `Gifted.Gift` gains `ways` (`MASTER`, `OPEN`, `REVEALED` bits) and `signature`; `Gifted.give(e, power, style, master, open)`,
    `ways`, `master`, `open`, `revealed`, `closeOnes(BiPredicate)` (Kingdoms sets who counts as family and friends),
    `witnesses(BiConsumer)` (told of each player who sees a gift at work; Kingdoms lets them ask about it).
  - Masters (`GiftedAI`): half cooldowns and gaps, reach 28, their signature move first and twice (`ECHO_DELAY`), a third of
    the corruption chance, their ultimate in any fight at half health.
  - Hidden gifts: no moves while close ones see (until half health), double gap, everyday use at 0.25 and only unseen by
    close ones and players; a player who sees one at work reveals it. Open gifts: everyday use twice as often, always.
  - `GiftKit.tick` (every tick). `WindGift`: soar (hover over the foe, casting), wing (fly away), everyday hovers and
    flights to somewhere near, falls caught; the `jaymospowers_wind_aloft` tag ends a flight cut short by unloading.
  - `ResonanceGift`: pulse, boom, ring, hush (a dome that silences a powered player foe's keys), shriek when corrupted,
    Collapse ultimate. `ResonanceHush.cast(level, caster, corrupted, ticks)` for any caster; clients look for domes on
    every entity. `/power gift <targets> ways hidden|open|master|master_open`. Not seen in game.
- **0.17.13, a whaling life-story start, and every start can be had (Kingdoms 0.7.228):** a capital, barbarian camp, monolith or harbour is
  made when a start needs one and the world hasn't got it; the cave start wakes you at half health, and every story
  landing gives 5 s of Resistance. No Powers changes. See Kingdoms' log.
- **0.17.14, players throw rocks:** right click the air with a loose rock (either hand) to throw it, the way Kingdoms'
  poor guards and barbarians do: the same snowball wearing the rock, hitting for 3, half a second between throws.
  Right clicking a block still sets the rock down, and flint in the other hand still strikes fire. Half the rocks lie
  where they land to be picked up again (`ground/ThrownRocks`).
- **0.17.16, the life story before MCA's creator (Kingdoms 0.7.229):** newcomers tell their story first, then MCA's
  creator opens; the name typed on the creator's first page is now always saved. No Powers changes. See Kingdoms' log.
- **0.17.17, strangers' MCA page (Kingdoms 0.7.230):** until you're introduced, MCA's screen shows only Introduce
  yourself, Gift and Trade, and nothing about the villager. No Powers changes. See Kingdoms' log.
- **0.17.18, a kneeling firing stance:** Andy: "crouching looks like a split right now, the back leg is fine, but the
  front leg should bend at the knee, and stay bent while firing". On one knee (`CrouchPose`) the front thigh now goes out
  level and the leg bends at the knee down to the planted foot, whatever is in the hands (bow, crossbow, gun). A leg is
  one box, so for that draw `client/KneeBend` swaps its boxes for bent copies that cut every side at the knee (texture
  included, so skin, trousers and armour bend too) and puts them back once the model is drawn. Not seen in game.
- **0.17.19, death and blood magic (Kingdoms 0.7.231):** Andy: "death magic, and blood magic, is there a way you can work
  that concept in?", then chose two new Sorcerer elements. Powers: `core/wounds/Hemomancy` (`cuts`, `cut`, `hemorrhage`),
  read by Kingdoms by reflection as Restoration reads `Mending`; Reap kills the downed through `Downed.execute`; casting
  either school adds lingering alchemical `Corruption`. See Kingdoms' log.
- **0.17.20, felled trees (Kingdoms 0.7.232):** ideas from Tree Physics (ARR, 1.21.1 only, needs the Sable physics
  library, so not shipped). Falling trees break through other trees' leaves, some of their own leaves snap off when
  they land, and a fallen trunk can be pushed, rolled down slopes, or built on to pin it. No Powers changes. See
  Kingdoms' log.
- **0.17.21, whale-sized aerwhales (Kingdoms 0.7.233):** Andy: "the aerwhales need to be at least as large as the
  overworld counter parts", then "make the health realistic. it needs to be hunted like the ones in the overworld".
  Drawn 3.2x (about 15 blocks, Kingdoms' great whale is about 14), lowered into a 5.0 x 3.6 box, culled with its full
  length; wild ones rise 8-20 blocks into the sky after spawning. 260 health, blubber lets 30% through (harpoons,
  tridents and the Armory harpoon bite whole); struck, it bolts away climbing, and each Kingdoms harpoon lodged in it
  (read by reflection) bleeds and tires it until, spent, it sinks to 4 blocks over the ground wearing the
  `jaymospowers_spent_whale` tag. With Kingdoms it drops whale blubber, meat, bone and sometimes ambergris. Andy wants
  the taut line lifting the thrower kept: it shouldn't be tried unprepared. Merged in #551 claiming 0.17.20/0.7.232,
  which #550 had already used, so renumbered here. Not seen in game.
- **0.17.25, a loading screen for a story's start (Kingdoms 0.7.236):** newcomers wait behind a loading screen while their
  capital, whaler or future is made and their landing loads; MCA's creator shows your heritage's look. No Powers changes.
- **0.17.27, black until MCA's creator (Kingdoms 0.7.238):** the life story and loading screens sit on black, and the
  world stays blacked out between them until MCA's creator opens. 0.17.26 is left for the mana spirits PR. No Powers
  changes.
- **0.17.30, ship crews are MCA villagers (Kingdoms 0.7.242):** every crewman aboard a pirate ship, warship,
  merchantman or whaler is one of MCA's villagers with a personality, temperament and talk. 0.17.28 and 0.17.29 are left
  for PRs #559 and #552. No Powers changes.

- **0.17.32, mana grows (Kingdoms 0.7.244):** the mana glow settles dim after attuning and shows power; casting folk are
  attuned; amethyst crushes for mana; mana sickness; vis, flux and corruption tie into mana; a mana wand; meditation
  restores mana; casts ripple the aura by intent. No Powers changes (Kingdoms reads Powers' Meditate, Phials and
  Corruption by reflection). See Kingdoms' log.
- **0.17.32, mana spirits (Kingdoms 0.7.245):** the mage hands and the first two spirits, Grimsby (blade) and Varro
  (siege): a chance at near death, five days of spirit sickness, aura arms that grow out of yours, moves on L, bond and
  level, lines and battle advice, autonomous parries and javelins, court wizards and hermits with spirits of their own.
  See Kingdoms' log and `docs/Spirits-Plan.md`. Kingdoms' spirit key unbinds vanilla's Advancements from L once per
  install (it stays on the pause menu), since L is the spirit key and no letter is free of both mods and vanilla. Second
  patch in the same release: Old Ambrose (ward), Thessaly (reach), the Quiet One (echo, silent but one word at bond 100),
  the arm-lock clash between two keepers (a letterboxed tug of war), Spirit armour and Spirit ride.
- **0.17.33, Mod Menu:** Andy asked for it, to reach Touch'n Motion's settings (EMF Compat Core's Config screen) and
  switch on its "Log decisions" while hunting the stray head-touch. Mod Menu 21.0.0 (MIT, TerraformersMC, Modrinth
  kyy7dbrZ, CurseForge 308702 file 8958984) ships unchanged in `packMods`. It's client-only and bundles Text Placeholder API.
- **0.17.34, ship crews and story starts (Kingdoms 0.7.246):** your whaler's crew know you and call you deckhand or
  powder monkey, villagers near where your story starts talk about it, one knight at most per ship (the rest dressed as
  sailors), crews stand on deck instead of sitting as if riding, run to get the downed up (you too), and ship stairs
  can be climbed.
- **0.17.35, the helmet gesture loop:** Andy kept touching his head every two seconds in first person with an iron
  helmet on (the stray head-touch blamed on wounds in 0.16.21). Touch'n Motion's ArmorDon plays its helmet gesture when
  the head slot's item changes, and it reads the slot while the model draws; First-person Model's PlayerMixin answers
  `getItemBySlot(HEAD)` with nothing while it draws your own body, so the helmet seemed to come off and go back on all
  the time. `client/mixin/tnm/TnmArmorDonMixin` (optional, `@Pseudo`) makes ArmorDon read the armour slots from the
  inventory instead, which First-person Model doesn't touch.
- **0.17.36, attuning waits till you can see it (Kingdoms 0.7.247):** a Mage or Sorcerer start's attunement flare
  plays 2 s after the start screens and MCA's creator are gone, not behind them. No Powers changes.
- **0.17.37, spirit arms only while used, a ghost's look (Kingdoms 0.7.248):** a mana spirit's arms form when a move
  starts and fade after, drawn as a solid pale core with thin drifting edges and smoke, and move more calmly. No Powers
  changes.
- **0.17.38, the life story keeps your power a surprise (Kingdoms 0.7.249):** the "Then one day" page no longer names
  the power you'll awaken. No Powers changes.
- **0.17.39, job halls and Blossom villages (Kingdoms 0.7.250):** settlements build a butcher's shop, a fishery and a
  hunting lodge (their jobs hire and pay more where they stand); cherry groves get their own Blossom villages (minka,
  shrine, torii) and samurai-armoured guards, and cherry-grove towns finally get the Blossom look. No Powers changes.
- **0.17.40, your villager's arms and legs move again in F5:** Andy's MCA villager body walked with its limbs frozen.
  `client/TnmFollow` (0.16.16) laid Touch'n Motion's kept copy of the body's pose on every model drawn on a player, MCA's
  skin, clothes and hair included. Touch'n Motion only keeps that copy from the player model the game draws, and MCA
  swaps in its own animated body and never draws it, so the copy was the plain body's pose from before MCA's look loaded
  and stayed that way all session. TnmFollow now keeps its own copy of whichever model the renderer posed this frame,
  taken as it turns to the layers (`PoseTakeoverLivingEntityRendererMixin`), and no longer calls into Touch'n Motion.
- **0.17.41, a voice test (Kingdoms 0.7.251):** the narrator reads "Where were you born?" on the life story's first
  page, and every mana spirit speaks its three summon lines (the Quiet One its one word) to its keeper alone, with the
  chat line as before. 14 clips (612 KB) made with VoxCPM2 (Apache-2.0; voices designed from a description, no real
  person cloned) by `tools/voices/make_voices.py`; Kingdoms' `Voices` maps a lang key to its sound. No Powers changes.
- **0.17.49, spirit hands become what they do (Kingdoms 0.7.258):** a mana spirit is an arm only at rest and while it
  speaks; the aegis is a shield of a hand in front of you, catch me cups hands beneath you, and the weapon forms and far
  hand shoot out to what you aim at within 20 blocks; the axe fells a whole tree in one blow. No Powers changes.
- **0.17.51, sailors not guards (Kingdoms 0.7.259):** no guards or armour on any ship; every crewman is a sailor who
  takes up their weapon when a fight starts and puts it away after; the sea no longer shows inside a ship's hold.
- **0.17.55, stomping Giants and Great Apes (Kingdoms 0.7.261):** the Aether Giants stomp with every step, a shockwave
  of dust running out along the ground that cracks it and hurts whoever isn't jumping, and walk with a slow, swaying
  stride at the same speed. Andy: "same with apes": every Great Ape footfall (a player's or a raging villager's,
  `ZFighterLineage.footfall`) now sends out a ring of dust and puffs to its reach (`ZFighterApeSteps`, from the new
  `ZFighterNet.ApeStep` packet), cracks the ground round the foot (`core/GroundCracks`, fading over 5 s, nothing broken)
  and spares whoever is off the ground; its reach is 6 blocks (was 4.5). A step every 5 blocks (was 3), and the ape's
  model plays vanilla's own walk on that slower stride, kept in step with the server's footfalls. Not seen in game.
- **0.17.57, bigger monoliths (Kingdoms 0.7.263):** the Aether monoliths stand three times as big, built of the Aether's
  own stone (holystone, ferrosite, icestone, irradiated holystone), and only a diamond-tier-or-better Tinkers tool can
  break them. No Powers changes.
- **0.17.59, low-end mode:** Andy asked for Luxium (cheap shadows/lighting/fog for weak PCs); it's 1.20.1 only, its
  licence bans ports, and it can't run beside Iris, so the pack has its own answer instead (`client/LowEndMode`).
  Once per install, after the game starts, a weak PC (built-in graphics: Intel, AMD Radeon APUs, software or virtual; under 3500 MB
  given to Java; 2 cores or fewer) gets shaders switched off through Iris's API and vanilla's Fast graphics preset
  (render 8, simulation 6, fast clouds, fewer particles, no entity shadows or smooth lighting), and the first world it
  joins says why in chat. `/lowend on|off` switches it any time; off puts back the preset, render and simulation
  distance and shaders (a Custom preset comes back as Fancy). Complementary's own settings are never touched (its
  licence). State in `config/jaymospowers-low-end.properties`; `LowEndMode.on()` is there for our own effects to
  check later. Not seen in game.
- **0.17.61, story sagas, side quests and party credit (Andy's NOEA picks, patch 7 track B):** three original sagas
  (`ZFighterSagas`: A Spark in the Ashes, What the Sky Left Behind, The Hollow Crown; 4 to 6 steps each: speak, reach a
  stat or form, find a shrine, the Tower or the Time Chamber, defeat foes, hand over goods, defeat a boss) told in the
  pack's lore (the Aether's scattered remnants, the monoliths and their Giants). Begun from a Story screen opened by a
  button on the training screen (`ZFighterStoryScreen`), with Easy/Normal/Hard picked at the start (boss health and
  damage, counts and prizes scale; constants at the top of the class). Bosses are our rogue fighters, landed beside the
  fighter while the step is open; Giants are named only by entity id (`jaymoskingdoms:<element>_giant`) and the step
  is passed if none are registered. Progress is the `zfighter_saga` attachment (synced to its owner, kept through
  death). `ZFighterSideQuests`: short repeatable jobs (defeat foes, hand over goods, win spars) from masters and the
  Keeper, paying TP and, with Kingdoms, reputation; when no job can be given the same gesture (sneak + right-click
  holding any item) opens the villager's Kingdoms town errands by reflection. `ZFighterKingdoms.partyMembers` calls
  `Parties.members` by reflection (falls back to the player alone); kills and bouts credit the party within 64 blocks.
  Escort jobs were left out (they need a following villager and could not be tried here). Compile-checked only; not
  seen in game. Abilities rows 441 to 450.
- **0.17.62, the space pod and planet missions (Andy's NOEA picks, patch 8 track A):** `ZFighterSpace`: a craftable Space Pod
  (a block; right-click opens the Star Map, `ZFighterStarMapScreen`) that flies to five new dimensions of our own, each with
  datapack terrain (`tools/gen_zfighter_space.py` writes the JSON and the art): the Pale Moon (own noise settings and surface rule,
  dry aquifers, gravity 35% via an attribute modifier), Rustfall (the Overworld's noise with a checkerboard of badlands biomes,
  gravity 2.2, slower, lower jumps, Z Fighters train twice as fast), Glacia (the Overworld's noise with the sea at 84 over snowy
  and ice-spike biomes), Cinder (the Nether's noise settings with our own biome adding iron, diamond, emerald and redstone veins
  to the Nether's; heat is extra exhaustion) and the Maw (a flat void with a black sphere, a glass ring, a slow pull on players
  and a tide at the rim, nine floating wrecks with the `chests/maw_wreck` loot table). The Verdant World and the future join
  the map (the future through new public `Future.podReady/podGo/podBack`, with no Echo needed, and a pod set down beside the
  arrival). Travel is a 3 second countdown, a lift, and a controlled descent onto a landing pad found, flattened and built on
  the first landing (saved on the dimension as `zfighter_space_pad`) with a pod standing on it; Home brings you down by the pod
  you left from (`zfighter_space_home`). `ZFighterSpaceMissions`: on Rustfall, Glacia and Cinder a mission to free the world
  (4 captains, then a boss) or conquer it (4 champions, then plant a Conquest Flag), picked by the Rogue path in Jaymo's
  Kingdoms (`ZFighterKingdoms.rogue`; without Kingdoms, freeing); the fighters are our rogue tyrants scaled down (and by the
  planet and the party), tagged so the raid prize skips them; kills and the flag count for the killer's party within 64 blocks
  (`ZFighterSagas.witnesses`); TP, ores and a planet title are the prize; progress is the synced `zfighter_space` attachment.
  Compile-checked only. Not seen in game: none of the dimension JSON has been loaded by a server (see the track report's
  worldgen caveats). Abilities rows 456 to 467.
- **0.17.62, the afterlife and the demon realm (Andy's NOEA picks, patch 8 track B; reworked onto the pack's own worlds at Andy's
  request: no new dimensions, no portals, no rite):** `ZFighterAfterlife`: a Z Fighter who truly dies (an `AFTER_DEATH` that
  something else has not cancelled, not at the Other World already, `afterlife=true` in `config/jaymospowers-server.properties`,
  the file `ZFighterRealism` reads, written with the new key when missing) is noted, and one tick after his normal respawn is
  moved to the Other World, a site in the Aether (`jaymospowers:aether`, the dimension Wind rises into) at x 20000, z 20000, floor
  y 200 (`SITE_X`, `SITE_Z`, `FLOOR`): far from the lowland islands Wind arrives on, above them and the broken islets. The first
  arrival builds it by code: an elliptical platform (108 by 92 blocks, aether grass over aether dirt over holystone, hanging
  deeper at the middle) with the air over it cleared, and on it the hall, a 40-block quartz road south to a pavilion, the
  tournament ring and the fortune teller's tent (no ground to search for: the site is always a platform, so it never depends on
  the islands' noise). "In the Other World" is now "within 64 blocks of the platform's middle in the Aether"
  (`inSite`, `inOtherWorld`); the training bonus uses it too. The death is never touched, so drops, the keep-inventory rule,
  Kitchen's corpses, the downed state, wounds and every other mod's death handling run as before. He wakes in the judge's hall
  with a halo (the `zfighter_afterlife_stay` attachment, synced to all; `ZFighterHalo` draws a ring of gold dust on each
  client). The Master of the Road gives one lesson a stay and bonus TP (training pays 1.5 times at the site), a ring with a bell
  holds three bouts against spirit fighters (vanilla vindicators with no axe) for TP, and a fortune teller sends him back
  free after the tournament, for 24 emeralds or 400 TP, or by the new 13th wish (`ZFighterWishes.CALL_BACK`, lang
  `wish.jaymospowers.zfighter.call_back`). After 30 minutes he is let go anyway; falling off the island takes him out of the
  Aether as it takes anyone (Wind's fall-home) and the halo is dropped. A player who reaches the site alive (Wind) has no halo
  and no timer and is never sent back, but may use the master, the ring and the training bonus. `ZFighterDemonRealm`: the three
  demon biomes (`demon_gloam`, `demon_ash`, `demon_abyss`, valid Nether biomes with the Nether's carver, ores, springs and
  glowstone) are added to the vanilla Nether's biome source with Fabric's `NetherBiomes.addNetherBiome`, at far corners of the
  Nether's temperature and humidity plane that the vanilla biomes leave empty (Gloam 0.6, -0.65; Ash -0.7, -0.7; Abyss 0.85, 0.85;
  the other climate axes 0, as the Nether's are), so they are uncommon, the Abyss the rarest. Villages are built by code in a
  seeded grid of 128-block cells when a player is within 56 blocks and the spot (and its four edge points) is in a demon biome,
  each with a dark master who teaches only Rogue-path fighters (Kingdoms' Stealth alignment, or 3 defeats without Kingdoms) a dark
  lesson; the army is vanilla fighters under a demon name and tag, spawned in squads around each player standing in a demon
  biome, stronger from the Gloam to the Ash to the Abyss (the old three layers). Compile-checked only; how rare the biomes come
  out, the platform's first build (about 40 chunks of Aether generated at once), the Nether villages' fit to the caves, and both
  death-time moves have not been seen in game. Abilities rows 468 to 478.
- **0.17.63, demon biomes after TaCZ (crash fix):** 0.17.62 crashed on start, server and client ("Errors during registry
  creation ... No value registered for key jaymospowers:demon_abyss", from `TaCZFabric.<clinit>`). TaCZ builds
  `VanillaRegistries.createWorldLookup()` in its main entrypoint, and once `NetherBiomes.addNetherBiome` has run the Nether
  preset asks for the demon biomes there, where only vanilla's exist. `ZFighterDemonRealm.addBiomes` now runs from the server
  and client entrypoints (after every mod's main one) instead of `init`; the world's registries come later from the data
  files, so the biomes still generate.
- **0.17.66, the Moon's surface rule (crash fix):** creating a world failed at "Preparing for world creation" ("Failed to
  parse jaymospowers:moon ... Not a string: {"Name":"minecraft:tuff"}"): `worldgen/material_rule/moon.json` wrote its
  block states in the old `{"Name": ...}` form, which 26.3's surface rules don't read. They're plain block ids now, as in
  `aether.json`. It broke every new world, singleplayer and server.

- **0.17.67, the guard and the parry for everyone; lock-on lunge:** Andy wanted parries easier than ours and combat more
  dynamic with the lock-on. Mallard Guard is All Rights Reserved and NeoForge 1.21.1 only, so `combat/Guard` is our own after
  its idea: every melee weapon or tool with nothing on right click gets vanilla's `blocks_attacks` component and raises a guard
  (60% of a frontal blow, feathers per blow, a guard break when they run out); for that guard and for shields, a blow in the
  first 5 ticks of a raise is a perfect parry and up to 12 a parry (+2/+4 ticks against your lock-on target; no window for a
  mashed raise). A parry takes nothing, shoves and stuns the attacker, and owes a riposte (`mixin/GuardRiposteMixin`, 1.5x or
  1.25x on the next blow). `client/combat/GuardClient` draws a timing bar and parry flash and steps you in when you swing at a
  locked foe up to 3 blocks past your reach; `client/mixin/GuardFirstPersonMixin` turns the blade across in first person.
  Darktide's and Z Fighter's parry windows are 8 ticks now. Spec: `docs/guard.md`. Compile-checked only.
  Enemies too (Kingdoms 0.7.267): fighters raise their blade and parry players (first 8 ticks of their guard, else
  12%/30% by experience), and every Better Combat swing a fighter starts shows a glint, red for heavy blows that carry
  `Guard.UNPARRYABLE_TAG` and can't be parried.
  Every mob too (`combat/AttackTells`, `mixin/AttackTellGoalMixin` on MeleeAttackGoal, `mixin/AttackTellBrainMixin` on the
  MeleeAttack behaviour): a melee blow glints and lands 6 ticks later if the target is still in reach and sight (red and
  unparryable for 10+ attack damage or 1.35+ wide bodies); the Burrukai's charge and Bladeshroom Hunter's cut glint too.
  Guns (Andy: "All guns should red glint too"): `FutureGuns` gunners glint red with a click 10 ticks before each burst, and
  their bullets carry the unparryable tag; `Guard` now counts hitscan shots as projectiles, and a tagged ball or any TaCZ
  bullet is blocked but never parried.
- **0.17.68, the Giants' and Great Apes' grab (Kingdoms 0.7.268):** Andy: "Giants and apes need a grab attack that is
  telegraphed and fairly slow, but deals massive damage to a single target, with a letterboxed animation". `power/zfighter/ApeGrab`:
  the Great Ape's grab in five phases, 30 ticks of wind-up with a roar and the right hand reared up behind him (red motes at the
  hand; step out of its reach and it closes on nothing), 8 ticks of the hand coming down, 40 held at his chest and squeezed (a
  blow's 0.3 every 10 ticks), 8 of hurling the catch into the ground (4 blows' worth, `GroundCracks`, an impact frame, a shake,
  the catch flung clear), 20 of straightening up. The catch is carried every tick (players by `teleportTo` keeping their
  rotation, mobs by `setPos` facing him), can't be bigger than the ape, another ape, or a creative/spectating player. A Z
  Fighter's ape grabs by holding the punch (`ApeSmash`) 50 ticks (`HOLD_TO_GRAB`; a growl and a puff of red when it's ready),
  taking the nearest thing in front of him his hand reaches, else the punch lands as before; the wild apes (a Warrior-born's and
  the raging villagers', `VillagerApe`) grab their quarry every 300 ticks or so instead of a swing. Synced attachment `GRAB`
  (`Grab(start, held)`) poses the arm on every client (`ZFighterApe.Model.grab`, the twin of Kingdoms' `GiantModel.grab`) and
  holds the wild ape's player still (`ZFighterApeInputMixin`). The film (`UltimateFilm.PUSH_IN`, 56 ticks): the catch, a
  player, sees the ape's face come down on them, the ape's own player watches over his shoulder, everyone within 48 blocks gets
  the bars. Kingdoms' `AetherGiant` has the same grab (its own target, 9-block reach, 4 squeeze / 40 slam, every 400-600
  ticks), and calls the film through `PowersCompat.film` by reflection. Lang line `zfighter_ape_grabbed`. Compile-checked only:
  the carry's smoothness for a player (teleports every tick) and the hand's fit to the model have not been seen in game.
- **0.17.69, the narrator reads your whole life story (Kingdoms 0.7.269):** Andy liked the voice test. The narrator now
  reads every life-story question, realm page and answer, and the grey line under each question is heard instead of
  shown. On the last page it reads your whole story back, line by line, and closes with "Once lived, it's yours for
  life." A realm's name isn't spoken ("the realm"), since it changes from world to world. 80 new VoxCPM2 clips,
  5.4 MB of voice in all; `Voices.voiced()` and `tools/voices/make_voices.py` list the same lines from the lang file.
  No Powers changes.
- **0.17.72, killing an echo wakes a stranded Chrono:** Andy asked how a Chrono who starts in the future (the life
  story's "lost in time") gets out, then: "add killing an echo wakes him up". A starter can't use sneak+B (Fast
  Forward) until his next ability unlocks, so he was stuck till then, dying or another Chrono's leap. Now once he
  has killed one of his echoes for good there, sneak+B in the future takes him home (`Future.wakesEarly`, through
  `ChronoPower.usableEarly` so his client lets the key through; `future_echo_slain` is now synced to its owner). He
  gets his own line ("...Sneak+B to wake back where you belong."). Leaping forward again still waits for the unlock.
  Not built or playtested here.
- **0.17.73, talk about work knows who's asking (Kingdoms 0.7.270):** Andy: "characters should have context sensitive
  talk options, for example asking the captain about work should tell the player what their role is on the boat
  either as harpooner or otherwise." A whaler's voyage now keeps each player's berth (`Voyage.berths`: harpooner,
  deckhand, powder monkey). A whaling life story signs you on as her harpooner; coming aboard while she fits out makes
  you a deckhand (a small character, her powder monkey). "Talk about work" now shows on any ship's crewman and goes to
  `ShipTalk` instead of the town's jobs: her captain tells you your berth and its duties and how the voyage stands
  (sailing in N minutes, outward, whales taken, cutting in, homeward), reckons your lay as the hold stands, lets you
  change berth before she sails (harpooner needs irons of your own; powder monkey only for the small), and hands his
  harpooner 4 irons once a voyage when they're down to 1. To someone who isn't a hand he offers each berth while she
  fits out, or says she's full. Her other hands tell you your berth and theirs and send you to him. Lays now go by
  berth (harpooner 1.5x a hand's, powder monkey 0.6x; `Whalers.HARPOONER_LAY`, `MONKEY_LAY`), and shipmates call you
  by your real berth (a sharp-tongued one still calls a deckhand a powder monkey). Warship, merchant and pirate
  crews say what their ship is about and what they do aboard her. Ashore, a town's "Talk about work" opens by who's
  speaking (`WorkTalk`): your apprentice, the ruler (your service by rank, or an offer), the watch (your round, or a
  place on it), one of your own trade, one whose trade the town is hiring for, or anyone to someone already working
  there. Compile-checked against the 26.3 jars; not seen in game.
- **0.17.75, named characters wear our own outfits; no women at sea (Kingdoms 0.7.272):** Andy found a ship's captain in
  an anime girl's skin: "we're about to go back to a set library of skins, at least for specific characters because these
  skins have no place in the pack", and "there shouldnt be any female sailors". The skin came from MCA's online clothing
  library (players' uploads): fishermen (every sailor) were dressed from clothes tagged "sailor" plus any theme tag
  ("fantasy", "rpg"...), which let it through. Now:
  - The library is off (`ClothingLibrary.ENABLED`); anyone in its clothes is dressed again from MCA's own (`Wardrobe`).
  - `Uniforms`: 188 outfits of our own, at least 4 of each (Andy: "each outfit needs at least 4 variations") (MCA clothing, `assets/jaymoskingdoms/skins/clothing`, listed in
    `data/jaymoskingdoms/skins/clothing/{male,female,neutral}.json`), drawn by `kingdoms/tools/gen_uniforms.py` from our
    folk's and sailors' old skins with the face and skin cleared (each villager keeps their own face, hair and skin),
    plus new plain seamen, masters' coats, more coat and robe colours, guards' belts, jerkins and mail coifs, four marks on a lord's surcoat, and gowns for queens and ladies.
    Captains (pirate red coat, navy blue, master's brown), pirate deckhands/gunners/powder monkeys/bombardiers, seamen on
    realm ships, kings, queens, lords and ladies in their overlord's colour, guards in their land's gambeson wear only
    these. They're put in them when they load, when they're made crew, every 5 s aboard, and on taking or losing a
    throne; clothes locked in MCA's editor are left alone.
  - Crews are men: new crewmen are made male, and a woman already aboard is replaced at her station by a man of her
    blood with her gear, role, side, health share and records (`Crewmen.recast`, as `convert` does for old folk).
  Compile-checked here; not seen in game.
- **0.17.76, everyone climbs walls; no cut-off text (Kingdoms 0.7.273, Kitchen 0.5.34):** Andy: "everyone should
  be able to ledge climb, the first rank of free running climber should just increase grip time. also the text is
  cut off on most of these UI". Wall climbing (cling, climb, shimmy, pull up and over, hop to the next wall, cling at
  the end of a wall run, Sneak + forward off an edge into a hang) no longer needs Climber: anyone gets a 3 s grip at
  0.05 a tick (`Freerun.GRIP_TICKS`/`CLIMB_SPEED` now by rank 0 to 3, `Freerun.grip`/`climbSpeed`). Climber rank 1
  only lengthens the grip to 6 s; rank 2 is 8 s and faster (0.07), rank 3 never slips (Andy: "unlimited"; `UNLIMITED_GRIP`, hunger still drains) and climbs at 0.09. Perk text rewritten.
  Text: the shared screen helpers (`UiStyle`, Kingdoms' `Ui`, Kitchen's `KitchenUi`) no longer cut with "...". A
  line too long for its room slides slowly back and forth inside it (as vanilla buttons do; `slide`, a 1.5 s pause
  each end), and a detail box with more lines than it has room for scrolls slowly up through them and back with a
  thin bar at the side (3 s pauses). Same for the H screen's own lines and wrapped text, the Z Fighter story,
  training and star map screens, the cooking screen's rows and hint (`KitchenUi.wrapped`), the life-story side panel
  and the world tier's rows (which used to drop the ones that didn't fit). The skill tree's tiles are 6 px wider so
  every perk name but Lockpicking Prowess fits outright ("Leap of Faith" was cut). Compile-checked here, not
  playtested.
- **0.17.77, stuck ships come off the beach (Kingdoms 0.7.274):** a stuck crewed ship's captain now really backs
  her off (warped astern with her sails struck, bow brought round to open water), a whaler hove to at her moorings no
  longer backs herself onto the beach while fitting out, and a hull already in the ground can move off it. Not seen
  in game.
- **0.17.78, skip the story (Kingdoms 0.7.275):** the life story's question pages have an "On second thought..."
  button (bottom left). It asks whether you'd rather skip the story; "Skip the story" answers every question at
  random (only answers open in this world, a realm drawn for one that names one, a Mage's spell or Sorcerer's element
  too) and turns to the story page, where Back, the heritage and class buttons and the look still change anything
  before "Live this life" and MCA's creator. "Tell it" goes back to the page. Compile-checked here, not playtested.
- **0.17.79, grand seats (Kingdoms 0.7.276):** a kingdom built from nothing takes one of the pack's real castles for its
  seat instead of the blocky citadel, picked by its land (Grim Kingdoms' keeps and citadels, ATi's chateau, castles and
  temple, Repurposed Structures' jungle fortress once that mod is added), read from those mods at runtime, emptied of
  monsters, spawners and loot, the throne and panic room in its largest room. `/kingdom found citadel` keeps the old one.
  Towns and Towers (CC BY-NC-SA 4.0, shipped unchanged with credit: modrinth.com/mod/towns-and-towers) joins the pack:
  new villages per biome (Kingdoms founds capitals in them too), biome pillager outposts and ships; it has no castles.
  Every mod's villages now share one spawn pool (and outposts another), so any of them can turn up at a village spot.
  Compile-checked here, not playtested.
- **0.17.80, Towns and Towers villages pooled (Kingdoms 0.7.277):** Towns and Towers' 27 villages were left out of the
  shared village pool (their set also holds an untagged Nether piglin village); they're in it now. Its licence is
  CC BY-NC-ND 4.0, so it stays shipped unchanged. Epic villages keep their biomes. Compile-checked here, not playtested.
  Also Kitchen 0.5.33.1: the server wouldn't start on 0.5.33 (five pie and cheesecake crafting recipes had a cooking
  category, which 26.3 refuses); fixed, and the pack ships the patched jar.
- **0.17.81, Repurposed Structures (Kingdoms 0.7.278):** Repurposed Structures 7.8.4 (LGPL-3.0, shipped unchanged)
  and MidnightLib 1.10.0 (MIT, which it needs) join the pack. Biome versions of vanilla's structures; a kingdom raised
  in the jungle now gets its jungle fortress for a seat, and its villages and outposts join the shared pools (outposts
  now pool by any id with "outpost" in it, since Repurposed Structures leaves off "pillager"). Compile-checked here,
  not playtested.
- **0.17.82, a floating spirit hand (Kingdoms 0.7.279):** Far Hand and Catch now send out a floating hand made of the
  ki glow's wisps (after Mage Hand): it flies to the chest or item before it opens or comes to you, and catches shots in
  front of you. Only the attuned see it. Not seen in game.
- **0.17.84, villagers stay put while you deal with them (Kingdoms 0.7.281):** a villager you talked to, traded with,
  hit or opened the menu of no longer teleports (MCA's walk-target teleport, Kingdoms' stuck puffs and return-to-post)
  for 30 seconds, or while you're still in their menu. Compile-checked here, not playtested.
- **0.17.85, wisp auras:** Andy: "all auras need to be more of a wisp making contact and dissipating rather than being a
  solid mesh" (four reference pictures). The flame, smooth and godly shells are gone; every style is now ~40-70 single
  tongues of light (`ZFighterKiAuraStyles.wisps`, texture `tools/gen_ki_wisp.py`) born touching the body, licking up
  and off it, drifting away and fading over a short life, then reborn elsewhere. Styles differ only in their wisps'
  numbers (constants at the top). Flying, they bend back toward the feet (the way the cruise pose lays you out) and
  narrow into a comet's tail (`ZFighterKiAura` passes a trail amount from speed). Sparks and motes kept. The old shell
  textures and their generators are removed. Compile-checked, not seen in game.
