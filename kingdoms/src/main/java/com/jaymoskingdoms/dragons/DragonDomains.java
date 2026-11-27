package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dragon domains (after Dragonforge's dragon realms, RochStudio; lore "Dragon domains"): old ruins in the Overworld that a
 * kind of dragon's breath has worked on, each a placed feature (data/jaymoskingdoms/worldgen) over a feature written here,
 * from the game's own blocks, inside the biomes that were already there (no new dimension):
 * <ul>
 * <li>{@link Kind#VOLCANO}: a burnt-out cone of basalt and blackstone in the badlands, its crater pooled with lava and smoking
 * (Notus).</li>
 * <li>{@link Kind#CITADEL}: a broken keep of packed ice with blue ice towers on the frozen peaks (Boreas).</li>
 * <li>{@link Kind#PILLARS}: a ring of shattered stone pillars on the windswept hills, a lightning rod on the tallest and the
 * ground round their feet fused to glass (Eurus).</li>
 * <li>{@link Kind#ARCHES}: mossy stone arches over jungle and plain, hung with vines (Stegonaut).</li>
 * </ul>
 * Each one is written down where it is made, and its dragon comes to it once dragons come to the Overworld at all
 * ({@link DragonRecords#worldSlain}): when someone is near, a wild one of its kind keeps it, and one slain is replaced after
 * {@link #REPLACE_TICKS}.
 */
public final class DragonDomains {
	public enum Kind {
		VOLCANO, CITADEL, PILLARS, ARCHES;

		@Nullable EntityType<? extends WindDragon> dragon() {
			return switch (this) {
				case VOLCANO -> Dragons.NOTUS_DRAGON;
				case CITADEL -> Dragons.BOREAS_DRAGON;
				case PILLARS -> Dragons.EURUS_DRAGON;
				case ARCHES -> Dragons.STEGONAUT_DRAGON;
			};
		}
	}

	/** One domain: where, what kind, and when its dragon was last seen there (or set down; -1 never). */
	record Domain(BlockPos at, Kind kind, long seen) {
		static final Codec<Domain> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("at").forGetter(Domain::at),
				Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(Domain::kind),
				Codec.LONG.optionalFieldOf("seen", -1L).forGetter(Domain::seen))
			.apply(i, Domain::new));
	}

	static final AttachmentType<List<Domain>> DOMAINS = AttachmentRegistry.create(JaymosKingdoms.id("dragon_domains"),
		b -> b.persistent(Domain.CODEC.listOf()));

	/** How often the domains are looked at (ticks), how near a player must be, how far round it its dragon is looked for. */
	static final int CHECK_EVERY = 200;
	static final double PLAYER_NEAR = 96.0, DRAGON_NEAR = 80.0;
	static final long REPLACE_TICKS = 24000L * 5;

	/** Made on the world-making threads, written down on the server's. */
	private static final Queue<Domain> MADE = new ConcurrentLinkedQueue<>();

	private DragonDomains() {
	}

	static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("dragon_volcano"), Volcano.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("dragon_citadel"), Citadel.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("dragon_pillars"), Pillars.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("dragon_arches"), Arches.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS),
			GenerationStep.Decoration.SURFACE_STRUCTURES, placed("dragon_volcano"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.FROZEN_PEAKS),
			GenerationStep.Decoration.SURFACE_STRUCTURES, placed("dragon_citadel"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST),
			GenerationStep.Decoration.SURFACE_STRUCTURES, placed("dragon_pillars"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS),
			GenerationStep.Decoration.SURFACE_STRUCTURES, placed("dragon_arches"));
		ServerTickEvents.END_SERVER_TICK.register(DragonDomains::tick);
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosKingdoms.id(name));
	}

	// ---- its dragon ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		List<Domain> domains = new ArrayList<>(level.getAttachedOrElse(DOMAINS, List.<Domain>of()));
		boolean changed = false;
		for (Domain made; (made = MADE.poll()) != null; ) {
			domains.add(made);
			changed = true;
		}
		if (DragonRecords.worldSlain()) {
			long now = level.getGameTime();
			for (int i = 0; i < domains.size(); i++) {
				Domain d = domains.get(i);
				EntityType<? extends WindDragon> type = d.kind().dragon();
				if (type == null || !level.isLoaded(d.at()) || !level.areEntitiesLoaded(ChunkPos.pack(d.at())) || !someoneNear(level, d.at())) {
					continue;
				}
				boolean there = !level.getEntitiesOfClass(WindDragon.class, new AABB(d.at()).inflate(DRAGON_NEAR),
					w -> w.getType() == type && w.isAlive() && !w.isTamed()).isEmpty();
				if (there || d.seen() < 0 || now - d.seen() >= REPLACE_TICKS) {
					if (there || bring(level, d, type)) {
						domains.set(i, new Domain(d.at(), d.kind(), now));
						changed = true;
					}
				}
			}
		}
		if (changed) {
			level.setAttached(DOMAINS, List.copyOf(domains));
		}
	}

	private static boolean someoneNear(ServerLevel level, BlockPos at) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(Vec3.atCenterOf(at)) < PLAYER_NEAR * PLAYER_NEAR) {
				return true;
			}
		}
		return false;
	}

	/** Sets a wild one of its kind down at domain {@code d}, keeping it as its home. */
	private static boolean bring(ServerLevel level, Domain d, EntityType<? extends WindDragon> type) {
		WindDragon dragon = type.create(level, EntitySpawnReason.EVENT);
		if (dragon == null) {
			return false;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, d.at());
		double up = dragon.flies() ? 18.0 : 0.0;
		dragon.snapTo(top.getX() + 0.5, top.getY() + up, top.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(top), EntitySpawnReason.EVENT, null);
		if (!level.noCollision(dragon)) {
			dragon.discard();
			return false;
		}
		dragon.home = Vec3.atBottomCenterOf(top);
		if (dragon.flies()) {
			dragon.setFlying(true);
		}
		level.addFreshEntity(dragon);
		JaymosKingdoms.LOGGER.debug("a {} came to its domain at {}", dragon.species(), d.at());
		return true;
	}

	// ---- the landmarks ---------------------------------------------------------------------------------------------------

	private static void found(WorldGenLevel level, BlockPos at, Kind kind) {
		if (level.getLevel().dimension() == Level.OVERWORLD) {
			MADE.add(new Domain(at.immutable(), kind, -1L));
		}
	}

	private static int surface(WorldGenLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
	}

	/** Whether the ground round {@code origin} is open land within {@code spread} blocks of level over {@code radius}. */
	private static boolean even(WorldGenLevel level, BlockPos origin, int radius, int spread) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int dx = -radius; dx <= radius; dx += radius) {
			for (int dz = -radius; dz <= radius; dz += radius) {
				int y = surface(level, origin.getX() + dx, origin.getZ() + dz);
				BlockState under = level.getBlockState(new BlockPos(origin.getX() + dx, y - 1, origin.getZ() + dz));
				if (!under.getFluidState().isEmpty()) {
					return false;
				}
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
		}
		return hi - lo <= spread;
	}

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		BlockState old = level.getBlockState(pos);
		if (!old.is(Blocks.BEDROCK) && !old.hasBlockEntity()) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	/** Fills the column at x, z from just under the ground up to {@code top}. */
	private static void column(WorldGenLevel level, int x, int z, int from, int top, RandomSource random, Block... palette) {
		for (int y = from; y <= top; y++) {
			set(level, new BlockPos(x, y, z), palette[random.nextInt(palette.length)].defaultBlockState());
		}
	}

	/** Where: badlands, one chunk in many. A burnt-out cone with a lava crater and smoking vents. */
	public record Volcano() implements Feature {
		public static final MapCodec<Volcano> CODEC = MapCodec.unit(Volcano::new);
		static final int RADIUS = 11, HEIGHT = 12, CRATER = 3;

		@Override
		public MapCodec<Volcano> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!even(level, origin, RADIUS, 8)) {
				return false;
			}
			int base = surface(level, origin.getX(), origin.getZ());
			int peak = base + HEIGHT;
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > RADIUS) {
						continue;
					}
					int x = origin.getX() + dx, z = origin.getZ() + dz;
					int ground = surface(level, x, z);
					int top = base + Mth.floor(HEIGHT * (1.0 - d / RADIUS)) + random.nextInt(2);
					if (d < CRATER) {
						top = peak - CRATER;
					}
					column(level, x, z, ground - 2, top, random, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.BLACKSTONE, Blocks.TUFF, Blocks.SMOOTH_BASALT);
					if (d < CRATER - 1) {
						set(level, new BlockPos(x, top, z), Blocks.LAVA.defaultBlockState());
					} else if (d < CRATER + 0.5 && random.nextInt(4) == 0) {
						// the vents smoking round the crater
						set(level, new BlockPos(x, top, z), Blocks.MAGMA_BLOCK.defaultBlockState());
						set(level, new BlockPos(x, top + 1, z), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
					} else if (random.nextInt(9) == 0) {
						set(level, new BlockPos(x, top, z), Blocks.MAGMA_BLOCK.defaultBlockState());
					}
				}
			}
			found(level, origin.atY(base), Kind.VOLCANO);
			return true;
		}
	}

	/** Where: frozen peaks, one chunk in many. A broken keep of packed ice with towers of blue ice at its corners. */
	public record Citadel() implements Feature {
		public static final MapCodec<Citadel> CODEC = MapCodec.unit(Citadel::new);
		static final int HALF = 6, WALL_MIN = 3, WALL_MAX = 8, TOWER_MIN = 9, TOWER_MAX = 15, FOOT = 8;

		@Override
		public MapCodec<Citadel> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!even(level, origin, HALF, 7)) {
				return false;
			}
			int base = surface(level, origin.getX(), origin.getZ());
			for (int dx = -HALF; dx <= HALF; dx++) {
				for (int dz = -HALF; dz <= HALF; dz++) {
					int x = origin.getX() + dx, z = origin.getZ() + dz;
					int ground = surface(level, x, z);
					boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
					boolean corner = Math.abs(dx) >= HALF - 1 && Math.abs(dz) >= HALF - 1;
					// its floor: packed snow, levelled at the keep's foot
					column(level, x, z, Math.min(ground, base) - FOOT, base - 1, random, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK);
					for (int y = base; y < ground; y++) {
						set(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
					if (corner) {
						column(level, x, z, base, base + TOWER_MIN + random.nextInt(TOWER_MAX - TOWER_MIN + 1), random, Blocks.BLUE_ICE, Blocks.BLUE_ICE, Blocks.PACKED_ICE);
					} else if (edge && !(Math.abs(dx) <= 1 && dz == -HALF)) {
						// the walls, broken to uneven heights; a gate in the north wall
						column(level, x, z, base, base + WALL_MIN + random.nextInt(WALL_MAX - WALL_MIN + 1), random, Blocks.PACKED_ICE, Blocks.PACKED_ICE, Blocks.ICE);
					} else if (random.nextInt(3) == 0) {
						set(level, new BlockPos(x, base, z), Blocks.SNOW.defaultBlockState());
					}
				}
			}
			found(level, origin.atY(base), Kind.CITADEL);
			return true;
		}
	}

	/** Where: windswept hills, one chunk in many. A ring of shattered pillars, a lightning rod on the tallest, glass at their feet. */
	public record Pillars() implements Feature {
		public static final MapCodec<Pillars> CODEC = MapCodec.unit(Pillars::new);
		static final int RING = 9, COUNT = 8, LOW = 3, HIGH = 12;

		@Override
		public MapCodec<Pillars> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!even(level, origin, RING, 10)) {
				return false;
			}
			int tallest = random.nextInt(COUNT);
			for (int i = 0; i < COUNT; i++) {
				double a = Mth.TWO_PI * i / COUNT;
				int px = origin.getX() + Mth.floor(Math.cos(a) * RING), pz = origin.getZ() + Mth.floor(Math.sin(a) * RING);
				int ground = surface(level, px, pz);
				int height = i == tallest ? HIGH : LOW + random.nextInt(HIGH - LOW - 1);
				for (int dx = 0; dx <= 1; dx++) {
					for (int dz = 0; dz <= 1; dz++) {
						int g = surface(level, px + dx, pz + dz);
						column(level, px + dx, pz + dz, g - 3, ground + height - (random.nextInt(3) == 0 ? 1 : 0), random,
							Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.POLISHED_ANDESITE);
					}
				}
				if (i == tallest) {
					set(level, new BlockPos(px, ground + height + 1, pz), Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
				}
				// the ground at its feet, struck so often it fused
				for (int dx = -2; dx <= 3; dx++) {
					for (int dz = -2; dz <= 3; dz++) {
						if (random.nextInt(3) == 0) {
							int x = px + dx, z = pz + dz;
							BlockPos floor = new BlockPos(x, surface(level, x, z) - 1, z);
							if (!level.getBlockState(floor).is(Blocks.STONE_BRICKS) && !level.getBlockState(floor).is(Blocks.CRACKED_STONE_BRICKS)) {
								set(level, floor, (random.nextBoolean() ? Blocks.GLASS : Blocks.TINTED_GLASS).defaultBlockState());
							}
						}
					}
				}
			}
			found(level, origin.atY(surface(level, origin.getX(), origin.getZ())), Kind.PILLARS);
			return true;
		}
	}

	/** Where: jungles and plains, one chunk in many. One or two great mossy stone arches, hung with vines. */
	public record Arches() implements Feature {
		public static final MapCodec<Arches> CODEC = MapCodec.unit(Arches::new);
		static final int SPAN = 7, RISE = 9, LEG = 1;

		@Override
		public MapCodec<Arches> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!even(level, origin, SPAN, 5)) {
				return false;
			}
			int base = surface(level, origin.getX(), origin.getZ());
			arch(level, origin, base, true, SPAN, RISE, random);
			if (random.nextBoolean()) {
				arch(level, origin, base, false, SPAN - 2, RISE - 2, random);
			}
			found(level, origin.atY(base), Kind.ARCHES);
			return true;
		}

		/** An arch along x (or z) through {@code origin}: two legs and a half-round span between them, two blocks thick. */
		private static void arch(WorldGenLevel level, BlockPos origin, int base, boolean alongX, int span, int rise, RandomSource random) {
			for (int t = -span; t <= span; t++) {
				double f = (double)t / span;
				int top = base + Mth.floor(rise * Math.sqrt(Math.max(0.0, 1.0 - f * f)));
				boolean leg = Math.abs(t) >= span - LEG;
				for (int w = 0; w <= 1; w++) {
					int x = origin.getX() + (alongX ? t : w), z = origin.getZ() + (alongX ? w : t);
					int ground = surface(level, x, z);
					int from = leg ? ground - 3 : top - 1;
					int to = leg ? base + rise / 2 + 1 : top;
					column(level, x, z, from, Math.max(to, top), random, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS,
						Blocks.CRACKED_STONE_BRICKS);
					if (!leg && random.nextInt(3) == 0) {
						// vines hanging off the span's underside
						BlockPos under = new BlockPos(x, top - 2, z);
						if (level.getBlockState(under).isAir()) {
							set(level, under, Blocks.VINE.defaultBlockState().setValue(VineBlock.UP, true));
						}
					}
				}
			}
		}
	}
}
