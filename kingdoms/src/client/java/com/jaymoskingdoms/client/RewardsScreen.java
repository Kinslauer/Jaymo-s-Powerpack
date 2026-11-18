package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Rewards;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The H menu's Rewards page (Notices): the rewards waiting for you (a bounty's pay, a job's wages, what a rescue brought),
 * each with a Claim button, and Claim all under them. What is held stays held until it is claimed.
 */
final class RewardsScreen extends Screen {
	private static final int W = 280, H = 196, PAD = 10, ROW = 26, HEAD = 26, FOOT = 26, BUTTON_W = 46, BUTTON_H = 14;
	private int scroll;

	RewardsScreen() {
		super(Component.translatable("menu.jaymoskingdoms.tab.rewards"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return MenuCompat.centre(this.width, W);
	}

	private int top() {
		return Math.max(MenuCompat.STRIP + 4, (this.height - H) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	private List<Rewards.Held> held() {
		return this.minecraft.player == null ? List.of() : Rewards.of(this.minecraft.player);
	}

	private int listTop() {
		return top() + HEAD;
	}

	private int listHeight() {
		return H - HEAD - FOOT;
	}

	private int rowY(int index) {
		return listTop() + index * ROW - this.scroll;
	}

	private int buttonX() {
		return left() + W - PAD - 8 - BUTTON_W;
	}

	private int allX() {
		return left() + W - PAD - 90;
	}

	private int allY() {
		return top() + H - FOOT + 4;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll -= (int)Math.signum(scrollY) * ROW;
		return true;
	}

	private void claim(int index) {
		ClientPlayNetworking.send(new Rewards.Claim(index));
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int)event.x(), my = (int)event.y();
		List<Rewards.Held> held = held();
		if (!held.isEmpty() && Ui.over(mx, my, allX(), allY(), 90, 16)) {
			claim(-1);
			return true;
		}
		if (Ui.over(mx, my, left() + PAD, listTop(), W - 2 * PAD, listHeight())) {
			for (int i = 0; i < held.size(); i++) {
				if (Ui.over(mx, my, buttonX(), rowY(i) + 6, BUTTON_W, BUTTON_H)) {
					claim(i);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** "3 x Realm Silver, 2 x Copper Bit". */
	private static Component parts(Rewards.Held h) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (Rewards.Part part : h.parts()) {
			ItemStack stack = part.stack();
			if (!stack.isEmpty()) {
				out.append(Component.literal(first ? "" : ", ")).append(Component.literal(part.count() + " x ")).append(stack.getHoverName());
				first = false;
			}
		}
		return out;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = W - 2 * PAD;
		Ui.panel(graphics, x, y, W, H, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("menu.jaymoskingdoms.tab.rewards"), x + PAD, y + 8, w, Ui.GOLD);
		Ui.rule(graphics, x + 6, x + W - 6, y + HEAD - 5);
		List<Rewards.Held> held = held();
		int total = held.size() * ROW;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, total - listHeight()));
		if (held.isEmpty()) {
			Ui.wrapped(graphics, this.font, Component.translatable("gui.jaymoskingdoms.rewards.none"), x + PAD, listTop() + 4, w, 4, Ui.LINE, Ui.DIM, false);
		}
		graphics.enableScissor(x + PAD - 2, listTop(), x + W - PAD + 2, listTop() + listHeight());
		for (int i = 0; i < held.size(); i++) {
			int ry = rowY(i);
			if (ry + ROW <= listTop() || ry >= listTop() + listHeight()) {
				continue;
			}
			Rewards.Held h = held.get(i);
			Ui.row(graphics, x + PAD, ry, x + W - PAD - 6, ry + ROW - 2, i, false);
			ItemStack icon = h.parts().isEmpty() ? ItemStack.EMPTY : h.parts().getFirst().stack();
			if (!icon.isEmpty()) {
				graphics.item(icon, x + PAD + 3, ry + 4);
			}
			int tx = x + PAD + 24, tw = buttonX() - tx - 6;
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.rewards.from", Component.translatable("rewards.jaymoskingdoms.source." + h.source()), h.from()),
				tx, ry + 4, tw, Ui.TEXT);
			Ui.line(graphics, this.font, parts(h), tx, ry + 14, tw, Ui.DIM);
			boolean over = Ui.over(mouseX, mouseY, buttonX(), ry + 6, BUTTON_W, BUTTON_H) && mouseY >= listTop() && mouseY < listTop() + listHeight();
			Ui.button(graphics, this.font, buttonX(), ry + 6, BUTTON_W, BUTTON_H, Component.translatable("gui.jaymoskingdoms.rewards.claim"), Ui.GOOD, over, true);
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, x + W - PAD - 2, listTop(), listHeight(), listHeight(), total, this.scroll);
		Ui.button(graphics, this.font, allX(), allY(), 90, 16, Component.translatable("gui.jaymoskingdoms.rewards.all"), Ui.GOOD,
			!held.isEmpty() && Ui.over(mouseX, mouseY, allX(), allY(), 90, 16), !held.isEmpty());
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
