# Future gun gear: three items

Source: vault inbox task `future-gun-gear` — Andy: "These would be good for our future aesthetic" (Fracture Point, TaCZ Tracers & Flash, TaCZ Additions, TaCZ Magazines, all by Raiiiden; rows fu-tracers, fu-mags, fu-additions and fu-armour on the Ragdolls and Cover pick-list). This spec covers the three named items: **Fracture Point** (fu-armour), **TaCZ Tracers & Flash** (fu-tracers), **TaCZ** (fu-mags — magazines). fu-additions is a pick-list for Andy, not built here (see Deferred below).

Nothing is copied: no code or art from Fracture Point, TaCZ, TaCZ Additions or TaCZ Magazines. Our own models, pixels and numbers.

## What exists in the pack (verified against the code)

- TaCZ is installed (refabricated port in the modpack). `src/main/java/com/jaymospowers/armory/TaczGuns.java` wraps it by reflection: a modern gun is `tacz:modern_kinetic_gun` with `GunId` (e.g. `tacz:ak47`) in custom data; a round is `tacz:ammo` with `AmmoId` (e.g. `tacz:9mm`). Helpers: `modernGun(name, rounds)`, `modernAmmo(id, count)`, `spendRound(stack)`, `loadRounds(stack, rounds)`, `ammo(stack)`, `fireMode(stack)`. Modern guns are kept out of creative tabs/JEI outside the future and are never craftable here.
- `src/main/java/com/jaymospowers/power/chrono/future/FutureGuns.java`: the dead and echoes of Los Perrito carry modern guns with one magazine of rounds plus spares; dropped guns and loose rounds are lootable; `handgun()` = one modern handgun with one full magazine, no spares.
- `src/main/java/com/jaymospowers/power/arsenal/ArsenalFutureGun.java`: 1 Reach pull in 600 brings one future handgun with exactly one magazine; once empty it's done.
- All gun FX go through typed FX (docs/MagicFx.md). The pack already has `MuzzleFlash` / `MuzzleFlashParticle` (gun muzzle burst, power/chrono/future/MuzzleFlash.java).

## 1. Fracture Point — plate carrier (fu-armour)

**Identity and slot.** `jaymospowers:plate_carrier`, display name "Plate Carrier". Chest armour slot (worn in place of a chestplate). Part of the future gunner's kit: A FutureGuns gunner wears one 1 time in 4 (if its chest is bare) and drops it 1 time in 10. Follow-ups not in this spec: a matching helmet, pouches, and any ammo storage in the carrier.

**Stats and behaviour.** Armour 5 (iron chestplate level), toughness 2, enchantability 10, durability 288 (18 x 16; 280 can't be hit exactly). No movement penalty (proposed — Andy's call). When a gunner wearing one dies, the carrier drops beside its gun; a looted carrier is empty of magazines. The carrier is armour, not storage — magazine storage is a separate choice for Andy (see Open questions).

**How you get it.** Loot only (Andy, 2026-11-17): never craftable, in no creative tab.

**Lang key.** `item.jaymospowers.plate_carrier` = "Plate Carrier" — lang/parts/gun_gear.json.

**Lore.** "Worn on Los Perrito's east bridge, and taken off the fallen with their guns." (The Frostborn fight in whole Rigs, so the carrier is the city's own kit, not theirs: Lore.md, *The last stand's kit*.)

**Visual references.** Fracture Point by Raiiiden (CurseForge, Modrinth): military-style 3D armour — plate carriers, backpacks, animated armour plates (Geckolib). Reference only: our own model at vanilla proportions, muted rig colours (coyote brown, olive drab, steel grey), no copied art or textures.

## 2. TaCZ Tracers & Flash — tracer round (fu-tracers)

**Identity and slot.** `jaymospowers:tracer_round`, display name "Tracer Round". Ammo: loaded into a modern gun's magazine and carried in the inventory; stack 16.

