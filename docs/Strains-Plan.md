# Plan: symbiote strains, martial-arts combos, the bomb glider

Andy's ask (2026-10-16), after looking at Throgy's Addon (CurseForge). That mod is All Rights Reserved and a Forge
1.20.1 Palladium/KubeJS addon, so nothing is taken from it: these are ideas rebuilt in our own code, art and names.
Not wanted yet: Wolverine, Magneto, Cyclops; skipped: Galactus, Knull, Throg.

Each part owns its own files, so they can run as parallel tracks (two at a time): **A** in `power/web`, **B** in
`power/zfighter`, **C** in `armory`. Numbers are `static final` constants at the top of each class.

## A. Symbiote strains (Web Slinger)

The symbiote (`WebSymbioteState`) gains a **strain**: `BLACK` (the one we have), `WHITE` (purified) and `RED`
(bloodlust). It's saved and synced with the bond, survives death like the bond does, and a rejection or losing the
web power clears it back to black with the bond. Every strain keeps the full kit (swing, zip, shot, yank, the X menu,
the big body, takeovers, feeding by kill-grab-carry-feed); what changes is below.

### White: purified (Andy: "what you get when you purify a symbiote")
- **How (Andy: both):** heal a web slinger *while their symbiote has taken over*, with **both** a Wind user's
  **Zephyr's Breath** (B) and a Chrono user's **Time Bloom** (B, which already heals everything near it). Each fills
  its own half of **purity**: Zephyr +`PURITY_ZEPHYR` 25 to the wind half, each Time Bloom heal tick +`PURITY_BLOOM` 5
  to the time half, each half capped at `PURITY_HALF` 50. Healing outside a takeover does nothing. When both halves are
  full the takeover ends at once: the black suit boils off in white flakes, a choir-like chime, and the
  strain is White. Purity fades `PURITY_DECAY` 1 every 10 s, so it takes a few takeovers' worth of help in a row, not
  one lucky heal. A Chrono or Wind user who purifies one gets a whisper ("Something in it went quiet.").
- **Look:** an original white suit with ragged black patches over the shoulders and face, black-rimmed white eyes;
  white webs with a pale gold sheen; the big body in the same colours. Painted fresh in vanilla style.
