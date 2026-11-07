package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: whether the player is holding the reel key while fighting a fish on the line. */
public record FishingPayloads() {
    private FishingPayloads() {
    }

    public record ReelInput(boolean reeling) implements CustomPacketPayload {
        public static final Type<ReelInput> TYPE = new Type<>(JaymosPowers.id("reel_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReelInput> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeBoolean(p.reeling),
                buf -> new ReelInput(buf.readBoolean()));

        @Override
        public Type<ReelInput> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ReelInput.TYPE, ReelInput.CODEC);
    }
}
