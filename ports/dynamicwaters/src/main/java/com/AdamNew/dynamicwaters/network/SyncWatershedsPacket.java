package com.AdamNew.dynamicwaters.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.AdamNew.dynamicwaters.WatershedPath;
import com.AdamNew.dynamicwaters.terrain.WatershedLake;
import java.util.ArrayList;
import java.util.List;

public record SyncWatershedsPacket(int regionX, int regionZ, List<WatershedPath> paths, List<WatershedLake> lakes) implements Payload {
    public static final Type<SyncWatershedsPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dynamicwaters", "sync_watersheds"));

    public static final StreamCodec<FriendlyByteBuf, SyncWatershedsPacket> STREAM_CODEC = StreamCodec.ofMember(
        SyncWatershedsPacket::write,
        SyncWatershedsPacket::new
    );

    public SyncWatershedsPacket(FriendlyByteBuf buffer) {
        this(
            buffer.readInt(),
            buffer.readInt(),
            readPaths(buffer),
            readLakes(buffer)
        );
    }

    private static List<WatershedPath> readPaths(FriendlyByteBuf buffer) {
        int pathCount = buffer.readInt();
        List<WatershedPath> paths = new ArrayList<>(pathCount);

        for (int i = 0; i < pathCount; i++) {
            int nodeCount = buffer.readInt();
            int validNodeCount = buffer.readInt();
            boolean hitMainRiver = buffer.readBoolean();

            double[] segmentsX = new double[nodeCount];
            double[] segmentsZ = new double[nodeCount];
            int[] segmentsY = new int[nodeCount];
            int[] segmentsTerrainY = new int[nodeCount];
            double[] segmentWidths = new double[nodeCount];

            for (int j = 0; j < nodeCount; j++) {
                segmentsX[j] = buffer.readDouble();
                segmentsZ[j] = buffer.readDouble();
                segmentsY[j] = buffer.readInt();
                segmentsTerrainY[j] = buffer.readInt();
                segmentWidths[j] = buffer.readDouble();
            }

            WatershedPath path = new WatershedPath(nodeCount, validNodeCount, hitMainRiver, segmentsX, segmentsZ, segmentsY, segmentsTerrainY, segmentWidths);
            paths.add(path);
        }
        return paths;
    }

    private static List<WatershedLake> readLakes(FriendlyByteBuf buffer) {
        int lakeCount = buffer.readInt();
        List<WatershedLake> lakes = new ArrayList<>(lakeCount);
        for (int i = 0; i < lakeCount; i++) {
            int waterY = buffer.readInt();
            int minCellX = buffer.readInt();
            int minCellZ = buffer.readInt();
            int w = buffer.readInt();
            int h = buffer.readInt();
            int n = buffer.readInt();
            long[] bits = new long[n];
            for (int k = 0; k < n; k++) bits[k] = buffer.readLong();
            int cn = buffer.readInt();
            short[] coarse = new short[cn];
            for (int k = 0; k < cn; k++) coarse[k] = buffer.readShort();
            lakes.add(new WatershedLake(waterY, minCellX, minCellZ, w, h, bits, coarse, buffer.readInt()));
        }
        return lakes;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.regionX);
        buffer.writeInt(this.regionZ);
        buffer.writeInt(this.paths.size());

        for (WatershedPath path : this.paths) {
            buffer.writeInt(path.nodeCount);
            buffer.writeInt(path.validNodeCount);
            buffer.writeBoolean(path.hitMainRiver);

            for (int j = 0; j < path.nodeCount; j++) {
                buffer.writeDouble(path.segmentsX[j]);
                buffer.writeDouble(path.segmentsZ[j]);
                buffer.writeInt(path.segmentsY[j]);
                buffer.writeInt(path.segmentsTerrainY[j]);
                buffer.writeDouble(path.segmentWidths[j]);
            }
        }

        buffer.writeInt(this.lakes.size());
        for (WatershedLake lake : this.lakes) {
            buffer.writeInt(lake.waterY);
            buffer.writeInt(lake.minCellX);
            buffer.writeInt(lake.minCellZ);
            buffer.writeInt(lake.cellsW);
            buffer.writeInt(lake.cellsH);
            buffer.writeInt(lake.bits.length);
            for (long b : lake.bits) buffer.writeLong(b);
            buffer.writeInt(lake.coarseH.length);
            for (short s : lake.coarseH) buffer.writeShort(s);
            buffer.writeInt(lake.dig);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
