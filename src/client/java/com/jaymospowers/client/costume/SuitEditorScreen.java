package com.jaymospowers.client.costume;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.costume.Costume;
import com.jaymospowers.costume.CostumeDesign;
import com.jaymospowers.costume.CostumeKit;
import com.jaymospowers.costume.CostumeSets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.17.58, Andy's NOEA picks) The suit editor: opened from the Costume Table's screen (the "Suit editor" button) and
 * editing the very same design. On the left a live preview of you wearing it (drag to turn it); in the middle one button
 * for each part of the suit, click to step through its choices (right-click steps back): the cut (the gi and armour
 * patterns are here), the head piece, the cape, gloves, boots, belt, helmet and head fins; on the right the three colours
 * (primary, secondary and trim, the trim able to follow the secondary) and the chest mark, picked from ready-made
 * emblems ({@link SuitMarks}). "Done" goes back to the table, where the rest of the emblem can still be painted by hand.
 * Everything is checked again by the server when the costume is sewn.
 */
public class SuitEditorScreen extends Screen {
	private static final int W = 372, H = 236;
	private static final int ACCENT = 0xFFD0609A;
	private static final int PREVIEW_W = 92, PREVIEW_H = 168;
	private static final int PREVIEW_SCALE = 62;
	private static final float TURN_PER_PIXEL = 1.6F;
	private static final int MIDDLE_W = 150;
	private static final int BUTTON_H = 14, BUTTON_PITCH = 17;
	private static final int SWATCH_W = 12, SWATCH_H = 9, SWATCHES_PER_ROW = 8;
	/** The chest marks: each cell's size and how many fit in a row. */
	private static final int MARK_CELL = 18, MARKS_PER_ROW = 5;

	private final CostumeScreen back;
	private static float yaw = 20.0F;
	private boolean dragging;
	private Button cut, mask, cape, gloves, boots, belt, helm, fins, follow;

	SuitEditorScreen(CostumeScreen back) {
		super(Component.translatable("screen.jaymospowers.suit.title"));
		this.back = back;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	private int contentY() {
		return top() + UiStyle.HEADER;
	}

	private int previewX() {
		return left() + 8;
	}

	private int middleX() {
		return previewX() + PREVIEW_W + 8;
	}

	private int rightX() {
		return middleX() + MIDDLE_W + 8;
	}

	/** Colour picker {@code which} (0 primary, 1 secondary, 2 trim): the top of its swatches. */
	private int swatchY(int which) {
		return contentY() + 10 + which * 34;
	}

	private int marksY() {
		return contentY() + 122;
	}

	private CostumeDesign design() {
		return CostumeScreen.design();
	}

	private void set(CostumeDesign design) {
		CostumeScreen.design(design);
		labels();
	}

	@Override
	protected void init() {
		int mx = middleX();
		int y = contentY() + 2;
		this.cut = row(mx, y, 0, () -> step(Slot.CUT, 1));
		this.mask = row(mx, y, 1, () -> step(Slot.MASK, 1));
		this.cape = row(mx, y, 2, () -> step(Slot.CAPE, 1));
		this.gloves = row(mx, y, 3, () -> step(Slot.GLOVES, 1));
		this.boots = row(mx, y, 4, () -> step(Slot.BOOTS, 1));
		this.belt = row(mx, y, 5, () -> step(Slot.BELT, 1));
		this.helm = row(mx, y, 6, () -> step(Slot.HELM, 1));
		this.fins = row(mx, y, 7, () -> step(Slot.FINS, 1));
		this.follow = addRenderableWidget(Button.builder(Component.empty(), b -> {
			CostumeKit kit = design().kit();
			set(design().withKit(kit.followsSecondary() ? kit.withTrim(design().secondary()) : kit.withTrim(null)));
		}).bounds(rightX(), swatchY(2) + 2 * SWATCH_H + 2, SWATCHES_PER_ROW * SWATCH_W, 12).build());
		int by = top() + H - 20;
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.suit.done"), b -> onClose())
			.bounds(left() + W - 8 - 64, by, 64, 14).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.suit.reset"), b -> set(design().withKit(CostumeKit.NONE)))
			.bounds(left() + W - 8 - 64 - 4 - 64, by, 64, 14).build());
		labels();
	}

