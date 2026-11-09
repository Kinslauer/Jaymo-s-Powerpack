package com.jaymoskingdoms.astronomy;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.IDynamicCodec;
import net.minecraft.util.dataflow.DataFlow;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.BitSet;
import java.util.UUID;

public class DiscoveryAttachment {

    /** The attachment type for per-player discovered stars. */
    public static final AttachmentType<BitSet> DISCOVERY = AttachmentRegistry.newType(
            Registries.DYNAMIC_CODEC,
            IDynamicCodec.of((codec, dataFlow) -> codec.codec().wrapBuilder(BitSet::new, BitSet::toLongArray, BitSet::valueOf)),
            builder -> builder.persistent()
    );

    /** Called when the server starts to register the attachment. */
    public static void register() {
        AttachmentRegistry.register(Registries.DYNAMIC_CODEC, JaymosKingdoms.id("astronomy_discovery"), DISCOVERY);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            // No global state to clear per-server; per-player data lives in attachments.
        });
    }

    /** Get the discovered stars bit set for a player. */
    public static BitSet getDiscovered(Player player) {
        return player.getOrCreateAttachment(DISCOVERY);
    }

    /** Set the discovered stars bit set for a player (call on server). */
    public static void setDiscovered(Player player, BitSet discovered) {
        player.setAttachment(DISCOVERY, discovered);
    }
}