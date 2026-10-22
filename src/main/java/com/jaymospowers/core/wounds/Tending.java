package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.15.96) Down on one knee at someone's side, hands at work: a player treating another ({@link Treatment}) or bringing
 * a downed one round ({@link Downed}). Andy (2026-10-22): "hold right click with the item on them and it will play a
 * kneeling interaction animation". Drawn for everyone (the client's {@code TendPose}). Whoever runs the work calls
 * {@link #keep} every tick it goes on; a player nobody kept for {@link #LINGER} ticks gets up.
 */
public final class Tending {
	/** Ticks without a {@link #keep} before the healer gets up. */
	static final long LINGER = 2L;

	/** Kneeling by {@code target} (entity id), since {@code since} (game time). */
	public record Tend(int target, long since) {
		public static final StreamCodec<ByteBuf, Tend> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Tend::target,
			ByteBufCodecs.VAR_LONG, Tend::since,
			Tend::new);
	}

	public static final AttachmentType<Tend> TEND = AttachmentRegistry.create(JaymosPowers.id("tending"),
		builder -> builder.syncWith(Tend.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Healer → the game time they were last kept kneeling. */
	private static final Map<UUID, Long> KEPT = new HashMap<>();

	private Tending() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Tending::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> KEPT.clear());
	}

	/** {@code healer} is still at work on {@code body} this tick (nothing for treating yourself). */
	static void keep(ServerPlayer healer, LivingEntity body) {
		if (body == healer) {
			return;
		}
		long now = healer.level().getGameTime();
		Tend tend = healer.getAttached(TEND);
		if (tend == null || tend.target() != body.getId()) {
			healer.setAttached(TEND, new Tend(body.getId(), now));
		}
		KEPT.put(healer.getUUID(), now);
	}

	private static void tick(MinecraftServer server) {
		if (KEPT.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Long>> it = KEPT.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			if (player.level().getGameTime() - e.getValue() > LINGER) {
				player.removeAttached(TEND);
				it.remove();
			}
		}
	}
}
