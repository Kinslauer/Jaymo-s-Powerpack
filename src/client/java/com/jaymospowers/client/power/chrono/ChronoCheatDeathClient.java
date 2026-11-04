package com.jaymospowers.client.power.chrono;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.chrono.ChronoCheatDeath;
import com.jaymospowers.power.chrono.ChronoPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * (0.20.42) Chrono cheating death, on the client ({@link ChronoCheatDeath}): nothing to press. When a death is rewound,
 * Respawn Recap (MIT, by DialingSpoon; shipped unchanged, see {@code client/mixin/respawnrecap}) plays its memory replay
 * as the rewind. Andy: "limit it to chrono": its replay is never shown for a real death, its death-screen takeover is
 * switched off, and it only keeps its screenshots of your life while you're a Chrono.
 */
public final class ChronoCheatDeathClient {
	/** Respawn Recap is to play its replay for our rewind (it plays it while the player "is dead"). */
	private static boolean recapping;
	/** Its replay for our rewind is running (until it's over and the respawn blink is done). */
	private static boolean recapShown;
	private static int recapTicks;
	/** Never longer than this (a replay that never ended can't hold your screen). */
	private static final int RECAP_MAX_TICKS = 20 * 90;

	private ChronoCheatDeathClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ChronoCheatDeath.Rewound.TYPE, (payload, ctx) -> {
			if (FabricLoader.getInstance().isModLoaded("respawnrecap") && payload.seconds() > 0) {
				recapping = true;
				recapShown = true;
				recapTicks = 0;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if ((recapping || recapShown) && (client.player == null || ++recapTicks > RECAP_MAX_TICKS)) {
				recapping = false;
				recapShown = false;
			}
		});
	}

	/** Whether Respawn Recap should be playing its replay as our rewind now. */
	public static boolean recapping() {
		return recapping;
	}

	/** Respawn Recap's replay is over (or skipped): the rewind's film is done. */
	public static void recapDone() {
		recapping = false;
	}

	/** Respawn Recap has gone back to idle (its respawn blink begun). */
	public static void recapIdle() {
		recapShown = false;
	}

	/**
	 * Whether Respawn Recap may keep {@code requested} off the screen (its death-screen takeover): only while it plays our
	 * rewind. Every other time screens open as they always do, the death screen too. Called from the patch
	 * {@code RespawnRecapPlugin} puts into its handler.
	 */
	public static boolean recapMayHide(Screen requested) {
		return recapping || recapShown;
	}

	/** Whether Respawn Recap should keep screenshots of your life now: only a Chrono's. */
	public static boolean recordsLife(Minecraft minecraft) {
		return minecraft.player != null && Powers.has(minecraft.player, ChronoPower.INSTANCE);
	}
}
