# Patch 7: Brewing quality (mortar, timed stirring, graded potions, Brewer's Alembic)

Powers **0.20.13**. Kingdoms and Kitchen untouched (no Kingdoms version, no loading hint). Row 7 of
`docs/Otectus-Plan.md`. Andy picked the idea of Immersive Brewing; this is our own version, with none of its code, art or data.

Andy-approved plan text: "A mortar and pestle grinding game before an ingredient goes in the cauldron, stirring timed
instead of six clicks, potions graded Crude to Masterwork (longer effects, faster drinking), and an alembic that
distils a brew one grade up."

Comment tag for all new code: `(0.20.13)`. Tuning numbers are `static final` constants at the top of each class.

## 0. Decisions (made here, so the edit stage has nothing open)

| # | Question | Decision |
|---|---|---|
| D1 | Name collision | `jaymospowers:alembic` (`Crucibles.ALEMBIC`, the essence still) stays as it is. The new block is **Brewer's Alembic**, id `jaymospowers:brewers_alembic`, and its research id is `distilling`. Nothing reuses `alembic`. |
| D2 | How grade lengthens effects | The vanilla component **`DataComponents.POTION_DURATION_SCALE`** (checked in the 26.3 sources). `PotionContents.onConsume` reads it for drinking. `ThrownSplashPotion.onHitAsPotion` reads it (line 43), and `AreaEffectCloud.applyComponentsFromItemStack` takes it (lines 376-385) for lingering clouds. The tooltip's durations already use it (`PotionContents.addToTooltip`). So `POTION_CONTENTS` is **never rebuilt**: `is(Potions.WATER)`, `is(PotionTags.DOUSES_FIRE)`, the brewing stand's `PotionIngredient` match, `EnchantRecipes` and the fletching copies all keep seeing the same potion. |
| D3 | How grade speeds drinking | The stack gets its own `DataComponents.CONSUMABLE`: the prototype's `Consumable` with `consumeSeconds` times the grade's drink factor. `Item.getUseDuration` reads `CONSUMABLE` (Item.java:324), so no mixin is needed. Splash and lingering potions have no `CONSUMABLE`, so for them only D2 applies. |
| D4 | Scale is always from the prototype | `BrewGrades.set` computes `stack.getPrototype().getOrDefault(POTION_DURATION_SCALE, 1F) * grade.duration` (lingering's prototype is 0.25F) and the prototype's `CONSUMABLE`. Setting a grade twice (the alembic) never compounds. `PatchedDataComponentMap.set` drops the patch when the value equals the prototype's (line 70), so Plain stays tidy. |
| D5 | Ground forms | **No new items.** Grinding puts the component `jaymospowers:ground` (Integer 1-3: Coarse, Fine, Dust) on the vanilla ingredient. Vanilla `PotionIngredient`/`Ingredient` match by item, so ground ingredients still work in a brewing stand, in Kitchen recipes and in villager trades. Three fixed levels keep stacks stackable. |
| D6 | What the mortar takes | Item tag `jaymospowers:mortar_grindable` (data; packs can add to it). Powders and liquids (redstone, glowstone, gunpowder, blaze powder, sugar, dragon's breath) are not in it and count as "nothing to grind": their step is scored on the stir alone. |
| D7 | Ungraded potions | Stand-made, looted and traded potions carry no grade and behave exactly as vanilla, like a Plain brew. In the Brewer's Alembic they count as Plain, so a stand potion can be distilled to Fine and no further. |
| D8 | Brewing stands | Untouched (no mixin). A graded potion put back into a stand comes out ungraded, because `BrewingRecipe.assemble` is `output.create()`. That is fine: the stand is the "Plain" way. |
| D9 | Distilling limits | Once per brew (`jaymospowers:brew_distilled`), one grade up, Masterwork refused. 2 or 3 bottles of the same brew give 1 or 2 bottles back, plus one empty glass bottle. |
| D10 | Research | Mortar and timed stirring are not gated (the cauldron is the hedge-witch's way, with no Codex). Working the Brewer's Alembic needs the research `distilling` (Crucible tab, after `alembic`). Crafting is not gated, the same as the crucible. |
| D11 | Blessings / skills | **None hook brewing in this patch.** `Forging.ZONE_BONUS` (Provenance's Blacksmith rank, Kingdoms' Anvil-Father True Strike through `PowersCompat.zoneBonus` → `Blessings.zoneBonus`) deliberately does **not** widen the mortar or stir bars. A new empty hook, `BrewQuality.ZONE_BONUS`, is there for a later blessing (open question 3). |
| D12 | Instant effects | Healing and Harming have no duration, so a grade only drinks them faster. Higher potency is out of scope (open question 2). |

## 1. Player-facing rules

1. **Mortar and Pestle** (stone block). Use it with a grindable brewing ingredient to put up to 4 of that ingredient
   in. Use it with an empty hand to start grinding: a timing bar appears under the crosshair (the Smithing Anvil's bar),
   5 strokes, one per use. When it finishes, the batch is **Coarsely Ground**, **Finely Ground** or **Ground to Dust**,
   by your average. Use again to take it out. Sneak-use with an empty hand before grinding takes the items back.
   A Coarse or Fine batch can be ground again and keeps the better result. Dust is refused with "already ground".
2. **Timed stirring.** The cauldron works as before: a full water cauldron over heat, ingredients thrown in float and
   swirl. The first stick or shovel use on an unstirred brew brings up the stir bar. Each use after that is one turn
   (6 turns), judged Perfect, Good or Miss. The floating ingredient swirls one place round per turn, as it does now.
   A turn needs heat ("It needs a fire under it to stir in").
3. **Grade.** Each ingredient is a *step*. Its score mixes how well it was ground (or that it didn't need grinding)
   with how well it was stirred. The brew's grade comes from the mean of its steps and is shown on bottling:

   | Grade | Effect duration | Drink time | Tooltip colour |
   |---|---|---|---|
   | Crude Brew | x0.75 | x1.15 (1.84 s) | grey |
   | Plain Brew (the same as vanilla) | x1.0 | x1.0 (1.6 s) | white |
   | Fine Brew | x1.25 | x0.9 (1.44 s) | green |
   | Expert Brew | x1.5 | x0.8 (1.28 s) | aqua |
   | Masterwork Brew | x2.0 | x0.6 (0.96 s) | gold, with the enchantment glint |

   Splash and lingering brews (gunpowder or dragon's breath stirred in) keep the duration factor on top of their own
   (lingering's 0.25). Tipped arrows made from them do not carry it: `Fletching` and vanilla's tipped-arrow recipe copy
   `POTION_CONTENTS` only.
4. **Brewer's Alembic** (copper and glass, over heat: anything `CauldronBrewing.heated` accepts under it). Use a bottled
   brew on it to put bottles in, up to 3 of the same brew (the same item and components). With 2 or more it distils
   for 60 s over heat (the timer pauses when cold and resets when its bottles change). Then each of 2 or 3 bottles gives
   1 or 2 bottles one grade up, marked "Distilled", and drops one empty glass bottle. Use it with an empty hand to see
   progress. Sneak-use it with an empty hand to take the bottles out. Needs the Codex research *Distilling Brews*.

## 2. Files

### New: `src/main/java/com/jaymospowers/alchemy/brewing/`

| File | Responsibility |
|---|---|
| `Brewing.java` | Registration and init for the package: the three data components, the two blocks (with items in the Alchemy tab through `Alchemy.item`), two block entity types, research `distilling`, `BrewingNet.register()`. Holds the public static fields below. A private `block(id, factory, props)` helper is copied from `Crucibles.block` (Crucibles.java:249-255, the non-jar branch). |
| `BrewGrade.java` | `enum BrewGrade { CRUDE, PLAIN, FINE, EXPERT, MASTERWORK }`: `id`, `ChatFormatting colour`, `float duration`, `float drink`; `displayName()`, `higher()`, `atLeast()`, `byId(String)`. Shaped like `tinkering/smithing/Grade`, which is **not** edited or reused. |
| `BrewGrades.java` | Grades on potion stacks: `@Nullable BrewGrade of(ItemStack)`, `BrewGrade orPlain(ItemStack)`, `void set(ItemStack, BrewGrade)` (D2-D4 plus the Masterwork glint), `boolean distilled(ItemStack)`, `void markDistilled(ItemStack)`, `boolean isBrew(ItemStack)` (POTION, SPLASH_POTION or LINGERING_POTION with `POTION_CONTENTS.hasEffects()`), `int ground(ItemStack)` (0 if none). |
| `BrewQuality.java` | Pure functions shared by client and server, with no world access (like `Forging`'s "the bar, the same on both sides"): the `Bar` record, `GRIND`/`STIR` bars, `perfectHalf`, `goodHalf`, `speed`, `centre`, `judge`, `score`, `groundLevel`, `grindScore`, `stepScore`, `grade`, `ZONE_BONUS` + `bonus(ServerPlayer)`. |
| `BrewingNet.java` | Payloads `BrewBar` (clientbound) and `BrewStroke` (serverbound), `register()`, and the receiver with the reach check. |
| `MortarBlock.java` | `Block implements EntityBlock`. Shape, ticker, `useItemOn`/`useWithoutItem` → `interact(...)` (copy of `LapidaryWheelBlock.interact`, lines 87-110), and the `public static BiPredicate<Player, BlockPos> clientStroke` hook. |
| `MortarEntity.java` | The batch, the result, the grinding game (`record Grinding(int done, float score, int perfects, long seed)` with a CODEC), the grinder's UUID, `lastStroke`, `finishedBy/At`. Modelled on `jewellery/LapidaryWheelEntity` (same `use / stroke / finish / sendSession / endSession / grinderNear / serverTick / changed / preRemoveSideEffects / load / save / getUpdatePacket / getUpdateTag` structure, without the choose-a-cut screen or chips). |
| `BrewersAlembicBlock.java` | `Block implements EntityBlock`, `IntegerProperty BOTTLES = IntegerProperty.create("bottles", 0, 3)`, shape, server ticker, use → entity, `animateTick` steam wisp. |
| `BrewersAlembicEntity.java` | Up to 3 bottles (`List<ItemStack>`), `progress`, `use`, `serverTick`, `distil`, drop on removal, save/load. |

### New: `src/main/java/com/jaymospowers/alchemy/cauldron/CauldronStirring.java`
The server half of the stir game (sessions, strokes, the finish). It is kept apart from `CauldronBrewing` so that file
stays readable. It is package-private where it touches `CauldronBrewing`'s helpers.

### New: client
| File | Responsibility |
|---|---|
| `src/client/java/com/jaymospowers/client/alchemy/BrewingClient.java` | Both bars (one session at a time, `BrewBar.game` tells which), arrow tick, local stroke judging, HUD element, tooltips for grade/distilled/ground, `MortarRenderer` registration. Sets `MortarBlock.clientStroke` and `CauldronStirring.clientStir`. |
| `src/client/java/com/jaymospowers/client/alchemy/MortarRenderer.java` | Draws the batch or result lying in the bowl. A copy of `client/jewellery/LapidaryWheelRenderer` (61 lines) with its own offset and scale. |

### Changed
| File | Change |
|---|---|
| `gradle.properties:13` | `version=0.20.13` |
| `alchemy/Alchemy.java` (init, ~line 62) | `com.jaymospowers.alchemy.brewing.Brewing.init();` **before** `CauldronBrewing.init();`, with a comment `// (0.20.13) brewing quality: mortar, graded brews, the Brewer's Alembic`. Class Javadoc list: add `<li>{@code brewing/}: grinding, brew grades and the Brewer's Alembic (0.20.13).</li>`. |
| `alchemy/cauldron/CauldronBrewing.java` | See section 4. |
| `client/alchemy/AlchemyClient.java` (init) | `BrewingClient.init(); // (0.20.13)` |
| `client/jei/AlchemyJei.java` (`registerRecipes`, line ~60) | `registration.addIngredientInfo(Brewing.MORTAR, Component.translatable("jei.jaymospowers.mortar"));` and the same for `Brewing.BREWERS_ALEMBIC` / `jei.jaymospowers.brewers_alembic`. Use the `addIngredientInfo(ItemLike, Component...)` overload that `TinkeringJei.java:128` uses. |
| `lang/parts/alchemy_cauldron.json` | Three texts change (section 7). |
| `data/minecraft/tags/block/mineable/pickaxe.json` | Add `"jaymospowers:mortar"`, `"jaymospowers:brewers_alembic"` (next to `jaymospowers:alembic`, line ~125). |
| `docs/HUD-Layout.md` (BELOW row, line 29) | `50 smithing` → `50 smithing · gem cutting · mortar / stir bar (only one shows at a time)`. |
| `docs/Alchemy-Plan.md` | New section `### 6. Brewing quality (0.20.13)` with one paragraph and a link to this file (extends the crucible/alembic section, replaces nothing). |
| `docs/Otectus-Plan.md:15` | Status: `built in Powers 0.20.13 (docs/Brewing-Quality-Plan.md)`. |
| `MODLOG.md` | The entry in section 13, appended after the 0.20.8 entry (line ~5279). |

**Not changed:** `JaymosPowers.registerAttachments()` (no new attachment: the three new types are *data components*,
the stir sessions are a transient map, and the brew stays in `CauldronBrewing.BREWS`), `CauldronBrewItemMixin`,
`SplashWaterDouseMixin`, `jaymospowers.mixins.json`, `Fletching`/`LingeringArrow`, `EnchantRecipes`, `Grade`/`Grades`/
`Forging`/`GradedLoot`, `Crucibles`/`AlembicBlock`/`CrucibleEntity`, `HealingBrews`, all of `kingdoms/` and `kitchen/`.

### New resources
```
src/main/resources/assets/jaymospowers/lang/parts/alchemy_brewing.json
src/main/resources/assets/jaymospowers/blockstates/mortar.json              ({"variants": {"": {"model": "jaymospowers:block/alchemy/mortar"}}})
src/main/resources/assets/jaymospowers/blockstates/brewers_alembic.json     (multipart, below)
src/main/resources/assets/jaymospowers/models/block/alchemy/mortar.json
src/main/resources/assets/jaymospowers/models/block/alchemy/brewers_alembic.json
src/main/resources/assets/jaymospowers/models/block/alchemy/brewers_alembic_bottle_1.json  (_2, _3)
src/main/resources/assets/jaymospowers/items/mortar.json                    (like items/alembic.json)
src/main/resources/assets/jaymospowers/items/brewers_alembic.json
src/main/resources/assets/jaymospowers/textures/block/alchemy/mortar_stone.png, mortar_inner.png, pestle.png, brewers_alembic_rack.png
src/main/resources/data/jaymospowers/recipe/mortar.json, brewers_alembic.json
src/main/resources/data/jaymospowers/loot_table/blocks/mortar.json, brewers_alembic.json   (like loot_table/blocks/alembic.json)
src/main/resources/data/jaymospowers/tags/item/mortar_grindable.json
tools/gen_alchemy_brewing.py
```

## 3. Ids, components, registrations (`Brewing.java`)

```java
public static DataComponentType<String> BREW_GRADE;      // "jaymospowers:brew_grade", persistent Codec.STRING, sync ByteBufCodecs.STRING_UTF8
public static DataComponentType<Unit> BREW_DISTILLED;    // "jaymospowers:brew_distilled", persistent Unit.CODEC, sync Unit.STREAM_CODEC (as DataComponents.UNBREAKABLE)
public static DataComponentType<Integer> GROUND;         // "jaymospowers:ground", persistent Codec.intRange(1, 3), sync ByteBufCodecs.VAR_INT
public static Block MORTAR, BREWERS_ALEMBIC;
public static BlockEntityType<MortarEntity> MORTAR_ENTITY;            // "jaymospowers:mortar"
public static BlockEntityType<BrewersAlembicEntity> BREWERS_ALEMBIC_ENTITY; // "jaymospowers:brewers_alembic"
public static final String DISTILLING = "distilling";    // research id
public static final TagKey<Item> GRINDABLE = TagKey.create(Registries.ITEM, JaymosPowers.id("mortar_grindable"));
```
Components are registered the way `Smithing.register()` does it (Smithing.java:70-77):
`Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id(...), DataComponentType.<T>builder().persistent(...).networkSynchronized(...).build())`.

Blocks:
- `MORTAR = block("mortar", MortarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE).noOcclusion())`
- `BREWERS_ALEMBIC = block("brewers_alembic", BrewersAlembicBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F).sound(SoundType.COPPER).noOcclusion())`
- Block entity types: `Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("mortar"), new BlockEntityType<>(MortarEntity::new, Set.of(MORTAR)))`, and the same for the alembic.

Research (copy of `Crucibles.registerResearch`, Crucibles.java:232-245):
```java
Research.register(new Research.Entry(DISTILLING, Research.Category.CRUCIBLE, () -> BREWERS_ALEMBIC.asItem(),
    EssenceList.of(Essence.WATER, 10, Essence.FIRE, 8).plus(Essence.MERCURY, 6), List.of("alembic"), 0, 3, 3));
```
Crucible-tab cells already in use: (0,1) crucible, (1,0) orichalc, (1,1) principles, (1,2) alembic, (2,0) transmutation,
(2,1) everflame, (2,2) pod still (`PodFuel`), (1,3) bonesetting, (2,3) vigour. (3,3) is free. Check in the Codex that
the line from (1,2) doesn't run through another node; if it does, use (3,2).

`init()` order: components → blocks/items → BE types → research → `BrewingNet.register()`.

## 4. `CauldronBrewing.java` changes (line numbers from 0.20.8)

1. **`Brew` record (lines 85-91)** becomes:
   ```java
   public record Brew(ItemStack potion, int stirs, int fills, float stirScore, int perfects, long seed, float grind, float quality, int steps)
   ```
   - `stirScore`/`perfects`: the ingredient being stirred now. `seed`: its bar seed. `grind`: its grind score
     (`BrewQuality.grindScore`, `-1` = nothing to grind). `quality`/`steps`: the sum of finished step scores, and how many.
   - CODEC: the first three fields as now; the new ones are `optionalFieldOf` with defaults `stir_score` 0F, `perfects` 0,
     `seed` 0L, `grind` -1F, `quality` 0F, `steps` 0, so 0.20.8 chunk saves load. An old stirred brew (steps 0)
     bottles as Plain, the same as before.
   - Methods: `Brew withFills(int fills)`; `Brew stroke(int result)` (stirs-1, stirScore + `BrewQuality.score(result)`,
     perfects + (PERFECT?1:0)); `Brew stirred()` (quality + `BrewQuality.stepScore(stirScore / STIR_TURNS, grind)`,
     steps + 1, stirScore 0, perfects 0, grind -1); `Brew withSeed(long)`; `BrewGrade grade()` → `BrewQuality.grade(quality, steps)`.
2. **Class Javadoc (lines 54-69):** the stirring bullet becomes "...until it's stirred in: a timed stir
   ({@link CauldronStirring}, (0.20.13)) of {@link #STIR_TURNS} turns...". Add a bullet: "(0.20.13) Each ingredient
   stirred in is a step graded on its grind ({@code Brewing.GROUND}) and its stir; bottles carry the brew's
   {@link com.jaymospowers.alchemy.brewing.BrewGrade}." `STIR_TURNS` stays 6, and its Javadoc becomes "Stirring turns
   (judged on the stir bar) that work an ingredient in."
3. **UseBlockCallback client branch (lines 113-116):**
   ```java
   if (level.isClientSide()) {
       if (stack.is(Items.STICK) || stack.is(ItemTags.SHOVELS)) {
           CauldronStirring.clientStir.test(player, pos); // (0.20.13) judges and sends a turn when the stir bar is up here
           return InteractionResult.SUCCESS;
       }
       return InteractionResult.PASS;
   }
   ```
4. **Stir branch (lines 118-120):** `return CauldronStirring.use((ServerPlayer) player, (ServerLevel) level, pos, brew);`.
   **Delete `stir()` (lines 207-233).**
5. **`itemInside` (lines 163-205):** before `set(...)` at line 201, compute `float grind = BrewQuality.grindScore(reagent);`
   and write
   `new Brew(result, STIR_TURNS, state.getValue(LayeredCauldronBlock.LEVEL), 0F, 0, level.getRandom().nextLong() | 1L, grind, brew == null ? 0F : brew.quality(), brew == null ? 0 : brew.steps())`.
   `reagent` is `item.getItem().copyWithCount(1)` and keeps the `ground` component. Nothing else changes in it: the
   recipe lookup, spits and floating setup stay as they are.
6. **`bottle()` (lines 235-247):** `ItemStack out = brew.potion().copy(); BrewGrades.set(out, brew.grade());` then
   `ItemUtils.createFilledResult(stack, player, out)`. `set(level, pos, fills > 0 ? brew.withFills(fills) : null)`.
   Change the `unstirred` overlay's arguments to (name, fills), as now.
7. **Empty-hand "holds" overlay (lines 131-136):** stirred brews send `holds` with (name, `brew.grade().displayName()`, fills).
8. **Visibility:** `set(...)` (line 348), `swirl(...)` (250), `floating(...)` (257) and `puff(...)` (282) become
   package-private (drop `private`) for `CauldronStirring`. `get`/`heated` are already public.
9. `bubble()` is unchanged (more froth while `stirs() > 0`).

## 5. `CauldronStirring.java` (server stir game)

```java
public final class CauldronStirring {
    /** Turns closer together than this are dropped (held use repeats every 4 ticks). */
    public static final int MIN_STROKE_GAP = 6;
    /** A stirrer who hasn't turned in this long gets the bar sent again on their next use. */
    public static final int RESEND_TICKS = 40;
    /** Further than this from the pot and someone else may take over (blocks, squared). */
    public static final double LEAVE_SQ = 10 * 10;

    /** Set by the client: a stick/shovel use on a cauldron whose stir bar is up judges the turn and sends it. */
    public static BiPredicate<Player, BlockPos> clientStir = (player, pos) -> false;

    private record Stirrer(ResourceKey<Level> dimension, BlockPos pos, long lastStroke) {}
    private static final Map<UUID, Stirrer> STIRRERS = new HashMap<>();
```
- `init()` (called from `CauldronBrewing.init()`): `ServerPlayConnectionEvents.DISCONNECT` removes the player and
  `ServerLifecycleEvents.SERVER_STOPPED` clears the map (as in `BeamClashTiming.register`, lines 81-84).
- `static InteractionResult use(ServerPlayer p, ServerLevel level, BlockPos pos, @Nullable Brew brew)`:
  `brew == null || stirs <= 0` → PASS (as now). Not `heated` → overlay `cold`, FAIL. If this player's `Stirrer` is
  this pos and `now - lastStroke < RESEND_TICKS` → SUCCESS and nothing else (the turns come as packets, the same
  rule as `LapidaryWheelEntity.cutting`). If another player's `Stirrer` is this pos, recent and within `LEAVE_SQ` →
  overlay `cauldron_brew.busy`, FAIL. Otherwise: if `brew.seed() == 0`, give it a fresh seed (`set`); put
  `Stirrer(dim, pos, now - MIN_STROKE_GAP)`; send the bar; when `brew.stirs() == STIR_TURNS`, also send overlay
  `cauldron_brew.stir_hint`. Return SUCCESS.
- `public static void stroke(ServerPlayer p, BlockPos pos, float arrow)` (from `BrewingNet`). Reject if: there is no
  `Stirrer` for this pos and dimension, `!Float.isFinite(arrow) || arrow < 0 || arrow > 100`,
  `now - lastStroke < MIN_STROKE_GAP`, or the brew is null or stirred. Not heated → overlay `cold` and resend the bar
  (`last` = -1). Otherwise `done = STIR_TURNS - brew.stirs()`,
  `result = BrewQuality.judge(BrewQuality.STIR, arrow, brew.seed(), done, BrewQuality.bonus(p))`,
  `brew = brew.stroke(result)`, update `lastStroke`, `p.swing(MAIN_HAND, SwingAnimation.DEFAULT, true)`, effects,
  and `swirl` each `floating` item to `brew.stirs()`.
  - Effects: PERFECT: `puff(level, pos, potion, 6)` + `BUBBLE_COLUMN_WHIRLPOOL_INSIDE` 0.5F/1.6F. GOOD: the current
    SPLASH x4 + whirlpool 0.4F/1.4F (lines 218-219). MISS: SPLASH x10 spread 0.3 + `GENERIC_SPLASH` 0.5F/0.8F.
  - At `stirs == 0`: discard the floaters, `brew = brew.stirred()`, `set`, `BREWING_STAND_BREW` 0.8F/1.0F,
    `puff(..., 16)`, overlay `stirred` with (name, `brew.grade().displayName()`), send `BrewBar.off(pos)`, remove the
    `Stirrer`. Otherwise `set` and send the bar with `last = result`.
- `private static void send(ServerPlayer p, BlockPos pos, Brew brew, int last)`:
  `new BrewingNet.BrewBar(pos, true, BrewQuality.STIR_GAME, STIR_TURNS, STIR_TURNS - brew.stirs(), BrewQuality.bonus(p), brew.seed(), last)`.

## 6. `alchemy/brewing` classes in detail

### `BrewGrade`
```java
CRUDE("crude", ChatFormatting.GRAY, 0.75F, 1.15F),
PLAIN("plain", ChatFormatting.WHITE, 1.0F, 1.0F),
FINE("fine", ChatFormatting.GREEN, 1.25F, 0.9F),
EXPERT("expert", ChatFormatting.AQUA, 1.5F, 0.8F),
MASTERWORK("masterwork", ChatFormatting.GOLD, 2.0F, 0.6F);
/** Effect durations times this; drinking time times this. */
public final float duration, drink;
public MutableComponent displayName() { return Component.translatable("brew_grade.jaymospowers." + id).withStyle(colour); }
```

### `BrewGrades.set(ItemStack stack, BrewGrade grade)`
```java
stack.set(Brewing.BREW_GRADE, grade.id);
float base = stack.getPrototype().getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
stack.set(DataComponents.POTION_DURATION_SCALE, base * grade.duration);
Consumable drink = stack.getPrototype().get(DataComponents.CONSUMABLE);
if (drink != null) {
    stack.set(DataComponents.CONSUMABLE, new Consumable(drink.consumeSeconds() * grade.drink, drink.animation(), drink.sound(),
        drink.hasConsumeParticles(), drink.onConsumeEffects()));
}
stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, grade == BrewGrade.MASTERWORK ? Boolean.TRUE : null);
```
(Every API here was checked in the 26.3 sources: `Consumable` is a record with these five components, and
`ItemStack.set(type, null)` removes the patch.)

### `BrewQuality` (constants and pure functions)
```java
public static final int MISS = 0, GOOD = 1, PERFECT = 2;
public static final int GRIND_GAME = 0, STIR_GAME = 1;
/** One timing bar's numbers (0 to 100 across). */
public record Bar(int strokes, float perfectHalf, float shrink, float minPerfectHalf, float goodScale,
                  float startSpeed, float speedUp, float maxSpeed, float centreMargin) {}
/** The pestle: 5 strokes, a kind zone that narrows a little, a slow arrow. */
public static final Bar GRIND = new Bar(5, 7.0F, 0.92F, 3.0F, 2.5F, 2.0F, 0.35F, 4.5F, 20.0F);
/** The stir: CauldronBrewing.STIR_TURNS turns, a wider zone that narrows faster, a quicker arrow. */
public static final Bar STIR = new Bar(CauldronBrewing.STIR_TURNS, 8.0F, 0.9F, 3.0F, 2.5F, 2.2F, 0.3F, 5.0F, 20.0F);
/** Score of a Good stroke (Perfect 1, Miss 0). */
public static final float GOOD_SCORE = 0.6F;
/** Mean stroke score a batch needs to come out Ground to Dust (3), Finely (2); anything less is Coarse (1). */
public static final float DUST_AT = 0.85F, FINE_AT = 0.55F;
/** What a step's grind counts: ground Coarse, Fine, Dust, and a grindable thing thrown in whole. */
public static final float COARSE_SCORE = 0.6F, FINE_SCORE = 0.85F, DUST_SCORE = 1.0F, UNGROUND_SCORE = 0.3F;
/** How much of a step's score is its grind (the rest is the stir). */
public static final float GRIND_WEIGHT = 0.4F;
/** Mean step score each grade needs. */
public static final float MASTERWORK_AT = 0.95F, EXPERT_AT = 0.8F, FINE_AT_GRADE = 0.6F, PLAIN_AT = 0.35F;
/** Widens the perfect zone (bar units) for this brewer; empty in 0.20.13 (a later blessing can add to it). */
public static final List<ToDoubleFunction<ServerPlayer>> ZONE_BONUS = new ArrayList<>();

public static Bar bar(int game)                    // GRIND_GAME → GRIND, else STIR
public static float perfectHalf(Bar b, int done, float bonus) // max(b.minPerfectHalf, b.perfectHalf * shrink^done) + bonus
public static float goodHalf(Bar b, int done, float bonus)    // perfectHalf * b.goodScale
public static float speed(Bar b, int done)                    // min(b.maxSpeed, b.startSpeed + b.speedUp * done)
public static float centre(Bar b, long seed, int done)        // b.centreMargin + new Random(seed * 31 + done).nextFloat() * (100 - 2 * b.centreMargin)
public static int judge(Bar b, float arrow, long seed, int done, float bonus)
public static float score(int result)                          // PERFECT 1, GOOD GOOD_SCORE, MISS 0
public static int groundLevel(float mean)                      // >= DUST_AT 3, >= FINE_AT 2, else 1
public static float grindScore(ItemStack reagent)              // ground 1/2/3 → COARSE/FINE/DUST_SCORE; in GRINDABLE → UNGROUND_SCORE; else -1
public static float stepScore(float stirMean, float grind)     // grind < 0 ? stirMean : stirMean * (1 - GRIND_WEIGHT) + grind * GRIND_WEIGHT
public static BrewGrade grade(float quality, int steps)        // steps == 0 → PLAIN; mean = quality / steps; thresholds above, else CRUDE
public static float bonus(ServerPlayer p)                      // sum of ZONE_BONUS
```
Checks of the numbers: all-perfect stirs on an ingredient thrown in whole give 0.6 + 0.12 = 0.72 (Fine). Coarse gives
0.84 (Expert). Fine-ground gives 0.94 (Expert). Dust gives 1.0. With Dust, Masterwork needs a stir mean of 0.917 or
better: one Good in six turns still makes it (0.933), two don't (0.867). All-Good stirs on an ingredient thrown in
whole give 0.48 (Plain, i.e. vanilla). All misses give Crude.

### `BrewingNet`
```java
static final double REACH_SQ = 8 * 8;
/** Server → client: the grinding or stirring bar at this block (off when active is false). last: the stroke just judged, -1 none. */
public record BrewBar(BlockPos pos, boolean active, int game, int total, int done, float bonus, long seed, int last) // TYPE "brew_bar"
    // StreamCodec.of(...) like SmithingNet.Session (SmithingNet.java:55-63); static BrewBar off(BlockPos pos)
/** Client → server: a stroke at this arrow position (0-100) for this game. */
public record BrewStroke(BlockPos pos, int game, float arrow) // TYPE "brew_stroke"; StreamCodec.composite(BlockPos, VAR_INT, FLOAT)
static void register() // PayloadTypeRegistry.clientboundPlay().register(BrewBar...), serverboundPlay().register(BrewStroke...),
    // ServerPlayNetworking.registerGlobalReceiver(BrewStroke.TYPE, ...): player.blockPosition().distSqr(pos) <= REACH_SQ and isLoaded,
    // GRIND_GAME → (getBlockEntity(pos) instanceof MortarEntity m) m.stroke(player, arrow); STIR_GAME → CauldronStirring.stroke(player, pos, arrow)
```

### `MortarBlock` / `MortarEntity`
- Shape: `Shapes.or(Block.box(4, 0, 4, 12, 5, 12), Block.box(9, 4, 6, 11, 11, 8))` (the bowl, and the pestle leaning up).
- Ticker: `MortarEntity::serverTick` on the server only (as `LapidaryWheelBlock.getTicker`).
- Entity constants: `HOLDS = 4`, `MIN_STROKE_GAP = 4`, `CHECK_TICKS = 20`, `LEAVE_SQ = 10 * 10`, `RESEND_TICKS = 40`,
  `FINISH_GRACE_TICKS = 2`.
- `use(ServerPlayer p, ItemStack held)`:
  1. A result is there → it goes back to the player (`placeItemBackInInventory`, `Prediction.SERVER_ONLY`), `STONE_HIT` 0.6F/1.2F.
  2. Contents are empty, or the held item `isSameItemSameComponents` as the contents, and the held item is in
     `GRINDABLE`, its `ground` is under 3 and there is room → move `min(held, HOLDS - count)` in, `STONE_PLACE` 0.6F/1.4F.
     Held Dust → `already_ground`. Held but not in `GRINDABLE` → `cant_grind`. A different item while it holds
     something → `other`. Full → `full` (with HOLDS).
  3. Empty hand + sneak, no game in progress → contents back.
  4. Empty hand with contents → start (`grinding = new Grinding(0, 0, 0, random.nextLong() | 1L)`) or resume. The busy
     rule (`busy`) is the lapidary's, then `sendSession`.
  5. Empty hand, nothing in it → `empty`.
- `stroke(p, arrow)`: the lapidary's checks, without the settle/chip rule. Then `judge(GRIND, ...)`, `hit`, and
  effects: `GRINDSTONE_USE` (PERFECT 0.7F/1.5F, GOOD 0.5F/1.1F, MISS 0.6F/0.6F) and `ItemParticleOption(ITEM, contents)`
  x6 (PERFECT adds CRIT x4). Finish at `done >= GRIND.strokes()`.
- `finish`: `level = max(existing ground, groundLevel(score / strokes))`. `result = contents.copy()` with `GROUND = level`.
  Contents empty, game null, `finishedBy/At`, `BrewBar.off`, `AMETHYST_BLOCK_CHIME` (Dust 1.0F/1.4F, else 0.6F/1.0F),
  overlay `done` with (`result.getHoverName()`, the ground line).
- Save keys: `contents`, `result` (`ItemStack.OPTIONAL_CODEC`), `grinding` (`Grinding.CODEC`). The client sync is the
  lapidary's (`getUpdatePacket`/`getUpdateTag`). `preRemoveSideEffects` drops both stacks and ends the session.

### `BrewersAlembicBlock` / `BrewersAlembicEntity`
- Shape: `Shapes.or(Block.box(3, 0, 3, 13, 8, 13), Block.box(6, 8, 6, 10, 13, 10), Block.box(10, 10, 7, 15, 12, 9))`
  (the pot, the neck and the spout).
- `BOTTLES` 0-3, kept equal to `bottles.size()` by the entity (`level.setBlock(pos, state.setValue(BOTTLES, n), Block.UPDATE_ALL)`).
- Entity constants: `HOLDS = 3`, `MIN_BOTTLES = 2`, `BOTTLE_LOSS = 1`, `DISTIL_TICKS = 1200` (60 s over heat),
  `PUFF_TICKS = 20`.
- `use(ServerPlayer p, ItemStack held, InteractionHand hand)`:
  1. `BrewGrades.isBrew(held)`: `!Knowledge.knows(p, Brewing.DISTILLING)` → `unknown`. `distilled` → `distilled_once`.
     Masterwork → `masterwork`. Different from the bottles already in → `not_same`. Full → `full`. Otherwise move one
     bottle in (`held.consume(1, p)`; it stacks to 1, so the hand empties), `progress = 0`, `BOTTLE_EMPTY` 0.8F/1.2F,
     update `BOTTLES`.
  2. A potion with no effects (water, awkward, mundane, thick) → `nothing_to_distil`. Any other item → `not_a_brew`.
  3. Empty hand + sneak → every bottle back, `progress = 0`.
  4. Empty hand → status overlay: no bottles `empty`; 1 bottle `too_few`; done `done`; cold `cold`; else `status`
     (name, grade, count, percent).
- `serverTick`: when `bottles.size() >= MIN_BOTTLES` and not done (any undistilled bottle):
  `if (!CauldronBrewing.heated(level, pos)) return;` `progress++`. Every `PUFF_TICKS`:
  `ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, colour | 0xFF000000)` x2 at the spout, plus CLOUD x1 (the
  colour is the first bottle's `POTION_CONTENTS.getColor()`, the same as `CauldronBrewing.puff`). At `DISTIL_TICKS`, `distil()`.
- `distil()`: `next = BrewGrades.orPlain(first).higher()`; `keep = bottles.size() - BOTTLE_LOSS`; the new list is
  `keep` copies of the first bottle, each `BrewGrades.set(copy, next)` and `markDistilled(copy)`.
  `Containers.dropItemStack(level, x + 0.5, y + 1, z + 0.5, new ItemStack(Items.GLASS_BOTTLE))`, `progress = 0`,
  `BREWING_STAND_BREW` 1.0F/1.0F (Masterwork adds `PLAYER_LEVELUP` 0.6F/1.4F), and HAPPY_VILLAGER x6.
- Save: `bottles` (`ItemStack.CODEC.listOf()`), `progress`. `preRemoveSideEffects` drops the bottles.
- `animateTick`: CLOUD wisp 1-in-5 when `BOTTLES >= 2` and `CauldronBrewing.heated` (it reads block states, so it
  works on the client).

## 7. Lang

`lang/parts/alchemy_brewing.json` (new):
```json
{
  "block.jaymospowers.mortar": "Mortar and Pestle",
  "block.jaymospowers.brewers_alembic": "Brewer's Alembic",
  "brew_grade.jaymospowers.crude": "Crude Brew",
  "brew_grade.jaymospowers.plain": "Plain Brew",
  "brew_grade.jaymospowers.fine": "Fine Brew",
  "brew_grade.jaymospowers.expert": "Expert Brew",
  "brew_grade.jaymospowers.masterwork": "Masterwork Brew",
  "tooltip.jaymospowers.brew.distilled": "Distilled",
  "tooltip.jaymospowers.ground.1": "Coarsely Ground",
  "tooltip.jaymospowers.ground.2": "Finely Ground",
  "tooltip.jaymospowers.ground.3": "Ground to Dust",
  "hud.jaymospowers.brewing.grind": "Grind %s/%s",
  "hud.jaymospowers.brewing.stir": "Stir %s/%s",
  "message.jaymospowers.mortar.empty": "Put a brewing ingredient in to grind it",
  "message.jaymospowers.mortar.cant_grind": "The pestle can't grind that",
  "message.jaymospowers.mortar.already_ground": "That's ground as fine as it goes",
  "message.jaymospowers.mortar.other": "Grind what's in it first",
  "message.jaymospowers.mortar.full": "It holds %s at a time",
  "message.jaymospowers.mortar.busy": "Someone else is grinding here",
  "message.jaymospowers.mortar.done": "%s: %s",
  "message.jaymospowers.brewers_alembic.unknown": "You don't know how to work it yet (Codex: Distilling Brews)",
  "message.jaymospowers.brewers_alembic.not_a_brew": "Only a bottled brew goes in",
  "message.jaymospowers.brewers_alembic.nothing_to_distil": "There's nothing in that worth distilling",
  "message.jaymospowers.brewers_alembic.masterwork": "That brew can't be bettered",
  "message.jaymospowers.brewers_alembic.distilled_once": "That brew has been distilled already",
  "message.jaymospowers.brewers_alembic.not_same": "Only bottles of the same brew go in together",
  "message.jaymospowers.brewers_alembic.full": "It holds three bottles",
  "message.jaymospowers.brewers_alembic.empty": "Put two or three bottles of one brew in",
  "message.jaymospowers.brewers_alembic.too_few": "It needs at least two bottles of the brew",
  "message.jaymospowers.brewers_alembic.cold": "It needs a fire under it",
  "message.jaymospowers.brewers_alembic.status": "%s (%s), %s bottles: %s%% distilled",
  "message.jaymospowers.brewers_alembic.done": "Distilled. Sneak-use with an empty hand to take the bottles",
  "message.jaymospowers.cauldron_brew.busy": "Someone else is stirring this",
  "message.jaymospowers.cauldron_brew.stir_hint": "Stir as the marker crosses the green",
  "research.jaymospowers.distilling": "Distilling Brews",
  "research.jaymospowers.distilling.text": "A Brewer's Alembic set over a fire boils a brew down. Two or three bottles of the same brew give one bottle fewer, each a grade better, and the empty bottle back. A brew takes it only once, and a Masterwork can't be bettered.",
  "jei.jaymospowers.mortar": "Put up to four of a brewing ingredient in and grind it with an empty hand, in time with the bar. Ground ingredients brew better potions in a cauldron; whole ones still work but brew worse.",
  "jei.jaymospowers.brewers_alembic": "Over a fire, two or three bottles of one brew distil into one fewer bottle, a grade better (Crude, Plain, Fine, Expert, Masterwork). Once per brew. Needs Distilling Brews in the Codex."
}
```
`lang/parts/alchemy_cauldron.json` (changed values):
```json
"message.jaymospowers.cauldron_brew.stirred": "Stirred in: %s (%s so far)",
"message.jaymospowers.cauldron_brew.unstirred": "%s, unstirred (%s fills). Stir it with a stick, in time with the bar",
"message.jaymospowers.cauldron_brew.holds": "%s, %s, %s fills left"
```

## 8. Client (`BrewingClient`)

- Constants: `BAR_HALF = 80, BAR_HEIGHT = 7, FLASH_TICKS = 8, MISS_COLOR = 0xFFE04040, AWAY_SQ = 10 * 10`, plus the
  colours from `TimingBar`.
- `init()`: `ClientPlayNetworking.registerGlobalReceiver(BrewingNet.BrewBar.TYPE, ...)`. This is the same
  session-replace logic as `JewelleryClient` (lines 54-69): `fresh` resets the arrow, `last >= 0` flashes.
  `MortarBlock.clientStroke = (p, pos) -> stroke(p, pos, BrewQuality.GRIND_GAME)`.
  `CauldronStirring.clientStir = (p, pos) -> stroke(p, pos, BrewQuality.STIR_GAME)`.
  `ClientTickEvents.END_CLIENT_TICK.register(BrewingClient::tick)`.
  `HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("brewing_bar"), BrewingClient::bar)`.
  `ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> tooltip(stack, lines))`.
  `BlockEntityRenderers.register(Brewing.MORTAR_ENTITY, MortarRenderer::new)`.
- `tick`: the arrow sweep from `JewelleryClient.tick` with `BrewQuality.speed(bar(game), done)`. The session drops
  when away or when the block is no longer a `MortarBlock` (grind) or `Blocks.WATER_CAULDRON` (stir).
- `stroke(player, pos, game)`: false unless the session is this pos and game and `done < total`. A local `gap`
  counter drops strokes closer than the game's gap (`MortarEntity.MIN_STROKE_GAP` / `CauldronStirring.MIN_STROKE_GAP`)
  and still returns true. Otherwise `judge` locally (flash), send `BrewStroke(pos, game, arrow)`, show `done + 1` at
  once, and return true.
- `bar`: `HudZones.place(HudZones.BELOW, "jaymospowers:brewing", 50, BAR_HALF * 2 + 4, BAR_HEIGHT + 6 + 4 + 9)` →
  `TimingBar.draw(...)`, then the label `hud.jaymospowers.brewing.grind|stir` with (done + 1, total) centred under it.
- `tooltip`: for `BrewGrades.of(stack)` insert `grade.displayName()` at index 1, and "Distilled" (GRAY) after it when
  set. For `ground > 0`, insert `tooltip.jaymospowers.ground.<n>` (GRAY) at index 1. Masterwork also gets the gold name
  line, as `SmithingClient.tooltip` does (line ~149).
- No new key binding: everything uses the vanilla use key.

## 9. Data

`data/jaymospowers/tags/item/mortar_grindable.json`:
```json
{ "values": ["minecraft:nether_wart", "minecraft:spider_eye", "minecraft:fermented_spider_eye", "minecraft:ghast_tear",
  "minecraft:magma_cream", "minecraft:rabbit_foot", "minecraft:glistering_melon_slice", "minecraft:golden_carrot",
  "minecraft:pufferfish", "minecraft:phantom_membrane", "minecraft:turtle_scute", "minecraft:breeze_rod"] }
```
`recipe/mortar.json`: `crafting_shaped`, category misc, pattern `["  K", "S S", " S "]`, `K` `minecraft:stick`,
`S` `#minecraft:stone_crafting_materials` (exists in 26.3), result `jaymospowers:mortar` x1.
`recipe/brewers_alembic.json`: `crafting_shaped`, category misc, pattern `[" G ", "CBC", "CCC"]`, `G` `minecraft:glass`,
`B` `minecraft:glass_bottle`, `C` `minecraft:copper_ingot`, result `jaymospowers:brewers_alembic` x1. This is different
from the essence alembic's `CGC / G G / CCC`.
Loot tables: copies of `loot_table/blocks/alembic.json` with the id swapped (`random_sequence` too).

Blockstate `brewers_alembic.json` (multipart):
```json
{ "multipart": [
  { "apply": { "model": "jaymospowers:block/alchemy/brewers_alembic" } },
  { "when": { "bottles": "1|2|3" }, "apply": { "model": "jaymospowers:block/alchemy/brewers_alembic_bottle_1" } },
  { "when": { "bottles": "2|3" },   "apply": { "model": "jaymospowers:block/alchemy/brewers_alembic_bottle_2" } },
  { "when": { "bottles": "3" },     "apply": { "model": "jaymospowers:block/alchemy/brewers_alembic_bottle_3" } } ] }
```

## 10. Art (`tools/gen_alchemy_brewing.py`, 16x16, run `python -I tools/gen_alchemy_brewing.py`)

The style is the crucible generator's (`tools/gen_alchemy_crucible.py`): flat shading, a 1 px darker rim and our palette.
- `textures/block/alchemy/mortar_stone.png`: grey granite with flecks (outside of the bowl).
- `textures/block/alchemy/mortar_inner.png`: darker, smoother stone with a pale powder ring (inside of the bowl).
- `textures/block/alchemy/pestle.png`: pale stone head over a short oak-brown handle.
- `textures/block/alchemy/brewers_alembic_rack.png`: a dark oak rack strip for the bottle stands.
- The Brewer's Alembic's pot and neck **reuse** `alembic_copper.png` and the bottles reuse `alembic_glass.png`
  (both ours).
- Models: `mortar.json` (bowl walls 4-12 x 0-5, inner floor at 1, pestle 9-11 x 4-11 tilted 22.5°), `brewers_alembic.json`
  (pot 3-13 x 0-8, neck 6-10 x 8-13, spout 10-15 x 10-12, rack strip along the front at y 0-1), and each
  `brewers_alembic_bottle_<n>.json` one small bottle (3 x 5 x 3) in the rack at x 2, 6.5 or 11. Item models point at
  the block models.
- Before release, rerun `python -I tools/excalibur_style/grade.py` (docs/Excalibur-Style.md:44) so `excalibur_style`
  gets graded copies of the four new textures.

## 11. Compatibility checks (what must not break, and why it doesn't)

- **Vanilla brewing stands:** no mixin, no recipe change. Ground ingredients match (item-only `Ingredient`), and graded
  potions match as inputs (`PotionIngredient` tests item + potion) and come out ungraded.
- **Splash/lingering:** made by the stand's own recipes in the cauldron, as now. The duration factor reaches the
  thrown potion (`ThrownSplashPotion`:43) and the cloud (`AreaEffectCloud`:376-385).
- **Splash-water douse** (`SplashWaterDouseMixin`) and the sneak-throw of a water bottle (`CauldronBrewing`
  UseItemCallback): `POTION_CONTENTS` is never changed, so `is(PotionTags.DOUSES_FIRE)` / `is(Potions.WATER)` hold.
- **Kitchen tanning** (empty hand on a water cauldron) still gets PASS. Kitchen heat (`KITCHEN_HEAT`) heats the
  Brewer's Alembic through `CauldronBrewing.heated`.
- **Kingdoms villagers:** `Routines` housework at cauldrons only plays a sound; `JobHalls` builds a water cauldron by
  the cleric's stand; `Healing` looks for a brewing stand. None reads brews or grades, and nothing here touches them.
  Traded and looted potions are ungraded (vanilla).
- **Apotheosis-style loot / graded loot:** `GradedLoot.gradable` needs a damageable item, so potions are never forged-graded,
  and `Smithing.GRADE` is a different component from `brew_grade`. Sockets (`gem_sockets`) don't touch potions.
- **Old saves:** `Brew` codec fields are optional; brews stirred in 0.20.8 bottle as Plain.
- **Dedicated servers:** no new attachments; payloads registered in common init on both sides (feature-local, as `SmithingNet`).
- **Tipped arrows:** do not carry the grade (by design, noted in MODLOG).

## 12. Build and check

`./gradlew build` in the root only (no dev client/server runs). Before editing, check these in the 26.3 sources if the
build complains: `Unit.STREAM_CODEC`, `Codec.intRange`, `IntegerProperty.create`, `ItemStack.isSameItemSameComponents`
(ItemStack.java:642), and `IRecipeRegistration.addIngredientInfo(ItemLike, Component...)` in JEI.
One editor track is enough (the cauldron, the package and the client are interlocked). A verifier then re-reads the
compatibility list in section 11 against the diff.

In-game checks for Andy (not run here):
1. Brew Swiftness with whole nether wart and sugar, stirring only Goods: the bottle says Plain Brew and lasts 3:00.
2. Grind the wart to Dust, stir all Perfect: Masterwork, 6:00, glinting, drinks in about a second.
3. Gunpowder into a graded brew: the splash bottle keeps the grade and the cloud/splash lasts longer.
4. Douse a fire with a thrown water bottle; brew in a stand with ground wart; put a graded potion into a stand.
5. Brewer's Alembic over a campfire: 3 Fine bottles → 2 Expert + an empty bottle after 60 s; the timer pauses when the
   fire goes out; it refuses a second distil and a Masterwork.
6. Two players at one cauldron: the second gets "Someone else is stirring this".

## 13. MODLOG entry (append to `MODLOG.md`, after the 0.20.8 entry)

```markdown
- **0.20.13, brewing quality (Patch 7)** (`alchemy/brewing/`, `alchemy/cauldron/CauldronStirring`, plan
  `docs/Brewing-Quality-Plan.md`): Andy picked the idea of Immersive Brewing; this is our own version (none of its code
  or art).
  - **Mortar and Pestle:** up to four of a brewing ingredient (`#jaymospowers:mortar_grindable`), five strokes on the
    timing bar, and the batch comes out Coarsely Ground, Finely Ground or Ground to Dust (`jaymospowers:ground` on the
    vanilla item, so it still works in stands and recipes). Coarse or Fine can be ground again.
  - **Timed stirring:** the cauldron's six stir clicks are now six turns on a timing bar (Perfect, Good, Miss), the
    floating ingredient still swirling round each turn.
  - **Graded brews:** each ingredient is a step scored on its grind and its stir; the brew's mean gives Crude, Plain,
    Fine, Expert or Masterwork Brew on bottling (`jaymospowers:brew_grade`). Durations x0.75 / 1 / 1.25 / 1.5 / 2
    through vanilla's `potion_duration_scale` (so splash and lingering keep it), drinking x1.15 / 1 / 0.9 / 0.8 / 0.6
    through the stack's own `consumable`, Masterwork glints. Stand-made potions are ungraded (Plain). Tipped arrows don't
    carry a grade.
  - **Brewer's Alembic** (`jaymospowers:brewers_alembic`, research Distilling Brews): over heat, 2-3 bottles of one
    brew become one fewer, a grade better, after 60 s, once per brew. The essence Alembic is unchanged.
  - Tuning: `BrewQuality` (bars, grind and grade thresholds), `BrewGrade` (multipliers), `MortarEntity`,
    `BrewersAlembicEntity`, `CauldronStirring`. No new attachments, mixins or keys. Kingdoms and Kitchen untouched.
    Built, not played.
```

## 14. Open questions for Andy (defaults are built)

1. Should a vanilla brewing stand's potions be distillable at all (default: yes, as Plain, so up to Fine)?
2. Should Masterwork also strengthen instant potions (Healing/Harming) a little? Default: no, only faster drinking.
3. Should a patron blessing (the Hearthkeeper?) or a Kingdoms skill widen the brewing bars through `BrewQuality.ZONE_BONUS`?
   That would mean touching Kingdoms: Kingdoms 0.7.511, loading hint 91 with `HINTS = 91` in `StoryHoldScreen`.
   Default: not now.
4. Gate the Brewer's Alembic behind Codex research (default: yes, *Distilling Brews*), or let anyone use it?
5. Should the healing brews (bone-knitting elixir, draught of vigour) take grades later? Default: not in this patch.

Optional Kingdoms loading hint (only if Andy wants Kingdoms touched, hint 91, `HINTS = 91`, Kingdoms 0.7.511, tag `(0.7.511)`):
"Grind brewing ingredients in a Mortar and Pestle and stir in time with the bar: a Masterwork brew lasts twice as long."
