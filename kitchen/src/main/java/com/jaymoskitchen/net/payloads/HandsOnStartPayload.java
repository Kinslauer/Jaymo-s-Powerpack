package com.jaymoskitchen.net.payloads;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client: a hands-on cooking session has started at {@code pos}. */
public record HandsOnStartPayload(BlockPos pos, int stationOrdinal, int dishOrdinal, int sessionId)
        implements CustomPacketPayload {
    public static final Type<HandsOnStartPayload> TYPE = new Type<>(JaymosKitchen.id("hands_on_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandsOnStartPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.stationOrdinal());
                buf.writeVarInt(p.dishOrdinal());
                buf.writeVarInt(p.sessionId());
            },
            buf -> new HandsOnStartPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<HandsOnStartPayload> type() {
        return TYPE;
    }
}
