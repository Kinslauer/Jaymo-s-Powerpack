package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-14): "to lockpick and break out of chains you must first be kneeling then that unlocks a context where you
 * can use right click to interact, for lockpicking, reviving, lifting, this should help free up some binds". Picking a
 * lock (a chest's, a door's, a shackle's, a cell door's) and working at a chain or rope are done from a kneel: Jaymo's
 * Powers' {@code Kneel}, the low crouch (Shift + scroll down) held still. Without Powers, a crouch on the ground counts.
 * Standing, the right-click does what it otherwise would, and the player is told (once in a while) to kneel. Reviving and
 * lifting the downed are Powers' own ({@code Downed}, {@code Carry}).
 */
public final class KneelFirst {
	/** How often (ticks) the kneel-first hint may come again. */
	static final long HINT_EVERY = 100L;

	/** Player → the game time their last hint was given. */
	private static final Map<UUID, Long> HINTED = new HashMap<>();

	private static java.lang.invoke.@Nullable MethodHandle kneeling;
	private static boolean looked;

	private KneelFirst() {
	}

	/** Whether {@code player} is down on one knee just now. */
	static boolean kneeling(ServerPlayer player) {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					kneeling = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.Kneel"), "kneeling",
						java.lang.invoke.MethodType.methodType(boolean.class, Player.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Jaymo's Powers has no kneel (older Powers?): a crouch will do", e);
				}
			}
		}
		if (kneeling != null) {
			try {
				return (boolean) kneeling.invoke((Player) player);
			} catch (Throwable e) {
				return false;
			}
		}
		return player.isShiftKeyDown() && player.onGround();
	}

	/** {@code player} is kneeling, ready to {@code what} (lock, shackle, chain, rope); if not, they're told to (now and then). */
	static boolean ready(ServerPlayer player, String what) {
		if (kneeling(player)) {
			return true;
		}
		long now = player.level().getGameTime();
		Long last = HINTED.get(player.getUUID());
		if (last == null || now - last >= HINT_EVERY || now < last) {
			HINTED.put(player.getUUID(), now);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.kneel." + what).withStyle(ChatFormatting.GRAY));
		}
		return false;
	}
}
