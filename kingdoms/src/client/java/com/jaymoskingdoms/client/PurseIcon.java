package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Currency;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.client.mixin.equestrian.AbstractContainerScreenAccessor;
import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.money.MoneyItems;
import java.util.Map;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Andy (2026-11-23, from Numismatic Overhaul's purse): the coin you carry, at a glance, "only when our bauble is
 * equipped, and it should contain and display all our different currencies". While a coin purse hangs in Jaymo's Powers'
 * belt slot, the survival inventory shows it beside your figure; hover it for every coin you carry, currency by currency
 * (loose, in any purse, in bundles: {@link Money#held}).
 */
public final class PurseIcon extends AbstractButton {
	static final int SIZE = 18;
	/** Right of the figure, under the crafting grid (from the inventory's corner). */
	static final int RIGHT = 77, DOWN = 60;

	private final InventoryScreen screen;
	/** The tooltip's text as last built, so it is built again only when the coins change. */
	private String shown = "";

	private PurseIcon(InventoryScreen screen) {
		super(0, 0, SIZE, SIZE, Component.translatable("gui.jaymoskingdoms.purse_icon"));
		this.screen = screen;
	}

	static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof InventoryScreen inventory) {
				PurseIcon icon = new PurseIcon(inventory);
				icon.place();
				Screens.getWidgets(inventory).add(icon);
				ScreenEvents.beforeExtract(inventory).register((s, graphics, mouseX, mouseY, delta) -> icon.place());
			}
		});
	}

	void place() {
		AbstractContainerScreenAccessor at = (AbstractContainerScreenAccessor)this.screen;
		Player player = Minecraft.getInstance().player;
		this.visible = player != null && PowersCompat.belt(player).is(MoneyItems.COIN_PURSE);
		setPosition(at.jaymoskingdoms$leftPos() + RIGHT, at.jaymoskingdoms$topPos() + DOWN);
		if (this.visible) {
			MutableComponent text = Component.translatable("gui.jaymoskingdoms.purse_icon").withStyle(ChatFormatting.GOLD);
			Map<Currency, Integer> held = Money.held(player);
			if (held.isEmpty()) {
				text.append("\n").append(Component.translatable("gui.jaymoskingdoms.purse_icon.empty").withStyle(ChatFormatting.GRAY));
			}
			for (Map.Entry<Currency, Integer> e : held.entrySet()) {
				text.append("\n").append(Component.translatable("gui.jaymoskingdoms.purse_icon.line", e.getValue(),
					Component.translatable(e.getKey().key())).withStyle(ChatFormatting.GRAY));
			}
			String now = text.getString();
			if (!now.equals(this.shown)) {
				this.shown = now;
				setTooltip(Tooltip.create(text));
			}
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.item(new ItemStack(MoneyItems.COIN_PURSE), getX() + 1, getY() + 1);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
