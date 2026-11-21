package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Darktide's packets. */
public final class DarktideNet {
	private DarktideNet() {
	}

	/** Client → server: the guard (right click held with guarding hands) went up or down. */
	public record Guard(boolean up) implements CustomPacketPayload {
		public static final Type<Guard> TYPE = new Type<>(JaymosPowers.id("darktide_guard"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Guard> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.up), buf -> new Guard(buf.readBoolean()));

		@Override
		public Type<Guard> type() {
			return TYPE;
		}
	}

	/** Client → server: the Power Skill key (X) went down or up (Gedan: Stillwater). */
	public record Skill(boolean down) implements CustomPacketPayload {
		public static final Type<Skill> TYPE = new Type<>(JaymosPowers.id("darktide_skill"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Skill> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.down), buf -> new Skill(buf.readBoolean()));

		@Override
		public Type<Skill> type() {
			return TYPE;
		}
	}

	/**
	 * Client → server: C for a Darktide that's corrupted in its own way but neither ascended nor Fate-corrupted (Drown).
	 * The core only forwards C for ascended (or Fate-corrupted) powers, and Darktide's own corruption isn't Fate's.
	 */
	public record NightKey(boolean down) implements CustomPacketPayload {
		public static final Type<NightKey> TYPE = new Type<>(JaymosPowers.id("darktide_night_key"));
		public static final StreamCodec<RegistryFriendlyByteBuf, NightKey> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.down), buf -> new NightKey(buf.readBoolean()));

		@Override
		public Type<NightKey> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: a flash (and, for a riposte, a freeze frame) between {@code entity} and {@code target}. {@code kind}:
	 * {@link #RIPOSTE}, {@link #HIT} (a Riptide flurry's hit), {@link #PARRY}.
	 */
	public record Fx(int kind, int entity, int target) implements CustomPacketPayload {
		public static final int RIPOSTE = 0, HIT = 1, PARRY = 2;
		public static final Type<Fx> TYPE = new Type<>(JaymosPowers.id("darktide_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.kind);
				buf.writeVarInt(p.entity);
				buf.writeVarInt(p.target);
			}, buf -> new Fx(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Fx> type() {
			return TYPE;
		}
	}

	/** Server → client: the soul menu opens. {@code names}: the entity type of each shadow held; {@code picked}: which are in the wave; {@code max}: the most a wave holds. */
	public record Souls(java.util.List<String> names, java.util.List<Integer> picked, int max) implements CustomPacketPayload {
		public static final Type<Souls> TYPE = new Type<>(JaymosPowers.id("darktide_souls"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Souls> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.names.size());
				for (String name : p.names) {
					buf.writeUtf(name);
				}
				writeInts(buf, p.picked);
				buf.writeVarInt(p.max);
			}, buf -> {
				java.util.List<String> names = new java.util.ArrayList<>();
				for (int n = Math.min(buf.readVarInt(), 64); n > 0; n--) {
					names.add(buf.readUtf());
				}
				return new Souls(names, readInts(buf), buf.readVarInt());
			});

		@Override
		public Type<Souls> type() {
			return TYPE;
		}
	}

	/** Client → server: the soul menu closed with these shadows (their places in the list it was sent) picked as the wave. */
	public record SetWave(java.util.List<Integer> picked) implements CustomPacketPayload {
		public static final Type<SetWave> TYPE = new Type<>(JaymosPowers.id("darktide_set_wave"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetWave> CODEC = CustomPacketPayload.codec(
			(p, buf) -> writeInts(buf, p.picked), buf -> new SetWave(readInts(buf)));

		@Override
		public Type<SetWave> type() {
			return TYPE;
		}
	}

	private static void writeInts(RegistryFriendlyByteBuf buf, java.util.List<Integer> ints) {
		buf.writeVarInt(ints.size());
		for (int i : ints) {
			buf.writeVarInt(i);
		}
	}

	private static java.util.List<Integer> readInts(RegistryFriendlyByteBuf buf) {
		java.util.List<Integer> ints = new java.util.ArrayList<>();
		for (int n = Math.min(buf.readVarInt(), 64); n > 0; n--) {
			ints.add(buf.readVarInt());
		}
		return ints;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Souls.TYPE, Souls.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetWave.TYPE, SetWave.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Guard.TYPE, Guard.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Skill.TYPE, Skill.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(NightKey.TYPE, NightKey.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Fx.TYPE, Fx.CODEC);
	}

	/** Sends an effect to everyone close enough to see {@code at}. */
	static void fx(ServerPlayer source, Entity at, Fx fx) {
		for (ServerPlayer viewer : source.level().players()) {
			if (viewer.distanceToSqr(at) < 64.0 * 64.0 && ServerPlayNetworking.canSend(viewer, Fx.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}
}
