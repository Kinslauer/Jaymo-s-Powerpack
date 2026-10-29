package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ApeRage;
import com.jaymospowers.power.zfighter.ZFighterForms;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * (0.17.194) Andy: "a menu for z fighters, accessed by holding C that lets you pick the form instead of sequential up and
 * down, as long as you have it unlocked". Hold C a moment: a ring of the forms he has ({@link ZFighterForms#choices}),
 * laid out like the technique wheel ({@link ZFighterWheelScreen}); point at one and let go (or click) to go straight to
 * it. A quick tap of C still steps up (sneak+C down) as before ({@link #filter}). It doesn't pause.
 */
public final class ZFighterFormMenu extends Screen {
	/** Held this long (ticks), C opens the menu instead of tapping. */
	static final int HOLD_TICKS = 6;
	private static final int RADIUS = 62;
	private static final int TILE = 26;
	private static final int DEAD_ZONE = 24;
	private static final int ACCENT = 0xFF000000 | ZFighterPower.COLOR;
	private static final int INFO_W = 210;
	private static final int INFO_H = 30;

	/** C's state: ticks held so far (-1: not being held), a press let straight through, the menu's key still down. */
	private static int held = -1;
	private static boolean passing, waitRelease;

	private final List<Integer> forms;
	private int selected = -1;
	private boolean chosen;

	private ZFighterFormMenu(List<Integer> forms) {
		super(Component.translatable("screen.jaymospowers.zfighter_forms"));
		this.forms = forms;
	}

	/**
	 * Called each tick from {@code JaymosPowersClient} with C's own state ({@code raw}) and whether keys are blocked (a
	 * screen, a seizure); returns whether C counts as down for the power. A Z Fighter's C is held back: let go quickly and
	 * it goes through as a tap; held {@link #HOLD_TICKS}, the menu opens. Sneak+C, the ape and other powers go straight through.
	 */
	public static boolean filter(Minecraft minecraft, LocalPlayer player, boolean raw, boolean blocked) {
		boolean down = raw && !blocked;
		if (passing) {
			passing = down;
			return down;
		}
		if (waitRelease) {
			waitRelease = raw;
			return false;
		}
		if (held < 0) {
			if (!down) {
				return false;
			}
			if (!Powers.has(player, ZFighterPower.INSTANCE) || player.isShiftKeyDown() || ZFighterLineage.ape(player)) {
				passing = true;
				return true;
			}
			held = 0;
			return false;
		}
		if (down) {
			if (++held >= HOLD_TICKS) {
				held = -1;
				waitRelease = true;
				List<Integer> forms = ZFighterForms.choices(player);
				if (forms.size() > 1) {
					minecraft.gui.setScreen(new ZFighterFormMenu(forms));
				}
			}
			return false;
		}
		held = -1;
		if (!blocked) {
			JaymosPowersClient.tap(JaymosPowersClient.TAP_ULTIMATE);
		}
		return false;
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
		// let go of C: take what's picked (polled, in case the release went elsewhere)
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(JaymosPowersClient.ultimateKey());
		if (bound.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(bound.getValue())) {
			choose();
		}
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isAlive() || !Powers.has(player, ZFighterPower.INSTANCE)) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(final KeyEvent event) {
		if (JaymosPowersClient.ultimateKey().matches(event)) {
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
		LocalPlayer player = minecraft.player;
		if (selected >= 0 && player != null && forms.get(selected) != current(player)) {
			ClientPlayNetworking.send(new ZFighterNet.Train(ZFighterNet.PICK_FORM, forms.get(selected)));
		}
		onClose();
	}

	private static int current(LocalPlayer player) {
		Integer form = player.getAttached(ZFighterPower.FORM);
		return form == null ? 0 : form;
	}

	/** The form's name: its own, or "Base". */
	private static Component name(LocalPlayer player, int form) {
		return form == 0 ? Component.translatable("screen.jaymospowers.zfighter_forms.base") : Component.translatable(ZFighterLineage.formKey(player, form));
	}

	/** A tile's few letters: B, 1-3 (L1-L3 on the legendary line), P, P+, G. */
	private static String tag(int form) {
		if (form == 0) {
			return "B";
		}
		if (form == ApeRage.PRIMAL_SUPER) {
			return "P";
		}
		if (form == ApeRage.SUPER_4) {
			return "P+";
		}
		if (form == ZFighterForms.GOD) {
			return "G";
		}
		if (form >= ZFighterForms.LEGEND_1 && form <= ZFighterForms.LEGEND_3) {
			return "L" + (form - ZFighterForms.LEGEND_1 + 1);
		}
		return Integer.toString(form);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		int n = forms.size();
		double slice = 360.0 / n;
		int now = current(player);
		int cx = width / 2;
		int cy = height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;
		int before = selected;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			selected = Math.floorMod((int)Math.round(angle / slice), n);
		}
		if (selected != before && selected >= 0) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
				0.15F, 1.6F + selected * 0.04F, false);
		}

		// a dark disc behind the ring, a thin ring, and the pointer
		int outer = RADIUS + TILE / 2 + 6;
		for (int y = -outer; y <= outer; y++) {
			int half = (int)Math.sqrt(outer * outer - y * y);
			graphics.fill(cx - half, cy + y, cx + half, cy + y + 1, 0xA0081018);
		}
		for (int k = 0; k < 96; k++) {
			double t = k * Math.PI * 2.0 / 96.0;
			int x = cx + (int)Math.round(Math.cos(t) * (RADIUS - TILE / 2 - 6));
			int y = cy + (int)Math.round(Math.sin(t) * (RADIUS - TILE / 2 - 6));
			graphics.fill(x, y, x + 1, y + 1, UiStyle.ROW);
		}
		if (selected >= 0) {
			double t = Math.atan2(dy, dx);
			for (int r = DEAD_ZONE; r < RADIUS - TILE / 2 - 6; r += 4) {
				int x = cx + (int)Math.round(Math.cos(t) * r);
				int y = cy + (int)Math.round(Math.sin(t) * r);
				graphics.fill(x, y, x + 2, y + 2, ACCENT);
			}
		}

		for (int i = 0; i < n; i++) {
			int form = forms.get(i);
			double t = Math.toRadians(i * slice - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int)Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			boolean picked = i == selected;
			boolean on = form == now;
			int color = 0xFF000000 | ZFighterKiGlow.color(player, form);
			graphics.fill(x, y, x + TILE, y + TILE, UiStyle.CHIP);
			graphics.fill(x, y, x + TILE, y + TILE, picked ? UiStyle.ROW_HOVER : i % 2 == 0 ? UiStyle.ROW : UiStyle.ROW_ALT);
			// the form's own colour, as a band across the tile
			graphics.fill(x + 3, y + 3, x + TILE - 3, y + 8, color);
			graphics.outline(x, y, TILE, TILE, picked ? ACCENT : on ? color : 0x60FFFFFF);
			String tag = tag(form);
			graphics.text(font, tag, x + TILE / 2 - font.width(tag) / 2, y + TILE / 2 - 1, picked || on ? UiStyle.TEXT : UiStyle.DIM);
			if (on) {
				graphics.fill(x + 2, y + TILE - 3, x + TILE - 2, y + TILE - 1, color);
			}
		}

		// the middle: the pick's name; under the ring, what letting go does
		Component name = selected >= 0 ? name(player, forms.get(selected)) : null;
		if (name != null) {
			UiStyle.lineCentred(graphics, font, name, cx, cy - 4, 2 * DEAD_ZONE + 20, UiStyle.TEXT);
		}
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, height - 4 - INFO_H);
		UiStyle.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		UiStyle.line(graphics, font, name != null ? name : title, ix + 8, iy + 6, INFO_W - 16, ACCENT);
		Component status = selected < 0 ? Component.translatable("screen.jaymospowers.zfighter_forms.hint")
			: forms.get(selected) == now ? Component.translatable("screen.jaymospowers.zfighter_forms.on")
			: Component.translatable("screen.jaymospowers.zfighter_forms.take");
		UiStyle.line(graphics, font, status, ix + 8, iy + 17, INFO_W - 16, name != null ? UiStyle.DIM : UiStyle.HINT);
	}
}
