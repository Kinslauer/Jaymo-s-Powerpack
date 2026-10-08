package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Wind Control's packets. */
public final class WindNet {
	private WindNet() {
	}

	/** Client → server: the flight state ({@link WindFlight} ordinal) and whether the cruise speed is locked. */
	public record Flight(int state, boolean locked) implements CustomPacketPayload {
		public static final Type<Flight> TYPE = new Type<>(JaymosPowers.id("wind_flight"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flight> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.state);
				buf.writeBoolean(p.locked);
			},
			buf -> new Flight(buf.readVarInt(), buf.readBoolean()));

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
	 */
	public record Status(int channel, int channelTicks, int[] cooldowns, float dash, boolean stopFlight) implements CustomPacketPayload {
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
			},
			buf -> {
				int channel = buf.readVarInt() - 1;
				int channelTicks = buf.readVarInt();
				int count = Math.min(buf.readVarInt(), 8);
				int[] cooldowns = new int[count];
				for (int i = 0; i < count; i++) {
					cooldowns[i] = buf.readVarInt();
				}
				return new Status(channel, channelTicks, cooldowns, buf.readFloat(), buf.readBoolean());
			});

		@Override
		public Type<Status> type() {
			return TYPE;
		}
	}

	/**
	 * Client → server: the flyer crashed into a wall ({@code wall}) or the ground, losing {@code lost} blocks/tick of
	 * speed. The server turns it into damage (survival only, clamped; see {@code WindPower.onImpact}).
	 */
	public record Impact(float lost, boolean wall) implements CustomPacketPayload {
		public static final Type<Impact> TYPE = new Type<>(JaymosPowers.id("wind_impact"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Impact> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeFloat(p.lost);
				buf.writeBoolean(p.wall);
			},
			buf -> new Impact(buf.readFloat(), buf.readBoolean()));

		@Override
		public Type<Impact> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Flight.TYPE, Flight.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Impact.TYPE, Impact.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
	}
}
