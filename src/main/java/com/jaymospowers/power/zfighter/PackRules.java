package com.jaymospowers.power.zfighter;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Pack-level rules the pack layers on top of the game: what may break blocks where. Ki breaks blocks where the mob
 * griefing rule allows, but never in a master's home (a kingdom's seat or a barbarian camp's) and never inside a kept
 * tournament ring; everywhere else mob griefing stays as it is.
 */
public final class PackRules {
	/** How far a master's home - his seat - is spared (blocks). */
	static final int HOME_RADIUS = 24;

	/** The mob griefing game rule: whether mobs (and ki) may tear blocks up at all. */
	static boolean griefing(ServerLevel level) {
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/**
	 * Whether ki may break the block at {@code pos}: the griefing rule, and the block is neither in a master's home
	 * nor in a kept tournament ring.
	 */
	static boolean mayBreak(ServerLevel level, BlockPos pos) {
		if (!griefing(level)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(server)) {
			boolean home = "KINGDOM".equals(s.type()) || "BARBARIAN".equals(s.type());
			if (home && pos.distSqr(s.seat()) <= (double) HOME_RADIUS * HOME_RADIUS) {
				return false;
			}
		}
		for (BlockPos ring : level.getAttachedOrElse(ZFighterTournaments.SITES, Map.of()).values()) {
			if (pos.distSqr(ring) <= (double) ZFighterTournaments.RADIUS * ZFighterTournaments.RADIUS) {
				return false;
			}
		}
		return true;
	}

	private PackRules() {
	}
}
