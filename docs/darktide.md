# Darktide

Water and darkness. Spec: section "1. Darktide" of the new-powers table. Colour 0x2F6FB5, icon ink sac.
All numbers are `static final` constants at the top of the class named in brackets.

## Weapons

- Katana: WeaponMod `weaponmod:katana.<tier>`. Knife: `weaponmod:knife.<tier>`.
- Darktide works bare-handed without WeaponMod; blade-only moves then say "This needs a blade" (`DarktideWeapons`).
- **Right click with a WeaponMod knife or katana is the guard, not WeaponMod's use.** A Darktide user who is not
  sneaking gets the item use cancelled (Fabric `UseItemCallback` returns FAIL), so knives don't throw and katanas don't
  raise WeaponMod's own block. **Shift + right click** throws the knife / uses the katana as normal. WeaponMod itself
  is not edited.

## Flow (0-100) [DarktideFlow]

- Submerged +4/s; waist-deep water (>= 0.6) or standing in rain +2/s.
- Dark +2/s, pitch dark (block light <= 4 and no sky light) +3/s.
- Night (anywhere, in a dimension with a day): at least +3/s (`NIGHT_FLOW`); the gain is max(3, the day gains above).
- Daylight under open sky drains 1/s while above 50.
- Blocking costs 2 Flow per point of damage blocked; parry +8, reflect +6, roll evasion +10.
- Flow is not saved across relog/restart (cooldowns neither).

## Keys

