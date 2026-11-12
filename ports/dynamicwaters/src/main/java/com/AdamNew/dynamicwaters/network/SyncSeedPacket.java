package com.AdamNew.dynamicwaters.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncSeedPacket(long seed, int seaLevel) implements Payload {
    public static final Type<SyncSeedPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dynamicwaters", "sync_seed"));

    public static final StreamCodec<FriendlyByteBuf, SyncSeedPacket> STREAM_CODEC = StreamCodec.ofMember(
        SyncSeedPacket::write,
        SyncSeedPacket::new
    );

    public SyncSeedPacket(FriendlyByteBuf buffer) {
        this(buffer.readLong(), buffer.readInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(this.seed);
        buffer.writeInt(this.seaLevel);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
