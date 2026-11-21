package com.jaymoskitchen.cooking;

import com.jaymoskitchen.delight.CookwareBlockEntity;
import com.jaymoskitchen.delight.CuttingBoardEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Server-side tick handler for hands-on cooking sessions. Advances all active sessions
 * on all stations each tick. For CHOP and STIR the client simulates and the server only
 * validates on finish; for future server-simmed methods (GRILL, SMOKE, SEAR) this would
 * send sync packets.
 */
public final class HandsOnTicker {
	private HandsOnTicker() {
	}

	public static void tick(MinecraftServer server) {
		for (var level : server.getAllLevels()) {
			for (BlockEntity be : level.getBlockEntities()) {
				if (be instanceof CuttingBoardEntity board) {
					tickSessions(board.getHandsOnSessions(), level);
				} else if (be instanceof CookwareBlockEntity cookware) {
					tickSessions(cookware.getHandsOnSessions(), level);
				}
			}
		}
	}

	private static void tickSessions(Map<UUID, HandsOnSession> sessions, net.minecraft.server.level.ServerLevel level) {
		// For CHOP/STIR: client sims, server only validates on finish (handled in packet handlers)
		// For future server-simmed methods: would advance state here and send HandsOnSyncPayload
		// Clean up timed-out sessions
		sessions.entrySet().removeIf(entry -> {
			HandsOnSession session = entry.getValue();
			int elapsed = level.getServer().getTickCount() - session.startTick();
			return elapsed > session.method().limitTicks(session.station());
		});
	}
}