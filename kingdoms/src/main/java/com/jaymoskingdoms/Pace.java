package com.jaymoskingdoms;

import java.util.HashSet;
import java.util.Set;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Travel pace, the walking-slowly end of it. A held key (default Left Alt) drops you to a careful crawl on foot: you
 * move a third slower, make less than half the noise, are harder for watchers to pick out, and notice more of what is
 * around you. The other end is already in the game: sprinting, or riding a horse hard, is loud, shows you from further
 * off ({@link Awareness}, {@link Senses}) and leaves you blind to traps and ambushes ({@link Notice}).
 * <p>
 * Walking slowly only counts on foot and on the ground, and not while sprinting or sneaking (sneaking is quieter still).
 */
public final class Pace {
	/** Walking slowly slows you by this share of your speed. */
	static final double SLOWED = -0.35;
	/** The share of the noise you make; of the distance a watcher sees you from; what it adds to noticing. */
	static final double NOISE = 0.6, SIGHT = 0.8;
	static final float NOTICING = 0.08F;
	/** A horse riding harder than this (blocks a tick) is riding hard. */
	static final double HARD_RIDE = Notice.HARD_RIDE;
	/** How loud riding hard is (blocks), as sprinting is. */
	static final double HOOVES = 14.0;
	/** The speed modifier's name. */
	static final String MODIFIER = "slow_pace";

	/** Player → server: the walk-slowly key is held, or let go. */
	public record Held(boolean down) implements CustomPacketPayload {
		public static final Type<Held> TYPE = new Type<>(JaymosKingdoms.id("slow_pace"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Held> CODEC = ByteBufCodecs.BOOL.map(Held::new, Held::down).cast();

		@Override
		public Type<Held> type() {
			return TYPE;
		}
	}

	private static final Set<UUID> HELD = new HashSet<>();

	private Pace() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Held.TYPE, Held.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Held.TYPE, (payload, context) -> {
			if (payload.down()) {
				HELD.add(context.player().getUUID());
			} else {
				HELD.remove(context.player().getUUID());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Pace::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HELD.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HELD.clear());
	}

	/** Whether {@code player} is walking slowly: key held, on foot, not sprinting, not flying. */
	public static boolean slow(ServerPlayer player) {
		return !HELD.isEmpty() && HELD.contains(player.getUUID()) && !player.isSprinting() && !player.isPassenger()
			&& !player.getAbilities().flying && !player.isFallFlying();
	}

	/** Whether {@code player} is riding a horse hard. */
	public static boolean ridingHard(ServerPlayer player) {
		return player.getVehicle() instanceof AbstractHorse horse && horse.getDeltaMovement().horizontalDistance() > HARD_RIDE;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 5 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean slow = slow(player);
			var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed == null) {
				continue;
			}
			var id = JaymosKingdoms.id(MODIFIER);
			if (slow) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(id, SLOWED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else if (speed.hasModifier(id)) {
				speed.removeModifier(id);
			}
		}
	}
}
