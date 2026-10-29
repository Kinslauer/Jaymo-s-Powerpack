package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * (0.7.330) Rogue {@link ClockworkGolem}s, sleeping folded in dungeons as mini-bosses:
 * <ul>
 * <li><b>Dungeon structures</b> ({@link #LAIRS}: the Aether's Sentry Ruins, strongholds, trial chambers): when the
 * structure is built, one in {@link #LAIRS}' chance of them gets a golem on the floor of one of its rooms (any piece but the
 * first, big enough to stand in), from {@code StructureStartMixin}.</li>
 * <li><b>Monster rooms</b> (a spawner on a cobblestone floor, as {@link Captives} finds them): looked at once when a player
 * first comes near, and {@link #MONSTER_ROOM_CHANCE} of them get one beside the spawner.</li>
 * </ul>
 */
public final class GolemLairs {
	/** Dungeon structures that may hold a rogue golem, and the chance each one does. */
	static final Map<String, Float> LAIRS = Map.of(
		"jaymospowers:sentry_ruins", 0.75F,
		"minecraft:stronghold", 0.6F,
		"enderscape:stronghold", 0.6F,
		"minecraft:trial_chambers", 0.5F);
	/** The chance a monster room holds one. */
	static final float MONSTER_ROOM_CHANCE = 0.15F;
	/** A monster room is looked at when a player comes this near (blocks). */
	static final double LOOK_RANGE = 48.0;
	/** A room piece must be at least this wide (x and z) and tall (blocks) to hold a golem. */
	static final int ROOM_MIN_WIDE = 5, ROOM_MIN_TALL = 4;
	/** Spots tried in a room: its middle, then this far out along each side (blocks). */
	static final int ROOM_SPREAD = 3;
	/** How often monster rooms near players are looked at (ticks). */
	static final int LOOK = 40;

	/** On a monster spawner: it was looked at for a golem (so each monster room is only once). Saved. */
	static final AttachmentType<Boolean> CHECKED = AttachmentRegistry.create(JaymosKingdoms.id("golem_lair_checked"),
		builder -> builder.persistent(Codec.BOOL));

	private static final Set<BlockPos> SPAWNERS = new HashSet<>();

	private GolemLairs() {
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != Level.OVERWORLD) {
				return;
			}
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				if (be instanceof SpawnerBlockEntity && !be.hasAttached(CHECKED)) {
					SPAWNERS.add(be.getBlockPos().immutable());
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
		ServerLevel level = server.overworld();
		if (level.getGameTime() % LOOK != 0 || SPAWNERS.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (BlockPos pos : new ArrayList<>(SPAWNERS)) {
			if (!level.isLoaded(pos)) {
				SPAWNERS.remove(pos);
				continue;
			}
			if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, LOOK_RANGE, false) == null) {
				continue;
			}
			SPAWNERS.remove(pos);
			if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) || spawner.hasAttached(CHECKED)) {
				continue;
			}
			spawner.setAttached(CHECKED, true);
			spawner.setChanged();
			BlockState floor = level.getBlockState(pos.below());
			if (!(floor.is(Blocks.COBBLESTONE) || floor.is(Blocks.MOSSY_COBBLESTONE)) || random.nextFloat() >= MONSTER_ROOM_CHANCE) {
				continue;
			}
			List<BlockPos> free = new ArrayList<>();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos at = pos.offset(dx, 0, dz);
					if (Math.abs(dx) + Math.abs(dz) >= 2 && standable(level, at)) {
						free.add(at);
					}
				}
			}
			while (!free.isEmpty() && !spawn(level, free.remove(random.nextInt(free.size())), random.nextFloat() * 360.0F)) {
				// try the next spot
			}
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
