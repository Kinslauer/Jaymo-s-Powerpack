package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.astronomy.DiscoveryAttachment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import java.util.BitSet;

/** Which stars a player has discovered, stored on the server and synced to the client.
 * A star is discovered by keeping it in view at night for LOOK_TICKS ticks;
 * constellation figures connect once all their stars are discovered.
 * Server side: the discovery state lives as a player attachment, synced to every client.
 * Client side: only reports the look; the server owns the authoritative state.
 */
public final class Discovery {

    private static final BitSet EMPTY = BitSet.valueOf(new long[0])[0];

    private Discovery() {
    }

    public static BitSet getDiscovered() {
        // Discovery state lives on the server as a player attachment, synced to clients.
        // The client only reports looks; the server owns the authoritative state.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return EMPTY;
        }
        return DiscoveryAttachment.getDiscovered(player);
        }

    public static void reportLook(int hip) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer serverPlayer = serverLevel.getPlayerList().getPlayer(player.getUUID());
        if (serverPlayer == null) {
            return;
        }
        ServerPlayNetworking.send(serverPlayer, new DiscoveryC2SPacket(hip));
    }
}