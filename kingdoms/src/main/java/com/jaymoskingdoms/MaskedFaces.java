package com.jaymoskingdoms;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Who is behind a full mask right now, for every client, near or far (Andy, 2026-11-12, on the secret identity's gaps:
 * "it should disguise them EVERYWHERE"). The tab list and the social interactions screen show a masked player's face as
 * a mask ({@code MaskedFacesClient}) and by the name the tab list gives them, not their username. Also the one test of
 * MCA's family ties ({@code mixin/mca/McaKin*}): a villager doesn't know their masked parent, child or spouse.
 */
public final class MaskedFaces {
	static final int EVERY = 20;

	/** Server → everyone: the players masked now. */
	public record Masked(Set<UUID> ids) implements CustomPacketPayload {
		public static final Type<Masked> TYPE = new Type<>(JaymosKingdoms.id("masked_faces"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Masked> CODEC = StreamCodec.composite(
			ByteBufCodecs.collection(HashSet::new, UUIDUtil.STREAM_CODEC), Masked::ids, Masked::new);

		@Override
		public Type<Masked> type() {
			return TYPE;
		}
	}

	private static Set<UUID> last = Set.of();

	private MaskedFaces() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Masked.TYPE, Masked.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(MaskedFaces::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, last));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> last = Set.of());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		Set<UUID> now = new HashSet<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Identities.masked(p)) {
				now.add(p.getUUID());
			}
		}
		if (now.equals(last)) {
			return;
		}
		last = Set.copyOf(now);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			send(p, last);
		}
	}

	private static void send(ServerPlayer player, Set<UUID> ids) {
		if (ServerPlayNetworking.canSend(player, Masked.TYPE)) {
			ServerPlayNetworking.send(player, new Masked(ids));
		}
	}

	/**
	 * For MCA's family tests: {@code who} is a player masked from {@code villager} (the same mask MCA's memories go by,
	 * {@link Identities#maskedAs}), so their ties to them don't show.
	 */
	public static boolean kinHidden(Object villager, Object who) {
		return villager instanceof LivingEntity && who instanceof Player p && Identities.maskedAs(p) != null;
	}
}
