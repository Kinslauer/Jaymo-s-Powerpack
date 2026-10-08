# Pocket Arsenal

Pocket dimension manipulation: the user reaches through a rift and pulls out a weapon, apparently at random. The
weapons come from **Balkon's WeaponMod: Legacy** (mod id `weaponmod`, MIT licensed, always in the modpack) plus a few
vanilla weapons. This power adds no weapon items of its own.

## Controls

| Key (default) | Ability | Cooldown |
|---|---|---|
| **R** | **Reach Through.** A rift of portal particles opens in front of you. About 0.5 s later a random weapon (and its ammo) comes out of it into your main hand, or into your inventory if your hand is full, or at your feet if the inventory is full. | 8 s |
| **G** | **Return.** Sends the summoned weapon in your main hand (or offhand) back, along with its ammo. If it was yours, Reach's cooldown drops by 4 s. | none |
| **V** | **Barrage.** 3 to 5 rifts open in an arc behind and above you. A weapon hovers in each one, turns to track what you're looking at (up to 48 blocks), then they fly in a staggered volley. Each hit does 7 damage and then the weapon dissolves. | 30 s |
| **B** | **Pocket Stash.** Opens your personal 27-slot pocket dimension in a chest screen. It's saved, and it's kept through death. | none |

HUD (to the right of the hotbar): Reach and Barrage cooldown bars with their bound keys, plus
`Summoned n/3 · Ts`, which shows how many summoned weapons you hold and how long until the soonest one leaves. That
line turns red under 10 s.

## The pull pool (`ArsenalPool`)
- It's built lazily on the first pull, by iterating `BuiltInRegistries.ITEM`. There's no compile dependency on
  weaponmod.
- **weaponmod items:** every item in the `weaponmod` namespace, except ids containing bullet, bolt, dart, shot,
  shell, cannonball, ironpart, gun-stock/gun_stock, dummy, cannon, mortar or firerod. Ids are `<weapon>.<tier>`
  (`katana.iron`) or untiered (`musket`). The pool matches keywords, not exact ids, so renames won't break it.
- **Tier weights** (by keyword):

  | Tier | Weight |
  |---|---|
  | iron, copper | 70 |
  | wood, stone | 35 |
  | gold | 30 |
  | diamond | 12 |
  | netherite | 3 |
  | untiered specials (flintlock, blunderbuss, blowgun, crossbow, javelin, dynamite) | 90 |
  | plain musket | 60 |

  With the full mod installed, guns and specials make up about 20% of pulls and vanilla weapons about 14%.
- **Stackable weapons** (javelin, dynamite) come as a stack of up to 4.
- **Ammo** comes with launchers, found by keyword in the same namespace:

  | Launcher | Ammo |
  |---|---|
  | musket, musketbayonet.*, flintlock | 8 `bullet` |
  | blunderbuss | 8 `shot` |
  | blowgun | 16 of a random `dart` variant |
  | crossbow | 16 `bolt` |

- **Vanilla weapons:**
  - Iron, diamond and netherite swords, axes and spears.
  - Mace.
  - Trident, enchanted with Loyalty III so it always comes back.
  - Bow, enchanted with Infinity, with 1 arrow.
  - Crossbow with 12 arrows.
- If `weaponmod` is missing, the pool is vanilla-only. The log line `Pocket Arsenal: N pullable weapons...` shows
  what was found.
- **Barrage looks:** Barrage weapons wear a random weaponmod spear, javelin, knife, katana, halberd, battleaxe,
  warhammer, boomerang or flail, or a vanilla sword, axe, spear or trident. They are purely visual. The projectile is
  our own `jaymospowers:arsenal_rift_blade`, which drops nothing, can't be picked up and is never saved.

## Summoned weapons
- Every pulled stack gets the `jaymospowers:arsenal_summoned` component, which holds the owner, the expiry game time
  and a batch id. A weapon and its ammo share a batch.
