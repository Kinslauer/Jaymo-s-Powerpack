# Future gun gear: three items

Source: vault inbox task `future-gun-gear` — Andy: "These would be good for our future aesthetic" (Fracture Point, TaCZ Tracers & Flash, TaCZ Additions, TaCZ Magazines, all by Raiiiden; rows fu-tracers, fu-mags, fu-additions and fu-armour on the Ragdolls and Cover pick-list). This spec covers the three named items: **Fracture Point** (fu-armour), **TaCZ Tracers & Flash** (fu-tracers), **TaCZ** (fu-mags — magazines). fu-additions is a pick-list for Andy, not built here (see Deferred below).

Nothing is copied: no code or art from Fracture Point, TaCZ, TaCZ Additions or TaCZ Magazines. Our own models, pixels, recipes, numbers.

## What exists in the pack (verified against the code)

- TaCZ is installed (refabricated port in the modpack). `src/main/java/com/jaymospowers/armory/TaczGuns.java` wraps it by reflection: a modern gun is `tacz:modern_kinetic_gun` with `GunId` (e.g. `tacz:ak47`) in custom data; a round is `tacz:ammo` with `AmmoId` (e.g. `tacz:9mm`). Helpers: `modernGun(name, rounds)`, `modernAmmo(id, count)`, `spendRound(stack)`, `loadRounds(stack, rounds)`, `ammo(stack)`, `fireMode(stack)`. Modern guns are kept out of creative tabs/JEI outside the future and are never craftable here.
- `src/main/java/com/jaymospowers/power/chrono/future/FutureGuns.java`: the dead and echoes of Los Perrito carry modern guns with one magazine of rounds plus spares; dropped guns and loose rounds are lootable; `handgun()` = one modern handgun with one full magazine, no spares.
- `src/main/java/com/jaymospowers/power/arsenal/ArsenalFutureGun.java`: 1 Reach pull in 600 brings one future handgun with exactly one magazine; once empty it's done.
- All gun FX go through typed FX (docs/MagicFx.md). The pack already has `MuzzleFlash` / `MuzzleFlashParticle` (gun muzzle burst, power/chrono/future/MuzzleFlash.java).

## 1. Fracture Point — plate carrier (fu-armour)

**Identity and slot.** `jaymospowers:plate_carrier`, display name "Fracture Point Carrier". Chest armour slot (worn in place of a chestplate). Part of the future gunner's kit: FutureGuns gunners can spawn wearing one, and it drops with their gun at the gun's normal drop roll. Also craftable (recipe below). Follow-ups not in this spec: a matching helmet, pouches, and any ammo storage in the carrier.

**Stats and behaviour.** Armour 5 (iron chestplate level), toughness 2, enchantability 10, durability 280. No movement penalty (proposed — Andy's call). When a gunner wearing one dies, the carrier drops beside its gun; a looted carrier is empty of magazines. The carrier is armour, not storage — magazine storage is a separate choice for Andy (see Open questions).

**Crafting recipe** (crafting table, shaped):

```
LIL
I I
 L
```

L = `minecraft:leather`, I = `#c:nuggets/steel` → 1 plate carrier. (3 leather, 3 steel nuggets.)

**Lang key.** `item.jaymospowers.plate_carrier` = "Fracture Point Carrier" — add to lang/parts/armory.json.

**Lore.** "The dead of Los Perrito still wear the rigs they died in. Frostborn troopers and the Warrior-born alike go into the streets' three-sided war in plate carriers; when a gunner falls, its carrier falls open beside its gun. Put one on and you wear the future's last war."

**Visual references.** Fracture Point by Raiiiden (CurseForge, Modrinth): military-style 3D armour — plate carriers, backpacks, animated armour plates (Geckolib). Reference only: our own model at vanilla proportions, muted rig colours (coyote brown, olive drab, steel grey), no copied art or textures.

## 2. TaCZ Tracers & Flash — tracer round (fu-tracers)

**Identity and slot.** `jaymospowers:tracer_round`, display name "Tracer Round". Ammo: loaded into a modern gun's magazine and carried in the inventory; stack 16.

**Stats and behaviour.** Same damage, range and magazine capacity as a normal round of the gun's calibre. When a modern gun fires, each round is a tracer with probability 1 in 4 (proposed — Andy's call; this was left open by the previous run). A tracer leaves a glowing stroke along the shot: typed FX (docs/MagicFx.md), one quad particle per block of flight, amber-orange (proposed #f5a623 — Andy's call), fading over 20 ticks — visible in daylight and clearly distinct from a normal shot. Plus a moment of dynamic light at the muzzle on firing: light level 12 within 6 blocks, fading over 8 ticks, drawn by typed FX light (no placed block, no world changes). Tracers and normal rounds share one magazine and fire in load order; a gunner's looted gun carries tracers at the same rate as the player's. Muzzle flash itself already exists (`MuzzleFlash#FLASH`) — tracers add the streak; flash colour follows the gun, not the round.

**Crafting recipe** (crafting table, shaped):

```
SGS
GIG
SGS
```

S = `minecraft:gunpowder`, G = `minecraft:glowstone_dust`, I = `#c:nuggets/iron` → 4 tracer rounds. (4 gunpowder, 4 glowstone dust, 1 iron nugget.)

**Lang key.** `item.jaymospowers.tracer_round` = "Tracer Round" — add to lang/parts/armory.json.

**Lore.** "Every fourth round burns bright, so the dead can follow their own fire through Los Perrito's endless night."

**Visual references.** TaCZ Additions by Raiiiden (CurseForge gallery): muzzle flash when shooting, bullet trails — reference only. Extend the pack's own `MuzzleFlash` particle; the streak is a new typed FX quad trail; nothing copied from TaCZ Additions' art or code.

## 3. TaCZ — magazine (fu-mags)

**Identity and slot.** `jaymospowers:magazine`, display name "Magazine". Held item; loaded into a modern gun's magazine (custom data holds the ammo id and round count). One magazine item serves every gun family; the family sets capacity (proposed — Andy's call): handgun 17, submachine gun 30, rifle 30, shotgun 8, machine gun 60.

