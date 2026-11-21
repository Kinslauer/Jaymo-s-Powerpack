package com.jaymoskitchen.net.payloads;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Client → server: abandon the session (walked away or let go). */
public record HandsOnAbandonPayload(UUID sessionId)
        implements CustomPacketPayload {
    public static final Type<HandsOnAbandonPayload> TYPE = new Type<>(JaymosKitchen.id("hands_on_abandon"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandsOnAbandonPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> buf.writeUUID(p.sessionId()),
            buf -> new HandsOnAbandonPayload(buf.readUUID()));

    @Override
    public Type<HandsOnAbandonPayload> type() {
        return TYPE;
    }
}