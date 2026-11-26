package com.jaymoskingdoms;

import io.netty.channel.ChannelFutureListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-26): "when players join, they get all kinds of achievements before spawning. all messages should be
 * halted until the player loads in." From the moment a player connects until they can see the world (their client has
 * finished loading in, and {@link StoryGround#unseen} no longer holds: the story, MCA's creator, the start's loading
 * screen and the short reveal after it), everything that would talk to them waits: chat, system lines, advancement
 * toasts and the advancement screen's updates, and new-recipe toasts. Once they're in, it all reaches them in the order
 * it was sent. Action-bar lines sent meanwhile are dropped (they'd be stale by then). The same holds whenever a later
 * story puts the loading screen back up (a new life after Reincarnate, a far journey).
 *
 * <p>The "X has made the advancement" line for an advancement earned while hidden waits too, for everyone: it's said
 * once the player is in, not while they're still behind the loading screen. Everything else (the world, entities,
 * the loading screen's own packets) goes through as before. Fed by {@code JoinHushSendMixin},
 * {@code JoinHushAwardMixin} and {@code JoinHushBroadcastMixin}.
 */
public final class JoinHush {
	/** System lines kept for a hidden player at most (the oldest go first); signed chat and advancements are always kept. */
	static final int MAX_LINES = 200;

	/** A packet held back, and what was to be told when it was sent. */
	private record Held(Packet<?> packet, @Nullable ChannelFutureListener listener) {
	}

	/** An advancement announcement held back until its earner can see the world. */
	private record Announcement(Component message, Function<ServerPlayer, Component> perViewer, boolean overlay) {
	}

	/** Each hidden player's held packets, oldest first (a player is hidden while they have an entry). */
	private static final Map<UUID, Deque<Held>> HUSHED = new ConcurrentHashMap<>();
	/** Each hidden player's held announcements, oldest first. */
	private static final Map<UUID, List<Announcement>> ANNOUNCEMENTS = new ConcurrentHashMap<>();
	/** Players whose client has loaded in at least once since they connected. */
	private static final Map<UUID, Boolean> ARRIVED = new ConcurrentHashMap<>();
	/** The players whose advancements are being awarded on this thread, innermost first (an award's rewards may award another). */
	private static final ThreadLocal<Deque<ServerPlayer>> AWARDING = ThreadLocal.withInitial(ArrayDeque::new);

	private JoinHush() {
	}

	static void register() {
		// hidden from the moment the connection is made, before the join's own lines and the first advancement update
		ServerPlayConnectionEvents.INIT.register((handler, server) -> {
			HUSHED.putIfAbsent(handler.player.getUUID(), new ArrayDeque<>());
			ARRIVED.remove(handler.player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(JoinHush::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			HUSHED.remove(id);
			ARRIVED.remove(id);
			// what they earned is still said to the others
			announce(server, ANNOUNCEMENTS.remove(id));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HUSHED.clear();
			ANNOUNCEMENTS.clear();
			ARRIVED.clear();
		});
	}

	/** Each tick: who can't see the world yet is hidden, and who just came in gets what waited for them. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (!ARRIVED.containsKey(id) && player.connection.hasClientLoaded()) {
				ARRIVED.put(id, Boolean.TRUE);
			}
			boolean hidden = !ARRIVED.containsKey(id) || StoryGround.unseen(player);
			if (hidden) {
				HUSHED.computeIfAbsent(id, k -> new ArrayDeque<>());
			} else if (HUSHED.containsKey(id)) {
				release(server, player);
			}
		}
	}

	/** Sends {@code player} everything held for them, in order, and says what they earned meanwhile. */
	private static void release(MinecraftServer server, ServerPlayer player) {
		Deque<Held> held = HUSHED.remove(player.getUUID());
		if (held != null) {
			List<Held> out;
			synchronized (held) {
				out = new ArrayList<>(held);
				held.clear();
			}
			for (Held h : out) {
				player.connection.send(h.packet(), h.listener());
			}
		}
		announce(server, ANNOUNCEMENTS.remove(player.getUUID()));
	}

	private static void announce(MinecraftServer server, @Nullable List<Announcement> said) {
		if (said == null) {
			return;
		}
		for (Announcement a : said) {
			server.getPlayerList().broadcastSystemMessage(a.message(), a.perViewer(), a.overlay());
		}
	}

	/**
	 * Called for every packet sent to a player: true if it's held back (or dropped) because they can't see the world
	 * yet, so the send is cancelled.
	 */
	public static boolean hold(ServerPlayer player, Packet<?> packet, @Nullable ChannelFutureListener listener) {
		Deque<Held> held = HUSHED.get(player.getUUID());
		if (held == null) {
			return false;
		}
		boolean line = false;
		if (packet instanceof ClientboundSystemChatPacket system) {
			if (system.overlay()) {
				return true;
			}
			line = true;
		} else if (!(packet instanceof ClientboundPlayerChatPacket || packet instanceof ClientboundDisguisedChatPacket
			|| packet instanceof ClientboundUpdateAdvancementsPacket || packet instanceof ClientboundRecipeBookAddPacket)) {
			return false;
		}
		synchronized (held) {
			if (line && held.size() >= MAX_LINES) {
				// the oldest plain line goes (never signed chat, whose order the client checks, or an advancement update)
				for (var it = held.iterator(); it.hasNext();) {
					if (it.next().packet() instanceof ClientboundSystemChatPacket) {
						it.remove();
						break;
					}
				}
			}
			held.addLast(new Held(packet, listener));
		}
		return true;
	}

	/** Marks {@code player} as having an advancement awarded on this thread, till {@link #awarded}. */
	public static void awarding(ServerPlayer player) {
		AWARDING.get().push(player);
	}

	/** The award {@link #awarding} marked is over. */
	public static void awarded() {
		AWARDING.get().poll();
	}

	/**
	 * Called for every broadcast system line: true if it's an advancement announcement for a player who can't see the
	 * world yet, held back until they can (so the broadcast is cancelled).
	 */
	public static boolean holdAnnouncement(Component message, Function<ServerPlayer, Component> perViewer, boolean overlay) {
		ServerPlayer earner = AWARDING.get().peek();
		if (earner == null || !HUSHED.containsKey(earner.getUUID())) {
			return false;
		}
		ANNOUNCEMENTS.computeIfAbsent(earner.getUUID(), k -> java.util.Collections.synchronizedList(new ArrayList<>()))
			.add(new Announcement(message, perViewer, overlay));
		return true;
	}
}