**Stats and behaviour.** A modern gun with no magazine fires nothing. Reload is a swap: right-click a modern gun with a matching magazine → it loads; the spent magazine returns to the inventory empty (custom data cleared). Loaded magazines stack to 1; empty magazines stack to 6 (mirrors TaCZ Magazines: "mags stack up to 6 when they have the same ammo count"). Refill: one empty magazine plus rounds of one calibre in a crafting grid → one loaded magazine of that calibre (shapeless; the ammo id is preserved in custom data; rounds consumed up to the magazine's capacity). ArsenalFutureGun's future handguns spawn with exactly one magazine and nothing else; once empty they're done unless reloaded with a magazine item. Guns looted from future gunners keep whatever magazine they had.

**Crafting recipe** (crafting table, shaped):

```
LSL
SIS
LSL
```

L = `minecraft:leather`, S = `minecraft:string`, I = `#c:nuggets/steel` → 2 empty magazines. (4 leather, 4 string, 1 steel nugget.)

**Refill recipe** (shapeless): empty magazine + one stack of rounds of one calibre → 1 loaded magazine of that calibre.

**Lang key.** `item.jaymospowers.magazine` = "Magazine" — add to lang/parts/armory.json.

**Lore.** "A future gunner counts every magazine. A dropped gun with one still in it is a gift; an empty gun is just a heavy club."

**Visual references.** TaCZ Magazines by Raiiiden (CurseForge): a magazine for every gun and addon, stacking to 6 with the same ammo count — reference only. Our own item model: a small steel-and-polymer box magazine at vanilla item-icon proportions; no copied art.

## Deferred: fu-additions pick-list (not built here)

From TaCZ Additions (Raiiiden, CurseForge feature list), worth building, in no order: bullet trails and water splash; muzzle flash when shooting; laser pointers with dynamic colour; PiP scopes; recoil multiplier per gun type; gun idle and tuck/pitch near walls; scope sway after 4× zoom; variable scope zoom scrolling; momentum-based gun movement and tilt. Recommendation: start with muzzle flash plus bullet trail, since fu-tracers rides on them. Bring to Andy as a Discord poll (2–4 options) before anything is built.

## Open questions — needs Andy's call

1. fu-tracers: tracer probability (proposed 1 in 4 rounds) and streak colour (proposed amber-orange #f5a623) — left open by the previous run.
2. fu-mags: magazine capacity per gun family (table above is proposed).
3. No new items without telling Andy first (2026-11-08): nothing in this spec is added to the game yet. If Andy wants them, the item list goes to his DMs with the patch notes before anything is pushed, and dungeon/chest loot prefers existing pack items.
4. Row priority: fu-tracers, fu-mags or fu-armour — which to build first.
5. fu-additions: which, if any, to build (pick-list above).

A builder can implement each item from this spec without further questions once items 1–2 above are confirmed.