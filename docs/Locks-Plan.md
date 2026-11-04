# Locks and keys: Kingdoms 0.7.497 (patch 6)

Andy picked the idea of Locks Reforged and wants our own version, with nothing copied from it. The plan text he approved:
"Forge a lock and its key, fit it to a door, chest, trapdoor or gate. Locked blocks resist villagers, hoppers and
breaking. Anyone else uses the Pin or Rotary game you already have. Townsfolk lock their homes and shops at night, and a
break-in that's seen is a crime. Key ring in a bauble slot."

- **Version:** Kingdoms `0.7.497` (`kingdoms/gradle.properties`). Tag every new comment `(0.7.497)`.
- **Powers is not changed.** The key ring is made wearable by a Kingdoms tag overlay on Powers' bauble tag, which needs
  no Powers code, so Powers stays at 0.19.79 and nothing is tagged 0.19.83.
- **Kitchen is not changed.**
- **No new payloads or screens.** Picking uses the existing `Lockpicking.Open`/`Result` and `LockpickScreen`. The key
  ring uses the vanilla bundle's click-to-insert and tooltip.
- **26.3 API lines** below come from the scratch decompile at `/tmp/claude-0/src263`. Check each one against
  `./gradlew genSources` before coding (see section 12).

---

## 1. How it plays

| Action | What happens |
|---|---|
| Craft a **Lock** (3 iron ingots + 3 nuggets) | A fresh lock, not yet cut. Its tooltip says it comes with its key. |
| Use a fresh lock on a door, trapdoor, fence gate, chest or barrel | It's fitted and locked. A new **Key** cut to it goes into your pack (dropped if the pack is full). |
| ...with a cut key in your off hand | It's fitted to *that* key, so one key opens many locks, and no new key is made. |
| ...using a lock that's already cut (one that dropped from a broken block) | It's fitted and still answers its old key. No key is made. |
| Use a locked block with the matching key in your hand, pack, a key ring in your pack, or a key ring worn in the belt bauble slot | It opens normally. |
| Sneak-use the lock with its key in your main hand | Locks or unlocks it. An unlocked lock lets anyone through. |
| Sneak-use an *unlocked* lock with an empty main hand and its key on you | The lock comes off and is returned to you (cut, still matching its key). |
| Anyone without the key | A "locked" click and the Pin or Rotary game (the kind comes from the key id, at level 3, "fine"). They need a lockpick; without one, a message. Picked: the lock is left unlocked and the block opens. |
| Break a locked block without its key | Takes much longer: progress x0.25 to x0.05 by lock level. The lock drops, still cut. A locked loot chest is no longer unbreakable, only slow. |
| Hopper or hopper minecart under a locked container | Pulls nothing. |
| Copper golem | Ignores locked containers, the same way vanilla treats a `LockCode` container. |
| Villagers, Folk and other mobs | Can't open a locked door or gate (vanilla AI, MCA's door task, Kingdoms' `Doorways`). A realm's own folk pass through their realm's night locks. |
| Redstone or a wind charge on a locked, shut door, trapdoor or gate | It stays shut. |
| Night (13000 to 23500) | The wooden doors of a realm's houses and barracks, the doors of its shops, and its shops' chests and barrels are locked (levels 1 to 3). A village's house doors are found from each villager's bed. Player-placed blocks, blocks that already have a lock, and buildings with a player inside are skipped. In the morning all of them are unlocked. |
| Picking or breaking a lock in town, seen | **Burglary**, a medium crime (heavy against a storehouse): reputation, a cry, guards, a bounty. Asleep villagers don't see. Your own placed blocks, holding the key, and rank enough to take from stores (`Theft.exempt`) are never a crime. Picking a locked loot chest in town counts too. |
| **Key Ring** (4 nuggets in a ring) | Holds up to 16 keys (click a key onto it, right-click to take one out). It opens their locks from your pack, or worn in Powers' **belt** bauble slot. It drops on death like any bauble, with its keys inside. |
| **Blank Key** (nugget + ingot -> 2) | Craft one cut key with 1 to 8 blanks to get that many copies. The original comes back, and copies keep its name. |

---

## 2. Files

