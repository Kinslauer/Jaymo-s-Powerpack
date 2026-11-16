package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hostile Mobs Improve Over Time (KNIZE_1007, all rights reserved; shipped unchanged, nothing of it copied) follows the
 * world tiers (Andy, Sensible MC picks: "Can we work it in with the world tiers?"). On its own it raises each player's
 * level by one every 1200 seconds of play, and the mobs near a player gain abilities from that player's level (zombies
 * that break through, silent creepers, witches that teleport...). Here its timer is stopped and each player's level is
 * set from the world tier they play at ({@link WorldTiers#of}): monsters pick up new tricks when you choose a harder
 * tier, and lose them when you step back down. WorldTiers already makes foes tougher; these levels are kept low so the
 * two don't stack into a wall.
 * <p>
 * It's a datapack that keeps its state in scoreboards, so it's driven with the same commands its own config menu uses.
 * When it isn't installed its objectives don't exist and the commands just fail, unseen.
 */
public final class HostileMobsTiers {
	/** How often the levels are set again (ticks). */
	static final int EVERY = 100;
	/** Its level for each world tier: Hearth, Borderlands, Wildlands, Dread, Legend. */
	static final int[] LEVEL = {0, 5, 12, 20, 30};
	/** Its time between level-ups, set out of reach so time alone never raises it. */
	static final int NEVER = Integer.MAX_VALUE;

	private HostileMobsTiers() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HostileMobsTiers::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0 || server.getPlayerList().getPlayers().isEmpty()) {
			return;
		}
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		run(server, source, "scoreboard players set #leveluptimer TimerScoreboard " + NEVER);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int level = LEVEL[Math.min(WorldTiers.of(player).ordinal(), LEVEL.length - 1)];
			run(server, source, "scoreboard players set " + player.getScoreboardName() + " HostileMobs " + level);
		}
	}

	private static void run(MinecraftServer server, CommandSourceStack source, String command) {
		server.getCommands().performPrefixedCommand(source, command);
	}
}