- **No blades:** the weapon arm forms only blunt shapes (a mace fist and a flat palm shove; no scythe, no spear, no
  severing). Spike Burst becomes **Recoil**: the same pose and push, rounded white nubs instead of spikes, half the
  damage, and any player in range is healed instead of hurt. Tendril barrage and crush still hit (they're blunt).
- **V heals:** Web Shot becomes **Mending Shot**: a hit on a player or a mob heals it `MEND_SHOT_HEAL` 4 (2 hearts) and
  clears Poison and Wither; no Slowness, no cobweb. Monsters are healed too: it doesn't take sides.
- **Too much healing puts it to sleep:** every point healed (Mending Shot, Recoil) costs satiety
  (`HEAL_SATIETY_COST` 1.5 a heart). At 0 it goes **dormant** for `DORMANT_TICKS` 6000 (5 minutes): the suit withdraws,
  the X menu is greyed, webs are plain, no takeovers. Feeding a corpse while dormant wakes it early.
- **Opposite personality:** takeovers are to heal, not to hunt. Instead of the starving hunt and the neglect rampage,
  it walks you (same takeover path) to the most hurt creature or player within 16 and fires Mending Shots at it; when
  starving it still has to eat, so it takes a corpse it can find, or kills a hostile mob only (never a passive one) and
  eats that. All narration and remarks get a white-strain set: gentle, hopeful, wanting to mend things
  (`say.white.<act>.1-3`, `see.white.<topic>.1-3`).

### Red: bloodlust (Andy: "Carnage always seeks murder and violence")
- **A rival symbiote, not a changed one (Andy):** its own **red craters**, much rarer than black ones
  (`RED_CRATER_RARITY` 1500 chunks, overworld, same site rules), with a **red goo** (`web_symbiote_red_goo`, "Red Ooze")
  bonded the same way. Purity does nothing to it (healing during its takeover just makes it angrier).
- **Aggressive at all times, even loose:** the red blob, settling or wandering, attacks everything near (players
  included) with its tendrils instead of waiting for a host; it takes hostile *and* passive hosts and never spits a
  host out early. A red host and a black/white bonded player or blob within 16 go for each other on sight (rivals).
- **Look:** an original red suit with black veins and wiry, jagged edges, white slit eyes; red webs with a black sheen;
  tendrils end in hooked blades even when walking.
- **Blades everywhere:** the weapon arm forms only blades (scythe and spear, plus a new **axe**), +`RED_BLADE_DAMAGE`
  25% and severing chances x1.5; barrage stabs every 6 ticks instead of 10; Spike Burst becomes a blade burst that
  makes everything hit bleed (`BladeBlood`) for 4 s.
- **Always looking for a fight:** takeovers come twice as often and don't need starving: any time a mob is within 12
  there's a `RED_HUNT_CHANCE` 10% each check that it goes for it, even fed. After a kill it often stays in for the next
  one (`RED_CHAIN` 50%). The neglect rampage happens at any bond under 40. Narration and remarks get a red set: gleeful,
  bloodthirsty.
- **Players (Andy: yes):** red takeovers and the red blob go for players too, the nearest living thing within 12.

## B. Martial-arts combos (Z Fighter)

Andy: the current combo animations are too stiff; **replace them all** with martial-arts moves that move the whole
body: steps and lunges into each blow, hips and shoulders turning through it, weight shifting foot to foot, a little
travel forward, never a blow thrown from standing still. The **hold combo** (Abilities 218b, `ZFighterCombo`) gets:
- New moves: **chi palm** (open-hand thrust, a puff of light), **elbow strike**, **rising knee**, **leg sweep** (the foe
  is tripped: knocked down and slowed 1 s), **crescent kick**, **spinning hook kick**, **double palm** (both hands, a
  heavy shove), **flying knee**, and a **rising dragon fist** finisher (a jumping uppercut that lifts the foe high).
- Kept as moves but **reanimated** in the new style: lead jab (a step in), reverse punch (hip turned through),
  front snap kick, roundhouse, axe kick, spinning back kick, flying double kick. Every tier's string is rebuilt from this
  set, from the first tier (step-in jab, reverse punch, elbow, chi palm) to the top (Super 2: ten blows ending in the
  dragon fist).
- Same timings, damage scaling and rules as now (each blow a real punch; strings soften per blow). Shadowboxing
  (`Shadowbox`) picks its extra combos from the same move set, so practice looks like the fight.

## C. Bomb glider (Armory)

**Paused (Andy, 2026-10-16):** a half-written draft is saved in the project files under `glider-wip/` (not in the mod).

Andy: "make it just dynamite it drops." An original flying wing you stand on (a bat-winged board, our own model).
- **Item:** crafted from iron, phantom membrane and a redstone block; placed like a boat, ridden standing.
- **Flying:** WASD steers, Jump climbs, Sneak dives, it glides forward on its own; out of fuel it only glides down.
  Fuel (Andy: blaze rods or lava): a blaze rod (`FUEL_BLAZE_TICKS` 1200) or a lava bucket (`FUEL_LAVA_TICKS` 6000,
  the bucket given back) in its fuel slot; a hum and flame puffs while it burns.
- **Dynamite:** it carries up to 16 dynamite in its own slot. Left click (or the ability 1 key, R) drops one straight
  down, falling with the glider's speed. Uses WeaponMod's dynamite through its compat class when WeaponMod is loaded;
  without it, plain primed TNT with a short fuse.
- Health 20, breaks into its item and its contents; no fall damage waiver for the rider (Andy's rule).

## Andy's answers (2026-10-16)
1. Purifying needs both a Wind and a Chrono heal.
2. Carnage is a rival red symbiote, found even more rarely, aggressive at all times, even wandering.
3. Carnage's takeovers attack players too.
4. Replace all the Z Fighter combo animations: too stiff, they need more movement.
5. The glider needs blaze rods or lava.
