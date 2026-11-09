package com.jaymoskingdoms.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The armillary sphere's screen: open it from the block, record a measurement back. */
public final class ArmillaryNet {
    /** Server to client: open the armillary screen at {@code pos}. */
    public record Open(BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Open> TYPE = new CustomPacketPayload.Type<>(JaymosKingdoms.id("armillary_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
                (packet, buf) -> {
                    buf.writeInt(packet.pos.getX());
                    buf.writeInt(packet.pos.getY());
                    buf.writeInt(packet.pos.getZ());
                },
                buf -> new Open(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt())));

        @Override
        public CustomPacketPayload.Type<Open> type() {
            return TYPE;
        }
    }

    /** Client to server: record the current reading at {@code pos}. */
    public record Measure(BlockPos pos, String measurement) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Measure> TYPE = new CustomPacketPayload.Type<>(JaymosKingdoms.id("armillary_measure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Measure> CODEC = CustomPacketPayload.codec(
                (packet, buf) -> {
                    buf.writeInt(packet.pos.getX());
                    buf.writeInt(packet.pos.getY());
                    buf.writeInt(packet.pos.getZ());
                    byte[] bytes = packet.measurement.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    buf.writeInt(bytes.length);
                    buf.writeBytes(bytes);
                },
                buf -> {
                    BlockPos pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
                    int len = buf.readInt();
                    byte[] bytes = new byte[len];
                    buf.readBytes(bytes);
                    return new Measure(pos, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                });

        @Override
        public CustomPacketPayload.Type<Measure> type() {
            return TYPE;
        }
    }

    private ArmillaryNet() {
    }
}
