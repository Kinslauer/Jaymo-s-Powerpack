package com.jaymospowers.aether.structure;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * One piece being built in one chunk, in the piece's own frame: {@code u} runs across (to the right of where the piece faces),
 * {@code v} along (the way it faces), {@code y} is the world's. Everything written stays inside the chunk, and every random
 * choice is a function of where it is (never of the order chunks come in), so a piece that spans chunks is the same whichever
 * chunk is built first. Works like the Sentry Ruins' site.
 */
final class Site {
	final WorldGenLevel level;
	final BoundingBox chunk, box;
	final Direction facing, right;
	final long seed;
	final int variant;
	/** The world corner that is (u 0, v 0). */
	final int ox, oz;
	/** Cells across and along. */
	final int width, length;
	/** The standing level inside: one above the bottom of the box. */
	final int floor;

	Site(WorldGenLevel level, BoundingBox chunk, BoundingBox box, Direction facing, long seed, int variant) {
		this.level = level;
		this.chunk = chunk;
		this.box = box;
		this.facing = facing;
		this.right = facing.getClockWise();
		this.seed = seed;
		this.variant = variant;
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

	BlockState get(int u, int y, int v) {
		int x = this.x(u, v), z = this.z(u, v);
		return this.chunk.isInside(x, y, z) ? this.level.getBlockState(new BlockPos(x, y, z)) : Blocks.AIR.defaultBlockState();
	}

	/** Which block to put at a spot (null leaves it). */
	interface Pick {
		BlockState at(int u, int y, int v);
	}

	void fill(int u0, int y0, int v0, int u1, int y1, int v1, BlockState state) {
		this.fill(u0, y0, v0, u1, y1, v1, (u, y, v) -> state);
	}

	void fill(int u0, int y0, int v0, int u1, int y1, int v1, Pick pick) {
		int xa = this.x(u0, v0), xb = this.x(u1, v1), za = this.z(u0, v0), zb = this.z(u1, v1);
		if (Math.max(xa, xb) < this.chunk.minX() || Math.min(xa, xb) > this.chunk.maxX() || Math.max(za, zb) < this.chunk.minZ()
			|| Math.min(za, zb) > this.chunk.maxZ()) {
			return;
		}
		for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) {
			for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++) {
				int x = this.x(u, v), z = this.z(u, v);
				if (x < this.chunk.minX() || x > this.chunk.maxX() || z < this.chunk.minZ() || z > this.chunk.maxZ()) {
					continue;
				}
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					BlockState state = pick.at(u, y, v);
					if (state != null && this.chunk.isInside(x, y, z)) {
						this.level.setBlock(new BlockPos(x, y, z), state, 2);
					}
				}
			}
		}
	}

	void air(int u0, int y0, int v0, int u1, int y1, int v1) {
		this.fill(u0, y0, v0, u1, y1, v1, Kit.air());
	}

	/** A box of walls (floor and ceiling too when {@code caps}) with air inside. */
	void room(int u0, int y0, int v0, int u1, int y1, int v1, Pick wall, boolean caps) {
		this.fill(u0, y0, v0, u1, y1, v1, (u, y, v) -> {
			boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
			boolean cap = y == y0 || y == y1;
			return edge || caps && cap ? wall.at(u, y, v) : Kit.air();
		});
	}

	/** Holds up a platform: every column of the rectangle that stands over air or water or plants gets {@code state} down to the ground (at most {@code depth}). */
	void foundation(int u0, int v0, int u1, int v1, int y, BlockState state, int depth) {
		for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) {
			for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++) {
				int x = this.x(u, v), z = this.z(u, v);
				if (!this.chunk.isInside(x, y, z)) {
					continue;
				}
				for (int d = 0; d < depth; d++) {
					BlockPos p = new BlockPos(x, y - d, z);
					BlockState at = this.level.getBlockState(p);
					if (d > 0 && !(at.isAir() || !at.getFluidState().isEmpty() || at.canBeReplaced() || at.is(net.minecraft.tags.BlockTags.LEAVES) || at.is(net.minecraft.tags.BlockTags.LOGS))) {
						break;
					}
					this.level.setBlock(p, state, 2);
				}
			}
		}
	}

	/** A layer-by-layer roof: {@code state} over the rectangle at {@code y}, then one smaller each layer up until it closes. */
	void pyramid(int u0, int v0, int u1, int v1, int y, BlockState state) {
		while (u0 <= u1 && v0 <= v1) {
			this.fill(u0, y, v0, u1, y, v1, state);
			u0++;
			v0++;
			u1--;
			v1--;
			y++;
		}
	}

	// ---- containers ----

	static ResourceKey<LootTable> table(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, JaymosPowers.id(name));
	}

	/** A chest here, its front toward local {@code dir}, filled from loot table {@code name} the first time it is opened. */
	void chest(int u, int y, int v, int dirLocal, String name) {
		if (!this.inside(u, y, v)) {
			return;
		}
		BlockPos pos = this.pos(u, y, v);
		this.level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, this.dir(dirLocal)), 2);
		RandomizableContainer.setBlockEntityLootTable(this.level, RandomSource.create(this.seed ^ pos.asLong()), pos, table(name));
	}

	/** A vase of {@code kind} here ({@code holystone}, {@code veradexian} or {@code brexallen}) holding {@code loot}. */
	void vase(int u, int y, int v, String kind, String loot) {
		if (!this.inside(u, y, v)) {
			return;
		}
		BlockPos pos = this.pos(u, y, v);
		this.level.setBlock(pos, Kit.b(kind + "_vase"), 2);
		RandomizableContainer.setBlockEntityLootTable(this.level, RandomSource.create(this.seed ^ pos.asLong()), pos, table(loot));
	}

	/** An animal stash here, facing local {@code dir}. */
	void stash(int u, int y, int v, int dirLocal, String loot) {
		if (!this.inside(u, y, v)) {
			return;
		}
		BlockPos pos = this.pos(u, y, v);
		this.level.setBlock(pos, Kit.with(Kit.b("animal_stash"), BlockStateProperties.HORIZONTAL_FACING, this.dir(dirLocal)), 2);
		RandomizableContainer.setBlockEntityLootTable(this.level, RandomSource.create(this.seed ^ pos.asLong()), pos, table(loot));
	}

	/** A ladder going up the face of a wall: it faces local {@code dir}. */
	void ladder(int u, int y0, int y1, int v, int dirLocal) {
		BlockState state = Kit.with(Kit.v("ladder"), BlockStateProperties.HORIZONTAL_FACING, this.dir(dirLocal));
		this.fill(u, y0, v, u, y1, v, state);
	}

	/** A wall-mounted prayer candle on the face of a wall: it faces local {@code dir}, lit. */
	void candle(int u, int y, int v, int dirLocal) {
		this.set(u, y, v, Kit.with(Kit.b("prayer_candle"), BlockStateProperties.HORIZONTAL_FACING, this.dir(dirLocal)));
	}

	/** A 2x2 outpost campfire, lit, covering local (u, v) to (u + 1, v + 1). */
	void outpostCampfire(int u, int y, int v) {
		int minX = Math.min(this.x(u, v), this.x(u + 1, v + 1)), minZ = Math.min(this.z(u, v), this.z(u + 1, v + 1));
		for (int du = 0; du < 2; du++) {
			for (int dv = 0; dv < 2; dv++) {
				BlockState state = Kit.b("outpost_campfire");
				state = Kit.with(state, OutpostCampfireBlock.EAST_PART, this.x(u + du, v + dv) > minX);
				state = Kit.with(state, OutpostCampfireBlock.SOUTH_PART, this.z(u + du, v + dv) > minZ);
				state = Kit.with(state, BlockStateProperties.LIT, true);
				this.set(u + du, y, v + dv, state);
			}
		}
	}

	// ---- mobs ----

	/** Spawns a mob at an exact spot, after {@code setup} has dressed it (only the chunk the spot is in does it), as the ruins' site does. */
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
