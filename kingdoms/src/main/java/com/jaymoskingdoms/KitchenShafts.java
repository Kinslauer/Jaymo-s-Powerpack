package com.jaymoskingdoms;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * (batch 4) Jaymo's Kitchen (optional), for the {@link RopeLift} pulley: whether one of Kitchen's shafts turns against a
 * block. Read only by registry id and block state ({@code turning}, {@code axis}), never Kitchen's code, so Kingdoms
 * builds and runs without it; without Kitchen nothing ever turns. A windmill or waterwheel sets every shaft of its run
 * turning, whatever is at the end, so a pulley at the end of a run sees its shaft turn.
 */
final class KitchenShafts {
	static final String MOD = "jaymoskitchen";
	static final Identifier SHAFT = Identifier.fromNamespaceAndPath(MOD, "shaft"), CORNER = Identifier.fromNamespaceAndPath(MOD, "shaft_corner");
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);

	private KitchenShafts() {
	}

	/** A turning shaft ends against {@code pos} (a straight one lying toward it, or a corner piece beside it). */
	static boolean turning(Level level, BlockPos pos) {
		if (!LOADED) {
			return false;
		}
		for (Direction d : Direction.values()) {
			BlockState state = level.getBlockState(pos.relative(d));
			Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			boolean shaft = id.equals(SHAFT);
			if (!shaft && !id.equals(CORNER) || !"true".equals(value(state, "turning"))) {
				continue;
			}
			if (!shaft || d.getAxis().getName().equals(value(state, "axis"))) {
				return true;
			}
		}
		return false;
	}

	/** A block state property's value by its name ("" if it has none). */
	private static String value(BlockState state, String name) {
		for (Property<?> p : state.getProperties()) {
			if (p.getName().equals(name)) {
				return name(state, p);
			}
		}
		return "";
	}

	private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}
}
