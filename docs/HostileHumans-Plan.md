# Fighting AI plan (after Hostile Humans)

Andy (2026-11-25): "the AI in these mods is exactly what our MCA and npcs have been missing." He picked our own rewrite
and ticked all 39 rows of the pick page (https://claude.ai/artifact/Hu4PqxurY5VdgUpnD7hSin). Plan and licence check:
https://claude.ai/artifact/SUwtC5hH997ZFvmmMeqtnM.

**Licence: ideas only.** Hostile Humans (CoconutQualityModding, code Craft1x) has a GPL v2 LICENSE in its source repo
but All Rights Reserved in its own mods.toml and on Modrinth. Hostile Humans Unified (Felix_xing) is GPL-2.0-only and
decompiled from the original. GPL v2-only can't go into Kingdoms (GPL v3). Nothing of either is copied: this file is
the behaviour spec, and the code, names and numbers below are ours. Credit both as inspiration in the change note.

## Who runs what (where each patch must hook in)

- **MCA villagers** (guards, archers, companions, every MCA body): MCA's own Brain. We reach in through
  `McaGuardFoesMixin`, `McaMeleeSwingMixin` and `RangedTactics.brainStep` (pauses the brain while it moves the body).
- **Folk** (realm folk, barbarians, orcs, goblins, pirates, bandits, smugglers, Cordon): vanilla goals in
  `Folk.registerGoals` plus each kind's `addGoals`.
- **Illagers, skeletons, zombies, piglins**: vanilla goals, reached by mixins (`MeleeAttackGoalMixin`,
  `RangedBowAttackGoalAccessor`, `CrossbowGoalAmmoMixin`) and `RangedTactics.RepositionGoal`.
- **Who to fight** stays `Comrades` (leanings, sides, never a downed body). Every new behaviour is about **how**.
- Shared rules: `Downed` is the end of a humanoid fight (never death, never an NPC execution); `Arrests` keeps guards
  making an arrest; `MobAmmo` counts every arrow, javelin and shot (no free projectiles, a thrown weapon is gone
  until picked up); `Cover` and `Lean` already give NPC shooters corners and leaning.

## Patch 1: weapon sense

1. **Weapon from what they carry** (`ws-pick`). In a fight, a fighter picks its weapon from what it really holds and
   carries: a loaded gun (`MobAmmo` shot), then a bow or crossbow with arrows, then a throwing weapon, then its best
   melee weapon. Re-picked when ammo runs out or the range changes. Mobs whose kind has one weapon (a skeleton's bow)
   keep it.
2. **Blade when rushed** (`ws-swap`). A shooter with a melee weapon switches to it when a foe is inside 6 blocks (4
   for a crossbow already loaded) and back to the bow once the foe is past 9 blocks or it has stepped clear. The swap
   takes a short moment (both hands visibly change). Andy: **add the WeaponMod boomerang to desert bandits' and other
   ranger NPCs' weapon pool**: thrown at 6 to 16 blocks, it flies back; caught, it's thrown again; missed, it lies
   where it falls and the thrower goes to pick it up (MobAmmo rule). Only while WeaponMod is loaded, through the
   WeaponMod compat class.
3. **Distance bands** (`ws-bands`). Each weapon has the range it likes and the fighter keeps to it: bow 12 to 22
   blocks, crossbow 14 to 26, javelin and boomerang 6 to 16, pistol 5 to 12, musket 14 to 28, spear thrower 6 to 14.
   Too close: back off (never off a drop or into lava); too far: close in. Andy: **pillagers struggle with this
   especially; remember the cover system and leaning.** Holding the band prefers a spot at a wall corner (`Cover.corner`)
   and leans out to shoot (`Lean.lean`).
4. **Strafe while drawing** (`ws-strafe`). While drawing or aiming, shooters sidestep (changing side every 1 to 2
   seconds) instead of standing still. Andy: **and lean around corners**: at a corner they lean out instead of
   stepping out.
5. **Throw the spear, go and get it** (`ws-spear`). Spear, javelin and trident throwers throw, then walk to pick the
   weapon up when the foe isn't on top of them; with none left they fight with a melee weapon or bare hands.
6. **Throw harmful potions** (`ws-potion`). Fighters who carry splash potions of harm, poison, slowness or weakness
   throw them at 4 to 10 blocks, spending the real potion; never at a foe in melee with a friend.

## Patch 2: break off and mend

1. **Fall back when badly hurt** (`bm-retreat`). Under 20% health a fighter breaks off and moves away along a safe path
   (no lava, no drop it would take damage from) for up to 8 seconds or 16 blocks; while falling back it doesn't swing.
2. **Heal with what they carry** (`bm-heal`). Once no foe is within 8 blocks it eats food, drinks a healing or
   regeneration potion, or binds a wound with a bandage, from its own hands or pack (MCA inventory, Folk gear). No item,
   no healing; nothing heals from nothing.
3. **Come back** (`bm-return`). At 35% health or more it rejoins: back to its leader, its post, or its foe.
4. **Nerve** (`bm-nerve`). Temperament moves the lines: reckless and fearless (`Temperaments.fearless`) only at 10%,
   timid at 30%; a band leader near (`BandLeaders`) shouts and the band holds 5% longer.
5. **Cornered, they fight** (`bm-corner`). With no safe way out, they fight on.
6. **Friends cover the wounded** (`bm-friends`). Allies within 8 blocks with the GUARD or MEDIC leaning step between a
   retreating friend and the foe for up to 5 seconds.
- Callouts: "falling back", "cover me", "back in" (bubbles, our own lines).

## Patch 3: shields and blows

1. **Shield up for an arrow in flight** (`sh-arc`). Each incoming arrow, bolt, javelin or bullet entity is stepped
   ahead (up to 10 ticks, with drag and gravity); if it will hit the shield bearer from the front and the shield can
   be up in time, it rises.
2. **Watch every shooter** (`sh-shooter`). Any shooter in sight drawing a bow, holding a loaded crossbow, or aiming a gun
   at the bearer counts, not only its own foe.
3. **Counter after a block** (`sh-counter`). A raised block holds at least 6 ticks; after a blocked blow a 10-tick
   window opens in which the bearer strikes first.
4. **One thing at a time** (`sh-busy`). No shooting with the shield up; a crossbowman reloads behind the shield and
   lowers it to fire.
5. **Jump crits** (`ml-crit`). Fighters with a heavy weapon (axe, great sword, hammer) sometimes time a jump to land a
   falling critical hit; quick weapons swing at full charge, never spam.
6. **Spacing by reach** (`ml-reach`). Each fighter keeps the distance its weapon reaches (Better Combat range): spears
   and long blades hold off, daggers close in, and they step back to that distance between blows.

## Patch 4: NPC eyes

1. **Senses between NPCs** (`ey-npc`). When NPCs fight NPCs, the same cone, light, sneaking and distance rules players
   face apply (`Senses`), not all-round sight. A foe already in a fight with them and in sight is seen at full range.
2. **Seen by any part of the body** (`ey-body`). A sight check also tries the body's chest, feet and shoulders, not only
   the eyes, so a shoulder round a corner can be seen. Players too.
3. **Every fighter loses track** (`ey-memory`). Folk lose a foe they can't see and search the last spot, like monsters
   (Senses' WatchGoal and SearchGoal; the Folk exemption goes).
4. **Turn to a blow from behind** (`ey-behind`). Hit by a foe it didn't see, a fighter stops for up to a second and
   turns to where the blow came from; it still has to see the foe to go for it.
5. **See small things at the feet** (`ey-feet`). Within 3 blocks, anything in front of the body at foot height is in
   view even below the cone (baby zombies, spiders, crawlers).
6. **Pass on a sighting** (`ey-share`). A fighter who sees a foe tells up to 3 friends of its side within 12 blocks;
   they know where it is for 10 seconds; they don't pass it on.
7. **Dev view** (`ey-dev`). Andy: **accessed through spectator mode.** A spectator in a dev world (or any creative
   operator in spectator) who looks at an NPC sees its view cone, its foe, what it remembers and why it's acting,
   drawn on their own screen only. No command, no player feature.

## Patch 5: danger sense

1. **Creepers** (`dg-creeper`): a fighter strikes a creeper first, then backs out of its blast once it starts to hiss.
2. **Lit TNT and dynamite** (`dg-tnt`): anyone near a lit charge runs from it, friend's or foe's.
3. **Fire and lava** (`dg-fire`): a burning fighter steps out of fire, makes for water within 8 blocks, and never paths
   or backs into lava.
4. **Water** (`dg-water`): in water they swim to keep fighting or head for the nearest bank, and leave water when the
   fight is over (with `Unstick.wadeOut`).
5. **Cobwebs** (`dg-web`): a fighter stuck in a web cuts it with a blade (or sword) in a moment.

## Patch 6: getting there

1. **Detours** (`pt-detour`). When the path to a foe fails or makes no headway for 2 seconds, try ways round in rings
   of 8, 16 and 24 blocks, and remember failed spots for 5 seconds so it doesn't retry the same dead end.
2. **Cover from shooters** (`pt-cover`). Under fire and unable to reach the shooter, a melee fighter hides behind
   something that blocks the shooter's line (`Cover`), waits 2 seconds, then tries again.
3. **Doors** (`pt-doors`). Fighters open fence gates and trapdoors on their path, and press a button or flip a lever
   within 2 blocks of an iron door to get through.
4. **Ladders** (`pt-ladders`). Paths go up and down ladders and vines and they climb them.
5. **Boats** (`pt-boats`). A fighter in a boat or minecart with a foe in reach gets out to fight.

## Patch 7: band styles

1. **Heavy hitters** (`st-heavy`): barbarians and orcs press in with heavy blows that slow the struck for a moment,
   and leap in to strike.
2. **Shield advance** (`st-shield`): realm guards with shields advance behind them at 80% speed, harder to knock back,
   and stand firmer near each other (`Formations`).
3. **Double-shot archers** (`st-archers`): skilled archers (veterans, rangers) loose two arrows in quick succession,
   each spent from the quiver.
4. **Dodgers** (`st-dodge`): bandits and assassins sidestep or roll out of a blow (a visible move with a cooldown, our
   own `Dodge` roll for NPCs, never an invisible chance), can fight with two blades, and carry a shield on the back.

## How it's built

- One PR (branch `claude/project-thread-jgzz4p`), one commit per patch. Kingdoms only.
- Each track: new classes for its behaviour, small hooks in the shared files. Tuning numbers as `static final`
  constants at the top of each class. Every Minecraft API checked against 26.3 sources; mixin targets checked with
  javap.
- Build after every patch: `JAVA_HOME=/opt/jdk25 sh ./gradlew build` in `kingdoms/`.

## As built

All 39 rows are built: one commit per patch, then one review-fix commit per pair, after an independent check of each
patch (66 issues found and fixed). What a player sees is in `changes/kingdoms/claude-project-thread-jgzz4p.md`.
Main classes: `WeaponSense`, `Boomerangs`, `BreakOff`, `BeltPouch`, `ShieldSense`, `Blows`, `NpcEyes`, `DangerSense`,
`Detours`, `Hiding`, `Passages`, `Climbing`, `Disembark`, `PathProbes`, `BandStyles`, `NpcDodge`, `DualBlades`.

Where the build differs from the rows above, or stops short:
- `bm-heal`: Folk had no pack, so fighting Folk carry a 3-slot `BeltPouch`, filled the first time it's looked in.
  With nothing to mend with, a fighter falls back once a fight, then fights on hurt (the rows didn't say).
- `PackUse`'s older band "draught" (40% from nothing, once a day) still runs; Andy is deciding.
- `ws-bands`: guards who carry a few javelins hold the javelin band only while they have throws left.
- `sh-busy`: vanilla and MCA crossbowmen charge with the shield down (one item in use at a time) and wait loaded
  behind it; Folk crossbowmen reload fully behind it.
- `ml-reach`: spacing only; vanilla hit reach is unchanged.
- `pt-doors`, `pt-ladders`, `pt-detour`, danger sense: humanoid fighters with a foe (Folk, illagers, MCA
  villagers, piglins for paths); zombies, skeletons and witches don't get them (undead already seek shade, and
  climbing zombies would raid bases). Everyone runs from TNT. Raiders can work gates, hatches and an iron door's
  near-side switch; `Locks` keeps them out.
- `st-dodge`: Powers' roll pose draws players only, so NPCs have their own (`NpcRollPose`).
- MCA archers used to shoot free arrows; they now spend from `MobAmmo` like everyone else.
- Nothing has been played. Mixins are checked with javap and compile, not seen applying at runtime.
