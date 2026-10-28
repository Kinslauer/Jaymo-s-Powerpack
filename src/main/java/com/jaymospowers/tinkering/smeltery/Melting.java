package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.power.wind.SorasDagger;
import com.jaymospowers.power.wind.SorasLineage;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.ToolStats;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.WeaponParts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What melts into what, which liquids alloy, and what the casting table and basin make. Ores and raw ore melt into two
 * ingots' worth (Tinkers' ore doubling); metal gear melts back into the ingots it took.
 */
public final class Melting {
	/** (0.8.115) Molten gold to gild Sora's Dagger: a block's worth, as the gold block it took before. */
	static final int DAGGER_GILDING = Molten.BLOCK;

	/** Ticks to melt: a base plus one per this many mB (an ingot about 2.5 s, a block about 15 s). */
	static final int MELT_BASE_TICKS = 20, MELT_MB_PER_TICK = 3;
	/** Ores and raw ore give this many ingots' worth. */
	static final int ORE_INGOTS = 2;
	/** (0.13) Ingots in uncraftable metal: horse and nautilus armour, a bell, chainmail (helmet, chest, legs, boots). */
	static final int HORSE_ARMOUR_INGOTS = 9, NAUTILUS_ARMOUR_INGOTS = 7, BELL_INGOTS = 4;
	static final int[] CHAINMAIL_INGOTS = {3, 5, 4, 2};
	/** (0.13) A chipped and a damaged anvil (a new one is 31 ingots). */
	static final int[] WORN_ANVIL_INGOTS = {20, 10};

	/**
	 * What an item melts into: one or more liquids, the largest share first (a paladin's helmet is iron and gold).
	 * {@link #molten()} and {@link #amount()} are that largest share.
	 */
	public record Result(List<Input> parts) {
		public Result(Molten molten, int amount) {
			this(List.of(new Input(molten, amount)));
		}

		public Molten molten() {
			return parts.getFirst().molten();
		}

		public int amount() {
			return parts.getFirst().amount();
		}

		/** Every liquid it gives, together (mB). */
		public int total() {
			int total = 0;
			for (Input part : parts) {
				total += part.amount();
			}
			return total;
		}

		public int ticks() {
			return MELT_BASE_TICKS + total() / MELT_MB_PER_TICK;
		}

		/** Several liquids, largest first; null if there's nothing. */
		public static @Nullable Result of(Map<Molten, Integer> liquids) {
			List<Input> parts = new ArrayList<>();
			liquids.forEach((molten, amount) -> {
				if (amount > 0) {
					parts.add(new Input(molten, amount));
				}
			});
			if (parts.isEmpty()) {
				return null;
			}
			parts.sort(Comparator.comparingInt(Input::amount).reversed().thenComparing(Input::molten));
			return new Result(List.copyOf(parts));
		}
	}

	/** An alloy: these liquids in these ratios make that much of the output. */
	public record Alloy(Molten output, int amount, List<Input> inputs) {
	}

	public record Input(Molten molten, int amount) {
	}

	/**
	 * Tinkers' alloys, in per-batch amounts (one batch every tick or so). (Smithing) Steel is wrought iron fined with
	 * carbon-rich pig iron, an ingot of each for one of steel (the smeltery holds no coal); a kiln can make it from
	 * coal directly ({@code kiln.Kiln}).
	 */
	public static final List<Alloy> ALLOYS = List.of(
		new Alloy(Molten.ROSE_GOLD, 40, List.of(new Input(Molten.COPPER, 30), new Input(Molten.GOLD, 10))),
		new Alloy(Molten.AMETHYST_BRONZE, 9, List.of(new Input(Molten.COPPER, 9), new Input(Molten.AMETHYST, 10))),
		new Alloy(Molten.PIG_IRON, 9, List.of(new Input(Molten.IRON, 9), new Input(Molten.BLOOD, 5), new Input(Molten.HONEY, 5))),
		new Alloy(Molten.SLIMESTEEL, 9, List.of(new Input(Molten.IRON, 9), new Input(Molten.SLIME, 25))),
		new Alloy(Molten.STEEL, 9, List.of(new Input(Molten.IRON, 9), new Input(Molten.PIG_IRON, 9))));

