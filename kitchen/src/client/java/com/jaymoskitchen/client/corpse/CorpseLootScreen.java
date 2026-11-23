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
 * below. What's taken comes off the body in the well too. A player's body has the wide screen ({@link CorpseLootMenu}).
 */
public class CorpseLootScreen extends AbstractContainerScreen<CorpseLootMenu> {
	private static final int WIDTH = 176, HEIGHT = 188;
	/** How steeply the body is looked down on (degrees), and how far the mouse turns it either way. */
	private static final float LOOK_DOWN = 60.0F, TURN = 60.0F;
	/** The most pixels a block is drawn at (a small body isn't blown up past this). */
	private static final float MAX_SCALE = 34.0F;
	private float xMouse;

	public CorpseLootScreen(CorpseLootMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, CorpseLootMenu.isPlayer(menu.corpse) ? CorpseLootMenu.PLAYER_WIDTH : WIDTH, HEIGHT);
		this.inventoryLabelY = CorpseLootMenu.INVENTORY_Y - 11;
		if (CorpseLootMenu.isPlayer(menu.corpse)) {
			this.inventoryLabelX = CorpseLootMenu.PLAYER_INVENTORY_X;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.xMouse = mouseX;
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

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
		KitchenUi.line(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - this.titleLabelX * 2, KitchenUi.ACCENT);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, KitchenUi.DIM);
	}
}
