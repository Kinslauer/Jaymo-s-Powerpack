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
- **How:** heal a web slinger *while their symbiote has taken over*. Another player's **Zephyr's Breath** (Wind, B) or
  **Time Bloom** (Chrono, B, which already heals everything near it) reaching the taken-over player adds **purity**:
  Zephyr +`PURITY_ZEPHYR` 40, each Time Bloom heal tick +`PURITY_BLOOM` 8. Purity outside a takeover does nothing.
  At `PURITY_FULL` 100 the takeover ends at once: the black suit boils off in white flakes, a choir-like chime, and the
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
- **How (default, see questions):** the black strain turns red from killing. Every kill made in a takeover or by the
  symbiote's own abilities adds **blood** (+1, +5 for a player); at `BLOOD_FULL` 60 the next feed turns it red, with a
  scream, a red flash and red veins spreading through the suit over 3 s. White can never turn red, and red can never be
  purified back (purity just makes it angrier).
- **Look:** an original red suit with black veins and wiry, jagged edges, white slit eyes; red webs with a black sheen;
  tendrils end in hooked blades even when walking.
- **Blades everywhere:** the weapon arm forms only blades (scythe and spear, plus a new **axe**), +`RED_BLADE_DAMAGE`
  25% and severing chances x1.5; barrage stabs every 6 ticks instead of 10; Spike Burst becomes a blade burst that
  makes everything hit bleed (`BladeBlood`) for 4 s.
- **Always looking for a fight:** takeovers come twice as often and don't need starving: any time a mob is within 12
  there's a `RED_HUNT_CHANCE` 10% each check that it goes for it, even fed. After a kill it often stays in for the next
  one (`RED_CHAIN` 50%). The neglect rampage happens at any bond under 40. Narration and remarks get a red set: gleeful,
  bloodthirsty.
- **Players:** red takeovers go for players only if the server's PvP is on and they're within 12 (see questions).

## B. Martial-arts combos (Z Fighter)

Andy: "all added or replacing z fighter's current combo animations". The **hold combo** (Abilities 218b,
`ZFighterCombo`) gets a martial-arts move set, each move with its own body animation like the existing ones:
- New moves: **chi palm** (open-hand thrust, a puff of light), **elbow strike**, **rising knee**, **leg sweep** (the foe
  is tripped: knocked down and slowed 1 s), **crescent kick**, **spinning hook kick**, **double palm** (both hands, a
  heavy shove), **flying knee**, and a **rising dragon fist** finisher (a jumping uppercut that lifts the foe high).
- Each tier's string is rebuilt from the old moves plus these, so the strings read as one martial art from the first
  tier (fresh: jab, cross, palm, elbow, chi palm) to the top (Super 2: ten blows ending in the dragon fist). The old
  front kick, roundhouse and axe kick stay; the plain hook and repeated jabs are what get replaced.
- Same timings, damage scaling and rules as now (each blow a real punch; strings soften per blow). Shadowboxing
  (`Shadowbox`) picks its extra combos from the same move set, so practice looks like the fight.

## C. Bomb glider (Armory)

Andy: "make it just dynamite it drops." An original flying wing you stand on (a bat-winged board, our own model).
- **Item:** crafted from iron, phantom membrane and a redstone block; placed like a boat, ridden standing.
- **Flying:** WASD steers, Jump climbs, Sneak dives, it glides forward on its own; out of fuel it only glides down.
  Fuel: coal, charcoal or blaze rods in its slot (`FUEL_TICKS` per item), a hum and smoke while it burns.
- **Dynamite:** it carries up to 16 dynamite in its own slot. Left click (or the ability 1 key, R) drops one straight
  down, falling with the glider's speed. Uses WeaponMod's dynamite through its compat class when WeaponMod is loaded;
  without it, plain primed TNT with a short fuse.
- Health 20, breaks into its item and its contents; no fall damage waiver for the rider (Andy's rule).

## Open questions (defaults are what gets built if unanswered)
1. White strain: should it take **both** a Wind and a Chrono heal (default: either one counts, purity adds up over a
   few takeovers)?
2. Red strain: how does a symbiote turn red (default: killing a lot, above)? Or is it found its own way, e.g. a red
   goo in Nether craters?
3. Red takeovers attacking other players (default: only with server PvP on).
4. Z Fighter: keep the old moves that aren't replaced (default), or only martial-arts moves?
5. Glider fuel: needs fuel (default), or free to fly like a boat?
