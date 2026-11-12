package com.jaymoskingdoms.client;

import com.jaymoskingdoms.TableTalk;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Sitting down with a companion ({@link TableTalk}, docs/Pawn-Plan.md row D4): they ask two or three things. Each row's
 * button turns through its answers; Done tells them, Leave it changes nothing.
 */
final class TableTalkScreen extends Screen {
	private static final int W = 270, ROW = 22;
	private static final String[] LEANINGS = {"biggest", "shooters", "weak", "guard", "medic", "first", "spoils", "backup"};

	private final TableTalk.OpenTable ask;
	private final List<String> seconds = new ArrayList<>();
	private final List<String> men = new ArrayList<>();
	private int second;
	private int chatter;
	private int style;
	private int man;
	private int lend;
	private boolean errand;

	TableTalkScreen(TableTalk.OpenTable ask) {
		super(Component.translatable("gui.jaymoskingdoms.table.title", ask.name()));
		this.ask = ask;
		// keep (their second as it is), then every other leaning but their main
		this.seconds.add(ask.second());
		for (String l : LEANINGS) {
			if (!l.equals(ask.main()) && !l.equals(ask.second())) {
				this.seconds.add(l);
			}
		}
		this.men.add("");
		for (String l : LEANINGS) {
			this.men.add(l);
		}
		this.man = Math.max(0, this.men.indexOf(ask.men()));
		this.chatter = ask.chatter();
		this.style = ask.style();
		this.lend = Math.max(0, ask.lend());
	}

	private boolean lends() {
		return this.ask.lend() >= 0;
	}

	private boolean errands() {
		return lends() && !this.ask.errand().getString().isEmpty();
	}

	/** The rows below the first three: his men's leaning, how they're lent, an errand. */
	private int rows() {
		return 3 + (this.ask.captain() ? 1 : 0) + (lends() ? 1 : 0) + (errands() ? 1 : 0);
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TableTalk.OpenTable.TYPE, (payload, context) -> context.client().gui.setScreen(new TableTalkScreen(payload)));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(null);
	}

	private int height() {
		return 64 + ROW * rows() + 28;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - height()) / 2;
	}

	private static Component leaning(String key) {
		return Component.translatable("gui.jaymoskingdoms.table.leaning." + key);
	}

	private Component secondLabel() {
		String s = this.seconds.get(this.second);
		return this.second == 0 ? Component.translatable("gui.jaymoskingdoms.table.keep", leaning(s)) : leaning(s);
	}

	private Component chatterLabel() {
		return Component.translatable("gui.jaymoskingdoms.table.chatter." + (this.chatter + 1));
	}

	private Component styleLabel() {
		return Component.translatable("gui.jaymoskingdoms.table.style." + this.style);
	}

	private Component lendLabel() {
		return Component.translatable("gui.jaymoskingdoms.table.lend." + this.lend);
	}

	private Component errandLabel() {
		return this.errand ? Component.translatable("gui.jaymoskingdoms.table.errand.yes", this.ask.errand())
			: Component.translatable("gui.jaymoskingdoms.table.errand.no");
	}

	private Component menLabel() {
		String m = this.men.get(this.man);
		return m.isEmpty() ? Component.translatable("gui.jaymoskingdoms.table.men.asis") : leaning(m);
	}

	@Override
	protected void init() {
		int x = left() + W - 120, y = top() + 58;
		addRenderableWidget(Button.builder(secondLabel(), b -> {
			this.second = (this.second + 1) % this.seconds.size();
			b.setMessage(secondLabel());
		}).bounds(x, y, 110, 16).build());
		addRenderableWidget(Button.builder(chatterLabel(), b -> {
			this.chatter = this.chatter >= 1 ? -1 : this.chatter + 1;
			b.setMessage(chatterLabel());
		}).bounds(x, y + ROW, 110, 16).build());
		addRenderableWidget(Button.builder(styleLabel(), b -> {
			this.style = (this.style + 1) % 3;
			b.setMessage(styleLabel());
		}).bounds(x, y + ROW * 2, 110, 16).build());
		int row = 3;
		if (this.ask.captain()) {
			addRenderableWidget(Button.builder(menLabel(), b -> {
				this.man = (this.man + 1) % this.men.size();
				b.setMessage(menLabel());
			}).bounds(x, y + ROW * row++, 110, 16).build());
		}
		if (lends()) {
			addRenderableWidget(Button.builder(lendLabel(), b -> {
				this.lend = (this.lend + 1) % 3;
				b.setMessage(lendLabel());
			}).bounds(x, y + ROW * row++, 110, 16).build());
		}
		if (errands()) {
			addRenderableWidget(Button.builder(errandLabel(), b -> {
				this.errand = !this.errand;
				b.setMessage(errandLabel());
			}).bounds(x, y + ROW * row, 110, 16).build());
		}
		int by = top() + height() - 24;
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.table.done"), b -> {
			ClientPlayNetworking.send(new TableTalk.SetTable(this.ask.entityId(), this.second == 0 ? "" : this.seconds.get(this.second), this.chatter,
				this.style, this.men.get(this.man), lends() ? this.lend : -1, this.errand));
			onClose();
		}).bounds(left() + 10, by, 90, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.table.leave"), b -> onClose()).bounds(left() + W - 100, by, 90, 16).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, W, height(), Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + 10, y + 8, W - 20, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.table.main", leaning(this.ask.main())), x + 10, y + 22, W - 20, Ui.DIM);
		int bond = Math.max(0, Math.min(100, this.ask.bond()));
		Ui.line(graphics, this.font, Component.translatable(bond >= 60 ? "gui.jaymoskingdoms.table.bond.close" : bond >= 25
			? "gui.jaymoskingdoms.table.bond.warm" : "gui.jaymoskingdoms.table.bond.new"), x + 10, y + 34, 120, Ui.TEXT);
		Ui.bar(graphics, x + 130, y + 36, W - 140, 4, bond / 100.0F, Ui.GOOD);
		int ry = y + 62;
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.table.ask.second"), x + 10, ry, W - 140, Ui.TEXT);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.table.ask.chatter"), x + 10, ry + ROW, W - 140, Ui.TEXT);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.table.ask.style"), x + 10, ry + ROW * 2, W - 140, Ui.TEXT);
		int row = 3;
		if (this.ask.captain()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.table.ask.men"), x + 10, ry + ROW * row++, W - 140, Ui.TEXT);
		}
		if (lends()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.table.ask.lend"), x + 10, ry + ROW * row++, W - 140, Ui.TEXT);
		}
		if (errands()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.table.ask.errand"), x + 10, ry + ROW * row, W - 140, Ui.TEXT);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
