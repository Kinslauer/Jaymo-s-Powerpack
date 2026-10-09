package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: right click while holding something with Lift (choke, then slam). */
public record GravityGrip() implements CustomPacketPayload {
	public static final Type<GravityGrip> TYPE = new Type<>(JaymosPowers.id("gravity_grip"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GravityGrip> CODEC = StreamCodec.unit(new GravityGrip());

	@Override
	public Type<GravityGrip> type() {
		return TYPE;
	}
}
