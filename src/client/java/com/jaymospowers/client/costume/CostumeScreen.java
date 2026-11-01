package com.jaymospowers.client.costume;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.costume.Costume;
import com.jaymospowers.costume.CostumeDesign;
import com.jaymospowers.costume.CostumeNet;
import com.jaymospowers.costume.CostumeSets;
import com.jaymospowers.costume.CostumeTailoring;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.11.66) The Costume Table's designer, in the Powers screens' look ({@link UiStyle}): on the left a preview of you
 * wearing the design (drag it to turn it), in the middle the name, the cut, mask and cape (click to cycle, right-click
 * back) and the two colours, on the right the 16x16 emblem painter (left button paints with the picked palette entry,
 * right button rubs out), and along the bottom what sewing costs against what you carry, "Sew" and "Take off". The
 * design lives here until it is sewn; the server checks it and the materials again.
 * <p>
 * (0.11.78) Pieces from a power's set ({@link CostumeSets}) are offered only while you have that power. A tailor's
 * order ({@link #tailored}) opens the same designer away from any table: the cost is the tailor's emeralds, not
 * materials, and there is no taking off.
 */
public class CostumeScreen extends Screen {
	private static final int W = 372, H = 236;
	private static final int ACCENT = 0xFFD0609A;
	private static final int PREVIEW_W = 92, PREVIEW_H = 150;
	/** The preview's size (the player's scale in it) and how far a pixel of drag turns it. */
	private static final int PREVIEW_SCALE = 58;
	private static final float TURN_PER_PIXEL = 1.6F;
	private static final int MIDDLE_W = 140;
	private static final int SWATCH_W = 14, SWATCH_H = 10, SWATCHES_PER_ROW = 8;
	/** One emblem pixel's size on screen. */
	private static final int CELL = 6;
	private static final int CHIP = 14;
	private static final int OK = 0xFF7CD67C, SHORT = 0xFFE06060;

	/** The table, or null for a tailor's order. */
	private final @Nullable BlockPos pos;
	/** A tailor's price in emeralds, or -1 at a table. */
	private final int price;
	/** Kept between openings (so a closed screen doesn't lose a half-done design). */
	private static CostumeDesign design = CostumeDesign.DEFAULT;
	private static float yaw = 20.0F;
	private static int brush = CostumeDesign.PRIMARY;
	private byte[] pixels;
	/** What the mouse is doing: 0 nothing, 1 turning the preview, 2 painting, 3 rubbing out. */
	private int dragging;

	private EditBox name;
	private Button cut, mask, cape, sew, takeOff, maskToggle;

	public CostumeScreen(BlockPos pos) {
		this(pos, -1);
	}

	private CostumeScreen(@Nullable BlockPos pos, int price) {
		super(Component.translatable(price >= 0 ? "screen.jaymospowers.costume.tailor_title" : "screen.jaymospowers.costume.title"));
		this.pos = pos;
		this.price = price;
	}

	/** (0.11.78) The designer for a tailor's order, at {@code price} emeralds. */
	static CostumeScreen tailored(int price) {
		return new CostumeScreen(null, Math.max(0, price));
	}

	private boolean tailoring() {
		return this.price >= 0;
	}

	/** (0.17.60) The design being made, for the suit editor ({@link SuitEditorScreen}) to edit in place. */
	static CostumeDesign design() {
		return design;
	}

	static void design(CostumeDesign edited) {
		design = edited;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	private int previewX() {
		return left() + 8;
	}

	private int contentY() {
		return top() + UiStyle.HEADER;
	}

	private int middleX() {
		return left() + 8 + PREVIEW_W + 8;
	}

	private int emblemX() {
		return middleX() + MIDDLE_W + 8;
	}

	private int swatchY(boolean secondary) {
		return contentY() + (secondary ? 128 : 92);
	}

	private int gridY() {
		return contentY() + 10;
	}

	private int paletteY() {
		return gridY() + CostumeDesign.EMBLEM_SIZE * CELL + 4;
	}

	private int bottomY() {
		return contentY() + PREVIEW_H + 6;
	}

	@Override
	protected void init() {
		if (this.minecraft.player != null) {
			// a piece of a power's set you no longer have goes back to the plain one
			design = CostumeSets.fit(this.minecraft.player, design);
		}
		this.pixels = design.pixels();
		int mx = middleX();
		int y = contentY();
		this.name = addRenderableWidget(new EditBox(this.font, mx, y + 10, MIDDLE_W, 14, Component.translatable("screen.jaymospowers.costume.name")));
		this.name.setMaxLength(CostumeDesign.MAX_NAME);
		this.name.setValue(design.name());
		this.name.setHint(Component.translatable("screen.jaymospowers.costume.name_hint"));
		this.name.setResponder(s -> design = design.withName(s));
		this.cut = addRenderableWidget(Button.builder(Component.empty(), b -> cycleCut(1)).bounds(mx, y + 30, MIDDLE_W, 14).build());
		this.mask = addRenderableWidget(Button.builder(Component.empty(), b -> cycleMask(1)).bounds(mx, y + 46, MIDDLE_W, 14).build());
		this.cape = addRenderableWidget(Button.builder(Component.empty(), b -> cycleCape(1)).bounds(mx, y + 62, MIDDLE_W, 14).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.costume.clear"), b -> {
			this.pixels = new byte[CostumeDesign.EMBLEM_PIXELS];
			design = design.withEmblem(this.pixels);
		}).bounds(emblemX(), paletteY() + CHIP + 14, CostumeDesign.EMBLEM_SIZE * CELL, 14).build());
		// (0.17.60) the suit editor: the gi and armour patterns, gloves, boots, belt, helmet, fins, three colours, chest marks
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.costume.suit_editor"),
			b -> this.minecraft.gui.setScreen(new SuitEditorScreen(this))).bounds(left() + W - 8 - 84, top() + 7, 84, 14).build());
		int by = top() + H - 20;
		this.sew = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.costume.sew"), b -> {
			if (this.pos != null) {
				ClientPlayNetworking.send(new CostumeNet.Sew(this.pos, design));
			} else {
				ClientPlayNetworking.send(new CostumeTailoring.Sew(design));
				onClose();
			}
		}).bounds(left() + W - 8 - 64, by, 64, 14).build());
		this.takeOff = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.costume.take_off"), b -> {
			if (this.pos != null) {
				ClientPlayNetworking.send(new CostumeNet.TakeOff(this.pos));
			}
		}).bounds(left() + W - 8 - 64 - 4 - 64, by, 64, 14).build());
		this.takeOff.visible = !tailoring();
		// (0.12.3) the worn costume's mask off / on (the mask key's, here too)
		this.maskToggle = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.costume.mask_off"),
			b -> CostumeClient.toggleMask()).bounds(left() + W - 8 - 64 - 4 - 64 - 4 - 64, by, 64, 14).build());
		labels();
	}

	private void labels() {
		this.cut.setMessage(Component.translatable("screen.jaymospowers.costume.cut",
			Component.translatable("costume.jaymospowers.cut." + design.cut().getSerializedName())));
		this.mask.setMessage(Component.translatable("screen.jaymospowers.costume.mask",
			Component.translatable("costume.jaymospowers.mask." + design.mask().getSerializedName())));
		this.cape.setMessage(Component.translatable("screen.jaymospowers.costume.cape",
			Component.translatable("costume.jaymospowers.cape." + design.cape().getSerializedName())));
	}

	private void cycleCut(int by) {
		design = design.withCut(CostumeSets.step(this.minecraft.player, CostumeDesign.Cut.values(), design.cut(), by, c -> c.power));
		labels();
	}

	private void cycleMask(int by) {
		design = design.withMask(CostumeSets.step(this.minecraft.player, CostumeDesign.Mask.values(), design.mask(), by, m -> m.power));
		labels();
	}

	private void cycleCape(int by) {
		design = design.withCape(CostumeSets.step(this.minecraft.player, CostumeDesign.Cape.values(), design.cape(), by, c -> c.power));
		labels();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		LocalPlayer player = this.minecraft.player;
		if (player == null || this.pos != null && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(this.pos)) > 64.0) {
			onClose();
		}
	}

	// ---- input --------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int)event.x(), mouseY = (int)event.y();
		boolean right = event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
		// right-click on a cycling button steps it back
		if (right && this.cut.isMouseOver(mouseX, mouseY)) {
			cycleCut(-1);
			return true;
		}
		if (right && this.mask.isMouseOver(mouseX, mouseY)) {
			cycleMask(-1);
			return true;
		}
		if (right && this.cape.isMouseOver(mouseX, mouseY)) {
			cycleCape(-1);
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (UiStyle.over(mouseX, mouseY, previewX(), contentY(), PREVIEW_W, PREVIEW_H)) {
			this.dragging = 1;
			return true;
		}
		if (paint(mouseX, mouseY, right)) {
			this.dragging = right ? 3 : 2;
			return true;
		}
		for (int i = 0; i < CostumeDesign.EMBLEM_COLOURS; i++) {
			if (UiStyle.over(mouseX, mouseY, emblemX() + i * (CHIP + 2), paletteY(), CHIP, CHIP)) {
				brush = i;
				return true;
			}
		}
		for (int s = 0; s < 2; s++) {
			DyeColor picked = swatchAt(mouseX, mouseY, s == 1);
			if (picked != null) {
				design = s == 1 ? design.withSecondary(picked) : design.withPrimary(picked);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging == 1) {
			yaw = (yaw + (float)dx * TURN_PER_PIXEL) % 360.0F;
			return true;
		}
		if (this.dragging >= 2) {
			paint((int)event.x(), (int)event.y(), this.dragging == 3);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = 0;
		return super.mouseReleased(event);
	}

	/** Paints (or rubs out) the emblem pixel under the mouse; false if it isn't over the grid. */
	private boolean paint(int mouseX, int mouseY, boolean erase) {
		int gx = Math.floorDiv(mouseX - emblemX(), CELL);
		int gy = Math.floorDiv(mouseY - gridY(), CELL);
		if (gx < 0 || gy < 0 || gx >= CostumeDesign.EMBLEM_SIZE || gy >= CostumeDesign.EMBLEM_SIZE) {
			return false;
		}
		byte value = (byte)(erase ? CostumeDesign.EMPTY : brush);
		int i = gy * CostumeDesign.EMBLEM_SIZE + gx;
		if (this.pixels[i] != value) {
			this.pixels[i] = value;
			design = design.withEmblem(this.pixels);
		}
		return true;
	}

	private DyeColor swatchAt(int mouseX, int mouseY, boolean secondary) {
		int x = mouseX - middleX();
		int y = mouseY - swatchY(secondary);
		if (x < 0 || y < 0 || x >= SWATCHES_PER_ROW * SWATCH_W || y >= 2 * SWATCH_H) {
			return null;
		}
		return DyeColor.byId(y / SWATCH_H * SWATCHES_PER_ROW + x / SWATCH_W);
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
			Component.translatable(tailoring() ? "screen.jaymospowers.costume.tailor_sub" : "screen.jaymospowers.costume.sub"), ACCENT, 0);
		preview(graphics, player);
		middle(graphics, mouseX, mouseY);
		emblem(graphics, mouseX, mouseY);
		cost(graphics, player);
		this.takeOff.active = !tailoring() && Costume.worn(player) != null;
		CostumeDesign wornNow = Costume.worn(player);
		this.maskToggle.active = wornNow != null && wornNow.mask() != CostumeDesign.Mask.NONE;
		this.maskToggle.setMessage(Component.translatable(Costume.unmasked(player) ? "screen.jaymospowers.costume.mask_on"
			: "screen.jaymospowers.costume.mask_off"));
		super.extractRenderState(graphics, mouseX, mouseY, a);
		pieceTooltip(graphics, this.cut, design.cut().power, mouseX, mouseY);
		pieceTooltip(graphics, this.mask, design.mask().power, mouseX, mouseY);
		pieceTooltip(graphics, this.cape, design.cape().power, mouseX, mouseY);
	}

	/** (0.11.78) Over a piece of a power's set: whose it is. */
	private void pieceTooltip(GuiGraphicsExtractor graphics, Button button, @Nullable String power, int mouseX, int mouseY) {
		if (power != null && button.isMouseOver(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("screen.jaymospowers.costume.from_set", CostumeSets.powerName(power)),
				mouseX, mouseY);
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
		CostumeClient.withPreview(design, () -> state[0] = renderer.createRenderState(player, 1.0F));
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

	/** The name's label and the two colour pickers (the buttons and the name box draw themselves). */
	private void middle(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int mx = middleX();
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.costume.name"), mx, contentY(), MIDDLE_W, UiStyle.DIM);
		for (int s = 0; s < 2; s++) {
			boolean secondary = s == 1;
			DyeColor chosen = secondary ? design.secondary() : design.primary();
			int sy = swatchY(secondary);
			UiStyle.line(graphics, this.font, Component.translatable(secondary ? "screen.jaymospowers.costume.secondary" : "screen.jaymospowers.costume.primary",
				Costume.colourName(chosen)), mx, sy - 10, MIDDLE_W, UiStyle.DIM);
			for (DyeColor colour : DyeColor.values()) {
				int i = colour.getId();
				int cx = mx + i % SWATCHES_PER_ROW * SWATCH_W;
				int cy = sy + i / SWATCHES_PER_ROW * SWATCH_H;
				graphics.fill(cx, cy, cx + SWATCH_W - 1, cy + SWATCH_H - 1, 0xFF000000 | colour.getTextureDiffuseColor());
				if (colour == chosen) {
					graphics.outline(cx - 1, cy - 1, SWATCH_W + 1, SWATCH_H + 1, UiStyle.TEXT);
				}
			}
			DyeColor hovered = swatchAt(mouseX, mouseY, secondary);
			if (hovered != null) {
				graphics.setTooltipForNextFrame(this.font, Costume.colourName(hovered), mouseX, mouseY);
			}
		}
	}

	/** The emblem grid and its palette. */
	private void emblem(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int ex = emblemX(), gy = gridY();
		int size = CostumeDesign.EMBLEM_SIZE * CELL;
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.costume.emblem"), ex, contentY(), size, UiStyle.DIM);
		for (int py = 0; py < CostumeDesign.EMBLEM_SIZE; py++) {
			for (int px = 0; px < CostumeDesign.EMBLEM_SIZE; px++) {
				int cx = ex + px * CELL, cy = gy + py * CELL;
				int colour = design.colour(this.pixels[py * CostumeDesign.EMBLEM_SIZE + px]);
				// empty pixels as a faint checkerboard
				graphics.fill(cx, cy, cx + CELL, cy + CELL, colour != 0 ? colour : (px + py) % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
			}
		}
		graphics.outline(ex - 1, gy - 1, size + 2, size + 2, UiStyle.ROW_HOVER);
		int hx = Math.floorDiv(mouseX - ex, CELL), hy = Math.floorDiv(mouseY - gy, CELL);
		if (hx >= 0 && hy >= 0 && hx < CostumeDesign.EMBLEM_SIZE && hy < CostumeDesign.EMBLEM_SIZE) {
			graphics.outline(ex + hx * CELL, gy + hy * CELL, CELL, CELL, UiStyle.TEXT);
		}
		int py = paletteY();
		for (int i = 0; i < CostumeDesign.EMBLEM_COLOURS; i++) {
			int cx = ex + i * (CHIP + 2);
			int colour = design.colour(i);
			graphics.fill(cx, py, cx + CHIP, py + CHIP, colour != 0 ? colour : 0x30FFFFFF);
			if (i == CostumeDesign.EMPTY) {
				// the eraser: a struck-out chip
				for (int k = 2; k < CHIP - 2; k++) {
					graphics.fill(cx + k, py + k, cx + k + 1, py + k + 1, SHORT);
				}
			}
			graphics.outline(cx - 1, py - 1, CHIP + 2, CHIP + 2, i == brush ? UiStyle.TEXT : UiStyle.ROW);
			if (UiStyle.over(mouseX, mouseY, cx, py, CHIP, CHIP)) {
				graphics.setTooltipForNextFrame(this.font, Component.translatable("screen.jaymospowers.costume.palette." + i), mouseX, mouseY);
			}
		}
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.costume.paint_hint"), ex, py + CHIP + 4, size, UiStyle.HINT);
	}

	/** What sewing takes, each against what you carry; free in creative. */
	private void cost(GuiGraphicsExtractor graphics, LocalPlayer player) {
		int by = bottomY();
		UiStyle.rule(graphics, left() + 6, left() + W - 6, by);
		int x = left() + 10;
		int iy = by + 6;
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.costume.cost"), x, iy + 4, 60, UiStyle.DIM);
		x += Math.min(60, this.font.width(Component.translatable("screen.jaymospowers.costume.cost"))) + 6;
		boolean creative = player.isCreative();
		boolean enough = true;
		if (tailoring()) {
			// (0.11.78) the tailor's price, in emeralds (bundles count)
			int have = CostumeTailoring.emeralds(player);
			boolean ok = creative || have >= this.price;
			enough = ok;
			graphics.item(new ItemStack(Items.EMERALD), x, iy);
			String text = creative ? "×" + this.price : Math.min(have, 999) + "/" + this.price;
			graphics.text(this.font, text, x + 17, iy + 4, ok ? OK : SHORT);
			x += 17 + this.font.width(text) + 6;
		}
		List<CostumeNet.Need> needs = tailoring() ? List.of() : CostumeNet.cost(design);
		for (CostumeNet.Need need : needs) {
			int have = need.have(player);
			boolean ok = creative || have >= need.count();
			enough &= ok;
			graphics.item(need.icon(), x, iy);
			String text = creative ? "×" + need.count() : Math.min(have, 99) + "/" + need.count();
			graphics.text(this.font, text, x + 17, iy + 4, ok ? OK : SHORT);
			x += 17 + this.font.width(text) + 6;
		}
		if (creative) {
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.costume.free"), x, iy + 4, left() + W - 8 - x, UiStyle.HINT);
		}
		this.sew.active = enough;
		CostumeDesign worn = Costume.worn(player);
		Component wearing = worn != null
			? Component.translatable("screen.jaymospowers.costume.wearing", com.jaymospowers.costume.CostumeItem.label(worn))
			: Component.translatable("screen.jaymospowers.costume.wearing_none");
		// (0.12.3) short of the three buttons (Mask, Take off, Sew)
		UiStyle.line(graphics, this.font, wearing, left() + 10, top() + H - 17, W - 228, UiStyle.DIM);
	}
}
