package com.jaymospowers.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.20.56) A sound channel's OpenAL source, for Blasts' air absorption. */
@Mixin(Channel.class)
public interface ChannelSourceAccessor {
	@Accessor("source")
	int jaymospowers$source();
}
