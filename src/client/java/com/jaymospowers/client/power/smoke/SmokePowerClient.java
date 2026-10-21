package com.jaymospowers.client.power.smoke;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.power.smoke.SmokePower;
import com.jaymospowers.power.smoke.SmokeState;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Smoke's client half: right of the hotbar, in Wind's chip style, R G V B with their cooldowns; over the crosshair, how
 * long Smokescreen has left to pour or Smother to choke, or Vapour Form's speed while cruising. The smoke itself (seen by
 * everyone) is {@link SmokeFieldClient}; Vapour Form's flight is {@link SmokeFlightClient} and its smoke legs
 * {@link SmokeFlightPose}.
 */
public final class SmokePowerClient implements PowerClient {
	public static final SmokePowerClient INSTANCE = new SmokePowerClient();

	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int CHIP_COLOR = SmokePower.COLOR;
	/** How long each runs at most (ticks), for the bar over the crosshair; mirrors the server's tuning. */
	private static final int POUR_TICKS = 200;
	private static final int SMOTHER_TICKS = 120;

	private SmokePowerClient() {
	}

	public static void init() {
		PowerClients.register(SmokePower.ID, INSTANCE);
		SmokeFieldClient.init();
		SmokeJetClient.init();
		SmokeSteam.register();
		ClientTickEvents.END_CLIENT_TICK.register(SmokeFlightPose::tick);
	}

	/** From the avatar renderer's extractRenderState (everyone): Vapour Form's smoke legs and every move's pose. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		SmokeFlightPose.extract(entity, state);
		SmokeMovePose.extract(entity, state, partialTicks);
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		SmokeFlightClient.tick(minecraft, player);
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		SmokeFlightClient.reset();
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Font font = Minecraft.getInstance().font;
		long now = player.level().getGameTime();
		SmokeState state = SmokePower.state(player);
		bar(graphics, font, player, state, now, graphics.guiWidth() / 2, graphics.guiHeight() / 2);
		chips(graphics, font, state, now);
	}

	private static void bar(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, SmokeState state, long now, int cx, int cy) {
		float fill;
		Component label;
		if (state.pourFrom() >= 0) {
			fill = 1.0F - Mth.clamp((now - state.pourFrom()) / (float)POUR_TICKS, 0.0F, 1.0F);
			label = Component.translatable("hud.jaymospowers.smoke.pour");
		} else if (state.smotherFrom() >= 0) {
			fill = 1.0F - Mth.clamp((now - state.smotherFrom()) / (float)SMOTHER_TICKS, 0.0F, 1.0F);
			label = Component.translatable("hud.jaymospowers.smoke.smother");
		} else if (SmokeFlightClient.cruising()) {
			fill = (float)SmokeFlightClient.speedFraction(player);
			label = Component.translatable("hud.jaymospowers.smoke.vapour");
		} else {
			return;
		}
		int width = 52;
		int x0 = cx - width / 2;
		int y = cy - 33;
		graphics.fill(x0 - 1, y - 1, x0 + width + 1, y + 4, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(width * fill), y + 3, 0xFF000000 | CHIP_COLOR);
		graphics.centeredText(font, label.copy().withColor(CHIP_COLOR), cx, y - 11, 0xFFFFFFFF);
	}

	private static void chips(GuiGraphicsExtractor graphics, Font font, SmokeState state, long now) {
		int total = CHIP * 4 + CHIP_GAP * 3;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int x = width / 2 + 91 + 30;
		int y = height - CHIP - 1;
		if (x + total > width - 2) {
			x = width / 2 + 91 - total;
			y = height - 22 - 30 - CHIP;
		}
		int step = CHIP + CHIP_GAP;
		chip(graphics, font, x, y, PowerKey.ABILITY_1, Items.PHANTOM_MEMBRANE, state, SmokeState.VAPOUR, now);
		chip(graphics, font, x + step, y, PowerKey.ABILITY_2, Items.CAMPFIRE, state, SmokeState.SCREEN, now);
		chip(graphics, font, x + step * 2, y, PowerKey.ABILITY_3, Items.FIREWORK_STAR, state, SmokeState.BOMB, now);
		chip(graphics, font, x + step * 3, y, PowerKey.ABILITY_4, Items.CHARCOAL, state, SmokeState.SMOTHER, now);
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, PowerKey key, Item icon, SmokeState state, int slot, long now) {
		String label = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString(), CHIP - 2);
		if (!JaymosPowersClient.isUnlocked(key)) {
			graphics.fill(x, y, x + CHIP, y + CHIP, 0xB0000000);
			graphics.outline(x, y, CHIP, CHIP, 0x60505050);
			graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, 0xFF505050);
			return;
		}
		long left = state.left(slot, now);
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | CHIP_COLOR : 0xA0505050);
		graphics.item(new ItemStack(icon), x + 2, y + 2);
		if (!ready) {
			int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)Math.max(1, state.length()[slot])));
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = left >= 200 ? Long.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}
}
