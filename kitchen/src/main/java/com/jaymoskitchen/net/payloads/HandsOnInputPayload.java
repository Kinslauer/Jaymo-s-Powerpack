package com.jaymoskitchen.net.payloads;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

/** Client → server: player input during a hands-on cooking session. */
public sealed interface HandsOnInputPayload {
    UUID sessionId();

    /** Chop input: player swung when the mark was at {@code cutOffset} (vegetable-length units). */
    record ChopInput(UUID sessionId, float cutOffset) implements HandsOnInputPayload {
        public static final Type<ChopInput> TYPE = new Type<>(JaymosKitchen.id("hands_on_chop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChopInput> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUUID(p.sessionId());
                    buf.writeFloat(p.cutOffset());
                },
                buf -> new ChopInput(buf.readUUID(), buf.readFloat()));

        @Override
        public Type<ChopInput> type() {
            return TYPE;
        }
    }

    /** Stir input: player moved the mouse around the pot at {@code angle} radians with {@code speed}. */
    record StirInput(UUID sessionId, float angle, float speed, boolean boiling) implements HandsOnInputPayload {
        public static final Type<StirInput> TYPE = new Type<>(JaymosKitchen.id("hands_on_stir"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StirInput> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUUID(p.sessionId());
                    buf.writeFloat(p.angle());
                    buf.writeFloat(p.speed());
                    buf.writeBoolean(p.boiling());
                },
                buf -> new StirInput(buf.readUUID(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

        @Override
        public Type<StirInput> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ChopInput.TYPE, ChopInput.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StirInput.TYPE, StirInput.CODEC);
    }
}
