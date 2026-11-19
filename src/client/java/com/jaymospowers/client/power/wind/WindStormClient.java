package com.jaymospowers.client.power.wind;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.power.wind.StormNet;
import com.jaymospowers.power.wind.WindModes;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Stormbound's keys and gauge (see {@code StormNet}). Draw (hold), Hurl (hold; sneak while winding up switches the
 * throw), Recall (sneak: bind), Twin, Landing and Road (sneak: mark). All of them only do anything for a Wind user with the
 * Stormbound or Thunderhead mode on; all can be rebound. The gauge is a chip in Wind's style, left of the ability chips.
 */
final class WindStormClient {
	private static final int CHIP = 20;
	private static final int STORM_COLOR = 0xB070FF, GLOW = 0x8FB4FF;
	private static final int[] DEFAULTS = {InputConstants.KEY_MINUS, InputConstants.KEY_EQUALS, InputConstants.KEY_HOME, InputConstants.KEY_PAGEUP,
		InputConstants.KEY_PAGEDOWN, InputConstants.KEY_INSERT};
	private static final String[] NAMES = {"draw", "hurl", "recall", "twin", "landing", "road"};
	/** Draw and Hurl are held; the rest are taps. */
	private static final boolean[] HELD = {true, true, false, false, false, false};

	private static final KeyMapping[] KEYS = new KeyMapping[NAMES.length];
	private static final boolean[] WAS = new boolean[NAMES.length];

	private static int charge, storm, mode, flags;

	private WindStormClient() {
	}

	static void init() {
		for (int i = 0; i < KEYS.length; i++) {
			KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.wind_storm_" + NAMES[i], DEFAULTS[i], JaymosPowersClient.CATEGORY));
		}
		ClientPlayNetworking.registerGlobalReceiver(StormNet.Hud.TYPE, (payload, ctx) -> {
			charge = payload.charge();
			storm = payload.storm();
			mode = payload.mode();
			flags = payload.flags();
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			charge = 0;
			storm = 0;
			mode = 0;
			flags = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(WindStormClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean active = player != null && minecraft.getConnection() != null && minecraft.gui.screen() == null && WindPowerClient.isActive(player)
			&& WindModes.storm(player) && ClientPlayNetworking.canSend(StormNet.Key.TYPE);
		for (int i = 0; i < KEYS.length; i++) {
			if (HELD[i]) {
				boolean down = active && KEYS[i].isDown();
				if (down != WAS[i]) {
					WAS[i] = down;
					if (down || ClientPlayNetworking.canSend(StormNet.Key.TYPE)) {
						ClientPlayNetworking.send(new StormNet.Key(i, down));
					}
				}
			} else {
				while (KEYS[i].consumeClick()) {
					if (active) {
						ClientPlayNetworking.send(new StormNet.Key(i, true));
					}
				}
			}
		}
	}

	/** The gauge: a chip with the stored charge rising in it, lit while the storm state lasts, and the throw mode under it. */
	static void render(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int x, int y, float time) {
		if (!WindModes.storm(player)) {
			return;
		}
		boolean storming = storm > 0;
		int fillHeight = storming ? Math.round(CHIP * Math.min(1.0F, storm / 400.0F)) : Math.round(CHIP * Math.min(100, charge) / 100.0F);
		int border = storming ? lerp(STORM_COLOR, 0xFFFFFF, 0.5F + 0.5F * (float)Math.sin(time * 0.6F)) : (flags & StormNet.BOUND) != 0 ? GLOW : 0x505050;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.fill(x + 1, y + CHIP - fillHeight, x + CHIP - 1, y + CHIP - 1, 0xB0000000 | (storming ? STORM_COLOR : GLOW));
		graphics.outline(x, y, CHIP, CHIP, 0xFF000000 | border);
		graphics.item(new ItemStack((net.minecraft.world.level.ItemLike)Items.LIGHTNING_ROD.weathering().unaffected()), x + 2, y + 2);
		String key = font.plainSubstrByWidth(KEYS[0].getTranslatedKeyMessage().getString(), CHIP - 2);
		graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, 0xFFFFFFFF);
		// the hammer's throw, and what is armed or marked
		StringBuilder line = new StringBuilder(Component.translatable("message.jaymospowers.wind_storm.mode_short_" + mode).getString());
		if ((flags & StormNet.ARMED) != 0) {
			line.append(" +");
		}
		if ((flags & StormNet.MARKED) != 0) {
			line.append(" *");
		}
		String text = line.toString();
		graphics.text(font, text, x + CHIP - font.width(text), y - 10, (flags & StormNet.CHARGING) != 0 ? 0xFFFFFFFF : 0xFFA0A0A0);
	}

	private static int lerp(int a, int b, float t) {
		int r = (int)(((a >> 16) & 0xFF) * (1.0F - t) + ((b >> 16) & 0xFF) * t);
		int g = (int)(((a >> 8) & 0xFF) * (1.0F - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int)((a & 0xFF) * (1.0F - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
