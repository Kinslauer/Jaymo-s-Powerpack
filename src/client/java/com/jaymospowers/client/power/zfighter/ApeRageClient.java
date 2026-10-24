package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ApeRage;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;

/**
 * (0.15.148) The client half of {@link ApeRage}: while a fit has the Great Ape, each new movement key press is a heave
 * against it (sent to the server, jolting the view); the HUD shows the rage building, and in a fit, the bar to break it.
 */
public final class ApeRageClient {
	/** Each heave jolts the view this much (degrees), more as the bar fills. */
	static final float JOLT = 0.6F, JOLT_PER = 1.2F;
	/** The bars: width, how far above the bottom (pixels). */
	static final int BAR_W = 120, BAR_UP = 62;
	static final int RAGE_COLOR = 0xC03020, FIT_COLOR = 0xFFD040;

	private static Input heldKeys = Input.EMPTY;
	private static int pressTick = -1;

	private ApeRageClient() {
	}

	/** The movement keys during a fit (from the ape's input mixin, before it takes them). */
	public static void struggleKeys(Input keys) {
		boolean pressed = keys.forward() && !heldKeys.forward() || keys.backward() && !heldKeys.backward() || keys.left() && !heldKeys.left()
			|| keys.right() && !heldKeys.right() || keys.jump() && !heldKeys.jump() || keys.shift() && !heldKeys.shift()
			|| keys.sprint() && !heldKeys.sprint();
		heldKeys = keys;
		LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (!pressed || player == null || player.tickCount == pressTick) {
			return;
		}
		pressTick = player.tickCount;
		Float share = player.getAttached(ApeRage.FIT);
		com.jaymospowers.client.ScreenShake.add(JOLT + JOLT_PER * (share == null ? 0.0F : share));
		if (ClientPlayNetworking.canSend(ApeRage.Struggle.TYPE)) {
			ClientPlayNetworking.send(new ApeRage.Struggle());
		}
	}

	static void hud(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, float time) {
		if (!ZFighterLineage.ape(player) || !player.hasAttached(ApeRage.RAGE)) {
			return;
		}
		int x = (graphics.guiWidth() - BAR_W) / 2;
		int y = graphics.guiHeight() - BAR_UP;
		Float fit = player.getAttached(ApeRage.FIT);
		if (fit != null) {
			// the fight against the fit: a fuller, throbbing bar
			int fill = Math.round(BAR_W * Mth.clamp(fit, 0.0F, 1.0F));
			graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + 6, 0xA0000000);
			graphics.fill(x, y, x + fill, y + 5, 0xFF000000 | FIT_COLOR);
			graphics.centeredText(font, Component.translatable("hud.jaymospowers.zfighter.ape_fit"), graphics.guiWidth() / 2, y - 11,
				0xFF000000 | (Mth.sin(time * 0.4F) > 0.0F ? 0xFF6040 : 0xFFD0C0));
			return;
		}
		float rage = Mth.clamp(ApeRage.rage(player) / 100.0F, 0.0F, 1.0F);
		graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + 3, 0x80000000);
		graphics.fill(x, y, x + Math.round(BAR_W * rage), y + 2, 0xFF000000 | RAGE_COLOR);
		graphics.centeredText(font, Component.translatable("hud.jaymospowers.zfighter.ape_rage", ApeRage.primal(player)), graphics.guiWidth() / 2,
			y - 10, 0xFFC0A0A0);
	}
}
