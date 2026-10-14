package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterWishes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Eternal Dragon asks for your wish: six buttons, each with what it grants under it. Escape doesn't close it; the
 * dragon waits until you wish (or until it tires of waiting, and the orbs scatter all the same).
 */
public class ZFighterWishScreen extends Screen {
	private static final int W = 280;
	private static final int H = 190;
	private static final int ACCENT = 0xFF3CC85A;

	public ZFighterWishScreen() {
		super(Component.translatable("screen.jaymospowers.zfighter.wish"));
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterNet.OpenWish.TYPE, (payload, context) -> context.client().gui.setScreen(new ZFighterWishScreen()));
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - H) / 2;
	}

	@Override
	protected void init() {
		for (int i = 0; i < ZFighterWishes.WISHES; i++) {
			int wish = i;
			String key = "wish.jaymospowers.zfighter." + ZFighterWishes.KEYS[i];
			addRenderableWidget(Button.builder(Component.translatable(key), b -> {
				ClientPlayNetworking.send(new ZFighterNet.Wish(wish));
				this.onClose();
			}).bounds(left() + 14 + (i % 2) * 130, top() + 44 + (i / 2) * 44, 122, 20)
				.tooltip(Tooltip.create(Component.translatable(key + ".desc"))).build());
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
		graphics.fill(x, y, x + W, y + H, 0xD0081008);
		graphics.fill(x, y, x + W, y + 2, ACCENT);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 8, ACCENT);
		var lines = this.font.split(Component.translatable("screen.jaymospowers.zfighter.wish_help"), W - 20);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			graphics.centeredText(this.font, lines.get(i), this.width / 2, y + 20 + i * 9, 0xFFA8B8A8);
		}
		for (int i = 0; i < ZFighterWishes.WISHES; i++) {
			var desc = this.font.split(Component.translatable("wish.jaymospowers.zfighter." + ZFighterWishes.KEYS[i] + ".short"), 122);
			if (!desc.isEmpty()) {
				graphics.text(this.font, desc.get(0), x + 14 + (i % 2) * 130, y + 67 + (i / 2) * 44, 0xFF90A090);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
