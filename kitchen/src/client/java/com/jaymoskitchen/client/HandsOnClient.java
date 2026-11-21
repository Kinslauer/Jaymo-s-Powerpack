package com.jaymoskitchen.client;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Minigames;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.net.payloads.HandsOnFinishPayload;
import com.jaymoskitchen.net.payloads.HandsOnInputPayload;
import com.jaymoskitchen.net.payloads.HandsOnStartPayload;
import com.jaymoskitchen.net.payloads.HandsOnSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side handler for hands-on cooking sessions. Receives start/sync/finish packets
 * and manages the client-side state for rendering in-world minigames.
 */
public final class HandsOnClient {
	/** Active hands-on sessions on the client, keyed by session UUID. */
	private static final Map<UUID, ClientSession> SESSIONS = new HashMap<>();

	private HandsOnClient() {
	}

	/** Called when a hands-on session starts. */
	public static void onStart(HandsOnStartPayload payload) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Dish dish = Dish.byIndex(payload.dishOrdinal());
		Station station = Station.byIndex(payload.stationOrdinal());
		if (dish == null || station == null) {
			return;
		}
		UUID sessionId = new UUID(0, payload.sessionId()); // sessionId is int in payload
		ClientSession session = new ClientSession(sessionId, player.getUUID(), dish, station, payload.pos(), level.getGameTime());
		SESSIONS.put(sessionId, session);
	}

	/** Called when a hands-on session receives a sync packet (for server-simmed methods). */
	public static void onSync(HandsOnSyncPayload payload) {
		ClientSession session = SESSIONS.get(payload.sessionId());
		if (session != null) {
			session.heat = payload.heat();
			session.progress = payload.progress();
			session.status = payload.status();
			session.quality = payload.quality();
		}
	}

	/** Called when a hands-on session finishes. */
	public static void onFinish(HandsOnFinishPayload payload) {
		ClientSession session = SESSIONS.remove(payload.sessionId());
		if (session != null) {
			// Play finish sound/particles based on quality
			ClientLevel level = Minecraft.getInstance().level;
			if (level != null) {
				// TODO: play finish effects at session.stationPos
			}
		}
	}

	/** Sends a CHOP input to the server. */
	public static void sendChopInput(UUID sessionId, float cutOffset) {
		var payload = new HandsOnInputPayload.ChopInput(sessionId, cutOffset);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
	}

	/** Sends a STIR input to the server. */
	public static void sendStirInput(UUID sessionId, float angle, float speed, boolean boiling) {
		var payload = new HandsOnInputPayload.StirInput(sessionId, angle, speed, boiling);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
	}

	/** Sends an abandon packet to the server. */
	public static void sendAbandon(UUID sessionId) {
		var payload = new com.jaymoskitchen.net.payloads.HandsOnAbandonPayload(sessionId);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
	}

	/** Gets the active session for the local player at the given position, if any. */
	public static ClientSession getSession(Player player, BlockPos pos) {
		for (ClientSession session : SESSIONS.values()) {
			if (session.playerId.equals(player.getUUID()) && session.stationPos.equals(pos)) {
				return session;
			}
		}
		return null;
	}

	/** Cleans up all sessions (e.g. on disconnect). */
	public static void clear() {
		SESSIONS.clear();
	}

	/** Client-side session state for rendering. */
	public static final class ClientSession {
		public final UUID sessionId;
		public final UUID playerId;
		public final Dish dish;
		public final Station station;
		public final BlockPos stationPos;
		public final long startTime;

		// CHOP state
		public float slidePos = -0.25F;
		public float slideSpeed = Minigames.CHOP_SPEED;
		public int nextCut = 0;
		public int perfects = 0;
		public int goods = 0;
		public int misses = 0;
		public java.util.List<Float> cutPositions = new java.util.ArrayList<>();
		public java.util.List<Integer> results = new java.util.ArrayList<>();
		public int chopAnim = -1;
		public int feedback = 0;
		public int lastResult = 0;
		public int doneTicks = -1;

		// STIR state
		public boolean placed = false;
		public float lastAngle = Float.NaN;
		public float omega = 0.0F;
		public int stirred = 0;
		public int stuck = 0;
		public int spilled = 0;
		public int boilTicks = 0;
		public int nextBoil = Minigames.BOIL_GAP_MIN + new java.util.Random().nextInt(Minigames.BOIL_GAP_MAX - Minigames.BOIL_GAP_MIN);
		public float spoonAngle = 0.0F;

		// Shared for server-simmed methods
		public float heat = 0.0F;
		public float progress = 0.0F;
		public int status = 0;
		public int quality = 0;

		public ClientSession(UUID sessionId, UUID playerId, Dish dish, Station station, BlockPos stationPos, long startTime) {
			this.sessionId = sessionId;
			this.playerId = playerId;
			this.dish = dish;
			this.station = station;
			this.stationPos = stationPos;
			this.startTime = startTime;

			// Initialize CHOP cut positions
			if (dish.method() == Dish.Method.CHOP) {
				for (int i = 0; i < Minigames.CUTS; i++) {
					cutPositions.add((i + 0.5F) * Minigames.MARK_GAP);
				}
			}
			// Initialize STIR nextBoil
			if (dish.method() == Dish.Method.STIR) {
				nextBoil = Minigames.BOIL_GAP_MIN + new java.util.Random().nextInt(Minigames.BOIL_GAP_MAX - Minigames.BOIL_GAP_MIN);
			}
		}

		public boolean boiling() {
			return boilTicks > 0;
		}
	}
}