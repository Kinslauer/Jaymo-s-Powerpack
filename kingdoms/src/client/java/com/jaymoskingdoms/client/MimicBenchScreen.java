package com.jaymoskingdoms.client;

import com.jaymoskingdoms.client.ui.FlatButton;
import com.jaymoskingdoms.tinker.Mimic;
import com.jaymoskingdoms.tinker.MimicBench;
import com.jaymoskingdoms.tinker.MimicGadgets;
import com.jaymoskingdoms.tinker.MimicStudy;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The Tinkerer's notebook ({@link MimicBench#NOTEBOOK}, the class key) and the Mad Tinkerer's gadget trade ({@link MimicBench#SHOP}):
 * a row for every ability that can be copied ({@link Mimic}), with its gadget, where it is seen, how far it is studied (a bar) or, once
 * learned, the parts a building takes. The notebook builds at a bench; the Mad Tinkerer sells the gadget or teaches the drawing.
 */
final class MimicBenchScreen extends Screen {
	private static final int W = 330, H = 236, PAD = 10, ROW_H = 26, TOP = 38, ROWS = 7, BUTTON_W = 54;
	private MimicBench.Open data;
	private int scroll;

	private MimicBenchScreen(MimicBench.Open data) {
		super(Component.translatable(data.mode() == MimicBench.SHOP ? "gui.jaymoskingdoms.mimic.shop" : "gui.jaymoskingdoms.mimic.notebook"));
		this.data = data;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MimicBench.Open.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof MimicBenchScreen open && open.data.mode() == payload.mode()) {
				// a press answered: the same page, brought up to date
				open.data = payload;
				open.rebuildWidgets();
			} else {
				context.client().gui.setScreen(new MimicBenchScreen(payload));
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - H) / 2;
	}

	private boolean shop() {
		return this.data.mode() == MimicBench.SHOP;
	}

	private boolean known(int i) {
		return i < this.data.progress().length && this.data.progress()[i] >= MimicStudy.STUDY_TICKS;
	}

	@Override
	protected void init() {
		Mimic[] all = Mimic.values();
		int x = left(), y = top() + TOP;
		for (int r = 0; r < ROWS && this.scroll + r < all.length; r++) {
			int i = this.scroll + r;
			Mimic m = all[i];
			int ry = y + r * ROW_H + (ROW_H - 16) / 2;
			int bx = x + W - PAD - 6 - BUTTON_W;
			if (shop()) {
				FlatButton learn = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mimic.learn"),
					b -> ClientPlayNetworking.send(new MimicBench.Act(MimicBench.LEARN, m.key(), this.data.npc()))).bounds(bx, ry, BUTTON_W, 16).build();
				learn.active = !known(i) && this.data.canLearn();
				learn.setTooltip(Ui.tipOf(known(i) ? Component.translatable("gui.jaymoskingdoms.mimic.learn.known")
					: Component.translatable("gui.jaymoskingdoms.mimic.learn.tip", this.data.learnPrice())));
				addRenderableWidget(learn);
				FlatButton buy = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mimic.buy"),
					b -> ClientPlayNetworking.send(new MimicBench.Act(MimicBench.BUY, m.key(), this.data.npc()))).bounds(bx - BUTTON_W - 4, ry, BUTTON_W, 16).build();
				buy.active = this.data.canBuy();
				buy.setTooltip(Ui.tipOf(Component.translatable("gui.jaymoskingdoms.mimic.buy.tip", this.data.buyPrice())));
				addRenderableWidget(buy);
			} else {
				FlatButton build = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mimic.build"),
					b -> ClientPlayNetworking.send(new MimicBench.Act(MimicBench.BUILD, m.key(), -1))).bounds(bx, ry, BUTTON_W, 16).build();
				boolean parts = i < this.data.parts().length && this.data.parts()[i];
				build.active = known(i) && parts && this.data.bench();
				build.setTooltip(Ui.tipOf(Component.translatable(!known(i) ? "gui.jaymoskingdoms.mimic.build.unknown"
					: !this.data.bench() ? "gui.jaymoskingdoms.mimic.build.bench" : !parts ? "gui.jaymoskingdoms.mimic.build.parts" : "gui.jaymoskingdoms.mimic.build.tip")));
				addRenderableWidget(build);
			}
		}
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(x + (W - 80) / 2, top() + H - 22, 80, 16).build());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int most = Math.max(0, Mimic.values().length - ROWS);
		int was = this.scroll;
		this.scroll = Math.max(0, Math.min(most, this.scroll - (int)Math.signum(scrollY)));
		if (was != this.scroll) {
			rebuildWidgets();
		}
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(g, x, y, W, H, Ui.GOLD);
		Ui.line(g, this.font, this.title, x + PAD, y + 8, W - PAD * 2, Ui.GOLD);
		Component sub = shop() ? Component.translatable("gui.jaymoskingdoms.mimic.shop.sub", this.data.buyPrice(), this.data.learnPrice())
			: this.data.bench() ? this.data.infusions() : Component.translatable("gui.jaymoskingdoms.mimic.notebook.sub", this.data.infusions());
		Ui.line(g, this.font, sub, x + PAD, y + 20, W - PAD * 2, Ui.DIM);
		Ui.rule(g, x + 6, x + W - 6, y + 32);
		Mimic[] all = Mimic.values();
		List<Component> tip = new ArrayList<>();
		for (int r = 0; r < ROWS && this.scroll + r < all.length; r++) {
			int i = this.scroll + r;
			Mimic m = all[i];
			int ry = y + TOP + r * ROW_H;
			boolean hover = Ui.over(mouseX, mouseY, x + PAD, ry, W - PAD * 2 - BUTTON_W * 2 - 16, ROW_H);
			Ui.row(g, x + PAD - 2, ry, x + W - PAD + 2, ry + ROW_H - 2, i, hover);
			ItemStack gadget = new ItemStack(MimicGadgets.ITEMS.get(m), m.makes);
			Ui.itemWell(g, this.font, gadget, x + PAD + 3, ry + 4);
			int tx = x + PAD + 26, textW = W - PAD * 2 - 26 - (shop() ? BUTTON_W * 2 + 14 : BUTTON_W + 10);
			Ui.line(g, this.font, gadget.getHoverName(), tx, ry + 3, textW, known(i) ? Ui.TEXT : Ui.DIM);
			if (known(i) && !shop()) {
				// the parts a building takes
				int px = tx;
				for (Mimic.Part part : m.cost) {
					ItemStack stack = new ItemStack(part.item(), part.count());
					g.item(stack, px, ry + 12);
					g.itemDecorations(this.font, stack, px, ry + 12, null);
					if (Ui.over(mouseX, mouseY, px, ry + 12, 16, 12)) {
						tip.clear();
						tip.add(stack.getHoverName());
					}
					px += 20;
				}
			} else if (known(i)) {
				Ui.line(g, this.font, Component.translatable("gui.jaymoskingdoms.mimic.known"), tx, ry + 14, textW, Ui.GOOD);
			} else {
				float share = i < this.data.progress().length ? this.data.progress()[i] / (float)MimicStudy.STUDY_TICKS : 0.0F;
				Ui.bar(g, tx, ry + 16, Math.min(textW, 90), 4, share, Ui.SURVIVAL);
				Ui.line(g, this.font, Component.translatable("mimic.jaymoskingdoms." + m.key() + ".seen"), tx + Math.min(textW, 90) + 6, ry + 14,
					textW - Math.min(textW, 90) - 6, Ui.HINT);
			}
			if (hover && tip.isEmpty()) {
				tip.add(gadget.getHoverName());
				tip.add(Component.translatable("gui.jaymoskingdoms.mimic.copies", Component.translatable("mimic.jaymoskingdoms." + m.key())));
				tip.add(Component.translatable("tooltip.jaymoskingdoms.mimic." + m.key()));
				tip.add(Component.translatable("gui.jaymoskingdoms.mimic.seen_in", Component.translatable("mimic.jaymoskingdoms." + m.key() + ".seen")));
			}
		}
		Ui.scrollbar(g, x + W - 5, y + TOP, ROWS * ROW_H, ROWS, all.length, this.scroll);
		super.extractRenderState(g, mouseX, mouseY, a);
		if (!tip.isEmpty()) {
			Ui.tip(g, this.font, tip, mouseX, mouseY);
		}
	}
}
