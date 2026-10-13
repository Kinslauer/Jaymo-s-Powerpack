package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.power.wind.SorasDagger;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.ToolPartItem;
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

	/** What an item melts into. */
	public record Result(Molten molten, int amount) {
		public int ticks() {
			return MELT_BASE_TICKS + amount / MELT_MB_PER_TICK;
		}
	}

	/** An alloy: these liquids in these ratios make that much of the output. */
	public record Alloy(Molten output, int amount, List<Input> inputs) {
	}

	public record Input(Molten molten, int amount) {
	}

	/** Tinkers' alloys, in per-batch amounts (one batch every tick or so). */
	public static final List<Alloy> ALLOYS = List.of(
		new Alloy(Molten.ROSE_GOLD, 40, List.of(new Input(Molten.COPPER, 30), new Input(Molten.GOLD, 10))),
		new Alloy(Molten.AMETHYST_BRONZE, 9, List.of(new Input(Molten.COPPER, 9), new Input(Molten.AMETHYST, 10))),
		new Alloy(Molten.PIG_IRON, 9, List.of(new Input(Molten.IRON, 9), new Input(Molten.BLOOD, 5), new Input(Molten.HONEY, 5))),
		new Alloy(Molten.SLIMESTEEL, 9, List.of(new Input(Molten.IRON, 9), new Input(Molten.SLIME, 25))));

	private static final Map<Item, Result> ITEMS = new IdentityHashMap<>();

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
		metal(Molten.COPPER, Items.COPPER_INGOT, Items.COPPER_NUGGET, null, Items.RAW_COPPER, Items.RAW_COPPER_BLOCK);
		for (Item block : Items.COPPER_BLOCK.asList()) {
			add(Molten.COPPER, Molten.BLOCK, block);
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

	/** What one of this item melts into, or null. */
	public static @Nullable Result of(ItemStack stack) {
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
		if (stack.getItem() instanceof ToolPartItem part) {
			Molten molten = Molten.of(ToolPartItem.material(stack));
			if (molten != null) {
				return new Result(molten, part.part.cost * Molten.INGOT);
			}
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
				default -> {
					PartType part = castItem.shape.part;
					yield part != null && molten.material != null && molten.material.makes(part.kind)
						? ToolPartItem.of(part, molten.material, 1) : null;
				}
			};
		}
		// gold over an ingot, nugget or part makes a cast of its shape
		CastItem.Shape shape = CastItem.Shape.of(cast);
		return molten == Molten.GOLD && shape != null ? new ItemStack(Tinkering.CASTS.get(shape)) : null;
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
			default -> castItem.shape.part.cost * Molten.INGOT;
		};
	}

	/** (0.8.115) An iron Sora's Dagger, which a block's worth of molten gold gilds. */
	public static boolean gilds(ItemStack stack) {
		return stack.getItem() instanceof SorasDagger dagger && dagger.stage == SorasDagger.IRON;
	}

	/** Whether pouring onto this item uses it up (making a cast does; casting on a cast doesn't). */
	public static boolean usesUpCast(ItemStack cast) {
		return !(cast.getItem() instanceof CastItem);
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
