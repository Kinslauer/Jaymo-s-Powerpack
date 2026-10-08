package com.jaymospowers.power.web;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The game time each cooldown ends at (Zip, Web Shot, Yank). Synced to the owner only, for the HUD. */
public record WebCooldowns(long zip, long shot, long yank) {
	public static final WebCooldowns NONE = new WebCooldowns(0L, 0L, 0L);

	public static final StreamCodec<ByteBuf, WebCooldowns> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, WebCooldowns::zip,
		ByteBufCodecs.VAR_LONG, WebCooldowns::shot,
		ByteBufCodecs.VAR_LONG, WebCooldowns::yank,
		WebCooldowns::new);
}
