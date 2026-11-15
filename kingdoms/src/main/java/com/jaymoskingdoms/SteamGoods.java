package com.jaymoskingdoms;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * What the steam towns ({@link SteamTowns}) build with and sell that belongs to the other two mods, found by registry id
 * only and only when that mod is loaded, so Kingdoms builds and runs without either: Jaymo's Kitchen's steam and wind
 * (firebox, boilers, steam engine, cylinder, safety valve, windmill, shaft, sails, windsock) and Jaymo's Powers' coke and
 * steel (coke oven bricks and mouth, firebricks, treated planks, coal coke, creosote, tin). Without them a town is built
 * in plain brick, iron and spruce, and its stalls carry what the vanilla game has.
 */
final class SteamGoods {
	static final String KITCHEN = "jaymoskitchen", POWERS = "jaymospowers";
	private static final boolean HAS_KITCHEN = FabricLoader.getInstance().isModLoaded(KITCHEN), HAS_POWERS = FabricLoader.getInstance().isModLoaded(POWERS);

	private SteamGoods() {
	}

	static boolean kitchen() {
		return HAS_KITCHEN;
	}

	static boolean powers() {
		return HAS_POWERS;
	}

	/** {@code mod}'s block {@code path}, or null if the mod isn't loaded or has no such block. */
	static @Nullable Block block(String mod, String path) {
		if (!FabricLoader.getInstance().isModLoaded(mod)) {
			return null;
		}
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(mod, path));
		return block == Blocks.AIR ? null : block;
	}

	/** {@code mod}'s item {@code path}, or null if the mod isn't loaded or has no such item. */
	static @Nullable Item item(String mod, String path) {
		if (!FabricLoader.getInstance().isModLoaded(mod)) {
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(mod, path));
		return item == Items.AIR ? null : item;
	}

	/** {@code mod}'s block {@code path} as placed, else {@code fallback}. */
	static BlockState state(String mod, String path, Block fallback) {
		Block block = block(mod, path);
		return (block != null ? block : fallback).defaultBlockState();
	}

	/** A vanilla block by name (the coloured ones have no field), else {@code fallback}. */
	static BlockState vanilla(String name, Block fallback) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
		return (block == Blocks.AIR ? fallback : block).defaultBlockState();
	}

	/** {@code state} turned to face {@code facing}, if it faces at all. */
	static BlockState facing(BlockState state, Direction facing) {
		return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : state;
	}

	/** {@code state} lying along {@code axis}, if it lies at all. */
	static BlockState axis(BlockState state, Direction.Axis axis) {
		return state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, axis) : state;
	}

	// ---- what the towns are built of ----

	/** Timber: Powers' creosote-treated planks, else spruce. */
	static BlockState planks() {
		return state(POWERS, "treated_planks", Blocks.SPRUCE_PLANKS);
	}

	/** The coke oven's walls: Powers' coke oven bricks, else plain bricks. */
	static BlockState ovenBricks() {
		return state(POWERS, "coke_oven_bricks", Blocks.BRICKS);
	}

	/** Fire-proof lining round the boilers: Powers' firebricks, else plain bricks. */
	static BlockState firebricks() {
		return state(POWERS, "firebricks", Blocks.BRICKS);
	}

	/** The coke oven's mouth facing {@code out}: Powers' coke oven, else a furnace. */
	static BlockState ovenMouth(Direction out) {
		return facing(state(POWERS, "coke_oven", Blocks.FURNACE), out);
	}

	/** A heap of fuel: Powers' coal coke block, else coal. */
	static BlockState cokeBlock() {
		return state(POWERS, "coal_coke_block", Blocks.COAL_BLOCK);
	}

	/** A firebox facing {@code out}: Kitchen's, else a furnace. */
	static BlockState firebox(Direction out) {
		return facing(state(KITCHEN, "firebox", Blocks.FURNACE), out);
	}

	/** A boiler facing {@code out}: Kitchen's copper boiler, else a block of copper. */
	static BlockState boiler(Direction out) {
		return facing(state(KITCHEN, "copper_boiler", Blocks.COPPER_BLOCK.waxed().unaffected()), out);
	}

	/** A steam engine whose shaft leaves toward {@code out}: Kitchen's, else a cauldron on the boiler. */
	static BlockState engine(Direction out) {
		return facing(state(KITCHEN, "steam_engine", Blocks.CAULDRON), out);
	}

	/** One of Kitchen's shafts along {@code axis}, else a spruce log lying the same way. */
	static BlockState shaft(Direction.Axis axis) {
		return axis(state(KITCHEN, "shaft", Blocks.STRIPPED_SPRUCE_LOG), axis);
	}

	/** Kitchen's windmill hub facing {@code toShaft} (its four hemp sails on), or null without Kitchen. */
	static @Nullable BlockState windmill(Direction toShaft) {
		Block block = block(KITCHEN, "windmill");
		return block == null ? null : facing(block.defaultBlockState(), toShaft);
	}

	/** Kitchen's windsock, or a lightning rod. */
	static BlockState windsock() {
		return state(KITCHEN, "windsock", Blocks.LIGHTNING_ROD.waxed().unaffected());
	}
}
