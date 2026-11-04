# Gem Sockets (Patch 5): implementation spec

Powers **0.20.7** (`gradle.properties`, was 0.20.0). Kingdoms is **not** changed (stays 0.7.498, no loading hint).
Worktree `/home/user/sockets` (base `5b77035ae`). Tag new code comments `(0.20.7)`.

Andy's approved plan: *"Gems cut in patch 4 socket into forged weapons and armour at the Smithing Anvil. Better grades
get more sockets (a Masterwork gets two). Each gem adds a small bonus by type."* Andy chose the idea of Immersive
Apotheosis (docs/Otectus-Plan.md row 5). This is our own version: none of its code, art or data is used.

Scope: socket counts by forging grade, socketing and unsocketing at the Smithing Anvil, a bonus for each socketed gem,
and tooltip lines. No new items, blocks, screens, packets, recipes, textures, keys or attachments.

---

## 0. Decisions taken (and why)

| # | Question | Decision |
|---|---|---|
| D1 | Which grades get sockets | `Grade` has five ids (no "Common"/"Fine"). **Poorly 0, Well 0, Expertly 1, Perfectly 1, Masterwork 2.** Well Forged is the usual result (60% of graded loot, 62% of trades), so sockets stay a reward for good hammering. |
| D2 | What can be socketed | **Forged weapons and armour that `Grades.bake` handles:** the stack has `jaymospowers:forging_grade`, a stack size of 1, is not a `TinkerTool`, not a `BaubleItem` (jewellery is graded too), not a head (`WeaponPartItem`/`ToolPartItem`), and `Grades.gearSlot(stack)` is not null (melee by bake's own rule, or armour with an `ARMOR` entry). This covers forged vanilla swords/axes/tools/spears, Armory and WeaponMod weapons and forged armour. |
| D3 | Tinkering tools | **Out of scope.** They already have modifier slots, and `Grades.bake` hands them to `ToolStats.apply`, which rebuilds their attributes on its own. `Sockets.socketable` refuses them. |
| D4 | Where | **Any Smithing Anvil block**, iron or stone (`instanceof SmithingAnvilBlock`; both share `SmithingAnvilEntity`). Socketing doesn't count as a forging, so it doesn't wear a stone anvil. Whatever lies on the anvil doesn't matter. |
| D5 | Control | **Sneak + use the anvil**, piece in the **main hand**, the other thing in the **off hand** (the grindstone pattern, `Finishing.grindstone`). There's no screen and no key. Off hand cut gem: set it in the next free socket. Off hand tongs: pull the last gem out whole. Empty off hand: prise the last gem out, and it shatters (use twice to confirm). |
| D6 | Removal | **The gem is lost unless you use tongs** (the anvil's existing take tool). Tongs take `TONGS_WEAR` damage. A plain prise asks first ("use again within 2 s"), so a stray sneak-click never destroys a gem. |
| D7 | Bonus | The gem's own `Gems.Gem` attribute, amount and operation, times `CutGem.scale()` (the patch 4 grade × cut factor, reused unchanged), times `WEAPON_FACTOR` (1.0) or `ARMOUR_FACTOR` (0.5). The forging grade of the piece only sets the **count**. Chips already lowered the cut grade, so they add nothing more. |
| D8 | Where the bonus lives | **Item-side `ATTRIBUTE_MODIFIERS`**, added inside `Grades.bake` through a new hook list `Grades.ATTRIBUTE_PASSES`. They go in after the grade modifiers and before `Wear.restrict`, so a broken piece loses its gem bonuses with everything else and gets them back on repair. The bauble player-attribute route (`Baubles.refresh`) is **not** used. |
| D9 | Slot of a modifier | Melee: `MAINHAND`. Armour: the slot of the prototype's `ARMOR` entry (`HEAD`/`CHEST`/`LEGS`/`FEET`). This is the same rule as `Grades.bake`. |
| D10 | Modifier ids | `jaymospowers:socket_<slot>_<index>` (`slot` = `EquipmentSlotGroup.getSerializedName()`, e.g. `socket_head_0`). 26.3's `LivingEntity` removes a modifier by id before adding one (`LivingEntity.java:2948`), so an id shared across armour slots would let one piece overwrite another. Per-slot ids avoid that, and the per-index part lets two of the same gem stack. |
| D11 | Grade goes down after socketing | Gems past the new count **stay in the item but go dormant**: no bonus, shown greyed out, still removable. Nothing is lost by a regrade. |
| D12 | Same gem twice | Allowed. Each socket adds its own modifier. |
| D13 | Data | One new data component, `jaymospowers:gem_sockets` = `List<CutGem>`, reusing `CutGem.CODEC`/`STREAM_CODEC`. No attachments, so `JaymosPowers.registerAttachments()` is **not** touched. |
| D14 | World-tier loot (Kingdoms `WorldTiers`) and `GradedLoot` | Untouched. Loot and trades never come socketed. `WorldTiers` doubles loot with `stack.copy()`, which keeps components, and `GradedLoot.gradable` still skips stacks that already have a grade. |
| D15 | Package | `com.jaymospowers.jewellery.sockets` (the gem rules live in `jewellery/`). Lang goes in its own `lang/parts/sockets.json`. |

---

## 1. Player-facing rules (append to `docs/Smithing.md` as "## Gem sockets", after "## Jewellery")

1. **Sockets.** Forged weapons and armour get gem sockets by grade: Expertly and Perfectly Forged have one, a
   Masterwork has two. Poorly and Well Forged have none. Tinkering tools and jewellery take no gems.
2. **Socketing.** Hold the piece in your main hand and a cut gem in your off hand, then sneak and use a Smithing Anvil
   (iron or stone). The gem goes into the next free socket. A hot or broken piece can't take a gem.
3. **Bonus.** Each gem adds its own bonus: emerald Luck, diamond armour toughness, amethyst health, lapis swimming,
   quartz less burning, zanite mining speed, glint speed, corrobonite knockback resistance, golden amber breath. A
   better cut and a finer gem give more. A weapon gives the bonus while it is held in the main hand. Armour gives
   half as much, while it is worn.
4. **Taking a gem out.** Sneak-use the anvil with the piece in your main hand and **tongs** in your off hand to pull
   the last gem out whole (it wears the tongs a little). With an empty off hand you prise it out instead, and **the gem
   shatters**. You have to use the anvil twice to do it.
5. A broken piece's gems do nothing until it is repaired. If a piece's grade drops, gems past its new count go dormant
   until they are taken out.

---

## 2. Numbers (every one a `static final` at the top of `Sockets.java` / `SocketAnvil.java`)

### 2.1 `jewellery/sockets/Sockets.java`

| Constant | Value | Meaning |
|---|---|---|
| `GRADE_SOCKETS` | `EnumMap<Grade,Integer>`: POOR 0, WELL 0, EXPERT 1, PERFECT 1, MASTER 2 | Sockets per forging grade (static block, like `Jewellery.GRADE_FACTOR`) |
| `MAX_SOCKETS` | `2` | Most sockets any grade gives. Used for the stream-codec list cap (`MAX_SOCKETS * 2`, room for dormant gems) |
| `WEAPON_FACTOR` | `1.0F` | Gem bonus multiplier on a melee piece (main hand) |
| `ARMOUR_FACTOR` | `0.5F` | Gem bonus multiplier on armour (up to 8 gems can be worn) |
| `ID_PREFIX` | `"socket_"` | Modifier id prefix: `socket_<slot>_<index>` |

Bonus examples (gem base × `CutGem.scale()` × factor):

| Gem in socket | Weapon, Well-cut Table (1.0) | Weapon, Masterwork Brilliant (2.0 × 1.3 = 2.6) | Armour, Masterwork Brilliant |
|---|---|---|---|
| Amethyst (+1.0 health) | +1.0 | +2.6 | +1.3 |
| Diamond (+0.5 toughness) | +0.5 | +1.3 | +0.65 |
| Glint (+2% speed) | +2% | +5.2% | +2.6% |
| Corrobonite (+0.05 KB res.) | +0.05 | +0.13 | +0.065 |

The most a player can wear is four Masterwork pieces × two Masterwork Brilliant amethysts = +10.4 health (5 hearts),
and that takes eight master cuts and four masterworks.

### 2.2 `jewellery/sockets/SocketAnvil.java`

| Constant | Value | Meaning |
|---|---|---|
| `SOCKET_COOLDOWN` | `10` | Ticks before the piece can be used at the anvil again (the click repeats while held; same as `Finishing.GRIND_COOLDOWN`) |
| `PRISE_CONFIRM_TICKS` | `40` | A second empty-off-hand use within this many ticks shatters the gem out |
| `TONGS_WEAR` | `8` | Durability the tongs lose pulling a gem out whole |

---

## 3. Powers: new files

### 3.1 `src/main/java/com/jaymospowers/jewellery/sockets/Sockets.java`

`public final class Sockets` holds the data, the rules and the attribute pass.

```java
/** (0.20.7) The cut gems set in a forged weapon or armour piece, in socket order. Saved id: never rename. */
public static DataComponentType<List<CutGem>> SOCKETS;

public static void register() {          // from Jewellery.init(), after CUT_GEM is registered
    SOCKETS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("gem_sockets"),
        DataComponentType.<List<CutGem>>builder().persistent(CutGem.CODEC.listOf())
            .networkSynchronized(CutGem.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SOCKETS * 2))).build());
    Grades.ATTRIBUTE_PASSES.add(Sockets::withGems);
    SocketAnvil.register();
}
```

- `public static boolean socketable(ItemStack stack)`: true when the stack is not empty, `getMaxStackSize() == 1`,
  `stack.has(Smithing.GRADE)`, not `TinkerTool`, not `BaubleItem`, not `WeaponPartItem`/`ToolPartItem`, and
  `Grades.gearSlot(stack) != null`. This is D2.
- `public static int count(ItemStack stack)`: `socketable ? GRADE_SOCKETS.get(Grades.of(stack)) : 0`.
- `public static List<CutGem> gems(ItemStack stack)`: `stack.getOrDefault(SOCKETS, List.of())`.
- `public static int free(ItemStack stack)`: `Math.max(0, count - gems.size())`.
- `public static boolean add(ItemStack piece, CutGem gem)`: if `free > 0`, set `SOCKETS` to a new immutable list
  (`List.copyOf`) with the gem added, call `Grades.bake(piece)` and return true. Otherwise return false.
- `public static @Nullable CutGem removeLast(ItemStack piece)`: drops the last entry, removes the component when the
  list is empty, calls `Grades.bake(piece)`, and returns the removed gem (or null).
- `public static double factor(ItemStack stack)`: `Grades.gearSlot(stack) == EquipmentSlotGroup.MAINHAND ?
  WEAPON_FACTOR : ARMOUR_FACTOR`.
- `public static BaubleItem.@Nullable Bonus bonus(ItemStack stack, CutGem gem)`: `gem.gemType() == null ? null :
  type.bonus(gem.scale() * factor(stack))`. Shared by the attribute pass and the tooltip.
- `static ItemAttributeModifiers withGems(ItemStack stack, ItemAttributeModifiers attributes)`: the `ATTRIBUTE_PASSES`
  hook. If `!socketable(stack)`, return `attributes` unchanged. Otherwise, for `i < min(count, gems.size())`, add
  `bonus.attribute()` with `new AttributeModifier(JaymosPowers.id(ID_PREFIX + slot.getSerializedName() + "_" + i),
  bonus.amount(), bonus.operation())` via `attributes.withModifierAdded(..., slot)`, where `slot =
  Grades.gearSlot(stack)`. Gems at index `>= count` are dormant (D11) and add nothing. The vanilla "When in Main
  Hand / When on Head" tooltip block then shows the numbers by itself (default `Display`).

### 3.2 `src/main/java/com/jaymospowers/jewellery/sockets/SocketAnvil.java`

`final class SocketAnvil` is the anvil interaction. Its shape is a copy of `Finishing.grindstone` (Finishing.java:112-138).

- `static void register()`: `UseBlockCallback.EVENT.register(SocketAnvil::use)`, and
  `ServerLifecycleEvents.SERVER_STOPPED.register(s -> PRISING.clear())`.
- `private static final Map<UUID, Long> PRISING`: the player and the game time of the first empty-off-hand use.
- `private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit)`:
  1. Return `PASS` unless all of these hold: `hand == MAIN_HAND`, `player.isSecondaryUseActive()`, the player is not
     a spectator, `level.getBlockState(hit.getBlockPos()).getBlock() instanceof SmithingAnvilBlock`, and
     `Sockets.socketable(piece = player.getMainHandItem())`.
  2. `off = player.getOffhandItem()`. Work out the action: `SOCKET` if `off.is(Jewellery.CUT_GEM_ITEM)` and
     `off.get(Jewellery.CUT_GEM)` has a known `gemType()`. (It must be the cut gem item, not a jewellery piece, even
     though a piece carries `CUT_GEM` too.) `PULL` if `off.getItem() instanceof TongsItem` and the piece has gems.
     `PRISE` if `off.isEmpty()` and the piece has gems. Otherwise return `PASS`.
  3. If `player.getCooldowns().isOnCooldown(piece)`, or the level is the client, return `SUCCESS` (the client swings,
     and the server decides).
  4. Server, `SOCKET`:
     - `Wear.broken(piece)` gives `message...sockets.broken`. (`Wear`'s own `UseBlockCallback` already FAILs earlier
       for a broken main-hand item, since it registers first from `SmithingWider`. This check is a backstop.)
     - `Grades.refusal(piece)` (hot) gives that reason.
     - `Sockets.count == 0` gives `no_sockets`, and `free == 0` gives `full`.
     - Otherwise `Sockets.add(piece, cut)`, `off.shrink(1)`, the sound `SoundEvents.AMETHYST_BLOCK_PLACE` (1.0, 1.1)
       plus `SMITHING_TABLE_USE` (0.6, 1.2), `ParticleTypes.WAX_ON` ×6 over the anvil top (`pos + 0.5, +1.0`), and
       the message `socketed`.
  5. Server, `PULL`: `CutGem g = Sockets.removeLast(piece)`, give `CutGemItem.of(g)` through
     `player.getInventory().placeItemBackInInventory(gem, Prediction.SERVER_ONLY)`, call
     `off.hurtAndBreak(TONGS_WEAR, player, InteractionHand.OFF_HAND)`, play `SoundEvents.CHAIN_PLACE` (0.8, 1.3), and
     send `pulled`. If `CutGemItem.of(g)` is EMPTY (an unknown gem id from a removed mod), treat it as a prise.
  6. Server, `PRISE`: if `PRISING` holds the player with `gameTime - t <= PRISE_CONFIRM_TICKS`, remove the entry, call
     `Sockets.removeLast`, play `SoundEvents.AMETHYST_CLUSTER_BREAK` (1.0, 0.9), send
     `new ItemParticleOption(ParticleTypes.ITEM, Jewellery.CUT_GEM_ITEM)` ×10 (26.3 takes an `Item` or `ItemStackTemplate`, not an `ItemStack`), and send `shattered`. Otherwise put
     `player.getUUID() -> gameTime` in `PRISING` and send `prise_confirm`. Don't start the cooldown on the first
     click, or the confirm click would hit it.
  7. After any action that changed the piece: `player.getCooldowns().addCooldown(piece, SOCKET_COOLDOWN)`, and return
     `SUCCESS`. Every message goes through `player.sendOverlayMessage(Component.translatable(...))`.
- The stacks are changed in place on the server, so the inventory syncs on its own. No payload is needed.

### 3.3 `src/client/java/com/jaymospowers/client/jewellery/SocketTooltip.java`

`public final class SocketTooltip`, `static void init()`, registers `ItemTooltipCallback.EVENT` (the same shape as
`WiderSmithingClient`):

- If `!Sockets.socketable(stack)` or `lines.isEmpty()`, return.
- `count = Sockets.count(stack)` and `gems = Sockets.gems(stack)`. If `count == 0 && gems.isEmpty()`, return (a
  Well Forged sword shows nothing).
- Insert position: just after the grade line. Find `lines.indexOf(Grades.of(stack).displayName())`, then `+1`. If it
  isn't found, use `min(lines.size(), 1)`. It runs after `SmithingClient` and `WiderSmithingClient`, because
  `JewelleryClient.init` comes later (JaymosPowersClient.java:203).
- The lines, in order:
  - `tooltip.jaymospowers.sockets.count` (count filled, count), GRAY.
  - Each gem `i < count`: `tooltip.jaymospowers.sockets.gem` (cut name, gem name, cut-grade name
    `grade.jaymospowers.cut.<id>`), coloured `TextColor.fromRgb(cut.colour())`.
  - Each empty socket: `tooltip.jaymospowers.sockets.empty`, DARK_GRAY.
  - Each gem `i >= count`: `tooltip.jaymospowers.sockets.dormant` (cut name, gem name), DARK_GRAY, ITALIC.
  - If `free > 0`: `tooltip.jaymospowers.sockets.hint`, DARK_GRAY.
- The numbers are not repeated: the vanilla attribute block lists them.

### 3.4 `src/main/resources/assets/jaymospowers/lang/parts/sockets.json` (new)

```json
{
  "tooltip.jaymospowers.sockets.count": "Gem sockets: %s of %s set",
  "tooltip.jaymospowers.sockets.gem": "◆ %1$s %2$s (%3$s)",
  "tooltip.jaymospowers.sockets.empty": "◇ Empty socket",
  "tooltip.jaymospowers.sockets.dormant": "◆ %1$s %2$s (dormant: too few sockets for its grade)",
  "tooltip.jaymospowers.sockets.hint": "Sneak-use a Smithing Anvil with a cut gem in your off hand",
  "message.jaymospowers.sockets.socketed": "%1$s set in %2$s",
  "message.jaymospowers.sockets.no_sockets": "%s has no sockets: only Expertly Forged or better takes a gem",
  "message.jaymospowers.sockets.full": "Every socket in %s is set",
  "message.jaymospowers.sockets.broken": "%s is broken: repair it first",
  "message.jaymospowers.sockets.pulled": "Pulled the %s out whole",
  "message.jaymospowers.sockets.prise_confirm": "Use again to prise out the %s: it will shatter. Tongs in your off hand save it.",
  "message.jaymospowers.sockets.shattered": "The %s shattered"
}
```

Arguments: gem messages pass `CutGemItem.of(g).getHoverName()` (for example "Brilliant Diamond"), and piece messages
pass `piece.getHoverName()`.

---

## 4. Powers: changed files

### 4.1 `tinkering/smithing/Grades.java`

- Add, beside `CARRIERS`:
  ```java
  /** (0.20.7) More item modifiers baked over the grade's (gem sockets): (stack, modifiers so far) -> modifiers. */
  public static final List<BiFunction<ItemStack, ItemAttributeModifiers, ItemAttributeModifiers>> ATTRIBUTE_PASSES = new ArrayList<>();
  ```
- Add `public static @Nullable EquipmentSlotGroup gearSlot(ItemStack stack)`. It looks at `proto =
  stack.getPrototype()` and `base = proto ATTRIBUTE_MODIFIERS` and returns the slot of the base's `ARMOR` entry if
  there is one. Otherwise it returns `MAINHAND` if `proto.has(WEAPON)` or the base has `ATTACK_DAMAGE`, and
  otherwise null. This is the same test `bake` uses. Leave `bake`'s own melee/armour code **as it is**, so the grade
  numbers can't drift.
- In `bake(ItemStack)`, between the end of `if (grade != null) { ... }` and
  `stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes)`, add:
  ```java
  for (BiFunction<ItemStack, ItemAttributeModifiers, ItemAttributeModifiers> pass : ATTRIBUTE_PASSES) {
      attributes = pass.apply(stack, attributes);
  }
  ```
  The `TinkerTool` early return above stays first, so Tinkering tools never reach the pass (D3). `Wear.restrict`
  still runs last and empties everything on a broken piece (D8).

### 4.2 `jewellery/CutGemItem.java`

- Add `public static ItemStack of(CutGem cut)`. It returns `ItemStack.EMPTY` when `cut.gemType()` is null, and
  otherwise `of(cut.gemType(), cut.cutType(), cut.grade(), cut.chips())`. That rebuilds the model data, name and
  glint. Used when tongs pull a gem out.

### 4.3 `jewellery/Jewellery.java`

- At the end of `init()`: `com.jaymospowers.jewellery.sockets.Sockets.register(); // (0.20.7) gem sockets in forged gear`.
  It must come after `CUT_GEM` is registered. Every `Grades.bake` call happens in play, after init. Before
  `register` runs, `ATTRIBUTE_PASSES` is simply empty.
- In the creative-tab output (the existing `modifyOutputEvent(Kiln.tab())` lambda), add one test piece:
  `new ItemStack(Items.IRON_SWORD)`, then `Grades.set(sword, Grade.MASTER)`, then `output.accept(sword)` (a
  Masterwork with two empty sockets).

### 4.4 `src/client/java/com/jaymospowers/client/jewellery/JewelleryClient.java`

- At the end of `init()`: `SocketTooltip.init(); // (0.20.7)`.

### 4.5 `lang/parts/jewellery.json`

- Change `tooltip.jaymospowers.jewellery.gem_hint` to `"Set it in a frame at a Jeweller's Bench, or in a forged
  weapon or armour's socket at a Smithing Anvil"`.

### 4.6 Docs and version

- `gradle.properties`: `version=0.20.7`.
- `docs/Smithing.md`: add "## Gem sockets (code: `jewellery/sockets/`, docs/Sockets-Plan.md)" with the five rules
  from section 1. Under "## Known limits" add: "Melting or salvaging a socketed piece loses its gems; a vanilla anvil
  repair keeps only the left item's gems."
- `docs/Otectus-Plan.md:13`: mark row 5 as built in 0.20.7.
- `MODLOG.md`: add the entry in section 7.

### 4.7 Not touched (on purpose)

`JaymosPowers.registerAttachments()` (no attachments), `Baubles`, `ToolStats`, `Wear`, `GradedLoot`, `Forging`,
`SmithingAnvilBlock`/`Entity`, `SmithingNet`, everything in `kingdoms/` (including `WorldTiers`), `Serverfiles/`.

---

## 5. Hooks into existing methods (summary)

| New piece | Hooks | Existing method |
|---|---|---|
| `Sockets.withGems` | `Grades.ATTRIBUTE_PASSES` (new list) | `Grades.bake`, after the grade modifiers, before `Wear.restrict` |
| `Sockets.socketable` / `factor` | `Grades.gearSlot` (new helper) | the same melee/armour rule as `Grades.bake` |
| Broken gear | nothing new | `Wear.restrict` (empties modifiers), `Wear.mend`, then `Grades.bake` (gems come back) |
| `SocketAnvil.use` | `UseBlockCallback.EVENT` | runs after `Wear.refuse` (registered earlier through `SmithingWider`) and beside `Finishing.grindstone` |
| Gem bonus | `Gems.Gem.bonus(scale)`, `CutGem.scale()` | patch 4 rules, reused unchanged |
| Returned gem | `CutGemItem.of(CutGem)` (new overload) | `CutGemItem.of(Gem, Cut, Grade, int)` |
| Tooltip | `ItemTooltipCallback.EVENT` | after `SmithingClient.tooltip`'s grade line |
| Regrade | nothing new | `Grades.set`, then `bake`; `count` follows the new grade, extra gems go dormant |

There are no blessings, patrons or Kingdoms hooks in this patch.

---

## 6. Must not break (checklist for the editor and the verifier)

1. Ungraded and Poorly/Well Forged items bake the same `ATTRIBUTE_MODIFIERS` as before: the pass returns
   `attributes` unchanged when there are no gems.
2. The `grade_damage`, `grade_speed` and `grade_armor` modifiers are unchanged in id, value and slot.
3. Tinkering tools: `ToolStats.apply` path unchanged, and they are never socketable.
4. A broken piece has empty modifiers (gems included), and a repair brings both back.
5. The id `jaymospowers:gem_sockets` is registered once. `CutGem` ids and fields are unchanged.
6. A jewellery piece in the off hand is not taken as a gem, and jewellery pieces are never socketable.
7. Plain (non-sneak) use of the anvil works as before (hammer, put, tongs take, empty-hand take). A sneak-use whose
   main-hand item isn't socketable returns `PASS`.
8. Kingdoms `WorldTiers` loot and Powers `GradedLoot` are unchanged. Copied stacks keep their sockets.
9. `./gradlew build` passes in `/home/user/sockets`, and the lang merge picks up `parts/sockets.json`.
10. Don't commit `Serverfiles/`.

Suggested track: **one editor track** (small patch, about 4 new or changed Java files). Files: `jewellery/sockets/*`,
`Grades.java`, `CutGemItem.java`, `Jewellery.java`, `client/jewellery/SocketTooltip.java`, `JewelleryClient.java`,
the lang parts, the docs and `gradle.properties`.

---

## 7. MODLOG text (append after the 0.20.0 entry)

```
- **0.20.7, gem sockets (Patch 5)** (`jewellery/sockets/`, plan `docs/Sockets-Plan.md`): Andy picked the idea of
  Immersive Apotheosis; this is our own version (none of its code or art).
  - Forged weapons and armour get gem sockets by grade: Expertly and Perfectly Forged one, Masterwork two, Poorly and
    Well Forged none. Tinkering tools and jewellery take no gems.
  - Socketing: piece in the main hand, a cut gem in the off hand, sneak-use a Smithing Anvil (iron or stone). With
    tongs in the off hand the last gem comes out whole (tongs wear 8); with an empty off hand it is prised out and
    shatters (use twice to confirm).
  - Each gem adds its own bonus (the jewellery gem's attribute) times its grade and cut: full on a weapon in the main
    hand, half on worn armour. The bonuses are item modifiers baked by `Grades.bake` through the new
    `Grades.ATTRIBUTE_PASSES` (ids `socket_<slot>_<n>`), so a broken piece loses them until repaired. If a piece's grade
    drops, gems past its count go dormant.
  - Tooltip: sockets filled, each gem and its cut grade, empty sockets, dormant gems. One data component
    (`jaymospowers:gem_sockets`), no attachments, no new items or packets. Kingdoms unchanged.
  - Not compile-checked yet at this entry; not played.
```

(The editor drops the last line once `./gradlew build` passes.)

---

## 8. Open questions for Andy (the defaults above get built unless he says otherwise)

1. **Socket counts:** Poorly 0 / Well 0 / Expertly 1 / Perfectly 1 / Masterwork 2. Should Well Forged get one too, or
   Perfectly two?
2. **Armour factor 0.5:** a full Masterwork set with eight top amethysts gives +5 hearts. Is that too much? (0.4 would
   give +4.)
3. **Removal:** tongs keep the gem and an empty hand shatters it. Should the tongs also have a chance to chip the
   gem (one grade lower)?
4. **Loot:** should found or traded Expertly-or-better gear sometimes come with a gem already set? (Not built, which
   keeps world-tier loot as it is.)
5. **Pre-existing, outside this patch:** `Grades.ARMOR_ID` (`grade_armor`) is one id for every armour slot. Because 26.3
   removes a modifier by id before adding one, wearing two graded armour pieces gives only one piece's grade armour,
   and taking either piece off removes it. The sockets avoid this with per-slot ids. Fix grade armour the same way
   (`grade_armor_<slot>`) in a follow-up?