	private static final Map<Item, Result> ITEMS = new IdentityHashMap<>();
	/**
	 * (0.13) Melting worked out once the game is up ({@link MeltingRecipes}): data files, forged weapons, and anything
	 * made from metal by a recipe. The server sends it to players, so both sides agree.
	 */
	private static volatile Map<Item, Result> extra = Map.of();

	static {
		int ingot = Molten.INGOT;
		metal(Molten.IRON, Items.IRON_INGOT, Items.IRON_NUGGET, Items.IRON_BLOCK, Items.RAW_IRON, Items.RAW_IRON_BLOCK);
		gear(Molten.IRON, Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.IRON_SPEAR,
			Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
		add(Molten.IRON, ingot * 3, Items.BUCKET);
		add(Molten.IRON, ingot * 2, Items.SHEARS);
		metal(Molten.GOLD, Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.GOLD_BLOCK, Items.RAW_GOLD, Items.RAW_GOLD_BLOCK);
		gear(Molten.GOLD, Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE,
			Items.GOLDEN_SPEAR, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
		add(Molten.GOLD, Molten.NUGGET * 6, Items.NETHER_GOLD_ORE);
		// (0.13) metal nobody can craft: mount armour, chainmail, the village bell
		add(Molten.IRON, ingot * HORSE_ARMOUR_INGOTS, Items.IRON_HORSE_ARMOR);
		add(Molten.GOLD, ingot * HORSE_ARMOUR_INGOTS, Items.GOLDEN_HORSE_ARMOR);
		add(Molten.COPPER, ingot * HORSE_ARMOUR_INGOTS, Items.COPPER_HORSE_ARMOR);
		add(Molten.IRON, ingot * NAUTILUS_ARMOUR_INGOTS, Items.IRON_NAUTILUS_ARMOR);
		add(Molten.GOLD, ingot * NAUTILUS_ARMOUR_INGOTS, Items.GOLDEN_NAUTILUS_ARMOR);
		add(Molten.COPPER, ingot * NAUTILUS_ARMOUR_INGOTS, Items.COPPER_NAUTILUS_ARMOR);
		Item[] chainmail = {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
		for (int i = 0; i < chainmail.length; i++) {
			add(Molten.IRON, ingot * CHAINMAIL_INGOTS[i], chainmail[i]);
		}
		add(Molten.GOLD, ingot * BELL_INGOTS, Items.BELL);
		// a worn anvil gives back less than a new one (which melts by its recipe)
		add(Molten.IRON, ingot * WORN_ANVIL_INGOTS[0], Items.CHIPPED_ANVIL);
		add(Molten.IRON, ingot * WORN_ANVIL_INGOTS[1], Items.DAMAGED_ANVIL);
		metal(Molten.COPPER, Items.COPPER_INGOT, Items.COPPER_NUGGET, null, Items.RAW_COPPER, Items.RAW_COPPER_BLOCK);
		for (Item block : Items.COPPER_BLOCK.asList()) {
			add(Molten.COPPER, Molten.BLOCK, block);
		}
		// a copper golem's statue is the block it was built from
		for (Item statue : Items.COPPER_GOLEM_STATUE.asList()) {
			add(Molten.COPPER, Molten.BLOCK, statue);
		}
		gear(Molten.COPPER, Items.COPPER_SWORD, Items.COPPER_PICKAXE, Items.COPPER_AXE, Items.COPPER_SHOVEL, Items.COPPER_HOE,
			Items.COPPER_SPEAR, Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS);
		add(Molten.AMETHYST, 100, Items.AMETHYST_SHARD);
		add(Molten.AMETHYST, 400, Items.AMETHYST_BLOCK);
		add(Molten.SLIME, 250, Items.SLIME_BALL);
		add(Molten.SLIME, 2250, Items.SLIME_BLOCK);
		add(Molten.HONEY, 250, Items.HONEY_BOTTLE);
		add(Molten.HONEY, 1000, Items.HONEY_BLOCK);
		add(Molten.BLOOD, 50, Items.ROTTEN_FLESH, Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT);
	}

	private Melting() {
	}

	/** (0.8.111) Silver, our own ore: its ingot, nugget, block, raw ore (doubled) and ore blocks. */
	public static void silver(Item ingot, Item nugget, Item block, Item raw, Item rawBlock, Item... ores) {
		metal(Molten.SILVER, ingot, nugget, block, raw, rawBlock);
		add(Molten.SILVER, Molten.INGOT * ORE_INGOTS, ores);
	}

	/** (Smithing) An alloy's nugget and block (its ingot melts as one of {@link Tinkering#ALLOY_INGOTS}). */
	public static void alloyForms(Molten molten, Item nugget, Item block) {
		add(molten, Molten.NUGGET, nugget);
		add(molten, Molten.BLOCK, block);
	}

	/** An ingot, nugget, block and raw ore (doubled) of a metal. */
	private static void metal(Molten molten, Item ingot, Item nugget, @Nullable Item block, Item raw, Item rawBlock) {
		add(molten, Molten.INGOT, ingot);
		add(molten, Molten.NUGGET, nugget);
		if (block != null) {
			add(molten, Molten.BLOCK, block);
		}
		add(molten, Molten.INGOT * ORE_INGOTS, raw);
		add(molten, Molten.BLOCK * ORE_INGOTS, rawBlock);
	}

	/** Vanilla gear, in the order sword, pickaxe, axe, shovel, hoe, spear, helmet, chestplate, leggings, boots. */
	private static void gear(Molten molten, Item... items) {
		int[] ingots = {2, 3, 3, 1, 2, 1, 5, 8, 7, 4};
		for (int i = 0; i < items.length; i++) {
			add(molten, Molten.INGOT * ingots[i], items[i]);
		}
	}

	private static void add(Molten molten, int amount, Item... items) {
		for (Item item : items) {
			ITEMS.put(item, new Result(molten, amount));
		}
	}

	/** What one of this item melts into, or null. (0.15.36) Worn gear melts into what's left of it. */
	public static @Nullable Result of(ItemStack stack) {
		if (SorasLineage.marked(stack)) {
			// (0.17.110) the one Sora's Dagger line (found blade, its cast, the dagger) never melts
			return null;
		}
		Result result = builtIn(stack);
		if (result == null) {
			result = extra.get(stack.getItem());
		}
		return result == null ? null : worn(stack, result);
	}

	/** (0.15.36) The share of a damaged item's metal that's left (at least a drop of its main liquid). */
	private static Result worn(ItemStack stack, Result result) {
		if (!stack.isDamageableItem() || !stack.isDamaged()) {
			return result;
		}
		int max = stack.getMaxDamage();
		int left = max - stack.getDamageValue();
		Map<Molten, Integer> liquids = new EnumMap<>(Molten.class);
		for (Input part : result.parts()) {
			liquids.merge(part.molten(), (int) ((long) part.amount() * left / max), Integer::sum);
		}
		Result scaled = Result.of(liquids);
		return scaled != null ? scaled : new Result(result.molten(), 1);
	}

	/** The extras ({@link MeltingRecipes}), for sending to players. */
	public static Map<Item, Result> extra() {
		return extra;
	}

	/** Swaps in a new set of extras (worked out on the server, or received from it). */
	public static void setExtra(Map<Item, Result> map) {
		extra = Map.copyOf(map);
	}

	/** What one of this item melts into by the built-in table and rules alone (without the extras), or null. */
	static @Nullable Result builtIn(ItemStack stack) {
		Result listed = ITEMS.get(stack.getItem());
		if (listed != null) {
			return listed;
		}
		int ore = Molten.INGOT * ORE_INGOTS;
		if (stack.is(ItemTags.IRON_ORES)) {
			return new Result(Molten.IRON, ore);
		}
		if (stack.is(ItemTags.GOLD_ORES)) {
			return new Result(Molten.GOLD, ore);
		}
		if (stack.is(ItemTags.COPPER_ORES)) {
			// copper ore drops several raw copper, so it melts into more
			return new Result(Molten.COPPER, ore * 2);
		}
		Molten alloy = ingotOf(stack.getItem());
		if (alloy != null) {
			return new Result(alloy, Molten.INGOT);
		}
		if (stack.getItem() instanceof WeaponPartItem part) {
			Molten molten = WeaponPartItem.metal(stack).molten;
			return molten == null ? null : new Result(molten, part.kind.mb());
		}
		if (stack.getItem() instanceof ToolPartItem part) {
			Molten molten = Molten.of(ToolPartItem.material(stack));
			if (molten != null) {
				return new Result(molten, part.part.cost * Molten.INGOT);
			}
		}
		if (stack.getItem() instanceof TinkerTool tool) {
			// (0.15.36) a Tinkering tool: each metal part's worth
			Map<Molten, Integer> liquids = new EnumMap<>(Molten.class);
			List<TinkerMaterial> materials = ToolStats.materials(stack);
			for (int i = 0; i < tool.type().parts.size() && i < materials.size(); i++) {
				Molten molten = Molten.of(materials.get(i));
				if (molten != null) {
					liquids.merge(molten, tool.type().parts.get(i).part().cost * Molten.INGOT, Integer::sum);
				}
			}
			// one of a build of several (arrows, shuriken) holds only its share of the parts' metal
			int yield = Math.max(1, tool.type().ranged.yield());
			liquids.replaceAll((molten, amount) -> amount / yield);
			return Result.of(liquids);
		}
		if (stack.getItem() instanceof CastItem) {
			// (0.15.36) a gold cast took an ingot of gold
			return new Result(Molten.GOLD, Molten.INGOT);
		}
		return null;
	}

	/** The alloy whose ingot this is, or null. */
	private static @Nullable Molten ingotOf(Item item) {
		for (Map.Entry<Molten, Item> entry : Tinkering.ALLOY_INGOTS.entrySet()) {
			if (entry.getValue() == item) {
				return entry.getKey();
			}
		}
		return null;
	}

	// --- casting ---

	/** What the casting table makes from this liquid on this cast (or an item to make a cast from), or null. */
	public static @Nullable ItemStack tableResult(ItemStack cast, Molten molten) {
		if (gilds(cast)) {
			// (0.8.115) Andy: Sora's Dagger is gilded in the smeltery, not with a gold block
			return molten == Molten.GOLD ? cast.transmuteCopy(SorasDagger.STAGES[SorasDagger.GILDED], 1) : null;
		}
		if (cast.getItem() instanceof CastItem castItem) {
			return switch (castItem.shape) {
				case INGOT -> ingot(molten);
				case NUGGET -> nugget(molten);
				case WEAPON -> {
					WeaponParts.Kind kind = WeaponParts.ofCast(cast);
					ItemStack head = kind == null ? null : WeaponParts.castResult(kind, molten);
					yield head == null ? null : SorasLineage.carry(cast, head);
				}
				default -> {
					PartType part = castItem.shape.part;
					yield part != null && molten.material != null && molten.material.makes(part.kind)
						? ToolPartItem.of(part, molten.material, 1) : null;
				}
			};
		}
		// gold over an ingot, nugget, part or weapon head makes a cast of its shape
		CastItem.Shape shape = CastItem.Shape.of(cast);
		if (molten != Molten.GOLD || shape == null) {
			return null;
		}
		return shape == CastItem.Shape.WEAPON ? SorasLineage.carry(cast, WeaponParts.cast(((WeaponPartItem) cast.getItem()).kind))
			: new ItemStack(Tinkering.CASTS.get(shape));
	}

	/** How much liquid the table needs for this cast and liquid (0: it can't cast it). */
	public static int tableAmount(ItemStack cast, Molten molten) {
		ItemStack result = tableResult(cast, molten);
		if (result == null || result.isEmpty()) {
			return 0;
		}
		if (gilds(cast)) {
			return DAGGER_GILDING;
		}
		if (!(cast.getItem() instanceof CastItem castItem)) {
			return Molten.INGOT;
		}
		return switch (castItem.shape) {
			case INGOT -> Molten.INGOT;
			case NUGGET -> Molten.NUGGET;
			case WEAPON -> WeaponParts.ofCast(cast).mb();
			default -> castItem.shape.part.cost * Molten.INGOT;
		};
	}

	/** (0.8.115) An iron Sora's Dagger, which a block's worth of molten gold gilds. */
	public static boolean gilds(ItemStack stack) {
		return stack.getItem() instanceof SorasDagger dagger && dagger.stage == SorasDagger.IRON;
	}

	/** Whether pouring onto this item uses it up (making a cast does; casting on a cast doesn't). */
	public static boolean usesUpCast(ItemStack cast) {
		// (0.17.110) the cast poured over a found Sora's Dagger Blade makes one iron blade only
		return !(cast.getItem() instanceof CastItem) || SorasLineage.marked(cast);
	}

	private static @Nullable ItemStack ingot(Molten molten) {
		Item item = switch (molten) {
			case IRON -> Items.IRON_INGOT;
			case GOLD -> Items.GOLD_INGOT;
			case COPPER -> Items.COPPER_INGOT;
			default -> Tinkering.ALLOY_INGOTS.get(molten);
		};
		return item == null ? null : new ItemStack(item);
	}

	private static @Nullable ItemStack nugget(Molten molten) {
		return switch (molten) {
			case IRON -> new ItemStack(Items.IRON_NUGGET);
			case GOLD -> new ItemStack(Items.GOLD_NUGGET);
			case COPPER -> new ItemStack(Items.COPPER_NUGGET);
			case SILVER -> new ItemStack(Tinkering.SILVER_NUGGET);
			case STEEL -> new ItemStack(Tinkering.STEEL_NUGGET);
			default -> null;
		};
	}

	/** What the casting basin makes from a liquid, and how much it takes. */
	public record BasinResult(ItemStack result, int amount) {
	}

	public static @Nullable BasinResult basinResult(Molten molten) {
		return switch (molten) {
			case IRON -> new BasinResult(new ItemStack(Items.IRON_BLOCK), Molten.BLOCK);
			case GOLD -> new BasinResult(new ItemStack(Items.GOLD_BLOCK), Molten.BLOCK);
			case COPPER -> new BasinResult(new ItemStack(Items.COPPER_BLOCK.weathering().unaffected()), Molten.BLOCK);
			case SILVER -> new BasinResult(new ItemStack(Tinkering.SILVER_BLOCK), Molten.BLOCK);
			case STEEL -> new BasinResult(new ItemStack(Tinkering.STEEL_BLOCK), Molten.BLOCK);
			case AMETHYST -> new BasinResult(new ItemStack(Items.AMETHYST_BLOCK), 400);
			case SLIME -> new BasinResult(new ItemStack(Items.SLIME_BLOCK), 2250);
			case HONEY -> new BasinResult(new ItemStack(Items.HONEY_BLOCK), 1000);
			default -> null;
		};
	}

	/** Ticks a cast takes to cool once full. */
	public static int coolTicks(int amount) {
		return 20 + amount / 6;
	}
}
