package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the server tells a player's own client about the conditions on them (frightened, held fast or asleep, deafened),
 * so the client can do its part: lock the keys with the eyes left free, slow the feet and shake the aim, muffle the world
 * and whine. Sent only when something changes. The conditions themselves live in {@link Fear}, {@link HeldFast} and
 * {@link Deafened}; the client's reading of this is in {@code FifthClient}.
 */
public final class Afflictions {
	/** Server to player: the keys are locked, until when deafness ends (game time), who frightens them and until when. */
	public record Sync(boolean locked, long deafUntil, int fearSource, long fearUntil) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("afflictions"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBoolean(p.locked);
				buf.writeLong(p.deafUntil);
				buf.writeVarInt(p.fearSource + 1);
				buf.writeLong(p.fearUntil);
			}, buf -> new Sync(buf.readBoolean(), buf.readLong(), buf.readVarInt() - 1, buf.readLong()));

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** The client's copy of the last Sync (read by the client mixins and ticks). */
	public static volatile boolean clientLocked;
	public static volatile long clientDeafUntil, clientFearUntil;
	public static volatile int clientFearSource = -1;

	/** What each player was last told. */
	private static final Map<UUID, Sync> SENT = new HashMap<>();

	private Afflictions() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Afflictions::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SENT.clear());
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> forget(handler.player.getUUID()));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long now = player.level().getGameTime();
			long deaf = Deafened.until(player);
			Fear.Scare scare = Fear.of(player);
			Sync sync = new Sync(HeldFast.locked(player), deaf > now ? deaf : 0L, scare == null ? -1 : scare.source(), scare == null ? 0L : scare.until());
			if (!sync.equals(SENT.get(player.getUUID())) && ServerPlayNetworking.canSend(player, Sync.TYPE)) {
				SENT.put(player.getUUID(), sync);
				ServerPlayNetworking.send(player, sync);
			}
		}
	}

	/** Forget what was sent to a player who left. */
	static void forget(UUID id) {
		SENT.remove(id);
	}
}
