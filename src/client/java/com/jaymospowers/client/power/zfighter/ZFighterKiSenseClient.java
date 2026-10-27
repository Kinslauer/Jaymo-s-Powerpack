package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterSensesNet;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.17.45, Andy's NOEA picks) Ki sense on the sensing player's screen: its key (registered unbound, Andy picks one in
 * Controls) sends the toggle, and the server's reports ({@link ZFighterSensesNet.Sense}, four a second) say who is in
 * range and how strong they are beside you. Those get the same through-the-wall outline Smoke Sight uses (two small
 * mixins ask {@link #color}): dim when weaker, pale when even, bright red when stronger.
 */
public final class ZFighterKiSenseClient {
	/** Outline colours by tier ({@code ZFighterKiSense.WEAKER}, {@code EVEN}, {@code STRONGER}). */
	static final int[] COLORS = {0x5E7C99, 0xE6E2C4, 0xFF2A2A};
	/** A mark stays this many ticks past its report (the server sends one every five). */
	static final int HOLD_TICKS = 12;

	private static KeyMapping key;
	private static boolean on;
	/** Entity id → {tier, game time it lapses}. */
	private static final Map<Integer, long[]> MARKS = new HashMap<>();

	private ZFighterKiSenseClient() {
	}

	static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.ki_sense", InputConstants.UNKNOWN.getType(),
			InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		ClientPlayNetworking.registerGlobalReceiver(ZFighterSensesNet.Sense.TYPE, (payload, ctx) -> {
			Minecraft minecraft = ctx.client();
			on = payload.on();
			MARKS.clear();
			if (on && minecraft.level != null) {
				long lapse = minecraft.level.getGameTime() + HOLD_TICKS;
				for (ZFighterSensesNet.Mark mark : payload.marks()) {
					MARKS.put(mark.id(), new long[] {Math.max(0, Math.min(COLORS.length - 1, mark.tier())), lapse});
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterKiSenseClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			on = false;
			MARKS.clear();
		});
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean pressed = false;
		while (key.consumeClick()) {
			pressed = true;
		}
		if (player == null || minecraft.level == null) {
			return;
		}
		if (pressed && minecraft.gui.screen() == null && !player.isSpectator() && Powers.has(player, ZFighterPower.INSTANCE)) {
			ClientPlayNetworking.send(new ZFighterSensesNet.SenseToggle());
		}
		if (on && !Powers.has(player, ZFighterPower.INSTANCE)) {
			on = false;
			MARKS.clear();
		}
	}

	/** Whether the sense is on (for the HUD). */
	public static boolean on() {
		return on;
	}

	/** The outline colour {@code entity} gets on this screen from ki sense, or 0 for none. */
	public static int color(Entity entity) {
		if (!on || MARKS.isEmpty() || !(entity instanceof LivingEntity) || !entity.isAlive()) {
			return 0;
		}
		long[] mark = MARKS.get(entity.getId());
		Minecraft minecraft = Minecraft.getInstance();
		if (mark == null || minecraft.level == null || minecraft.level.getGameTime() > mark[1] || entity == minecraft.player) {
			return 0;
		}
		return COLORS[(int)mark[0]];
	}

	static String keyLabel() {
		return key == null ? "?" : key.getTranslatedKeyMessage().getString();
	}
}
