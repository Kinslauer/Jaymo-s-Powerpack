package com.jaymoskingdoms.remnants;

import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Fused glass round the Irradiated monolith (Andy's pick: "fused-glass ground round the Irradiated Giant's monolith").
 * The last of the Aether's sick light burned the ground it stands on: round the Irradiated monolith the sand has run to clear
 * glass and the soil has cooked to green, glassy crust, thickest at its foot and breaking up into patches toward the rim
 * ({@link #RADIUS}). Only the top block of each column is fused, so the ground under it is as it was, and nothing of the
 * monolith's stone, its paving or its rubble is touched. A new monolith is fused as it is raised
 * ({@link Monoliths#raise}); one already standing is fused once, the first time a player comes within {@link #NEAR} of it
 * with its ground loaded. Fusing is idempotent (glass is not sand or soil), so a monolith fused twice is the same.
 */
public final class FusedGround {
	// ---- tuning ----
	/** How far (blocks, level) the ground is fused round the monolith's foot, and how much of that is solid crust. */
	static final int RADIUS = 30;
	static final float SOLID_SHARE = 0.35F;
	/** The chance a column at the rim is fused (the chance falls from 1 to this across the patchy part). */
	static final float RIM_CHANCE = 0.12F;
	/** How near a player must be (blocks) for an old monolith to be fused, and how often (ticks) that is looked at. */
	static final double NEAR = 96.0;
	static final int CHECK_TICKS = 100;

	/** Monoliths fused this session, by foot. */
	private static final Set<BlockPos> DONE = new HashSet<>();

	private FusedGround() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FusedGround::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DONE.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 11) {
			return;
		}
		ServerLevel level = Factions.level(server);
		for (Monoliths.Monolith m : Monoliths.all(level)) {
			if (m.element() != Element.IRRADIATED || DONE.contains(m.pos())) {
				continue;
			}
			boolean near = false;
			for (ServerPlayer player : level.players()) {
				if (Monoliths.horizontal(m.pos(), player.blockPosition()) < NEAR * NEAR) {
					near = true;
					break;
				}
			}
			if (near && level.hasChunksAt(m.pos().getX() - RADIUS, m.pos().getZ() - RADIUS, m.pos().getX() + RADIUS, m.pos().getZ() + RADIUS)) {
				fuse(level, m.pos(), level.getRandom());
			}
		}
	}

	/** Fuses the sand and soil round the monolith whose foot is {@code foot}. */
	static void fuse(ServerLevel level, BlockPos foot, RandomSource random) {
		DONE.add(foot.immutable());
		int fused = 0;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				double share = Math.sqrt(dx * dx + dz * dz) / RADIUS;
				if (share > 1.0) {
					continue;
				}
				// solid crust at the foot, patches beyond it
				float chance = share <= SOLID_SHARE ? 1.0F : Mth.lerp((float)((share - SOLID_SHARE) / (1.0 - SOLID_SHARE)), 1.0F, RIM_CHANCE);
				if (random.nextFloat() >= chance) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, foot.offset(dx, 0, dz)).below();
				BlockState ground = level.getBlockState(top);
				BlockState glass = fused(ground, share, random);
				if (glass == null || !level.getFluidState(top.above()).isEmpty()) {
					continue;
				}
				level.setBlock(top, glass, Block.UPDATE_ALL);
				// whatever grew on it is burned off
				BlockState above = level.getBlockState(top.above());
				if (!above.isAir() && above.canBeReplaced()) {
					level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
				fused++;
			}
		}
		JaymosKingdoms.LOGGER.info("Fused {} blocks of ground round the Irradiated monolith at {}", fused, foot);
	}

	/**
	 * What {@code ground} becomes: sand runs to clear glass; soil, gravel and clay cook to green stained glass near the foot
	 * and, further out, now and then to clear glass; null for anything else (stone, water, the monolith's own blocks).
	 */
	private static BlockState fused(BlockState ground, double share, RandomSource random) {
		if (ground.is(BlockTags.SAND)) {
			return Blocks.GLASS.defaultBlockState();
		}
		boolean soil = ground.is(BlockTags.DIRT) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.CLAY) || ground.is(Element.IRRADIATED.ground());
		if (!soil) {
			return null;
		}
		return share > 0.6 && random.nextInt(3) == 0 ? Blocks.GLASS.defaultBlockState() : Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.GREEN).defaultBlockState();
	}
}
