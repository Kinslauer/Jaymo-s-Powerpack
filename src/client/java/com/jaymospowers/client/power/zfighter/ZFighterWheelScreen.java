package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterSlots;
import com.jaymospowers.power.zfighter.ZFighterTraining;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * (0.17.46, Andy's NOEA picks) The technique wheel (Andy: "Hold Y for a technique wheel"): hold Y, point the mouse at
 * one of the eight slots ({@link ZFighterSlots}) and let go (or click) to put that technique on its key, G for a
 * quick one, sneak+V for a charged one. Laid out like the Rig's radial menu, clockwise from the top; a lit strip under
 * what's on G or V now. It doesn't pause, and the world stays in view.
 */
final class ZFighterWheelScreen extends Screen {
	private static final int RADIUS = 62;
	private static final int TILE = 26;
	private static final int DEAD_ZONE = 24;
	private static final int ACCENT = 0xFF000000 | ZFighterPower.COLOR;
	private static final int INFO_W = 210;
	private static final int INFO_H = 30;
	private static final int SLICE = 360 / ZFighterSlots.SLOTS;

	private static KeyMapping key;

	private int selected = -1;
	private boolean chosen;

	private ZFighterWheelScreen() {
		super(Component.translatable("screen.jaymospowers.zfighter_wheel"));
	}

	/** Called once from {@link ZFighterSensesClient#init}: the wheel's key, Y. */
	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.zfighter_wheel", InputConstants.KEY_Y, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			boolean pressed = false;
			while (key.consumeClick()) {
				pressed = true;
			}
			LocalPlayer player = minecraft.player;
			if (pressed && player != null && minecraft.gui.screen() == null && !player.isSpectator() && Powers.has(player, ZFighterPower.INSTANCE)) {
				minecraft.gui.setScreen(new ZFighterWheelScreen());
			}
		});
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
		// let go of the key: take what's picked (polled, in case the release went elsewhere)
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
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
		LocalPlayer player = minecraft.player;
		if (selected >= 0 && player != null) {
			int technique = ZFighterSlots.of(player).get(selected);
			if (technique != ZFighterSlots.EMPTY && !onKey(player, technique)) {
				ClientPlayNetworking.send(new ZFighterNet.Train(ZFighterNet.EQUIP_TECHNIQUE, technique));
			}
		}
		onClose();
	}

	/** Whether this technique is on its key (G or V) now. */
	private static boolean onKey(LocalPlayer player, int technique) {
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		return ZFighterTraining.charged(technique) ? data.charged() == technique : data.quick() == technique;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		List<Integer> slots = ZFighterSlots.of(player);
		int cx = width / 2;
		int cy = height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;
		int before = selected;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			selected = Math.floorMod((int)Math.round(angle / SLICE), ZFighterSlots.SLOTS);
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

		for (int i = 0; i < ZFighterSlots.SLOTS; i++) {
			double t = Math.toRadians(i * SLICE - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int)Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			int technique = slots.get(i);
			boolean empty = technique == ZFighterSlots.EMPTY;
			boolean picked = i == selected;
			boolean on = !empty && onKey(player, technique);
			graphics.fill(x, y, x + TILE, y + TILE, UiStyle.CHIP);
			graphics.fill(x, y, x + TILE, y + TILE, picked ? UiStyle.ROW_HOVER : i % 2 == 0 ? UiStyle.ROW : UiStyle.ROW_ALT);
			graphics.outline(x, y, TILE, TILE, picked ? ACCENT : on ? 0xA0000000 | ZFighterPower.COLOR : empty ? UiStyle.ROW_ALT : 0x60FFFFFF);
			if (empty) {
				String number = Integer.toString(i + 1);
				graphics.text(font, number, x + TILE / 2 - font.width(number) / 2, y + TILE / 2 - 4, UiStyle.HINT);
				continue;
			}
			graphics.item(new ItemStack(ZFighterPowerClient.techniqueIcon(technique)), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
			String slot = ZFighterTraining.charged(technique) ? "V" : "G";
			graphics.text(font, slot, x + TILE - font.width(slot) - 1, y + 1, on ? ACCENT : UiStyle.DIM);
			if (on) {
				graphics.fill(x + 2, y + TILE - 3, x + TILE - 2, y + TILE - 1, ACCENT);
			}
		}

		// the middle: the pick's name; under the ring, where it goes
		int technique = selected >= 0 ? slots.get(selected) : ZFighterSlots.EMPTY;
		Component name = technique != ZFighterSlots.EMPTY ? Component.translatable(ZFighterLineage.techniqueKey(player, technique)) : null;
		if (name != null) {
			UiStyle.lineCentred(graphics, font, name, cx, cy - 4, 2 * DEAD_ZONE + 20, UiStyle.TEXT);
		}
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, height - 4 - INFO_H);
		UiStyle.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		UiStyle.line(graphics, font, name != null ? name : title, ix + 8, iy + 6, INFO_W - 16, ACCENT);
		Component status;
		if (selected < 0) {
			status = Component.translatable("screen.jaymospowers.zfighter_wheel.hint");
		} else if (technique == ZFighterSlots.EMPTY) {
			status = Component.translatable("screen.jaymospowers.zfighter_wheel.empty");
		} else if (onKey(player, technique)) {
			status = Component.translatable("screen.jaymospowers.zfighter_wheel.on", ZFighterTraining.charged(technique) ? "sneak+V" : "G");
		} else {
			status = Component.translatable("screen.jaymospowers.zfighter_wheel.put", ZFighterTraining.charged(technique) ? "sneak+V" : "G");
		}
		UiStyle.line(graphics, font, status, ix + 8, iy + 17, INFO_W - 16, name != null ? UiStyle.DIM : UiStyle.HINT);
	}
}
