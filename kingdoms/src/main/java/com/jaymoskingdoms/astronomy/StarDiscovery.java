package com.jaymoskingdoms.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Which stars each player has discovered, kept on the server so the armillary sphere, the telescope and the journal can
 * all read it. It lives on the player (saved, kept through death, cleared with the rest of a reincarnated life) and is
 * synced to that player's client, which draws the constellations from it.
 * <p>
 * The client watches where the player looks and, after a few seconds of steady looking at a star, reports it
 * ({@link Looked}). The server takes the report only from an Overworld player under an open night sky, and only so many
 * at a time, so a modified client can't hand in the whole catalogue at once.
 */
public final class StarDiscovery {
	/** Hipparcos numbers a player has discovered, in the order found. */
	public static final AttachmentType<List<Integer>> DISCOVERED = AttachmentRegistry.create(JaymosKingdoms.id("stars_discovered"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** The highest Hipparcos number in the catalogue is about 120,000; anything past this is not a star. */
	static final int MAX_HIP = 130_000;
	/** Reports a player can bank (a cluster of stars in view can finish together), and ticks to earn one back. */
	static final int BURST = 8, REFILL_TICKS = 10;

	/** By player: game time of their last report, and reports still in hand then. */
	private static final Map<UUID, long[]> BUDGET = new HashMap<>();

	/** Player to server: they have looked at star {@code hip} long enough to know it. */
	public record Looked(int hip) implements CustomPacketPayload {
		public static final Type<Looked> TYPE = new Type<>(JaymosKingdoms.id("star_looked"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Looked> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Looked::hip, Looked::new);

		@Override
		public Type<Looked> type() {
			return TYPE;
		}
	}

	private StarDiscovery() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Looked.TYPE, Looked.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Looked.TYPE, (payload, context) -> looked(context.player(), payload.hip()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BUDGET.clear());
	}

	/** {@code player}'s discovered stars (empty if none). */
	public static List<Integer> discovered(ServerPlayer player) {
		return player.getAttachedOrElse(DISCOVERED, List.of());
	}

	private static void looked(ServerPlayer player, int hip) {
		if (hip <= 0 || hip > MAX_HIP || !(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
			|| !level.isDarkOutside() || !level.canSeeSky(player.blockPosition().above()) || SunMoon.blinded(player)) {
			return;
		}
		List<Integer> known = discovered(player);
		if (known.contains(hip) || !spend(player, level.getGameTime())) {
			return;
		}
		List<Integer> next = new ArrayList<>(known);
		next.add(hip);
		player.setAttached(DISCOVERED, List.copyOf(next));
	}

	/** Takes one report from {@code player}'s budget; false if they have none left. */
	private static boolean spend(ServerPlayer player, long now) {
		long[] b = BUDGET.computeIfAbsent(player.getUUID(), id -> new long[] {now, BURST});
		long earned = Math.max(0L, now - b[0]) / REFILL_TICKS;
		if (earned > 0) {
			b[1] = Math.min(BURST, b[1] + earned);
			b[0] += earned * REFILL_TICKS;
		}
		if (b[1] <= 0) {
			return false;
		}
		b[1]--;
		return true;
	}
}