| Key | What it does |
|---|---|
| Right click (starter) | **Guard**: hold with bare hands, katana or knife (off hand empty or a blade). Blocks 75% (starter user: 50%) of melee/projectile damage from the front 150 deg; each blocked point costs 2 Flow, and if Flow runs out the guard breaks (12-tick stagger, stun, 40-tick lock). Slows you to 0.35 (katana 0.75). Raising the guard within 5 ticks of a hit = **parry**: full block, +8 Flow, empowered for 4 s, 1 s parry cooldown. Blades reflect arrows and thrown projectiles back at the shooter (aim and speed improve with ascension); magic projectiles only once ascended. Katana parry with R unlocked against a humanoid: stun 24 ticks, reel, **riposte** for 6 + weapon damage. Each katana parry counts toward ascension. |
| R | **Dark Strike** (hold): a katana or knife is raised overhead, bare hands draw a fist back by the hip; release strikes (reach 3.5, 3 s cooldown). Full charge at 1 s (`DarktideMove.CHARGE_FULL_TICKS`): a blade hits for weapon x lerp(1.0, 1.5, charge), a fist for 1-5 damage. Empowered (after a parry) and 25 Flow: x2.5/1.5 on top (2.5x weapon at full charge), with darkness. Held 30 s it strikes on its own. Ascended with a knife: **Pounce** (hold to aim up to 1 s, release to leap 6 blocks, land on the target's back, 2x; 3 stabs if the target is humanoid and crippled), 6 s. |
| V | **Undertow**: sink into the ground and surface where you look (12 blocks, 18 ascended) in water, a shadow or a Black Tide puddle. 15 Flow, 6 s. Ascended: surfacing deals 3 around you. |
| G | Every G hit goes for the legs: a humanoid it hurts is crippled, whatever the aim height. Katana: **Crescent** (4 blocks, 120 deg, up to 3 targets, +4 damage, 15 Flow, 8 s). Knife: **Ink Cross** (3 blocks, 1.5x, Slowness III 3 s, 10 Flow, 6 s). Ascended with knives: **Fang Barrage** (hold up to 4 s, 2 damage every 4 ticks to everything in front, held in place, 2 s stun at the end, 40 Flow, 18 s). |
| B | **Black Tide**: a 10-block, 5-wide wave: 6 damage, push, Slowness II + Blindness 2 s, puts out fire. 40 Flow, 20 s. Ascended leaves a 3 s puddle (Undertow target). Ascended with a knife: **Riptide Dash** (5 blocks, 6 damage, 30 Flow, 12 s); hold R too for an 8-hit flurry on the first foe, ending behind it. |
| Z | Corrupted (ascended or not): **Shadow Harvest**: hold Z aimed at a Kitchen corpse for 1.5 s; soul sand streams into your outstretched hand and its shadow is yours (max 5). Ascended and cured: **Veil** on/off: drains 2 Flow/s; mobs lose track of you beyond 6 blocks in the dark / 12 in light; your name tag hides in the dark; it drops when the corruption returns (nightfall). Cured and not ascended: only a vague hint. |
| X | **Gedan / Stillwater** (all 4 unlocked, katana, hold): you stand rooted while a 10-block field grows over 5 s; parrying while holding cycles kamae (Gedan, Chudan, Jodan, Hasso). Release: you vanish and strike up to 8 foes in the field from below, 6 + 1 per second held (max 5), +1 slash per parry (max 2 extra). 60 s cooldown, max hold 15 s. Routed like Wind's X key; skipped while symbiote-bonded. |
| C | Ascended: **shift + C** is **Tide Arms** (needs WeaponMod): blades of dark water of the tier of the Balkon blade you hold. Holding a knife: knife → katana of its tier → back to your knife. Holding a katana: katana → twin daggers (knives) of its tier → back to your katana. Bare hands (empty main hand): your own Balkon katana or knife is moved into the selected slot (hotbar first, then the rest of the inventory; the highest tier among several), and the next shift + C converts it as above; with none, only a vague hint. Any other item in hand: a hint, nothing moves. A tier with no blade of the other kind falls to the nearest lower tier that has one (wood, gold, stone, copper, iron, diamond, netherite), then iron. Going back, the blade dissolves into what you held. Your real items are stored and returned (death, logout and drops as before). **Tap C** while corrupted: **Necromancy**, raise a harvested shadow (max 3 risen, they follow you, fight your enemies and melt when the corruption lifts or 48 blocks away); tap C while cured: only a hint that the shadows are dormant. Corrupted, not ascended: C (sneaking or not) is **Drown** (6-block sphere around the target for 4 s, 2 damage/s, pulls foes back from the edge, 45 s). |
| Combat Roll | With Combat Roll installed: +2 roll charges (3 total; `combat_roll:count`). A roll that passes a projectile within 2.5 or an attacking mob within 3 without being hit gives +10 Flow once and leaves an ink trail. Nothing happens without the mod. |

Unlock order R, V, G, B. Ascension (**Abyssal Tide**): 40 katana parries with all four unlocked (whispers at 15
and 30). Z follows the state (`slotUnlocked(player, "mode")` = corrupted or ascended; the key guide reads `key.mode.night`
for the harvest, `key.mode.ascended` for the Veil, and the plain line is empty).

Darktide users **never drown**: air never runs down (`DarktideAirMixin` on `LivingEntity.decreaseAirSupply`, plus a
refill each tick) and drowning damage is cancelled.

## Corruption [DarktidePower]

Darktide **starts corrupted** (**Drowned Dark / Drowned Abyss**, shown through `statusSuffix` only, never written into
Fate): `corrupted = Fate.isCorrupted || !cured || (night && !nightReturnPrevented)`.
- **Cure**: 60 continuous seconds of Conduit Power (`CURE_TICKS` 1200). It lasts until the next nightfall on the
  overworld clock (`Save.curedUntil`, persisted in the SAVE attachment), when the cure is cleared and the corruption
  returns with a whisper. Cured at night, the corruption still holds until dawn.
- `DarktidePower.nightReturnPrevented(player)` (false for now) is the one place to stop the nightly return; while it's
  true the cure is carried past nightfall.
- The client reads the synced `CURED` attachment (to everyone) for the name, the HUD, the key guide and C.
- Corrupted gates: Drown (not ascended), Z harvest (anyone corrupted), C raise (ascended), risen shadows melting.
  Cured gates: the Veil (ascended).
- `/powerdebug darktide cure | corrupt` for testing.

## The shade [DarktideShade]

A Darktide user who dies **at night** (a dimension with a day, so never the Nether or the End) rises as their own
**shade** instead: back on 10 health (`REBIRTH_HEALTH`) with full Flow, effects and fire cleared, 3 s of Darkness on the
screen, drawn dark like a risen shadow and shedding black dust and ash (`DUST`; `SHADE` attachment, synced;
`Save.shadeUntil` persisted so logging out dodges nothing). Once a night: a shade that dies again dies for real.
`/kill`, the void and anything else through invulnerability are always final, and a held Totem of Undying goes first.
- The shade lasts until the **next nightfall** on the overworld clock (a whole day to hide through), then the user is
  flesh again. A whisper 30 s before dawn tells them to find a roof.
- **Sunlight** kills it: a world with a sun, `isBrightOutside` (so not at night or in the rain), eyes under open sky,
  not under water. After 1 s in it (`SUN_GRACE_TICKS`, smoke and a warning) the shade **burns away**: every item it
  carries is destroyed (inventory, armour, off hand, the crafting grid, the cursor, Tide Arms' stored blade), 3 Shadow
  Ash (`jaymospowers:shadow_ash`) drop where it stood, and the user dies (`jaymospowers:darktide_dawn`, through armour,
  invulnerability, Resistance and totems).
- `/powerdebug darktide shade` rises as your shade now.

## Crippled [DarktideStatus]

A humanoid (tag `jaymospowers:humanoid`, players always) hit by a Darktide user's melee or ability hit whose aim
point is in the **lower 40%** (`CRIPPLE_LOW_SHARE`) of its hitbox is crippled for **5 s** (`CRIPPLE_TICKS` 100):
forced crawl (`Pose.SWIMMING`), movement x0.3 (`CRIPPLE_SPEED`), no jumping, server-synced so everyone sees it.
The aim point is the user's look ray clipped against the target's box; if the ray misses, the ray's height at the
target's distance is used. G moves (Crescent, Ink Cross, Fang Barrage) skip the aim rule: they always cripple. Stun, Ink Cross's slow and Black Tide's slow are not "crippled" (Ink Cross can still
cripple when it lands low). Pounce's 3 stabs need humanoid AND crippled.

## Files

- `src/main/java/com/jaymospowers/power/darktide/`: `DarktidePower` (keys, ticking, Veil, corruption and cure), `DarktideState`
  (attachments), `DarktideLive` (server state), `DarktideFlow`, `DarktideGuard` (guard, parry, riposte, reflect),
  `DarktideMoves` (R/G/V/B moves), `DarktideGedan` (X), `DarktideTide` (C: Tide Arms, Drown, raising; Z: shadow harvest),
  `DarktideStatus` (stun, cripple, reel), `DarktideMove` (pose kinds), `DarktideWeapons`, `DarktideNet`,
  `CombatRollCompat`, `DarktideShade` (dying at night), `DarktideDebugCommand` (`/powerdebug darktide flow <n> | parries <n> | ready | cure | corrupt | shade`), `mixin/`.
- `src/client/java/com/jaymospowers/client/power/darktide/`: `DarktidePowerClient` (guard/X keys, C while corrupted and not routed by the core),
  `DarktideHud`, `DarktidePose`, `DarktideFx`, `mixin/`.
- `jaymospowers.darktide.mixins.json`, `jaymospowers.darktide.client.mixins.json`, `lang/parts/darktide.json`,
  `data/jaymospowers/tags/entity_type/humanoid.json`.
- Shared (additive): `JaymosPowers`, `JaymosPowersClient`, `KeyGuide` (C line uses the `.night` corrupted suffix),
  `fabric.mod.json`, `KitchenCompat.freeCorpseOf`.

## Check first in game

1. Guard with bare hands / katana / knife: front block, Flow cost, guard break at 0 Flow, parry timing (5 ticks).
2. Right click with a knife or katana does not throw / WeaponMod-block; shift + right click does.
3. Katana riposte on a zombie/pillager (it runs inside `applyItemBlocking`).
4. Arrow and WeaponMod projectile reflection (WeaponMod projectiles go through the damage path).
5. Crippled crawl: hit a zombie and a player low; both should crawl for 5 s for every viewer.
6. Gedan: rooting, field particles/tint, release teleports and return.
7. Tide Arms: items come back on switch, death and logout; conjured blades can't be dropped or stored.
8. A new Darktide is corrupted by day (C is Drown); 60 s of Conduit Power cures it until dusk (whisper), dusk brings it
   back (whisper). Z harvest while corrupted, tap-C raise while corrupted and ascended; shift + C cycles Tide Arms.
   Ascended and cured, Z is the Veil; it drops at nightfall.
9. Combat Roll charges show 3.
10. R charge: overhead blade / drawn-back fist seen by another player; release damage scales with the hold.
11. Underwater: no air bubbles drop, no drowning.
12. Die at night: you stand back up dark on 10 health, shedding black dust. Step into the sun the next day and you burn
    away, leaving only Shadow Ash and none of your items; hide until nightfall and you are yourself again. Die twice in one
    night and you stay dead.
