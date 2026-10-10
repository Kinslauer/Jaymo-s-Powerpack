package com.jaymoskingdoms;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.jspecify.annotations.Nullable;

/**
 * What a block costs a builder crew, in the <b>raw</b> materials a faction's storage really holds (its production makes
 * logs and cobblestone): the foreman works them into planks, bricks and fittings on site. Costs are in eighths of a raw
 * item, so a slab of planks costs an eighth of a log. Wood (logs whole, anything else wooden a quarter of a log, wooden
 * slabs an eighth) is paid in the faction's own logs ({@link Production#logItem}); stonework in cobblestone (stone will
 * do as well); fittings (glass, torches, wool, beds, lanterns...) a quarter of a log; the land's own soil, paths, water
 * and plants are dug or gathered on site and cost nothing. A faction type may set its own rule with {@link #override}.
 */
public final class Materials {
	/** Eighths of a raw item in one. */
	public static final int WHOLE = 8;
	/** A log, a wooden block (planks, stairs, fences, doors), a wooden slab, a stone block, a stone slab, a fitting. */
	static final int LOG_COST = 8, WOOD_COST = 2, WOOD_SLAB_COST = 1, STONE_COST = 8, STONE_SLAB_COST = 4, FITTING_COST = 2;

	/** One block's price: {@code eighths} eighths of {@code item}. */
	public record Cost(Item item, int eighths) {
	}

	/** A faction type's own pricing (null for a free block). */
	@FunctionalInterface
	public interface Pricing {
		@Nullable Cost cost(ServerLevel level, Faction faction, BlockState state);
	}

	private static final Map<FactionType, Pricing> OVERRIDES = new EnumMap<>(FactionType.class);

	private Materials() {
	}

	/** Sets how a faction type's buildings are priced (for example a camp paying for its wool in loot). */
	public static void override(FactionType type, Pricing pricing) {
		OVERRIDES.put(type, pricing);
	}

	/** What placing {@code state} costs {@code faction} (null: nothing). The second half of a door or a bed is free. */
	public static @Nullable Cost cost(ServerLevel level, Faction faction, BlockState state) {
		if (state.isAir()) {
			return null;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return null;
		}
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
			return null;
		}
		Pricing pricing = OVERRIDES.get(faction.type());
		if (pricing != null) {
			return pricing.cost(level, faction, state);
		}
		if (free(state)) {
			return null;
		}
		boolean slab = state.getBlock() instanceof SlabBlock;
		if (state.is(BlockTags.LOGS)) {
			return new Cost(Production.logItem(level, faction), LOG_COST);
		}
		NoteBlockInstrument instrument = state.instrument();
		if (instrument == NoteBlockInstrument.BASS) {
			return new Cost(Production.logItem(level, faction), slab ? WOOD_SLAB_COST : WOOD_COST);
		}
		if (instrument == NoteBlockInstrument.BASEDRUM) {
			return new Cost(Items.COBBLESTONE, slab ? STONE_SLAB_COST : STONE_COST);
		}
		return new Cost(Production.logItem(level, faction), FITTING_COST);
	}

	/** Soil, paths, water and plants: dug or gathered on site. */
	static boolean free(BlockState state) {
		return !state.getFluidState().isEmpty() || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.FARMLAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW) || state.is(Blocks.COARSE_DIRT)
			|| state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.canBeReplaced();
	}

	/** What may be taken from storage to pay in {@code raw} (cobblestone may be paid in stone). */
	public static Predicate<ItemStack> accepts(Item raw) {
		if (raw == Items.COBBLESTONE) {
			return stack -> stack.is(Items.COBBLESTONE) || stack.is(Items.STONE);
		}
		return stack -> stack.is(raw);
	}

	/** Whole raw items for a list of blocks (each kind rounded up). */
	public static Map<Item, Integer> total(ServerLevel level, Faction faction, List<BlockState> states) {
		Map<Item, Integer> eighths = new LinkedHashMap<>();
		for (BlockState state : states) {
			Cost cost = cost(level, faction, state);
			if (cost != null) {
				eighths.merge(cost.item(), cost.eighths(), Integer::sum);
			}
		}
		return whole(eighths);
	}

	/** Eighths → whole items, rounded up; empty entries dropped. */
	static Map<Item, Integer> whole(Map<Item, Integer> eighths) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		eighths.forEach((item, n) -> {
			if (n > 0) {
				out.put(item, (n + WHOLE - 1) / WHOLE);
			}
		});
		return out;
	}

	static String key(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	static Item item(String key) {
		return BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(key));
	}
}
