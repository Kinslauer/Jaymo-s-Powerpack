package com.jaymospowers.power.web;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A web line a player currently has out, synced to everyone who can see them (a player attachment) so they can draw
 * it. {@code entityId} is the entity the line is stuck to (Yank), or -1 for a fixed point ({@code anchor}).
 */
public record WebLine(byte kind, Vec3 anchor, int entityId) {
	public static final byte SWING = 0;
	public static final byte ZIP = 1;
	public static final byte YANK = 2;

	public static final StreamCodec<ByteBuf, WebLine> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BYTE, WebLine::kind,
		Vec3.STREAM_CODEC, WebLine::anchor,
		ByteBufCodecs.VAR_INT, WebLine::entityId,
		WebLine::new);
}
