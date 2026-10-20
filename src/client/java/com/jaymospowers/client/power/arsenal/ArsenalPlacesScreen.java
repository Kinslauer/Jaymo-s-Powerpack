package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.arsenal.ArsenalPlaces;
import com.jaymospowers.power.arsenal.ArsenalPower;
import com.jaymospowers.power.arsenal.ArsenalStanding;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
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
 * (0.13.36) Arsenal's places menu (Sneak + G, like Rig's radial): one slice for each remembered place, clockwise from
 * the top, each showing its biome and coordinates, its dimension and how far away it is. The mouse wheel or Tab flips
 * between Marks (Sneak + R) and Recent arrivals. Let go of G over a slice (or click) and a pair of standing portals
 * opens there ({@code ArsenalStanding}); the last slice, "Close my portals", shows while any are open; the middle
 * cancels. It doesn't pause.
 */
final class ArsenalPlacesScreen extends Screen {
	private static final int RADIUS = 66;
	private static final int TILE = 26;
	private static final int DEAD_ZONE = 24;
	private static final int ACCENT = 0xFFB38CFF;
	private static final int INFO_W = 230;
	private static final int INFO_H = 40;

	private final KeyMapping key;
	/** 0: Marks, 1: Recent. */
	private int tab;
	private int selected = -1;
	private boolean chosen;

	ArsenalPlacesScreen(KeyMapping key) {
		super(Component.translatable("screen.jaymospowers.arsenal_places"));
		this.key = key;
	}

	private List<ArsenalPlaces.Place> places(LocalPlayer player) {
		return tab == 0 ? ArsenalPlaces.marks(player) : ArsenalPlaces.recent(player);
	}

	private static boolean anyOpen(LocalPlayer player) {
		return player.getAttachedOrElse(ArsenalStanding.OPEN, 0) > 0;
	}

	/** The slices: the places, then "Close my portals" while any are open. */
	private int slices(LocalPlayer player) {
		return places(player).size() + (anyOpen(player) ? 1 : 0);
	}

