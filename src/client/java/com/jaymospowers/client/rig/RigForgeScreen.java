package com.jaymospowers.client.rig;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigForgeBlock;
import com.jaymospowers.rig.RigForgeEntity;
import com.jaymospowers.rig.RigFrame;
import com.jaymospowers.rig.RigNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The Rig Forge's screen, in the Costume Table designer's look (Andy: "they deserve a costume style UI"): on the left a
 * preview of you in the whole suit in the picked frame and paint (drag it to turn it); in the middle what to build (the
 * four pieces, a Rig arm, a Rig leg) and the frame, each a button that cycles (right-click steps back), with the frame's
 * numbers under them; on the right the two paints, plates and trim, as dye swatches with a bare-metal chip; along the
 * bottom what the build costs against what you carry and what is fed into the Forge, "Paint" and "Build".
 * <p>
 * Piece and frame are sent as they change, so the hologram over the Forge follows. Paint is only sent by "Paint" or
 * "Build": a new paint takes one dye of that colour ({@link RigForgeBlock}). The server checks everything again.
 */
public class RigForgeScreen extends Screen {
	private static final int W = 372, H = 236;
	private static final int ACCENT = 0xFF5FE6FF;
	private static final int PREVIEW_W = 92, PREVIEW_H = 150;
	private static final int PREVIEW_SCALE = 58;
	private static final float TURN_PER_PIXEL = 1.6F;
	private static final int MIDDLE_W = 140;
	private static final int SWATCH_W = 14, SWATCH_H = 10, SWATCHES_PER_ROW = 8;
	private static final int OK = 0xFF7CD67C, SHORT = 0xFFE06060;
	/** A paint pick that is bare metal. */
	private static final int BARE = -1;

	private final BlockPos pos;
	private static float yaw = 20.0F;
	private int selection;
	private RigFrame frame = RigFrame.STANDARD;
	/** The paints picked here: a dye's id or {@link #BARE}. */
	private int plates = BARE, trim = BARE;
	private boolean turning;
	private Button pieceButton, frameButton, build, paint;

	public RigForgeScreen(BlockPos pos) {
		super(Component.translatable("screen.jaymospowers.rig_forge.title"));
		this.pos = pos;
	}

	private @Nullable RigForgeEntity forge() {
		return this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.pos) instanceof RigForgeEntity forge ? forge : null;
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

	private int paintX() {
		return middleX() + MIDDLE_W + 8;
	}

	private int swatchY(boolean trimRow) {
		return contentY() + (trimRow ? 62 : 12);
	}

	private int bottomY() {
		return contentY() + PREVIEW_H + 6;
	}

