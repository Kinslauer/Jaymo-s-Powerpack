package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: a click while holding something with Lift. Right: choke, then slam (or draw it in, or pocket a
 * chunk). Left ({@code left}): throw it, or push a creature away.
 */
public record GravityGrip(boolean left) implements CustomPacketPayload {
	public static final Type<GravityGrip> TYPE = new Type<>(JaymosPowers.id("gravity_grip"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GravityGrip> CODEC = StreamCodec.composite(
		net.minecraft.network.codec.ByteBufCodecs.BOOL, GravityGrip::left, GravityGrip::new);

	@Override
	public Type<GravityGrip> type() {
		return TYPE;
	}
}