	private Button row(int x, int y, int index, Runnable press) {
		return addRenderableWidget(Button.builder(Component.empty(), b -> press.run()).bounds(x, y + index * BUTTON_PITCH, MIDDLE_W, BUTTON_H).build());
	}

	/** The parts the editor steps through. */
	private enum Slot {
		CUT, MASK, CAPE, GLOVES, BOOTS, BELT, HELM, FINS
	}

	private void step(Slot slot, int by) {
		CostumeDesign design = design();
		CostumeKit kit = design.kit();
		LocalPlayer player = this.minecraft.player;
		switch (slot) {
			case CUT -> set(design.withCut(CostumeSets.step(player, CostumeDesign.Cut.values(), design.cut(), by, c -> c.power)));
			case MASK -> set(design.withMask(CostumeSets.step(player, CostumeDesign.Mask.values(), design.mask(), by, m -> m.power)));
			case CAPE -> set(design.withCape(CostumeSets.step(player, CostumeDesign.Cape.values(), design.cape(), by, c -> c.power)));
			case GLOVES -> set(design.withKit(kit.withGloves(next(CostumeKit.Gloves.values(), kit.gloves(), by))));
			case BOOTS -> set(design.withKit(kit.withBoots(next(CostumeKit.Boots.values(), kit.boots(), by))));
			case BELT -> set(design.withKit(kit.withBelt(next(CostumeKit.Belt.values(), kit.belt(), by))));
			case HELM -> set(design.withKit(kit.withHelm(next(CostumeKit.Helm.values(), kit.helm(), by))));
			case FINS -> set(design.withKit(kit.withFins(next(CostumeKit.Fins.values(), kit.fins(), by))));
		}
	}

	private static <E extends Enum<E>> E next(E[] values, E now, int by) {
		return values[Math.floorMod(now.ordinal() + by, values.length)];
	}

	private void labels() {
		if (this.cut == null) {
			return;
		}
		CostumeDesign d = design();
		CostumeKit k = d.kit();
		this.cut.setMessage(label("cut", "costume.jaymospowers.cut." + d.cut().getSerializedName()));
		this.mask.setMessage(label("mask", "costume.jaymospowers.mask." + d.mask().getSerializedName()));
		this.cape.setMessage(label("cape", "costume.jaymospowers.cape." + d.cape().getSerializedName()));
		this.gloves.setMessage(label("gloves", "costume.jaymospowers.gloves." + k.gloves().getSerializedName()));
		this.boots.setMessage(label("boots", "costume.jaymospowers.boots." + k.boots().getSerializedName()));
		this.belt.setMessage(label("belt", "costume.jaymospowers.belt." + k.belt().getSerializedName()));
		this.helm.setMessage(label("helm", "costume.jaymospowers.helm." + k.helm().getSerializedName()));
		this.fins.setMessage(label("fins", "costume.jaymospowers.fins." + k.fins().getSerializedName()));
		this.follow.setMessage(Component.translatable(k.followsSecondary() ? "screen.jaymospowers.suit.trim_follows" : "screen.jaymospowers.suit.trim_own"));
	}

	private static Component label(String slot, String value) {
		return Component.translatable("screen.jaymospowers.suit." + slot, Component.translatable(value));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		// back to the table's screen, with the design as it is now
		this.minecraft.gui.setScreen(this.back);
	}

	@Override
	public void tick() {
		if (this.minecraft.player == null) {
			this.minecraft.gui.setScreen(null);
		}
	}

