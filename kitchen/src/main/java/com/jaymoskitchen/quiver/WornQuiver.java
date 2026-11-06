package com.jaymoskitchen.quiver;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * (0.5.82) The quiver a player shows on their back: which quiver it is (its item id, which picks its look) and the
 * arrows in it. Every quiver in the pack is drawn with the same slung leather model; only the colours change.
 */
public record WornQuiver(Identifier item, QuiverContents contents) {
	public static final StreamCodec<RegistryFriendlyByteBuf, WornQuiver> STREAM_CODEC = StreamCodec.composite(
		Identifier.STREAM_CODEC, WornQuiver::item,
		QuiverContents.STREAM_CODEC, WornQuiver::contents,
		WornQuiver::new);
}
