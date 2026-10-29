package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.332) The colour of the MCA clothes a player wears (Andy, of the beast's rags, the torn cloth and the player's own
 * clothes: "they should all be one and the same"). Only the client has the clothes' pixels, so each client reads its
 * own player's clothes as they're drawn ({@code client.ClothesColourClient}) and tells the server; {@link Lycans#clothes}
 * dyes the beast's rags and the torn cloth with it from then on. Kept, so a change away from MCA keeps the last colour.
 */
public final class ClothesColour {
	/** On a player: their clothes' colour (RGB). */
	static final AttachmentType<Integer> COLOUR = AttachmentRegistry.create(JaymosKingdoms.id("clothes_colour"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** Client to server: the colour of the clothes I'm wearing. */
	public record Tell(int rgb) implements CustomPacketPayload {
		public static final Type<Tell> TYPE = new Type<>(JaymosKingdoms.id("clothes_colour"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Tell> CODEC = CustomPacketPayload.codec((p, buf) -> buf.writeInt(p.rgb),
			buf -> new Tell(buf.readInt()));

		@Override
		public Type<Tell> type() {
			return TYPE;
		}
	}

	private ClothesColour() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Tell.TYPE, Tell.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Tell.TYPE, (payload, context) -> context.player().setAttached(COLOUR, payload.rgb() & 0xFFFFFF));
	}

	/** {@code player}'s clothes' colour, if their client has told it. */
	static @Nullable Integer of(Player player) {
		return player.getAttached(COLOUR);
	}
}
