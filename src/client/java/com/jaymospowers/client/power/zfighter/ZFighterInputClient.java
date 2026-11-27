package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * X for a Z Fighter: the training screen, or with sneak, Limit Break up a tier. X is registered by the symbiote's menu
 * (the same key); for a Z Fighter its presses are taken here, at the start of the tick, as Resonance does.
 */
public final class ZFighterInputClient {
	private static final String KEY_NAME = "key.jaymospowers.web_symbiote";
	/** Hold Sneak+X at least this many ticks to raise Overburn; quicker tap cycles Limit Break. */
	private static final int OVERBURN_HOLD_TICKS = 7;

	private static @Nullable KeyMapping key;
	private static int sneakHoldTicks;
	private static boolean overburnHolding;

	private ZFighterInputClient() {
	}

	static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(ZFighterInputClient::input);
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

	private static void releaseOverburn() {
		if (overburnHolding) {
			overburnHolding = false;
			ClientPlayNetworking.send(new ZFighterNet.OverburnHold(false));
		}
		sneakHoldTicks = 0;
	}

	private static void input(Minecraft minecraft) {
		KeyMapping mapping = key(minecraft);
		LocalPlayer player = minecraft.player;
		if (mapping == null || player == null || !Powers.has(player, ZFighterPower.INSTANCE) || WebSymbiote.isBonded(player)) {
			if (overburnHolding && player != null) {
				releaseOverburn();
			} else {
				overburnHolding = false;
				sneakHoldTicks = 0;
			}
			return;
		}
		boolean pressed = false;
		while (mapping.consumeClick()) {
			pressed = true;
		}
		if (minecraft.gui.screen() != null || player.isSpectator()) {
			releaseOverburn();
			return;
		}
		boolean down = mapping.isDown();
		if (down && player.isShiftKeyDown()) {
			sneakHoldTicks++;
			if (sneakHoldTicks >= OVERBURN_HOLD_TICKS && !overburnHolding) {
				overburnHolding = true;
				ClientPlayNetworking.send(new ZFighterNet.OverburnHold(true));
			}
			return;
		}
		if (overburnHolding) {
			releaseOverburn();
			return;
		}
		if (sneakHoldTicks > 0) {
			sneakHoldTicks = 0;
			ClientPlayNetworking.send(new ZFighterNet.Train(ZFighterNet.CYCLE_LIMIT, 0));
			return;
		}
		if (pressed && !player.isShiftKeyDown()) {
			minecraft.gui.setScreen(new ZFighterTrainingScreen());
		}
	}
}
