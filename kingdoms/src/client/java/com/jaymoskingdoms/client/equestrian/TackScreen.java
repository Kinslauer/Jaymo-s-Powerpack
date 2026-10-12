package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.equestrian.Bond;
import com.jaymoskingdoms.equestrian.Bonding;
import com.jaymoskingdoms.equestrian.Discipline;
import com.jaymoskingdoms.equestrian.TackMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * (0.5.40) The tack screen ({@link TackMenu}): saddle, bridle, blanket and barding down the left, the horse in the
 * middle, and on the right how much it trusts you, its four disciplines (level and progress), its hunger, thirst and
 * hooves. Drawn plainly in vanilla's grey.
 */
public class TackScreen extends AbstractContainerScreen<TackMenu> {
	private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
	private static final int WIDTH = 176, HEIGHT = 184;
	private static final int PANEL_X = 82, PANEL_Y = 18, ROW = 9, BAR_X = 50, BAR_W = 38;
	static final int TRUST_LOW = 0xE04040, TRUST_MID = 0xFFC04A, TRUST_HIGH = 0x8FE07A;
	static final int HUNGER_COLOR = 0xD9A040, THIRST_COLOR = 0x4AA8FF, HOOF_COLOR = 0xB8A890, SHOD_COLOR = 0xC8CCD2;
	private static final int[] DISCIPLINE_COLORS = {0x8FE07A, 0x7FD4FF, 0xBFF4EA, 0xE06A4A};
	private static final int TEXT = 0xFF404040;
	private float xMouse;
	private float yMouse;

	public TackScreen(TackMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.xMouse = mouseX;
		this.yMouse = mouseY;
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		// a vanilla-grey panel with a bevelled edge
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFFFFFFFF);
		graphics.fill(x + 3, y + 3, x + WIDTH - 1, y + HEIGHT - 1, 0xFF555555);
		graphics.fill(x + 3, y + 3, x + WIDTH - 3, y + HEIGHT - 3, 0xFFC6C6C6);
		for (Slot slot : this.menu.slots) {
			if (slot.isActive()) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x + slot.x - 1, y + slot.y - 1, 18, 18);
			}
		}
		// the horse
		graphics.fill(x + 26, y + 17, x + 78, y + 89, 0xFF000000);
		graphics.fill(x + 27, y + 18, x + 77, y + 88, 0xFF8B8B8B);
		AbstractHorse horse = this.menu.horse;
		if (horse != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x + 27, y + 18, x + 77, y + 88, 17, 0.25F, this.xMouse, this.yMouse, horse);
			panel(graphics, horse, x + PANEL_X, y + PANEL_Y);
		}
	}

	/** Its trust in you, its disciplines, its needs and hooves. */
	private void panel(GuiGraphicsExtractor graphics, AbstractHorse horse, int x, int y) {
		Bond b = horse.getAttached(Bonding.BOND);
		if (b == null) {
			return;
		}
		float trust = this.minecraft.player == null ? 0.0F : b.trustOf(this.minecraft.player.getUUID());
		row(graphics, x, y, Component.translatable("tack.jaymoskingdoms.trust", Math.round(trust)), trust / Bond.FULL, trustColor(trust));
		for (Discipline d : Discipline.values()) {
			float xp = b.xp(d);
			Component label = Component.translatable("tack.jaymoskingdoms.discipline", Component.translatable(d.key()), Discipline.level(xp));
			row(graphics, x, y + ROW * (1 + d.ordinal()), label, Discipline.progress(xp), DISCIPLINE_COLORS[d.ordinal()]);
		}
		int ny = y + ROW * 5;
		row(graphics, x, ny, Component.translatable("tack.jaymoskingdoms.hunger"), b.hunger() / Bond.FULL, HUNGER_COLOR);
		row(graphics, x, ny + ROW, Component.translatable("tack.jaymoskingdoms.thirst"), b.thirst() / Bond.FULL, THIRST_COLOR);
		if (b.shod()) {
			row(graphics, x, ny + ROW * 2, Component.translatable("tack.jaymoskingdoms.shoes"), b.shoes() / Bond.FULL, SHOD_COLOR);
		} else {
			row(graphics, x, ny + ROW * 2, Component.translatable("tack.jaymoskingdoms.hooves"), b.hooves() / Bond.FULL,
				b.hooves() < Bonding.LAME ? TRUST_LOW : HOOF_COLOR);
		}
	}

	private void row(GuiGraphicsExtractor graphics, int x, int y, Component label, float share, int color) {
		graphics.text(this.font, label, x, y, TEXT, false);
		int bx = x + BAR_X;
		graphics.fill(bx, y + 2, bx + BAR_W, y + 6, 0xFF8B8B8B);
		graphics.fill(bx, y + 2, bx + Mth.ceil(BAR_W * Mth.clamp(share, 0.0F, 1.0F)), y + 6, 0xFF000000 | color);
	}

	static int trustColor(float trust) {
		return trust < Bonding.BUCK_BELOW ? TRUST_LOW : trust < Bonding.WHISTLE_TRUST ? TRUST_MID : TRUST_HIGH;
	}
}
