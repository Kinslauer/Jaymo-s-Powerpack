package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → client: an echolocation ring to draw. */
public record BlindnessEcho(
        double x, double y, double z,
        float distance,
        float volume,
        String soundId
) implements CustomPacketPayload {
    public static final Type<BlindnessEcho> TYPE = new Type<>(JaymosPowers.id("blindness_echo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlindnessEcho> CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.distance);
                buf.writeFloat(p.volume);
                buf.writeUtf(p.soundId, 128);
            },
            buf -> new BlindnessEcho(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readUtf(128)
            )
    );

    @Override
    public Type<BlindnessEcho> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }
}