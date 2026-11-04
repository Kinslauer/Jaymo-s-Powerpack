# Jewellery (Patch 4): implementation spec

Powers **0.19.87** (`gradle.properties`), Kingdoms **0.7.496** (`kingdoms/gradle.properties`). Worktree `/home/user/jewel`,
branch `jewellery-work` (base `eb3ef5b6`). Tag new code comments `(0.19.87)` in Powers and `(0.7.496)` in Kingdoms.

Andy's approved plan: *"A jeweller's bench and a lapidary wheel: cut a gem (four cuts, chipping if you rush), set it in
a forged ring, amulet, bracelet or circlet frame, graded Poorly to Masterwork like forged gear. Town smiths take
jewellery orders. Found baubles stay as they are."* This is our own version, inspired by the Immersive Jewelry mod.
None of its code or art is used.

Patch 5 (not this patch) sets cut gems into sockets on forged weapons and armour. So the cut gem here is one item
with one data component (`jaymospowers:cut_gem`), and every rule about a gem lives in `Gems` / `CutGem`, not in the
jewellery items.

---

## 0. Decisions taken (and why)

| # | Question | Decision |
|---|---|---|
| D1 | "Four cuts" | **Four cut styles**: Cabochon, Table, Rose, Brilliant. A harder cut takes more strokes on the wheel and gives a bigger bonus. The other reading (every gem takes four strokes) is in Open questions. |
| D2 | "Chipping if you rush" | A stroke made before the wheel has spun back up (`SETTLE_TICKS` after the last stroke) **chips** the gem. That stroke scores as a miss, and each chip takes the finished cut **one grade lower**. Gems never shatter, so no diamond is lost. |
| D3 | Bracelet slot | **A new eighth bauble slot, Wrist**, added at the end (`Baubles.SLOTS[7]`, screen position `{152, 62}`, which fills the empty bottom-right cell). Saved lists of 7 pad to 8 (`Baubles.worn`), so old saves still load. The charm slot keeps the rabbit foot. |
| D4 | Circlet slot | HEAD (as the Diver's Circlet). Amulet goes to AMULET and ring to RING (two slots). |
| D5 | Grade of a piece | The frame's forging grade (from the anvil, carried through `Grades.carry`), capped by the gem's cut grade: `min(frame, gem)`. It is stored in the usual `jaymospowers:forging_grade`, so it reads "Expertly Forged" like gear and Kingdoms can read it. |
| D6 | Gem quality | Its own component (`CutGem.quality`, a `Grade` id), **not** `forging_grade`. The tooltip says "Expertly Cut", not "Expertly Forged". |
| D7 | Frames | Hammered on the Smithing Anvil through `Forging.PROVIDERS` (gold, silver, copper). Blueprints, the stone-anvil cap, quenching, the maker's mark and Blacksmith rank all apply unchanged. No grid recipe. |
| D8 | Bonus | One attribute per gem, scaled by grade × cut × metal × frame kind. It is worked out from the stack every bauble refresh, through a new stack-aware `BaubleItem.bonuses(ItemStack)`. The seven found baubles keep their item-level bonuses and do not change. |
| D9 | Wheel judging | The client sends the arrow position. The server judges it with `Forging.judge` (its own seed, difficulty and zone bonus) and decides chips from its own game time. This is stricter than the anvil, which trusts the client's result. |
| D10 | Town smith orders | Kingdoms GATHER quests with a grade floor: the target is `"<item id>@<grade>"`. Count and take match only stacks at that grade or better. This reuses the whole GATHER flow, so no new quest kind is needed. The pay is emeralds, kept as constants so the coin change can switch them. |
| D11 | Gem list | One Java list (`Gems.ALL`) holds each gem's colour, hardness and bonus. No item tag, because a tag would only duplicate the list and could drift from it. |
| D12 | Attachments | None. Everything is data components and block-entity NBT, so `JaymosPowers.registerAttachments()` is **not** touched. |

Gold armour (scan note 0.5) is still forged by `ArmourForging`. That is unrelated and is not touched here: jewellery
only needs gold as a metal, and gold as a metal is allowed.

---

## 1. Player-facing rules (also appended to `docs/Smithing.md` as "## Jewellery")

1. **Frames.** Lay hot gold, silver or copper ingots on a Smithing Anvil, hammer to open the forge screen and pick a
   Ring Frame (1 ingot), Bracelet Frame (2), Amulet Frame (2) or Circlet Frame (3). Hammer it out like a head. It comes
   off hot: quench it in water, or it loses a grade cooling in the air. A Blueprint for the frame's shape raises the
   cap above Expertly Forged, as for weapons.
2. **Cutting.** Place a Lapidary Wheel. Use it holding a cuttable gem to lay one gem on it. Use it again (empty hand
   or anything else) to choose a cut. The bar from the anvil appears under the crosshair: use the wheel when the arrow
   is in the zone. Each stroke spins the wheel down for 0.8 s (the bar dims). A stroke while it is dimmed chips the
   gem. When the last stroke lands, the cut gem sits on the wheel; use the wheel to take it. Once a cut has started,
   the gem can't come off until it's finished. Walking away pauses it, and you can come back to it.
3. **Setting.** At a Jeweller's Bench put a cooled frame in the left slot and a cut gem in the middle. The piece
   appears on the right. Its grade is the lower of the frame's and the gem's.
4. **Wearing.** Rings go in either ring slot, amulets in the amulet slot, circlets on the head, bracelets in the new
   Wrist slot. Each gives a small bonus decided by its gem, grade, cut, metal and frame.
5. **Orders.** Town armourers, weaponsmiths and toolsmiths sometimes ask for a gem amulet, ring or bracelet of at
   least a given grade, and pay more than for their usual errands.
6. The seven found baubles (Heartstone Amulet ... Rabbit's Foot Charm) and the lantern pole are unchanged.

---

## 2. Numbers (every one a `static final` at the top of its class)

### 2.1 Gems: `jewellery/Gems.java`
| Gem id (`Gem.id`) | Item | Colour (RGB) | Hardness | Attribute | Base amount | Operation |
|---|---|---|---|---|---|---|
| `emerald` | `minecraft:emerald` | `0x17DD62` | 1.1 | `LUCK` | 0.5 | ADD_VALUE |
| `diamond` | `minecraft:diamond` | `0x8FF4E6` | 1.3 | `ARMOR_TOUGHNESS` | 0.5 | ADD_VALUE |
| `amethyst` | `minecraft:amethyst_shard` | `0xA77BE0` | 0.9 | `MAX_HEALTH` | 1.0 | ADD_VALUE |
| `lapis` | `minecraft:lapis_lazuli` | `0x2E5CC9` | 0.8 | `WATER_MOVEMENT_EFFICIENCY` | 0.08 | ADD_VALUE |
| `quartz` | `minecraft:quartz` | `0xEDE6DD` | 0.9 | `BURNING_TIME` | -0.08 | ADD_MULTIPLIED_BASE |
| `zanite` | `jaymospowers:zanite_gemstone` | `0x7A6CF0` | 1.0 | `MINING_EFFICIENCY` | 1.0 | ADD_VALUE |
| `glint` | `jaymospowers:glint_gemstone` | `0xF2D16B` | 1.0 | `MOVEMENT_SPEED` | 0.02 | ADD_MULTIPLIED_BASE |
| `corrobonite` | `jaymospowers:corrobonite_crystal` | `0x5FC9B8` | 1.1 | `KNOCKBACK_RESISTANCE` | 0.05 | ADD_VALUE |
| `golden_amber` | `jaymospowers:golden_amber` | `0xF0A030` | 0.8 | `OXYGEN_BONUS` | 0.5 | ADD_VALUE |

Every attribute was checked against the 26.3 `Attributes` (`ARMOR_TOUGHNESS`, `LUCK`, `MAX_HEALTH`,
`WATER_MOVEMENT_EFFICIENCY`, `BURNING_TIME`, `MINING_EFFICIENCY`, `MOVEMENT_SPEED`, `KNOCKBACK_RESISTANCE`,
`OXYGEN_BONUS` all exist). Items are kept as `Identifier`s and looked up when used, so the order of Aether's init
doesn't matter.

### 2.2 Cuts: `jewellery/Cut.java`
| Cut (`id`) | Strokes | Bonus factor | Difficulty factor |
|---|---|---|---|
| `cabochon` | 3 | 0.85 | 0.9 |
| `table` | 4 | 1.0 | 1.0 |
| `rose` | 5 | 1.15 | 1.1 |
| `brilliant` | 6 | 1.3 | 1.2 |

Wheel difficulty = `gem.hardness × cut.difficulty` (a brilliant-cut diamond is 1.56), fed to the shared
`Forging.perfectHalf/goodHalf/speed/judge`.

### 2.3 Cutting: `jewellery/GemCutting.java`
- `SETTLE_TICKS = 16`: after a stroke the wheel needs this long before the next one, or it's a rush.
- `SETTLE_GRACE = 2`: the server forgives this many ticks of the settle (packet lag only makes strokes later).
- `MIN_STROKE_GAP = 3`: strokes closer than this are dropped as packet spam (they are not chips).
- `CHECK_TICKS = 20`, `LEAVE_SQ = 10 * 10`, `RESEND_TICKS = 40`: the same as the anvil.
- `CHIP = 3`: the result code for a chipped stroke (besides `Forging.MISS 0 / GOOD 1 / PERFECT 2`).
- Grade = `Forging.grade(new Smithing.Work("cut/" + gem + "/" + cut, strokes, score, perfects, seed), strokes, random)`,
  then `lower()` once per chip. A Masterwork roll (`Forging.MASTER_CHANCE`) only counts with no chips.

### 2.4 Frames: `jewellery/JewelKind.java`
| Kind | Bauble kind | Frame item | Piece item | Ingots | Blows (`Forging.hitsFor`) | Bonus factor |
|---|---|---|---|---|---|---|
| `RING` | `RING` | `ring_frame` | `gem_ring` | 1 | 3 | 1.0 |
| `AMULET` | `AMULET` | `amulet_frame` | `gem_amulet` | 2 | 4 | 1.5 |
| `BRACELET` | `WRIST` (new) | `bracelet_frame` | `gem_bracelet` | 2 | 4 | 1.0 |
| `CIRCLET` | `HEAD` | `circlet_frame` | `gem_circlet` | 3 | 6 | 1.25 |

`JewelFrames.METALS = {GOLD, SILVER, COPPER}` (`Molten`). Metal bonus factor: `COPPER 0.8, SILVER 1.0, GOLD 1.15`
(`Jewellery.METAL_FACTOR`, an `EnumMap`). The forging difficulty is the existing `Forging.difficulty` (gold and
copper 0.8, silver 0.9), so no steel and no `SmithTiers` gate.

### 2.5 Grade factor: `Jewellery.GRADE_FACTOR`
`POOR 0.5, WELL 1.0, EXPERT 1.25, PERFECT 1.5, MASTER 2.0`.

**Bonus** = `gem.amount × GRADE_FACTOR[piece grade] × cut.bonus × METAL_FACTOR[metal] × kind.bonus`.
For example, a Well silver table-cut emerald ring gives +0.5 Luck. The best possible (Masterwork gold brilliant
amulet) gives 0.5 × 2 × 1.3 × 1.15 × 1.5 = +2.24 Luck; the same with amethyst gives +4.5 max health (2.25 hearts,
next to the Heartstone's 4).

### 2.6 Kingdoms orders: `QuestBoard.java`
- `JEWEL_ORDER_EMERALDS = {6, 10, 16, 24, 40}` (indexed by the floor grade, Poor..Masterwork), plus
  `random.nextInt(JEWEL_ORDER_SPREAD + 1)` with `JEWEL_ORDER_SPREAD = 4`. Hearts as GATHER. Emeralds until the coin
  change (it converts the `Reward.emeralds` field everywhere, this included).
- Orders: armorer `armorer.amulet` = `jaymospowers:gem_amulet@well` ×1; weaponsmith `weaponsmith.ring` =
  `jaymospowers:gem_ring@expert` ×1-2; toolsmith `toolsmith.bracelet` = `jaymospowers:gem_bracelet@well` ×1. No order
  asks above Expert, the most a smith can reach without a blueprint.

### 2.7 Wandering trader
Four blueprints at **12 emeralds** each (`jewel/ring_frame`, `jewel/amulet_frame`, `jewel/bracelet_frame`,
`jewel/circlet_frame`), `max_uses 1`, `reputation_discount 0.05`, in the existing `uncommon` pool.

---

## 3. Powers: new files

All in `src/main/java/com/jaymospowers/jewellery/` unless stated. Class docs start `(0.19.87) ...`.

### 3.1 `Jewellery.java`: registration and the piece rules
```java
public final class Jewellery {
    public static final Map<Grade, Float> GRADE_FACTOR;            // 2.5
    public static final Map<Molten, Float> METAL_FACTOR;           // 2.4
    public static DataComponentType<CutGem> CUT_GEM;               // "cut_gem"
    public static DataComponentType<String> JEWEL_METAL;           // "jewel_metal" (a Molten id)
    public static Item CUT_GEM_ITEM;                               // "cut_gem"
    public static final Map<JewelKind, Item> FRAMES, PIECES;       // EnumMaps
    public static Block JEWELLERS_BENCH, LAPIDARY_WHEEL;
    public static BlockEntityType<LapidaryWheelEntity> LAPIDARY_WHEEL_ENTITY;
    public static MenuType<JewellersBenchMenu> BENCH_MENU;

    public static void init();
    /** The piece set from this frame and cut gem, or EMPTY. Server and client (the bench's result). */
    public static ItemStack piece(ItemStack frame, ItemStack gem);
    /** Why this frame can't be set yet (hot: Grades.refusal), or null. */
    public static @Nullable Component refusal(ItemStack frame);
    /** The bonus a worn piece gives, or null if it has no gem, metal or grade. */
    public static @Nullable BaubleItem.Bonus bonus(ItemStack piece);
    static Item item(String id, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> props); // as Smithing.item
}
```
`init()` (called from `JaymosPowers.onInitialize` right **after** `com.jaymospowers.aether.Aether.init();`):
1. Components, in the style of `Smithing.register`: `CUT_GEM` = `builder().persistent(CutGem.CODEC).networkSynchronized(CutGem.STREAM_CODEC)`,
   `JEWEL_METAL` = `persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)`.
2. Items: `CUT_GEM_ITEM = item("cut_gem", CutGemItem::new, p -> p.stacksTo(16))`. For each `JewelKind`:
   `FRAMES.put(k, item(k.frameId, JewelFrameItem::new, p -> p.stacksTo(1)))` and
   `PIECES.put(k, item(k.pieceId, p -> new JewelleryItem(p, k), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON)))`.
3. Blocks via `Kiln.blockWithTooltip`:
   - `"jewellers_bench"`: `JewellersBenchBlock::new`, `Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)`.
   - `"lapidary_wheel"`: `LapidaryWheelBlock::new`, `Properties.of().mapColor(MapColor.STONE).strength(2.5F).sound(SoundType.WOOD).noOcclusion()`.
4. `LAPIDARY_WHEEL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("lapidary_wheel"), new BlockEntityType<>(LapidaryWheelEntity::new, Set.of(LAPIDARY_WHEEL)))`.
5. `BENCH_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("jewellers_bench"), new MenuType<>(JewellersBenchMenu::new, FeatureFlags.VANILLA_SET))`.
6. `JewelFrames.register()` (adds the `Forging.PROVIDERS` supplier, which is still before any `Forging.all()` call, since
   those all happen at run time).
7. `for (Gems.Gem g : Gems.ALL) Baubles.touch(g.attribute());` and `Baubles.picture(Baubles.Kind.WRIST, PIECES.get(JewelKind.BRACELET));`.
8. `JewelleryNet.register()`.
9. Creative output on `Kiln.tab()`: bench, wheel, each frame in silver (Well), each gem as a table cut (Well), and the
   four pieces as silver table-cut emerald (Well).

`piece(frame, gem)`: frame is a `JewelFrameItem` with `JEWEL_METAL` and a grade, and `gem.get(CUT_GEM)` is not null,
otherwise EMPTY. Then:
`out = new ItemStack(PIECES.get(kind))`; `out.set(JEWEL_METAL, metal)`; `out.set(CUT_GEM, cut)`;
`Grades.carry(frame, out)` (moves the maker's mark through `Grades.CARRIERS`, applies `ASSEMBLY_CAPS`, writes the
frame's grade); `Grades.set(out, Grade.min(Grades.of(out), cut.grade()))`;
`out.set(CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(metal, cut.cut()), List.of(gem.colour())))`;
`out.set(ITEM_NAME, Component.translatable("item.jaymospowers." + kind.pieceId + ".named", metalName(metal), gem.displayName()))`.

`bonus(piece)`: the gem, cut, metal and grade from the components, then the formula in 2.5, giving
`new BaubleItem.Bonus(gem.attribute(), amount, gem.operation())`.

`static Component metalName(String molten)` = `Component.translatable("jewel_metal.jaymospowers." + molten)`.

### 3.2 `Gems.java`: the cuttable gems
```java
public final class Gems {
    public record Gem(String id, Identifier item, int colour, float hardness, Holder<Attribute> attribute, double amount,
                      AttributeModifier.Operation operation) {
        public Component displayName();                     // "gem.jaymospowers.<id>"
        public BaubleItem.Bonus bonus(double scale);         // amount × scale; patch 5 uses this for sockets
        public Item asItem();                                // BuiltInRegistries.ITEM.getValue(item)
    }
    public static final List<Gem> ALL;                       // table 2.1, in that order
    public static @Nullable Gem of(ItemStack raw);           // by the stack's item id
    public static @Nullable Gem byId(String id);
}
```

### 3.3 `CutGem.java`: the reusable component (patch 5 builds on this)
```java
public record CutGem(String gem, String cut, String quality, int chips) {
    public static final Codec<CutGem> CODEC;   // gem, cut, quality: STRING; chips: INT optionalFieldOf("chips", 0)
    public static final StreamCodec<ByteBuf, CutGem> STREAM_CODEC; // StreamCodec.composite of the four
    public @Nullable Gems.Gem gemType();       // Gems.byId(gem)
    public Cut cutType();                      // Cut.byId(cut), TABLE if unknown
    public Grade grade();                      // Grade.byId(quality), WELL if unknown
    /** Grade × cut, the part of the bonus that comes from the stone itself (frames and sockets add their own). */
    public double scale();                     // Jewellery.GRADE_FACTOR[grade] × cut.bonus
    public int colour();                       // the gem's colour, white if unknown
}
```
The ids are stable strings saved on items. **Never rename** a gem or cut id once shipped.

### 3.4 `Cut.java` and `JewelKind.java`
Enums holding table 2.2 (`id`, `strokes`, `bonus`, `difficulty`, `displayName()` -> `cut.jaymospowers.<id>`,
`byId`) and table 2.4 (`id`, `Baubles.Kind bauble`, `frameId`, `pieceId`, `ingots`, `bonus`, `byFrame(Item)`).

### 3.5 `CutGemItem.java`
`extends Item`.
- `static ItemStack of(Gems.Gem gem, Cut cut, Grade quality, int chips)`: sets `CUT_GEM`,
  `CUSTOM_MODEL_DATA(strings [cut.id], colors [gem.colour])`, and
  `ITEM_NAME = translatable("item.jaymospowers.cut_gem.named", cut.displayName(), gem.displayName())`. When
  `quality == MASTER` it also sets `ENCHANTMENT_GLINT_OVERRIDE` true.
- `appendHoverText`: the quality line `translatable("grade.jaymospowers.cut." + id).withStyle(grade.colour)`; if
  chips > 0, `tooltip.jaymospowers.jewellery.chipped` (RED); `tooltip.jaymospowers.jewellery.gives` with the
  attribute's description (BLUE); `tooltip.jaymospowers.jewellery.gem_hint` (DARK_GRAY).

### 3.6 `JewelFrameItem.java`
`extends Item`.
- `static ItemStack of(JewelKind kind, Molten metal)`: sets `JEWEL_METAL`, `CUSTOM_MODEL_DATA(strings [metal.id])`,
  and `ITEM_NAME = translatable("item.jaymospowers." + kind.frameId + ".named", Jewellery.metalName(metal.id))`.
- The tooltip adds `tooltip.jaymospowers.jewellery.frame_hint` (DARK_GRAY). The grade and heat lines already come
  from `SmithingClient.tooltip`.

### 3.7 `JewelleryItem.java`
`extends BaubleItem`.
- ctor `(Properties p, JewelKind kind)` calls `super(p, kind.bauble, List.of())`.
- `@Override public List<Bonus> bonuses(ItemStack stack)` returns `bonus == null ? List.of() : List.of(bonus)` from `Jewellery.bonus(stack)`.
- `appendHoverText`: `super` (the slot plus the stack's bonus line, after the change in 4.1), then
  `tooltip.jaymospowers.jewellery.set_with` with the cut gem's name and its cut grade in its colour (GRAY).

### 3.8 `JewelFrames.java`: the anvil's frames
Modelled on `ProstheticForging`:
`static void register() { Forging.PROVIDERS.add(JewelFrames::options); }`. `options()` gives, for each kind and each
metal in `METALS`:
`new Forging.Option("jewel/" + kind.frameId + "/" + metal.id, JewelFrameItem.of(kind, metal), metal, kind.ingots, Forging.hitsFor(kind.ingots))`.
- That makes 12 option ids, e.g. `jewel/ring_frame/gold`. **They are saved on blanks and must never be renamed.**
- The blueprint shape is `jewel/ring_frame` (`Blueprints.shape` cuts at the last `/`), and `Blueprints.shapeName`
  gives `item.jaymospowers.ring_frame` = "Ring Frame". No change to `Blueprints.shape/shapeName` is needed.

### 3.9 `LapidaryWheelBlock.java`
`extends HorizontalDirectionalBlock implements EntityBlock`, like `SmithingAnvilBlock`.
- Shape: `Shapes.or(box(1,0,4,15,3,12), box(2,3,6,4,12,10), box(12,3,6,14,12,10), box(4,4,7,12,12,9))`, rotated for X/Z
  as the anvil does. The wheel disc stands along the facing.
- `public static BiPredicate<Player, BlockPos> clientStroke = (p, pos) -> false;` set by the client (the anvil's
  `clientBlow` pattern).
- `useItemOn(stack, ...)`:
  1. Main hand while the client has this wheel's session: client calls `clientStroke.test(player, pos)` and returns
     SUCCESS. Server: if `wheel.cutting(player)`, return SUCCESS (the stroke comes as a packet).
  2. Server: `wheel.use(player, stack)` (below), return SUCCESS. Client: return SUCCESS if `Gems.of(stack) != null`
     or the wheel is not empty, else `TRY_WITH_EMPTY_HAND`.
- `useWithoutItem` gives the same, with `ItemStack.EMPTY`.
- Ticker: server only, `LapidaryWheelEntity::serverTick`.

### 3.10 `LapidaryWheelEntity.java`
Fields: `ItemStack gem` (raw, count 1), `ItemStack result` (cut gem), `@Nullable Cutting cutting`,
`@Nullable UUID smith`, `long lastStroke`, `long lastSeen`.

`record Cutting(String cut, int done, float score, int perfects, int chips, long seed)` with a CODEC (saved under
`"cutting"`), plus `hit(int result)` and `work()` that returns a `Smithing.Work` for `Forging.grade`.

`use(ServerPlayer player, ItemStack held)` works through these in order:
1. `result` not empty: give it (`placeItemBackInInventory`), sound `AMETHYST_CLUSTER_PLACE`.
2. `gem` empty: if `Gems.of(held) != null`, then `gem = held.split(1)` with sound `STONE_PLACE`; otherwise overlay
   `message.jaymospowers.jewellery.not_a_gem` (only if `held` is not empty; with an empty hand, `wheel_empty`).
3. `cutting == null`: if the player is sneaking with an empty hand, give back the raw gem. Otherwise send
   `JewelleryNet.OpenCuts(pos, gem.copy(), Cut.values())`.
4. `cutting != null` (a resume): if `smith` is someone else who is near (`LEAVE_SQ`), show `busy`. Otherwise set
   `smith`, set `lastStroke = now - SETTLE_TICKS` and send the session.

`choose(ServerPlayer, String cutId)`: valid only if `gem` is set, `cutting == null` and `result` is empty. Then
`cutting = new Cutting(cut, 0, 0, 0, 0, level.getRandom().nextLong())`, `smith = player`,
`lastStroke = now - SETTLE_TICKS`, sound `GRINDSTONE_USE`, and send the session.

`stroke(ServerPlayer, float arrow)` does nothing unless the player is the smith, `cutting` is set and `arrow` is
finite and within 0..100. A gap under `MIN_STROKE_GAP` is ignored. Otherwise:
- `rushed = now - lastStroke < SETTLE_TICKS - SETTLE_GRACE`
- `result = rushed ? CHIP : Forging.judge(arrow, seed, done, difficulty(), Forging.bonus(player))`
- `cutting = cutting.hit(result)`: a chip scores 0 and adds 1 to `chips`.

It then sets `lastStroke = now`, swings the main hand and plays the effects. When `done >= cut.strokes` it calls
`finish`; otherwise it sends the session.

`finish(player)`: the grade comes from 2.3, then `result = CutGemItem.of(gem, cut, grade, chips)`, `gem = EMPTY`,
`cutting = null`, `smith = null`. It sends `CutSession.off`, overlay `message.jaymospowers.jewellery.cut_done` (name,
cut grade), sound `AMETHYST_BLOCK_CHIME` (`PLAYER_LEVELUP` on a Masterwork, plus `TOTEM_OF_UNDYING` particles as the
anvil does).

`difficulty()` = `gem hardness × cut difficulty`.

Effects per stroke:
| Result | Sound | Particles |
|---|---|---|
| PERFECT | `AMETHYST_BLOCK_CHIME` 0.8 / 1.5 | `CRIT` ×8 |
| GOOD | `GRINDSTONE_USE` 0.5 / 1.2 | `ELECTRIC_SPARK` ×4 |
| MISS | `GRINDSTONE_USE` 0.6 / 0.7 | `SMOKE` ×3 |
| CHIP | `SoundEvents.ITEM_BREAK.value()` 0.6 / 1.6 | `new ItemParticleOption(ParticleTypes.ITEM, gemItem)` ×8 (26.3 ctor `(type, Item)` confirmed) |

`serverTick` (every `CHECK_TICKS`): if `smith` is no longer near, end the session (the progress stays on the wheel).
`preRemoveSideEffects`: end the session and drop `gem` and `result`, so breaking the wheel loses an unfinished cut.
NBT: `gem`, `result` (`ItemStack.OPTIONAL_CODEC`), `cutting` (optional). Update packet and tag as
`SmithingAnvilEntity`.

### 3.11 `JewelleryNet.java`: the wheel's packets (SmithingNet style; reach `REACH_SQ = 8 * 8`)
| Payload | Id | Direction | Fields / codec |
|---|---|---|---|
| `OpenCuts` | `jewellery_cuts` | S→C | `BlockPos pos, ItemStack gem, List<CutChoice> cuts`; `CutChoice(String id, int strokes)` composite |
| `ChooseCut` | `jewellery_choose_cut` | C→S | `BlockPos pos, String cut` |
| `CutSession` | `jewellery_cut_session` | S→C | `pos, boolean active, int total, int done, float difficulty, float bonus, long seed, int chips, int last` (`StreamCodec.of` like `SmithingNet.Session`); `static off(pos)` |
| `Stroke` | `jewellery_stroke` | C→S | `BlockPos pos, float arrow` (`ByteBufCodecs.FLOAT`) |
`register()` registers both directions plus receivers that find the wheel through `wheel(player, pos)` (distance and
`isLoaded`, as `SmithingNet.anvil`).

### 3.12 `JewellersBenchBlock.java`
`extends HorizontalDirectionalBlock` (it has a front face).
- `useWithoutItem` (server): `player.openMenu(new SimpleMenuProvider((id, inv, p) -> new JewellersBenchMenu(id, inv, ContainerLevelAccess.create(level, pos)), Component.translatable("container.jaymospowers.jewellers_bench")))`, then return SUCCESS.
- `useItemOn` returns `TRY_WITH_EMPTY_HAND`, so the bench opens with anything in hand.

### 3.13 `JewellersBenchMenu.java`
`extends ItemCombinerMenu` (26.3 API checked: ctor `(MenuType, int, Inventory, ContainerLevelAccess,
ItemCombinerMenuSlotDefinition)`, abstract `createResult()`, `onTake(Player, ItemStack)`, `isValidBlock(BlockState)`).
- Slots: `ItemCombinerMenuSlotDefinition.create().withSlot(0, 27, 47, s -> s.getItem() instanceof JewelFrameItem).withSlot(1, 76, 47, s -> s.has(Jewellery.CUT_GEM)).withResultSlot(2, 134, 47)`.
- Client ctor `(int id, Inventory inv)` calls `this(id, inv, ContainerLevelAccess.NULL)`.
- `createResult()`: `resultSlots.setItem(0, Jewellery.refusal(frame) != null ? EMPTY : Jewellery.piece(frame, gem))`.
- `onTake`: shrink input 0 and input 1 by one, play `SMITHING_TABLE_USE` (via `access.execute`), then `createResult()`.
- `isValidBlock(state)` = `state.is(Jewellery.JEWELLERS_BENCH)`.
- `public @Nullable Component refusal()` lets the screen show why there's no result.

### 3.14 Client: `src/client/java/com/jaymospowers/client/jewellery/`
- **`JewelleryClient.java`**: `init()` is called from `JaymosPowersClient` right after `BaublesClient.init()`. It:
  - registers `MenuScreens.register(Jewellery.BENCH_MENU, JewellersBenchScreen::new)`;
  - registers `BlockEntityRenderers.register(Jewellery.LAPIDARY_WHEEL_ENTITY, LapidaryWheelRenderer::new)`;
  - adds receivers for `OpenCuts` (opens `CutScreen`) and `CutSession` (the client's session, as in `SmithingClient`;
    on `last` it flashes the bar);
  - sets `LapidaryWheelBlock.clientStroke`;
  - ticks the arrow on `END_CLIENT_TICK` (the same motion as `SmithingClient.tick`: `Forging.speed(done, difficulty)`),
    and drops the session beyond `AWAY_SQ = 10 * 10` or when the block isn't a wheel;
  - adds the bar to the HUD: `HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("gem_cutting_bar"), ...)`,
    placed with `HudZones.place(HudZones.BELOW, "jaymospowers:gem_cutting", 50, w, h)`.
  - `stroke(player, pos)` sends `Stroke(pos, arrow)` and predicts a flash locally (CHIP if
    `settle > 0`, else `Forging.judge`). It sets `settle = GemCutting.SETTLE_TICKS` and `done + 1` (the server's
    answer corrects it).
  - Bar drawing goes through the shared `TimingBar` (below). While `settle > 0` it lays a `0x90000000` dim over the
    bar, with a thin `0xFFB0B0C0` line filling left to right as the wheel spins up. The chip flash colour is
    `CHIP_COLOR = 0xFFB060FF`. Under the bar: `done+1/total`, plus a chip pip count in `CHIP_COLOR` when chips > 0.
- **`CutScreen.java`**: `extends Screen`, built like `ForgeScreen` (`UiStyle` header and rows). Title
  `screen.jaymospowers.jewellery.cuts`. Four rows, each showing:
  - the icon `CutGemItem.of(gem, cut, WELL, 0)`;
  - the cut's name;
  - `screen.jaymospowers.jewellery.strokes` (strokes);
  - `screen.jaymospowers.jewellery.bonus` (bonus factor as "x1.15").

  A click sends `ChooseCut` and closes the screen; Esc closes it.
- **`JewellersBenchScreen.java`**: `extends AbstractContainerScreen<JewellersBenchMenu>`, 176×166, drawn with
  `TinkerUi`:
  - `panel`;
  - `slots(menu, left, top, 2)` (big well on the result);
  - a "+" (two 1-px fills, `TinkerUi.DIM`) between slots 0 and 1;
  - `arrowRight` from x 98 to 128 at y 55 (`ACCENT` when there is a result, `DIM` otherwise);
  - `labels(...)`;
  - the menu's `refusal()` text in `0xFFE06060` at (8, 70) when there is one, else the result's bonus line in `DIM`.
- **`LapidaryWheelRenderer.java`**: the gem or result lying on the rest at y 13/16. It copies
  `SmithingAnvilRenderer`'s state and submit pattern (83 lines).
- **`client/tinkering/smithing/TimingBar.java`** (new, public): `static void draw(GuiGraphicsExtractor g, int left, int y, int halfWidth, int height, float centre, float good, float perfect, float arrow, int border)`,
  taken out of `SmithingClient.bar`. `SmithingClient.bar` calls it with the same numbers, so the anvil looks the
  same as before.
- **`client/tinkering/TinkerUi.java`**: make the class and its methods/constants `public`, so the bench screen in
  another package can use them. No change in behaviour.

---

## 4. Powers: changed files

### 4.1 Baubles (`src/main/java/com/jaymospowers/baubles/`)
- `Baubles.Kind`: add `WRIST("wrist")` **at the end** (tag `jaymospowers:baubles/wrist`).
- `Baubles.SLOTS`: append `Kind.WRIST`, giving `{AMULET, RING, RING, BELT, HEAD, BODY, CHARM, WRIST}` and `SIZE` 8.
  Update the class doc ("eight slots ... and a wrist (0.19.87)").
- `refresh`: `bauble.bonuses()` becomes `bauble.bonuses(worn.get(slot))`. The modifier ids keep the `bauble_<slot>_<i>`
  scheme (slot 7 is new).
- New `public static void touch(Holder<Attribute> attribute) { TOUCHED.add(attribute); }` so jewellery bonuses come
  off when the piece does.
- New `private static final Map<Kind, Item> PICTURES = new EnumMap<>(Kind.class)` with
  `public static void picture(Kind kind, Item item)`. `picture(kind)` checks `ITEMS` first (unchanged for the seven
  kinds), then `PICTURES`, then the Heartstone.
- `BaubleItem`: new `public List<Bonus> bonuses(ItemStack stack) { return this.bonuses; }`. `appendHoverText`
  iterates `bonuses(stack)` (same output for the seven found baubles).
- `BaublesMenu.SLOT_AT`: append `{152, 62}`. Update the doc comment.
- `client/baubles/BaublesScreen.java`: doc comment only ("... head, body, charm and wrist on the right"). The loops
  already use `Baubles.SIZE`.
- Lang `lang/parts/baubles.json`: add `"bauble.jaymospowers.slot.wrist": "Wrist"`.

### 4.2 Smithing
- `Blueprints.CREATIVE_SHAPES`: append `"jewel/ring_frame", "jewel/amulet_frame", "jewel/bracelet_frame", "jewel/circlet_frame"`.
- `client/tinkering/smithing/SmithingClient.java`: `bar` draws through `TimingBar.draw` (3.14).

### 4.3 Entrypoints
- `JaymosPowers.java`: add `com.jaymospowers.jewellery.Jewellery.init();` after `com.jaymospowers.aether.Aether.init();`
  with the comment `// (0.19.87) jewellery: the lapidary wheel, the jeweller's bench, forged frames and gem baubles`.
  `registerAttachments()` is **unchanged** (no AttachmentType).
- `client/JaymosPowersClient.java`: add `com.jaymospowers.client.jewellery.JewelleryClient.init();` after `BaublesClient.init();`.

### 4.4 Docs
- `docs/Smithing.md`: new section `## Jewellery (code: jewellery/, docs/Jewellery-Plan.md)` with the rules in §1.
- `MODLOG.md`: the entry in §8.
- `gradle.properties`: `version=0.19.87`.

---

## 5. Powers: data and assets

### 5.1 Tags (`src/main/resources/data/jaymospowers/tags/item/baubles/`, `"replace": false`)
- `ring.json` += `jaymospowers:gem_ring`; `amulet.json` += `jaymospowers:gem_amulet`; `head.json` += `jaymospowers:gem_circlet`.
- New `wrist.json`: `{"replace": false, "values": ["jaymospowers:gem_bracelet"]}`.
- `data/minecraft/tags/block/mineable/axe.json` += `jaymospowers:jewellers_bench`, `jaymospowers:lapidary_wheel`.

### 5.2 Recipes (`data/jaymospowers/recipe/jewellery/`)
- `jewellers_bench.json`: crafting_shaped, category `building`, pattern `["NAN", "PPP", "P P"]`, N `minecraft:iron_nugget`,
  A `minecraft:amethyst_shard`, P `#minecraft:planks`, result `jaymospowers:jewellers_bench`.
- `lapidary_wheel.json`: crafting_shaped, category `building`, pattern `["IGI", "P P"]`, I `minecraft:iron_ingot`,
  G `minecraft:grindstone`, P `#minecraft:planks`, result `jaymospowers:lapidary_wheel`.
- Frames, cut gems and pieces have **no** grid recipes.

### 5.3 Loot tables
`data/jaymospowers/loot_table/blocks/jewellers_bench.json` and `lapidary_wheel.json` drop themselves (copy
`drafting_table.json`).

### 5.4 Wandering trader
- `data/jaymospowers/villager_trade/tinkering/wandering_trader/blueprint_{ring,amulet,bracelet,circlet}_frame.json`,
  copying `blueprint_boots.json`:
  - shape `jewel/<kind>_frame`;
  - item name `{"translate": "item.jaymospowers.blueprint.named", "with": [{"translate": "item.jaymospowers.<kind>_frame"}]}`;
  - wants 12 `minecraft:emerald`.
- Add the four ids to `data/minecraft/tags/villager_trade/wandering_trader/uncommon.json`.

### 5.5 Art: `tools/gen_jewellery.py` (new, our own pixel art, PIL, in the style of `tools/gen_baubles.py`)
Run with `python -I tools/gen_jewellery.py [preview.png]`. It writes:
- **Textures** `textures/item/jewellery/`:
  - `{ring,amulet,bracelet,circlet}_frame_{gold,silver,copper}.png` (12): 16×16, the empty setting visible, three
    tones plus an outline. Palettes: gold as `gen_baubles.GOLD`, copper as `COPPER`, silver
    `((246,248,252),(196,204,214),(122,130,146))`.
  - `{ring,amulet,bracelet,circlet}_gem_{cabochon,table,rose,brilliant}.png` (16): **greyscale** gem overlays that sit
    exactly on each frame's setting (tones 255/200/130 plus a 255 highlight pixel), transparent elsewhere.
    Cabochon is a smooth dome, table a flat-topped square, rose a faceted dome, brilliant a pointed many-facet stone.
  - `cut_gem_{cabochon,table,rose,brilliant}.png` (4): a loose greyscale stone filling about 10×10.
- **Block textures** `textures/block/jewellery/`: `lapidary_wheel_stone.png` (grey grit disc),
  `lapidary_wheel_frame.png` (oak frame, iron axle), `jewellers_bench_top.png` (leather mat, tiny tools),
  `jewellers_bench_side.png`, `jewellers_bench_front.png` (a drawer with a brass knob), `jewellers_bench_bottom.png`.
- **Models**:
  - `models/item/jewellery/<kind>_frame_<metal>.json` (12): `item/generated`, layer0 = frame.
  - `models/item/jewellery/<kind>_<metal>_<cut>.json` (48): `item/generated`, layer0 = frame, layer1 = gem overlay.
  - `models/item/jewellery/cut_gem_<cut>.json` (4).
  - `models/block/lapidary_wheel.json` (elements as the 3.9 shape, the disc using `lapidary_wheel_stone`).
  - `models/block/jewellers_bench.json` (`parent: minecraft:block/orientable_with_bottom`; top, front, side, bottom).
- **Item definitions** `items/`:
  - `<kind>_frame.json`: `select` on `minecraft:custom_model_data` index 0 (gold / silver / copper; fallback silver).
  - `gem_<kind>.json`: `select` index 0 (metal) → `select` index 1 (cut) → `model` with
    `tints: [{"type": "minecraft:constant", "value": -1}, {"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]`.
    The fallbacks are silver and table.
  - `cut_gem.json`: `select` index 0 (cut) → model with `tints: [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]`.
  - `jewellers_bench.json` and `lapidary_wheel.json` point at the block models.
- **Blockstates**: `lapidary_wheel.json` and `jewellers_bench.json`, `facing` north/east/south/west with y 0/90/180/270.

(26.3 checked: the item model types `select`, `model` and `composite` exist, as do `ParticleTypes.CRIT/ELECTRIC_SPARK/SMOKE/ITEM/TOTEM_OF_UNDYING` and `SoundEvents.GRINDSTONE_USE/AMETHYST_BLOCK_CHIME/AMETHYST_CLUSTER_PLACE/STONE_PLACE/SMITHING_TABLE_USE/PLAYER_LEVELUP`; `ITEM_BREAK` is a `Holder`, so it needs `.value()`. The tint source `custom_model_data`
takes `index` and `default`, and `CustomModelData(floats, flags, strings, colors)`. Gems are drawn as layer1 of one
`item/generated` model rather than as a `composite`, to avoid two coplanar models z-fighting when held.)

### 5.6 Lang: `lang/parts/jewellery.json` (new)
```json
{
  "block.jaymospowers.jewellers_bench": "Jeweller's Bench",
  "block.jaymospowers.lapidary_wheel": "Lapidary Wheel",
  "tooltip.jaymospowers.tinkering.jewellers_bench": "Sets a cut gem in a forged ring, amulet, bracelet or circlet frame.",
  "tooltip.jaymospowers.tinkering.lapidary_wheel": "Lay a gem on it, choose a cut, then cut in time with the bar. Rush a stroke and the gem chips.",
  "container.jaymospowers.jewellers_bench": "Jeweller's Bench",
  "item.jaymospowers.ring_frame": "Ring Frame",
  "item.jaymospowers.amulet_frame": "Amulet Frame",
  "item.jaymospowers.bracelet_frame": "Bracelet Frame",
  "item.jaymospowers.circlet_frame": "Circlet Frame",
  "item.jaymospowers.ring_frame.named": "%s Ring Frame",
  "item.jaymospowers.amulet_frame.named": "%s Amulet Frame",
  "item.jaymospowers.bracelet_frame.named": "%s Bracelet Frame",
  "item.jaymospowers.circlet_frame.named": "%s Circlet Frame",
  "item.jaymospowers.gem_ring": "Gem Ring",
  "item.jaymospowers.gem_amulet": "Gem Amulet",
  "item.jaymospowers.gem_bracelet": "Gem Bracelet",
  "item.jaymospowers.gem_circlet": "Gem Circlet",
  "item.jaymospowers.gem_ring.named": "%1$s %2$s Ring",
  "item.jaymospowers.gem_amulet.named": "%1$s %2$s Amulet",
  "item.jaymospowers.gem_bracelet.named": "%1$s %2$s Bracelet",
  "item.jaymospowers.gem_circlet.named": "%1$s %2$s Circlet",
  "item.jaymospowers.cut_gem": "Cut Gem",
  "item.jaymospowers.cut_gem.named": "%1$s %2$s",
  "jewel_metal.jaymospowers.gold": "Gold",
  "jewel_metal.jaymospowers.silver": "Silver",
  "jewel_metal.jaymospowers.copper": "Copper",
  "gem.jaymospowers.emerald": "Emerald",
  "gem.jaymospowers.diamond": "Diamond",
  "gem.jaymospowers.amethyst": "Amethyst",
  "gem.jaymospowers.lapis": "Lapis",
  "gem.jaymospowers.quartz": "Quartz",
  "gem.jaymospowers.zanite": "Zanite",
  "gem.jaymospowers.glint": "Glint",
  "gem.jaymospowers.corrobonite": "Corrobonite",
  "gem.jaymospowers.golden_amber": "Amber",
  "cut.jaymospowers.cabochon": "Cabochon",
  "cut.jaymospowers.table": "Table-Cut",
  "cut.jaymospowers.rose": "Rose-Cut",
  "cut.jaymospowers.brilliant": "Brilliant-Cut",
  "grade.jaymospowers.cut.poor": "Poorly Cut",
  "grade.jaymospowers.cut.well": "Well Cut",
  "grade.jaymospowers.cut.expert": "Expertly Cut",
  "grade.jaymospowers.cut.perfect": "Perfectly Cut",
  "grade.jaymospowers.cut.master": "Masterwork",
  "tooltip.jaymospowers.jewellery.chipped": "Chipped (%s)",
  "tooltip.jaymospowers.jewellery.gives": "Set in jewellery: %s",
  "tooltip.jaymospowers.jewellery.gem_hint": "Set it in a frame at a Jeweller's Bench",
  "tooltip.jaymospowers.jewellery.frame_hint": "Set a cut gem in it at a Jeweller's Bench",
  "tooltip.jaymospowers.jewellery.set_with": "Set with: %s (%s)",
  "screen.jaymospowers.jewellery.cuts": "Choose a Cut",
  "screen.jaymospowers.jewellery.strokes": "%s strokes",
  "screen.jaymospowers.jewellery.bonus": "bonus x%s",
  "message.jaymospowers.jewellery.not_a_gem": "That can't be cut on a lapidary wheel.",
  "message.jaymospowers.jewellery.wheel_empty": "Lay a gem on the wheel first.",
  "message.jaymospowers.jewellery.busy": "Someone else is cutting on this wheel.",
  "message.jaymospowers.jewellery.chipped": "Too fast: the gem chipped!",
  "message.jaymospowers.jewellery.cut_done": "%s: %s"
}
```
The hot-frame refusal reuses `message.jaymospowers.smithing.quench_first` through `Grades.refusal`.
`message.jaymospowers.jewellery.chipped` is sent as an overlay on each chip.

---

## 6. Kingdoms (0.7.496): smith jewellery orders

Kingdoms never compiles against Powers. Items are looked up by id string and the grade through the
`jaymospowers:forging_grade` component id, as `GradedTrades` already does.

### 6.1 `GradedTrades.java`
- `static int gradeIndex(ItemStack stack)`: the index in `GRADES` of the stack's `forging_grade` (through
  `component()`), or -1 for ungraded or when Powers is missing.
- `static int gradeIndex(String id)`: the index of `id` in `GRADES`, or -1.
- `static String gradeId(int index)`: `GRADES[index]`.

### 6.2 `Quests.java`
- New `static String itemId(String target)`: the target up to `'@'`.
- New `static int minGrade(String target)`: `GradedTrades.gradeIndex(after '@')`, or -1 when there's no `'@'`.
- `item(String id)`: parse `itemId(id)` (rewards are unaffected).
- `count(player, item)` delegates to new `count(player, item, int minGrade)`. A stack counts only if
  `minGrade < 0 || GradedTrades.gradeIndex(s) >= minGrade`.
- `take(player, item, n)` becomes `take(player, item, n, minGrade)` with the same test.
- `ready` (GATHER): `count(player, item(t)) >= n` becomes `count(player, item(t), minGrade(t)) >= n` (t = `q.objective().target()`).
- `turnIn` (GATHER): `take(..., minGrade(target))`.
- `objective` (GATHER): count with `minGrade`.
- `itemName(String id)`: when `minGrade(id) >= 0`, it gives
  `Component.translatable("quest.jaymoskingdoms.graded", base, Component.translatableWithFallback("grade.jaymospowers." + gradeId(g), gradeId(g)))`.
  (`translatableWithFallback(String, String)` is confirmed in 26.3 `Component`.)
- `count(player, Items.BRUSH)` in `accept` is unchanged (the overload).

### 6.3 `QuestBoard.java`
- `make(...)`: the GATHER existence check becomes
  `BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(Quests.itemId(t.target()))).isEmpty()`. This fixes the
  null that `tryParse` returns on `'@'`, and means no jewellery order appears without Powers. Pay:
  `int floor = Quests.minGrade(t.target()); int emeralds = floor >= 0 ? JEWEL_ORDER_EMERALDS[floor] + random.nextInt(JEWEL_ORDER_SPREAD + 1) : switch (...)` (the existing switch).
- Constants: `JEWEL_ORDER_EMERALDS = {6, 10, 16, 24, 40}`, `JEWEL_ORDER_SPREAD = 4`.
- `TRADES`:
  - armorer += `T.gather("armorer.amulet", "jaymospowers:gem_amulet@well", 1, 1)`
  - weaponsmith += `T.gather("weaponsmith.ring", "jaymospowers:gem_ring@expert", 1, 2)`
  - toolsmith += `T.gather("toolsmith.bracelet", "jaymospowers:gem_bracelet@well", 1, 1)`

### 6.4 Kingdoms lang (`kingdoms/src/main/resources/assets/jaymoskingdoms/lang/en_us.json`, beside the smith keys at ~1181)
```json
"quest.jaymoskingdoms.armorer.amulet.title": "An Amulet for the Captain",
"quest.jaymoskingdoms.armorer.amulet.text": "The captain wants an amulet for his lady, and I've no hand for fine work. Bring me %1$s %2$s.",
"quest.jaymoskingdoms.weaponsmith.ring.title": "A Ring to Swear On",
"quest.jaymoskingdoms.weaponsmith.ring.text": "A knight wants a ring to swear his oath on. I make swords, not rings. Bring me %1$s %2$s.",
"quest.jaymoskingdoms.toolsmith.bracelet.title": "A Bracelet for the Bride",
"quest.jaymoskingdoms.toolsmith.bracelet.text": "The miller's daughter is to be wed and he's asked me for a bracelet. I make picks. Bring me %1$s %2$s.",
"quest.jaymoskingdoms.graded": "%1$s (%2$s or better)"
```

### 6.5 Kingdoms version and log
`kingdoms/gradle.properties` `version=0.7.496`, and the `kingdoms/MODLOG.md` entry in §8.

---

## 7. Must not break (checklist for the editors and verifier)
- The seven found baubles and the lantern pole: same items, tags, recipes, bonuses, tooltips; the `SHOWN` sync
  (`BELT_SLOT 3`, `BODY_SLOT 5`) is unchanged. The new slot is **appended** (index 7).
- `Baubles.refresh` removes slot 7 modifiers when the slot empties (the loop covers `SIZE`). Every gem attribute is
  in `TOUCHED` (through `Baubles.touch`).
- Pieces are not damageable, so `Wear`, `GradedLoot.gradable` and `GradedTrades.gear` ignore them. Frames are not
  rough (`Finishing.roughKind` is false), and the Tool Station never sees them.
- The option ids `jewel/<kind>_frame/<metal>` and the gem/cut ids are permanent. `forging_grade` values are unchanged.
- No new `AttachmentType`. The new packets need the version bump (done).
- Kingdoms: the GATHER targets of DisasterRelief, TownNotices and the existing trades have no `'@'` and behave as
  before. The `"@"` parse is only in `itemId`/`minGrade`.
- Build both: `./gradlew build` in `/home/user/jewel` and in `/home/user/jewel/kingdoms`. No client or server runs.

### Suggested agent tracks (two at once, separate worktrees, no shared files)
- **Track A, Powers**: §3, §4, §5 (Java, client, data, `tools/gen_jewellery.py`, then run it), `MODLOG.md`,
  `docs/Smithing.md`, Powers version.
- **Track B, Kingdoms**: §6 (`GradedTrades`, `Quests`, `QuestBoard`, lang, `kingdoms/MODLOG.md`, Kingdoms version).
  It only depends on the string ids `jaymospowers:gem_amulet/gem_ring/gem_bracelet`, so it can run alongside A.

---

## 8. MODLOG text

**`MODLOG.md`** (end of the log):
> - **0.19.87, Kingdoms 0.7.496, jewellery (Patch 4)** (`jewellery/`, plan `docs/Jewellery-Plan.md`): Andy picked the
>   idea of the Immersive Jewelry mod; this is our own version (none of its code or art).
>   - A **Lapidary Wheel** cuts gems: lay one on it and choose one of four cuts: Cabochon (3 strokes), Table (4), Rose
>     (5) or Brilliant (6). Then cut in time with the smithing bar. The wheel needs 0.8 s to spin back up after each
>     stroke; a stroke before that **chips** the gem and costs it a grade. Cut gems are graded Poorly Cut to
>     Masterwork and keep their gem, cut, grade and chips in one component (`jaymospowers:cut_gem`), ready to be
>     socketed into weapons and armour next patch.
>   - Emerald, diamond, amethyst, lapis, quartz and the Aether's zanite, glint, corrobonite and golden amber can be
>     cut; each gives its own small bonus (Luck, armour toughness, health, swimming, less burning, mining speed,
>     speed, knockback resistance, breath).
>   - Ring, amulet, bracelet and circlet **frames** are hammered from gold, silver or copper on the Smithing Anvil
>     like any head: graded Poorly Forged to Masterwork, quenched, capped without a blueprint (four new frame
>     blueprints at the wandering trader).
>   - The **Jeweller's Bench** sets a cut gem in a cooled frame. The piece is as good as the worse of the two, and its
>     bonus grows with grade, cut, metal (copper < silver < gold) and frame (amulets most).
>   - Rings, amulets and circlets go in their bauble slots; bracelets in a new eighth bauble slot, the **Wrist**
>     (bottom right). The seven found baubles are unchanged.
>   - Town smiths (Kingdoms) now and then ask for a gem amulet, ring or bracelet of at least a given grade, for more
>     emeralds than their usual errands. Compile-checked; not played.

**`kingdoms/MODLOG.md`**:
> ## 0.7.496: jewellery orders from town smiths (pack 0.19.87)
> - Armourers ask for a gem amulet (Well Forged or better), weaponsmiths for one or two gem rings (Expertly Forged or
>   better), toolsmiths for a gem bracelet (Well Forged or better), made with Powers' lapidary wheel, anvil and
>   jeweller's bench. They pay 10 to 20 emeralds (`QuestBoard.JEWEL_ORDER_EMERALDS`), to become coin with the coin
>   change.
> - A gather quest's target may now carry a grade floor (`<item id>@<grade>`): only pieces graded at least that
>   count and are taken (`Quests.minGrade`, `GradedTrades.gradeIndex`), and the quest names the grade. Without
>   Powers no such quest is offered. Compile-checked; not played.

---

## 9. Open questions for Andy (the defaults above are built unless he says otherwise)
1. "Four cuts": four **cut styles** (built), or every gem takes **four strokes**?
2. Chipping only lowers the grade (built). Should a gem chipped three or more times shatter instead?
3. Bracelet: a new **Wrist** slot (built), or share the Charm slot?
4. Should the bench also take a gem back out of a piece (it would come back chipped)? Not built.
5. Kingdoms' Stoneborn `jaymoskingdoms:cut_gem` (a Haven trade good) stays as it is. Should it be settable as well?
6. Gold armour is still forged by `ArmourForging` even though gold armour is meant to be off pack-wide. Leave it as
   it is or remove it? (Not part of this patch.)