	private static Item icon(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> Items.GRASS_BLOCK;
			case "minecraft:the_nether" -> Items.NETHERRACK;
			case "minecraft:the_end" -> Items.END_STONE;
			case "jaymospowers:aether" -> Items.FEATHER;
			default -> Items.ENDER_PEARL;
		};
	}

	private static Component dimensionName(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> Component.translatable("gui.jaymospowers.arsenal_places.dim.overworld");
			case "minecraft:the_nether" -> Component.translatable("gui.jaymospowers.arsenal_places.dim.nether");
			case "minecraft:the_end" -> Component.translatable("gui.jaymospowers.arsenal_places.dim.end");
			case "jaymospowers:aether" -> Component.translatable("gui.jaymospowers.arsenal_places.dim.aether");
			default -> Component.translatable("gui.jaymospowers.arsenal_places.dim.other");
		};
	}

	/** "The Overworld, 123 blocks away" (or just the dimension, from another one). */
	private static Component where(LocalPlayer player, ArsenalPlaces.Place place) {
		Component dimension = dimensionName(place.dimension());
		if (!player.level().dimension().identifier().toString().equals(place.dimension())) {
			return dimension;
		}
		double dx = place.x() - player.getX();
		double dy = place.y() - player.getY();
		double dz = place.z() - player.getZ();
		return Component.translatable("gui.jaymospowers.arsenal_places.away", dimension, Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
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
		// let go of the key: open what's picked (polled, in case the release went elsewhere)
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		if (bound.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(bound.getValue())) {
			choose();
		}
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isAlive() || !Powers.has(player, ArsenalPower.INSTANCE) || !ArsenalPlaces.unlocked(player)) {
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
	public boolean keyPressed(final KeyEvent event) {
		if (event.key() == InputConstants.KEY_TAB) {
			flip();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		flip();
		return true;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		choose();
		return true;
	}

	private void flip() {
		tab = 1 - tab;
		selected = -1;
		LocalPlayer player = minecraft.player;
		if (player != null) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
				0.2F, 1.2F, false);
		}
	}

	private void choose() {
		if (chosen) {
			return;
		}
		chosen = true;
		LocalPlayer player = minecraft.player;
		if (selected >= 0 && player != null && ClientPlayNetworking.canSend(ArsenalStanding.Pick.TYPE)) {
			int n = places(player).size();
			if (selected < n) {
				ClientPlayNetworking.send(new ArsenalStanding.Pick(tab, selected));
			} else if (selected == n && anyOpen(player)) {
				ClientPlayNetworking.send(new ArsenalStanding.Pick(tab, -1));
			}
		}
		onClose();
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		List<ArsenalPlaces.Place> places = places(player);
		int n = slices(player);
		double slice = n == 0 ? 360.0 : 360.0 / n;
		int cx = width / 2;
		int cy = height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;
		int before = selected;
		if (n == 0 || dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
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
			graphics.fill(cx - half, cy + y, cx + half, cy + y + 1, 0xA0100818);
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
			double t = Math.toRadians(i * slice - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int)Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			boolean picked = i == selected;
			boolean close = i >= places.size();
			graphics.fill(x, y, x + TILE, y + TILE, UiStyle.CHIP);
			graphics.fill(x, y, x + TILE, y + TILE, picked ? UiStyle.ROW_HOVER : i % 2 == 0 ? UiStyle.ROW : UiStyle.ROW_ALT);
			graphics.outline(x, y, TILE, TILE, picked ? ACCENT : close ? 0x80FF7070 : 0x60FFFFFF);
			Item item = close ? Items.BARRIER : icon(places.get(i).dimension());
			graphics.item(new ItemStack(item), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
			if (!close) {
				graphics.text(font, Integer.toString(i + 1), x + 2, y + 2, 0xFFFFFFFF);
			}
		}

		// above the ring: Marks | Recent (the wheel or Tab flips them)
		Component marks = Component.translatable("gui.jaymospowers.arsenal_places.marks");
		Component recent = Component.translatable("gui.jaymospowers.arsenal_places.recent");
		int ty = cy - outer - 14;
		int gap = 12;
		int total = font.width(marks) + gap + font.width(recent);
		int tx = cx - total / 2;
		graphics.text(font, marks, tx, ty, tab == 0 ? ACCENT : UiStyle.HINT);
		graphics.text(font, recent, tx + font.width(marks) + gap, ty, tab == 1 ? ACCENT : UiStyle.HINT);

		// the middle: the pick's number; under the ring, its name, where it is, and what it does
		Component name = null;
		Component detail;
		if (n == 0) {
			detail = Component.translatable(tab == 0 ? "gui.jaymospowers.arsenal_places.empty_marks" : "gui.jaymospowers.arsenal_places.empty_recent");
		} else if (selected < 0) {
			detail = Component.translatable("gui.jaymospowers.arsenal_places.hint");
		} else if (selected >= places.size()) {
			name = Component.translatable("gui.jaymospowers.arsenal_places.close");
			detail = Component.translatable("gui.jaymospowers.arsenal_places.close_hint", player.getAttachedOrElse(ArsenalStanding.OPEN, 0));
		} else {
			ArsenalPlaces.Place place = places.get(selected);
			name = place.label();
			detail = where(player, place);
		}
		if (selected >= 0 && selected < places.size()) {
			UiStyle.lineCentred(graphics, font, Component.literal(Integer.toString(selected + 1)), cx, cy - 4, 2 * DEAD_ZONE, UiStyle.TEXT);
		}
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, height - 4 - INFO_H);
		UiStyle.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		UiStyle.line(graphics, font, name != null ? name : title, ix + 8, iy + 6, INFO_W - 16, ACCENT);
		UiStyle.line(graphics, font, detail, ix + 8, iy + 17, INFO_W - 16, name != null ? UiStyle.DIM : UiStyle.HINT);
		if (name != null) {
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.arsenal_places.open_hint",
				ArsenalStanding.MAX_PAIRS), ix + 8, iy + 28, INFO_W - 16, UiStyle.HINT);
		}
	}
}
