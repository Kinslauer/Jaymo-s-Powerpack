package com.jaymospowers.tundra;

import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.tundra.world.TundraWorld;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * How a burnt taiga comes back as lichen woodland, the way boreal forest does after a fire: first the moss, then the
 * soil, then the shrubs and the first spruces. No state is kept; each stage is read off the blocks themselves.
 * <ol>
 * <li><b>Scorch</b>: when a log burns away in a taiga or in the lichen woodland ({@link #scorch}), the soil under it is left
 * as coarse dirt.</li>
 * <li><b>Moss</b>: now and then yellow moss takes hold on open coarse dirt there ({@link #MOSS_CHANCE}).</li>
 * <li><b>Soil</b>: where moss has spread over a patch ({@link #MOSS_FOR_SOIL} carpets close by), its dead lower growth makes
 * the coarse dirt under it podzol: the moss rebuilds what the fire took.</li>
 * <li><b>Stand</b>: on podzol the moss covers, a shrub or a spruce sapling replaces the carpet ({@link #PLANT_CHANCE}), as
 * long as the stand near it is not already crowded ({@link #CROWD}); the sapling grows dwarfed in the cold, as the
 * woodland's trees do.</li>
 * </ol>
 * Only the Overworld, only near players, and never on land a Kingdoms faction holds ({@link KingdomsCompat#claimed}): a
 * player who wants a burn kept or cleared keeps it, nothing is forced on their ground.
 */
public final class Regrowth {
	// ---- tuning ----
	/** Ticks between rounds, the spots tried round each player in one, and how far round the player (blocks). */
	public static final int EVERY = 40, SAMPLES = 24, RADIUS = 48;
	/** The chance moss takes hold at a bare spot, and a plant at a mossy one. */
	public static final float MOSS_CHANCE = 0.2F, PLANT_CHANCE = 0.12F;
	/** Moss carpets within {@link #NEAR} blocks that turn coarse dirt to podzol. */
	public static final int MOSS_FOR_SOIL = 3, NEAR = 3;
	/** Shrubs, saplings and logs within {@link #CROWD_RANGE} blocks above which no more are planted. */
	public static final int CROWD = 3, CROWD_RANGE = 4;
	/** Share of new plants that are spruce saplings; the rest are shrubs. */
	public static final float SAPLING_SHARE = 0.4F;
	/** How far under a burnt log the soil is looked for. */
	public static final int SCORCH_DEPTH = 4;

	private Regrowth() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
					round(level, player.blockPosition());
				}
			}
		});
	}

	/** Whether burnt land at {@code pos} is the kind that comes back as lichen woodland. */
	private static boolean regrows(ServerLevel level, BlockPos pos) {
		var biome = level.getBiome(pos);
		return biome.is(BiomeTags.IS_TAIGA) || biome.is(TundraWorld.LICHEN_WOODLAND);
	}

	/** (FireBlock mixin) A log at {@code pos} has just burnt away: its ground is scorched. */
	public static void scorch(ServerLevel level, BlockPos pos) {
		if (!regrows(level, pos) || KingdomsCompat.claimed(level, pos)) {
			return;
		}
		BlockPos.MutableBlockPos at = pos.mutable();
		for (int i = 0; i < SCORCH_DEPTH; i++) {
			at.move(0, -1, 0);
			BlockState state = level.getBlockState(at);
			if (state.isAir() || state.is(Blocks.FIRE)) {
				continue;
			}
			if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MOSS_BLOCK)) {
				level.setBlockAndUpdate(at, Blocks.COARSE_DIRT.defaultBlockState());
			}
			return;
		}
	}

	private static void round(ServerLevel level, BlockPos centre) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < SAMPLES; i++) {
			int x = centre.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS, z = centre.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
			if (!level.hasChunkAt(new BlockPos(x, centre.getY(), z))) {
				continue;
			}
			BlockPos above = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			BlockPos ground = above.below();
			BlockState soil = level.getBlockState(ground);
			BlockState top = level.getBlockState(above);
			// (a carpet has no collision, so the spot the heightmap calls free is the one it lies on)
			boolean mossy = top.is(Tundra.YELLOW_MOSS_CARPET);
			if (!regrows(level, ground) || KingdomsCompat.claimed(level, ground)) {
				continue;
			}
			if (soil.is(Blocks.COARSE_DIRT)) {
				if (!mossy && top.isAir() && random.nextFloat() < MOSS_CHANCE) {
					level.setBlockAndUpdate(above, Tundra.YELLOW_MOSS_CARPET.defaultBlockState());
				} else if (mossy && count(level, above, NEAR, Tundra.YELLOW_MOSS_CARPET) >= MOSS_FOR_SOIL) {
					level.setBlockAndUpdate(ground, Blocks.PODZOL.defaultBlockState());
				}
			} else if (soil.is(Blocks.PODZOL) && mossy && random.nextFloat() < PLANT_CHANCE && crowd(level, above) < CROWD) {
				plant(level, above, random);
			}
		}
	}

	private static void plant(ServerLevel level, BlockPos at, RandomSource random) {
		Block[] shrubs = {Tundra.STUNTED_RED_SHRUB, Tundra.ORANGE_SHRUB, Tundra.YELLOW_SHRUB};
		Block plant = random.nextFloat() < SAPLING_SHARE ? Blocks.SPRUCE_SAPLING : shrubs[random.nextInt(shrubs.length)];
		BlockState state = plant.defaultBlockState();
		if (state.canSurvive(level, at)) {
			level.setBlockAndUpdate(at, state);
		}
	}

	/** Shrubs, spruce saplings and logs standing close to {@code at}. */
	private static int crowd(ServerLevel level, BlockPos at) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-CROWD_RANGE, -1, -CROWD_RANGE), at.offset(CROWD_RANGE, 3, CROWD_RANGE))) {
			BlockState state = level.getBlockState(p);
			if (state.is(Tundra.SHRUBS) || state.is(Blocks.SPRUCE_SAPLING) || state.is(net.minecraft.tags.BlockTags.LOGS)) {
				n++;
			}
		}
		return n;
	}

	private static int count(ServerLevel level, BlockPos at, int range, Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-range, 0, -range), at.offset(range, 0, range))) {
			if (level.getBlockState(p).is(block)) {
				n++;
			}
		}
		return n;
	}
}
