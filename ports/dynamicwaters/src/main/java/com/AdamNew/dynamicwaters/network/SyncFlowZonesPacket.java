package com.AdamNew.dynamicwaters.network;

import com.AdamNew.dynamicwaters.FlowZones;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SyncFlowZonesPacket(List<FlowZones.Zone> zones) implements Payload {
    public static final Type<SyncFlowZonesPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("dynamicwaters", "sync_flow_zones"));

    public static final StreamCodec<FriendlyByteBuf, SyncFlowZonesPacket> STREAM_CODEC = StreamCodec.ofMember(
            SyncFlowZonesPacket::write,
            SyncFlowZonesPacket::new);

    public SyncFlowZonesPacket {
        zones = List.copyOf(zones);
    }

    public SyncFlowZonesPacket(FriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private static List<FlowZones.Zone> read(FriendlyByteBuf buffer) {
        final int n = buffer.readVarInt();
        final List<FlowZones.Zone> zones = new ArrayList<>(Math.min(n, FlowZones.MAX_ZONES));
        for (int i = 0; i < n; i++) {
            zones.add(new FlowZones.Zone(buffer.readVarInt(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    FlowZones.direction(buffer.readByte())));
        }
        return zones;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(zones.size());
        for (FlowZones.Zone zone : zones) {
            buffer.writeVarInt(zone.id());
            buffer.writeInt(zone.minX());
            buffer.writeInt(zone.minY());
            buffer.writeInt(zone.minZ());
            buffer.writeInt(zone.maxX());
            buffer.writeInt(zone.maxY());
            buffer.writeInt(zone.maxZ());
            buffer.writeByte(zone.direction());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