	@Override
	protected void init() {
		RigForgeEntity forge = forge();
		if (forge != null) {
			this.selection = forge.selection();
			this.frame = forge.frame();
			this.plates = dyeOf(forge.plates());
			this.trim = dyeOf(forge.trim());
		}
		int mx = middleX(), y = contentY();
		this.pieceButton = addRenderableWidget(Button.builder(Component.empty(), b -> cyclePiece(1)).bounds(mx, y + 2, MIDDLE_W, 14).build());
		this.frameButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleFrame(1)).bounds(mx, y + 20, MIDDLE_W, 14).build());
		int by = top() + H - 20;
		this.build = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.rig_forge.build"), b -> send(true, true))
			.bounds(left() + W - 8 - 64, by, 64, 14).build());
		this.paint = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.rig_forge.paint"), b -> send(true, false))
			.bounds(left() + W - 8 - 64 - 4 - 64, by, 64, 14).build());
		this.paint.setTooltip(Tooltip.create(Component.translatable("screen.jaymospowers.rig_forge.paint_tip")));
		labels();
	}

	/** The dye whose colour {@code rgb} is, or bare. */
	private static int dyeOf(int rgb) {
		if (rgb != RigForgeEntity.NO_PAINT) {
			for (DyeColor colour : DyeColor.values()) {
				if ((colour.getTextureDiffuseColor() & 0xFFFFFF) == (rgb & 0xFFFFFF)) {
					return colour.getId();
				}
			}
		}
		return BARE;
	}

	private static int rgbOf(int dye) {
		return dye < 0 ? -1 : DyeColor.byId(dye).getTextureDiffuseColor() & 0xFFFFFF;
	}

	private Component pieceName() {
		Rig.Piece[] pieces = Rig.Piece.values();
		return Component.translatable(this.selection < pieces.length ? "item.jaymospowers.rig_" + pieces[this.selection].id()
			: this.selection == pieces.length ? "item.jaymospowers.rig_arm" : "item.jaymospowers.rig_leg");
	}

	private void labels() {
		this.pieceButton.setMessage(Component.translatable("screen.jaymospowers.rig_forge.piece", pieceName()));
		this.frameButton.setMessage(Component.translatable("screen.jaymospowers.rig_forge.frame",
			Component.translatable("rig.jaymospowers.frame." + this.frame.id())));
	}

	private void cyclePiece(int by) {
		this.selection = Math.floorMod(this.selection + by, RigForgeEntity.SELECTIONS);
		labels();
		send(false, false);
	}

	private void cycleFrame(int by) {
		this.frame = by > 0 ? this.frame.next() : this.frame.previous();
		labels();
		send(false, false);
	}

	private void send(boolean withPaint, boolean build) {
		ClientPlayNetworking.send(new RigNet.ForgeSet(this.pos, this.selection, this.frame.ordinal(),
			withPaint ? (this.plates < 0 ? RigNet.PAINT_WASH : this.plates) : RigNet.PAINT_LEAVE,
			withPaint ? (this.trim < 0 ? RigNet.PAINT_WASH : this.trim) : RigNet.PAINT_LEAVE, build));
	}

	private RigForgeBlock.Cost cost() {
		Rig.Piece[] pieces = Rig.Piece.values();
		return this.selection < pieces.length ? RigForgeBlock.cost(pieces[this.selection], this.frame)
			: RigForgeBlock.limbCost(this.selection - pieces.length + 1, this.frame);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		LocalPlayer player = this.minecraft.player;
		if (player == null || forge() == null || player.distanceToSqr(Vec3.atCenterOf(this.pos)) > 64.0) {
			onClose();
		}
	}

	// ---- input --------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int)event.x(), mouseY = (int)event.y();
		boolean right = event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
		if (right && this.pieceButton.isMouseOver(mouseX, mouseY)) {
			cyclePiece(-1);
			return true;
		}
		if (right && this.frameButton.isMouseOver(mouseX, mouseY)) {
			cycleFrame(-1);
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (UiStyle.over(mouseX, mouseY, previewX(), contentY(), PREVIEW_W, PREVIEW_H)) {
			this.turning = true;
			return true;
		}
		for (int row = 0; row < 2; row++) {
			int picked = swatchAt(mouseX, mouseY, row == 1);
			if (picked != Integer.MIN_VALUE) {
				if (row == 1) {
					this.trim = picked;
				} else {
					this.plates = picked;
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.turning) {
			yaw = (yaw + (float)dx * TURN_PER_PIXEL) % 360.0F;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.turning = false;
		return super.mouseReleased(event);
	}

	/** The paint under the mouse in a row: a dye's id, {@link #BARE} for the bare-metal chip, or {@code Integer.MIN_VALUE} for none. */
	private int swatchAt(int mouseX, int mouseY, boolean trimRow) {
		int x = mouseX - paintX(), y = mouseY - swatchY(trimRow);
		if (x >= 0 && y >= 0 && x < SWATCHES_PER_ROW * SWATCH_W && y < 2 * SWATCH_H) {
			return y / SWATCH_H * SWATCHES_PER_ROW + x / SWATCH_W;
		}
		if (x >= 0 && x < SWATCHES_PER_ROW * SWATCH_W && y >= 2 * SWATCH_H + 2 && y < 3 * SWATCH_H + 2) {
			return BARE;
		}
		return Integer.MIN_VALUE;
	}

	// ---- drawing ------------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		int x = left(), y = top();
		UiStyle.panel(graphics, x, y, W, H, ACCENT);
		UiStyle.header(graphics, this.font, x, y, W, new ItemStack(Rig.CORE), this.title,
			Component.translatable("screen.jaymospowers.rig_forge.sub"), ACCENT, 0);
		preview(graphics, player);
		stats(graphics);
		paints(graphics, mouseX, mouseY);
		cost(graphics, player);
		RigForgeEntity forge = forge();
		this.paint.active = forge != null && (rgbOf(this.plates) != forge.plates() || rgbOf(this.trim) != forge.trim());
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The player in the whole suit, in the picked frame and paint, turned by dragging. */
	private void preview(GuiGraphicsExtractor graphics, LocalPlayer player) {
		int px = previewX(), py = contentY();
		graphics.fill(px, py, px + PREVIEW_W, py + PREVIEW_H, UiStyle.ROW_ALT);
		UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.turn"), px + PREVIEW_W / 2,
			py + PREVIEW_H - 10, PREVIEW_W - 4, UiStyle.HINT);
		EntityRenderer<? super LivingEntity, ?> renderer = this.minecraft.getEntityRenderDispatcher().getRenderer((LivingEntity)player);
		EntityRenderState[] state = new EntityRenderState[1];
		RigSuit.withPreview(this.frame, rgbOf(this.plates), rgbOf(this.trim), () -> state[0] = renderer.createRenderState(player, 1.0F));
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

	/** Under the two buttons: what the picked frame is like, against the standard one. */
	private void stats(GuiGraphicsExtractor graphics) {
		int mx = middleX(), y = contentY() + 40;
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.note." + this.frame.id()), mx, y, MIDDLE_W, UiStyle.TEXT);
		y += UiStyle.LINE + 4;
		y = stat(graphics, mx, y, "armor", signed(this.frame.armor), this.frame.armor);
		y = stat(graphics, mx, y, "ground", percent(1.0 + this.frame.speed), this.frame.speed);
		y = stat(graphics, mx, y, "air", percent(this.frame.airSpeed), this.frame.airSpeed - 1.0);
		y = stat(graphics, mx, y, "shots", percent(this.frame.shotDamage), this.frame.shotDamage - 1.0);
		if (this.frame.extraMissiles > 0) {
			stat(graphics, mx, y, "missiles", String.valueOf(this.frame.extraMissiles), 1.0);
		}
	}

	private int stat(GuiGraphicsExtractor graphics, int x, int y, String key, String value, double better) {
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.stat." + key, value), x, y, MIDDLE_W,
			better > 1.0E-6 ? OK : better < -1.0E-6 ? SHORT : UiStyle.DIM);
		return y + UiStyle.LINE + 1;
	}

	private static String signed(double value) {
		return (value > 0.0 ? "+" : "") + (int)Math.round(value);
	}

	private static String percent(double share) {
		return Math.round(share * 100.0) + "%";
	}

	/** The two paints: sixteen dyes and a bare-metal chip each. */
	private void paints(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = paintX(), wide = SWATCHES_PER_ROW * SWATCH_W;
		for (int row = 0; row < 2; row++) {
			boolean trimRow = row == 1;
			int chosen = trimRow ? this.trim : this.plates;
			int sy = swatchY(trimRow);
			Component name = chosen < 0 ? Component.translatable("screen.jaymospowers.rig_forge.bare")
				: Component.translatable("color.minecraft." + DyeColor.byId(chosen).getName());
			UiStyle.line(graphics, this.font, Component.translatable(trimRow ? "screen.jaymospowers.rig_forge.trim" : "screen.jaymospowers.rig_forge.plates",
				name), x, sy - 10, wide, UiStyle.DIM);
			for (DyeColor colour : DyeColor.values()) {
				int i = colour.getId();
				int cx = x + i % SWATCHES_PER_ROW * SWATCH_W, cy = sy + i / SWATCHES_PER_ROW * SWATCH_H;
				graphics.fill(cx, cy, cx + SWATCH_W - 1, cy + SWATCH_H - 1, 0xFF000000 | colour.getTextureDiffuseColor());
				if (i == chosen) {
					graphics.outline(cx - 1, cy - 1, SWATCH_W + 1, SWATCH_H + 1, UiStyle.TEXT);
				}
			}
			int by = sy + 2 * SWATCH_H + 2;
			graphics.fill(x, by, x + wide - 1, by + SWATCH_H - 1, UiStyle.ROW);
			UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.bare"), x + wide / 2, by + 1, wide - 4,
				chosen < 0 ? UiStyle.TEXT : UiStyle.DIM);
			if (chosen < 0) {
				graphics.outline(x - 1, by - 1, wide + 1, SWATCH_H + 1, UiStyle.TEXT);
			}
			int hovered = swatchAt(mouseX, mouseY, trimRow);
			if (hovered >= 0) {
				graphics.setTooltipForNextFrame(this.font, Component.translatable("color.minecraft." + DyeColor.byId(hovered).getName()), mouseX, mouseY);
			}
		}
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.dye_cost"), x, swatchY(true) + 3 * SWATCH_H + 8, wide,
			UiStyle.HINT);
	}

	/** What the build takes, each against what you carry (salvage points count what is fed into the Forge); free in creative. */
	private void cost(GuiGraphicsExtractor graphics, LocalPlayer player) {
		int by = bottomY();
		UiStyle.rule(graphics, left() + 6, left() + W - 6, by);
		int x = left() + 10, iy = by + 6;
		Component label = Component.translatable("screen.jaymospowers.rig_forge.cost");
		UiStyle.line(graphics, this.font, label, x, iy + 4, 60, UiStyle.DIM);
		x += Math.min(60, this.font.width(label)) + 6;
		boolean creative = player.isCreative();
		RigForgeEntity forge = forge();
		int fed = forge == null ? 0 : forge.fed();
		RigForgeBlock.Cost cost = cost();
		boolean enough = true;
		int[] need = {cost.points(), cost.firstCount(), cost.secondCount()};
		int[] have = {RigForgeBlock.points(player) + fed, RigForgeBlock.count(player, cost.first()), RigForgeBlock.count(player, cost.second())};
		ItemStack[] icons = {new ItemStack(Rig.SALVAGE), new ItemStack(cost.first()), new ItemStack(cost.second())};
		for (int i = 0; i < need.length; i++) {
			boolean ok = creative || have[i] >= need[i];
			enough &= ok;
			graphics.item(icons[i], x, iy);
			String text = creative ? "×" + need[i] : Math.min(have[i], 99) + "/" + need[i];
			graphics.text(this.font, text, x + 17, iy + 4, ok ? OK : SHORT);
			x += 17 + this.font.width(text) + 6;
		}
		if (creative) {
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.free"), x, iy + 4, left() + W - 8 - x, UiStyle.HINT);
		}
		this.build.active = enough;
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.fed", fed, RigForgeEntity.MAX_FED), left() + 10,
			top() + H - 17, W - 160, UiStyle.DIM);
	}
}
