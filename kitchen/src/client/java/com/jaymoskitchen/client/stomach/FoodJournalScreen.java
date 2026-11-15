package com.jaymoskitchen.client.stomach;

import com.jaymoskitchen.client.KitchenUi;
import com.jaymoskitchen.stomach.Variety;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The food journal ({@link com.jaymoskitchen.stomach.FoodJournalItem}): an open book, the foods you've eaten on the left
 * page and the ones you haven't tried on the right, each a grid of the foods themselves (their names on hover), each
 * page scrolled on its own. Just the two lists.
 */
public class FoodJournalScreen extends Screen {
	private static final int PAGE_W = 128, PAGE_H = 168, GUTTER = 6, CELL = 18, COLUMNS = 6, ROWS = 8;
	private static final int TOP = 20, SIDE = (PAGE_W - COLUMNS * CELL) / 2;

	private final List<ItemStack> eaten = new ArrayList<>();
	private final List<ItemStack> untried = new ArrayList<>();
	private final int[] scroll = new int[2];

	public FoodJournalScreen() {
		super(Component.translatable("item.jaymoskitchen.food_journal"));
	}

	public static void open() {
		Minecraft.getInstance().gui.setScreen(new FoodJournalScreen());
	}

	@Override
	protected void init() {
		eaten.clear();
		untried.clear();
		Player player = Minecraft.getInstance().player;
		List<Item> had = player == null ? List.of() : Variety.foods(player);
		Set<Item> hadSet = new HashSet<>(had);
		for (Item item : had) {
			eaten.add(new ItemStack(item));
		}
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.has(DataComponents.FOOD) && !hadSet.contains(item)) {
				untried.add(stack);
			}
		}
		Comparator<ItemStack> byName = Comparator.comparing(stack -> stack.getHoverName().getString());
		eaten.sort(byName);
		untried.sort(byName);
	}

	private int left() {
		return (this.width - PAGE_W * 2 - GUTTER) / 2;
	}

	private int top() {
		return (this.height - PAGE_H) / 2;
	}

	private int pageX(int page) {
		return left() + page * (PAGE_W + GUTTER);
	}

	private List<ItemStack> list(int page) {
		return page == 0 ? eaten : untried;
	}

	private int maxScroll(int page) {
		return Math.max(0, Mth.positiveCeilDiv(list(page).size(), COLUMNS) - ROWS);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		ItemStack hovered = ItemStack.EMPTY;
		for (int page = 0; page < 2; page++) {
			int x = pageX(page), y = top();
			graphics.fill(x - 1, y - 1, x + PAGE_W + 1, y + PAGE_H + 1, KitchenUi.PAPER_EDGE);
			graphics.fill(x, y, x + PAGE_W, y + PAGE_H, KitchenUi.PAPER);
			Component heading = Component.translatable(page == 0 ? "screen.jaymoskitchen.food_journal.eaten" : "screen.jaymoskitchen.food_journal.untried");
			KitchenUi.lineCentred(graphics, this.font, heading, x + PAGE_W / 2, y + 7, PAGE_W - 8, KitchenUi.INK);
			List<ItemStack> list = list(page);
			int first = scroll[page] * COLUMNS;
			for (int i = first; i < list.size() && i < first + COLUMNS * ROWS; i++) {
				int cx = x + SIDE + (i - first) % COLUMNS * CELL, cy = y + TOP + (i - first) / COLUMNS * CELL;
				graphics.item(list.get(i), cx + 1, cy + 1);
				if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
					hovered = list.get(i);
				}
			}
			if (maxScroll(page) > 0) {
				// how far down the page you've turned
				int track = ROWS * CELL, knob = Math.max(8, track * ROWS / (ROWS + maxScroll(page)));
				int kx = x + PAGE_W - 4, ky = y + TOP + (track - knob) * scroll[page] / maxScroll(page);
				graphics.fill(kx, y + TOP, kx + 2, y + TOP + track, KitchenUi.PAPER_EDGE);
				graphics.fill(kx, ky, kx + 2, ky + knob, KitchenUi.INK_FAINT);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!hovered.isEmpty()) {
			KitchenUi.tip(graphics, this.font, hovered.getHoverName(), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int page = mouseX < pageX(1) ? 0 : 1;
		scroll[page] = Mth.clamp(scroll[page] - (int) Math.signum(scrollY), 0, maxScroll(page));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
