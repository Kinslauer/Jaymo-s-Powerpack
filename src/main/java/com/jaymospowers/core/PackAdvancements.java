package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.tinkering.TinkerTool;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * (0.20.88) Andy (seven-mods plan, patch 3): the "Jaymo's Powerpack" advancement tab ({@code data/jaymospowers/advancement/pack}),
 * one screen laid out on Better Advancements' map: a main line through a playthrough (the life story, a job's rank, a
 * patron, a monolith, the Giants, the Aether) and side branches. Powers awards its own part: a power awakened, the first
 * alchemy research, a forged Tinkers tool in hand, an execution. Kingdoms and Kitchen award theirs by the same ids.
 */
public final class PackAdvancements {
	static final int EVERY = 100;

	private PackAdvancements() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (Powers.of(player) != null) {
					award(player, "awakened");
				}
				if (!Knowledge.of(player).research().isEmpty()) {
					award(player, "insight");
				}
				if (forged(player.getInventory())) {
					award(player, "forged");
				}
			}
		});
	}

	private static boolean forged(Inventory inventory) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (!s.isEmpty() && s.getItem() instanceof TinkerTool) {
				return true;
			}
		}
		return false;
	}

	/** {@code pack/<name>}'s one criterion, "done", for {@code player} (nothing if it's done already). */
	public static void award(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(JaymosPowers.id("pack/" + name));
		if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
			player.getAdvancements().award(holder, "done");
		}
	}
}
