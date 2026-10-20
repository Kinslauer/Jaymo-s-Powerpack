package com.jaymoskitchen.ragdoll;

import com.jaymoskitchen.corpse.CorpseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.5.0) The game's side of {@link RagdollWorld}: blocks' collision shapes, the living things and vehicles moving
 * through the body (someone walking over it kicks it, a cart running over it flings it), and water and lava.
 */
public final class LevelRagdollWorld implements RagdollWorld {
	/** The most blocks looked at in one tick (a body flung very fast looks at fewer, nearest first). */
	private static final int MAX_BLOCKS = 6000;
	/** Something moving slower than this (blocks a tick) doesn't kick a body. */
	private static final double MOVER_MIN_SPEED = 0.02;

	private final Level level;
	/** Never counted as moving into the body: the body's own entity (a corpse, or a living player ragdolling). */
	private final Entity self;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	public LevelRagdollWorld(Level level, Entity self) {
		this.level = level;
		this.self = self;
	}

	@Override
	public void solids(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Boxes out) {
		int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY), z0 = (int) Math.floor(minZ);
		int x1 = (int) Math.floor(maxX), y1 = (int) Math.floor(maxY), z1 = (int) Math.floor(maxZ);
		long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
		if (volume > MAX_BLOCKS) {
			// shrink toward the middle until it fits (a huge body flung far: it'll catch up next tick)
			double shrink = Math.cbrt((double) MAX_BLOCKS / volume);
			double cx = (minX + maxX) * 0.5, cy = (minY + maxY) * 0.5, cz = (minZ + maxZ) * 0.5;
			x0 = (int) Math.floor(cx - (cx - minX) * shrink);
			x1 = (int) Math.floor(cx + (maxX - cx) * shrink);
			y0 = (int) Math.floor(cy - (cy - minY) * shrink);
			y1 = (int) Math.floor(cy + (maxY - cy) * shrink);
			z0 = (int) Math.floor(cz - (cz - minZ) * shrink);
			z1 = (int) Math.floor(cz + (maxZ - cz) * shrink);
		}
		y0 = Math.max(y0, level.getMinY());
		y1 = Math.min(y1, level.getMaxY());
		for (int bx = x0; bx <= x1; bx++) {
			for (int bz = z0; bz <= z1; bz++) {
				for (int by = y0; by <= y1; by++) {
					cursor.set(bx, by, bz);
					BlockState state = level.getBlockState(cursor);
					if (state.isAir()) {
						continue;
					}
					VoxelShape shape = state.getCollisionShape(level, cursor);
					if (shape.isEmpty()) {
						continue;
					}
					for (AABB box : shape.toAabbs()) {
						out.add(bx + box.minX, by + box.minY, bz + box.minZ, bx + box.maxX, by + box.maxY, bz + box.maxZ);
					}
				}
			}
		}
	}

	@Override
	public void movers(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Boxes out) {
		AABB area = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
		for (Entity entity : level.getEntities(self, area, LevelRagdollWorld::moves)) {
			double vx = entity.getX() - entity.xo, vy = entity.getY() - entity.yo, vz = entity.getZ() - entity.zo;
			if (vx * vx + vy * vy + vz * vz < MOVER_MIN_SPEED * MOVER_MIN_SPEED) {
				continue;
			}
			AABB box = entity.getBoundingBox();
			out.add(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, vx, vy, vz);
		}
	}

	/** Walkers, riders and vehicles kick bodies; other bodies, items, arrows and the like don't. */
	private static boolean moves(Entity entity) {
		if (entity instanceof CorpseEntity || entity.isSpectator() || !entity.isAlive()) {
			return false;
		}
		return entity instanceof LivingEntity || entity instanceof AbstractBoat
			|| entity instanceof com.jaymoskitchen.horse.CartEntity
			|| BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().contains("minecart");
	}

	@Override
	public int fluid(double x, double y, double z) {
		cursor.set(x, y, z);
		FluidState fluid = level.getFluidState(cursor);
		if (fluid.isEmpty()) {
			return AIR;
		}
		// only below the fluid's surface
		if (y > cursor.getY() + fluid.getHeight(level, cursor)) {
			return AIR;
		}
		return fluid.is(FluidTags.LAVA) ? LAVA : fluid.is(FluidTags.WATER) ? WATER : AIR;
	}
}
