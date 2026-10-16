package com.jaymospowers.client.rig;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigHelm;
import com.jaymospowers.rig.RigNet;
import com.jaymospowers.rig.RigScan;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
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
 * (0.11.110) The Rig's radial menu (Andy: "make j menu radial"): hold Sneak + J, point the mouse toward an entry, let go
 * (or click). An active (Hover, Palm Blast, Launcher, Unibeam, Scan, Suit) becomes what a tap of J uses, and is used
 * now; the Helm's switches (Faceplate, Night Vision) just flip. Eight tiles clockwise from the top, each with its icon,
 * cooldown, and the Mind level it waits for; a lit strip on what's on. Like the symbiote's menu, it doesn't pause.
 */
final class RigMenuScreen extends Screen {
	/** Ring radius, tile size, and the dead zone in the middle (no pick). */
	private static final int RADIUS = 62;
	private static final int TILE = 26;
	private static final int DEAD_ZONE = 24;
	private static final int ACCENT = 0xFF5FE6FF;
	/** The panel under the ring that names the pick and says what's stopping it. */
	private static final int INFO_W = 200;
	private static final int INFO_H = 30;

	/** The entries, clockwise from the top. */
	private static final int[] ACTIONS = {RigNet.HOVER, RigNet.PALM, RigNet.LAUNCHER, RigNet.UNIBEAM, RigNet.SCAN, RigNet.CALL, RigNet.FACEPLATE,
		RigNet.NIGHT};
	private static final String[] NAMES = {"hover", "palm", "launcher", "unibeam", "scan", "call", "faceplate", "night"};
	private static final int[] LEVEL = {1, 1, 1, Rig.UNIBEAM_LEVEL, Rig.SCAN_LEVEL, Rig.CALL_LEVEL, 1, 1};
	/** Cooldowns' full lengths (ticks), for the shading; 0 for none. */
	private static final int[] COOLDOWN = {0, RigHud.PALM_COOLDOWN, RigHud.LAUNCHER_COOLDOWN, RigHud.UNIBEAM_COOLDOWN, RigScan.SCAN_COOLDOWN, 0, 0, 0};
	private static final int SLICE = 360 / 8;

	private int selected = -1;
	private boolean chosen;

	RigMenuScreen() {
		super(Component.translatable("screen.jaymospowers.rig_menu"));
	}

