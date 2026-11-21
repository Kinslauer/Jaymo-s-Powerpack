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
                buf.writeUuid(p.sessionId());
                buf.writeFloat(p.heat());
                buf.writeFloat(p.progress());
                buf.writeVarInt(p.status());
                buf.writeVarInt(p.quality());
            },
            buf -> new HandsOnSyncPayload(buf.readUuid(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<HandsOnSyncPayload> type() {
        return TYPE;
    }
}

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

/** Client → server: abandon the session (walked away or let go). */
public record HandsOnAbandonPayload(UUID sessionId) implements CustomPacketPayload {
    public static final Type<HandsOnAbandonPayload> TYPE = new Type<>(JaymosKitchen.id("hands_on_abandon"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandsOnAbandonPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> buf.writeUuid(p.sessionId()),
            buf -> new HandsOnAbandonPayload(buf.readUuid()));

    @Override
    public Type<HandsOnAbandonPayload> type() {
        return TYPE;
    }
}
