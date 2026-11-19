package com.jaymoskitchen.farmwater;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The fish a stretch of water holds (Let's Do's fish traps, our own): every chunk of water has a stock, fixed by how
 * much water is round the trap ({@link #capacity}), that traps draw down and that recovers a fish every
 * {@link #RECOVER_TICKS}. A trap in a puddle or a trapped-out stretch comes up near empty; leave the water alone for
 * days and it fills again. Nothing is punished: a stretch you stop trapping simply recovers.
 */
public final class WaterStock {
	/** The most fish a stretch (a chunk) holds, and the water blocks round a trap that account for one of them. */
	static final int STOCK_MAX = 24, WATER_PER_FISH = 10;
	/** The box of water counted round a trap: this far to each side, this far down and up. */
	static final int REACH = 4, DOWN = 2, UP = 1;
	/** Ticks for one fish to come back (24000 is a day: six a day). */
	static final long RECOVER_TICKS = 4000L;

	/** The fish left in a chunk's water, and the game time the count was last brought up to date. Saved on the chunk. */
	private static final AttachmentType<Integer> STOCK = AttachmentRegistry.create(JaymosKitchen.id("water_stock"), b -> b.persistent(Codec.INT));
	private static final AttachmentType<Long> COUNTED = AttachmentRegistry.create(JaymosKitchen.id("water_stock_at"), b -> b.persistent(Codec.LONG));

	private WaterStock() {
	}

	public static void register() {
		JaymosKitchen.LOGGER.debug("water stock attachments: {} {}", STOCK, COUNTED);
	}

	/** How many fish the water round {@code pos} could hold, from how much of it there is. */
	static int capacity(ServerLevel level, BlockPos pos) {
		int water = 0;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-REACH, -DOWN, -REACH), pos.offset(REACH, UP, REACH))) {
			if (level.getFluidState(p).is(FluidTags.WATER)) {
				water++;
			}
		}
		return Math.min(STOCK_MAX, water / WATER_PER_FISH);
	}

	/** The fish in the stretch now, recovery counted (never above what the water round {@code pos} holds). */
	public static int stock(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		long now = level.getGameTime();
		int cap = capacity(level, pos);
		Integer held = chunk.getAttached(STOCK);
		long counted = chunk.getAttachedOrElse(COUNTED, now);
		if (held == null) {
			return cap;
		}
		long steps = Math.max(0L, (now - counted) / RECOVER_TICKS);
		return (int)Math.min(cap, held + steps);
	}

	/** Takes up to {@code n} fish from the stretch; how many there were to take. */
	static int take(ServerLevel level, BlockPos pos, int n) {
		LevelChunk chunk = level.getChunkAt(pos);
		long now = level.getGameTime();
		int have = stock(level, pos);
		int taken = Math.min(n, have);
		Long counted = chunk.getAttached(COUNTED);
		// the count moves on by whole recoveries only, so a part of the wait carries over
		long steps = counted == null ? 0L : Math.max(0L, (now - counted) / RECOVER_TICKS);
		chunk.setAttached(STOCK, have - taken);
		chunk.setAttached(COUNTED, counted == null ? now : counted + steps * RECOVER_TICKS);
		return taken;
	}
}
