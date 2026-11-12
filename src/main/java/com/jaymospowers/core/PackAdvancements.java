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
		// a deed left as a tag survives the death that was the deed (a life weighed and born again)
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			for (String tag : oldPlayer.entityTags()) {
				if (tag.startsWith(DEED)) {
					newPlayer.addTag(tag);
				}
			}
		});
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

	/** The tag a Retaking deed leaves on a player until Jaymo's Kingdoms reads it ({@link #deed}). */
	public static final String DEED = "jaymospowers.retaking.";

	/**
	 * A deed toward the Retaking, Jaymo's Kingdoms' main story, that only Powers sees (a raider band beaten, a fallen star
	 * broken, a pod flown, a far world freed or conquered). It is left as a tag: Kingdoms awards its milestone if the player's
	 * act has begun and takes the tag off either way, so a deed done too early has to be done again. Without Kingdoms the
	 * tag just sits there.
	 */
	public static void deed(ServerPlayer player, String name) {
		player.addTag(DEED + name);
	}

	/** {@code pack/<name>}'s one criterion, "done", for {@code player} (nothing if it's done already). */
	public static void award(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(JaymosPowers.id("pack/" + name));
		if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
			player.getAdvancements().award(holder, "done");
		}
	}
}
