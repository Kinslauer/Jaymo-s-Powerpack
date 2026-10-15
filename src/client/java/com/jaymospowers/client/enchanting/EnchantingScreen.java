package com.jaymospowers.client.enchanting;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.enchanting.EnchantRecipes;
import com.jaymospowers.enchanting.Enchanting;
import com.jaymospowers.enchanting.EnchantingMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/**
 * The enchanting table ({@link Enchanting}), drawn like the H screen: the item and its price on the left, the
 * enchantments you know that fit it on the right, the chosen one's price and the Enchant button under them.
 */
public class EnchantingScreen extends AbstractContainerScreen<EnchantingMenu> {
	static final int ACCENT = 0xFF9B6BFF, WELL_EDGE = 0xFF34343E, WELL = 0xFF0C0C12, GOOD = 0xFF6BE07A, BAD = 0xFFE06B6B;
	static final int LIST_X = 62, LIST_Y = 18, LIST_W = 162, ROW_H = 12, ROWS = 6;
	static final int COST_Y = LIST_Y + ROWS * ROW_H + 8, BUTTON_X = 162, BUTTON_Y = COST_Y + 22, BUTTON_W = 62, BUTTON_H = 14;
	private int scroll;
	private @Nullable Holder<Enchantment> picked;

	public EnchantingScreen(EnchantingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 232, 226);
		this.inventoryLabelX = EnchantingMenu.INVENTORY_X;
		this.inventoryLabelY = EnchantingMenu.INVENTORY_Y - 11;
	}

	private List<Enchanting.Option> options() {
		return Enchanting.options(minecraft.player, menu.item());
	}

	private Enchanting.@Nullable Option pickedOption(List<Enchanting.Option> options) {
		for (Enchanting.Option option : options) {
			if (option.enchantment().equals(picked)) {
				return option;
			}
		}
		return null;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos, y = topPos;
		UiStyle.panel(graphics, x, y, imageWidth, imageHeight, ACCENT);
		for (int i = 0; i < menu.slots.size(); i++) {
			var slot = menu.slots.get(i);
			if (i == EnchantingMenu.ITEM) {
				well(graphics, x + slot.x - 5, y + slot.y - 5, 26, 26);
				graphics.fill(x + slot.x - 5, y + slot.y + 20, x + slot.x + 21, y + slot.y + 21, ACCENT);
			} else {
				well(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18);
			}
		}
		UiStyle.rule(graphics, x + 6, x + imageWidth - 6, y + EnchantingMenu.INVENTORY_Y - 15);

		List<Enchanting.Option> options = options();
		scroll = Math.max(0, Math.min(scroll, options.size() - ROWS));
		int lx = x + LIST_X, ly = y + LIST_Y;
		graphics.fill(lx - 2, ly - 2, lx + LIST_W + 2, ly + ROWS * ROW_H + 2, 0x40000000);
		if (options.isEmpty()) {
			Component hint = Component.translatable(menu.item().isEmpty() ? "gui.jaymospowers.enchanting.put_item"
				: Enchanting.enchantable(menu.item()) ? "gui.jaymospowers.enchanting.none_known" : "gui.jaymospowers.enchanting.cannot");
			List<net.minecraft.util.FormattedCharSequence> lines = font.split(hint, LIST_W - 12);
			for (int i = 0; i < lines.size(); i++) {
				graphics.text(font, lines.get(i), lx + LIST_W / 2 - font.width(lines.get(i)) / 2, ly + 20 + i * 10, UiStyle.DIM);
			}
		}
		for (int i = 0; i < ROWS && scroll + i < options.size(); i++) {
			Enchanting.Option option = options.get(scroll + i);
			int ry = ly + i * ROW_H;
			boolean hover = UiStyle.over(mouseX, mouseY, lx, ry, LIST_W, ROW_H);
			if (option.enchantment().equals(picked)) {
				graphics.fill(lx, ry, lx + LIST_W, ry + ROW_H, UiStyle.ROW_PICKED);
				graphics.fill(lx, ry, lx + 2, ry + ROW_H, ACCENT);
			} else {
				UiStyle.row(graphics, lx, ry, lx + LIST_W, ry + ROW_H, i, hover);
			}
			boolean ready = option.state() == Enchanting.State.READY;
			Component name = Enchantment.getFullname(option.enchantment(), Math.max(1, option.target()));
			int color = ready ? UiStyle.TEXT : UiStyle.HINT;
			Component tag = switch (option.state()) {
				case READY -> option.current() > 0 ? Component.translatable("gui.jaymospowers.enchanting.raise") : Component.empty();
				case MAXED -> Component.translatable("gui.jaymospowers.enchanting.maxed");
				case CLASHES -> Component.translatable("gui.jaymospowers.enchanting.clashes");
				case FULL -> Component.translatable("gui.jaymospowers.enchanting.full");
				case UNKNOWN_LEVEL -> Component.translatable("gui.jaymospowers.enchanting.unknown_level");
			};
			int tagW = font.width(tag);
			UiStyle.line(graphics, font, Component.literal(name.getString()), lx + 5, ry + 2, LIST_W - tagW - 14, color);
			graphics.text(font, tag, lx + LIST_W - 4 - tagW, ry + 2, ready ? ACCENT : UiStyle.HINT);
		}
		UiStyle.scrollbar(graphics, lx + LIST_W + 3, ly, ROWS * ROW_H, ROWS, options.size(), scroll);

		Enchanting.Option option = pickedOption(options);
		UiStyle.rule(graphics, lx, lx + LIST_W, y + COST_Y - 4);
		if (option == null) {
			if (!options.isEmpty()) {
				UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.enchanting.pick"), lx + 2, y + COST_Y + 4, LIST_W, UiStyle.DIM);
			}
			return;
		}
		if (option.state() != Enchanting.State.READY) {
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.enchanting.why." + option.state().name().toLowerCase()),
				lx + 2, y + COST_Y + 4, LIST_W, UiStyle.DIM);
			return;
		}
		List<EnchantRecipes.Ing> cost = EnchantRecipes.cost(option.enchantment(), option.target());
		int[] found = EnchantingMenu.match(cost, menu.materials());
		boolean free = minecraft.player.hasInfiniteMaterials();
		for (int i = 0; i < cost.size(); i++) {
			int ix = lx + 2 + i * 26, iy = y + COST_Y;
			well(graphics, ix - 1, iy - 1, 18, 18);
			ItemStack icon = cost.get(i).icon();
			graphics.item(icon, ix, iy);
			graphics.itemDecorations(font, icon, ix, iy);
			graphics.fill(ix - 1, iy + 18, ix + 17, iy + 19, found[i] >= 0 || free ? GOOD : BAD);
		}
		boolean canPay = free || EnchantingMenu.paid(found);
		boolean hover = UiStyle.over(mouseX, mouseY, x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H);
		int bx = x + BUTTON_X, by = y + BUTTON_Y;
		graphics.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, canPay ? hover ? 0xC0603CB0 : 0xA0402880 : 0x60303030);
		graphics.fill(bx, by + BUTTON_H - 1, bx + BUTTON_W, by + BUTTON_H, canPay ? ACCENT : UiStyle.HINT);
		UiStyle.lineCentred(graphics, font, Component.translatable("gui.jaymospowers.enchanting.enchant"), bx + BUTTON_W / 2, by + 3, BUTTON_W - 4,
			canPay ? UiStyle.TEXT : UiStyle.HINT);
		if (!canPay) {
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.enchanting.missing"), lx + 2, by + 3, BUTTON_X - LIST_X - 6, BAD);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, 8, 6, ACCENT, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiStyle.DIM, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		List<Enchanting.Option> options = options();
		int lx = leftPos + LIST_X, ly = topPos + LIST_Y;
		for (int i = 0; i < ROWS && scroll + i < options.size(); i++) {
			if (UiStyle.over(mouseX, mouseY, lx, ly + i * ROW_H, LIST_W, ROW_H)) {
				Enchanting.Option option = options.get(scroll + i);
				String key = option.enchantment().value().description().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
					? t.getKey() + ".desc" : null;
				List<Component> lines = new ArrayList<>();
				lines.add(Enchantment.getFullname(option.enchantment(), Math.max(1, option.target())));
				lines.add(Component.translatable("gui.jaymospowers.enchanting.levels", option.current(), EnchantRecipes.maxLevel(option.enchantment()))
					.withStyle(ChatFormatting.GRAY));
				if (key != null && net.minecraft.locale.Language.getInstance().has(key)) {
					lines.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
				}
				graphics.setTooltipForNextFrame(lines.stream().flatMap(c -> font.split(c, 180).stream()).toList(), mouseX, mouseY);
				return;
			}
		}
		Enchanting.Option option = pickedOption(options);
		if (option == null || option.state() != Enchanting.State.READY) {
			return;
		}
		List<EnchantRecipes.Ing> cost = EnchantRecipes.cost(option.enchantment(), option.target());
		for (int i = 0; i < cost.size(); i++) {
			if (UiStyle.over(mouseX, mouseY, lx + 2 + i * 26, topPos + COST_Y, 16, 16)) {
				EnchantRecipes.Ing ing = cost.get(i);
				List<Component> lines = new ArrayList<>();
				lines.add(Component.literal(ing.count() + " x ").append(ing.icon().getHoverName()));
				if (ing.options().size() > 1 || ing.options().getFirst().startsWith("#")) {
					lines.add(Component.translatable("gui.jaymospowers.enchanting.any_of").withStyle(ChatFormatting.GRAY));
				}
				graphics.setTooltipForNextFrame(lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
				return;
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		List<Enchanting.Option> options = options();
		int lx = leftPos + LIST_X, ly = topPos + LIST_Y;
		for (int i = 0; i < ROWS && scroll + i < options.size(); i++) {
			if (UiStyle.over((int) mx, (int) my, lx, ly + i * ROW_H, LIST_W, ROW_H)) {
				picked = options.get(scroll + i).enchantment();
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
		}
		Enchanting.Option option = pickedOption(options);
		if (option != null && option.state() == Enchanting.State.READY
			&& UiStyle.over((int) mx, (int) my, leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H)) {
			boolean canPay = minecraft.player.hasInfiniteMaterials()
				|| EnchantingMenu.paid(EnchantingMenu.match(EnchantRecipes.cost(option.enchantment(), option.target()), menu.materials()));
			if (canPay) {
				int id = minecraft.player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getId(option.enchantment().value());
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (UiStyle.over((int) x, (int) y, leftPos + LIST_X, topPos + LIST_Y, LIST_W, ROWS * ROW_H)) {
			scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private static void well(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, WELL_EDGE);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
	}
}
