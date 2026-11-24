package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BeastForms;
import com.jaymoskingdoms.Druids;
import com.jaymoskingdoms.ShapeWheel;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Druid's wheel of shapes: hold the class key, point at a slice and let go (or click) to take that shape; right click stars
 * a favourite, so it sits at the top; let go with sneak held to call a wild beast of that kind instead (Pack Leader). Scroll for
 * more than {@link #PER_PAGE}. Laid out like the horse wheel; it doesn't pause, and the world stays in view.
 */
final class ShapeWheelScreen extends Screen {
	private static final int RADIUS = 70, TILE = 26, DEAD_ZONE = 24, PER_PAGE = 12, INFO_W = 230, INFO_H = 30;
	private static final int ACCENT = 0xFF7BE87B, STAR = Ui.GOLD;

	private final KeyMapping key;
	private List<ShapeWheel.Slice> slices;
	private int page, selected = -1;
	private boolean chosen;

	ShapeWheelScreen(KeyMapping key, List<ShapeWheel.Slice> slices) {
		super(Component.translatable("screen.jaymoskingdoms.shape_wheel"));
		this.key = key;
		this.slices = slices;
	}

	/** The server's fresher list (a star was toggled). */
	void refresh(List<ShapeWheel.Slice> slices) {
		this.slices = slices;
		this.page = Math.min(this.page, Math.max(0, (slices.size() - 1) / PER_PAGE));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// no blur: the world stays in view
	}

	private List<ShapeWheel.Slice> shown() {
		int from = Math.min(this.page * PER_PAGE, this.slices.size());
		return this.slices.subList(from, Math.min(this.slices.size(), from + PER_PAGE));
	}

	@Override
	public void tick() {
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
		if (event.button() == 1) {
			// right click: star it (the wheel stays open)
			if (this.selected >= 0 && this.selected < shown().size()) {
				ClientPlayNetworking.send(new ShapeWheel.Pick(shown().get(this.selected).form(), ShapeWheel.STAR));
			}
			return true;
		}
		choose();
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		int pages = Math.max(1, (this.slices.size() + PER_PAGE - 1) / PER_PAGE);
		this.page = Math.floorMod(this.page + (dy < 0 ? 1 : -1), pages);
		return true;
	}

	private void choose() {
		if (this.chosen) {
			return;
		}
		this.chosen = true;
		if (this.selected >= 0 && this.selected < shown().size()) {
			boolean sneak = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
			ClientPlayNetworking.send(new ShapeWheel.Pick(shown().get(this.selected).form(), sneak ? ShapeWheel.CALL : ShapeWheel.TAKE));
		}
		onClose();
	}

	private static ItemStack icon(String form) {
		var type = BeastForms.typeOf(form);
		if (type != null) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			Item egg = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg")).orElse(Items.LEAD);
			return new ItemStack(egg);
		}
		return new ItemStack(switch (form) {
			case Druids.WOLF -> Items.BONE;
			case Druids.BEAR -> Items.HONEYCOMB;
			case Druids.HAWK -> Items.FEATHER;
			case Druids.FISH -> Items.COD;
			case "fox" -> Items.SWEET_BERRIES;
			case "stag" -> Items.LEATHER;
			case "treant" -> Items.OAK_SAPLING;
			default -> Items.WIND_CHARGE;
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		List<ShapeWheel.Slice> shown = shown();
		int n = Math.max(1, shown.size());
		double step = 360.0 / n;
		int cx = this.width / 2, cy = this.height / 2, dx = mouseX - cx, dy = mouseY - cy;
		int before = this.selected;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE || shown.isEmpty()) {
			this.selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			this.selected = Math.floorMod((int) Math.round(angle / step), n);
		}
		if (this.selected != before && this.selected >= 0) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
				0.15F, 1.6F + this.selected * 0.04F, false);
		}
		int outer = RADIUS + TILE / 2 + 6;
		Ui.wheel(graphics, cx, cy, RADIUS, TILE);
		if (this.selected >= 0) {
			Ui.wheelPointer(graphics, cx, cy, RADIUS, TILE, Math.atan2(dy, dx), ACCENT);
		}
		for (int i = 0; i < shown.size(); i++) {
			ShapeWheel.Slice slice = shown.get(i);
			double t = Math.toRadians(i * step - 90.0);
			int x = cx + (int) Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int) Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			boolean picked = i == this.selected;
			Ui.wheelTile(graphics, x, y, TILE, i, picked, picked ? ACCENT : slice.star() ? STAR : 0x60FFFFFF);
			graphics.item(icon(slice.form()), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
			if (slice.locked()) {
				graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xA0101018);
			}
			if (slice.star()) {
				graphics.fill(x + TILE - 6, y + 2, x + TILE - 2, y + 6, STAR);
			}
		}
		Component title = this.slices.isEmpty() ? Component.translatable("message.jaymoskingdoms.druid.none") : this.title;
		Ui.lineCentred(graphics, this.font, title, cx, cy - 4, 2 * DEAD_ZONE + 20, Ui.DIM);
		int ix = cx - INFO_W / 2, iy = Math.min(cy + outer + 4, this.height - 4 - INFO_H);
		Ui.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		ShapeWheel.Slice slice = this.selected >= 0 ? shown.get(this.selected) : null;
		Ui.line(graphics, this.font, slice == null ? this.title : Druids.formName(slice.form()), ix + 8, iy + 6, INFO_W - 16, ACCENT);
		Component status = slice == null ? Component.translatable("screen.jaymoskingdoms.shape_wheel.hint")
			: slice.locked() ? Component.translatable("message.jaymoskingdoms.druid.too_great")
			: BeastForms.isMob(slice.form()) ? Component.translatable("screen.jaymoskingdoms.shape_wheel.size." + BeastForms.size(slice.form()).name().toLowerCase())
			: Component.translatable("screen.jaymoskingdoms.shape_wheel.old");
		Ui.line(graphics, this.font, status, ix + 8, iy + 17, INFO_W - 16, slice != null ? Ui.DIM : Ui.HINT);
	}
}
