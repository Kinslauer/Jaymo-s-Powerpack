package com.jaymospowers.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.17.54) The ground cracked under a giant's foot (the Great Ape's steps, {@code ZFighterLineage#footfall}): the blocks
 * under the foot and a few cracks wandering out from it ({@code destroyBlockProgress}), fainter towards their ends, that
 * fade over {@link #FADE_TICKS}. Nothing breaks. (Kingdoms' Aether Giants crack the ground the same way, on their own.)
 */
public final class GroundCracks {
	/** How many cracks run out from the foot, how deep they're cracked (at the foot, and at their ends), and how long they last. */
	static final int LINES = 5, STAGE = 9, END_STAGE = 4, FADE_TICKS = 100;
	/** The ids they're drawn under, in turn (one block each), clear of any real entity's. */
	static final int ID_BASE = 0x4B000000, IDS = 4096;
	/** How far up or down a crack follows the ground (blocks). */
	static final int CLIMB = 3;

	private record Crack(ResourceKey<Level> dimension, BlockPos pos, int id, int stage, long born) {
	}

	private static final List<Crack> CRACKS = new ArrayList<>();
	private static int next;

	private GroundCracks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GroundCracks::fade);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CRACKS.clear());
	}

	/** The ground cracked round {@code under} (the block the foot came down on), its cracks up to {@code length} long. */
	public static void crack(ServerLevel level, BlockPos under, RandomSource random, int length) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				at(level, under.offset(dx, 0, dz), STAGE);
			}
		}
		for (int line = 0; line < LINES; line++) {
			double angle = (line + random.nextDouble() * 0.6) * Mth.TWO_PI / LINES;
			int reach = Math.max(1, length / 2 + random.nextInt(length / 2 + 1));
			for (int i = 2; i <= reach + 1; i++) {
				// each crack wanders a little as it runs out
				angle += (random.nextDouble() - 0.5) * 0.5;
				int x = Mth.floor(under.getX() + 0.5 + Math.cos(angle) * i), z = Mth.floor(under.getZ() + 0.5 + Math.sin(angle) * i);
				BlockPos ground = top(level, x, under.getY(), z);
				if (ground != null) {
					at(level, ground, STAGE - (STAGE - END_STAGE) * (i - 2) / Math.max(reach - 1, 1));
				}
			}
		}
	}

	/** The top solid block at {@code (x, z)} within {@link #CLIMB} of {@code y}, or null. */
	private static BlockPos top(ServerLevel level, int x, int y, int z) {
		for (int dy = CLIMB; dy >= -CLIMB; dy--) {
			BlockPos pos = new BlockPos(x, y + dy, z);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
				return pos;
			}
		}
		return null;
	}

	private static void at(ServerLevel level, BlockPos pos, int stage) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
			return;
		}
		int id = ID_BASE + next;
		next = (next + 1) % IDS;
		CRACKS.removeIf(crack -> crack.id() == id);
		level.destroyBlockProgress(id, pos, stage);
		CRACKS.add(new Crack(level.dimension(), pos.immutable(), id, stage, level.getGameTime()));
	}

	/** Every half second: the second half of their time they fade, and then they're gone. */
	private static void fade(MinecraftServer server) {
		if (CRACKS.isEmpty() || server.getTickCount() % 10 != 0) {
			return;
		}
		for (Iterator<Crack> it = CRACKS.iterator(); it.hasNext();) {
			Crack crack = it.next();
			ServerLevel level = server.getLevel(crack.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			long age = level.getGameTime() - crack.born();
			if (age >= FADE_TICKS) {
				level.destroyBlockProgress(crack.id(), crack.pos(), -1);
				it.remove();
			} else if (age > FADE_TICKS / 2) {
				level.destroyBlockProgress(crack.id(), crack.pos(), (int)Math.max(0, crack.stage() * 2 * (FADE_TICKS - age) / FADE_TICKS));
			}
		}
	}
}
