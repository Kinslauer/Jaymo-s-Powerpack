package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Andy (2026-11-27): "show a hint when players get captured that shows this. each hint should play once. not repeating."
 * The first time a player is held each way, the ways out are told, one hint at a time, each once in their life:
 * <ul>
 * <li><b>In chains</b> ({@link StoryHard#shackled}): how captors treat them, slipping the shackle, picking it, and breaking
 * the chain or sawing the rope (whichever holds them; the other is told the first time it does).</li>
 * <li><b>In the cells</b> ({@link Busts#jailed}): waiting it out, the door (the fine, a lockpick), breaking out.</li>
 * <li><b>Hogtied</b> ({@code Lasso#hogtied}): struggling free.</li>
 * </ul>
 * (Webbed in a lair is Jaymo's Powers' own tip.) Each is sent as an action-bar line whose key is listed in Jaymo's Powers'
 * {@code hint_keys.txt}, so it comes up as a tip card top right; one every {@link #GAP} ticks, so each card has its time.
 */
final class CaptiveTips {
	/** How often (ticks) players are looked at, and the least time (ticks) between two of one player's tips. */
	static final int EVERY = 20, GAP = 200;

	/** On a player: the captive tips they've been shown. Saved, kept through death. */
	static final AttachmentType<List<String>> SHOWN = AttachmentRegistry.create(JaymosKingdoms.id("captive_tips"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** Player → the server tick of their last tip. */
	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private CaptiveTips() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CaptiveTips::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			Integer last = LAST.get(player.getUUID());
			if (last != null && now - last < GAP && now >= last) {
				continue;
			}
			String part = next(player);
			if (part == null) {
				continue;
			}
			List<String> shown = new ArrayList<>(player.getAttachedOrElse(SHOWN, List.of()));
			shown.add(part);
			player.setAttached(SHOWN, List.copyOf(shown));
			LAST.put(player.getUUID(), now);
			player.sendOverlayMessage(Component.translatable("captive_tip.jaymoskingdoms." + part,
				Component.keybind("key.sneak"), Component.keybind("key.use")));
		}
	}

	/** The next tip {@code player} hasn't been shown for how they're held now, or null. */
	private static String next(ServerPlayer player) {
		List<String> due = new ArrayList<>();
		if (StoryHard.shackled(player)) {
			Shackles.Tether tether = player.getAttached(Shackles.TETHER);
			due.add("chained");
			due.add("slip");
			due.add("pick");
			if (tether != null) {
				due.add(tether.rope() ? "rope" : "chain");
			}
		}
		if (Busts.jailed(player)) {
			due.add("cell");
			due.add("cell_door");
			due.add("cell_out");
		}
		if (com.jaymoskingdoms.forms.Lasso.hogtied(player)) {
			due.add("hogtied");
		}
		if (due.isEmpty()) {
			return null;
		}
		List<String> shown = player.getAttachedOrElse(SHOWN, List.of());
		for (String part : due) {
			if (!shown.contains(part)) {
				return part;
			}
		}
		return null;
	}
}
