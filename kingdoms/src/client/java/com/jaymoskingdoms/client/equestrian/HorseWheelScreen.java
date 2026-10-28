package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.client.Ui;
import com.jaymoskingdoms.equestrian.Bond;
import com.jaymoskingdoms.equestrian.Bonding;
import com.jaymoskingdoms.equestrian.Herd;
import com.jaymoskingdoms.equestrian.Herding;
import com.jaymoskingdoms.equestrian.Tackroom;
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
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.305, ideas from Icy's Better Horses) The horse command wheel: hold the whistle key (N), point at a slice and let
 * go (or click). Orders go to the horse under the crosshair, or the one you ride: let it be, follow, stay, wander here,
 * make this its home, go home. Ride on (in the saddle) keeps it going without holding forward. Horses opens the roster
 * of every horse you own; Handbook opens the horse handbook. Laid out like the Z Fighter technique wheel; it doesn't
 * pause, and the world stays in view.
 */
final class HorseWheelScreen extends Screen {
	private static final int RADIUS = 66;
	private static final int TILE = 26;
	private static final int DEAD_ZONE = 24;
	private static final int ACCENT = Ui.GOLD;
	private static final int INFO_W = 210;
	private static final int INFO_H = 30;

	/** The slices, clockwise from the top. */
	private enum Slice {
		LET_BE("let_be", Herd.Order.NONE.ordinal(), Items.HAY_BLOCK),
		FOLLOW("follow", Herd.Order.FOLLOW.ordinal(), Items.LEAD),
		STAY("stay", Herd.Order.STAY.ordinal(), Items.OAK_FENCE),
		WANDER("wander", Herd.Order.WANDER.ordinal(), Items.WHEAT),
		SET_HOME("set_home", Herding.Command.SET_HOME, Items.STRAW_BED),
		GO_HOME("go_home", Herd.Order.HOME.ordinal(), Items.COMPASS),
		RIDE_ON("ride_on", 0, Items.SADDLE),
		HORSES("horses", 0, Items.FILLED_MAP),
		HANDBOOK("handbook", 0, null);

		final String key;
		final int order;
		final net.minecraft.world.item.@Nullable Item icon;

		Slice(String key, int order, net.minecraft.world.item.@Nullable Item icon) {
			this.key = key;
			this.order = order;
			this.icon = icon;
		}

		boolean command() {
			return this.ordinal() <= GO_HOME.ordinal();
		}

		ItemStack icon() {
			return new ItemStack(this.icon != null ? this.icon : Tackroom.HANDBOOK);
		}
	}

	private static final int SLICE = 360 / Slice.values().length;

	private final KeyMapping key;
	private final @Nullable AbstractHorse horse;
	private int selected = -1;
	private boolean chosen;

	HorseWheelScreen(KeyMapping key, @Nullable AbstractHorse horse) {
		super(Component.translatable("screen.jaymoskingdoms.horse_wheel"));
		this.key = key;
		this.horse = horse;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// no blur: the world stays in view
	}

