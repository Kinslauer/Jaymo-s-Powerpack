package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.client.KitchenUi;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.corpse.CorpseLootMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A body's loot screen ({@link CorpseLootMenu}), in the Kitchen's panel like the lunch box's: its armour down the left,
 * the body itself in the well beside it, drawn as it lies (its own renderer: ragdoll, wounds, arrows, shut eyes) and seen
 * from above, turning a little with the mouse; its hands to the right of that, its pockets on the right, your inventory
 * below. What's taken comes off the body in the well too. A player's body has the wide screen ({@link CorpseLootMenu}), and
 * so does a mob's that wore harness: a block of it to the right of the pockets, titled.
 */
public class CorpseLootScreen extends AbstractContainerScreen<CorpseLootMenu> {
	private static final int WIDTH = 176, HEIGHT = 188;
	/** How steeply the body is looked down on (degrees), and how far the mouse turns it either way. */
	private static final float LOOK_DOWN = 60.0F, TURN = 60.0F;
	/** The most pixels a block is drawn at (a small body isn't blown up past this). */
	private static final float MAX_SCALE = 34.0F;
	private float xMouse;

	public CorpseLootScreen(CorpseLootMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, menu.width(WIDTH), HEIGHT);
		this.inventoryLabelY = CorpseLootMenu.INVENTORY_Y - 11;
		if (menu.inventoryX() != 8) {
			this.inventoryLabelX = menu.inventoryX();
		}
	}

	/** The cut-off button (in the free spot above the hands) and its list of parts, beside it over the pockets. */
	private static final int CUT_X = CorpseLootMenu.HANDS_X - 1, CUT_Y = CorpseLootMenu.TOP - 1, CUT_SIZE = 18,
		LIST_X = CorpseLootMenu.POCKETS_X - 1, LIST_W = 74, LIST_ROW = 12;
	private static final String[] PART_KEYS = {"head", "right_arm", "left_arm", "right_leg", "left_leg"};
	private static final net.minecraft.world.item.ItemStack CUT_ICON = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE);
	/** A piece bolted on: a red wash over its slot. */
	private static final int LOCKED = 0x50C03030;
	private boolean listOpen;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.xMouse = mouseX;
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!this.menu.canCutOff()) {
			return;
		}
		int x = this.leftPos, y = this.topPos;
		boolean overCut = KitchenUi.over(mouseX, mouseY, x + CUT_X, y + CUT_Y, CUT_SIZE, CUT_SIZE);
		KitchenUi.button(graphics, this.font, x + CUT_X, y + CUT_Y, CUT_SIZE, CUT_SIZE, Component.empty(), KitchenUi.ACCENT, overCut || this.listOpen, true);
		graphics.item(CUT_ICON, x + CUT_X + 1, y + CUT_Y + 1);
		for (int i = 0; i < 4; i++) {
			if (this.menu.attached(i) >= 0 && this.menu.slots.get(i).isActive()) {
				var slot = this.menu.slots.get(i);
				graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, LOCKED);
			}
		}
		if (this.listOpen) {
			int[] parts = partsOn();
			int top = y + CUT_Y;
			graphics.fill(x + LIST_X - 1, top - 1, x + LIST_X + LIST_W + 1, top + parts.length * LIST_ROW + 1, KitchenUi.WELL_EDGE);
			for (int i = 0; i < parts.length; i++) {
				int rowY = top + i * LIST_ROW;
				boolean over = KitchenUi.over(mouseX, mouseY, x + LIST_X, rowY, LIST_W, LIST_ROW);
				graphics.fill(x + LIST_X, rowY, x + LIST_X + LIST_W, rowY + LIST_ROW, KitchenUi.PANEL);
				KitchenUi.row(graphics, x + LIST_X, rowY, x + LIST_X + LIST_W, rowY + LIST_ROW, i, over);
				KitchenUi.line(graphics, this.font, Component.translatable("screen.jaymoskitchen.corpse.cut." + PART_KEYS[parts[i]]),
					x + LIST_X + 4, rowY + 2, LIST_W - 8, over ? KitchenUi.TEXT : KitchenUi.DIM);
			}
			if (parts.length == 0) {
				graphics.fill(x + LIST_X, top, x + LIST_X + LIST_W, top + LIST_ROW, KitchenUi.PANEL);
				KitchenUi.line(graphics, this.font, Component.translatable("screen.jaymoskitchen.corpse.cut.none"), x + LIST_X + 4, top + 2,
					LIST_W - 8, KitchenUi.HINT);
			}
		} else if (overCut) {
			KitchenUi.tip(graphics, this.font, Component.translatable("screen.jaymoskitchen.corpse.cut.tip"), mouseX, mouseY);
		}
	}

	/** The parts still on the body, as indexes into {@link CorpseLootMenu#PARTS}. */
	private int[] partsOn() {
		if (this.menu.corpse == null) {
			return new int[0];
		}
		int missing = this.menu.corpse.missing();
		return java.util.stream.IntStream.range(0, CorpseLootMenu.PARTS.length).filter(i -> (missing & CorpseLootMenu.PARTS[i]) == 0).toArray();
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (this.menu.canCutOff() && this.minecraft != null && this.minecraft.gameMode != null) {
			int x = this.leftPos, y = this.topPos;
			if (this.listOpen) {
				int[] parts = partsOn();
				for (int i = 0; i < parts.length; i++) {
					if (KitchenUi.over((int) event.x(), (int) event.y(), x + LIST_X, y + CUT_Y + i * LIST_ROW, LIST_W, LIST_ROW)) {
						this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, CorpseLootMenu.CUT + parts[i]);
						this.listOpen = false;
						return true;
					}
				}
			}
			if (KitchenUi.over((int) event.x(), (int) event.y(), x + CUT_X, y + CUT_Y, CUT_SIZE, CUT_SIZE)) {
				this.listOpen = !this.listOpen;
				this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
			if (this.listOpen) {
				this.listOpen = false;
				return true;
			}
			// a piece bolted on: a click on it cuts off the part it's on
			if (this.hoveredSlot != null && this.hoveredSlot.index < CorpseLoot_GEAR && this.menu.partFor(this.hoveredSlot.index) != 0) {
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, CorpseLootMenu.CUT_GEAR + this.hoveredSlot.index);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected java.util.List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack) {
		java.util.List<Component> lines = new java.util.ArrayList<>(super.getTooltipFromContainerItem(stack));
		if (this.hoveredSlot != null && this.hoveredSlot.index < CorpseLoot_GEAR) {
			int on = this.menu.attached(this.hoveredSlot.index);
			int part = this.menu.partFor(this.hoveredSlot.index);
			if (on == 0) {
				lines.add(Component.translatable("screen.jaymoskitchen.corpse.attached.torso").withStyle(net.minecraft.ChatFormatting.RED));
			} else if (part != 0) {
				int i = java.util.Arrays.stream(CorpseLootMenu.PARTS).boxed().toList().indexOf(part);
				lines.add(Component.translatable("screen.jaymoskitchen.corpse.attached." + PART_KEYS[i]).withStyle(net.minecraft.ChatFormatting.RED));
			}
		}
		return lines;
	}

	/** The gear slots come first in the menu ({@code CorpseLoot.GEAR}: head, chest, legs, feet, body, hands). */
	private static final int CorpseLoot_GEAR = 7;

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		KitchenUi.panel(graphics, x, y, this.imageWidth, HEIGHT, KitchenUi.ACCENT);
		KitchenUi.rule(graphics, x + 6, x + this.imageWidth - 6, y + this.inventoryLabelY - 4);
		KitchenUi.slots(graphics, this.menu.slots, x, y);
		int x0 = x + CorpseLootMenu.BODY_X, y0 = y + CorpseLootMenu.BODY_Y;
		KitchenUi.well(graphics, x0, y0, CorpseLootMenu.BODY_W, CorpseLootMenu.BODY_H);
		body(graphics, x0 + 1, y0 + 1, x0 + CorpseLootMenu.BODY_W - 1, y0 + CorpseLootMenu.BODY_H - 1);
	}

	/** The body, drawn by its own renderer, centred in the box and fitted to it. */
	private void body(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		CorpseEntity corpse = this.menu.corpse;
		if (corpse == null || corpse.isRemoved() || this.minecraft == null) {
			return;
		}
		EntityRenderer<? super CorpseEntity, ?> renderer = this.minecraft.getEntityRenderDispatcher().getRenderer(corpse);
		EntityRenderState state = renderer.createRenderState(corpse, 1.0F);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		// (on the client the corpse's box takes in the whole drawn ragdoll: see CorpseEntity#makeBoundingBox)
		AABB box = corpse.getBoundingBox();
		Vec3 centre = box.getCenter();
		Vector3f middle = new Vector3f((float) (centre.x - corpse.getX()), (float) (centre.y - corpse.getY()), (float) (centre.z - corpse.getZ()));
		float turn = Mth.clamp((this.xMouse - (x0 + x1) / 2.0F) / ((x1 - x0) / 2.0F), -1.0F, 1.0F) * TURN;
		Quaternionf camera = new Quaternionf().rotateX(LOOK_DOWN * Mth.DEG_TO_RAD);
		Quaternionf rotation = new Quaternionf().rotateZ(Mth.PI).mul(camera).rotateY(turn * Mth.DEG_TO_RAD);
		// moved so the middle of the body is at the middle of the box (the model is turned about its own feet)
		Vector3f translation = rotation.transform(middle).negate();
		float size = (float) Math.max(0.5, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())));
		float scale = Math.min(MAX_SCALE, (x1 - x0 - 6) / (size * 1.15F));
		graphics.entity(state, scale, translation, rotation, camera, x0, y0, x1, y1);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.menu.harnessPlaces > 0) {
			// (the name stops where the harness block starts)
			KitchenUi.line(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, CorpseLootMenu.MOB_HARNESS_X - this.titleLabelX - 6, KitchenUi.ACCENT);
			KitchenUi.line(graphics, this.font, Component.translatable("container.jaymoskitchen.corpse.harness"), CorpseLootMenu.MOB_HARNESS_X, this.titleLabelY,
				CorpseLootMenu.MOB_HARNESS_COLUMNS * 18, KitchenUi.ACCENT);
		} else {
			KitchenUi.line(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - this.titleLabelX * 2, KitchenUi.ACCENT);
		}
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, KitchenUi.DIM);
	}
}
