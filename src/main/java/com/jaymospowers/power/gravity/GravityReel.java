package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the scroll wheel while Lift holds something. {@code steps} notches: up pushes it further out, down
 * reels it in (Andy's pick from Star Wars: Galaxy in a Cube, ideas only).
 */
public record GravityReel(int steps) implements CustomPacketPayload {
	public static final Type<GravityReel> TYPE = new Type<>(JaymosPowers.id("gravity_reel"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GravityReel> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, GravityReel::steps, GravityReel::new);

	@Override
	public Type<GravityReel> type() {
		return TYPE;
	}
}