**Stats and behaviour.** Same damage, range and magazine capacity as a normal round of the gun's calibre. When a modern gun fires, each round is a tracer with probability 1 in 4 (Andy, 2026-11-17). A tracer leaves a glowing stroke along the shot: typed FX (docs/MagicFx.md), one quad particle per block of flight, amber-orange (#f5a623), fading over 20 ticks — visible in daylight and clearly distinct from a normal shot. Plus a moment of dynamic light at the muzzle on firing: light level 12 within 6 blocks, fading over 8 ticks, a Light block placed in the air at the muzzle and dimmed each tick until it's gone (as the pack's flares do, gear/GroundFx), all cleared when the server stops. Tracers and normal rounds share one magazine and fire in load order; a gunner's looted gun carries tracers at the same rate as the player's. Muzzle flash itself already exists (`MuzzleFlash#FLASH`) — tracers add the streak; flash colour follows the gun, not the round.

**How you get it.** Loot only (Andy, 2026-11-17): never craftable, in no creative tab.

**Lang key.** `item.jaymospowers.tracer_round` = "Tracer Round" — lang/parts/gun_gear.json.

**Lore.** "Every fourth round burns bright, so the dead can follow their own fire through Los Perrito's endless night."

**Visual references.** TaCZ Additions by Raiiiden (CurseForge gallery): muzzle flash when shooting, bullet trails — reference only. Extend the pack's own `MuzzleFlash` particle; the streak is a new typed FX quad trail; nothing copied from TaCZ Additions' art or code.

## 3. TaCZ — magazine (fu-mags)

**Identity and slot.** `jaymospowers:magazine`, display name "Magazine". Held item; loaded into a modern gun's magazine (custom data holds the ammo id and round count). One magazine item serves every gun family; the family sets capacity (proposed — Andy's call): handgun 17, submachine gun 30, rifle 30, shotgun 8, machine gun 60.

**Stats and behaviour.** Each magazine is made for one family (it says so in its name). Load it by hand: right-click loose rounds of a calibre that family takes onto it, up to its size. Reload is a swap: right-click the magazine onto a gun of its family in the inventory; the gun takes the magazine's rounds, up to what that gun holds (a 7-shot pistol takes 7), and the magazine takes the gun's and what didn't fit (any over its size come back loose), tracers included. Loaded magazines stack to 1; empty ones stack to 6. A dead gunner drops a magazine for its gun 1 time in 4, up to half full. TaCZ's own reload from loose rounds still works (not blocked: that would change every future gun at once; ask Andy if he wants it).

**How you get it.** Loot only (Andy, 2026-11-17): never craftable, in no creative tab.

**Lang key.** `item.jaymospowers.magazine` = "Magazine" — lang/parts/gun_gear.json.

**Lore.** "A future gunner counts every magazine. A dropped gun with one still in it is a gift; an empty gun is just a heavy club."

**Visual references.** TaCZ Magazines by Raiiiden (CurseForge): a magazine for every gun and addon, stacking to 6 with the same ammo count — reference only. Our own item model: a small steel-and-polymer box magazine at vanilla item-icon proportions; no copied art.

## Deferred: fu-additions pick-list (not built here)

From TaCZ Additions (Raiiiden, CurseForge feature list), worth building, in no order: bullet trails and water splash; muzzle flash when shooting; laser pointers with dynamic colour; PiP scopes; recoil multiplier per gun type; gun idle and tuck/pitch near walls; scope sway after 4× zoom; variable scope zoom scrolling; momentum-based gun movement and tilt. Recommendation: start with muzzle flash plus bullet trail, since fu-tracers rides on them. Bring to Andy as a Discord poll (2–4 options) before anything is built.

## Andy's answers (2026-11-17)

1. Tracers: both. About 1 ordinary shot in 4 from a future gun streaks (amber #f5a623, muzzle light 12 fading over 8
   ticks), and Tracer Rounds always streak. A tracer round right-clicked onto a loaded gun takes the place of one of
   its rounds, which comes back to you.
2. Magazines: the sizes above (handgun 17, SMG 30, rifle 30, shotgun 8, machine gun 60). Snipers have none. TaCZ's
   own reload from loose rounds is left working; magazines are a faster swap, not a requirement.
3. All three built together (PR #1671).
4. Loot only: off the future's dead and echoes, like the guns. No recipes, no creative tab.
5. fu-additions: still open, not built here.