	private static Item icon(int i) {
		return switch (i) {
			case 0 -> Items.FEATHER;
			case 1 -> Items.GLOWSTONE_DUST;
			case 2 -> Items.FIREWORK_ROCKET;
			case 3 -> Items.BEACON;
			case 4 -> Items.SPYGLASS;
			case 5 -> Items.ARMOR_STAND;
			case 6 -> Rig.HELM != null ? Rig.HELM : Items.IRON_HELMET;
			default -> Items.ENDER_EYE;
		};
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
		// let go of the key: use what's picked (polled, in case the release went elsewhere)
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(RigClient.rigKey());
		if (key.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(key.getValue())) {
			choose();
		}
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isAlive() || !Rig.wearsAny(player)) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(final KeyEvent event) {
		if (RigClient.rigKey().matches(event)) {
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
		if (selected >= 0 && player != null && blocker(player, selected) == null) {
			RigClient.pick(ACTIONS[selected]);
		}
		onClose();
	}

	/** Why this entry can't be used now, or null if it can. */
	private static Component blocker(LocalPlayer player, int i) {
		int level = Rig.level(player);
		if (level < LEVEL[i]) {
			return Component.translatable("gui.jaymospowers.rig_menu.level", LEVEL[i]);
		}
		int action = ACTIONS[i];
		boolean helm = Rig.wears(player, Rig.Piece.HELM);
		boolean core = !Rig.core(player).isEmpty();
		if (action == RigNet.FACEPLATE && !helm) {
			return Component.translatable("gui.jaymospowers.rig_menu.no_helm");
		}
		if ((action == RigNet.NIGHT || action == RigNet.SCAN) && (!helm || !core)) {
			return Component.translatable("gui.jaymospowers.rig_menu.no_helm_core");
		}
		if (action == RigNet.CALL && !core) {
			return Component.translatable("gui.jaymospowers.rig_menu.no_core");
		}
		if ((action == RigNet.HOVER || action == RigNet.PALM || action == RigNet.LAUNCHER || action == RigNet.UNIBEAM) && !Rig.fullSet(player)) {
			return Component.translatable("gui.jaymospowers.rig_menu.incomplete");
		}
		return null;
	}

	/** Ticks left on this entry's cooldown. */
	private static int left(int i) {
		RigNet.Status status = RigClient.status;
		return switch (ACTIONS[i]) {
			case RigNet.PALM -> status.palmCooldown();
			case RigNet.LAUNCHER -> status.launcherCooldown();
			case RigNet.UNIBEAM -> status.unibeamCooldown();
			case RigNet.SCAN -> status.scanCooldown();
			default -> 0;
		};
	}

	/** Whether this entry is switched on (hovering, the faceplate up, night vision on). */
	private static boolean on(LocalPlayer player, int i) {
		return switch (ACTIONS[i]) {
			case RigNet.HOVER -> RigClient.status.hovering();
			case RigNet.FACEPLATE -> RigHelm.faceUp(player);
			case RigNet.NIGHT -> RigHelm.night(player);
			default -> false;
		};
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		int cx = width / 2;
		int cy = height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;
		int before = selected;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			selected = Math.floorMod((int)Math.round(angle / SLICE), ACTIONS.length);
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

		for (int i = 0; i < ACTIONS.length; i++) {
			double t = Math.toRadians(i * SLICE - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int)Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			int left = left(i);
			boolean blocked = blocker(player, i) != null;
			boolean picked = i == selected;
			boolean jKey = ACTIONS[i] == RigClient.selected;
			graphics.fill(x, y, x + TILE, y + TILE, UiStyle.CHIP);
			graphics.fill(x, y, x + TILE, y + TILE, picked ? UiStyle.ROW_HOVER : i % 2 == 0 ? UiStyle.ROW : UiStyle.ROW_ALT);
			graphics.outline(x, y, TILE, TILE, picked ? ACCENT : jKey ? 0xA05FE6FF : left <= 0 && !blocked ? 0x60FFFFFF : UiStyle.ROW_ALT);
			graphics.item(new ItemStack(icon(i)), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
			if (left > 0 && COOLDOWN[i] > 0) {
				int shade = (int)Math.ceil((TILE - 2) * Math.min(1.0, left / (double)COOLDOWN[i]));
				graphics.fill(x + 1, y + 1 + (TILE - 2) - shade, x + TILE - 1, y + TILE - 1, 0xB0000000);
				String seconds = left >= 200 ? Integer.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
				graphics.text(font, seconds, x + TILE / 2 - font.width(seconds) / 2, y + TILE / 2 - 4, 0xFFFFFFFF);
			}
			if (Rig.level(player) < LEVEL[i]) {
				// not yet: nearly black, with the Mind level it waits for
				graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xD8000000);
				String need = "Lv" + LEVEL[i];
				graphics.text(font, need, x + TILE / 2 - font.width(need) / 2, y + TILE / 2 - 4, 0xFF70A0B0);
			} else if (blocked) {
				graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0x90000000);
			}
			if (on(player, i)) {
				graphics.fill(x + 2, y + TILE - 3, x + TILE - 2, y + TILE - 1, ACCENT);
			}
		}

		// the middle: the pick's name; under the ring, what it does or what's stopping it
		Component name = selected >= 0 ? Component.translatable("gui.jaymospowers.rig_menu." + NAMES[selected]) : null;
		if (name != null) {
			UiStyle.lineCentred(graphics, font, name, cx, cy - 4, 2 * DEAD_ZONE + 20, UiStyle.TEXT);
		}
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, height - 4 - INFO_H);
		UiStyle.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		UiStyle.line(graphics, font, name != null ? name : title, ix + 8, iy + 6, INFO_W - 16, ACCENT);
		UiStyle.line(graphics, font, status(player), ix + 8, iy + 17, INFO_W - 16, name != null ? UiStyle.DIM : UiStyle.HINT);
	}

	private Component status(LocalPlayer player) {
		if (selected < 0) {
			return Component.translatable("gui.jaymospowers.rig_menu.hint");
		}
		Component blocker = blocker(player, selected);
		if (blocker != null) {
			return blocker;
		}
		int left = left(selected);
		if (left > 0) {
			return Component.translatable("gui.jaymospowers.rig_menu.cooling", String.format(Locale.ROOT, "%.1f", left / 20.0));
		}
		if (ACTIONS[selected] == RigNet.FACEPLATE || ACTIONS[selected] == RigNet.NIGHT || ACTIONS[selected] == RigNet.HOVER) {
			return Component.translatable(on(player, selected) ? "gui.jaymospowers.rig_menu.on" : "gui.jaymospowers.rig_menu.off");
		}
		return Component.translatable("gui.jaymospowers.rig_menu.ready");
	}
}
