package com.jaymospowers.power.xenoform.nadir;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Nadir's Reality (sprint + C), the biome wave of the Hand of God mod (ideas only; it is All Rights Reserved): a ring of
 * violet runs out over the ground from Nadir, and behind it the land is somewhere else: desert, snowfield, cherry grove,
 * mushroom isle, badlands, pale garden or jungle (the biome itself, so the grass, the sky and the fog change, and the
 * top of the ground and its small plants with it). Andy, 2026-11-25: it is "just a visual effect", so it all comes back:
 * after {@link #LASTS}, when Nadir casts it again, or when the server stops. Only what is still as the wave left it is
 * put back (a block someone dug or built over since is left alone). One wave at a time in one place.
 */
public final class NadirReality {
	// ---- tuning ----
	/** How far the wave runs (blocks) and how fast (blocks a tick); how long the land stays changed (ticks). */
	static final int RADIUS = 40, LASTS = 6000;
	static final double SPEED = 1.0;
	/** The cooldown (ticks) once the land comes back. */
	public static final int COOLDOWN = 1200;
	/** The biomes are rewritten every this many ticks as the wave runs (a biome cell is 4 blocks across). */
	static final int BIOME_EVERY = 4;
	/** The wave's colour. */
	static final int WAVE = 0xB26BFF;

	/** What the land becomes: the biome, the top of the ground, a small plant and how often it grows. */
	private record Land(ResourceKey<Biome> biome, BlockState ground, @Nullable BlockState plant, float plants, @Nullable BlockState plant2) {
	}

	private static final List<Land> LANDS = List.of(
		new Land(Biomes.DESERT, Blocks.SAND.defaultBlockState(), Blocks.DEAD_BUSH.defaultBlockState(), 0.03F, Blocks.SHORT_DRY_GRASS.defaultBlockState()),
		new Land(Biomes.SNOWY_PLAINS, Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SNOW.defaultBlockState(), 0.6F, null),
		new Land(Biomes.CHERRY_GROVE, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.PINK_PETALS.defaultBlockState(), 0.25F, Blocks.SHORT_GRASS.defaultBlockState()),
		new Land(Biomes.MUSHROOM_FIELDS, Blocks.MYCELIUM.defaultBlockState(), Blocks.RED_MUSHROOM.defaultBlockState(), 0.04F, Blocks.BROWN_MUSHROOM.defaultBlockState()),
		new Land(Biomes.BADLANDS, Blocks.RED_SAND.defaultBlockState(), Blocks.DEAD_BUSH.defaultBlockState(), 0.03F, null),
		new Land(Biomes.PALE_GARDEN, Blocks.PALE_MOSS_BLOCK.defaultBlockState(), Blocks.PALE_MOSS_CARPET.defaultBlockState(), 0.3F, null),
		new Land(Biomes.JUNGLE, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.FERN.defaultBlockState(), 0.2F, Blocks.SHORT_GRASS.defaultBlockState()));

	private static final class Wave {
		final UUID owner;
		final ServerLevel level;
		final Vec3 center;
		final Land land;
		final Holder<Biome> biome;
		final long start;
		/** Blocks changed: their place, what was there, what the wave put. */
		final Map<Long, BlockState[]> blocks = new HashMap<>();
		/** Biome cells changed (packed quart x, y, z), and what they were; and the chunks they're in. */
		final Map<Long, Holder<Biome>> biomes = new HashMap<>();
		final Set<Long> chunks = new HashSet<>();
		int reached;

		Wave(UUID owner, ServerLevel level, Vec3 center, Land land, Holder<Biome> biome, long start) {
			this.owner = owner;
			this.level = level;
			this.center = center;
			this.land = land;
			this.biome = biome;
			this.start = start;
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private NadirReality() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(NadirReality::tick);
		// nothing of it is kept: put it all back before the world is saved for the last time
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Wave w : WAVES) {
				restore(w);
			}
			WAVES.clear();
		});
	}

	/** Sprint + C: sends the wave out, or (one already out) brings the land back. Returns the message key to show, or null. */
	public static @Nullable String cast(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave w = it.next();
			if (w.owner.equals(player.getUUID())) {
				it.remove();
				restore(w);
				NEXT.put(player.getUUID(), now + COOLDOWN);
				return null;
			}
		}
		if (now < NEXT.getOrDefault(player.getUUID(), 0L)) {
			return "reality_wait";
		}
		for (Wave w : WAVES) {
			if (w.level == level && w.center.distanceToSqr(player.position()) < 4.0 * RADIUS * RADIUS) {
				return "reality_busy";
			}
		}
		RandomSource random = player.getRandom();
		Land land = LANDS.get(random.nextInt(LANDS.size()));
		Holder<Biome> biome = level.registryAccess().lookupOrThrow(Registries.BIOME).get(land.biome()).orElse(null);
		if (biome == null) {
			return "reality_wait";
		}
		WAVES.add(new Wave(player.getUUID(), level, player.position(), land, biome, now));
		Vec3 at = player.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.6F, 0.6F);
		MagicFx.blast(level, at.add(0.0, 1.0, 0.0), 2.5F, WAVE);
		Shakes.around(level, at, 32.0, 0.3F);
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (WAVES.isEmpty()) {
			return;
		}
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave w = it.next();
			long age = w.level.getGameTime() - w.start;
			if (age >= LASTS) {
				it.remove();
				restore(w);
				NEXT.put(w.owner, w.level.getGameTime() + COOLDOWN);
				continue;
			}
			int radius = (int)Math.min(RADIUS, age * SPEED);
			if (radius <= w.reached) {
				continue;
			}
			for (int r = w.reached + 1; r <= radius; r++) {
				ground(w, r);
			}
			if (radius % BIOME_EVERY == 0 || radius == RADIUS) {
				biomes(w, radius);
			}
			if (age % 2 == 0) {
				MagicFx.ring(w.level, w.center.add(0.0, 0.3, 0.0), null, radius, WAVE, 6);
			}
			if (age % 8 == 0) {
				w.level.playSound(null, w.center.x, w.center.y, w.center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4F, 0.4F + radius / (float)RADIUS);
			}
			w.reached = radius;
		}
	}

	/** The ground the wave front ({@code r} blocks out) passes over: its top and small plants become the new land's. */
	private static void ground(Wave w, int r) {
		ServerLevel level = w.level;
		RandomSource random = level.getRandom();
		int cx = (int)Math.floor(w.center.x), cz = (int)Math.floor(w.center.z);
		double inner = (r - 1) * (r - 1), outer = r * r;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = dx * dx + dz * dz;
				if (d < inner || d >= outer) {
					continue;
				}
				int x = cx + dx, z = cz + dz;
				if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				pos.set(x, top, z);
				BlockState old = level.getBlockState(pos);
				if (!old.is(BlockTags.DIRT) && !old.is(BlockTags.SAND) && !old.is(Blocks.SNOW_BLOCK)) {
					continue;
				}
				set(w, pos.immutable(), old, w.land.ground());
				BlockPos above = pos.above();
				BlockState over = level.getBlockState(above);
				if (!over.getFluidState().isEmpty() || !(over.canBeReplaced() || over.is(BlockTags.SMALL_FLOWERS))) {
					continue;
				}
				BlockState plant = Blocks.AIR.defaultBlockState();
				if (w.land.plant() != null && random.nextFloat() < w.land.plants()) {
					plant = w.land.plant2() != null && random.nextBoolean() ? w.land.plant2() : w.land.plant();
				}
				set(w, above, over, plant);
			}
		}
	}

	private static void set(Wave w, BlockPos pos, BlockState old, BlockState now) {
		if (old == now) {
			return;
		}
		w.blocks.putIfAbsent(pos.asLong(), new BlockState[] {old, now});
		// no neighbour updates: nothing pops off or flows, and it all goes back as it was
		w.level.setBlock(pos, now, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** Rewrites every biome cell within {@code r} to the new land's, keeping what each was, and sends the chunks on. */
	private static void biomes(Wave w, int r) {
		ServerLevel level = w.level;
		int minX = SectionPos.blockToSectionCoord(w.center.x - r), maxX = SectionPos.blockToSectionCoord(w.center.x + r);
		int minZ = SectionPos.blockToSectionCoord(w.center.z - r), maxZ = SectionPos.blockToSectionCoord(w.center.z + r);
		double reach = (double)r * r;
		List<ChunkAccess> changed = new ArrayList<>();
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
				if (chunk == null) {
					continue;
				}
				boolean[] any = {false};
				chunk.fillBiomesFromNoise((qx, qy, qz) -> {
					Holder<Biome> was = chunk.getNoiseBiome(qx, qy, qz);
					double bx = QuartPos.toBlock(qx) + 2.0 - w.center.x, bz = QuartPos.toBlock(qz) + 2.0 - w.center.z;
					if (bx * bx + bz * bz > reach) {
						return was;
					}
					long key = BlockPos.asLong(qx, qy, qz);
					if (!w.biomes.containsKey(key)) {
						w.biomes.put(key, was);
						any[0] = true;
					}
					return w.biome;
				});
				if (any[0]) {
					w.chunks.add(ChunkPos.pack(x, z));
					changed.add(chunk);
				}
			}
		}
		if (!changed.isEmpty()) {
			level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
		}
	}

	/** The land comes back: the blocks still as the wave left them, and every biome cell. */
	private static void restore(Wave w) {
		ServerLevel level = w.level;
		for (Map.Entry<Long, BlockState[]> e : w.blocks.entrySet()) {
			BlockPos pos = BlockPos.of(e.getKey());
			if (level.getBlockState(pos) == e.getValue()[1]) {
				level.setBlock(pos, e.getValue()[0], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
		List<ChunkAccess> changed = new ArrayList<>();
		for (long packed : w.chunks) {
			ChunkAccess chunk = level.getChunk(ChunkPos.getX(packed), ChunkPos.getZ(packed));
			chunk.fillBiomesFromNoise((qx, qy, qz) -> {
				Holder<Biome> was = w.biomes.get(BlockPos.asLong(qx, qy, qz));
				return was != null ? was : chunk.getNoiseBiome(qx, qy, qz);
			});
			chunk.markUnsaved();
			changed.add(chunk);
		}
		if (!changed.isEmpty()) {
			level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
		}
		Vec3 at = w.center;
		MagicFx.ring(level, at.add(0.0, 0.3, 0.0), null, RADIUS, WAVE, 20);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.6F);
	}
}
