package com.jaymoskitchen.client;

import com.jaymoskitchen.net.Payloads;
import com.jaymoskitchen.net.Payloads.OpenButcher;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.4.30) A butcher villager sizes up the body you brought: what he charges, how good he is (up to three times what
 * you'd get yourself) if you've heard tell of him, and whether you hand it over.
 */
public class ButcherScreen extends Screen {
	private static final int PANEL_W = 260;
	private static final int PANEL_H = 150;
	private static final int BUTTON_H = 20;
	private static final int TIERS = 5;

	private static final int COLOR_PANEL = 0xE0201812;
	private static final int COLOR_BORDER = 0xFF8B5A2B;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA0A0A0;
	private static final int COLOR_TITLE = 0xFFFFDD55;
	private static final int COLOR_GOOD = 0xFF55FF55;
	private static final int COLOR_BAD = 0xFFFF5555;
	private static final int COLOR_PIP = 0xFFC0392B;
	private static final int COLOR_PIP_EMPTY = 0xFF3A2A20;

	private final OpenButcher offer;

	public ButcherScreen(OpenButcher offer) {
		super(offer.name());
		this.offer = offer;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return (this.height - PANEL_H) / 2;
	}

	private boolean canPay() {
		if (this.minecraft.player == null) {
			return false;
		}
		return this.minecraft.player.hasInfiniteMaterials() || ContainerHelper.clearOrCountMatchingItems(
			this.minecraft.player.getInventory(), s -> s.is(Items.EMERALD), offer.price(), true) >= offer.price();
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int by = top() + PANEL_H - 28;
		Button deal = Button.builder(Component.translatable("screen.jaymoskitchen.butcher.deal", offer.price()), b -> {
				ClientPlayNetworking.send(new Payloads.ButcherDeal(offer.villager()));
				onClose();
			})
			.bounds(cx - 115, by, 110, BUTTON_H).build();
		deal.active = canPay();
		addRenderableWidget(deal);
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.butcher.keep"), b -> onClose())
			.bounds(cx + 5, by, 110, BUTTON_H).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		int cx = this.width / 2;
		graphics.fill(x - 2, y - 2, x + PANEL_W + 2, y + PANEL_H + 2, COLOR_BORDER);
		graphics.fill(x, y, x + PANEL_W, y + PANEL_H, COLOR_PANEL);
		graphics.centeredText(this.font, this.title, cx, y + 8, COLOR_TITLE);

		super.extractRenderState(graphics, mouseX, mouseY, a);

		// how good they are: only if you've heard (gossip) or seen their work; otherwise every butcher swears they're the best
		boolean known = offer.tier() >= 0;
		Component tier = Component.translatable(known ? "screen.jaymoskitchen.butcher.tier." + offer.tier() : "screen.jaymoskitchen.butcher.tier.unknown");
		graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.butcher.skill", tier), cx, y + 22, known ? COLOR_TEXT : COLOR_DIM);
		int pipW = 14, pipGap = 3, pipsX = cx - (TIERS * pipW + (TIERS - 1) * pipGap) / 2;
		for (int i = 0; i < TIERS; i++) {
			int px = pipsX + i * (pipW + pipGap);
			graphics.fill(px, y + 34, px + pipW, y + 38, known && i <= offer.tier() ? COLOR_PIP : COLOR_PIP_EMPTY);
		}
		if (known) {
			graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.butcher.yield",
				String.format(Locale.ROOT, "%.1f", offer.skill())), cx, y + 46, COLOR_GOOD);
			graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.butcher.flavour." + offer.tier()), cx, y + 60, COLOR_DIM);
		} else {
			graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.butcher.unheard"), cx, y + 46, COLOR_DIM);
			graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.butcher.boast"), cx, y + 60, COLOR_DIM);
		}
		graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.butcher.body", offer.what()), cx, y + 78, COLOR_TEXT);

		// the price, with an emerald, in red if you can't cover it
		boolean pay = canPay();
		Component price = Component.translatable("screen.jaymoskitchen.butcher.price", offer.price());
		int pw = this.font.width(price);
		int px = cx - (pw + 20) / 2;
		graphics.item(new ItemStack(Items.EMERALD), px, y + 90);
		graphics.text(this.font, price, px + 20, y + 94, pay ? COLOR_TEXT : COLOR_BAD);
		if (!pay) {
			graphics.centeredText(this.font, Component.translatable("screen.jaymoskitchen.butcher.short"), cx, y + 108, COLOR_BAD);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
