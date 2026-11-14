package com.jaymospowers.client.power.gravity;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.gravity.GravityRockOrders;
import com.jaymospowers.power.gravity.GravityRocks;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The harnessed rocks' order wheel (G held with B, the rocks out; the Attendant Blades' wheel, for stones): Harry,
 * Steer, Stone storm and Closing ring. Let go of the key over a slice (or click) and the order goes
 * ({@link GravityRockOrders.Pick}); the middle cancels. It doesn't pause.
 */
final class GravityRocksScreen extends Screen {
	private static final int RADIUS = 60;
	private static final int TILE = 26;
	private static final int DEAD_ZONE = 22;
	private static final int ACCENT = 0xFFD8B070;
	private static final int INFO_W = 230;
	private static final int INFO_H = 30;
	private static final Item[] ICONS = {Items.CROSSBOW, Items.ENDER_EYE, Items.WIND_CHARGE, Items.IRON_BARS};

	private final KeyMapping key;
	private int selected = -1;
	private boolean chosen;

	GravityRocksScreen(KeyMapping key) {
		super(Component.translatable("screen.jaymospowers.gravity_rocks"));
		this.key = key;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		// no blur: the world stays in view
	}

	@Override
	public void tick() {
		// let go of the key: the picked order goes (polled, in case the release went elsewhere)
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		if (bound.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(bound.getValue())) {
			choose();
		}
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isAlive() || !Powers.has(player, GravityPower.INSTANCE) || player.getAttached(GravityRocks.ROCKS) == null) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(final KeyEvent event) {
		if (key.matches(event)) {
			choose();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		choose();
		return true;
	}

	private void choose() {
		if (chosen) {
			return;
		}
		chosen = true;
		if (selected >= 0 && ClientPlayNetworking.canSend(GravityRockOrders.Pick.TYPE)) {
			ClientPlayNetworking.send(new GravityRockOrders.Pick(selected));
		}
		onClose();
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.getAttached(GravityRocks.ROCKS) == null) {
			return;
		}
		int n = GravityRockOrders.PICKS;
		double slice = 360.0 / n;
		int cx = width / 2;
		int cy = height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;
		int before = selected;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			selected = Math.floorMod((int) Math.round(angle / slice), n);
		}
		if (selected != before && selected >= 0) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
				0.15F, 1.6F + selected * 0.04F, false);
		}
		int outer = RADIUS + TILE / 2 + 6;
		UiStyle.wheel(graphics, cx, cy, RADIUS, TILE);
		if (selected >= 0) {
			UiStyle.wheelPointer(graphics, cx, cy, RADIUS, TILE, Math.atan2(dy, dx), ACCENT);
		}
		for (int i = 0; i < n; i++) {
			double t = Math.toRadians(i * slice - 90.0);
			int x = cx + (int) Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int) Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			boolean picked = i == selected;
			UiStyle.wheelTile(graphics, x, y, TILE, i, picked, picked ? ACCENT : 0x60FFFFFF);
			graphics.item(new ItemStack(ICONS[i]), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
		}
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, height - 4 - INFO_H);
		UiStyle.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		if (selected < 0) {
			UiStyle.line(graphics, font, title, ix + 8, iy + 6, INFO_W - 16, ACCENT);
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.gravity_rocks.hint"), ix + 8, iy + 17, INFO_W - 16, UiStyle.HINT);
		} else {
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.gravity_rocks." + selected), ix + 8, iy + 6, INFO_W - 16, ACCENT);
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.gravity_rocks." + selected + ".hint"), ix + 8, iy + 17, INFO_W - 16, UiStyle.DIM);
		}
	}
}