	// ---- input --------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int)event.x(), mouseY = (int)event.y();
		if (event.button() == 1) {
			// right-click on a stepping button steps it back
			Button[] buttons = {this.cut, this.mask, this.cape, this.gloves, this.boots, this.belt, this.helm, this.fins};
			for (int i = 0; i < buttons.length; i++) {
				if (buttons[i].isMouseOver(mouseX, mouseY)) {
					step(Slot.values()[i], -1);
					return true;
				}
			}
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (UiStyle.over(mouseX, mouseY, previewX(), contentY(), PREVIEW_W, PREVIEW_H)) {
			this.dragging = true;
			return true;
		}
		for (int which = 0; which < 3; which++) {
			DyeColor picked = swatchAt(mouseX, mouseY, which);
			if (picked != null) {
				CostumeDesign d = design();
				set(which == 0 ? d.withPrimary(picked) : which == 1 ? d.withSecondary(picked) : d.withKit(d.kit().withTrim(picked)));
				return true;
			}
		}
		int mark = markAt(mouseX, mouseY);
		if (mark >= 0) {
			set(design().withEmblem(SuitMarks.pixels(mark)));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging) {
			yaw = (yaw + (float)dx * TURN_PER_PIXEL) % 360.0F;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = false;
		return super.mouseReleased(event);
	}

	private @Nullable DyeColor swatchAt(int mouseX, int mouseY, int which) {
		int x = mouseX - rightX();
		int y = mouseY - swatchY(which);
		if (x < 0 || y < 0 || x >= SWATCHES_PER_ROW * SWATCH_W || y >= 2 * SWATCH_H) {
			return null;
		}
		return DyeColor.byId(y / SWATCH_H * SWATCHES_PER_ROW + x / SWATCH_W);
	}

	/** The chest mark under the mouse (0: the blank one), or -1. */
	private int markAt(int mouseX, int mouseY) {
		int x = mouseX - rightX();
		int y = mouseY - marksY();
		if (x < 0 || y < 0 || x >= MARKS_PER_ROW * MARK_CELL) {
			return -1;
		}
		int i = y / MARK_CELL * MARKS_PER_ROW + x / MARK_CELL;
		return i < SuitMarks.count() ? i : -1;
	}

	// ---- drawing ------------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		int x = left();
		int y = top();
		UiStyle.panel(graphics, x, y, W, H, ACCENT);
		UiStyle.header(graphics, this.font, x, y, W, new ItemStack(Costume.TABLE), this.title,
			Component.translatable("screen.jaymospowers.suit.sub"), ACCENT, 0);
		preview(graphics, player);
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.suit.hint"), middleX(), contentY() + 2 + 8 * BUTTON_PITCH,
			MIDDLE_W, UiStyle.HINT);
		colours(graphics, mouseX, mouseY);
		marks(graphics, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		Button[] buttons = {this.cut, this.mask, this.cape};
		String[] powers = {design().cut().power, design().mask().power, design().cape().power};
		for (int i = 0; i < buttons.length; i++) {
			if (powers[i] != null && buttons[i].isMouseOver(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(this.font, Component.translatable("screen.jaymospowers.costume.from_set", CostumeSets.powerName(powers[i])),
					mouseX, mouseY);
			}
		}
	}

	/** The player in the design, turned by dragging. */
	private void preview(GuiGraphicsExtractor graphics, LocalPlayer player) {
		int px = previewX(), py = contentY();
		graphics.fill(px, py, px + PREVIEW_W, py + PREVIEW_H, UiStyle.ROW_ALT);
		UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.costume.turn"), px + PREVIEW_W / 2,
			py + PREVIEW_H - 10, PREVIEW_W - 4, UiStyle.HINT);
		EntityRenderer<? super LivingEntity, ?> renderer = this.minecraft.getEntityRenderDispatcher().getRenderer((LivingEntity)player);
		EntityRenderState[] state = new EntityRenderState[1];
		CostumeClient.withPreview(design(), () -> state[0] = renderer.createRenderState(player, 1.0F));
		EntityRenderState renderState = state[0];
		renderState.shadowPieces.clear();
		renderState.outlineColor = 0;
		if (renderState instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F + yaw;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(-8.0F * (float)(Math.PI / 180.0));
		rotation.mul(camera);
		Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
		graphics.entity(renderState, PREVIEW_SCALE, translation, rotation, camera, px, py, px + PREVIEW_W, py + PREVIEW_H - 12);
	}

	/** The three colour pickers: primary, secondary and trim. */
	private void colours(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		CostumeDesign d = design();
		int rx = rightX();
		for (int which = 0; which < 3; which++) {
			DyeColor chosen = which == 0 ? d.primary() : which == 1 ? d.secondary() : d.trim();
			int sy = swatchY(which);
			Component name = Costume.colourName(chosen);
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.suit.colour." + which, name), rx, sy - 10,
				SWATCHES_PER_ROW * SWATCH_W + 4, UiStyle.DIM);
			boolean dimmed = which == 2 && d.kit().followsSecondary();
			for (DyeColor colour : DyeColor.values()) {
				int i = colour.getId();
				int cx = rx + i % SWATCHES_PER_ROW * SWATCH_W;
				int cy = sy + i / SWATCHES_PER_ROW * SWATCH_H;
				graphics.fill(cx, cy, cx + SWATCH_W - 1, cy + SWATCH_H - 1, (dimmed ? 0x80000000 : 0xFF000000) | colour.getTextureDiffuseColor());
				if (colour == chosen && !dimmed) {
					graphics.outline(cx - 1, cy - 1, SWATCH_W + 1, SWATCH_H + 1, UiStyle.TEXT);
				}
			}
			DyeColor hovered = swatchAt(mouseX, mouseY, which);
			if (hovered != null) {
				graphics.setTooltipForNextFrame(this.font, Costume.colourName(hovered), mouseX, mouseY);
			}
		}
	}

