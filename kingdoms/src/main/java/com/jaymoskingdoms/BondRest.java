package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The sitting half of bonding (Jaymo's Powers keeps the sleeping half): a minute seated where you would catch your breath
 * ({@link CatchBreath#resting}), calm and unhunted, bonds what you have on you that is not bonded yet ({@link ItemBonds#rest}),
 * up to three things. You need not be hurt.
 */
public final class BondRest {
	/** A minute of sitting, counted every second. */
	static final int REST_TICKS = 1200, EVERY = 20;

	private static final Map<UUID, Integer> SAT = new HashMap<>();

	private BondRest() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BondRest::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SAT.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level) || !CatchBreath.resting(level, player) || !CatchBreath.calm(level, player)
				|| !wantsBond(player)) {
				SAT.remove(player.getUUID());
				continue;
			}
			int sat = SAT.merge(player.getUUID(), EVERY, Integer::sum);
			if (sat >= REST_TICKS) {
				SAT.remove(player.getUUID());
				ItemBonds.rest(player);
			}
		}
	}

	private static boolean wantsBond(ServerPlayer player) {
		if (ItemBonds.bondCount(player) >= 3) {
			return false;
		}
		for (ItemStack stack : ItemBonds.carried(player)) {
			if (ItemBonds.needs(stack) && !ItemBonds.bonded(player, stack)) {
				return true;
			}
		}
		return false;
	}
}
