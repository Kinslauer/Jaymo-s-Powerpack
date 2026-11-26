package com.jaymospowers.core;

import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Hold, wait, release (God Wand's way with a spell: choose it, charge it, decide when to loose it). For a power whose ultimate
 * is a single press ({@code Power#ultimateHeld}): a quick tap of C still casts at once, as it always did; held for
 * {@link #CHARGE_TICKS} the ultimate is charged, and it stays charged, held ready, for as long as C is kept down (up to
 * {@link #WAIT_TICKS} more), to be loosed by letting go. Let go half-charged, the charge slips away and nothing goes off; held
 * past the wait, it fades. The charge shows as a bar ({@link HudBar#hold}, not locking the hands, so the player can still fight
 * and move while the ultimate waits) and a ball of power (typed, {@link MagicFx#orb}) at the chest that swells with the charge.
 */
public final class UltimateHold {
	// ---- tuning ----
	/** A press let go within this many ticks is a tap and casts at once. */
	static final int TAP_TICKS = 6;
	/** Ticks of holding to a full charge, and how much longer a full charge is kept while C stays down. */
	static final int CHARGE_TICKS = 40, WAIT_TICKS = 20 * 20;
	/** The orb's colour, and its radius empty and full (blocks). */
	static final int COLOUR = 0xFFD45A;
	static final float ORB_MIN = 0.3F, ORB_MAX = 0.9F;
	/** The ball and the bar are sent again this often (ticks). */
	static final int EVERY = 4;

	private static final Map<UUID, Long> SINCE = new HashMap<>();

	private UltimateHold() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(UltimateHold::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SINCE.clear());
	}

	/** Forgets whatever {@code player} was charging (they left, or died). */
	public static void forget(ServerPlayer player) {
		SINCE.remove(player.getUUID());
	}

	/**
	 * C pressed or let go for a power that holds its ultimate. True when this took the key (the press is kept back, the release
	 * is answered): {@code loose} then runs the ultimate (press and release) if it is to go off.
	 */
	static boolean key(ServerPlayer player, boolean down, Runnable loose) {
		long now = player.level().getGameTime();
		if (down) {
			SINCE.put(player.getUUID(), now);
			return true;
		}
		Long since = SINCE.remove(player.getUUID());
		if (since == null) {
			// a release with no press kept (the charge faded): nothing to loose
			return true;
		}
		long held = now - since;
		HudBar.hold(player, "ultimate_hold", 0.0F, HudBar.GOLD, null, false);
		if (held < TAP_TICKS || held >= CHARGE_TICKS) {
			loose.run();
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.ultimate_hold.slipped").withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (SINCE.isEmpty()) {
			return;
		}
		for (var it = SINCE.entrySet().iterator(); it.hasNext();) {
			var entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.isSpectator()) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long held = level.getGameTime() - entry.getValue();
			if (held < TAP_TICKS) {
				continue;
			}
			if (held >= CHARGE_TICKS + WAIT_TICKS) {
				it.remove();
				HudBar.hold(player, "ultimate_hold", 0.0F, HudBar.GOLD, null, false);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.ultimate_hold.faded").withStyle(ChatFormatting.GRAY));
				continue;
			}
			float charge = Math.min(1.0F, held / (float) CHARGE_TICKS);
			if (held == CHARGE_TICKS) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.2F);
			}
			if (held % EVERY == 0) {
				boolean full = charge >= 1.0F;
				HudBar.hold(player, "ultimate_hold", charge, HudBar.GOLD,
					Component.translatable(full ? "hud.jaymospowers.ultimate_hold.full" : "hud.jaymospowers.ultimate_hold"), false);
				Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.65, 0.0);
				MagicFx.orb(level, at, ORB_MIN + (ORB_MAX - ORB_MIN) * charge, COLOUR, EVERY + 2);
			}
		}
	}
}