### New (Kingdoms)
| Path | Role |
|---|---|
| `kingdoms/src/main/java/com/jaymoskingdoms/Locks.java` | Core: constants, the `Fitted` record, the attachments, the `lock_id` component, the items, the `lockable` tag, register, use, fit, key checks, break slow-down and drop, the queries the mixins call, the worn-keys tick. |
| `kingdoms/src/main/java/com/jaymoskingdoms/NightLocks.java` | Night lock and morning unlock, the night index attachment, the per-night candidate cache. |
| `kingdoms/src/main/java/com/jaymoskingdoms/KeyRingItem.java` | `extends BundleItem` that accepts keys only and doesn't dump its contents on use. |
| `kingdoms/src/main/java/com/jaymoskingdoms/KeyCopyRecipe.java` | `extends CustomRecipe`: key + blanks -> copies. Holds `SERIALIZER`. |
| `kingdoms/src/main/java/com/jaymoskingdoms/mixin/locks/BlockStateBaseMixin.java` | Slow breaking (`getDestroyProgress`). |
| `kingdoms/src/main/java/com/jaymoskingdoms/mixin/locks/DoorBlockMixin.java` | `setOpen` refusal and redstone (`neighborChanged`). |
| `kingdoms/src/main/java/com/jaymoskingdoms/mixin/locks/TrapDoorBlockMixin.java` | `toggle` refusal and redstone. |
| `kingdoms/src/main/java/com/jaymoskingdoms/mixin/locks/FenceGateBlockMixin.java` | Redstone. |
| `kingdoms/src/main/java/com/jaymoskingdoms/mixin/locks/HopperBlockEntityMixin.java` | `suckInItems` refusal. |
| `kingdoms/src/main/java/com/jaymoskingdoms/mixin/locks/TransportItemsBetweenContainersMixin.java` | Copper golem respects locks. |
| `kingdoms/src/main/java/com/jaymoskingdoms/mixin/mca/McaDoorsMixin.java` | MCA `PathingBlockInteraction.setOpen` refusal (this covers MCA's fence gates, which skip `DoorBlock.setOpen`). |
| `kingdoms/src/main/resources/jaymoskingdoms.locks.mixins.json` | Mixin config for `com.jaymoskingdoms.mixin.locks`. |
| `kingdoms/src/main/resources/data/jaymoskingdoms/tags/block/lockable.json` | What a lock fits. |
| `kingdoms/src/main/resources/data/jaymoskingdoms/recipe/{lock,blank_key,key_ring,key_copy}.json` | Recipes. |
| `kingdoms/src/main/resources/data/jaymospowers/tags/item/baubles/belt.json` | Overlay: the key ring goes in the belt slot. |
| `kingdoms/src/main/resources/assets/jaymoskingdoms/items/{lock,key,blank_key,key_ring}.json` | Item model definitions. |
| `kingdoms/src/main/resources/assets/jaymoskingdoms/models/item/{lock,key,blank_key,key_ring}.json` | `minecraft:item/generated` (key: `minecraft:item/handheld`) with `layer0` set to `jaymoskingdoms:item/<id>`. |
| `kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/{lock,key,blank_key,key_ring}.png` | 16 px icons painted by `tools/gen_locks.py`. |
| `tools/gen_locks.py` | Icon painter in the style of `tools/gen_lockpick.py`. |

### Changed (Kingdoms)
| Path | Change |
|---|---|
| `Lockpicking.java` | `LOCK` syncs. The BEFORE "unbreakable" hook is removed. `halves` takes a `Level` and becomes package-private. New `pickLock(...)` (kind and level aware); `pickVirtual` delegates to it. XP by level for virtual picks. New `sealed(ServerLevel, BlockPos)` for hoppers. A picked loot chest calls `Theft.burgled`. Header doc. |
| `Theft.java` | New `Crime.BURGLARY` and `burgled(...)`. `broke()` makes a locked block BURGLARY. Header doc. |
| `Storage.java` | `containers()` skips a container under a player's lock. |
| `Doorways.java` | `open()` won't open a gate (or door) that's locked against the mob. |
| `JaymosKingdoms.java` | Registers Locks and NightLocks, with attachment debug references. |
| `kingdoms/src/main/resources/fabric.mod.json` | Adds `"jaymoskingdoms.locks.mixins.json"` to `mixins` (common). |
| `kingdoms/src/main/resources/jaymoskingdoms.mca.mixins.json` | Adds `"McaDoorsMixin"`. |
| `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/en_us.json` | Keys in section 9. Removes `message.jaymoskingdoms.lock.unbreakable`. |
| `kingdoms/gradle.properties` | `version=0.7.497` |
| `kingdoms/MODLOG.md` | Entry in section 10. |

Not touched: Powers (`src/`), Kitchen, `PowersCompat` (`baubles(Player)` already does what's needed),
`LockpickScreen`, `JaymosKingdomsClient`.

---

## 3. Ids and data

| Kind | Id | Notes |
|---|---|---|
| Item | `jaymoskingdoms:lock` | `stacksTo(16)`. With no `lock_id` it's fresh; with one it's cut. |
| Item | `jaymoskingdoms:key` | `stacksTo(KEY_STACK = 16)`. Opens nothing without `lock_id`. Not in the creative tab. |
| Item | `jaymoskingdoms:blank_key` | `stacksTo(16)`. |
| Item | `jaymoskingdoms:key_ring` | `KeyRingItem`, `stacksTo(1)`, `.component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)`. **Not** in `#minecraft:bundles`: that tag would bring the dye transmute recipe and bundle scroll-select. |
| Data component | `jaymoskingdoms:lock_id` | `DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build()`, registered before the items (the pattern in `Tomes.java:254`). Never 0 on a cut key or lock. |
| Chunk attachment | `jaymoskingdoms:fitted_locks` | `Map<String, Fitted>`, keyed by `Long.toString(anchor.asLong())` (the `Placed.MARKS` pattern). `persistent(Codec.unboundedMap(Codec.STRING, Fitted.CODEC))` plus `syncWith(map stream codec, AttachmentSyncPredicate.all())` so the client knows for crack speed and use prediction. FAPI 2.2.32 has `AttachmentTargetInfo$ChunkTarget`, so chunk sync is supported. |
| Player attachment | `jaymoskingdoms:worn_keys` | `List<Long>`, the key ids on a key ring worn as a bauble. Not persistent. `syncWith(ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())` (pattern: `Heritage.java:370`). Lets the client's crack speed match the server's. |
| Level attachment | `jaymoskingdoms:night_locks` | On the Overworld `ServerLevel`: `Map<String, List<Long>>` (faction id -> packed positions it night-locked). Persistent, not synced. |
| Block tag | `jaymoskingdoms:lockable` | `{"replace": false, "values": ["#minecraft:doors", "#minecraft:trapdoors", "#minecraft:fence_gates", "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "#minecraft:copper_chests"]}`. `BlockTags.COPPER_CHESTS` exists in 26.3 (`BlockTags.java:61`). |
| Recipe serializer | `jaymoskingdoms:key_copy` | `Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, JaymosKingdoms.id("key_copy"), KeyCopyRecipe.SERIALIZER)` |
| Crime | `Theft.Crime.BURGLARY(Weight.MEDIUM, "burglary")` | Cry key `burglary`. |

### `Locks.Fitted`
```java
/** (0.7.497) A lock on a block: the key id it answers (0: a realm's lock, no key), Pin or Rotary, its level 1-5, the
 *  realm that keeps it (-1: none), whether it's locked now, and whether it's a night lock (taken off in the morning). */
public record Fitted(long key, int kind, int level, int faction, boolean locked, boolean night) {
    CODEC: key LONG opt 0L; kind INT opt 0; level INT opt FORGED_LEVEL; faction INT opt -1; locked BOOL opt true; night BOOL opt false
    STREAM_CODEC: StreamCodec.composite(VAR_LONG, VAR_INT, VAR_INT, VAR_INT, BOOL, BOOL, Fitted::new)
    Fitted withLocked(boolean);
    boolean keyed() { return key != 0L; }
    long seed(BlockPos anchor) { return keyed() ? key : anchor.asLong() ^ NightLocks.NIGHT_SALT; }
}
```
- A keyed lock's kind is `Math.floorMod(Long.hashCode(id), Lockpicking.Kind.values().length)`. The picking seed is the key
  id, so every lock cut to one key has the same combination.
- The **anchor** is the lower half for a door. For a chest it's the half the lock was fitted to. Every lookup checks both
  halves of a double chest through `Lockpicking.halves(Level, BlockPos)`, so a second chest placed beside a locked one
  is locked with it.

---

## 4. `Locks.java` (public final class)

Header javadoc: (0.7.497) Andy's plan text; the idea is Locks Reforged's, our own build, nothing copied; a summary of
section 1.

### Constants
```java
/** A forged lock's level (Lockpicking's 1-5: "fine"). */
static final int FORGED_LEVEL = 3;
/** Breaking a locked block without its key: progress times this, by lock level 1..5. */
static final float[] BREAK_FACTOR = {0.25F, 0.15F, 0.10F, 0.07F, 0.05F};
/** Keys stack this high; a key ring (a bundle) holds this many (each key weighs 1/KEY_STACK). */
static final int KEY_STACK = 16;
/** Worn key rings are read this often (ticks). */
static final int KEYRING_REFRESH_TICKS = 20;
/** "It's locked: this will take a while" is said at most this often per player (ticks). */
static final int SLOW_MESSAGE_TICKS = 100;
```

### Registered things (static fields)
`LOCK_ID`, `LOCK`, `KEY`, `BLANK_KEY`, `KEY_RING` (Item), `LOCKS` (chunk attachment), `WORN_KEYS` (player attachment),
`LOCKABLE = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("lockable"))`.

Nested `static final class Lockwork extends Item`, used for `LOCK`, `KEY` and `BLANK_KEY`. It overrides
`appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` (the signature in
`MasonTable.java:384`):
- lock, fresh: `tooltip.jaymoskingdoms.lock.fresh` and `tooltip.jaymoskingdoms.lock.share`
- lock, cut: `tooltip.jaymoskingdoms.lock.cut` with `code(id)`
- key: `tooltip.jaymoskingdoms.key.cut` with `code(id)`, then `tooltip.jaymoskingdoms.key.how`
- blank: `tooltip.jaymoskingdoms.blank_key`

All tooltip lines are GRAY. `static String code(long id)` = `String.format("%04X", (int)((id ^ (id >>> 32)) & 0xFFFF))`.

### `register()` (called after `Lockpicking.register()`)
1. Register `LOCK_ID`, then the four items (`Registry.register(BuiltInRegistries.ITEM, key, new X(new Item.Properties().setId(key)...))`).
   Register the `key_copy` serializer.
2. Creative tab `TOOLS_AND_UTILITIES`: `insertAfter(Lockpicking.LOCKPICK, LOCK, BLANK_KEY, KEY_RING)`.
3. `UseBlockCallback.EVENT.register(Locks::use)`.
4. `AttackBlockCallback.EVENT.register(Locks::attacked)`.
5. `PlayerBlockBreakEvents.AFTER.register(Locks::broken)`. This **must** be registered after `Theft.register()` so that
   `Theft.broke` still sees the lock. JaymosKingdoms already registers Theft (:208) before Lockpicking (:229).
6. `ServerTickEvents.END_SERVER_TICK.register(Locks::tick)`: every `KEYRING_REFRESH_TICKS`, for each player compute
   `wornKeyIds(player)` and set or remove `WORN_KEYS` only when it changed.
7. `ServerLifecycleEvents.SERVER_STOPPED` and `ServerPlayConnectionEvents.DISCONNECT`: clear `SLOW_TOLD`
   (`Map<UUID, Long>`).

### `use(Player, Level, InteractionHand, BlockHitResult)`, in order
- Spectators: PASS.
- **Client** (`level.isClientSide()`): return SUCCESS, so the packet is sent but the door isn't predicted open, when:
  - the main hand holds `LOCK` and the block is `LOCKABLE`, or
  - `entry(level, pos)` is locked and `!player.isCreative()` and `!holdsKey(player, entry.key())`, or
  - the player is sneaking with a matching key in the main hand.

  Otherwise PASS. (Fabric: SUCCESS on the client sends the packet; FAIL would not send it. Check this in the 26.3
  UseBlockCallback javadoc.)
- **Server:**
  1. If the main hand holds `LOCK` (and `hand == MAIN_HAND`): `fit(...)`, then return SUCCESS (FAIL if refused, after
     the message).
  2. `Fitted f = entry(level, pos)`. If it's null: PASS.
  3. Sneaking, main hand is a `KEY` with `lock_id == f.key()`, keyed lock: toggle `locked`. Shut an open door, gate or
     trapdoor before locking (`DoorBlock.setOpen(player, ..., false)`; gates and trapdoors with `setBlock(OPEN false)`).
     Play `SoundEvents.IRON_TRAPDOOR_CLOSE`/`OPEN` at volume 0.6 and pitch 1.6. Overlay `message.jaymoskingdoms.locks.locked`
     or `unlocked`. SUCCESS. A key that doesn't match: `locks.wrong_key`, FAIL.
  4. Sneaking, empty main hand, keyed lock that's unlocked, `holdsKey`: remove the entry, give a cut `LOCK` stack with
     `lock_id`, overlay `locks.removed`. SUCCESS.
  5. If it's not locked, or the player is creative, or `mayPass(player, f, level)`: PASS (opens normally).
  6. Play `SoundEvents.CHEST_LOCKED`. Off hand: FAIL. Then
     `Lockpicking.pickLock(sp, anchor, Kind.values()[f.kind()], f.level(), f.seed(anchor), () -> picked(sp, anchor))`.
     If that's false (no lockpick), overlay
     `message.jaymoskingdoms.locks.no_key.<pin|rotary>` (`%s` = `message.jaymoskingdoms.lock.level.<n>`), with
     `locks.night` first for a night lock. Return FAIL. If it's true, return SUCCESS.

### `fit(ServerPlayer, ServerLevel, BlockPos, BlockState, ItemStack lock)`
Refusals, each with an overlay in GRAY:
- the block isn't `LOCKABLE`: PASS (not handled)
- `Lockpicking.lockLevel(level, pos) > 0` or `entry(level, pos) != null`: `locks.already`
- `Factions.storageOwner(server, pos) != null`: `locks.storehouse`
- `Theft.lawAt(level, pos)` is a faction, and `!Placed.byPlayer(sp, level, pos, state)` and `!Theft.exempt(sp, f)`:
  `locks.not_yours`

The id:
- the stack's `lock_id` if it has one (`locks.refitted`, with the code)
- else the off-hand `KEY`'s `lock_id` (`locks.fitted_to_key`)
- else a new `level.getRandom().nextLong()`, rerolled while it's 0. Give a `KEY` with that id
  (`getInventory().add`, else `drop`). Message `locks.fitted`.

Write `Fitted(id, kind(id), FORGED_LEVEL, -1, true, false)` at the anchor, then shut the block if it's open. Shrink the
stack unless creative. Play `SoundEvents.IRON_TRAPDOOR_CLOSE` at volume 0.8 and pitch 1.4.

### `picked(ServerPlayer, BlockPos anchor)` (the pick game was won)
Read the entry again (if it's gone, return). A night lock: remove it. Else `withLocked(false)`. Then
`Theft.burgled(sp, level, anchor, state)`. Overlay `message.jaymoskingdoms.lock.picked` in GREEN. Then
`state.useWithoutItem(level, sp, new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false))` opens it,
as Lockpicking does.

### `attacked(...)` (AttackBlockCallback, server only, returns PASS)
If the block entity is a chest or barrel: `Lockpicking.locked(server, pos)` reads a loot chest now, so its lock syncs
before the crack starts. Then, if `breakFactor(level, pos, state, player) < 1` and `SLOW_TOLD` allows, overlay
`message.jaymoskingdoms.locks.slow`.

### `broken(...)` (PlayerBlockBreakEvents.AFTER, server)
`Fitted f = entryAt(anchor)`. If it's null, return. Remove it. A keyed lock, broken by a player who isn't creative:
`Block.popResource(level, pos, cut LOCK with lock_id)`. Faction (night) locks drop nothing.

### Queries (public where a mixin calls them)
```java
/** The lock on the block at pos (door: its lower half; chest: either half), or null. Server: forgets one whose block is gone. */
static @Nullable Fitted entry(Level level, BlockPos pos);
/** Whether player carries the key cut to id: hands, pack (36 + equipment), key rings in the pack, the worn key ring
 *  (server: PowersCompat.baubles; client: WORN_KEYS). Always false for id 0. */
public static boolean holdsKey(Player player, long id);
/** Whether who may open f: creative player; holdsKey; a realm's lock: a ServerPlayer with Theft.exempt, Folk with
 *  factionId() == f.faction(), a Villager whose Theft.lawAt(level, its pos) is f.faction(). Null and everyone else: no. */
static boolean mayPass(@Nullable Entity who, Fitted f, Level level);
/** A locked lock on the block that who may not pass (DoorBlock.setOpen, TrapDoorBlock.toggle, MCA, Doorways). */
public static boolean barred(Level level, BlockPos pos, @Nullable Entity who);
/** A locked lock on the shut block (redstone checks). */
public static boolean shut(Level level, BlockPos pos);
/** Its container may not be pulled from: a locked fitted lock, or a locked loot lock (Lockpicking.sealed). Server. */
public static boolean sealed(ServerLevel level, BlockPos pos);
/** A player's lock (keyed) that keeps faction's own folk out (Storage). */
static boolean barredTo(ServerLevel level, BlockPos pos, Faction faction);
/** Theft.broke: the block just broken was locked against player (fitted without their key, or a locked loot lock on entity). */
static boolean lockedAgainst(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, ServerPlayer player);
/** Break progress factor for player on this block: 1, or BREAK_FACTOR[level - 1] when locked against them. Both sides. */
static float breakFactor(Level level, BlockPos pos, BlockState state, Player player);
/** Mixin entry: original * breakFactor, unless !state.is(LOCKABLE) or creative, or level isn't a Level. */
public static float slowBreak(BlockState state, Player player, BlockGetter level, BlockPos pos, float original);
```
`breakFactor` has two sources of locks:
- the fitted lock: slow when locked and `!holdsKey`. A faction lock is always slow, even for exempt players, because
  the client can't know rank; this keeps the crack and the server in step.
- Lockpicking's `LOCK` on the block entity (either half): if `locked()`, it uses that lock's level. The client reads the
  synced value.

Performance: `slowBreak` returns at once unless `state.is(LOCKABLE)`. Lookups are one chunk attachment `get` plus a
`Map.get`.

---

## 5. `NightLocks.java` (final class)

Header: (0.7.497) Townsfolk lock their homes and shops at night (Andy's plan). Ownership-aware: player-placed blocks
are theirs (`Placed`). The state comes from the clock every pass, never toggled, so a sleep skip (`Nights`) or a restart
lands right.

```java
static final int EVERY = 100;                      // ticks between passes
static final long LOCK_FROM = 13000L, UNLOCK_AT = 23500L;   // time of day (Royals.timeOfDay); UNLOCK_AT = Homes.WAKE
static final Set<String> HOMES = Set.of("house", "barracks");
static final Set<String> SHOPS = Set.of("market", "stall", "tavern", "smithy", "butcher", "cookhouse", "mason");
static final double HOME_DOOR_RANGE = 6.0;         // doors this near a villager's bed are their house's (Trespass.HOUSE_RANGE)
static final int HOME_DOOR_HEIGHT = 3;
static final int MOST = 256;                       // night locks per realm at most
static final int LEVEL_VILLAGE = 1, LEVEL_FIEFDOM = 2, LEVEL_KINGDOM = 2, SHOP_BONUS = 1;
static final long NIGHT_SALT = 0x6E696768746CL;    // a night lock's pick seed: anchor ^ this
```
- `INDEX` is the level attachment `jaymoskingdoms:night_locks` (section 3).
- `CANDIDATES` is a transient `Map<Integer, List<Candidate>>`, where `record Candidate(BlockPos anchor, AABB house, boolean shop)`.
  It's built at the first pass of a night and cleared in the morning or when the server stops.

`register()`: `END_SERVER_TICK` -> `tick`, `SERVER_STOPPED` -> clear `CANDIDATES`.

`tick(server)`: `level = Factions.level(server)`. Run only on `getGameTime() % EVERY == 0`. `night = t >= LOCK_FROM ||
t < UNLOCK_AT`, with `t = Royals.timeOfDay(level)`. For each `f` in `Factions.all(server)`:
- skip if `f.type() == BARBARIAN` or `!level.isPositionEntityTicking(f.seat())`
- if `night && !f.fallen()`: `lock(level, f)`, else `unlock(level, f)`

`lock(level, f)`:
- Candidates (built once a night):
  - doors in `Works.of(server, f.id()).built()` entries in `HOMES` and `SHOPS`, over `BlockPos.betweenClosed(b.min(), b.top())`,
    skipping ruined ones (`b.ruined() != 0`). The house box is the building's bounds.
  - in `SHOPS` also chest, trapped chest, barrel and copper chests, but not `f.holdings().storage()` positions.
  - for each `Villager` within `Villages.VILLAGE_RADIUS` of the seat whose `Theft.lawAt` is `f`: `McaCompat.home(v)`
    (same dimension). Doors in `#minecraft:wooden_doors` within `HOME_DOOR_RANGE` horizontally and `HOME_DOOR_HEIGHT`
    vertically of the bed. The house box is the bed inflated by those amounts.
  - Only the lower half of a door. Deduplicate. Cap at `MOST`.
- For each candidate without a lock yet:
  - skip if not loaded, `Placed.any(level, anchor)`, or `Locks.entry(...) != null`, or the block is no longer lockable
  - skip **for this pass** if a non-spectator player stands inside its house box (nobody is locked in). It's tried again
    next pass.
  - shut it if it's open and its doorway is empty (`DoorBlock.setOpen(null, ..., false)`; gate `setBlock`). If the
    doorway is occupied, skip this pass.
  - write `Fitted(0L, floorMod(seed, 2), level, f.id(), true, true)`, with `level = by type + (shop ? SHOP_BONUS : 0)`,
    clamped 1..5. Add the anchor to `INDEX[f]`.
  - Candidates that got a lock are dropped from the list, so later passes are cheap.

`unlock(level, f)`: `CANDIDATES.remove(f.id())`. For each indexed position in a loaded chunk, remove the entry if it's
`night` and `faction == f.id()`, and drop it from the index. Unloaded ones stay in the index for the next pass. Remove the
faction's key from the index when it's empty.

No message and no sound at locking time. A player trying the door is told `locks.night`.

---

## 6. `KeyRingItem` and `KeyCopyRecipe`

**`KeyRingItem extends BundleItem`** (26.3 `BundleItem.java:31`):
- `overrideStackedOnOther(self, slot, action, player)`: if `action == PRIMARY` and the slot's item isn't empty and isn't
  a `KEY`, return false (a normal swap). Otherwise `super`.
- `overrideOtherStackedOnMe(self, other, slot, action, player, carried)`: if `action == PRIMARY` and `other` isn't empty
  and isn't a `KEY`, return false. Otherwise `super`.
- `use(...)`: return `InteractionResult.PASS` (vanilla starts a use that empties the bundle).
- `appendHoverText`: `tooltip.jaymoskingdoms.key_ring`. The bundle's own grid tooltip still shows its keys.
- Keys stack to 16, so each weighs 1/16 and a ring holds 16 keys.
- `static LongStream ids(ItemStack ring)` reads the keys through
  `getOrDefault(BUNDLE_CONTENTS, EMPTY).itemCopies()`, filtered to `KEY` and mapped to `lock_id`.

**`KeyCopyRecipe extends CustomRecipe`** (pattern: `BookCloningRecipe`):
- `static final KeyCopyRecipe INSTANCE`
- `SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE))`
- `matches`: exactly one `KEY` with a `lock_id`, at least one `BLANK_KEY`, nothing else.
- `assemble(CraftingInput)`: `TransmuteRecipe.createWithOriginalComponents(new ItemStackTemplate(Locks.KEY), source, blanks - 1)`
  copies the id and the name.
- `getRemainingItems`: the source key comes back (`copyWithCount(1)`) in its slot. Other slots get their crafting
  remainder, as `BookCloningRecipe` does.
- `getSerializer`: `SERIALIZER`. `category()` stays MISC.

---

## 7. Mixins

`jaymoskingdoms.locks.mixins.json`:
```json
{
	"required": true,
	"package": "com.jaymoskingdoms.mixin.locks",
	"compatibilityLevel": "JAVA_25",
	"mixins": ["BlockStateBaseMixin", "DoorBlockMixin", "TrapDoorBlockMixin", "FenceGateBlockMixin",
		"HopperBlockEntityMixin", "TransportItemsBetweenContainersMixin"],
	"injectors": { "defaultRequire": 1 }
}
```
Add it to `fabric.mod.json` `mixins` (a common entry, after `jaymoskingdoms.omni.mixins.json`).

| Mixin | Target (26.3) | Injection |
|---|---|---|
| `BlockStateBaseMixin` | `BlockBehaviour.BlockStateBase#getDestroyProgress(Player, BlockGetter, BlockPos)` (public, `BlockBehaviour.java:647`; covers blocks that override the protected one) | `@ModifyReturnValue` -> `Locks.slowBreak((BlockState)(Object)this, player, level, pos, original)` |
| `DoorBlockMixin` | `DoorBlock#setOpen(@Nullable Entity, Level, BlockState, BlockPos, boolean)` (:212; vanilla AI `InteractWithDoor`, `DoorInteractGoal`, wind charge `onExplosionHit`, MCA, `Doorways`, `BoltWorks`) | `@Inject HEAD cancellable`: `if (shouldOpen && Locks.barred(level, pos, source)) ci.cancel()` |
| | `DoorBlock#neighborChanged(...)` (:221) | `@Inject HEAD cancellable`: `if (!state.getValue(OPEN) && Locks.shut(level, pos)) ci.cancel()` |
| `TrapDoorBlockMixin` | private `TrapDoorBlock#toggle(BlockState, Level, BlockPos, @Nullable Player)` (:102; use, wind charge, MCA) | HEAD cancellable: `if (!state.getValue(OPEN) && Locks.barred(level, pos, player)) ci.cancel()` |
| | `TrapDoorBlock#neighborChanged` (:120) | as the door |
| `FenceGateBlockMixin` | `FenceGateBlock#neighborChanged` (:172) | as the door. Player use goes through UseBlockCallback; `Doorways` and MCA have their own checks. |
| `HopperBlockEntityMixin` | `HopperBlockEntity#suckInItems(Level, Hopper)` static (:218; also `MinecartHopper.suckInItems` :105) | HEAD cancellable: `if (level instanceof ServerLevel s && Locks.sealed(s, BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ()))) cir.setReturnValue(false)`. Don't touch `getContainerAt` (`MasonTable.java:719`) or `ejectItems`. |
| `TransportItemsBetweenContainersMixin` | private `isContainerLocked(TransportItemTarget)` (:330, copper golem) | `@ModifyReturnValue`: `locked \|\| target.blockEntity().getLevel() instanceof ServerLevel s && Locks.sealed(s, target.pos())` |
| `mixin/mca/McaDoorsMixin` | `net.conczin.mca.entity.ai.PathingBlockInteraction#setOpen(Entity, Level, BlockState, BlockPos, boolean)` static, returns boolean (seen in `libs/mca-fabric-7.8.0-dev...jar`; it calls `DoorBlock.setOpen`, `TrapDoorBlock.toggle` and `Level.setBlock` for gates) | HEAD cancellable: `if (open && Locks.barred(level, pos, entity)) cir.setReturnValue(false)`. Add to `jaymoskingdoms.mca.mixins.json` (plugin-gated on MCA). |

Name injector methods `jaymoskingdoms$...`, the house convention. Each mixin's javadoc carries (0.7.497) and its reason.

---

## 8. Changes to existing classes

**`Lockpicking.java`**
- `LOCK`: `builder.persistent(Lock.CODEC).syncWith(Lock.STREAM_CODEC, AttachmentSyncPredicate.all())`. Add
  `Lock.STREAM_CODEC = StreamCodec.composite(BOOL, locked, VAR_INT, kind, VAR_INT, level, VAR_LONG, seed, Lock::new)`.
- Remove the `PlayerBlockBreakEvents.BEFORE` hook (:175-186). `Locks` slows the break instead.
- `halves(ServerLevel, BlockPos)` becomes `static List<BlockPos> halves(Level, BlockPos)` (package-private).
- `VIRTUAL` becomes `Map<UUID, Pending>` with `private record Pending(Runnable picked, int level)`.
  `virtualResult` grants `XP_PICK + XP_PER_LEVEL * pending.level()`.
- New
  `public static boolean pickLock(ServerPlayer sp, BlockPos at, Kind kind, int level, long seed, Runnable picked)`: the
  body of today's `pickVirtual`, with `kind.ordinal()` and the level. `pickVirtual(sp, at, level, seed, picked)` becomes
  `return pickLock(sp, at, Kind.PIN, level, seed, picked);`, so StoryHard is unchanged.
- New `static boolean sealed(ServerLevel level, BlockPos pos)`. For each half: if the block entity has `LOCK`, use
  `locked()`. Otherwise, if it's a vanilla `RandomizableContainer` with a loot table (not Lootr), `lockOf` reads it now:
  a hopper under an unread dungeon chest must not drain it. Otherwise false. Cheap after the first read.
- `result()` PICKED: after the halves are set `Lock.OPEN` and before `useWithoutItem`, call
  `Theft.burgled(player, level, pos, state)`.
- Header doc: `(0.7.497) A locked loot chest is slow to break, not unbreakable (Locks), and picking one in town is burglary.`
- Keep `locked(ServerLevel, BlockPos)` as it is (the QuietOne comment refers to it), and keep `lockLevel` and `pickBySpirit`.

**`Theft.java`**
- Crime: `/** (0.7.497) a lock picked or a locked block broken where a realm's law holds ({@link Locks}). */ BURGLARY(Weight.MEDIUM, "burglary")`.
- New:
  ```java
  /** (0.7.497) player picked the lock at pos: burglary if its realm's people see it (heavy against a storehouse). */
  static void burgled(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
      if (player.isCreative() || level != Factions.level(level.getServer()) || Placed.byPlayer(player, level, pos, state)) return;
      Faction owner = Factions.storageOwner(level.getServer(), pos);
      Faction f = owner != null ? owner : lawAt(level, pos);
      if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN || exempt(player, f)) return;
      commit(player, level, f, Crime.BURGLARY, pos, owner != null ? Weight.HEAVY : null, false);
  }
  ```
- `broke()`: right after the `exempt` return, add
  `if (Locks.lockedAgainst(level, pos, state, entity, player)) { commit(player, level, f, Crime.BURGLARY, pos, owner != null ? Weight.HEAVY : null, false); return; }`.
  This makes a locked door, gate or trapdoor a crime, and a locked chest burglary rather than CHEST.
- Header javadoc: `(0.7.497) Picking a lock or breaking a locked block in a faction's land is burglary ({@link Locks}).`

**`Storage.java`** `containers()`: add `&& !Locks.barredTo(level, pos, faction)` to the condition, with the comment
`(0.7.497) not one under a player's lock`.

**`Doorways.java`** `open()`: first line, `if (Locks.barred(level, at, mob)) return false;` with the comment
`(0.7.497) a locked door or gate they've no key to stays shut; fence gates skip DoorBlock.setOpen`.

**`JaymosKingdoms.java`**, right after `Lockpicking.register();`:
```java
// (0.7.497) locks and keys: fitted locks, night locks on townsfolk's homes and shops, the key ring (and their attachments)
LOGGER.debug("locks attachments: {} {} {}", Locks.LOCKS, Locks.WORN_KEYS, NightLocks.INDEX);
Locks.register();
NightLocks.register();
```

---

## 9. Recipes, tags, models, textures, lang

**Recipes** (`kingdoms/src/main/resources/data/jaymoskingdoms/recipe/`). None of these patterns clash with vanilla,
Kingdoms, Powers or Kitchen; iron-only patterns were checked across the repo.
- `lock.json`: shaped, category `misc`, `[" N ", "N N", "III"]`, N `minecraft:iron_nugget`, I `minecraft:iron_ingot`,
  result `jaymoskingdoms:lock` x1
- `blank_key.json`: shaped, category `misc`, `["NI"]`, result `jaymoskingdoms:blank_key` x2
- `key_ring.json`: shaped, category `misc`, `[" N ", "N N", " N "]`, result `jaymoskingdoms:key_ring` x1
- `key_copy.json`: `{"type": "jaymoskingdoms:key_copy"}`

**Bauble overlay** `kingdoms/src/main/resources/data/jaymospowers/tags/item/baubles/belt.json`:
`{"replace": false, "values": ["jaymoskingdoms:key_ring"]}`. Without Powers the tag is simply unused.

**Item definitions** `assets/jaymoskingdoms/items/<id>.json`: `{"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/<id>"}}`.
Models: `key` uses parent `minecraft:item/handheld`; `lock`, `blank_key` and `key_ring` use `minecraft:item/generated`.

**Textures** (`tools/gen_locks.py`, the `gen_lockpick.py` palette: outline `(40,40,46)`, iron
`(232,232,232)/(170,170,178)/(108,108,118)`; 16x16, vanilla style):
- `lock.png`: a padlock. A rounded iron shackle (nugget-thin arch over the top 6 rows) on a squat body 8 wide by 7 high
  with a dark keyhole, two-tone shading, light from the top left.
- `key.png`: a diagonal key, bottom left to top right. A ring bow (4x4 with a hole), a 1 px shaft, two bit teeth at the
  top right. A brass tint (`(222,180,82)/(170,128,48)/(110,80,28)`) so it reads as cut against the blank.
- `blank_key.png`: the same silhouette in plain iron with no teeth.
- `key_ring.png`: an iron hoop (an 11 px circle, 1 px thick) with two small keys hanging from it, one brass and one iron.

Then run `python -I tools/excalibur_style/grade.py` so the optional Excalibur pack gets graded copies (`--check` should
list none missing).

**Lang** (`kingdoms/src/main/resources/assets/jaymoskingdoms/lang/en_us.json`). Add these and remove
`message.jaymoskingdoms.lock.unbreakable`. Check the file has no duplicate keys after the edit.
```json
"item.jaymoskingdoms.lock": "Lock",
"item.jaymoskingdoms.key": "Key",
"item.jaymoskingdoms.blank_key": "Blank Key",
"item.jaymoskingdoms.key_ring": "Key Ring",
"tooltip.jaymoskingdoms.lock.fresh": "Comes with its key. Use it on a door, trapdoor, fence gate, chest or barrel to fit it.",
"tooltip.jaymoskingdoms.lock.share": "Hold a cut key in your off hand to fit it to that key instead.",
"tooltip.jaymoskingdoms.lock.cut": "Cut to key %s.",
"tooltip.jaymoskingdoms.key.cut": "Opens lock %s.",
"tooltip.jaymoskingdoms.key.how": "Works from your hand, your pack or a key ring. Sneak-use it on its lock to lock or unlock it; sneak-use an unlocked lock empty-handed to take it off.",
"tooltip.jaymoskingdoms.blank_key": "Craft it with a cut key to copy that key.",
"tooltip.jaymoskingdoms.key_ring": "Holds up to 16 keys: click a key onto it. Opens their locks from your pack or worn in your belt bauble slot.",
"message.jaymoskingdoms.locks.fitted": "Lock fitted. Its key is in your pack.",
"message.jaymoskingdoms.locks.fitted_to_key": "Lock fitted to key %s.",
"message.jaymoskingdoms.locks.refitted": "Lock fitted. It still answers to key %s.",
"message.jaymoskingdoms.locks.already": "There's a lock on it already.",
"message.jaymoskingdoms.locks.not_yours": "That isn't yours to lock.",
"message.jaymoskingdoms.locks.storehouse": "The realm keeps its own locks on its stores.",
"message.jaymoskingdoms.locks.locked": "Locked.",
"message.jaymoskingdoms.locks.unlocked": "Unlocked.",
"message.jaymoskingdoms.locks.removed": "You take the lock off.",
"message.jaymoskingdoms.locks.wrong_key": "That key doesn't fit this lock.",
"message.jaymoskingdoms.locks.night": "Locked up for the night.",
"message.jaymoskingdoms.locks.no_key.pin": "Locked: a %s pin lock. You'll need its key, or a lockpick.",
"message.jaymoskingdoms.locks.no_key.rotary": "Locked: a %s rotary lock. You'll need its key, or a lockpick.",
"message.jaymoskingdoms.locks.slow": "It's locked and banded with iron: breaking it will take a while.",
"message.jaymoskingdoms.theft.cry.burglary.0": "Burglar! Someone's breaking in!",
"message.jaymoskingdoms.theft.cry.burglary.1": "Housebreaker! Guards, guards!",
"recall.jaymoskingdoms.crime.burglary": "burglary",
"bounty.jaymoskingdoms.crime.burglary": "burglary"
```
(`locks.night` is sent as its own overlay line when a night lock is tried without a pick. With a pick, the game opens.
Cries take `SHOUT_VARIANTS = 2`; `Recollections.crime` and `Bounties.crimeBy` read `recall.`/`bounty.` by the cry.)

---

## 10. MODLOG (`kingdoms/MODLOG.md`, appended)

```markdown
## 0.7.497: locks and keys (pack 0.19.79, Kitchen 0.5.55)
Andy picked the idea of Locks Reforged; this is our own build of it, nothing copied (`Locks`, `NightLocks`).
- **Lock and key**: three iron ingots and three nuggets forge a lock. Use it on a door, trapdoor, fence gate, chest or
  barrel to fit it, and its key (a new one, cut to that lock) goes in your pack. Hold a cut key in your off hand while
  fitting and the lock is cut to that key instead, so one key can open your whole house. Keys carry a short code
  ("Opens lock 4F2A") and can be renamed at an anvil.
- **Locked**: only the matching key opens it, in your hand, your pack, or on a key ring. Sneak-use with the key to lock
  or unlock; sneak-use an unlocked lock empty-handed to take it off. Everyone else gets the Pin or Rotary game
  (a "fine" level 3 lock; every lock cut to one key shares its combination); picked, it's left unlocked.
- **Resists villagers, hoppers and breaking**: villagers, folk and MCA can't open a locked door or gate (a realm's own
  folk pass its night locks); redstone and wind charges don't open it; hoppers, hopper minecarts and copper golems
  can't take from a locked container. Breaking a locked block without its key is 4 to 20 times slower (by lock
  level), and the lock drops, still cut. Locked loot chests are no longer unbreakable, only slow, and a hopper under
  an unread one reads its lock first.
- **Night locks**: from dusk (13000) to dawn (23500) a realm locks its houses' and barracks' doors and its shops'
  doors, chests and barrels (market, stall, tavern, smithy, butcher, cookhouse, mason; never its storehouse), and a
  village the doors by each villager's bed. Blocks a player placed, or already locked, are left alone, and nobody is
  locked in: a building with a player inside waits. A sleep skip or a restart lands right.
- **Burglary**: picking a lock or breaking a locked block where a realm's law holds, seen, is a medium crime (heavy
  against a storehouse): a cry, reputation, guards, a bounty. Sleeping villagers see nothing. Your own blocks, holding
  the key, and rank enough to take from its stores never count. Picking a locked loot chest in town counts too.
- **Blank keys and copies**: a nugget and an ingot make two blank keys; a cut key with up to eight blanks at a
  crafting table makes that many copies (names kept), and the key comes back.
- **Key ring**: four nuggets in a ring. Click keys onto it (up to 16); right-click to take one out. It opens their
  locks from your pack, or worn in Jaymo's Powers' belt bauble slot (by tag; no Powers change). It drops on death like
  any bauble, keys inside. It doesn't show at the hip.
- Mixins (`mixin/locks`): break speed (`BlockStateBase.getDestroyProgress`, so the crack matches on both sides),
  `DoorBlock.setOpen`/`neighborChanged`, `TrapDoorBlock.toggle`/`neighborChanged`, `FenceGateBlock.neighborChanged`,
  `HopperBlockEntity.suckInItems`, the copper golem's `isContainerLocked`, MCA's `PathingBlockInteraction.setOpen`.
  Locks are kept per chunk and synced; Lockpicking's loot lock now syncs too.
```

---

## 11. Order of work (one editor track)

The files interlock (Locks <-> Theft <-> Lockpicking), so this is **one** editor track in its own worktree, not two
parallel ones:
1. Lockpicking changes (section 8), then Theft `BURGLARY` and `burgled`.
2. `Locks` (component, items, attachments, tag, use, fit, queries, break), `KeyRingItem`, `KeyCopyRecipe`, resources,
   lang.
3. Mixins and their config, plus `fabric.mod.json` and the MCA config.
4. `NightLocks`, then the Storage and Doorways one-liners, then the JaymosKingdoms registration.
5. Textures (`tools/gen_locks.py`, then the grade), the version, the MODLOG.
6. `cd kingdoms && ./gradlew build`. Run a JSON duplicate-key check on `en_us.json`. No dev client or server runs unless
   Andy asks.

Then an independent verifier pass over the diff (CLAUDE.md). Manual test list for when Andy wants an in-game check:
- fit a lock to a door and a double chest (and its other half)
- a second player is refused and gets the game
- picked: it opens
- break it slowly, and the lock drops
- a hopper under a locked barrel stays empty
- a copper golem skips the chest
- a villager is stopped at a locked door
- a button doesn't open it
- a wind charge doesn't open it
- at night a village house door is locked and a faction villager walks through
- `/time set day` unlocks it
- picking in view of a villager draws the burglary cry
- the key ring works in the belt slot
- copy recipe output is right (with a name)

---

## 12. Check against genSources before coding

- `BlockBehaviour.BlockStateBase#getDestroyProgress(Player, BlockGetter, BlockPos)` is public and returns float.
- `DoorBlock#setOpen` and `#neighborChanged` signatures (with `Orientation` and `movedByPiston`).
- `TrapDoorBlock#toggle` (private, `@Nullable Player`) and `#neighborChanged`; `FenceGateBlock#neighborChanged`.
- `HopperBlockEntity#suckInItems(Level, Hopper)` is static, and `Hopper#getLevelX/Y/Z`.
- `TransportItemsBetweenContainers#isContainerLocked`, and that `TransportItemTarget` is a record with `pos()` and
  `blockEntity()`.
- `BundleItem` `overrideStackedOnOther`/`overrideOtherStackedOnMe`/`use` signatures; `BundleContents.itemCopies()`; that
  a 16-stack key weighs 1/16.
- `RecipeSerializer` is a record `(MapCodec, StreamCodec)`; `CustomRecipe` abstract methods;
  `CraftingRecipe#assemble(CraftingInput)` has no registries argument; `TransmuteRecipe.createWithOriginalComponents(template, input, extraCount)`.
- `StreamCodec.composite` arity 6; `ByteBufCodecs.map(...)`; `ByteBufCodecs.VAR_LONG`.
- FAPI `UseBlockCallback` client semantics: SUCCESS sends the packet, FAIL does not.
- FAPI chunk attachment sync (`syncWith` on a `LevelChunk` target) and level (world) attachment persistence.
- `Item#appendHoverText` signature (MasonTable uses it).
- MCA `PathingBlockInteraction.setOpen` return meaning (true = changed?), from the jar in `libs/`.

---

## 13. Risks and things left out

- **Pathing:** a mob that plans through a locked door walks up to it and stops there, as at an iron door. Telling
  `WalkNodeEvaluator` about locks (it reads a `PathNavigationRegion`, not the level) is left out.
- **Pistons** can push a fence gate or trapdoor off its lock; the lock is forgotten lazily. Explosions destroy locked
  blocks as usual.
- **Zombies breaking doors** (`BreakDoorGoal`) aren't slowed. The plan's "breaking" is read as players breaking.
- **Key ring at the hip:** `jaymospowers:hip_shown` doesn't exist on master. Drawing the ring at the hip needs a Powers
  change (a tag plus a `Baubles.SHOWN` extension) and would be Powers 0.19.83.
- **Exempt players and faction locks:** realm members of rank still see the slow crack on a night lock (the client
  can't know their rank). They can open it normally.
- **Other mods' pipes and item movers** aren't covered. Only vanilla hoppers, hopper minecarts and copper golems are.

## 14. Open questions for Andy
1. Belt slot for the key ring, or the charm slot too? It's a one-line tag either way.
2. Should the key ring show at the hip like the lantern? That's a Powers change.
3. Break slowdown of 4x (simple) to 20x (legendary), with forged locks at 10x. Right feel?
4. Should zombies' door breaking also respect locks?
