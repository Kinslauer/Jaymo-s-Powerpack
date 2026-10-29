package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * (0.7.332) Rogue {@link ClockworkGolem}s, sleeping folded in dungeons as mini-bosses. Like the monoliths they are remnants
 * the Aether left scattered over the world, only these want to keep you out:
 * <ul>
 * <li><b>Known dungeons</b> ({@link #LAIRS}: the Aether's Sentry Ruins, strongholds, trial chambers): when the structure
 * is built, its chance in {@link #LAIRS} of a golem on the floor of one of its rooms (any piece but the first, big enough
 * to stand in), from {@code StructureStartMixin}.</li>
 * <li><b>Any other dungeon</b>, any mod's: a structure (not a village) with a monster or trial spawner in it. Looked at
 * once, when a player first comes near one of its spawners: {@link #DUNGEON_CHANCE} of them get a golem near it.</li>
 * <li><b>Monster rooms</b> (a spawner on a cobblestone floor, in no structure): {@link #MONSTER_ROOM_CHANCE} of them.</li>
 * </ul>
 * Only in the Overworld and the Aether.
 */
public final class GolemLairs {
	/** Dungeon structures that may hold a rogue golem, and the chance each one does. */
	static final Map<String, Float> LAIRS = Map.of(
		"jaymospowers:sentry_ruins", 0.75F,
		"minecraft:stronghold", 0.6F,
		"enderscape:stronghold", 0.6F,
		"minecraft:trial_chambers", 0.5F);
	/** The chance any other dungeon (a structure with a spawner) holds one; a monster room. */
	static final float DUNGEON_CHANCE = 0.5F, MONSTER_ROOM_CHANCE = 0.15F;
	/** A spawner is looked at when a player comes this near (blocks); a golem is set down within this of it. */
	static final double LOOK_RANGE = 48.0;
	static final int NEAR_SPAWNER = 5;
	/** A room piece must be at least this wide (x and z) and tall (blocks) to hold a golem. */
	static final int ROOM_MIN_WIDE = 5, ROOM_MIN_TALL = 4;
	/** Spots tried in a room: its middle, then this far out along each side (blocks). */
	static final int ROOM_SPREAD = 3;
	/** The worlds rogue golems are found in. */
	static final Set<ResourceKey<Level>> WORLDS = Set.of(Level.OVERWORLD,
		ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("jaymospowers", "aether")));
	/** How often spawners near players are looked at (ticks). */
	static final int LOOK = 40;

	/** On a monster or trial spawner: it was looked at for a golem. Saved. */
	static final AttachmentType<Boolean> CHECKED = AttachmentRegistry.create(JaymosKingdoms.id("golem_lair_checked"),
		builder -> builder.persistent(Codec.BOOL));
	/** On a structure's start chunk: the structures starting there already rolled for a golem (so each is only once). Saved. */
	static final AttachmentType<List<String>> ROLLED = AttachmentRegistry.create(JaymosKingdoms.id("golem_lair_rolled"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	private static final Map<ResourceKey<Level>, Set<BlockPos>> SPAWNERS = new HashMap<>();

	private GolemLairs() {
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (!WORLDS.contains(level.dimension())) {
				return;
			}
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				if ((be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) && !be.hasAttached(CHECKED)) {
					SPAWNERS.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(be.getBlockPos().immutable());
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(GolemLairs::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SPAWNERS.clear());
	}

	// ---- dungeon structures (at worldgen) -------------------------------------------------------------------------------

	/** After {@code start} is built in the chunk {@code chunk}: its golem, if it has one and it stands in this chunk. */
	public static void place(WorldGenLevel level, StructureStart start, BoundingBox chunk) {
		Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
		Float chance = id == null ? null : LAIRS.get(id.toString());
		List<StructurePiece> pieces = start.getPieces();
		if (chance == null || pieces.size() < 2) {
			return;
		}
		// the same roll in every chunk the structure covers, so it is made once
		RandomSource random = RandomSource.create(level.getSeed() ^ start.getChunkPos().pack() * 0x9E3779B97F4A7C15L);
		if (random.nextFloat() >= chance) {
			return;
		}
		List<BoundingBox> rooms = new ArrayList<>();
		for (StructurePiece piece : pieces.subList(1, pieces.size())) {
			BoundingBox box = piece.getBoundingBox();
			if (box.getXSpan() >= ROOM_MIN_WIDE && box.getZSpan() >= ROOM_MIN_WIDE && box.getYSpan() >= ROOM_MIN_TALL) {
				rooms.add(box);
			}
		}
		if (rooms.isEmpty()) {
			return;
		}
		BoundingBox room = rooms.get(random.nextInt(rooms.size()));
		BlockPos mid = room.getCenter();
		if (!chunk.isInside(mid.getX(), chunk.minY(), mid.getZ())) {
			return;
		}
		int[][] tries = {{0, 0}, {ROOM_SPREAD, 0}, {-ROOM_SPREAD, 0}, {0, ROOM_SPREAD}, {0, -ROOM_SPREAD}};
		for (int[] t : tries) {
			int x = mid.getX() + t[0], z = mid.getZ() + t[1];
			if (!chunk.isInside(x, chunk.minY(), z)) {
				continue;
			}
			for (int y = room.minY(); y <= room.maxY() - 2; y++) {
				BlockPos at = new BlockPos(x, y, z);
				if (standable(level, at) && spawn(level, at, random.nextFloat() * 360.0F)) {
					return;
				}
			}
		}
	}

	// ---- monster rooms (when a player first comes near) -----------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.overworld().getGameTime() % LOOK != 0) {
			return;
		}
		// (a copy: looking can load a chunk, which may add a world)
		for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> e : new ArrayList<>(SPAWNERS.entrySet())) {
			ServerLevel level = server.getLevel(e.getKey());
			if (level == null) {
				e.getValue().clear();
				continue;
			}
			for (BlockPos pos : new ArrayList<>(e.getValue())) {
				if (!level.isLoaded(pos)) {
					e.getValue().remove(pos);
					continue;
				}
				if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, LOOK_RANGE, false) == null) {
					continue;
				}
				e.getValue().remove(pos);
				look(level, pos);
			}
		}
	}

	/** A spawner a player has come near: in a dungeon not yet rolled, or a monster room, maybe a golem. */
	private static void look(ServerLevel level, BlockPos pos) {
		BlockEntity spawner = level.getBlockEntity(pos);
		if (!(spawner instanceof SpawnerBlockEntity || spawner instanceof TrialSpawnerBlockEntity) || spawner.hasAttached(CHECKED)) {
			return;
		}
		spawner.setAttached(CHECKED, true);
		spawner.setChanged();
		RandomSource random = level.getRandom();
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (Map.Entry<Structure, LongSet> e : level.getChunkAt(pos).getAllReferences().entrySet()) {
			Holder<Structure> holder = registry.wrapAsHolder(e.getKey());
			Identifier id = registry.getKey(e.getKey());
			if (id == null || Seats.village(holder) || SharedPools.village(holder)) {
				continue;
			}
			for (long ref : e.getValue()) {
				ChunkPos from = ChunkPos.unpack(ref);
				if (!level.hasChunk(from.x(), from.z())) {
					continue;
				}
				LevelChunk origin = level.getChunk(from.x(), from.z());
				StructureStart start = origin.getStartForStructure(e.getKey());
				if (start == null || !start.isValid() || start.getPieces().stream().noneMatch(p -> p.getBoundingBox().isInside(pos))) {
					continue;
				}
				// a dungeon: rolled once (the known ones were rolled when they were built)
				List<String> rolled = origin.getAttachedOrElse(ROLLED, List.of());
				if (LAIRS.containsKey(id.toString()) || rolled.contains(id.toString())) {
					return;
				}
				List<String> more = new ArrayList<>(rolled);
				more.add(id.toString());
				origin.setAttached(ROLLED, more);
				if (random.nextFloat() < DUNGEON_CHANCE) {
					near(level, pos, random);
				}
				return;
			}
		}
		BlockState floor = level.getBlockState(pos.below());
		if ((floor.is(Blocks.COBBLESTONE) || floor.is(Blocks.MOSSY_COBBLESTONE)) && random.nextFloat() < MONSTER_ROOM_CHANCE) {
			near(level, pos, random);
		}
	}

	/** A golem on open floor near {@code spawner} (not where it stands), if there's room. */
	private static void near(ServerLevel level, BlockPos spawner, RandomSource random) {
		List<BlockPos> free = new ArrayList<>();
		for (int dy = -2; dy <= 1; dy++) {
			for (int dx = -NEAR_SPAWNER; dx <= NEAR_SPAWNER; dx++) {
				for (int dz = -NEAR_SPAWNER; dz <= NEAR_SPAWNER; dz++) {
					BlockPos at = spawner.offset(dx, dy, dz);
					if (Math.abs(dx) + Math.abs(dz) >= 2 && standable(level, at)) {
						free.add(at);
					}
				}
			}
		}
		while (!free.isEmpty() && !spawn(level, free.remove(random.nextInt(free.size())), random.nextFloat() * 360.0F)) {
			// try the next spot
		}
	}

	// ---- both ----------------------------------------------------------------------------------------------------------

	/** Room for a golem's feet at {@code at}: three blocks of open space on a sturdy floor. */
	private static boolean standable(WorldGenLevel level, BlockPos at) {
		return open(level, at) && open(level, at.above()) && open(level, at.above(2))
			&& level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP);
	}

	private static boolean open(WorldGenLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		return state.getCollisionShape(level, at).isEmpty() && state.getFluidState().isEmpty();
	}

	/** A rogue golem, folded, at {@code at}; false if it doesn't fit there. */
	private static boolean spawn(WorldGenLevel level, BlockPos at, float yaw) {
		ClockworkGolem golem = ClockworkGolem.TYPE.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (golem == null) {
			return false;
		}
		golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		golem.yHeadRot = yaw;
		golem.yBodyRot = yaw;
		if (!level.noCollision(golem)) {
			return false;
		}
		golem.makeRogue();
		level.addFreshEntityWithPassengers(golem);
		return true;
	}
}
