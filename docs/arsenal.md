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

## 0.1.5
- **Barrage blades stay in the world.** After hitting they bounce off and drop like a thrown trident; on a miss they
  stick in the ground.
- **Picking them up:** only the caster can, by walking over them. Each one comes back as a fresh summon on the usual
  timer, and the 3-summon cap still applies (`ArsenalPower.capSummons`).
- Uncollected blades dissolve after `RiftBlade.LYING_TICKS` 600 (30 s).

## Reach Through animation (0.1.6)
- **Timeline** (`ArsenalReach`, in ticks):
  - 0–6: the portal tears open with a slight overshoot.
  - 6–12: the weapon arm reaches into it.
  - 12–22: the arm hauls back while the weapon slides out of the portal into the hand, tumbling, then settling and
    growing.
  - 22: the server hands the weapon over.
  - 22–28: the portal snaps shut.
- **Rolled up front:** the weapon is rolled when the portal opens, so the one that comes out is the one you get.
- **Sync:** `jaymospowers:arsenal_reach` (start time + weapon), seen by everyone.
- **Portal (`ArsenalReachRenderer`):** an elliptical disc of the animated nether-portal texture, its UVs turning so
  it swirls, plus a bright rim, all full-bright. It sits 1 block ahead of the weapon hand at shoulder height, facing
  the player.
- **Arm (`ArsenalArmPose` + client mixins):** aims at the portal, then pulls back to the chest. In first person the
  weapon flies into the bottom corner of the screen.
- **Untested in game:** the portal and item drawing only run when R is pressed.

## 0.2.0: Ascension & Corruption

Nothing here is explained in game. The only hints are whispers in the action bar, sounds, and the key's tooltip.

### Void corruption (the meter)
Only arsenal users are affected. Every source goes through `Fate.addCorruption`, which spares creative players and
does nothing once the power is corrupted or purified. All numbers are constants in `ArsenalVoid`.

| Trigger | Meter | Constant |
|---|---|---|
| An Enderman picks you as its target (`ArsenalEnderManMixin`, HEAD of `Enderman.setTarget`, only when the target changes) | +2 | `CORRUPT_AGGRO` |
| You hit an Enderman, or one hits you (`ServerLivingEntityEvents.AFTER_DAMAGE`) | +4 | `CORRUPT_HIT` |
| You kill an Enderman (`AFTER_DEATH`) | +10 | `CORRUPT_KILL` |
| You are in the End (checked every tick in `ArsenalPower.tick`; not in creative or spectator) | `Fate.corrupt` at once | |

- A whisper and a quiet Enderman sound, heard only by you, play when the meter crosses 25, 50 and 75 (`WHISPER_AT`).
- Arriving in the End plays its own whisper.
- A purified power is immune, which `Fate` enforces.

### Void Arsenal (corrupted)
- **Visuals**
  - The Reach portal becomes a window onto the End's starfield, drawn with the end-gateway shader
    (`RenderTypes.endGateway()`). It has a deep-purple lip that fades into black haze. The drawing is shared in
    `RiftPortals.disc`.
  - Barrage blades now hang in a small rift of their own: a portal disc normally, a void disc when corrupted. These
    are drawn in `RiftBladeRenderer`.
  - Void-drawn weapons get the enchantment glint, set with `ENCHANTMENT_GLINT_OVERRIDE` in
    `ArsenalVoid.voidTouched`. That is the "void sheen".
  - In the Reach animation and on Barrage and Hundred Blades blades, they're also drawn at near-zero light
    (`RiftPortals.VOID_LIGHT`), which tints them dark.
  - Void blades trail ink instead of portal motes.
- **Aura** (`ArsenalAura`): every client draws it for every corrupted arsenal user within 48 blocks, from the synced
  power and Fate state.
  - Reverse-portal motes drift inward every 2 ticks.
  - A faint smoke haze every 4 ticks.
  - About every 5.5 s on average, a dark pulse: a ring of squid ink and a quiet heartbeat.
  - In first person, it stays below eye level.
