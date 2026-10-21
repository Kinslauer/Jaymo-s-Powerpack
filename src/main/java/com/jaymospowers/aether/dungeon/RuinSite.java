package com.jaymospowers.aether.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * One piece being built in one chunk. It works in the piece's own frame: {@code u} runs across (to the right of where the piece
 * faces), {@code v} along (the way it faces), {@code y} is the world's. Everything written is kept inside the chunk, and
 * every random choice is a function of where it is (never of the order the chunks come in), so a piece that spans chunks
 * is the same whichever is built first.
 */
final class RuinSite {
	final WorldGenLevel level;
	final BoundingBox chunk, box;
	final Direction facing, right;
	final long seed;
	/** The world corner that is (u 0, v 0). */
	final int ox, oz;
	/** Cells across and along. */
	final int width, length;
	/** The standing level inside: one above the bottom of the box. */
	final int floor;

	RuinSite(WorldGenLevel level, BoundingBox chunk, BoundingBox box, Direction facing, long seed) {
		this.level = level;
		this.chunk = chunk;
		this.box = box;
		this.facing = facing;
		this.right = facing.getClockWise();
		this.seed = seed;
		int fx = facing.getStepX(), fz = facing.getStepZ(), rx = this.right.getStepX(), rz = this.right.getStepZ();
		this.ox = rx + fx > 0 ? box.minX() : box.maxX();
		this.oz = rz + fz > 0 ? box.minZ() : box.maxZ();
		this.width = rx != 0 ? box.getXSpan() : box.getZSpan();
		this.length = fx != 0 ? box.getXSpan() : box.getZSpan();
		this.floor = box.minY() + 1;
	}

	int x(int u, int v) {
		return this.ox + u * this.right.getStepX() + v * this.facing.getStepX();
	}

	int z(int u, int v) {
		return this.oz + u * this.right.getStepZ() + v * this.facing.getStepZ();
	}

	BlockPos pos(int u, int y, int v) {
		return new BlockPos(this.x(u, v), y, this.z(u, v));
	}

	/** The world direction of a local one: 0 along (v), 1 right (u), 2 back, 3 left. */
	Direction dir(int local) {
		return switch (local & 3) {
			case 0 -> this.facing;
			case 1 -> this.right;
			case 2 -> this.facing.getOpposite();
			default -> this.right.getOpposite();
		};
	}

	/** A number from 0 up to (not including) 1 that depends only on where, the piece and {@code salt}. */
	float rnd(int u, int y, int v, int salt) {
		long h = this.seed + salt * 0x632BE59BD9B4E019L + u * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + v * 0x165667B19E3779F9L;
		h ^= h >>> 32;
		h *= 0xD6E8FEB86659FD93L;
		h ^= h >>> 32;
		h *= 0xD6E8FEB86659FD93L;
		h ^= h >>> 32;
		return (h >>> 40) / (float)(1 << 24);
	}

	/** A random source that starts the same in every chunk (for lists of spots chosen up front). */
	java.util.Random random(int salt) {
		return new java.util.Random(this.seed * 31L + salt * 0x9E3779B97F4A7C15L);
	}

	boolean inside(int u, int y, int v) {
		return this.chunk.isInside(this.x(u, v), y, this.z(u, v));
	}

	void set(int u, int y, int v, BlockState state) {
		int x = this.x(u, v), z = this.z(u, v);
		if (this.chunk.isInside(x, y, z)) {
			this.level.setBlock(new BlockPos(x, y, z), state, 2);
		}
	}

	/** The block that stands where it is (air outside the chunk). */
	BlockState get(int u, int y, int v) {
		int x = this.x(u, v), z = this.z(u, v);
		return this.chunk.isInside(x, y, z) ? this.level.getBlockState(new BlockPos(x, y, z)) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
	}

	/** Which block to put at a spot. */
	interface Pick {
		BlockState at(int u, int y, int v);
	}

	void fill(int u0, int y0, int v0, int u1, int y1, int v1, BlockState state) {
		this.fill(u0, y0, v0, u1, y1, v1, (u, y, v) -> state);
	}

	void fill(int u0, int y0, int v0, int u1, int y1, int v1, Pick pick) {
		// skip what cannot touch the chunk
		int xa = this.x(u0, v0), xb = this.x(u1, v1), za = this.z(u0, v0), zb = this.z(u1, v1);
		if (Math.max(xa, xb) < this.chunk.minX() || Math.min(xa, xb) > this.chunk.maxX() || Math.max(za, zb) < this.chunk.minZ()
			|| Math.min(za, zb) > this.chunk.maxZ()) {
			return;
		}
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				int x = this.x(u, v), z = this.z(u, v);
				if (x < this.chunk.minX() || x > this.chunk.maxX() || z < this.chunk.minZ() || z > this.chunk.maxZ()) {
					continue;
				}
				for (int y = y0; y <= y1; y++) {
					BlockState state = pick.at(u, y, v);
					if (state != null && this.chunk.isInside(x, y, z)) {
						this.level.setBlock(new BlockPos(x, y, z), state, 2);
					}
				}
			}
		}
	}

	/** A sentry crate here (facing local {@code dir}), filled from {@code loot}, a mimic if {@code mimic}. */
	void crate(int u, int y, int v, int dirLocal, ResourceKey<LootTable> loot, boolean mimic) {
		if (!this.inside(u, y, v)) {
			return;
		}
		BlockPos pos = this.pos(u, y, v);
		this.level.setBlock(pos, DungeonBlocks.SENTRY_CRATE.defaultBlockState().setValue(SentryCrateBlock.FACING, this.dir(dirLocal)), 2);
		RandomizableContainer.setBlockEntityLootTable(this.level, RandomSource.create(this.seed ^ pos.asLong()), pos, loot);
		if (mimic && this.level.getBlockEntity(pos) instanceof SentryCrateEntity crate) {
			crate.setMimic(true);
		}
	}

	/** Puts a mob on the floor of a cell, facing {@code yaw} (only the chunk the cell is in does it). */
	<T extends Mob> T mob(EntityType<T> type, int u, int y, int v, float yaw) {
		return this.spawn(type, this.x(u, v) + 0.5, y, this.z(u, v) + 0.5, yaw, mob -> {
		});
	}

	/** Spawns a mob at an exact spot, after {@code setup} has dressed it (only the chunk the spot is in does it). */
	<T extends Mob> T spawn(EntityType<T> type, double x, double y, double z, float yaw, java.util.function.Consumer<T> setup) {
		if (!this.chunk.isInside(BlockPos.containing(x, y, z))) {
			return null;
		}
		T mob = type.create(this.level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (mob == null) {
			return null;
		}
		mob.snapTo(x, y, z, yaw, 0.0F);
		setup.accept(mob);
		mob.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.STRUCTURE, null);
		mob.setPersistenceRequired();
		this.level.addFreshEntityWithPassengers(mob);
		return mob;
	}
}
