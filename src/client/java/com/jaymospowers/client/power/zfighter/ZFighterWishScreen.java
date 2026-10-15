package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterWishes;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The Eternal Dragon asks for your wish: six wishes, each with what it grants. Escape doesn't close it; the dragon
 * waits until you wish (or until it tires of waiting, and the orbs scatter all the same). (0.10.x) Restyled like the H
 * screen: a striped row per wish (its name, what it grants in short, a Wish button) and the box at the bottom shows the
 * hovered wish in full, where the tooltips were.
 */
public class ZFighterWishScreen extends Screen {
	private static final int MAX_W = 300;
	private static final int ROW = 18;
	private static final int DETAIL_LINES = 4;
	private static final int BUTTON_W = 50;
	private static final int ACCENT = 0xFF3CC85A;

	public ZFighterWishScreen() {
		super(Component.translatable("screen.jaymospowers.zfighter.wish"));
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterNet.OpenWish.TYPE, (payload, context) -> context.client().gui.setScreen(new ZFighterWishScreen()));
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int panelH() {
		return UiStyle.HEADER + ZFighterWishes.WISHES * ROW + 6 + UiStyle.detailHeight(DETAIL_LINES);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int rowY(int i) {
		return top() + UiStyle.HEADER + i * ROW;
	}

	private static String key(int i) {
		return "wish.jaymospowers.zfighter." + ZFighterWishes.KEYS[i];
	}

	@Override
	protected void init() {
		for (int i = 0; i < ZFighterWishes.WISHES; i++) {
			int wish = i;
			addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter.wish_button"), b -> {
				ClientPlayNetworking.send(new ZFighterNet.Wish(wish));
				this.onClose();
			}).bounds(left() + panelW() - BUTTON_W - 8, rowY(i) + 1, BUTTON_W, 14).build());
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		int w = panelW();
		UiStyle.panel(graphics, x, y, w, panelH(), ACCENT);
		UiStyle.header(graphics, this.font, x, y, w, ZFighterWishes.ORB[3] == null ? null : new ItemStack(ZFighterWishes.ORB[3]), this.title,
			Component.translatable("screen.jaymospowers.zfighter.wish_sub"), ACCENT, 0);
		int hovered = -1;
		int nameW = 0;
		for (int i = 0; i < ZFighterWishes.WISHES; i++) {
			nameW = Math.max(nameW, this.font.width(Component.translatable(key(i))));
		}
		nameW = Math.min(110, nameW);
		int textX = x + 12 + nameW + 8;
		int textW = x + w - BUTTON_W - 14 - textX;
		for (int i = 0; i < ZFighterWishes.WISHES; i++) {
			int ry = rowY(i);
			boolean hover = UiStyle.over(mouseX, mouseY, x + 6, ry, w - 12, ROW - 2);
			if (hover) {
				hovered = i;
			}
			UiStyle.row(graphics, x + 6, ry, x + w - 6, ry + ROW - 2, i, hover);
			UiStyle.line(graphics, this.font, Component.translatable(key(i)), x + 12, ry + 4, nameW, UiStyle.TEXT);
			UiStyle.line(graphics, this.font, Component.translatable(key(i) + ".short"), textX, ry + 4, textW, UiStyle.DIM);
		}
		int dy = y + panelH() - UiStyle.detailHeight(DETAIL_LINES);
		if (hovered >= 0) {
			UiStyle.detail(graphics, this.font, x, dy, w, DETAIL_LINES, Component.translatable(key(hovered)), ACCENT,
				List.of(Component.translatable(key(hovered) + ".desc")));
		} else {
			UiStyle.detail(graphics, this.font, x, dy, w, DETAIL_LINES, null, ACCENT,
				List.of(Component.translatable("screen.jaymospowers.zfighter.wish_help")));
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
