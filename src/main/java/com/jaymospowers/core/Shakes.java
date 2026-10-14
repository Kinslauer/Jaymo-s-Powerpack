package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Every power's screen shake, one system: a shake has a strength (degrees at its peak), a length, and usually a place
 * it came from, so the view is kicked away from the blow (you feel which side a slam landed on) before it rattles and
 * dies away. {@link #around} reaches everyone near, weaker with distance. The client side is {@code ScreenShake}.
 */
public final class Shakes {
	/** A shake's length when nobody says: this many ticks, and this many more per degree (a big one rumbles on). */
	public static final int BASE_TICKS = 10, TICKS_PER_DEGREE = 5;
	/** The longest a shake can ask for. */
	public static final int MAX_TICKS = 60;
	/** How a shake fades with distance: (1 - d/range)^this, so nothing past {@code range}. */
	public static final double FALLOFF_POWER = 1.5;
	/** Bystanders feel a mover's own shake ({@link #heavy}) at this share. */
	public static final float BYSTANDER_SHARE = 0.5F;

	private Shakes() {
	}

	/**
	 * Server → client: shake your screen. {@code strength} in degrees at the peak, over {@code ticks}; {@code from} the
	 * spot it came from (the view is kicked away from it), or null for a shake from nowhere in particular.
	 */
	public record Shake(float strength, int ticks, Vec3 from) implements CustomPacketPayload {
		public static final Type<Shake> TYPE = new Type<>(JaymosPowers.id("screen_shake"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shake> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeFloat(p.strength);
				buf.writeVarInt(p.ticks);
				buf.writeBoolean(p.from != null);
				if (p.from != null) {
					Vec3.STREAM_CODEC.encode(buf, p.from);
				}
			},
			buf -> new Shake(buf.readFloat(), buf.readVarInt(), buf.readBoolean() ? Vec3.STREAM_CODEC.decode(buf) : null));

		@Override
		public Type<Shake> type() {
			return TYPE;
		}
	}

	/** How long a shake of {@code strength} degrees lasts when nobody says. */
	public static int ticksFor(float strength) {
		return Math.clamp(BASE_TICKS + Math.round(strength * TICKS_PER_DEGREE), BASE_TICKS, MAX_TICKS);
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Shake.TYPE, Shake.CODEC);
	}

	/** Shake one player's screen, from nowhere in particular. */
	public static void send(ServerPlayer player, float strength) {
		send(player, strength, ticksFor(strength), null);
	}

	/** Shake one player's screen, kicked away from {@code from}. */
	public static void send(ServerPlayer player, float strength, Vec3 from) {
		send(player, strength, ticksFor(strength), from);
	}

	public static void send(ServerPlayer player, float strength, int ticks, Vec3 from) {
		if (strength > 0.02F) {
			ServerPlayNetworking.send(player, new Shake(strength, Math.clamp(ticks, 1, MAX_TICKS), from));
		}
	}

	/** Everyone within {@code range} of {@code at} feels it, weaker with distance, kicked away from {@code at}. */
	public static void around(ServerLevel level, Vec3 at, double range, float strength) {
		around(level, at, range, strength, ticksFor(strength), null);
	}

	/** As {@link #around(ServerLevel, Vec3, double, float)}, but {@code except} (who gets their own) is left out. */
	public static void around(ServerLevel level, Vec3 at, double range, float strength, int ticks, ServerPlayer except) {
		for (ServerPlayer player : level.players()) {
			if (player == except) {
				continue;
			}
			double d = player.position().distanceTo(at);
			if (d <= range) {
				send(player, strength * (float)Math.pow(1.0 - d / range, FALLOFF_POWER), ticks, at);
			}
		}
	}

	/**
	 * A heavy body's own impact (a landing, a slam, a stomp): the mover feels {@code strength} from under/behind it,
	 * everyone else within {@code range} feels a share of it from where it hit.
	 */
	public static void heavy(ServerPlayer player, Vec3 at, double range, float strength) {
		heavy(player, at, range, strength, ticksFor(strength));
	}

	public static void heavy(ServerPlayer player, Vec3 at, double range, float strength, int ticks) {
		send(player, strength, ticks, at);
		around(player.level(), at, range, strength * BYSTANDER_SHARE, ticks, player);
	}
}
