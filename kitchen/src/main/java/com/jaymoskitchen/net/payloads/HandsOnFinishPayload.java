package com.jaymoskitchen.net.payloads;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Server → client: the session finished with {@code quality}; {@code rewards} are the items to grant. */
public record HandsOnFinishPayload(UUID sessionId, int quality, ItemStack[] rewards)
        implements CustomPacketPayload {
    public static final Type<HandsOnFinishPayload> TYPE = new Type<>(JaymosKitchen.id("hands_on_finish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandsOnFinishPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeUuid(p.sessionId());
                buf.writeVarInt(p.quality());
                buf.writeVarInt(p.rewards.length);
                for (ItemStack s : p.rewards()) {
                    buf.writeItemStack(s);
                }
            },
            buf -> new HandsOnFinishPayload(buf.readUuid(), buf.readVarInt(),
                    buf.readList(buf.readVarInt(), ItemStack.STREAM_CODEC::new).toArray(ItemStack[]::new)));

    @Override
    public Type<HandsOnFinishPayload> type() {
        return TYPE;
    }
}