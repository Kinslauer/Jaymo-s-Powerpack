package com.jaymospowers.client.rig;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigForgeBlock;
import com.jaymospowers.rig.RigFinish;
import com.jaymospowers.rig.RigForgeEntity;
import com.jaymospowers.rig.RigFrame;
import com.jaymospowers.rig.RigNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.jaymospowers.client.ui.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
 * numbers under them; on the right the paints (Andy: "you should also be able to customize the rigs in the forge ... I'd
 * like the ability to recolor it"): a tab for each of the plates, the trim, the under-suit and the lights, then the
 * sixteen dyes, a mixer (a hue bar and a shade bar, for any colour) and a chip to take the paint off; along the bottom
 * what the build costs against what you carry and what is fed into the Forge, "Paint" and "Build".
 * <p>
 * Piece and frame are sent as they change, so the hologram over the Forge follows. Paint is only sent by "Paint" or
 * "Build": a new paint takes one dye, of that colour or, mixed, the nearest ({@link RigForgeBlock}). The server checks
 * everything again.
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
	/** The finish chips: their height, and each one's colour (plain first). */
	private static final int FINISH_H = 14;
	private static final int[] FINISH_CHIPS = {0xFF8A8F98, 0xFF29DFEB, 0xFFB4D6F6, 0xFF3A302B};
	/** A paint pick that is bare metal (or, for the lights, the frame's own glow). */
	private static final int BARE = -1;
	/** The paint tabs (two rows of two), and the mixer's bars: height, and the gap under each. */
	private static final int TAB_H = 12, BAR_H = 6, BAR_GAP = 3;
	private static final String[] PARTS = {"plates", "trim", "under", "lights"};
	/** The mixer bar being dragged: none, the hue, the shade. */
	private static final int NO_BAR = 0, HUE_BAR = 1, SHADE_BAR = 2;

	private final BlockPos pos;
	/** Whether this player may pick the Octo Core (a Tinkerer or a web slinger; the server says, and checks again). */
	private final boolean octo;
	private static float yaw = 20.0F;
	private int selection;
	private RigFrame frame = RigFrame.STANDARD;
	/** The paints picked here, in {@link RigForgeEntity#PLATES} order: rgb or {@link #BARE}. */
	private final int[] paints = {BARE, BARE, BARE, BARE};
	/** The paint being picked (a {@link #PARTS} index), and the mixer's hue and shade (0-1: black, the pure hue, white). */
	private int tab;
	private float hue, shade = 0.5F;
	private int dragging = NO_BAR;
	/** The finish shown on the preview: the one worn, until another is picked. */
	private int finish = -1;
	private boolean turning;
	private Button pieceButton, frameButton, build, paint;

	public RigForgeScreen(BlockPos pos, boolean octo) {
		super(Component.translatable("screen.jaymospowers.rig_forge.title"));
		this.pos = pos;
		this.octo = octo;
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

	private int wide() {
		return SWATCHES_PER_ROW * SWATCH_W;
	}

	private int tabsY() {
		return contentY();
	}

	private int swatchY() {
		return contentY() + 2 * TAB_H + 15;
	}

	private int barY(int bar) {
		return swatchY() + 2 * SWATCH_H + 3 + (bar - 1) * (BAR_H + BAR_GAP);
	}

	private int bareY() {
		return barY(SHADE_BAR) + BAR_H + BAR_GAP;
	}

	private int bottomY() {
		return contentY() + PREVIEW_H + 6;
	}

	@Override
	protected void init() {
		RigForgeEntity forge = forge();
		if (forge != null) {
			this.selection = forge.selection() == RigForgeEntity.OCTO && !this.octo ? 0 : forge.selection();
			this.frame = forge.frame();
			for (int i = 0; i < RigForgeEntity.PAINTS; i++) {
				this.paints[i] = forge.paint(i) == RigForgeEntity.NO_PAINT ? BARE : forge.paint(i);
			}
			mixFrom(this.paints[this.tab]);
		}
		int mx = middleX(), y = contentY();
		this.pieceButton = addRenderableWidget(FlatButton.builder(Component.empty(), b -> cyclePiece(1)).bounds(mx, y + 2, MIDDLE_W, 14).build());
		this.frameButton = addRenderableWidget(FlatButton.builder(Component.empty(), b -> cycleFrame(1)).bounds(mx, y + 20, MIDDLE_W, 14).build());
		int by = top() + H - 20;
		this.build = addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymospowers.rig_forge.build"), b -> send(true, true))
			.bounds(left() + W - 8 - 64, by, 64, 14).build());
		this.paint = addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymospowers.rig_forge.paint"), b -> send(true, false))
			.bounds(left() + W - 8 - 64 - 4 - 64, by, 64, 14).build());
		this.paint.setTooltip(UiStyle.tipOf(Component.translatable("screen.jaymospowers.rig_forge.paint_tip")));
		labels();
	}

	/** The dye whose colour {@code rgb} is exactly, or null for a mixed colour. */
	private static @Nullable DyeColor dyeOf(int rgb) {
		for (DyeColor colour : DyeColor.values()) {
			if ((colour.getTextureDiffuseColor() & 0xFFFFFF) == (rgb & 0xFFFFFF)) {
				return colour;
			}
		}
		return null;
	}

	/** The mixer's colour: the hue at full strength, darkened toward black below the middle of the shade bar and paled toward white above it. */
	private static int mixed(float hue, float shade) {
		int pure = java.awt.Color.HSBtoRGB(hue, 1.0F, 1.0F) & 0xFFFFFF;
		int r = pure >> 16 & 0xFF, g = pure >> 8 & 0xFF, b = pure & 0xFF;
		if (shade < 0.5F) {
			float k = shade * 2.0F;
			return Math.round(r * k) << 16 | Math.round(g * k) << 8 | Math.round(b * k);
		}
		float k = (shade - 0.5F) * 2.0F;
		return Math.round(r + (255 - r) * k) << 16 | Math.round(g + (255 - g) * k) << 8 | Math.round(b + (255 - b) * k);
	}

	/** Sets the mixer's bars to show {@code rgb} (as near as a hue and a shade can). */
	private void mixFrom(int rgb) {
		if (rgb < 0) {
			return;
		}
		float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
		if (hsb[1] > 0.05F) {
			this.hue = hsb[0];
		}
		// full strength and bright is the middle; dark below it, pale above it
		this.shade = hsb[2] < 0.999F ? hsb[2] * 0.5F : 0.5F + (1.0F - hsb[1]) * 0.5F;
	}

	/** A paint's name: the dye it is, bare (the lights: the frame's own), or a mixed colour as hex. */
	private Component paintName(int which, int rgb) {
		if (rgb < 0) {
			return Component.translatable(which == RigForgeEntity.LIGHTS ? "screen.jaymospowers.rig_forge.own_glow" : "screen.jaymospowers.rig_forge.bare");
		}
		DyeColor dye = dyeOf(rgb);
		return dye != null ? Component.translatable("color.minecraft." + dye.getName())
			: Component.translatable("screen.jaymospowers.rig_forge.mixed", String.format("#%06X", rgb & 0xFFFFFF));
	}

	private Component pieceName() {
		Rig.Piece[] pieces = Rig.Piece.values();
		if (this.selection == RigForgeEntity.OCTO) {
			return Component.translatable("item.jaymospowers.rig_octo_core");
		}
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
		if (this.selection == RigForgeEntity.OCTO && !this.octo) {
			// only a Tinkerer or a web slinger sees the Octo Core's drawings
			this.selection = Math.floorMod(this.selection + by, RigForgeEntity.SELECTIONS);
		}
		labels();
		send(false, false);
	}

	private void cycleFrame(int by) {
		this.frame = by > 0 ? this.frame.next() : this.frame.previous();
		labels();
		send(false, false);
	}

	private void send(boolean withPaint, boolean build) {
		java.util.List<Integer> out = new java.util.ArrayList<>();
		for (int paint : this.paints) {
			out.add(withPaint ? (paint < 0 ? RigNet.PAINT_WASH : paint) : RigNet.PAINT_LEAVE);
		}
		ClientPlayNetworking.send(new RigNet.ForgeSet(this.pos, this.selection, this.frame.ordinal(), out, build));
	}

	private RigForgeBlock.Cost cost() {
		Rig.Piece[] pieces = Rig.Piece.values();
		if (this.selection == RigForgeEntity.OCTO) {
			return com.jaymospowers.rig.RigOcto.cost(this.frame);
		}
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
		int chip = finishAt(mouseX, mouseY);
		if (chip >= 0) {
			if (RigFinish.has(this.minecraft.player, chip)) {
				this.finish = chip;
				ClientPlayNetworking.send(new RigNet.Finish(this.pos, chip));
			}
			return true;
		}
		int tabAt = tabAt(mouseX, mouseY);
		if (tabAt >= 0) {
			this.tab = tabAt;
			mixFrom(this.paints[tabAt]);
			return true;
		}
		int bar = barAt(mouseX, mouseY);
		if (bar != NO_BAR) {
			this.dragging = bar;
			slide(mouseX);
			return true;
		}
		int picked = swatchAt(mouseX, mouseY);
		if (picked != Integer.MIN_VALUE) {
			this.paints[this.tab] = picked < 0 ? BARE : DyeColor.byId(picked).getTextureDiffuseColor() & 0xFFFFFF;
			mixFrom(this.paints[this.tab]);
			return true;
		}
		return false;
	}

	/** Moves the dragged mixer bar to the mouse and takes its colour as the paint. */
	private void slide(double mouseX) {
		float at = net.minecraft.util.Mth.clamp((float)(mouseX - paintX()) / (wide() - 1), 0.0F, 1.0F);
		if (this.dragging == HUE_BAR) {
			this.hue = at;
		} else if (this.dragging == SHADE_BAR) {
			this.shade = at;
		} else {
			return;
		}
		this.paints[this.tab] = mixed(this.hue, this.shade);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.turning) {
			yaw = (yaw + (float)dx * TURN_PER_PIXEL) % 360.0F;
			return true;
		}
		if (this.dragging != NO_BAR) {
			slide(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.turning = false;
		this.dragging = NO_BAR;
		return super.mouseReleased(event);
	}

	/** The paint tab under the mouse, or -1. */
	private int tabAt(int mouseX, int mouseY) {
		int x = mouseX - paintX(), y = mouseY - tabsY(), half = wide() / 2;
		return x >= 0 && y >= 0 && x < wide() && y < 2 * TAB_H ? y / TAB_H * 2 + x / half : -1;
	}

	/** The mixer bar under the mouse, or {@link #NO_BAR}. */
	private int barAt(int mouseX, int mouseY) {
		int x = mouseX - paintX();
		if (x < 0 || x >= wide()) {
			return NO_BAR;
		}
		for (int bar = HUE_BAR; bar <= SHADE_BAR; bar++) {
			if (mouseY >= barY(bar) - 1 && mouseY < barY(bar) + BAR_H + 1) {
				return bar;
			}
		}
		return NO_BAR;
	}

	/** The swatch under the mouse: a dye's id, {@link #BARE} for the chip that takes the paint off, or {@code Integer.MIN_VALUE} for none. */
	private int swatchAt(int mouseX, int mouseY) {
		int x = mouseX - paintX(), y = mouseY - swatchY();
		if (x >= 0 && y >= 0 && x < wide() && y < 2 * SWATCH_H) {
			return y / SWATCH_H * SWATCHES_PER_ROW + x / SWATCH_W;
		}
		if (x >= 0 && x < wide() / 2 && mouseY >= bareY() && mouseY < bareY() + SWATCH_H) {
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
		finishes(graphics, player, mouseX, mouseY);
		cost(graphics, player);
		RigForgeEntity forge = forge();
		this.paint.active = forge != null && changed(forge);
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
		RigSuit.withPreview(this.frame, this.paints[RigForgeEntity.PLATES], this.paints[RigForgeEntity.TRIM], this.paints[RigForgeEntity.UNDER],
			this.paints[RigForgeEntity.LIGHTS], finish(player), () -> state[0] = renderer.createRenderState(player, 1.0F));
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

	/** Whether any paint picked here differs from the Forge's. */
	private boolean changed(RigForgeEntity forge) {
		for (int i = 0; i < RigForgeEntity.PAINTS; i++) {
			if ((this.paints[i] < 0 ? RigForgeEntity.NO_PAINT : this.paints[i]) != forge.paint(i)) {
				return true;
			}
		}
		return false;
	}

	/** The paints: a tab for each part, then the sixteen dyes, the mixer's hue and shade bars, and the chip that takes the paint off. */
	private void paints(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = paintX(), wide = wide(), half = wide / 2;
		// the tabs: each part's name over a strip of its paint
		for (int i = 0; i < PARTS.length; i++) {
			int tx = x + i % 2 * half, ty = tabsY() + i / 2 * TAB_H;
			graphics.fill(tx, ty, tx + half - 1, ty + TAB_H - 1, i == this.tab ? UiStyle.ROW_ALT : UiStyle.ROW);
			int colour = this.paints[i];
			graphics.fill(tx, ty + TAB_H - 3, tx + half - 1, ty + TAB_H - 1, colour < 0 ? 0xFF6A7078 : 0xFF000000 | colour);
			UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.part." + PARTS[i]), tx + half / 2, ty + 1,
				half - 4, i == this.tab ? UiStyle.TEXT : UiStyle.DIM);
			if (i == this.tab) {
				graphics.outline(tx - 1, ty - 1, half + 1, TAB_H + 1, ACCENT);
			}
		}
		int chosen = this.paints[this.tab];
		int sy = swatchY();
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge." + PARTS[this.tab], paintName(this.tab, chosen)),
			x, sy - 10, wide, UiStyle.DIM);
		DyeColor exact = chosen < 0 ? null : dyeOf(chosen);
		for (DyeColor colour : DyeColor.values()) {
			int i = colour.getId();
			int cx = x + i % SWATCHES_PER_ROW * SWATCH_W, cy = sy + i / SWATCHES_PER_ROW * SWATCH_H;
			graphics.fill(cx, cy, cx + SWATCH_W - 1, cy + SWATCH_H - 1, 0xFF000000 | colour.getTextureDiffuseColor());
			if (colour == exact) {
				graphics.outline(cx - 1, cy - 1, SWATCH_W + 1, SWATCH_H + 1, UiStyle.TEXT);
			}
		}
		// the mixer: a hue bar and a shade bar (black, the hue, white), each with its marker
		for (int bar = HUE_BAR; bar <= SHADE_BAR; bar++) {
			int by = barY(bar);
			for (int i = 0; i < wide; i++) {
				float at = i / (float)(wide - 1);
				int c = bar == HUE_BAR ? mixed(at, 0.5F) : mixed(this.hue, at);
				graphics.fill(x + i, by, x + i + 1, by + BAR_H, 0xFF000000 | c);
			}
			int mark = x + Math.round((bar == HUE_BAR ? this.hue : this.shade) * (wide - 1));
			graphics.fill(mark - 1, by - 1, mark + 2, by + BAR_H + 1, UiStyle.TEXT);
			graphics.fill(mark, by, mark + 1, by + BAR_H, 0xFF000000);
		}
		// the chip that takes it off, and the colour as it stands
		int by = bareY();
		graphics.fill(x, by, x + half - 1, by + SWATCH_H - 1, UiStyle.ROW);
		UiStyle.lineCentred(graphics, this.font, Component.translatable(this.tab == RigForgeEntity.LIGHTS ? "screen.jaymospowers.rig_forge.own_glow"
			: "screen.jaymospowers.rig_forge.bare"), x + half / 2, by + 1, half - 4, chosen < 0 ? UiStyle.TEXT : UiStyle.DIM);
		if (chosen < 0) {
			graphics.outline(x - 1, by - 1, half + 1, SWATCH_H + 1, UiStyle.TEXT);
		} else {
			graphics.fill(x + half, by, x + wide - 1, by + SWATCH_H - 1, 0xFF000000 | chosen);
		}
		UiStyle.line(graphics, this.font, chosen < 0 || exact != null ? Component.translatable("screen.jaymospowers.rig_forge.dye_cost")
			: Component.translatable("screen.jaymospowers.rig_forge.mix_cost", Component.translatable("color.minecraft."
				+ RigForgeBlock.nearestDye(chosen).getName())), x, by + SWATCH_H + 3, wide, UiStyle.HINT);
		int hovered = swatchAt(mouseX, mouseY);
		if (hovered >= 0) {
			UiStyle.tip(graphics, this.font, Component.translatable("color.minecraft." + DyeColor.byId(hovered).getName()), mouseX, mouseY);
		} else if (tabAt(mouseX, mouseY) >= 0) {
			UiStyle.tip(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.part." + PARTS[tabAt(mouseX, mouseY)] + ".tip"),
				mouseX, mouseY);
		}
	}

	/** The finish the preview wears: the one picked here, else the one on the Core (or any piece) worn. */
	private int finish(LocalPlayer player) {
		if (this.finish >= 0) {
			return this.finish;
		}
		int worn = RigFinish.NONE;
		for (Rig.Piece piece : Rig.Piece.values()) {
			ItemStack stack = player.getItemBySlot(piece.slot);
			if (piece.item() != null && stack.is(piece.item()) && (worn == RigFinish.NONE || piece == Rig.Piece.CORE)) {
				worn = Math.max(worn, RigFinish.of(stack));
			}
		}
		return worn;
	}

	private int finishY() {
		return bareY() + SWATCH_H + 26;
	}

	/** The finish chip under the mouse, or -1. */
	private int finishAt(int mouseX, int mouseY) {
		int x = mouseX - paintX(), y = mouseY - finishY();
		int wide = SWATCHES_PER_ROW * SWATCH_W / RigFinish.COUNT;
		return x >= 0 && y >= 0 && y < FINISH_H && x < wide * RigFinish.COUNT ? x / wide : -1;
	}

	/** The finishes: a chip each (plain first), the unearned ones dark; their deed under the mouse. */
	private void finishes(GuiGraphicsExtractor graphics, LocalPlayer player, int mouseX, int mouseY) {
		int x = paintX(), y = finishY(), wide = SWATCHES_PER_ROW * SWATCH_W / RigFinish.COUNT;
		int worn = finish(player);
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.rig_forge.finish",
			Component.translatable("gui.jaymospowers.rig_finish." + RigFinish.NAMES[worn])), x, y - 10, wide * RigFinish.COUNT, UiStyle.DIM);
		for (int i = 0; i < RigFinish.COUNT; i++) {
			boolean has = RigFinish.has(player, i);
			int cx = x + i * wide;
			graphics.fill(cx, y, cx + wide - 1, y + FINISH_H, has ? FINISH_CHIPS[i] : UiStyle.ROW);
			if (!has) {
				UiStyle.lineCentred(graphics, this.font, Component.literal("?"), cx + wide / 2, y + 3, wide - 2, UiStyle.HINT);
			}
			if (i == worn) {
				graphics.outline(cx - 1, y - 1, wide + 1, FINISH_H + 2, UiStyle.TEXT);
			}
		}
		int hovered = finishAt(mouseX, mouseY);
		if (hovered >= 0) {
			UiStyle.tip(graphics, this.font, Component.translatable("gui.jaymospowers.rig_finish." + RigFinish.NAMES[hovered] + ".deed"), mouseX, mouseY);
		}
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
