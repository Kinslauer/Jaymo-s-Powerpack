package com.jaymoskitchen.client.climate;

import com.jaymoskitchen.client.KitchenUi;
import com.jaymoskitchen.climate.Warmth;
import com.jaymoskitchen.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The hearth's and the icebox's screen (Andy's TFC Hardrock picks: they track fuel, so the screen shows it): a panel in the
 * Kitchen's style with a fuel bar, how much air it fills, and an Add fuel button that feeds it one thing from the pockets.
 * The bar is a bar, never a count of days left.
 */
public class HearthScreen extends Screen {
	private static final int PANEL_W = 200, PANEL_H = 112, BUTTON_H = 20;
	private static final int FIRE = 0xFFE07830, ICE = 0xFF70C8E8;

	private final Payloads.OpenHearth data;

	public HearthScreen(Payloads.OpenHearth data) {
		super(Component.translatable(data.cold() ? "block.jaymoskitchen.icebox" : "block.jaymoskitchen.hearth"));
		this.data = data;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return (this.height - PANEL_H) / 2;
	}

	@Override
	protected void init() {
		int by = top() + PANEL_H - 28;
		Button feed = Button.builder(Component.translatable(data.cold() ? "screen.jaymoskitchen.hearth.add_cold" : "screen.jaymoskitchen.hearth.add"), b -> {
				ClientPlayNetworking.send(new Payloads.FeedHearth(data.pos()));
				b.active = false;
			})
			.bounds(left() + 8, by, PANEL_W / 2 - 12, BUTTON_H).build();
		feed.active = data.canFeed() && data.fuel() < data.most();
		addRenderableWidget(feed);
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskitchen.hearth.done"), b -> onClose())
			.bounds(left() + PANEL_W / 2 + 4, by, PANEL_W / 2 - 12, BUTTON_H).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		int accent = data.cold() ? ICE : FIRE;
		KitchenUi.panel(graphics, x, y, PANEL_W, PANEL_H, accent);
		Component state = Component.translatable(data.lit() ? (data.cold() ? "screen.jaymoskitchen.hearth.cooling" : "screen.jaymoskitchen.hearth.burning")
			: "screen.jaymoskitchen.hearth.out");
		KitchenUi.header(graphics, this.font, x, y, PANEL_W, new ItemStack(data.cold() ? Warmth.ICEBOX_BLOCK : Warmth.HEARTH_BLOCK), this.title, state,
			accent, x + PANEL_W - 9);
		int ry = y + KitchenUi.HEADER;
		KitchenUi.row(graphics, x + 6, ry, x + PANEL_W - 6, ry + 26, 0, false);
		KitchenUi.line(graphics, this.font, Component.translatable("screen.jaymoskitchen.hearth.fuel"), x + 9, ry + 3, PANEL_W - 18, KitchenUi.TEXT);
		float share = data.most() <= 0 ? 0.0F : (float) data.fuel() / data.most();
		KitchenUi.bar(graphics, x + 9, ry + 14, PANEL_W - 18, 7, share, share > 0.2F ? accent : KitchenUi.BAD);
		ry += 30;
		KitchenUi.row(graphics, x + 6, ry, x + PANEL_W - 6, ry + 15, 1, false);
		KitchenUi.line(graphics, this.font, Component.translatable(data.cold() ? "screen.jaymoskitchen.hearth.cool_air" : "screen.jaymoskitchen.hearth.warm_air",
			data.cells()), x + 9, ry + 4, PANEL_W - 18, KitchenUi.DIM);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
