package com.jaymospowers.client.power.resonance;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.resonance.ResonanceNet;
import com.jaymospowers.power.resonance.ResonancePower;
import com.jaymospowers.power.web.WebSymbiote;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The scroll wheel for a Resonance user: held X (Tune) moves the pitch, held Z (Throw Voice) picks the sound; a scroll
 * taken here doesn't turn the hotbar. X is registered by the symbiote's menu (the same key); for a Resonance user its
 * presses are taken here, at the start of the tick, before the symbiote's handler would see them (as Wind does). A
 * quick tap of X sets the pitch back to the middle.
 */
public final class ResonanceInputClient {
	private static final String KEY_NAME = "key.jaymospowers.web_symbiote";
	/** A press shorter than this, with no scroll, is a tap. */
	private static final int TAP_TICKS = 6;

	private static @Nullable KeyMapping key;
	private static boolean xHeld;
	private static int xTicks;
	private static boolean xScrolled;

	private ResonanceInputClient() {
	}

	static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(ResonanceInputClient::input);
	}

	static @Nullable KeyMapping key(Minecraft minecraft) {
		if (key == null) {
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				if (KEY_NAME.equals(mapping.getName())) {
					key = mapping;
					break;
				}
			}
		}
		return key;
	}

	static String keyLabel(Minecraft minecraft) {
		KeyMapping mapping = key(minecraft);
		return mapping == null ? "X" : mapping.getTranslatedKeyMessage().getString();
	}

	private static boolean ours(@Nullable LocalPlayer player) {
		return player != null && Powers.has(player, ResonancePower.INSTANCE) && !WebSymbiote.isBonded(player);
	}

	private static void input(Minecraft minecraft) {
		KeyMapping mapping = key(minecraft);
		LocalPlayer player = minecraft.player;
		if (mapping == null || !ours(player)) {
			xHeld = false;
			return;
		}
		while (mapping.consumeClick()) {
			// X is Tune's for a Resonance user: the symbiote menu never opens
		}
		boolean down = mapping.isDown() && minecraft.gui.screen() == null && !player.isSpectator();
		if (down && !xHeld) {
			xHeld = true;
			xTicks = 0;
			xScrolled = false;
		} else if (down) {
			xTicks++;
		} else if (xHeld) {
			xHeld = false;
			if (!xScrolled && xTicks < TAP_TICKS && ResonancePower.INSTANCE.slotUnlocked(player, "special")) {
				ClientPlayNetworking.send(new ResonanceNet.Tune(0));
			}
		}
	}

	/** Whether X is held for tuning right now (for the HUD). */
	static boolean tuning() {
		return xHeld;
	}

	/** From the scroll mixin: true if the scroll was taken (Tune or Throw Voice). */
	public static boolean scroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (minecraft.gui.screen() != null || !ours(player) || player.isSpectator() || amount == 0.0) {
			return false;
		}
		int steps = amount > 0.0 ? 1 : -1;
		if (xHeld && ResonancePower.INSTANCE.slotUnlocked(player, "special")) {
			xScrolled = true;
			ClientPlayNetworking.send(new ResonanceNet.Tune(steps));
			return true;
		}
		if (JaymosPowersClient.modeKey().isDown() && ResonancePower.INSTANCE.slotUnlocked(player, "mode")) {
			// like the hotbar: scrolling down goes to the next
			ClientPlayNetworking.send(new ResonanceNet.Cycle(-steps));
			return true;
		}
		return false;
	}
}
