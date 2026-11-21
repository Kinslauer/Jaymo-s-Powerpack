package com.jaymoskitchen.net.payloads;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Server → client: per-tick sync for server-simmed sessions (grill, smoke, sear). */
public record HandsOnSyncPayload(UUID sessionId, float heat, float progress, int status, int quality)
        implements CustomPacketPayload {
    public static final Type<HandsOnSyncPayload> TYPE = new Type<>(JaymosKitchen.id("hands_on_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandsOnSyncPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeUUID(p.sessionId());
                buf.writeFloat(p.heat());
                buf.writeFloat(p.progress());
                buf.writeVarInt(p.status());
                buf.writeVarInt(p.quality());
            },
            buf -> new HandsOnSyncPayload(buf.readUUID(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<HandsOnSyncPayload> type() {
        return TYPE;
    }
}