	@Override
	public void tick() {
		// let go of the key: take what's picked (polled, in case the release went elsewhere)
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(this.key);
		if (bound.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(bound.getValue())) {
			choose();
		}
		LocalPlayer player = this.minecraft.player;
		if (player == null || !player.isAlive()) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (this.key.matches(event)) {
			choose();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		choose();
		return true;
	}

	private boolean usable(Slice slice) {
		LocalPlayer player = this.minecraft.player;
		if (slice.command()) {
			return this.horse != null && this.horse.isAlive();
		}
		return slice != Slice.RIDE_ON || player != null && EquestrianClient.ridden(player) != null;
	}

	private void choose() {
		if (this.chosen) {
			return;
		}
		this.chosen = true;
		Slice[] slices = Slice.values();
		Slice slice = this.selected >= 0 ? slices[this.selected] : null;
		if (slice == null || !usable(slice)) {
			onClose();
			return;
		}
		switch (slice) {
			case RIDE_ON -> {
				EquestrianClient.toggleCruise();
				onClose();
			}
			case HORSES -> this.minecraft.gui.setScreen(new RosterScreen());
			case HANDBOOK -> this.minecraft.gui.setScreen(new HandbookScreen());
			default -> {
				ClientPlayNetworking.send(new Herding.Command(this.horse.getId(), slice.order));
				onClose();
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		Slice[] slices = Slice.values();
		int cx = this.width / 2;
		int cy = this.height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;
		int before = this.selected;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			this.selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			this.selected = Math.floorMod((int)Math.round(angle / SLICE), slices.length);
		}
		if (this.selected != before && this.selected >= 0) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
				0.15F, 1.6F + this.selected * 0.04F, false);
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
			graphics.fill(x, y, x + 1, y + 1, Ui.ROW);
		}
		if (this.selected >= 0) {
			double t = Math.atan2(dy, dx);
			for (int r = DEAD_ZONE; r < RADIUS - TILE / 2 - 6; r += 4) {
				int x = cx + (int)Math.round(Math.cos(t) * r);
				int y = cy + (int)Math.round(Math.sin(t) * r);
				graphics.fill(x, y, x + 2, y + 2, ACCENT);
			}
		}

		Herd herd = this.horse == null ? null : this.horse.getAttached(Herding.HERD);
		int current = herd == null ? -2 : herd.order();
		for (int i = 0; i < slices.length; i++) {
			Slice slice = slices[i];
			double t = Math.toRadians(i * SLICE - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int)Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			boolean picked = i == this.selected;
			boolean usable = usable(slice);
			boolean on = slice.command() && slice != Slice.SET_HOME && slice.order == current || slice == Slice.RIDE_ON && EquestrianClient.cruising();
			graphics.fill(x, y, x + TILE, y + TILE, Ui.CHIP);
			graphics.fill(x, y, x + TILE, y + TILE, picked ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW : Ui.ROW_ALT);
			graphics.outline(x, y, TILE, TILE, picked ? ACCENT : on ? 0xA0FFC21A : usable ? 0x60FFFFFF : Ui.ROW_ALT);
			graphics.item(slice.icon(), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
			if (!usable) {
				graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xA0101018);
			}
			if (on) {
				graphics.fill(x + 2, y + TILE - 3, x + TILE - 2, y + TILE - 1, ACCENT);
			}
		}

		// the middle: the horse it's for; under the ring, the pick and what it does
		Component who = this.horse != null ? Bonding.name(this.horse) : Component.translatable("screen.jaymoskingdoms.horse_wheel.no_horse");
		int whoWidth = Math.min(this.font.width(who), 2 * DEAD_ZONE + 20);
		Ui.line(graphics, this.font, who, cx - whoWidth / 2, cy - 4, 2 * DEAD_ZONE + 20, this.horse != null ? Ui.TEXT : Ui.HINT);
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, this.height - 4 - INFO_H);
		Ui.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		Slice slice = this.selected >= 0 ? slices[this.selected] : null;
		Component name = slice == null ? this.title : Component.translatable("wheel.jaymoskingdoms.horse." + slice.key);
		Ui.line(graphics, this.font, name, ix + 8, iy + 6, INFO_W - 16, ACCENT);
		Component status;
		if (slice == null) {
			status = Component.translatable("screen.jaymoskingdoms.horse_wheel.hint");
		} else if (!usable(slice)) {
			status = Component.translatable(slice == Slice.RIDE_ON ? "wheel.jaymoskingdoms.horse.not_riding" : "wheel.jaymoskingdoms.horse.no_horse");
		} else if (slice.command() && this.horse != null && !trusted(player)) {
			status = Component.translatable("wheel.jaymoskingdoms.horse.wary", (int)Herding.ORDER_TRUST);
		} else {
			status = Component.translatable("wheel.jaymoskingdoms.horse." + slice.key + ".info");
		}
		Ui.line(graphics, this.font, status, ix + 8, iy + 17, INFO_W - 16, slice != null ? Ui.DIM : Ui.HINT);
	}

	private boolean trusted(LocalPlayer player) {
		Bond b = this.horse == null ? null : this.horse.getAttached(Bonding.BOND);
		return b != null && b.trustOf(player.getUUID()) >= Herding.ORDER_TRUST;
	}
}
