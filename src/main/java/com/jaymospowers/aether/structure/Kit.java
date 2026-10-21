package com.jaymospowers.aether.structure;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/** The structures' building blocks, found by id (the Aether's own, and vanilla's), with a few state helpers. */
final class Kit {
	private static final Map<String, Block> BLOCKS = new ConcurrentHashMap<>();

	private Kit() {
	}

	/** One of our blocks by id (stone, with a log line, if it is missing). */
	static Block ours(String id) {
		return BLOCKS.computeIfAbsent("j:" + id, key -> {
			Block block = BuiltInRegistries.BLOCK.getValue(JaymosPowers.id(id));
			if (block == Blocks.AIR) {
				JaymosPowers.LOGGER.error("Aether structures: no block {}", id);
				return Blocks.STONE;
			}
			return block;
		});
	}

	static Block vanilla(String id) {
		return BLOCKS.computeIfAbsent("m:" + id, key -> BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)));
	}

	static BlockState b(String id) {
		return ours(id).defaultBlockState();
	}

	static BlockState v(String id) {
		return vanilla(id).defaultBlockState();
	}

	static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	/** {@code state} with a property set, if it has it. */
	static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, T value) {
		return state.hasProperty(property) ? state.setValue(property, value) : state;
	}

	static BlockState stairs(String id, Direction facing, boolean top) {
		return with(with(b(id), BlockStateProperties.HORIZONTAL_FACING, facing), BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(String id, boolean top) {
		return with(b(id), BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState log(String id, Direction.Axis axis) {
		return with(b(id), BlockStateProperties.AXIS, axis);
	}

	/** A crop-like block at its last growth stage. */
	static BlockState ripe(String id) {
		BlockState state = b(id);
		for (Property<?> p : state.getProperties()) {
			if (p.getName().equals("age") && p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip) {
				return state.setValue(ip, ip.getPossibleValues().stream().max(Integer::compare).orElse(0));
			}
		}
		return state;
	}

	/** A hanging arkenium lantern. */
	static BlockState lantern() {
		return with(b("arkenium_lantern"), BlockStateProperties.HANGING, true);
	}

	static BlockState water() {
		return Blocks.WATER.defaultBlockState();
	}
}
