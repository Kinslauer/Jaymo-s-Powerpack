package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.Future;
import com.jaymospowers.power.chrono.future.FutureAndroid;
import com.jaymospowers.power.chrono.future.FutureMachines;
import com.jaymospowers.power.chrono.future.FutureMech;
import com.jaymospowers.power.chrono.future.LosPerrito;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Where the wrecks lie, placed at runtime as chunks are first seen (like {@code FutureRuins}), each chunk weighed once
 * ({@link #CHECKED}):
 * <ul>
 * <li><b>Dead Rigs</b> only in Los Perrito's ruins (the future, on the island): about one chunk in
 * {@link #DEAD_CHANCE}, each guarded by androids ({@link #GUARDS}) and sometimes a mech ({@link #MECH_GUARD}).</li>
 * <li><b>Fallen Rigs</b> only in the Nether and the End, in freshly generated chunks: one in {@link #NETHER_CHANCE} /
 * {@link #END_CHANCE}.</li>
 * </ul>
 */
public final class RigWrecks {
	/** On a chunk: it has been weighed for a wreck. */
	public static final AttachmentType<Boolean> CHECKED = AttachmentRegistry.create(JaymosPowers.id("rig_wreck_checked"),
		builder -> builder.persistent(Codec.BOOL));

	/** One chunk in this many gets a wreck. */
	static final int DEAD_CHANCE = 28, NETHER_CHANCE = 220, END_CHANCE = 160;
	/** A Dead Rig's guard: androids, and the chance of a mech too. */
	static final int GUARDS = 2;
	static final float MECH_GUARD = 0.2F;
	/** Fallen Rigs in the End keep off the dragon's island (blocks from the centre). */
	static final int END_CLEAR = 256;
	/** Nether floors looked for between these heights. */
	static final int NETHER_LOW = 31, NETHER_HIGH = 112;
	/** Wrecks placed per tick at most. */
	static final int PER_TICK = 1;

	private record Pending(ResourceKey<Level> dimension, ChunkPos pos) {
	}

	private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
	private static final Set<Pending> QUEUED = new HashSet<>();

	private RigWrecks() {
	}

	static void init() {
		ServerChunkEvents.CHUNK_LOAD.register(RigWrecks::onLoad);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUEUE.clear();
			QUEUED.clear();
		});
	}

	private static void onLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (Boolean.TRUE.equals(chunk.getAttached(CHECKED))) {
			return;
		}
		ResourceKey<Level> dimension = level.dimension();
		ChunkPos pos = chunk.getPos();
		int chance;
		if (dimension == Future.FUTURE) {
			// the city comes ready-made: only its own (saved, not generated) chunks
			if (generated || !onIsland(pos)) {
				chunk.setAttached(CHECKED, true);
				return;
			}
			chance = DEAD_CHANCE;
		} else if (dimension == Level.NETHER) {
			chance = NETHER_CHANCE;
		} else if (dimension == Level.END) {
			chance = END_CHANCE;
		} else {
			return;
		}
		if (!generated && dimension != Future.FUTURE) {
			// old ground explored before the Rig existed: leave it be
			chunk.setAttached(CHECKED, true);
			return;
		}
		RandomSource random = RandomSource.create(pos.pack() * 0x9E3779B97F4A7C15L + 0x51C0L);
		if (random.nextInt(chance) != 0) {
			chunk.setAttached(CHECKED, true);
			return;
		}
		Pending pending = new Pending(dimension, pos);
		if (QUEUED.add(pending)) {
			QUEUE.add(pending);
		}
	}

	private static boolean onIsland(ChunkPos pos) {
		return pos.getMaxBlockX() >= LosPerrito.MIN_X && pos.getMinBlockX() <= LosPerrito.MAX_X
			&& pos.getMaxBlockZ() >= LosPerrito.MIN_Z && pos.getMinBlockZ() <= LosPerrito.MAX_Z;
	}

	static void tick(MinecraftServer server) {
		for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) {
			Pending pending = QUEUE.poll();
			QUEUED.remove(pending);
			ServerLevel level = server.getLevel(pending.dimension());
			if (level == null || !(level.getChunkSource().getChunkNow(pending.pos().x(), pending.pos().z()) instanceof LevelChunk chunk)
				|| Boolean.TRUE.equals(chunk.getAttached(CHECKED))) {
				continue;
			}
			chunk.setAttached(CHECKED, true);
			chunk.markUnsaved();
			place(level, pending.pos());
		}
	}

	private static void place(ServerLevel level, ChunkPos pos) {
		RandomSource random = RandomSource.create(pos.pack() * 31L + 0x816L);
		ResourceKey<Level> dimension = level.dimension();
		for (int attempt = 0; attempt < 6; attempt++) {
			int x = pos.getMinBlockX() + 2 + random.nextInt(12);
			int z = pos.getMinBlockZ() + 2 + random.nextInt(12);
			BlockPos at;
			if (dimension == Level.NETHER) {
				at = netherFloor(level, x, z, random);
			} else {
				if (dimension == Level.END && (long)x * x + (long)z * z < (long)END_CLEAR * END_CLEAR) {
					return;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				at = y <= level.getMinY() + 1 ? null : new BlockPos(x, y, z);
			}
			if (at == null || !fits(level, at)) {
				continue;
			}
			Block wreck = dimension == Future.FUTURE ? Rig.DEAD_RIG : Rig.FALLEN_RIG;
			level.setBlock(at, wreck.defaultBlockState(), Block.UPDATE_ALL);
			if (dimension == Future.FUTURE) {
				guard(level, at, random);
			}
			JaymosPowers.LOGGER.debug("Rig wreck at {} in {}", at, dimension.identifier());
			return;
		}
	}

	private static @Nullable BlockPos netherFloor(ServerLevel level, int x, int z, RandomSource random) {
		int start = NETHER_LOW + random.nextInt(NETHER_HIGH - NETHER_LOW);
		for (int y = start; y < NETHER_HIGH; y++) {
			BlockPos at = new BlockPos(x, y, z);
			if (fits(level, at)) {
				return at;
			}
		}
		return null;
	}

	/** Air here and above, dry, on solid ground. */
	private static boolean fits(ServerLevel level, BlockPos at) {
		BlockState floor = level.getBlockState(at.below());
		return level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && level.getFluidState(at).isEmpty()
			&& floor.isFaceSturdy(level, at.below(), Direction.UP) && level.getFluidState(at.below()).isEmpty();
	}

	/** The future's machines keep watch over what's left of it. */
	private static void guard(ServerLevel level, BlockPos at, RandomSource random) {
		for (int i = 0; i < GUARDS; i++) {
			FutureAndroid android = FutureMachines.ANDROID.create(level, EntitySpawnReason.EVENT);
			if (android != null && stand(level, android, at.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3))) {
				android.outfit(level);
				android.setPersistenceRequired();
				level.addFreshEntity(android);
			}
		}
		if (random.nextFloat() < MECH_GUARD) {
			FutureMech mech = FutureMachines.MECH.create(level, EntitySpawnReason.EVENT);
			if (mech != null && stand(level, mech, at.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5))) {
				mech.setPersistenceRequired();
				level.addFreshEntity(mech);
			}
		}
	}

	private static boolean stand(ServerLevel level, Mob mob, BlockPos pos) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		if (!level.getFluidState(new BlockPos(pos.getX(), y - 1, pos.getZ())).isEmpty()) {
			return false;
		}
		mob.snapTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		return level.noCollision(mob, mob.getBoundingBox());
	}
}
