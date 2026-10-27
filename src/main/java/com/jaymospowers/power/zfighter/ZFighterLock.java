package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.42, Andy's NOEA picks) Lock-on, the server half. The client picks the living thing nearest the crosshair and
 * sends its id ({@link LockOn}); the server checks it (alive, in sight, within {@link #RANGE}) and keeps it. While it
 * holds, the hold combo, Flash Step and the ki blasts aim at it ({@link #target}, {@link #aim}). It lets go on its own
 * past {@link #RANGE}, or after {@link #BLIND_TICKS} out of sight.
 */
public final class ZFighterLock {
	public static final double RANGE = 32.0;
	/** The server allows this much more than the client's range, so the two never disagree at the edge. */
	static final double SLACK = 2.0;
	/** Ticks out of sight before the lock lets go (a pillar flickering past isn't losing sight). */
	static final int BLIND_TICKS = 15;

	/** Client to server: lock onto entity {@code id}, or let go (-1). */
	public record LockOn(int id) implements CustomPacketPayload {
		public static final Type<LockOn> TYPE = new Type<>(JaymosPowers.id("zfighter_lock_on"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LockOn> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.id),
			buf -> new LockOn(buf.readVarInt()));

		@Override
		public Type<LockOn> type() {
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

	private ZFighterLock() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(LockOn.TYPE, LockOn.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LockOn.TYPE, (payload, ctx) -> set(ctx.player(), payload.id()));
	}

	private static void set(ServerPlayer player, int id) {
		if (id < 0 || !Powers.has(player, ZFighterPower.INSTANCE) || !player.isAlive()) {
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
	static void tick(ServerPlayer player) {
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

	static void drop(ServerPlayer player) {
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

	/** The way his ki goes: at the locked foe, else wherever he looks. */
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
