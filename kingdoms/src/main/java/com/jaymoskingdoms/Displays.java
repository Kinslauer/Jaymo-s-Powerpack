package com.jaymoskingdoms;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.8) Cooking for Blockheads' fruit baskets and tool racks as displays, when that mod is in the pack (nothing of
 * it is compiled against: its blocks are looked up by name and filled through their {@code getContainer()}). Market
 * stalls set out produce in fruit baskets; smithies, barracks, masons and houses hang tools and weapons on racks.
 * When a building is finished ({@link Construction}) its displays are stocked: from the faction's storage where it
 * holds the goods, else a few made on the spot, so a display is never bare.
 */
final class Displays {
	static final String MOD = "cookingforblockheads";
	/** Produce a basket may show, and how many kinds and of each. */
	static final Item[] PRODUCE = {Items.APPLE, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.MELON_SLICE, Items.SWEET_BERRIES, Items.BREAD,
		Items.GLOW_BERRIES, Items.PUMPKIN_PIE, Items.COOKIE};
	static final int KINDS_MIN = 2, KINDS_MAX = 4, EACH_MIN = 2, EACH_MAX = 8;

	private Displays() {
	}

	private static @Nullable Block block(String name) {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(MOD, name));
		return b == Blocks.AIR ? null : b;
	}

	/** A fruit basket facing {@code facing}, or null without Cooking for Blockheads. */
	static @Nullable BlockState basket(Direction facing) {
		Block b = block("fruit_basket");
		return b == null ? null : face(b.defaultBlockState(), facing);
	}

	/** A tool rack on a wall, facing {@code facing} (out from the wall), or null without Cooking for Blockheads. */
	static @Nullable BlockState rack(Direction facing) {
		Block b = block("tool_rack");
		return b == null ? null : face(b.defaultBlockState(), facing);
	}

	/** Whether {@code state} is a fruit basket. */
	static boolean isBasket(BlockState state) {
		Block b = block("fruit_basket");
		return b != null && state.is(b);
	}

	private static BlockState face(BlockState state, Direction facing) {
		return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : state;
	}

	/** What a building's racks show. */
	private static Item[] tools(String entry) {
		return switch (entry) {
			case "smithy" -> new Item[] {Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.SHEARS};
			case "barracks" -> new Item[] {Items.IRON_SWORD, Items.STONE_SWORD, Items.BOW, Items.CROSSBOW, Items.IRON_AXE, Items.SHIELD};
			case "mason" -> new Item[] {Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.STONE_SHOVEL, Items.BRUSH};
			default -> new Item[] {Items.STONE_AXE, Items.IRON_HOE, Items.SHEARS, Items.FISHING_ROD, Items.STONE_SHOVEL, Items.BUCKET};
		};
	}

	/** A building just finished between {@code min} and {@code top}: its stools get cushions, its baskets and racks are stocked. */
	static void built(ServerLevel level, Faction f, String entry, BlockPos min, BlockPos top) {
		Cushions.seat(level, f, min, top);
		Block basket = block("fruit_basket"), rack = block("tool_rack");
		if (basket == null && rack == null) {
			return;
		}
		RandomSource r = level.getRandom();
		for (BlockPos pos : BlockPos.betweenClosed(min, top)) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.AIR) || state.getBlock() != basket && state.getBlock() != rack) {
				continue;
			}
			Container shelf = container(level.getBlockEntity(pos));
			if (shelf == null || !shelf.isEmpty()) {
				continue;
			}
			if (state.getBlock() == basket) {
				stockBasket(level, f, shelf, r);
			} else {
				stockRack(level, f, shelf, tools(entry), r);
			}
			shelf.setChanged();
		}
	}

	private static void stockBasket(ServerLevel level, Faction f, Container shelf, RandomSource r) {
		List<Item> kinds = new ArrayList<>(List.of(PRODUCE));
		int n = KINDS_MIN + r.nextInt(KINDS_MAX - KINDS_MIN + 1);
		for (int i = 0, slot = 0; i < n && !kinds.isEmpty() && slot < shelf.getContainerSize(); i++) {
			Item item = kinds.remove(r.nextInt(kinds.size()));
			int want = EACH_MIN + r.nextInt(EACH_MAX - EACH_MIN + 1);
			ItemStack stack = f == null ? ItemStack.EMPTY : Storage.take(level, f, item, want);
			if (stack.isEmpty()) {
				stack = new ItemStack(item, 1 + r.nextInt(3));
			}
			shelf.setItem(slot++, stack);
		}
	}

	private static void stockRack(ServerLevel level, Faction f, Container shelf, Item[] tools, RandomSource r) {
		List<Item> kinds = new ArrayList<>(List.of(tools));
		for (int slot = 0; slot < shelf.getContainerSize() && !kinds.isEmpty(); slot++) {
			Item item = kinds.remove(r.nextInt(kinds.size()));
			ItemStack stack = f == null ? ItemStack.EMPTY : Storage.take(level, f, item, 1);
			shelf.setItem(slot, stack.isEmpty() ? new ItemStack(item) : stack);
		}
	}

	/** A Cooking for Blockheads block entity's container (through its {@code getContainer()}), or null. */
	static @Nullable Container container(@Nullable BlockEntity be) {
		if (be == null) {
			return null;
		}
		try {
			Method get = be.getClass().getMethod("getContainer");
			return get.invoke(be) instanceof Container c ? c : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
