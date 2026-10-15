package com.jaymoskitchen.client;

import com.jaymoskitchen.net.Payloads;
import com.jaymoskitchen.net.Payloads.OpenButcher;
import java.util.List;
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
 * (0.4.32) A butcher villager sizes up the body you brought: what he charges, how good he is (up to three times what
 * you'd get yourself) if you've heard tell of him, and whether you hand it over. (0.4.40) Restyled like the H screen:
 * a dark panel with the Kitchen's stripe, a header with his skill as pips, striped rows, and what he says underneath.
 */
public class ButcherScreen extends Screen {
	private static final int PANEL_W = 260;
	private static final int PANEL_H = 150;
	private static final int BUTTON_H = 20;
	private static final int TIERS = 5;

	/** Height of one of the striped rows (what he'd get, the body, the price). */
	private static final int ROW_H = 18;

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
		KitchenUi.panel(graphics, x, y, PANEL_W, PANEL_H, KitchenUi.ACCENT);

		// how good they are: only if you've heard (gossip) or seen their work; otherwise every butcher swears they're the best
		boolean known = offer.tier() >= 0;
		Component tier = Component.translatable(known ? "screen.jaymoskitchen.butcher.tier." + offer.tier() : "screen.jaymoskitchen.butcher.tier.unknown");
		int pipW = 12, pipGap = 3, pipsW = TIERS * pipW + (TIERS - 1) * pipGap, pipsX = x + PANEL_W - 9 - pipsW;
		KitchenUi.header(graphics, this.font, x, y, PANEL_W, new ItemStack(Items.SMOKER), this.title,
			Component.translatable("screen.jaymoskitchen.butcher.skill", tier), KitchenUi.ACCENT, pipsX - 6);
		for (int i = 0; i < TIERS; i++) {
			int px = pipsX + i * (pipW + pipGap);
			graphics.fill(px, y + 21, px + pipW, y + 25, known && i <= offer.tier() ? KitchenUi.ACCENT : KitchenUi.BAR_BACK);
		}

		int ry = y + KitchenUi.HEADER;
		int textW = PANEL_W - 18;
		// what he'd get out of it, the body, and the price (with an emerald, in red if you can't cover it)
		KitchenUi.row(graphics, x + 6, ry, x + PANEL_W - 6, ry + ROW_H - 1, 0, false);
		KitchenUi.line(graphics, this.font, known ? Component.translatable("screen.jaymoskitchen.butcher.yield",
				String.format(Locale.ROOT, "%.1f", offer.skill())) : Component.translatable("screen.jaymoskitchen.butcher.unheard"),
			x + 9, ry + 5, textW, known ? KitchenUi.GOOD : KitchenUi.DIM);
		ry += ROW_H;
		KitchenUi.row(graphics, x + 6, ry, x + PANEL_W - 6, ry + ROW_H - 1, 1, false);
		KitchenUi.line(graphics, this.font, Component.translatable("screen.jaymoskitchen.butcher.body", offer.what()), x + 9, ry + 5,
			textW, KitchenUi.TEXT);
		ry += ROW_H;
		boolean pay = canPay();
		KitchenUi.row(graphics, x + 6, ry, x + PANEL_W - 6, ry + ROW_H - 1, 0, false);
		graphics.item(new ItemStack(Items.EMERALD), x + 8, ry + 1);
		Component price = Component.translatable("screen.jaymoskitchen.butcher.price", offer.price());
		KitchenUi.line(graphics, this.font, price, x + 28, ry + 5, textW - 19, pay ? KitchenUi.TEXT : KitchenUi.BAD);
		if (!pay) {
			int used = 28 + this.font.width(price) + 8;
			KitchenUi.lineRight(graphics, this.font, Component.translatable("screen.jaymoskitchen.butcher.short"), x + PANEL_W - 9, ry + 5,
				PANEL_W - 9 - used, KitchenUi.BAD);
		}
		ry += ROW_H + 3;

		// what he says about himself, in full underneath
		KitchenUi.detail(graphics, this.font, x, ry, PANEL_W, 2, null, KitchenUi.DIM, List.of(Component.translatable(
			known ? "screen.jaymoskitchen.butcher.flavour." + offer.tier() : "screen.jaymoskitchen.butcher.boast")));

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
