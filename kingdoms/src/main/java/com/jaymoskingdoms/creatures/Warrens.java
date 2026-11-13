package com.jaymoskingdoms.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A digger warren: a chamber under a mound with a couple of diggers in it, and round it, hidden in the ground, pits with
 * stakes (pointed dripstone) at the bottom, each lidded by a trap cover ({@link TrapCoverBlock}) dressed as the ground it
 * replaced. A digger waits in a nook dug into the side of each pit ({@link Digger#lurk}) and is on whoever falls in at once;
 * the lid giving brings the rest. A careful walker sneaks; a watchful one sees the lid is out of place.
 */
final class Warrens {
	/** Diggers in a warren's chamber (each pit has its own besides), pits round it, how far out the pits ring (blocks), and how deep each is. */
	static final int DIGGERS_LEAST = 1, DIGGERS_MOST = 2, PITS = 7, RING = 6, DEPTH = 3;
	/** How far (blocks) the diggers hear a lid give way. */
	static final double ALARM = 24.0;

	private Warrens() {
	}

	/** Digs a warren at {@code middle}'s surface; true if it was dug. */
	static boolean dig(ServerLevel level, BlockPos middle) {
		RandomSource random = level.getRandom();
		BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, middle);
		BlockPos floor = top.below(3);
		if (!level.getFluidState(top).isEmpty() || !level.getBlockState(top.below()).isSolid()) {
			return false;
		}
		// the chamber: 3x3, two high, with the mound above (a mouth to the sky)
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy < 2; dy++) {
					level.setBlock(floor.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		for (int y = floor.getY() + 2; y <= top.getY() - 1; y++) {
			level.setBlock(new BlockPos(top.getX(), y, top.getZ()), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		level.setBlock(top.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		BlockPos chest = floor.offset(1, 0, 1);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(chest) instanceof ChestBlockEntity box) {
			box.setItem(random.nextInt(box.getContainerSize()), new ItemStack(Items.IRON_NUGGET, 4 + random.nextInt(9)));
			box.setItem(random.nextInt(box.getContainerSize()), new ItemStack(Items.BONE, 2 + random.nextInt(4)));
			box.setItem(random.nextInt(box.getContainerSize()), new ItemStack(Items.COAL, 3 + random.nextInt(5)));
		}
		int n = DIGGERS_LEAST + random.nextInt(DIGGERS_MOST - DIGGERS_LEAST + 1);
		for (int i = 0; i < n; i++) {
			Digger d = Creatures.DIGGER.create(level, EntitySpawnReason.EVENT);
			if (d != null) {
				// (down in the chamber, not out on the mound)
				d.snapTo(floor.getX() + 0.5 - random.nextInt(2), floor.getY(), floor.getZ() + 0.5 - random.nextInt(2), random.nextFloat() * 360.0F, 0.0F);
				d.setPersistenceRequired();
				d.home(floor);
				level.addFreshEntity(d);
			}
		}
		for (int i = 0; i < PITS; i++) {
			double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2.0 / PITS;
			int r = RING + random.nextInt(3);
			pit(level, top.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r)));
		}
		return true;
	}

	/** A pit at {@code near}'s column: stakes at the bottom, a digger in a nook beside them, a lid like the ground on top. */
	private static void pit(ServerLevel level, BlockPos near) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, near).below();
		if (!level.getBlockState(top).isSolid() || !level.getFluidState(top.above()).isEmpty() || !level.getBlockState(top.above()).isAir()) {
			return;
		}
		for (int d = 0; d < DEPTH; d++) {
			if (!level.getBlockState(top.below(d)).isSolid()) {
				return;
			}
		}
		// the lid is dressed as the ground it takes the place of (or, failing that, the ground beside it): no pit where it can't be
		TrapCoverBlock.Ground look = TrapCoverBlock.Ground.of(level.getBlockState(top));
		BlockState lid = look != null ? Creatures.TRAP_COVER.defaultBlockState().setValue(TrapCoverBlock.LOOK, look) : TrapCoverBlock.matching(level, top);
		if (lid == null) {
			return;
		}
		for (int d = 1; d < DEPTH; d++) {
			level.setBlock(top.below(d), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		level.setBlock(top.below(DEPTH), Blocks.POINTED_DRIPSTONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(top, lid, Block.UPDATE_ALL);
		lurker(level, top);
	}

	/**
	 * A nook two high in one wall of the pit under {@code top}, its floor level with the stakes, and a digger waiting in it.
	 * Dug only where the ground is sound: solid all round, and a roof that will not fall in.
	 */
	private static void lurker(ServerLevel level, BlockPos top) {
		for (Direction side : Direction.Plane.HORIZONTAL.shuffledCopy(level.getRandom())) {
			BlockPos nook = top.below(DEPTH).relative(side), roof = nook.above(2);
			if (!sound(level, roof) || level.getBlockState(roof).getBlock() instanceof FallingBlock || !sound(level, nook) || !sound(level, nook.above())
				|| !sound(level, nook.below()) || !sound(level, nook.relative(side)) || !sound(level, nook.above().relative(side))
				|| !sound(level, nook.relative(side.getClockWise())) || !sound(level, nook.relative(side.getCounterClockWise()))
				|| !sound(level, nook.above().relative(side.getClockWise())) || !sound(level, nook.above().relative(side.getCounterClockWise()))) {
				continue;
			}
			level.setBlock(nook, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(nook.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			Digger d = Creatures.DIGGER.create(level, EntitySpawnReason.EVENT);
			if (d != null) {
				d.snapTo(nook.getX() + 0.5, nook.getY(), nook.getZ() + 0.5, side.getOpposite().toYRot(), 0.0F);
				d.setPersistenceRequired();
				d.lurk(nook);
				level.addFreshEntity(d);
			}
			return;
		}
	}

	private static boolean sound(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isSolid() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
	}

	/** A lid gave way under {@code victim}: the diggers about come for them. */
	static void alarm(ServerLevel level, BlockPos at, LivingEntity victim) {
		for (Digger d : level.getEntitiesOfClass(Digger.class, new net.minecraft.world.phys.AABB(at).inflate(ALARM), x -> x.isAlive())) {
			d.embolden();
			d.setTarget(victim);
		}
	}
}
