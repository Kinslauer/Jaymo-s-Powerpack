package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.Captives;
import com.jaymoskingdoms.Doubles;
import com.jaymoskingdoms.Factions;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * Where the creatures of patch 9 are met. Nothing here is worldgen JSON: every {@link #EVERY} ticks a few spots near each
 * player are looked at, and each spot is rolled once for good ({@link #ROLLED}), so a place is the same however often you
 * return. It happens only in the tiers the player has opened.
 * <ul>
 * <li><b>Ruins</b> (a Grim ruin or any structure with "ruin" in its name): shadows in the dark of them, and sometimes a
 * basilisk's den with its statues ({@link Statues}).</li>
 * <li><b>Mineshafts, strongholds, trial chambers:</b> a clear cube in the corridors.</li>
 * <li><b>Hills, taiga, savanna:</b> a digger warren ringed with pits ({@link Warrens}).</li>
 * <li><b>Near a settlement:</b> a cellar with a stolen life tied up in it ({@link Doubles}); villagers found shut in under a
 * Grim ruin or village are such lives too.</li>
 * <li><b>Sea cliffs:</b> a harpy singing on the edge.</li>
 * <li><b>Cave ceilings:</b> piercers hanging overhead, and in the deep a cloak flier.</li>
 * </ul>
 */
public final class Haunts {
	static final int EVERY = 100, WINDOW = 4;
	/** Chances a roll comes up: a ruin's shadows, its den, a structure's cube, a hill's warren, a cellar near a settlement, a cliff's harpy. */
	static final float SHADOWS = 0.6F, DEN = 0.2F, CUBE = 0.45F, WARREN = 0.06F, CELLAR = 0.05F, HARPY = 0.12F;
	/** Spawn chances a scan, and the most of each near the player (within 56 blocks). */
	static final float CEILING = 0.4F, DEEP = 0.12F;
	static final int PIERCERS_MOST = 5, FLIERS_MOST = 1, HARPIES_MOST = 2;
	/** A settlement's quiet ground: nearer than this (blocks) to its seat no warren is dug; a cellar is dug between these. */
	static final double WARREN_CLEAR = 110.0, CELLAR_FROM = 50.0, CELLAR_TO = 140.0;

	static final AttachmentType<List<Long>> ROLLED = AttachmentRegistry.create(com.jaymoskingdoms.JaymosKingdoms.id("haunts_rolled"),
		b -> b.persistent(Codec.LONG.listOf()));
	private static @Nullable Set<Long> rolled;

	private Haunts() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Haunts::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> rolled = null);
	}

	private static Set<Long> rolled(ServerLevel level) {
		if (rolled == null) {
			rolled = new HashSet<>(level.getAttachedOrElse(ROLLED, List.<Long>of()));
		}
		return rolled;
	}

	/** Marks {@code key} rolled; false if it already was. */
	private static boolean roll(ServerLevel level, long key) {
		Set<Long> set = rolled(level);
		if (!set.add(key)) {
			return false;
		}
		level.setAttached(ROLLED, new ArrayList<>(set));
		return true;
	}

	private static long key(int x, int z, int kind) {
		return (((long) x << 32) ^ (z & 0xFFFFFFFFL)) * 16L + kind;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			try {
				structures(level, player);
				ground(level, player);
				ceilings(level, player);
				buried(level, player);
			} catch (RuntimeException e) {
				com.jaymoskingdoms.JaymosKingdoms.LOGGER.debug("haunts scan failed", e);
			}
		}
	}

	// ---- structures ---------------------------------------------------------------------------------------------------

	private static void structures(ServerLevel level, ServerPlayer player) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		ChunkPos middle = player.chunkPosition();
		RandomSource random = level.getRandom();
		int cx = middle.x() + random.nextInt(2 * WINDOW + 1) - WINDOW, cz = middle.z() + random.nextInt(2 * WINDOW + 1) - WINDOW;
		ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
		if (chunk == null) {
			return;
		}
		for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
			Identifier id = registry.getKey(e.getKey());
			if (id == null) {
				continue;
			}
			String path = id.getPath();
			boolean ruin = id.getNamespace().equals("grim_kingdoms") || path.contains("ruin");
			boolean halls = path.contains("mineshaft") || path.contains("stronghold") || path.contains("trial_chamber");
			if (!ruin && !halls) {
				continue;
			}
			if (ruin && !Creatures.open(player, 1) || halls && !Creatures.open(player, 1)) {
				continue;
			}
			for (long ref : e.getValue()) {
				ChunkPos from = ChunkPos.unpack(ref);
				ChunkAccess startChunk = level.getChunkSource().getChunkNow(from.x(), from.z());
				if (startChunk == null) {
					continue;
				}
				StructureStart start = startChunk.getStartForStructure(e.getKey());
				if (start == null || !start.isValid() || !roll(level, ref * 16L + (ruin ? 2 : 3) + (path.hashCode() & 0x7FFF) * 17L)) {
					continue;
				}
				if (ruin) {
					ruin(level, start, random);
				} else {
					cubes(level, start, random);
				}
			}
		}
	}

	private static void ruin(ServerLevel level, StructureStart start, RandomSource random) {
		if (random.nextFloat() < SHADOWS) {
			int n = 2 + random.nextInt(2);
			for (int i = 0; i < n; i++) {
				BlockPos spot = inside(level, start, random, 3, 7);
				if (spot != null) {
					spawn(level, Creatures.SHADOW, spot, true);
				}
			}
		}
		if (random.nextFloat() < DEN) {
			BoundingBox box = start.getBoundingBox();
			Statues.den(level, new BlockPos(box.maxX() + 6, box.minY(), box.getCenter().getZ()));
		}
	}

	private static void cubes(ServerLevel level, StructureStart start, RandomSource random) {
		if (random.nextFloat() >= CUBE) {
			return;
		}
		int n = 1 + random.nextInt(2);
		for (int i = 0; i < n; i++) {
			BlockPos spot = inside(level, start, random, 3, 15);
			if (spot != null) {
				spawn(level, Creatures.CLEAR_CUBE, spot, true);
			}
		}
	}

	/** Open floor inside one of {@code start}'s pieces with {@code height} cells of air, dimmer than {@code light}; or null. */
	private static @Nullable BlockPos inside(ServerLevel level, StructureStart start, RandomSource random, int height, int light) {
		List<StructurePiece> pieces = start.getPieces();
		if (pieces.isEmpty()) {
			return null;
		}
		for (int tries = 0; tries < 24; tries++) {
			BoundingBox box = pieces.get(random.nextInt(pieces.size())).getBoundingBox();
			BlockPos p = new BlockPos(box.minX() + random.nextInt(box.getXSpan()), box.minY() + random.nextInt(box.getYSpan()), box.minZ() + random.nextInt(box.getZSpan()));
			if (!level.hasChunkAt(p)) {
				continue;
			}
			while (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isAir() && p.getY() > box.minY() - 1) {
				p = p.below();
			}
			if (!level.getBlockState(p.below()).isSolid()) {
				continue;
			}
			boolean room = true;
			for (int dy = 0; dy < height; dy++) {
				room &= level.getBlockState(p.above(dy)).isAir();
			}
			if (room && level.getBrightness(LightLayer.BLOCK, p) <= light && !level.canSeeSky(p)) {
				return p;
			}
		}
		return null;
	}

	// ---- the ground ------------------------------------------------------------------------------------------------------

	private static void ground(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		int cx = player.chunkPosition().x() + random.nextInt(13) - 6, cz = player.chunkPosition().z() + random.nextInt(13) - 6;
		if (!level.hasChunk(cx, cz)) {
			return;
		}
		BlockPos at = new ChunkPos(cx, cz).getMiddleBlockPosition(0).offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, at);
		if (!level.getFluidState(top.below()).isEmpty()) {
			return;
		}
		var biome = level.getBiome(top);
		com.jaymoskingdoms.Faction seat = Captives.nearestSettlement(level.getServer(), top);
		double seatD = seat == null ? Double.MAX_VALUE : Math.sqrt(seat.seat().distSqr(top));
		boolean warrenGround = biome.is(BiomeTags.IS_HILL) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_SAVANNA);
		if (warrenGround && seatD > WARREN_CLEAR && Creatures.open(player, 0) && random.nextFloat() < WARREN * 4 && roll(level, key(cx, cz, 4))) {
			Warrens.dig(level, top);
			return;
		}
		if (seat != null && seatD > CELLAR_FROM && seatD < CELLAR_TO && Creatures.open(player, 0) && random.nextFloat() < CELLAR * 4 && roll(level, key(cx, cz, 5))) {
			Doubles.cellar(level, top);
			return;
		}
		if (Creatures.open(player, 1) && cliff(level, top) && random.nextFloat() < HARPY * 4 && roll(level, key(cx, cz, 6))
			&& level.getEntitiesOfClass(Harpy.class, player.getBoundingBox().inflate(64.0)).size() < HARPIES_MOST) {
			spawn(level, Creatures.HARPY, top, false);
		}
	}

	/** High ground (six above the sea) with the sea within a short way. */
	private static boolean cliff(ServerLevel level, BlockPos top) {
		int sea = level.getSeaLevel();
		if (top.getY() < sea + 6) {
			return false;
		}
		for (int a = 0; a < 8; a++) {
			double angle = a * Math.PI / 4.0;
			for (int r : new int[] {6, 10, 14}) {
				BlockPos p = new BlockPos(top.getX() + (int) Math.round(Math.cos(angle) * r), sea - 1, top.getZ() + (int) Math.round(Math.sin(angle) * r));
				if (level.hasChunkAt(p) && !level.getFluidState(p).isEmpty() && level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) {
					return true;
				}
			}
		}
		return false;
	}

	// ---- ceilings ------------------------------------------------------------------------------------------------------------

	private static void ceilings(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		if (!Creatures.open(player, 1) || player.getY() > 48.0 || level.canSeeSky(player.blockPosition()) || random.nextFloat() >= CEILING) {
			return;
		}
		boolean deep = Creatures.open(player, 2) && player.getY() < 10.0 && random.nextFloat() < DEEP / CEILING;
		for (int tries = 0; tries < 16; tries++) {
			BlockPos p = player.blockPosition().offset(random.nextInt(41) - 20, random.nextInt(17) - 8, random.nextInt(41) - 20);
			if (p.distSqr(player.blockPosition()) < 100.0 || !level.hasChunkAt(p) || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isSolid()
				|| level.getBrightness(LightLayer.BLOCK, p) > 5 || level.canSeeSky(p)) {
				continue;
			}
			boolean drop = true;
			for (int dy = 1; dy <= 3; dy++) {
				drop &= level.getBlockState(p.below(dy)).isAir();
			}
			if (!drop) {
				continue;
			}
			AABBCount counts = new AABBCount(level, player);
			if (deep) {
				if (counts.fliers < FLIERS_MOST) {
					hang(level, Creatures.CLOAK_FLIER, p, 0.5);
				}
			} else if (counts.piercers < PIERCERS_MOST) {
				hang(level, Creatures.PIERCER, p, 1.0);
			}
			return;
		}
	}

	private record AABBCount(int piercers, int fliers) {
		AABBCount(ServerLevel level, ServerPlayer player) {
			this(level.getEntitiesOfClass(Piercer.class, player.getBoundingBox().inflate(56.0)).size(),
				level.getEntitiesOfClass(CloakFlier.class, player.getBoundingBox().inflate(64.0)).size());
		}
	}

	private static <T extends Mob> void hang(ServerLevel level, EntityType<T> type, BlockPos open, double height) {
		T mob = type.create(level, EntitySpawnReason.NATURAL);
		if (mob == null) {
			return;
		}
		mob.snapTo(open.getX() + 0.5, open.getY() + 1.0 - height, open.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.setNoGravity(true);
		level.addFreshEntity(mob);
	}

	// ---- the buried ------------------------------------------------------------------------------------------------------------

	/** Villagers shut in under a Grim ruin or village are stolen lives. */
	private static void buried(ServerLevel level, ServerPlayer player) {
		if (!Creatures.open(player, 0)) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(40.0), x -> x.isAlive() && !x.isBaby()
			&& !x.hasAttached(Doubles.STOLEN) && !x.hasAttached(Doubles.COPY))) {
			BlockPos p = v.blockPosition();
			if (level.canSeeSky(p) || p.getY() > level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()) - 4 || level.structureManager().getAllStructuresAt(p).isEmpty()) {
				continue;
			}
			boolean grim = false;
			Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			for (Structure s : level.structureManager().getAllStructuresAt(p).keySet()) {
				Identifier id = registry.getKey(s);
				grim |= id != null && (id.getNamespace().equals("grim_kingdoms") || id.getPath().contains("village"));
			}
			if (grim) {
				Doubles.markBuried(level, v);
			}
		}
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------------------

	private static void spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos at, boolean keep) {
		Mob mob = type.create(level, EntitySpawnReason.NATURAL);
		if (mob == null) {
			return;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, null);
		if (keep) {
			mob.setPersistenceRequired();
		}
		level.addFreshEntity(mob);
	}
}
