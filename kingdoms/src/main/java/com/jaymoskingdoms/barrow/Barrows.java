package com.jaymoskingdoms.barrow;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Beasts;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The old barrows: the Overworld's buried cobblestone rooms (a spawner on a cobblestone floor, in no structure) are the
 * graves the lantern-keepers tend. When a player first comes near one, {@link #BARROW_CHANCE} of them have a few of the
 * old dead standing in them ({@link ZombieKnight}), and {@link #LORD_SHARE} of those their lord as well
 * ({@link Revenant}). Each is seen to once, and its dead keep to it.
 */
public final class Barrows {
	/** How often the ground round the players is looked over (ticks), and how many chunks out. */
	static final int EVERY = 100, LOOK_CHUNKS = 2;
	/** How near a player comes before a barrow's dead are stood in it (blocks). */
	static final double WAKE_RANGE = 40.0;
	/** Of the buried rooms, how many are barrows with their dead still in them; of those, how many hold their lord. */
	static final float BARROW_CHANCE = 0.5F, LORD_SHARE = 0.3F;
	/** How many knights keep a barrow, and how far from its middle they stand (blocks). */
	static final int KNIGHTS_MIN = 2, KNIGHTS_MAX = 3, SPREAD = 3;
	/** How far a knight keeps to its barrow (blocks). */
	static final int KNIGHT_HOME = 10;

	/** On a spawner: its room was seen to. Saved. */
	static final AttachmentType<Boolean> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("barrow_seen"),
		builder -> builder.persistent(Codec.BOOL));

	private Barrows() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Barrows::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (server.getTickCount() % EVERY != 0 || Beasts.ZOMBIE_KNIGHT == null || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		List<BlockPos> found = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			ChunkPos middle = player.chunkPosition();
			for (int dx = -LOOK_CHUNKS; dx <= LOOK_CHUNKS; dx++) {
				for (int dz = -LOOK_CHUNKS; dz <= LOOK_CHUNKS; dz++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(middle.x() + dx, middle.z() + dz);
					if (chunk == null) {
						continue;
					}
					for (BlockEntity be : chunk.getBlockEntities().values()) {
						if (be instanceof SpawnerBlockEntity && !be.hasAttached(SEEN)
							&& be.getBlockPos().closerToCenterThan(player.position(), WAKE_RANGE)) {
							found.add(be.getBlockPos());
						}
					}
				}
			}
		}
		for (BlockPos pos : found) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be == null || be.hasAttached(SEEN)) {
				continue;
			}
			be.setAttached(SEEN, true);
			be.setChanged();
			settle(level, pos);
		}
	}

	/** The room round the spawner at {@code pos}: if it is a barrow, its dead are stood in it. */
	private static void settle(ServerLevel level, BlockPos pos) {
		BlockState floor = level.getBlockState(pos.below());
		if (!floor.is(Blocks.COBBLESTONE) && !floor.is(Blocks.MOSSY_COBBLESTONE) || !level.getChunkAt(pos).getAllReferences().isEmpty()) {
			return;
		}
		// which rooms are barrows is fixed by where they are
		RandomSource random = RandomSource.create(level.getSeed() ^ pos.asLong() * 0x9E3779B97F4A7C15L);
		if (random.nextFloat() >= BARROW_CHANCE) {
			return;
		}
		List<BlockPos> free = new ArrayList<>();
		for (int dx = -SPREAD; dx <= SPREAD; dx++) {
			for (int dz = -SPREAD; dz <= SPREAD; dz++) {
				BlockPos at = pos.offset(dx, 0, dz);
				if (Math.abs(dx) + Math.abs(dz) >= 2 && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
					free.add(at);
				}
			}
		}
		if (random.nextFloat() < LORD_SHARE) {
			stand(level, Beasts.REVENANT, free, random, pos, Revenant.HOME_RADIUS);
		}
		for (int i = KNIGHTS_MIN + random.nextInt(KNIGHTS_MAX - KNIGHTS_MIN + 1); i > 0; i--) {
			stand(level, Beasts.ZOMBIE_KNIGHT, free, random, pos, KNIGHT_HOME);
		}
	}

	/** One of {@code type} on the first of {@code free} (taken at random) it fits on, kept within {@code home} of {@code heart}. */
	private static void stand(ServerLevel level, EntityType<? extends Mob> type, List<BlockPos> free, RandomSource random, BlockPos heart, int home) {
		Mob mob = type.create(level, EntitySpawnReason.STRUCTURE);
		if (mob == null) {
			return;
		}
		while (!free.isEmpty()) {
			BlockPos at = free.remove(random.nextInt(free.size()));
			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (level.noCollision(mob) && !level.containsAnyLiquid(mob.getBoundingBox())) {
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
				mob.setPersistenceRequired();
				mob.setHomeTo(heart, home);
				level.addFreshEntity(mob);
				return;
			}
		}
		mob.discard();
	}
}
