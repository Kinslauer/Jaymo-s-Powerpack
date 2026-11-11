package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * The minotaur's labyrinths. Andy: the Epic dungeons "should spawn the minotaur instead of the slider dungeon". The great
 * dungeons under the Overworld (the structure tag {@link #ALWAYS}: Epic Structures: Dungeons' large ones) each hold one
 * minotaur, and some of the middling ones ({@link #SOME}, {@link #SOME_CHANCE} of them) do too. Nothing of the dungeon is
 * changed: when a player first comes near a labyrinth's biggest hall, the minotaur is stood in it, once for that
 * labyrinth, and it keeps to the hall. The tags list their structures as optional, so without the mod there are none.
 */
public final class Labyrinths {
	// ---- tuning ----
	/** How often the players are looked over for a labyrinth near them (ticks). */
	static final int EVERY = 40;
	/** How many chunks out from a player a labyrinth is looked for. */
	static final int LOOK_CHUNKS = 2;
	/** How near a hall's middle a player comes before its minotaur is stood in it (blocks, flat). */
	static final int WAKE_RANGE = 40;
	/** Of the middling dungeons, how many hold a minotaur. */
	static final float SOME_CHANCE = 0.35F;
	/** How many of a labyrinth's rooms, biggest first, are tried for a place to stand it. */
	static final int HALLS_TRIED = 4;
	/** The least room a hall must have (blocks across, and high). */
	static final int HALL_MIN_SPAN = 7, HALL_MIN_HEIGHT = 5;
	/** Clear floor kept around it where it is stood (blocks), and how near that spot it keeps. */
	static final double ELBOW_ROOM = 1.0;
	static final int HOME_RADIUS = 14;

	/** Dungeons that each hold a minotaur, and dungeons of which only some do. */
	public static final TagKey<Structure> ALWAYS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("minotaur_labyrinths"));
	public static final TagKey<Structure> SOME = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("minotaur_labyrinths_some"));

	/** On a labyrinth's minotaur. */
	public static final String TAG = "jaymoskingdoms.labyrinth_minotaur";

	/** The labyrinths already seen to (their start chunks, packed), with a minotaur or without. Saved on the Overworld. */
	static final AttachmentType<List<Long>> SETTLED = AttachmentRegistry.create(JaymosKingdoms.id("labyrinths_settled"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private Labyrinths() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Labyrinths::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0 || Beasts.MINOTAUR == null) {
			return;
		}
		ServerLevel level = Factions.level(server);
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		if (structures.get(ALWAYS).isEmpty() && structures.get(SOME).isEmpty()) {
			return;
		}
		Set<Long> settled = null;
		Set<Long> looked = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			ChunkPos middle = player.chunkPosition();
			for (int dx = -LOOK_CHUNKS; dx <= LOOK_CHUNKS; dx++) {
				for (int dz = -LOOK_CHUNKS; dz <= LOOK_CHUNKS; dz++) {
					int cx = middle.x() + dx, cz = middle.z() + dz;
					if (!level.hasChunk(cx, cz)) {
						continue;
					}
					for (StructureStart start : level.structureManager().startsForStructure(cx, cz, s -> labyrinth(structures, s))) {
						long key = start.getChunkPos().pack();
						if (!start.isValid() || !looked.add(key)) {
							continue;
						}
						if (settled == null) {
							settled = new HashSet<>(level.getAttachedOrElse(SETTLED, List.<Long>of()));
						}
						if (!settled.contains(key) && settle(level, structures, start, player)) {
							settled.add(key);
							level.setAttached(SETTLED, List.copyOf(settled));
						}
					}
				}
			}
		}
	}

	private static boolean labyrinth(Registry<Structure> structures, Structure structure) {
		Holder<Structure> holder = structures.wrapAsHolder(structure);
		return holder.is(ALWAYS) || holder.is(SOME);
	}

	/** Sees to the labyrinth {@code start} if {@code player} is near one of its halls; whether it is seen to for good. */
	private static boolean settle(ServerLevel level, Registry<Structure> structures, StructureStart start, ServerPlayer player) {
		List<BoundingBox> halls = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			BoundingBox box = piece.getBoundingBox();
			if (box.getXSpan() >= HALL_MIN_SPAN && box.getZSpan() >= HALL_MIN_SPAN && box.getYSpan() >= HALL_MIN_HEIGHT) {
				halls.add(box);
			}
		}
		if (halls.isEmpty()) {
			return true;
		}
		halls.sort(Comparator.comparingInt((BoundingBox box) -> box.getXSpan() * box.getZSpan()).reversed());
		BoundingBox first = halls.get(0);
		BlockPos heart = first.getCenter();
		double dx = player.getX() - heart.getX(), dz = player.getZ() - heart.getZ();
		if (dx * dx + dz * dz > WAKE_RANGE * WAKE_RANGE) {
			return false;
		}
		// which of the middling dungeons hold one is fixed by where the dungeon is
		if (!structures.wrapAsHolder(start.getStructure()).is(ALWAYS)
			&& RandomSource.create(level.getSeed() ^ start.getChunkPos().pack() * 0x9E3779B97F4A7C15L).nextFloat() >= SOME_CHANCE) {
			return true;
		}
		Minotaur minotaur = Beasts.MINOTAUR.create(level, EntitySpawnReason.STRUCTURE);
		if (minotaur == null) {
			return false;
		}
		for (BoundingBox hall : halls.subList(0, Math.min(HALLS_TRIED, halls.size()))) {
			if (!level.hasChunksAt(hall.minX(), hall.minZ(), hall.maxX(), hall.maxZ())) {
				// not all of it is in the world yet: look again when the player is deeper in
				minotaur.discard();
				return false;
			}
			BlockPos spot = spot(level, hall, minotaur);
			if (spot == null) {
				continue;
			}
			minotaur.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			minotaur.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.STRUCTURE, null);
			minotaur.setPersistenceRequired();
			minotaur.setHomeTo(spot, HOME_RADIUS);
			minotaur.addTag(TAG);
			level.addFreshEntity(minotaur);
			return true;
		}
		minotaur.discard();
		return true;
	}

	/** The clear, roofed floor nearest the middle of {@code hall} where {@code minotaur} can stand with room about it. */
	private static @Nullable BlockPos spot(ServerLevel level, BoundingBox hall, Minotaur minotaur) {
		BlockPos heart = hall.getCenter();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int y = hall.minY(); y <= hall.maxY() - HALL_MIN_HEIGHT + 1; y++) {
			for (int x = hall.minX() + 1; x < hall.maxX(); x++) {
				for (int z = hall.minZ() + 1; z < hall.maxZ(); z++) {
					double dist = heart.distToCenterSqr(x + 0.5, y + 0.5, z + 0.5);
					if (dist >= bestDist) {
						continue;
					}
					BlockState feet = level.getBlockState(at.set(x, y, z));
					if (!feet.isAir()) {
						continue;
					}
					at.set(x, y - 1, z);
					if (!level.getBlockState(at).isFaceSturdy(level, at, Direction.UP) || level.canSeeSky(at.set(x, y, z))) {
						continue;
					}
					minotaur.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
					if (!level.noCollision(minotaur, minotaur.getBoundingBox().inflate(ELBOW_ROOM, 0.0, ELBOW_ROOM))
						|| level.containsAnyLiquid(minotaur.getBoundingBox())) {
						continue;
					}
					best = new BlockPos(x, y, z);
					bestDist = dist;
				}
			}
		}
		return best;
	}
}
