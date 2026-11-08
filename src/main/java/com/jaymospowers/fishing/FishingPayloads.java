package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: reeling while fighting a fish, and (Track A) turning the rod's drag up or down with sneak + scroll. */
public record FishingPayloads() {
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

    /** Turn the held rod's drag by {@code delta} steps (sneak + scroll). */
    public record SetDrag(int delta) implements CustomPacketPayload {
        public static final Type<SetDrag> TYPE = new Type<>(JaymosPowers.id("rod_drag"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetDrag> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.delta),
                buf -> new SetDrag(buf.readVarInt()));

        @Override
        public Type<SetDrag> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ReelInput.TYPE, ReelInput.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetDrag.TYPE, SetDrag.CODEC);
    }
}