	/** The chest marks, each drawn small in the design's colours. */
	private void marks(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		CostumeDesign d = design();
		int rx = rightX(), my = marksY();
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.suit.marks"), rx, my - 10, SWATCHES_PER_ROW * SWATCH_W + 4, UiStyle.DIM);
		int hovered = markAt(mouseX, mouseY);
		String current = d.emblem();
		for (int i = 0; i < SuitMarks.count(); i++) {
			int cx = rx + i % MARKS_PER_ROW * MARK_CELL, cy = my + i / MARKS_PER_ROW * MARK_CELL;
			byte[] pixels = SuitMarks.pixels(i);
			boolean picked = CostumeDesign.pack(pixels).equals(current);
			graphics.fill(cx, cy, cx + MARK_CELL - 2, cy + MARK_CELL - 2, i == hovered ? UiStyle.ROW_HOVER : UiStyle.ROW);
			if (picked) {
				graphics.outline(cx - 1, cy - 1, MARK_CELL, MARK_CELL, UiStyle.TEXT);
			}
			if (i == 0) {
				// the blank mark: a struck-out square
				for (int k = 3; k < MARK_CELL - 5; k++) {
					graphics.fill(cx + k, cy + k, cx + k + 1, cy + k + 1, 0xFFE06060);
				}
				continue;
			}
			for (int py = 0; py < CostumeDesign.EMBLEM_SIZE; py++) {
				for (int px = 0; px < CostumeDesign.EMBLEM_SIZE; px++) {
					int colour = d.colour(pixels[py * CostumeDesign.EMBLEM_SIZE + px]);
					if (colour != 0) {
						graphics.fill(cx + px, cy + py, cx + px + 1, cy + py + 1, colour);
					}
				}
			}
		}
		if (hovered >= 0) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable(hovered == 0 ? "screen.jaymospowers.suit.mark_none"
				: "screen.jaymospowers.suit.mark." + SuitMarks.KEYS[hovered - 1]), mouseX, mouseY);
		}
	}
}
