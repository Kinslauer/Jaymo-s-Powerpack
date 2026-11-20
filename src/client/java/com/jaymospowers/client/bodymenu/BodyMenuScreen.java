package com.jaymospowers.client.bodymenu;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.core.BodyMenu;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * The body menu ({@link BodyMenu}): a small list beside the crosshair of what can be done to the body you tapped, its name
 * on top. Click a row, or press its number; right-click, Escape or stepping off closes it. It doesn't pause, and the world
 * stays in view.
 */
final class BodyMenuScreen extends Screen {
	// ===== the knobs ================================================================================================
	/** Gap from the crosshair to the menu's left edge, a row's height, the padding, and the narrowest the menu gets (gui pixels). */
	static final int GAP = 14, ROW_H = 14, PAD = 5, MIN_W = 90;
	/** It closes once the body is this much past your reach (blocks). */
	static final double CLOSE_SLACK = 2.0;
	// ================================================================================================================

	private final int entity;
	private final List<BodyMenu.Row> rows;
	private int hovered = -1;
	private boolean chosen;

	BodyMenuScreen(BodyMenu.Menu menu) {
		super(menu.title());
		this.entity = menu.entity();
		this.rows = menu.rows();
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
		LocalPlayer player = minecraft.player;
		Entity body = player == null ? null : player.level().getEntity(entity);
		if (player == null || !player.isAlive() || body == null || !BodyMenu.body(body) || !player.isWithinEntityInteractionRange(body, CLOSE_SLACK)) {
			onClose();
		}
	}

	private int menuWidth() {
		int w = Math.max(MIN_W, font.width(title) + PAD * 2);
		for (int i = 0; i < rows.size(); i++) {
			w = Math.max(w, font.width(label(i)) + PAD * 2);
		}
		return w;
	}

	private int left() {
		return width / 2 + GAP;
	}

	/** The top of the menu: its rows centred on the crosshair, the name above them. */
	private int top() {
		return height / 2 - (rows.size() * ROW_H) / 2 - ROW_H;
	}

	private Component label(int i) {
		return Component.literal((i + 1) + "  ").append(rows.get(i).label());
	}

	private int rowAt(double x, double y) {
		int left = left(), top = top() + ROW_H;
		if (x < left || x >= left + menuWidth() || y < top) {
			return -1;
		}
		int i = (int) ((y - top) / ROW_H);
		return i < rows.size() ? i : -1;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			onClose();
			return true;
		}
		int i = rowAt(event.x(), event.y());
		if (i >= 0) {
			choose(i);
		}
		return true;
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		int key = event.key();
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
			int i = key - InputConstants.KEY_1;
			if (i < rows.size()) {
				choose(i);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private void choose(int i) {
		if (chosen) {
			return;
		}
		chosen = true;
		ClientPlayNetworking.send(new BodyMenu.Choose(entity, rows.get(i).id()));
		if (minecraft.player != null) {
			minecraft.player.level().playLocalSound(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ(),
				SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.2F, 1.4F, false);
		}
		onClose();
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		int left = left(), top = top(), w = menuWidth(), h = (rows.size() + 1) * ROW_H + PAD;
		hovered = rowAt(mouseX, mouseY);
		UiStyle.panel(graphics, left, top, w, h, UiStyle.ACCENT);
		UiStyle.line(graphics, font, title, left + PAD, top + (ROW_H - 8) / 2 + 1, w - PAD * 2, UiStyle.DIM);
		for (int i = 0; i < rows.size(); i++) {
			int y = top + ROW_H * (i + 1);
			UiStyle.row(graphics, left + 1, y, left + w - 1, y + ROW_H, i, i == hovered);
			UiStyle.line(graphics, font, label(i), left + PAD, y + (ROW_H - 8) / 2, w - PAD * 2, i == hovered ? UiStyle.GOLD : UiStyle.TEXT);
		}
	}
}
