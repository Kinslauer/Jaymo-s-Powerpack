package com.jaymospowers.power.web;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a tendril bites into a block, the block cracks (the usual mining cracks), and the crack lingers after it lets go:
 * held for {@code HOLD_TICKS}, then fading a stage at a time over {@code FADE_TICKS}. Biting the same block again deepens
 * it and starts the wait over. Works on either side: the server's cracks (Wall Crawl's bites, the wall tethers) reach
 * everyone near; a client's (the walking tendrils' steps, worked out on every client) only that client, which sees the
 * same walk as everyone else.
 */
public final class WebCracks {
	/** How long a crack holds after its last bite, then how long it takes to fade away (ticks). */
	public static final int HOLD_TICKS = 100, FADE_TICKS = 200;
	/** The deepest a bite cracks a block (the cracks run 0-9). */
	public static final int MAX_STAGE = 8;

	private static final class Crack {
		final int id;
		int stage;
		int shown;
		long at;

		Crack(int id) {
			this.id = id;
		}
	}

	private static final Map<Level, Map<BlockPos, Crack>> CRACKS = new WeakHashMap<>();
	/** Breaker ids for the cracks, well away from any entity's. */
	private static int nextId = 0x5EB0_0000;

	private WebCracks() {
	}

	static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(WebCracks::tick);
	}

	/** Cracks the block at {@code pos} at least to {@code stage}, and starts its wait over. */
	public static synchronized void crack(Level level, @Nullable BlockPos pos, int stage) {
		if (pos == null || level.getBlockState(pos).isAir() || level.getBlockState(pos).getDestroySpeed(level, pos) < 0.0F) {
			return;
		}
		Crack crack = CRACKS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(pos.immutable(), p -> new Crack(nextId++));
		crack.stage = Math.min(MAX_STAGE, Math.max(crack.shown, stage));
		crack.at = level.getGameTime();
		if (crack.shown != crack.stage) {
			crack.shown = crack.stage;
			level.destroyBlockProgress(crack.id, pos, crack.shown);
		}
	}

	/** Cracks the block a surface point {@code at} lies on (the point is on its face, so look just around it). */
	public static void crackAt(Level level, Vec3 at, int stage) {
		crack(level, solidAt(level, at), stage);
	}

	/** The solid block a surface point lies on, or null. */
	public static @Nullable BlockPos solidAt(Level level, Vec3 at) {
		BlockPos here = BlockPos.containing(at);
		if (!level.getBlockState(here).getCollisionShape(level, here).isEmpty()) {
			return here;
		}
		for (Direction dir : Direction.values()) {
			BlockPos pos = BlockPos.containing(at.add(dir.getStepX() * 0.05, dir.getStepY() * 0.05, dir.getStepZ() * 0.05));
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return pos;
			}
		}
		return null;
	}

	/** Each tick (on its own side): the cracks fade once their wait is over. */
	public static synchronized void tick(Level level) {
		Map<BlockPos, Crack> cracks = CRACKS.get(level);
		if (cracks == null || cracks.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Map.Entry<BlockPos, Crack>> it = cracks.entrySet().iterator(); it.hasNext();) {
			Map.Entry<BlockPos, Crack> entry = it.next();
			Crack crack = entry.getValue();
			long age = now - crack.at;
			int shown = age < HOLD_TICKS ? crack.stage
				: (int)Math.ceil(crack.stage * (1.0 - (age - HOLD_TICKS) / (double)FADE_TICKS)) - 1;
			if (shown < 0 || level.getBlockState(entry.getKey()).isAir()) {
				level.destroyBlockProgress(crack.id, entry.getKey(), -1);
				it.remove();
			} else if (shown != crack.shown) {
				crack.shown = shown;
				level.destroyBlockProgress(crack.id, entry.getKey(), shown);
			}
		}
	}
}
