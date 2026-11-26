package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rain barrels (Jaymo's Kitchen's, found by registry id, so Kingdoms needs no Kitchen jar to build or run): a village's builders
 * set one against the side wall of each house they put up, to gather rain for the house. Done at the one place every building
 * is registered ({@link Blueprint#register}), as the trade signs are ({@link TradeSigns}), to the villages' "house" only: a
 * strip one block wide is added along the house's east wall, a foundation block with the barrel on it, and the plan grows by
 * that block, so the footprint a crew levels and clears includes it. A house with no plain wall for it goes without. The barrel
 * is wood, so a crew pays for it as for a plank ({@link Materials}), and it starts empty.
 */
public final class RainBarrels {
	private static final String KITCHEN = "jaymoskitchen";
	private static final Identifier BARREL = Identifier.fromNamespaceAndPath(KITCHEN, "rain_barrel");
	/** The buildings that get one: a village's houses. */
	private static final String HOUSE = "house";

	private RainBarrels() {
	}

	/** {@code entry}, its house drawn with a rain barrel beside it if it is a village house and Kitchen is here. */
	static Blueprint.Entry beside(FactionType type, Blueprint.Entry entry) {
		if (type != FactionType.VILLAGE || !HOUSE.equals(entry.id()) || !FabricLoader.getInstance().isModLoaded(KITCHEN)) {
			return entry;
		}
		return new Blueprint.Entry(entry.id(), (level, faction, variant) -> set(entry.maker().make(level, faction, variant)), entry.max(), entry.minPopulation());
	}

	/** {@code plan} with a barrel against the outside of its east wall (as it is, if there is no plain wall or no barrel to set). */
	static Blueprint set(Blueprint plan) {
		Optional<Block> barrel = BuiltInRegistries.BLOCK.getOptional(BARREL);
		if (barrel.isEmpty()) {
			return plan;
		}
		int east = plan.width() - 1;
		// the east wall's plain stretch at the height of a person's feet, nearest the front (low z) first
		int zAt = -1;
		for (int z = 0; z < plan.depth() && zAt < 0; z++) {
			BlockState wall = at(plan, east, 1, z), floor = at(plan, east, 0, z);
			if (wall != null && floor != null && plain(wall) && at(plan, east, 2, z) != null) {
				zAt = z;
			}
		}
		if (zAt < 0) {
			return plan;
		}
		BlockPos ground = new BlockPos(plan.width(), 0, zAt), barrelAt = ground.above();
		List<Blueprint.Placement> blocks = new ArrayList<>(plan.blocks().size() + 2);
		boolean footing = false, stood = false;
		for (Blueprint.Placement pl : plan.blocks()) {
			// keep the bottom-up order: the footing with the ground layer, the barrel with the first layer
			if (!footing && pl.offset().getY() >= 1) {
				blocks.add(new Blueprint.Placement(ground, at(plan, east, 0, zAt)));
				footing = true;
			}
			if (!stood && pl.offset().getY() >= 2) {
				blocks.add(new Blueprint.Placement(barrelAt, barrel.get().defaultBlockState()));
				stood = true;
			}
			blocks.add(pl);
		}
		if (!footing) {
			blocks.add(new Blueprint.Placement(ground, at(plan, east, 0, zAt)));
		}
		if (!stood) {
			blocks.add(new Blueprint.Placement(barrelAt, barrel.get().defaultBlockState()));
		}
		return new Blueprint(plan.id(), plan.width() + 1, plan.depth(), plan.height(), List.copyOf(blocks));
	}

	/** A whole wall block: full, no door, no pane, no opening. */
	private static boolean plain(BlockState state) {
		return !(state.getBlock() instanceof DoorBlock) && Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
	}

	private static BlockState at(Blueprint plan, int x, int y, int z) {
		for (Blueprint.Placement pl : plan.blocks()) {
			BlockPos o = pl.offset();
			if (o.getX() == x && o.getY() == y && o.getZ() == z) {
				return pl.state();
			}
		}
		return null;
	}
}
