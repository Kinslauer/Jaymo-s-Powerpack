package com.jaymospowers.net;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Packets for mount climbing synchronisation.
 */
public final class MountClimbPackets {

    private MountClimbPackets() {
    }

    /** Client → server: climb-on animation finished, start the actual ride. */
    public record ClimbOnFinish(int mountId) implements CustomPacketPayload {
        public static final Type<ClimbOnFinish> TYPE = new Type<>(JaymosPowers.id("mount_climb_on_finish"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClimbOnFinish> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.mountId()),
                buf -> new ClimbOnFinish(buf.readVarInt()));

        @Override
        public Type<ClimbOnFinish> type() {
            return TYPE;
        }
    }

    /** Client → server: climb-off animation started, player will dismount when done. */
    public record ClimbOffStart(int mountId) implements CustomPacketPayload {
        public static final Type<ClimbOffStart> TYPE = new Type<>(JaymosPowers.id("mount_climb_off_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClimbOffStart> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.mountId()),
                buf -> new ClimbOffStart(buf.readVarInt()));

        @Override
        public Type<ClimbOffStart> type() {
            return TYPE;
        }
    }

    /** Server → client: another player started a climb animation (for sync to other clients). */
    public record ClimbSync(int playerId, int mountId, byte climbType, boolean climbingOn) implements CustomPacketPayload {
        public static final Type<ClimbSync> TYPE = new Type<>(JaymosPowers.id("mount_climb_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClimbSync> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.playerId());
                    buf.writeVarInt(p.mountId());
                    buf.writeByte(p.climbType());
                    buf.writeBoolean(p.climbingOn());
                },
                buf -> new ClimbSync(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readBoolean()));

        @Override
        public Type<ClimbSync> type() {
            return TYPE;
        }
    }

    /** Server → client: climb cancelled (hit during climb). */
    public record ClimbCancel(int playerId) implements CustomPacketPayload {
        public static final Type<ClimbCancel> TYPE = new Type<>(JaymosPowers.id("mount_climb_cancel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClimbCancel> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.playerId()),
                buf -> new ClimbCancel(buf.readVarInt()));

        @Override
        public Type<ClimbCancel> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ClimbSync.TYPE, ClimbSync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClimbCancel.TYPE, ClimbCancel.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClimbOnFinish.TYPE, ClimbOnFinish.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClimbOffStart.TYPE, ClimbOffStart.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ClimbOnFinish.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            Entity mount = player.level().getEntity(payload.mountId());
            if (mount != null && mount.isAlive() && !player.isPassenger()) {
                // Actually start riding now
                player.startRiding(mount);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ClimbOffStart.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            // The client will play the climb-off animation, then actually dismount
            // Server just acknowledges; the client handles the timing
            // We could track this server-side for anti-cheat, but for now trust the client
        });
    }
}