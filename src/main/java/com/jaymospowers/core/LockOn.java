package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.45, Andy's NOEA picks) Lock-on, the server half, for everyone (Andy: "make lock on middle mouse, and it should
 * work across the board"). The client picks the living thing nearest the crosshair and sends its id ({@link Packet});
 * the server checks it (alive, in sight, within {@link #RANGE}) and keeps it. The client turns the camera to it, so
 * every power, bow and blow aims there; a Z Fighter's hold combo, Flash Step and ki blasts also aim at it directly
 * ({@link #target}, {@link #aim}). It lets go on its own past {@link #RANGE}, or after {@link #BLIND_TICKS} out of sight.
 */
public final class LockOn {
	public static final double RANGE = 32.0;
	/** The server allows this much more than the client's range, so the two never disagree at the edge. */
	static final double SLACK = 2.0;
	/** Ticks out of sight before the lock lets go (a pillar flickering past isn't losing sight). */
	static final int BLIND_TICKS = 15;

	/** Client to server: lock onto entity {@code id}, or let go (-1). */
	public record Packet(int id) implements CustomPacketPayload {
		public static final Type<Packet> TYPE = new Type<>(JaymosPowers.id("lock_on"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Packet> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.id),
			buf -> new Packet(buf.readVarInt()));

		@Override
		public Type<Packet> type() {
			return TYPE;
		}
	}

	private static final class Lock {
		final UUID target;
		int blind;

		Lock(UUID target) {
			this.target = target;
		}
	}

	private static final Map<UUID, Lock> LOCKS = new HashMap<>();
	/** (0.17.122) The client's own lock (LockOnClient fills it in), so code run on both sides can ask either. */
	public static Function<Player, @Nullable LivingEntity> clientTarget = player -> null;

	private LockOn() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Packet.TYPE, Packet.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Packet.TYPE, (payload, ctx) -> set(ctx.player(), payload.id()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> drop(handler.player));
	}

	private static void set(ServerPlayer player, int id) {
		if (id < 0 || !player.isAlive()) {
			LOCKS.remove(player.getUUID());
			return;
		}
		if (player.level().getEntity(id) instanceof LivingEntity living && usable(player, living)) {
			LOCKS.put(player.getUUID(), new Lock(living.getUUID()));
		}
	}

	private static boolean usable(ServerPlayer player, LivingEntity living) {
		return living != player && living.isAlive() && living.level() == player.level() && !living.isSpectator()
			&& player.distanceTo(living) <= RANGE + SLACK;
	}

	/** Every tick: drops a lock whose target is gone, too far, or has been out of sight too long. */
	private static void tick(ServerPlayer player) {
		Lock lock = LOCKS.get(player.getUUID());
		if (lock == null) {
			return;
		}
		Entity found = player.level().getEntity(lock.target);
		if (!(found instanceof LivingEntity living) || !usable(player, living) || !player.isAlive()) {
			LOCKS.remove(player.getUUID());
			return;
		}
		lock.blind = player.hasLineOfSight(living) ? 0 : lock.blind + 1;
		if (lock.blind > BLIND_TICKS) {
			LOCKS.remove(player.getUUID());
		}
	}

	public static void drop(ServerPlayer player) {
		LOCKS.remove(player.getUUID());
	}

	/** Who he's locked onto, or null. */
	public static @Nullable LivingEntity target(ServerPlayer player) {
		Lock lock = LOCKS.get(player.getUUID());
		if (lock == null) {
			return null;
		}
		return player.level().getEntity(lock.target) instanceof LivingEntity living && usable(player, living) ? living : null;
	}

	/** (0.17.122) Who {@code player} is locked onto on whichever side this runs, or null. */
	public static @Nullable LivingEntity target(Player player) {
		return player instanceof ServerPlayer serverPlayer ? target(serverPlayer)
			: player.level().isClientSide() ? clientTarget.apply(player) : null;
	}

	/** The way his shot goes: at the locked foe, else wherever he looks. */
	public static Vec3 aim(ServerPlayer player) {
		LivingEntity locked = target(player);
		if (locked != null) {
			Vec3 to = locked.getBoundingBox().getCenter().subtract(player.getEyePosition());
			if (to.lengthSqr() > 1.0E-4) {
				return to.normalize();
			}
		}
		return player.getViewVector(1.0F);
	}
}
