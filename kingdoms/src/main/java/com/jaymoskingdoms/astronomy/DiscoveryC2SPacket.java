package com.jaymoskingdoms.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: report a star look (HIP number). */
public record DiscoveryC2SPacket(int hip) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DiscoveryC2SPacket> TYPE =
        new CustomPacketPayload.Type<>(JaymosKingdoms.id("discovery_look"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiscoveryC2SPacket> CODEC =
        CustomPacketPayload.codec(
            (packet, buf) -> buf.writeVarInt(packet.hip),
            buf -> new DiscoveryC2SPacket(buf.readVarInt())
        );

    /** Read the BitSet of discovered HIP numbers from the packet. */
    public static BitSet readDiscovered(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        BitSet discovered = BitSet.valueOf(new long[count]);
        for (int i = 0; i < count; i++) {
            discovered.set(buf.readVarInt());
        }
        return discovered;
    }

    @Override
    public CustomPacketPayload.Type<DiscoveryC2SPacket> type() {
        return TYPE;
    }
}