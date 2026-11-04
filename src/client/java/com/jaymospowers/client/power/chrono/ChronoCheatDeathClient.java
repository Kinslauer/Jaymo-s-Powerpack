package com.jaymospowers.client.power.chrono;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.jaymospowers.power.chrono.ChronoCheatDeath;
import com.jaymospowers.power.chrono.ChronoPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * (0.20.50) Chrono cheating death, on the client ({@link ChronoCheatDeath}): nothing shown (Andy: "the Chrono is NOT told
 * anywhere that they can revive here ... they have a window now, no warning"). Inside the server's window, R (Rewind's
 * key) sends the rewind, whatever screen is up (the death screen, a knock-out). When a death is rewound, Respawn Recap (MIT, by DialingSpoon; shipped unchanged, see {@code client/mixin/respawnrecap}) plays its memory replay
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

	/** Client ticks R stays open (from {@link ChronoCheatDeath.Window}); nothing is ever drawn for it. */
	private static int windowLeft;
	private static boolean rWasDown;

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ChronoCheatDeath.Window.TYPE, (payload, ctx) -> {
			windowLeft = payload.ticks();
			rWasDown = rDown(); // (a key already held when you fell doesn't count)
		});
		ClientPlayNetworking.registerGlobalReceiver(ChronoCheatDeath.Rewound.TYPE, (payload, ctx) -> {
			windowLeft = 0;
			Minecraft minecraft = ctx.client();
			if (minecraft.player != null) {
				// (alive after all: no lying tipped over, no death screen)
				minecraft.player.deathTime = 0;
			}
			if (minecraft.gui.screen() instanceof DeathScreen) {
				minecraft.gui.setScreen(null);
			}
			if (FabricLoader.getInstance().isModLoaded("respawnrecap") && payload.seconds() > 0) {
				recapping = true;
				recapShown = true;
				recapTicks = 0;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (windowLeft > 0) {
				windowLeft--;
				// R, read straight off the keyboard (the death screen or a knock-out takes the keys otherwise)
				boolean down = rDown();
				if (down && !rWasDown && client.player != null) {
					windowLeft = 0;
					ClientPlayNetworking.send(new ChronoCheatDeath.Rewind());
				}
				rWasDown = down;
			}
			if ((recapping || recapShown) && (client.player == null || ++recapTicks > RECAP_MAX_TICKS)) {
				recapping = false;
				recapShown = false;
			}
		});
	}

	/** Whether Rewind's key (R by default) is held right now. */
	private static boolean rDown() {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(JaymosPowersClient.keyMapping(PowerKey.ABILITY_1));
		return key.getType() == InputConstants.Type.KEYBOARD && key.getValue() >= 0 && InputConstants.isKeyDown(key.getValue());
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
