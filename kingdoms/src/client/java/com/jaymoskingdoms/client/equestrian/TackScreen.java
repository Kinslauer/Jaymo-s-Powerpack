package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.equestrian.Bond;
import com.jaymoskingdoms.equestrian.Bonding;
import com.jaymoskingdoms.equestrian.Discipline;
import com.jaymoskingdoms.equestrian.TackMenu;
import com.jaymoskingdoms.client.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;

/**
 * (0.5.44) The tack screen ({@link TackMenu}): saddle, bridle, blanket and barding down the left, the horse in the
 * middle, and on the right how much it trusts you, its four disciplines (level and progress), its hunger, thirst and
 * hooves. (0.7.38) Drawn like the H screen: a dark panel with a gold stripe, sunken slots.
 */
public class TackScreen extends AbstractContainerScreen<TackMenu> {
	private static final int WIDTH = 176, HEIGHT = 184;
	private static final int PANEL_X = 82, PANEL_Y = 18, ROW = 9, BAR_X = 50, BAR_W = 38;
	static final int TRUST_LOW = 0xE04040, TRUST_MID = 0xFFC04A, TRUST_HIGH = 0x8FE07A;
	static final int HUNGER_COLOR = 0xD9A040, THIRST_COLOR = 0x4AA8FF, HOOF_COLOR = 0xB8A890, SHOD_COLOR = 0xC8CCD2;
	private static final int[] DISCIPLINE_COLORS = {0x8FE07A, 0x7FD4FF, 0xBFF4EA, 0xE06A4A};
	private static final int TEXT = Ui.TEXT;
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
		Ui.panel(graphics, x, y, WIDTH, HEIGHT, Ui.GOLD);
		Ui.slots(graphics, this.menu.slots, x, y);
		// the horse
		Ui.well(graphics, x + 26, y + 17, 52, 72);
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
		graphics.fill(bx, y + 3, bx + BAR_W, y + 6, Ui.BAR_BACK);
		graphics.fill(bx, y + 3, bx + Mth.ceil(BAR_W * Mth.clamp(share, 0.0F, 1.0F)), y + 6, 0xFF000000 | color);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, Ui.GOLD, false);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, Ui.DIM, false);
	}

	static int trustColor(float trust) {
		return trust < Bonding.BUCK_BELOW ? TRUST_LOW : trust < Bonding.WHISTLE_TRUST ? TRUST_MID : TRUST_HIGH;
	}
}
