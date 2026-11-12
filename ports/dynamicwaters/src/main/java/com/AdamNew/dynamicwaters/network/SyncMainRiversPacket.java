package com.AdamNew.dynamicwaters.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.AdamNew.dynamicwaters.river.RiverGraphEdge;
import java.util.ArrayList;
import java.util.List;

public record SyncMainRiversPacket(int regionX, int regionZ, List<RiverGraphEdge> edges) implements Payload {
    public static final Type<SyncMainRiversPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dynamicwaters", "sync_main_rivers"));

    public static final StreamCodec<FriendlyByteBuf, SyncMainRiversPacket> STREAM_CODEC = StreamCodec.ofMember(
        SyncMainRiversPacket::write,
        SyncMainRiversPacket::new
    );

    public SyncMainRiversPacket(FriendlyByteBuf buffer) {
        this(
            buffer.readInt(),
            buffer.readInt(),
            readEdges(buffer)
        );
    }

    private static List<RiverGraphEdge> readEdges(FriendlyByteBuf buffer) {
        int edgeCount = buffer.readInt();
        List<RiverGraphEdge> edges = new ArrayList<>(edgeCount);

        for (int i = 0; i < edgeCount; i++) {
            int width = buffer.readInt();
            boolean sourceEdge = buffer.readBoolean();
            int cachedDrainWidth = buffer.readInt();
            boolean delta = buffer.readBoolean();
            boolean deltaMain = buffer.readBoolean();
            double flareRadius = buffer.readDouble();
            double flareLength = buffer.readDouble();
            double tipLength = buffer.readDouble();

            double sourceX = buffer.readDouble();
            double sourceZ = buffer.readDouble();
            double drainX = buffer.readDouble();
            double drainZ = buffer.readDouble();

            int segmentCount = buffer.readInt();
            double[] fractalSegments = new double[segmentCount];
            for (int j = 0; j < segmentCount; j++) {
                fractalSegments[j] = buffer.readDouble();
            }
            double fractalNorm = buffer.readDouble();

            RiverGraphEdge edge = new RiverGraphEdge(
                width, sourceEdge, cachedDrainWidth, delta, deltaMain, flareRadius, flareLength, tipLength,
                sourceX, sourceZ, drainX, drainZ,
                fractalSegments, fractalNorm
            );
            edges.add(edge);
        }
        return edges;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.regionX);
        buffer.writeInt(this.regionZ);
        buffer.writeInt(this.edges.size());

        for (RiverGraphEdge edge : this.edges) {
            buffer.writeInt(edge.width);
            buffer.writeBoolean(edge.sourceEdge());
            buffer.writeInt(edge.drainEdge() == null ? edge.width : edge.drainEdge().width);
            buffer.writeBoolean(edge.delta());
            buffer.writeBoolean(edge.deltaMain());
            buffer.writeDouble(edge.flareRadius());
            buffer.writeDouble(edge.flareLength());
            buffer.writeDouble(edge.tipLength());

            buffer.writeDouble(edge.source().x());
            buffer.writeDouble(edge.source().y());
            buffer.writeDouble(edge.drain().x());
            buffer.writeDouble(edge.drain().y());

            double[] segments = edge.fractal().segments;
            buffer.writeInt(segments.length);
            for (int j = 0; j < segments.length; j++) {
                buffer.writeDouble(segments[j]);
            }
            buffer.writeDouble(edge.fractal().getNorm());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
