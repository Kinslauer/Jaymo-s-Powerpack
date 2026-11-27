package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Andy (2026-11-26): "the talk option should change with context. saying "busy" when they are". Whether someone would
 * stop to talk ({@link TalkBusy#why}) is only known on the server, so every {@link #EVERY} ticks each player is told
 * which of the MCA villagers within {@link #RANGE} blocks are busy just now, and with what; {@link ContextLines} reads it
 * for the talk line by the crosshair. Sent only when it changed, so a quiet street sends nothing.
 */
public final class ContextBusy {
	// ===== the knobs ================================================================================================
	/** Ticks between looks, and how far around each player (blocks) people are looked over. */
	static final int EVERY = 10;
	static final double RANGE = 8.0;
	// ================================================================================================================

	/** Server → client: the busy people near the player, by entity id, each with its {@link TalkBusy.Why} ordinal. */
	public record Busy(List<Integer> ids, List<Integer> whys) implements CustomPacketPayload {
		public static final Type<Busy> TYPE = new Type<>(JaymosKingdoms.id("context_busy"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Busy> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Busy::ids,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Busy::whys,
			Busy::new);

		@Override
		public Type<Busy> type() {
			return TYPE;
		}
	}

	/** Player → what they were last told. */
	private static final Map<UUID, Map<Integer, Integer>> SENT = new HashMap<>();
	/** The client's copy: entity id → {@link TalkBusy.Why} ordinal (set by the client's receiver). */
	private static volatile Map<Integer, Integer> heard = Map.of();

	private ContextBusy() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Busy.TYPE, Busy.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ContextBusy::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT.clear());
	}

	private static void tick(MinecraftServer server) {
		if (!McaCompat.LOADED || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !ServerPlayNetworking.canSend(player, Busy.TYPE)) {
				continue;
			}
			Map<Integer, Integer> now = new HashMap<>();
			for (PathfinderMob mob : player.level().getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(RANGE),
				m -> m.isAlive() && (m instanceof Villager || m instanceof Folk) && McaCompat.isMca(m))) {
				TalkBusy.Why why = TalkBusy.why(mob, player);
				if (why != null) {
					now.put(mob.getId(), why.ordinal());
				}
			}
			Map<Integer, Integer> last = SENT.get(player.getUUID());
			if (now.equals(last == null ? Map.of() : last)) {
				continue;
			}
			SENT.put(player.getUUID(), now);
			List<Integer> ids = new ArrayList<>(now.size()), whys = new ArrayList<>(now.size());
			now.forEach((id, why) -> {
				ids.add(id);
				whys.add(why);
			});
			ServerPlayNetworking.send(player, new Busy(ids, whys));
		}
	}

	/** On the client, from its receiver: what the server last said. */
	public static void heard(Busy busy) {
		Map<Integer, Integer> map = new HashMap<>();
		for (int i = 0; i < busy.ids().size() && i < busy.whys().size(); i++) {
			map.put(busy.ids().get(i), busy.whys().get(i));
		}
		heard = map;
	}

	/** On the client: why the one with entity id {@code id} won't stop to talk, as a lang key's last part, or null. */
	static String why(int id) {
		Integer why = heard.get(id);
		TalkBusy.Why[] all = TalkBusy.Why.values();
		return why == null || why < 0 || why >= all.length ? null : all[why].name().toLowerCase(java.util.Locale.ROOT);
	}

	/** On the client: forget it all (a new world). */
	public static void clear() {
		heard = Map.of();
	}
}
