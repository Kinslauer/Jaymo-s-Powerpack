package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: a meteor has burst at ({@code x}, {@code y}, {@code z}), as big as {@code charge} (0 to 1), its
 * blast reaching {@code radius}. The client throws the debris (chunks of {@code states}, by block state id: the ground
 * it hit and the rock itself) and raises the dust and smoke that hang over the crater a long while (GravityMeteorFx).
 */
public record GravityMeteorBurst(double x, double y, double z, float charge, float radius, List<Integer> states) implements CustomPacketPayload {
	public static final Type<GravityMeteorBurst> TYPE = new Type<>(JaymosPowers.id("gravity_meteor_burst"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GravityMeteorBurst> CODEC = CustomPacketPayload.codec(
		(p, buf) -> {
			buf.writeDouble(p.x);
			buf.writeDouble(p.y);
			buf.writeDouble(p.z);
			buf.writeFloat(p.charge);
			buf.writeFloat(p.radius);
			buf.writeVarInt(p.states.size());
			for (int state : p.states) {
				buf.writeVarInt(state);
			}
		},
		buf -> {
			double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
			float charge = buf.readFloat(), radius = buf.readFloat();
			int count = Math.min(buf.readVarInt(), 32);
			List<Integer> states = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				states.add(buf.readVarInt());
			}
			return new GravityMeteorBurst(x, y, z, charge, radius, states);
		});

	@Override
	public Type<GravityMeteorBurst> type() {
		return TYPE;
	}
}
