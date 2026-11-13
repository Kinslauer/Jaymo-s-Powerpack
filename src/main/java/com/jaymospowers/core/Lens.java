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
 * A black hole that bends the light round it. The server only says where one hangs, how big it is and for how long
 * ({@link #open}); each client warps its own screen about that point (the client's {@code LensClient}). Gravity's
 * Singularity and Nadir's Event Horizon open one. It changes nothing but the look.
 */
public final class Lens {
	/** Players within this far of a black hole are told of it (blocks). */
	public static final double TOLD_WITHIN = 96.0;

	/** Server to client: a black hole at (x, y, z) that bends light out to {@code radius} blocks for {@code ticks}. */
	public record Open(double x, double y, double z, float radius, int ticks) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("lens_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeFloat(p.radius);
				buf.writeVarInt(p.ticks);
			},
			buf -> new Open(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt()));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	private Lens() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
	}

	/** The black hole at {@code at} is gone before its time. */
	public static void close(ServerLevel level, Vec3 at) {
		open(level, at, 0.0F, 0);
	}

	/** A black hole opens at {@code at}: everyone near enough sees the light bend round it. */
	public static void open(ServerLevel level, Vec3 at, float radius, int ticks) {
		Open open = new Open(at.x, at.y, at.z, radius, ticks);
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at) <= TOLD_WITHIN * TOLD_WITHIN && ServerPlayNetworking.canSend(player, Open.TYPE)) {
				ServerPlayNetworking.send(player, open);
			}
		}
	}
}
