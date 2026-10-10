package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Wind Control's packets. */
public final class WindNet {
	private WindNet() {
	}

	/** Client → server: the flight state ({@link WindFlight} ordinal) and whether the cruise speed is locked. */
	public record Flight(int state, boolean locked, boolean braking) implements CustomPacketPayload {
		public static final Type<Flight> TYPE = new Type<>(JaymosPowers.id("wind_flight"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flight> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.state);
				buf.writeBoolean(p.locked);
				buf.writeBoolean(p.braking);
			},
			buf -> new Flight(buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));

		@Override
		public Type<Flight> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: ability cooldowns and channel state, plus one-off events. Sent whenever something changes; the
	 * client counts cooldowns down and the channel up by itself in between.
	 *
	 * @param channel   the {@link WindAbility} being channelled, or -1
	 * @param channelTicks how long it has been channelled
	 * @param cooldowns ticks left on each ability's cooldown
	 * @param dash      a Tailwind dash to apply now (blocks/tick), or 0
	 * @param stopFlight the server ended (or refused) the flight
	 * @param lanceCooldown ticks left on Cyclone Lance (the ascended ability)
	 * @param gustCooldown ticks left on the ground Gust (G on the ground; Tailwind, G in the air, has its own)
	 */
	public record Status(int channel, int channelTicks, int[] cooldowns, float dash, boolean stopFlight, int lanceCooldown,
		int gustCooldown) implements CustomPacketPayload {
		public static final Type<Status> TYPE = new Type<>(JaymosPowers.id("wind_status"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.channel + 1);
				buf.writeVarInt(p.channelTicks);
				buf.writeVarInt(p.cooldowns.length);
				for (int cooldown : p.cooldowns) {
					buf.writeVarInt(cooldown);
				}
				buf.writeFloat(p.dash);
				buf.writeBoolean(p.stopFlight);
				buf.writeVarInt(p.lanceCooldown);
				buf.writeVarInt(p.gustCooldown);
			},
			buf -> {
				int channel = buf.readVarInt() - 1;
				int channelTicks = buf.readVarInt();
				int count = Math.min(buf.readVarInt(), 8);
				int[] cooldowns = new int[count];
				for (int i = 0; i < count; i++) {
					cooldowns[i] = buf.readVarInt();
				}
				float dash = buf.readFloat();
				boolean stop = buf.readBoolean();
				return new Status(channel, channelTicks, cooldowns, dash, stop, buf.readVarInt(), buf.readVarInt());
			});

		@Override
		public Type<Status> type() {
			return TYPE;
		}
	}

	/**
	 * Client → server: the flyer crashed into a wall ({@code wall}) or the ground, losing {@code lost} blocks/tick of
	 * speed while heading {@code dir}. The server turns it into damage (survival only, clamped) and, hard enough, smashes
	 * what was hit (see {@code WindPower.onImpact}).
	 */
	public record Impact(float lost, boolean wall, Vec3 dir) implements CustomPacketPayload {
		public static final Type<Impact> TYPE = new Type<>(JaymosPowers.id("wind_impact"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Impact> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeFloat(p.lost);
				buf.writeBoolean(p.wall);
				Vec3.STREAM_CODEC.encode(buf, p.dir);
			},
			buf -> new Impact(buf.readFloat(), buf.readBoolean(), Vec3.STREAM_CODEC.decode(buf)));

		@Override
		public Type<Impact> type() {
			return TYPE;
		}
	}

	/** Client → server: the flyer ploughed through creature {@code entity} at {@code speed} blocks/tick, heading {@code dir}. */
	public record Ram(int entity, float speed, Vec3 dir) implements CustomPacketPayload {
		public static final Type<Ram> TYPE = new Type<>(JaymosPowers.id("wind_ram"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ram> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entity);
				buf.writeFloat(p.speed);
				Vec3.STREAM_CODEC.encode(buf, p.dir);
			},
			buf -> new Ram(buf.readVarInt(), buf.readFloat(), Vec3.STREAM_CODEC.decode(buf)));

		@Override
		public Type<Ram> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: a screen effect for this player (they needn't have the wind themselves: modes and the Thunderhead
	 * reach everyone nearby).
	 *
	 * @param kind  {@link #VIGNETTE} (a coloured frame that fades out) or {@link #LIGHTNING} (a white flash, a black
	 *              after-image, a second flicker)
	 * @param color 0xRRGGBB of a vignette
	 * @param ticks how long it lasts
	 */
	public record ScreenFx(int kind, int color, int ticks) implements CustomPacketPayload {
		public static final int VIGNETTE = 0, LIGHTNING = 1;
		public static final Type<ScreenFx> TYPE = new Type<>(JaymosPowers.id("wind_screen_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScreenFx> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.kind);
				buf.writeInt(p.color);
				buf.writeVarInt(p.ticks);
			},
			buf -> new ScreenFx(buf.readVarInt(), buf.readInt(), Math.min(buf.readVarInt(), 400)));

		@Override
		public Type<ScreenFx> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Flight.TYPE, Flight.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Impact.TYPE, Impact.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Ram.TYPE, Ram.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScreenFx.TYPE, ScreenFx.CODEC);
	}
}
