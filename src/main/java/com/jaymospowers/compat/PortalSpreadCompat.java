package com.jaymospowers.compat;

import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Whether Nether Portal Spread (Serilum) may look at the land round a portal this tick: every chunk it can read is already
 * at FULL. Its looks use Level.getBlockState, which loads and waits for a chunk that isn't ready, and a chunk still
 * generating held the server thread past the watchdog (mixin.portalspread). Asked with getChunkNow, which never
 * schedules or waits; Level.hasChunk and isLoaded only ask for a ticket and are true while a chunk still generates.
 */
public final class PortalSpreadCompat {
	/** How far (blocks) the portal check follows a portal's frame (one portal width, 21) and looks round it (3). */
	public static final int FRAME_REACH = 25;
	/** The mod's largest spread radius, used if its setting can't be read. */
	static final int RADIUS_MOST = 100;

	private static Field radius;
	private static boolean looked;

	private PortalSpreadCompat() {
	}

	/** Whether every chunk within {@code reach} blocks of {@code pos} (across) is at FULL now. */
	public static boolean ready(Level level, BlockPos pos, int reach) {
		for (int cx = (pos.getX() - reach) >> 4; cx <= (pos.getX() + reach) >> 4; cx++) {
			for (int cz = (pos.getZ() - reach) >> 4; cz <= (pos.getZ() + reach) >> 4; cz++) {
				if (level.getChunkSource().getChunkNow(cx, cz) == null) {
					return false;
				}
			}
		}
		return true;
	}

	/** The mod's spread radius setting (blocks), read from its config each time; {@link #RADIUS_MOST} if it can't be. */
	public static int spreadRadius() {
		if (!looked) {
			looked = true;
			try {
				radius = Class.forName("com.serilum.netherportalspread_common_fabric.config.ConfigHandler").getField("portalSpreadRadius");
			} catch (ReflectiveOperationException | LinkageError e) {
				radius = null;
			}
		}
		try {
			return radius == null ? RADIUS_MOST : Math.max(1, radius.getInt(null));
		} catch (IllegalAccessException e) {
			return RADIUS_MOST;
		}
	}
}
