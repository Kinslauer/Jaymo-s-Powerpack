package com.AdamNew.dynamicwaters.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncVanillaFlowPacket(int chunkX, int chunkZ, byte[] angles) implements Payload {
    public static final Type<SyncVanillaFlowPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("dynamicwaters", "sync_vanilla_flow"));

    public static final StreamCodec<FriendlyByteBuf, SyncVanillaFlowPacket> STREAM_CODEC = StreamCodec.ofMember(
            SyncVanillaFlowPacket::write,
            SyncVanillaFlowPacket::new);

    public SyncVanillaFlowPacket(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readInt(), readAngles(buffer));
    }

    private static byte[] readAngles(FriendlyByteBuf buffer) {
        final byte[] a = new byte[16];
        buffer.readBytes(a);
        return a;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeBytes(angles, 0, 16);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
