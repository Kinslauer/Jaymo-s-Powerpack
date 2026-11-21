package com.jaymoskitchen.cooking;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.net.payloads.HandsOnStartPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * A cooking station that supports hands-on, in-world interaction (no GUI screen).
 * Each station block entity holds a {@link Map<UUID, HandsOnSession>} of active per-player sessions.
 * <p>
 * Players interact directly with the block: right-click with the right tool starts a session,
 * then in-world input (left-click swings, mouse circles, etc.) drives it. Walking away ends it.
 */
public interface HandsOnStation {

    /** Called when {@code player} right-clicks this station with {@code tool} in {@code hand}.
     * Returns true if a hands-on session was started (no screen should open). */
    boolean onInteract(ServerPlayer player, InteractionHand hand, ItemStack tool, BlockPos pos, Level level);

    /** Called when the player stops interacting (walked away, released use, etc.). */
    void onStopUsing(ServerPlayer player, BlockPos pos, Level level);

    /** All active sessions on this station (keyed by player UUID). */
    Map<UUID, HandsOnSession> getActiveSessions();

    /** Remove a player's session (e.g. they walked away or abandoned). */
    void removeSession(UUID playerId, BlockPos pos, Level level);

    /** Whether this station type supports hands-on cooking at all. */
    default boolean supportsHandsOn() {
        return true;
    }
}