- **Gameplay trade** (`ArsenalVoid`)

  | Effect | Value | Constant |
  |---|---|---|
  | Summoned weapons deal more damage. This applies to melee and projectiles whose `DamageSource.getWeaponItem()` is summoned (`ArsenalLivingEntityMixin`, `@ModifyVariable` at the HEAD of `LivingEntity.hurtServer`), and to Barrage blades (applied when they're created). | ×1.3 | `DAMAGE_MULTIPLIER` |
  | Those hits apply Darkness and Wither I (Hundred Blades blades apply only Darkness). | Darkness 60 t, Wither 60 t | `DARKNESS_TICKS`, `WITHER_TICKS`, `WITHER_AMPLIFIER` |
  | Water and rain hurt you (drown damage, while `isInWaterOrRain()`). | 1 every 20 t | `WATER_DAMAGE`, `WATER_INTERVAL` |
  | A hit of 6 or more (damage taken, after armour) has a chance to blink you, Enderman-style, onto safe ground. This uses `LivingEntity.randomTeleport` with 16 tries, and plays the Enderman teleport sound and portal particles at both ends. | 30%, 4–8 blocks | `BLINK_MIN_DAMAGE`, `BLINK_CHANCE`, `BLINK_MIN`, `BLINK_MAX` |

### The cure
1. Every player who damages an Ender Dragon is remembered per dragon, in server memory (`AFTER_DAMAGE`). End-crystal
   blasts set off by a player count.
2. When the dragon dies (`AFTER_DEATH`, the start of its death animation), the cure applies to each corrupted arsenal
   user who:
   - fought it, or landed the killing blow, and
   - is in the same dimension, and
   - is within 160 blocks of it (`CURE_RADIUS`).
3. The void is torn out of each of them over 70 ticks (`PURGE_TICKS`):
   - They are rooted (Slowness IV).
   - Spirals of ink and reverse-portal particles stream up and out of the body, widening.
   - Smoke, the Enderman scream, an elder-guardian curse and warden heartbeats play.
   - Then `Fate.purify` lands with its title, and a column of void is thrown skyward, with end-rod sparks and the
     end-portal sound.
4. Purification is permanent. A player who disconnects mid-purge is purified on the spot.

### Ascension: the Dimensional Key
- **Item:** `jaymospowers:arsenal_dimensional_key`. Epic rarity, stacks to 1, fireproof, and not in any creative tab.
  Its lore is two cryptic lines. The art comes from `tools/gen_arsenal.py`.
- **Loot** (`LootTableEvents.MODIFY`, built-in tables only): an extra pool with a random-chance condition.

  | Table | Chance | Constant |
  |---|---|---|
  | `minecraft:chests/bastion_treasure` | 25% | `KEY_CHANCE_TREASURE` |
  | `minecraft:chests/bastion_other` | 5% | `KEY_CHANCE_OTHER` |

- **Use (right-click)**
  - An arsenal user with `Fate.canAscend` sees the key turn: the iron door and end-portal sounds play, the key is
    consumed, and `Fate.ascend` is called.
  - Anyone else hears the locked-chest rattle. The key isn't consumed and gets a 1 s cooldown (`RATTLE_COOLDOWN`).

### Armory (ascended form, `ArsenalArmory`)
- **Pin:** sneak + R while holding a summoned weapon. It pins that item type, saved in the `jaymospowers:arsenal_pin`
  attachment (persistent, kept through death, synced to the owner).
- **Effect:** from then on every Reach brings the pinned weapon first, into your hand, dressed as a normal pull
  (ammo, Infinity, Loyalty). The usual random pull comes too, in the same batch.
- **Replace or clear:** pinning again replaces the pin. Sneak + R with an empty hand clears it; if nothing is pinned,
  it's an ordinary Reach.
- **HUD:** shows `Armory: <weapon>` above the Hundred Blades row.
- The pin is removed when the power is lost, and ignored while not ascended.

### Hundred Blades (ascended ability, C, `HundredBlades`)
1. **Aim:** ray-cast up to 48 blocks (`RANGE`).
   - A living thing on the ray is the point.
   - Otherwise the block hit is.
   - With no hit at all, the point drops from the end of the ray to the ground below it.
   - If there's no ground there either, nothing happens and no cooldown is spent.
2. **Rifts:** 5 rifts (`PORTALS`, radius `PORTAL_RADIUS` 1.15) tear open 11 blocks above the point (`HEIGHT`), or just
   under a ceiling, but never lower than 3.5 (`MIN_HEIGHT`).
   - They are drawn flat, facing down, by every client from the synced `jaymospowers:arsenal_sky` attachment
     (`ArsenalSkyRenderer`), using the same rift drawing as Reach.
   - They become void rifts when corrupted.
3. **Blades:** 20 `RiftBlade`s (`BLADES`) hang from the rifts and drop one every 2 ticks from tick 12 (`FIRST_DROP`,
   `DROP_INTERVAL`), so about 2 s.
   - They aim at the living things within 4 blocks of the point (`RADIUS`), cycling through them and tracking them
     while they hang. With no targets they fall at random points in the radius.
4. **Damage:** each blade that hits deals a fixed 3 (`BLADE_DAMAGE`; ×1.3 when corrupted).
   - It resets the target's hurt-immunity first, so every blade lands: about 30 on one target.
   - Corrupted blades are void-tinted, glinting, and apply Darkness.
   - Sky blades can't be picked up and vanish 12 ticks after landing (`RiftBlade.SKY_LINGER_TICKS`).
5. **Cooldown:** 400 ticks, 20 s (`COOLDOWN`). It is stored in the `ultimate` field of `jaymospowers:arsenal_cooldowns`
   (backward-compatible codec). While ascended, the HUD shows it as a gold row with the bound key, above the Reach row.

### Debug commands (gamemasters)
| Command | Effect |
|---|---|
| `/powerdebug arsenal corruption <targets> <amount>` | Feeds the meter as Enderman contact would, with whispers. |
| `/powerdebug arsenal dragonkill` | Treats every corrupted arsenal user within 160 blocks of you as having fought a dragon that just died where you stand. |
| `/powerdebug arsenal purge <targets>` | Starts the cure sequence directly. |
| `/powerdebug arsenal blink <targets>` | Forces a void blink. |
| `/powerdebug arsenal ready <targets>` | Clears every arsenal cooldown, including Hundred Blades. |
| `/powerdebug arsenal pin <targets>` | Prints the Armory pin. |

### How to test
Start with `/power set @s arsenal` in **survival**. Creative is spared by `Fate.addCorruption` and by the End check.
1. **Meter:**
   - Look at an Enderman until it screams, then hit it and let it hit you.
   - Run `/power get @s` to check the meter (+2 aggro, +4 per hit, +10 per kill).
   - Whispers appear at 25, 50 and 75.
   - `/powerdebug arsenal corruption @s 30` speeds this up.
2. **End:** go through an End portal (or `/execute in minecraft:the_end run tp @s 0 100 0`). You're corrupted at once.
3. **Void visuals:**
   - Press R and check the portal is a starfield with a purple lip, and the weapon comes out dark and glinting.
   - Press V and check the Barrage blades hang in void rifts.
   - From a second player, check the aura is visible.
4. **Trade:**
   - Hit a mob with a summoned sword. It should take about 30% more damage and get Darkness and Wither.
   - Stand in rain or water and take 1 damage per second.
   - Take a heavy hit (`/damage @s 8`) a few times and blink about 30% of the time.
5. **Cure:**
   - Fight and kill the dragon while corrupted, then watch the purge.
   - Or use `/powerdebug arsenal dragonkill` or `/powerdebug arsenal purge @s`.
   - Then go back to the End and check you're not corrupted again.
6. **Key:**
   - `/give @s jaymospowers:arsenal_dimensional_key`, then right-click before mastery: you get the rattle and keep the
     key.
   - Run `/power level @s 4`, then right-click: you ascend and the key is used up.
   - Loot: `/loot give @s loot minecraft:chests/bastion_treasure` a few times.
7. **Armory:**
   - Press R, then sneak + R holding the summoned weapon: the HUD shows `Armory: ...`.
   - Press R again and the pinned weapon comes first.
   - Sneak + R with an empty hand clears the pin.
8. **Hundred Blades:**
   - Look at a mob or the ground and press C: the rifts open in the sky and 20 blades fall over about 2 s.
   - A zombie under it should take about 30.
   - `/powerdebug arsenal ready @s` resets the cooldown.

### Risky or untested at runtime
None of this has been run in game.
- **End-gateway rift drawing:**
  - The end-gateway render type is position-only and is drawn through `submitCustomGeometry` from a level-render event.
    If it renders nothing or crashes, the shader setup differs from the block-entity path.
  - The starfield is screen-projected, as on real gateways.
- **`ArsenalLivingEntityMixin`:** a `@ModifyVariable` at the HEAD of `LivingEntity.hurtServer` with MixinExtras
  `@Local(argsOnly)`. Subclasses that override `hurtServer` without calling super won't get the ×1.3.
- **Weaponmod projectiles:** guns and spears only get the ×1.3 and effects if their projectile reports the weapon
  through `getWeaponItem()`. Vanilla arrows and tridents do.
- **Dragon death:** relies on Fabric's `AFTER_DEATH` firing from `LivingEntity.die` for the dragon. The dragon's
  `handleKillingBlow` sets its health to 1 and goes into its dying phase rather than dying outright. A `/kill`ed dragon
  doesn't count.
- **Disconnect mid-purge:** depends on `ServerPlayConnectionEvents.DISCONNECT` firing before the player's data is
  saved.
- **Water and rain damage:** depends on the server's `isInWaterOrRain()` for players.
- **Blink:** a server-side `randomTeleport` on a player, from inside the damage event.
- **Hundred Blades targets:** it hits every living thing in the radius except the caster, including other players and
  pets.
