package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.power.arsenal.ArsenalPowerClient;
import com.jaymospowers.client.power.chrono.ChronoPowerClient;
import com.jaymospowers.client.power.web.WebPowerClient;
import com.jaymospowers.client.power.wind.WindPowerClient;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.net.PowerPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class JaymosPowersClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(JaymosPowers.id("powers"));
	private static final int[] DEFAULT_KEYS = {InputConstants.KEY_R, InputConstants.KEY_G, InputConstants.KEY_V, InputConstants.KEY_B};
	private static final KeyMapping[] ABILITY_KEYS = new KeyMapping[PowerKey.values().length];
	private static KeyMapping menuKey;
	/** The ascended ability's key (C by default): silent until the power ascends. */
	private static KeyMapping ultimateKey;
	private static boolean ultimateWasDown;

	private static final boolean[] wasDown = new boolean[PowerKey.values().length];
	private static @Nullable Identifier lastPower;
	private static boolean affinityPending;

	@Override
	public void onInitializeClient() {
		for (PowerKey key : PowerKey.values()) {
			ABILITY_KEYS[key.ordinal()] = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.jaymospowers." + key.id(), DEFAULT_KEYS[key.ordinal()], CATEGORY));
		}
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.menu", InputConstants.KEY_H, CATEGORY));
		ultimateKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.ultimate", InputConstants.KEY_C, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(JaymosPowersClient::tick);
		// the affinity choice waits until no other screen is open
		ClientPlayNetworking.registerGlobalReceiver(PowerPayloads.OpenAffinity.TYPE, (payload, context) -> affinityPending = true);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("power_hud"), PowerHud::render);

		WindPowerClient.init();
		ArsenalPowerClient.init();
		WebPowerClient.init();
		ChronoPowerClient.init();
	}

	/** The key mapping for an ability key (to show its bound key in a HUD, say). */
	public static KeyMapping keyMapping(PowerKey key) {
		return ABILITY_KEYS[key.ordinal()];
	}

	/** The ascended ability's key mapping. */
	public static KeyMapping ultimateKey() {
		return ultimateKey;
	}

	/** Whether the local player has unlocked this ability yet (powers' HUDs dim the locked ones). */
	public static boolean isUnlocked(PowerKey key) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null || Mastery.isUnlocked(player, key);
	}

	/** The local player's power's client half, or null. */
	public static @Nullable PowerClient active(LocalPlayer player) {
		Power power = Powers.of(player);
		return power == null ? null : PowerClients.get(power.id());
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			lastPower = null;
			return;
		}
		Power power = Powers.of(player);
		if (affinityPending && minecraft.gui.screen() == null) {
			affinityPending = false;
			if (power == null && !player.isCreative() && !com.jaymospowers.core.Affinity.chosen(player)) {
				minecraft.gui.setScreen(new AffinityScreen());
			}
		}
		while (menuKey.consumeClick()) {
			// in survival there's nothing to see until your power has awakened (but an unmade affinity choice comes back)
			if (power != null || player.isCreative()) {
				minecraft.gui.setScreen(new PowerScreen());
			} else if (!com.jaymospowers.core.Affinity.chosen(player)) {
				minecraft.gui.setScreen(new AffinityScreen());
			}
		}

		Identifier powerId = power == null ? null : power.id();
		if (!java.util.Objects.equals(powerId, lastPower)) {
			PowerClient old = PowerClients.get(lastPower);
			if (old != null) {
				old.onActiveChanged(player, false);
			}
			PowerClient now = PowerClients.get(powerId);
			if (now != null) {
				now.onActiveChanged(player, true);
			}
			lastPower = powerId;
		}
		PowerClient client = PowerClients.get(powerId);

		// report ability key changes (a key held while a screen opens counts as released)
		for (PowerKey key : PowerKey.values()) {
			// a locked ability's key does nothing here; the server still says it's locked when it's pressed
			boolean down = power != null && minecraft.gui.screen() == null && ABILITY_KEYS[key.ordinal()].isDown();
			if (down != wasDown[key.ordinal()]) {
				wasDown[key.ordinal()] = down;
				ClientPlayNetworking.send(new PowerPayloads.KeyInput(key.ordinal(), down));
				if (client != null && Mastery.isUnlocked(player, key)) {
					client.onKey(player, key, down);
				}
			}
		}
		boolean ultimate = power != null && Fate.isAscended(player) && minecraft.gui.screen() == null && ultimateKey.isDown();
		if (ultimate != ultimateWasDown) {
			ultimateWasDown = ultimate;
			ClientPlayNetworking.send(new PowerPayloads.UltimateKey(ultimate));
			if (client != null) {
				client.onUltimate(player, ultimate);
			}
		}
		if (client != null && !minecraft.isPaused()) {
			client.tick(minecraft, player);
		}
	}
}