- The tooltip shows "Summoned: returns to its dimension in Ns", added client-side via `ItemTooltipCallback`.
- **They go home:**
  - After 75 s. A once-a-second server scan of every online player covers the inventory, armour, offhand and cursor
    stack.
  - 3 s after being dropped, via `ItemEntityMixin`. A dropped summoned item can't be picked up by anyone.
  - When the holder dies, via `PlayerMixin` at the HEAD of `dropEquipment`, so they are never loot.
  - When the owner loses the power.
  - When the owner pulls a 4th: you can hold at most 3 batches, and the oldest goes.
- **They can't be stored:**
  - `SlotMixin` blocks every non-player-inventory slot: chests, the Pocket Stash, ender chest, crafting grids,
    anvils...
  - `ShulkerBoxSlotMixin` blocks shulker boxes.
  - `BundleContentsMixin` blocks bundles.
  - `HopperBlockEntityMixin` stops hoppers picking them up.

## Tuning (`ArsenalPower`)
| Constant | Value |
|---|---|
| `REACH_COOLDOWN` | 160 ticks |
| `RIFT_DELAY` | 10 ticks |
| `SUMMON_TICKS` | 1500 ticks (75 s) |
| `MAX_HELD` | 3 |
| `RETURN_REFUND` | 80 ticks |
| `BARRAGE_COOLDOWN` | 600 ticks |
| `BARRAGE_RANGE` | 48 blocks |
| `BARRAGE_DAMAGE` | 7 |
| `DROPPED_TICKS` | 60 ticks |

Pool weights are in `ArsenalPool` (`tierWeight` and the vanilla list).

## Sync and multiplayer
- The server does everything. Cooldowns live in the `jaymospowers:arsenal_cooldowns` attachment, which is persistent,
  kept through death, and synced to its owner only for the HUD.
- The stash is the `jaymospowers:arsenal_stash` attachment (persistent, kept through death).
- Rift, pull, return and dissolve effects use server `sendParticles` and `playSound`, so everyone nearby sees and
  hears them.

## How to test
```
/power set @s arsenal
```
1. Press R a few times. Check that the weapons land in your hand or inventory, guns come with ammo, the HUD counts
   up to 3 and a 4th pull dissolves the oldest.
2. Hover a summoned item and check the countdown in its tooltip. Wait 75 s and it dissolves with particles and a
   chat-bar message.
3. Press Q on a summoned weapon. It can't be picked up and dissolves after 3 s.
4. Try to put one in a chest, bundle, shulker box or the stash (B). It's refused.
5. Die while holding summoned weapons. They don't drop.
6. Press G with one in hand. It's sent back and the Reach bar jumps ahead.
7. Look at a mob and press V. Weapons hover behind you, then fly at it. Nothing is left on the ground.
8. Press B, store something, relog or die. It's still there.

**Balkon's items:** `/give @s weaponmod:<id>`. Examples: `weaponmod:katana.iron`, `weaponmod:musket`,
`weaponmod:bullet`, `weaponmod:blowgun`, `weaponmod:dart.hunger`, `weaponmod:dynamite`.

**A summoned test weapon by hand:**
```
/give @s minecraft:iron_sword[jaymospowers:arsenal_summoned={owner:[I;0,0,0,0],expires:999999999,batch:1}]
```
Replace the owner with your UUID int-array if you want it to count on your HUD.

## Known limitations and upgrade ideas
- Item frames, armour stands and allay hands aren't covered, so a summoned weapon placed there stays until something
  takes it back into an inventory.
- Thrown weaponmod projectiles (spears, knives, javelins, boomerangs) carry the stack with its component. It expires
  once picked back up, but a projectile stuck in the ground keeps it until then.
- Weaponmod ammo matching relies on its id keywords (`bullet`, `shot`, `dart`, `bolt`). If a launcher is renamed, it
  still gets pulled, but without ammo.
- The Barrage blade's orientation assumes a standard item sprite (tip top-right). Weaponmod items with unusual models
  may look skewed in flight.
- Ideas:
  - A rarer "legendary" pull with a visual flourish.
  - Let Return send a weapon into the Stash instead (making it permanent) at a high cost.
  - A Barrage upgrade that tracks moving targets mid-flight.
