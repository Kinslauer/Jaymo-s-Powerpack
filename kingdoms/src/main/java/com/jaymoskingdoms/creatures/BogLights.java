package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.Notice;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The lights of the bog at night. Now and then, near a player alone in a swamp after dark, one light rises a little way off
 * and begins to lead: to the door of a witch's hut if one stands near (there are no hags in this world; the swamp witch and
 * her mud golems are who keeps the lights), else toward quicksand that is already there, or deep water, or the lip of a drop, and where none is near, to a
 * pit of quicksand it lays down at the spot (a few blocks across, the depth of a man and more, looking like the sand of
 * the shore). A patient eye finds quicksand before it finds you ({@link Notice}).
 */
public final class BogLights {
	/** Looked at this often (ticks); the chance each time a player in a bog after dark gets a light; no more than this many near. */
	static final int EVERY = 200;
	static final float CHANCE = 0.3F;
	static final int MOST_NEAR = 2;
	/** A light rises this far from the player (blocks), and the goal it picks is this far from them (least, most). */
	static final int RISES_MIN = 10, RISES_MAX = 16, GOAL_MIN = 16, GOAL_MAX = 34;
	/** How often a light leads to a witch's hut when one stands within this many chunks. */
	static final float TO_HUT = 0.5F;
	static final int HUT_CHUNKS = 5;
	/** A hazard is looked for within this far (blocks) of the goal ring. */
	static final int LOOK_RADIUS = 30;
	/** A pit of quicksand: half-width and depth. */
	static final int PIT_HALF = 1, PIT_DEPTH = 4;

	private BogLights() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BogLights::tick);
		Notice.add((player, into) -> {
			ServerLevel level = player.level();
			BlockPos at = player.blockPosition();
			for (BlockPos p : BlockPos.betweenClosed(at.offset(-5, -2, -5), at.offset(5, 1, 5))) {
				if (level.getBlockState(p).is(Creatures.QUICKSAND) && !level.getBlockState(p.above()).is(Creatures.QUICKSAND)) {
					into.add(new Notice.Spot(p.asLong() ^ 0x51CCL << 40, Vec3.atCenterOf(p).add(0, 0.4, 0), SoundEvents.SAND_STEP));
				}
			}
		});
	}

	static boolean bog(ServerLevel level, BlockPos pos) {
		var biome = level.getBiome(pos);
		return biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level.isBrightOutside()) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isCreative() || !bog(level, p.blockPosition()) || !level.canSeeSky(p.blockPosition()) || p.getRandom().nextFloat() >= CHANCE) {
				continue;
			}
			if (level.getEntitiesOfClass(BogLight.class, p.getBoundingBox().inflate(48.0)).size() >= MOST_NEAR) {
				continue;
			}
			lead(level, p);
		}
	}

	private static void lead(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		// to the witch's door, when her hut stands near (the bog's lights are hers as often as not); else to the mire
		BlockPos goal = random.nextFloat() < TO_HUT ? hut(level, player) : null;
		if (goal == null) {
			goal = existing(level, player, random);
		}
		if (goal == null) {
			goal = pit(level, player, random);
		}
		if (goal == null) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2;
		int dist = RISES_MIN + random.nextInt(RISES_MAX - RISES_MIN + 1);
		BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist)));
		BogLight light = Creatures.BOG_LIGHT.create(level, EntitySpawnReason.EVENT);
		if (light == null) {
			return;
		}
		light.snapTo(spot.getX() + 0.5, spot.getY() + BogLight.HOVER, spot.getZ() + 0.5, 0.0F, 0.0F);
		light.leads(player, goal);
		level.addFreshEntity(light);
	}

	/** The door of a witch's hut within {@link #HUT_CHUNKS} chunks of {@code player}, or null. */
	private static BlockPos hut(ServerLevel level, ServerPlayer player) {
		var structure = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
			.getValue(net.minecraft.world.level.levelgen.structure.BuiltinStructures.SWAMP_HUT);
		if (structure == null) {
			return null;
		}
		int cx = player.chunkPosition().x(), cz = player.chunkPosition().z();
		for (int dx = -HUT_CHUNKS; dx <= HUT_CHUNKS; dx++) {
			for (int dz = -HUT_CHUNKS; dz <= HUT_CHUNKS; dz++) {
				if (!level.hasChunk(cx + dx, cz + dz)) {
					continue;
				}
				for (var start : level.structureManager().startsForStructure(cx + dx, cz + dz, s -> s == structure)) {
					if (start.isValid()) {
						var box = start.getBoundingBox();
						BlockPos door = new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ());
						if (door.distSqr(player.blockPosition()) > (double) GOAL_MIN * GOAL_MIN) {
							return door;
						}
					}
				}
			}
		}
		return null;
	}

	/** Quicksand, deep water or a drop already lying a good way off. */
	private static BlockPos existing(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int i = 0; i < 24; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			int d = GOAL_MIN + random.nextInt(GOAL_MAX - GOAL_MIN + 1);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d)));
			if (!level.isLoaded(top)) {
				continue;
			}
			BlockPos under = top.below();
			if (level.getBlockState(under).is(Creatures.QUICKSAND)) {
				return under;
			}
			if (level.getFluidState(under).is(FluidTags.WATER) && level.getFluidState(under.below(2)).is(FluidTags.WATER)) {
				return under;
			}
			// the lip of a drop
			if (level.getBlockState(under).isSolid() && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.offset(2, 0, 0)).getY() <= top.getY() - 5) {
				return under;
			}
		}
		return null;
	}

	/** Lays a pit of quicksand in sand, dirt, mud or grass a good way from the player; its centre, or null. */
	private static BlockPos pit(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int i = 0; i < 16; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			int d = GOAL_MIN + random.nextInt(GOAL_MAX - GOAL_MIN + 1);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d)));
			if (!level.isLoaded(top) || !bog(level, top)) {
				continue;
			}
			BlockPos surface = top.below();
			if (!soft(level.getBlockState(surface)) || !level.getFluidState(top).isEmpty()) {
				continue;
			}
			boolean ok = true;
			for (BlockPos p : BlockPos.betweenClosed(surface.offset(-PIT_HALF, 0, -PIT_HALF), surface.offset(PIT_HALF, 0, PIT_HALF))) {
				if (!soft(level.getBlockState(p)) || !level.getBlockState(p.above()).isAir() && !level.getBlockState(p.above()).canBeReplaced()) {
					ok = false;
				}
			}
			if (!ok || level.getBlockState(surface.below(PIT_DEPTH + 1)).isAir()) {
				continue;
			}
			for (BlockPos p : BlockPos.betweenClosed(surface.offset(-PIT_HALF, -(PIT_DEPTH - 1), -PIT_HALF), surface.offset(PIT_HALF, 0, PIT_HALF))) {
				BlockState s = level.getBlockState(p);
				if (!s.isAir() && s.getFluidState().isEmpty()) {
					level.setBlock(p, Creatures.QUICKSAND.defaultBlockState(), 3);
				}
			}
			return surface;
		}
		return null;
	}

	static boolean soft(BlockState s) {
		return s.is(BlockTags.SAND) || s.is(BlockTags.DIRT) || s.is(net.minecraft.world.level.block.Blocks.MUD);
	}
}
