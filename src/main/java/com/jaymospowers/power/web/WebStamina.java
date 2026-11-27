package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The tendrils tire. A bonded web slinger has a stamina (0 to {@link #MAX}, in tenths of a point) that the symbiote's
 * heavier tendril moves spend: Tendril Grab, Web Zip, Yank, the Lash, and every tick of clinging to a wall (Wall Crawl,
 * or a parkour cling). It comes back at rest, {@link #REST_TICKS} after the last spend. Out of it the moves refuse with
 * a line (rate-limited), and the client lets go of the wall below {@link #CLING_MIN}. Synced to its owner for the HUD.
 * Creative spends nothing.
 */
public final class WebStamina {
	/** A full stamina (tenths of a point). */
	public static final int MAX = 1000;
	/** What each move costs. */
	public static final int GRAB = 80, ZIP = 100, YANK = 80, LASH = 120;
	/** What a tick of clinging costs (about 25 seconds from full), and the least stamina a cling can start or go on with. */
	public static final int CLING_TICK = 2, CLING_MIN = 40;
	/** Ticks after the last spend before it comes back, how often it is topped up, and by how much (about 6 seconds from empty). */
	public static final int REST_TICKS = 30, REGEN_EVERY = 4, REGEN_STEP = 32;
	/** The least gap between its complaints (ticks), and how many lines it has for them. */
	private static final int COMPLAIN_GAP = 60, COMPLAINTS = 3;

	/** Stamina, 0 to {@link #MAX}. Saved, and synced to its owner for the HUD. */
	public static final AttachmentType<Integer> STAMINA = AttachmentRegistry.create(JaymosPowers.id("web_stamina"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** Game time of each player's last spend and last complaint (server only). */
	private static final Map<UUID, Long> SPENT = new HashMap<>();
	private static final Map<UUID, Long> COMPLAINED = new HashMap<>();

	private WebStamina() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.tickCount % REGEN_EVERY == 0) {
					regen(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SPENT.remove(handler.getPlayer().getUUID());
			COMPLAINED.remove(handler.getPlayer().getUUID());
		});
	}

	/** Its stamina (tenths of a point); readable on both sides. */
	public static int get(Player player) {
		return Mth.clamp(player.getAttachedOrElse(STAMINA, MAX), 0, MAX);
	}

	/** Whether stamina matters for this player: bonded, and not in creative. */
	private static boolean applies(Player player) {
		return WebSymbiote.isBonded(player) && !player.isCreative();
	}

	/** Whether the player has {@code cost} to spend; if not, it says so (rate-limited). Spends nothing. */
	public static boolean afford(ServerPlayer player, int cost) {
		if (!applies(player) || get(player) >= cost) {
			return true;
		}
		complain(player);
		return false;
	}

	/** Spends {@code cost} (down to nothing); no check, no line: ask {@link #afford} first. */
	public static void spend(ServerPlayer player, int cost) {
		if (!applies(player)) {
			return;
		}
		set(player, get(player) - cost);
		SPENT.put(player.getUUID(), player.level().getGameTime());
	}

	/** {@link #afford} then {@link #spend}: false (with its line) when the move is refused. */
	public static boolean pay(ServerPlayer player, int cost) {
		if (!afford(player, cost)) {
			return false;
		}
		spend(player, cost);
		return true;
	}

	/** A cling may start: there is enough stamina to hold on (says so if not). */
	public static boolean canCling(ServerPlayer player) {
		if (!applies(player) || get(player) >= CLING_MIN) {
			return true;
		}
		complain(player);
		return false;
	}

	/** One tick of clinging: spends it; false (with its line) once there is too little to hold on. */
	public static boolean cling(ServerPlayer player) {
		if (!applies(player)) {
			return true;
		}
		if (get(player) < CLING_MIN) {
			complain(player);
			return false;
		}
		spend(player, CLING_TICK);
		return true;
	}

	/** The client's check: too tired to hold a wall. */
	public static boolean tired(Player player) {
		return applies(player) && get(player) < CLING_MIN;
	}

	private static void complain(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long was = COMPLAINED.get(player.getUUID());
		if (was != null && now - was >= 0 && now - was < COMPLAIN_GAP) {
			return;
		}
		COMPLAINED.put(player.getUUID(), now);
		WebSymbiote.whisper(player, "stamina.tired." + (1 + player.getRandom().nextInt(COMPLAINTS)));
	}

	private static void regen(ServerPlayer player) {
		int now = get(player);
		if (now >= MAX) {
			return;
		}
		Long spent = SPENT.get(player.getUUID());
		if (spent != null && player.level().getGameTime() - spent < REST_TICKS) {
			return;
		}
		set(player, now + REGEN_STEP);
	}

	private static void set(ServerPlayer player, int value) {
		int next = Mth.clamp(value, 0, MAX);
		if (next != get(player) || !player.hasAttached(STAMINA)) {
			player.setAttached(STAMINA, next);
		}
	}
}
