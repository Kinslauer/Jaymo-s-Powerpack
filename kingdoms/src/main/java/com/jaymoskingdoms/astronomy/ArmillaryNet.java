package com.jaymoskingdoms.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The armillary sphere's screen: open it from the block, ask for a reading at it, and hear back how the reading went.
 * The server takes the reading itself ({@link Measurements#read}); the client only asks.
 */
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

    /** Client to server: take a reading with the sphere at {@code pos} and write it in the player's journal. */
    public record Measure(BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Measure> TYPE = new CustomPacketPayload.Type<>(JaymosKingdoms.id("armillary_measure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Measure> CODEC = CustomPacketPayload.codec(
                (packet, buf) -> {
                    buf.writeInt(packet.pos.getX());
                    buf.writeInt(packet.pos.getY());
                    buf.writeInt(packet.pos.getZ());
                },
                buf -> new Measure(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt())));

        @Override
        public CustomPacketPayload.Type<Measure> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: how a reading went, for the armillary screen's line. {@code outcome} names the line
     * ({@code gui.jaymoskingdoms.armillary.reading.<outcome>}); {@code count} is the new measurements written and
     * {@code xp} the Scholar experience they earned.
     */
    public record Reading(String outcome, int count, int xp) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Reading> TYPE = new CustomPacketPayload.Type<>(JaymosKingdoms.id("armillary_reading"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reading> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Reading::outcome, ByteBufCodecs.VAR_INT, Reading::count, ByteBufCodecs.VAR_INT, Reading::xp, Reading::new);

        @Override
        public CustomPacketPayload.Type<Reading> type() {
            return TYPE;
        }
    }

    private ArmillaryNet() {
    }
}
