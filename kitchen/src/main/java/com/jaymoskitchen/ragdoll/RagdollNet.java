package com.jaymoskitchen.ragdoll;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * (0.5.1) Ragdoll packets. Whoever simulates a body (the server for a corpse, a player's own client while they're
 * limp) sends its points every tick it moves; everyone else draws them.
 */
public final class RagdollNet {
	private RagdollNet() {
	}

	/** Server → client: where a body's points are (a corpse, or a limp player someone else is watching). */
	public record Pose(int entity, RagdollSnapshot snapshot) implements CustomPacketPayload {
		public static final Type<Pose> TYPE = new Type<>(JaymosKitchen.id("ragdoll_pose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pose> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entity);
				RagdollSnapshot.CODEC.encode(buf, p.snapshot);
			},
			buf -> new Pose(buf.readVarInt(), RagdollSnapshot.CODEC.decode(buf)));

		@Override
		public Type<Pose> type() {
			return TYPE;
		}
	}

	/** Client → server: the sender is limp, and here's their body this tick (relayed to everyone watching them). */
	public record LimpPose(RagdollSnapshot snapshot) implements CustomPacketPayload {
		public static final Type<LimpPose> TYPE = new Type<>(JaymosKitchen.id("limp_pose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LimpPose> CODEC = CustomPacketPayload.codec(
			(p, buf) -> RagdollSnapshot.CODEC.encode(buf, p.snapshot),
			buf -> new LimpPose(RagdollSnapshot.CODEC.decode(buf)));

		@Override
		public Type<LimpPose> type() {
			return TYPE;
		}
	}

	/** Client → server: the sender went limp ({@code on}), or got back up. */
	public record Limp(boolean on) implements CustomPacketPayload {
		public static final Type<Limp> TYPE = new Type<>(JaymosKitchen.id("limp"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Limp> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.on),
			buf -> new Limp(buf.readBoolean()));

		@Override
		public Type<Limp> type() {
			return TYPE;
		}
	}

	/** Server → client: something knocked you limp, flinging you this fast (blocks a tick). */
	public record Knock(float vx, float vy, float vz) implements CustomPacketPayload {
		public static final Type<Knock> TYPE = new Type<>(JaymosKitchen.id("knock_limp"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Knock> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeFloat(p.vx);
				buf.writeFloat(p.vy);
				buf.writeFloat(p.vz);
			},
			buf -> new Knock(buf.readFloat(), buf.readFloat(), buf.readFloat()));

		@Override
		public Type<Knock> type() {
			return TYPE;
		}
	}

	/** Called first thing at startup, before any receiver (see {@code Payloads.register}). */
	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Pose.TYPE, Pose.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Knock.TYPE, Knock.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LimpPose.TYPE, LimpPose.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Limp.TYPE, Limp.CODEC);
	}

	/** Sends a body's points to everyone who can see {@code entity}, except {@code skip} (its own simulator). */
	public static void sendAround(Entity entity, RagdollSnapshot snapshot, ServerPlayer skip) {
		Pose pose = new Pose(entity.getId(), snapshot);
		for (ServerPlayer watcher : PlayerLookup.tracking(entity)) {
			if (watcher != skip) {
				ServerPlayNetworking.send(watcher, pose);
			}
		}
	}

	public static void sendTo(ServerPlayer watcher, Entity entity, RagdollSnapshot snapshot) {
		ServerPlayNetworking.send(watcher, new Pose(entity.getId(), snapshot));
	}
}
