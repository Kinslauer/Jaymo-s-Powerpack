package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.Feats;
import com.jaymoskingdoms.Knowledge;
import com.jaymoskingdoms.Paths;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * The feats and paths screen ({@link Feats}, {@link Paths}), a tab of the H menu. Feats: one for each knowledge level reached, picked
 * from the list. Paths: for each class held (the first, and a second learned from a master), one path, picked once.
 */
final class GrowthScreen extends Screen {
	static final int W = 400, H = 240, LIST_W = 140, HEADER = 34, ROW = 16, FOOTER = 24;

	private boolean feats = true;
	private int scroll;
	private int pickedFeat = -1;
	private @Nullable PlayerClass pickedClass;
	private int pickedPath = -1;
	private Button take;
	private Button featsTab, pathsTab;

	GrowthScreen() {
		super(Component.translatable("menu.jaymoskingdoms.tab.growth"));
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(W, this.width - 12);
	}

	private int panelH() {
		return Math.min(H, this.height - 8);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	@Override
	protected void init() {
		int y = top() + panelH() - FOOTER + 4;
		this.featsTab = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.growth.feats"), b -> {
			this.feats = true;
			this.scroll = 0;
		}).bounds(left() + panelW() - 150, top() + 7, 68, 16).build());
		this.pathsTab = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.growth.paths"), b -> {
			this.feats = false;
			this.scroll = 0;
		}).bounds(left() + panelW() - 78, top() + 7, 68, 16).build());
		this.take = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.growth.take"), b -> confirm())
			.bounds(left() + panelW() - 130, y, 120, 16).build());
	}

	private void confirm() {
		if (this.feats && this.pickedFeat >= 0) {
			ClientPlayNetworking.send(new Feats.Pick(this.pickedFeat));
		} else if (!this.feats && this.pickedClass != null && this.pickedPath >= 0) {
			ClientPlayNetworking.send(new Paths.Pick(this.pickedClass.ordinal(), this.pickedPath));
		}
	}

	private int fits() {
		return Math.max(1, (panelH() - HEADER - FOOTER - 4) / ROW);
	}

	/** The rows of the left list: feats, or the classes held. */
	private int rows() {
		return this.feats ? Feats.Feat.values().length : Classes.both(this.minecraft.player).size();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < left() + LIST_W) {
			this.scroll = Math.max(0, Math.min(Math.max(0, rows() - fits()), this.scroll - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private int rowAt(double mx, double my) {
		for (int i = this.scroll; i < rows() && i < this.scroll + fits(); i++) {
			if (Ui.over((int) mx, (int) my, left() + 4, top() + HEADER + (i - this.scroll) * ROW, LIST_W - 8, ROW - 1)) {
				return i;
			}
		}
		return -1;
	}

	/** Where the path chips sit down the right pane (class picked): {x, y, w}. */
	private List<int[]> pathRows() {
		List<int[]> out = new ArrayList<>();
		if (this.pickedClass == null) {
			return out;
		}
		int x = left() + LIST_W + 9, y = top() + HEADER + 16;
		for (int i = 0; i < Paths.of(this.pickedClass).size(); i++) {
			out.add(new int[] {x, y + i * 17, panelW() - LIST_W - 18});
		}
		return out;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int row = rowAt(event.x(), event.y());
		if (row >= 0) {
			if (this.feats) {
				this.pickedFeat = row;
			} else {
				this.pickedClass = Classes.both(this.minecraft.player).get(row);
				this.pickedPath = -1;
			}
			click();
			return true;
		}
		if (!this.feats) {
			List<int[]> rows = pathRows();
			for (int i = 0; i < rows.size(); i++) {
				if (Ui.over((int) event.x(), (int) event.y(), rows.get(i)[0], rows.get(i)[1], rows.get(i)[2], 15)) {
					this.pickedPath = i;
					click();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void click() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		var player = this.minecraft.player;
		if (player == null) {
			return;
		}
		int x = left(), y = top(), w = panelW(), h = panelH(), px = x + LIST_W, pw = w - LIST_W;
		Ui.panel(graphics, x, y, w, h, Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + 8, y + 8, LIST_W - 12, Ui.TEXT);
		int owed = Feats.owed(player);
		Ui.line(graphics, this.font, this.feats ? Component.translatable("gui.jaymoskingdoms.growth.owed", owed, Knowledge.level(player))
			: Component.translatable("gui.jaymoskingdoms.growth.paths_sub"), x + 8, y + 19, LIST_W - 12, owed > 0 && this.feats ? Ui.GOOD : Ui.DIM);
		graphics.fill(x + LIST_W - 1, y + 4, x + LIST_W, y + h - 4, Ui.ROW);
		int hover = rowAt(mouseX, mouseY);
		List<PlayerClass> held = Classes.both(player);
		for (int i = this.scroll; i < rows() && i < this.scroll + fits(); i++) {
			int ry = y + HEADER + (i - this.scroll) * ROW;
			boolean picked = this.feats ? i == this.pickedFeat : held.get(i) == this.pickedClass;
			graphics.fill(x + 4, ry, x + LIST_W - 4, ry + ROW - 1, picked ? Ui.ROW_PICKED : i == hover ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW_ALT : 0x0CFFFFFF);
			if (this.feats) {
				Feats.Feat f = Feats.Feat.values()[i];
				boolean has = Feats.has(player, f);
				Ui.line(graphics, this.font, Component.translatable("feat.jaymoskingdoms." + f.key()), x + 9, ry + 4, LIST_W - 22, has ? Ui.GOOD : picked ? Ui.GOLD : Ui.TEXT);
				if (has) {
					graphics.text(this.font, "✓", x + LIST_W - 14, ry + 4, Ui.GOOD);
				}
			} else {
				PlayerClass c = held.get(i);
				Ui.line(graphics, this.font, Component.translatable("class.jaymoskingdoms." + c.key()), x + 9, ry + 4, LIST_W - 22, picked ? Ui.GOLD : Ui.TEXT);
			}
		}
		boolean ready = false;
		if (this.feats) {
			if (this.pickedFeat >= 0) {
				Feats.Feat f = Feats.Feat.values()[this.pickedFeat];
				Ui.detail(graphics, this.font, px, y + HEADER, pw, (h - HEADER - FOOTER - 8) / Ui.LINE, Component.translatable("feat.jaymoskingdoms." + f.key()), Ui.GOLD,
					List.of(Component.translatable("feat.jaymoskingdoms." + f.key() + ".desc")));
				ready = owed > 0 && !Feats.has(player, f);
			} else {
				Ui.detail(graphics, this.font, px, y + HEADER, pw, (h - HEADER - FOOTER - 8) / Ui.LINE, this.title, Ui.GOLD,
					List.of(Component.translatable("gui.jaymoskingdoms.growth.feats_help")));
			}
		} else if (this.pickedClass != null) {
			PlayerClass c = this.pickedClass;
			String have = Paths.chosen(player, c);
			Ui.line(graphics, this.font, Component.translatable(have.isEmpty() ? "gui.jaymoskingdoms.growth.path_for" : "gui.jaymoskingdoms.growth.path_is",
				Component.translatable("class.jaymoskingdoms." + c.key())), px + 9, y + HEADER + 2, pw - 18, Ui.DIM);
			List<int[]> rows = pathRows();
			List<String> paths = Paths.of(c);
			for (int i = 0; i < rows.size(); i++) {
				int[] r = rows.get(i);
				boolean mine = paths.get(i).equals(have), sel = i == this.pickedPath;
				graphics.fill(r[0], r[1], r[0] + r[2], r[1] + 15, sel ? Ui.ROW_PICKED : Ui.over(mouseX, mouseY, r[0], r[1], r[2], 15) ? Ui.ROW_HOVER : Ui.CHIP);
				Ui.line(graphics, this.font, Component.translatable("path.jaymoskingdoms." + c.key() + "." + paths.get(i)), r[0] + 5, r[1] + 4, r[2] - 10,
					mine ? Ui.GOOD : sel ? Ui.GOLD : Ui.TEXT);
			}
			int below = y + HEADER + 16 + rows.size() * 17 + 4;
			if (this.pickedPath >= 0) {
				String p = paths.get(this.pickedPath);
				Ui.detail(graphics, this.font, px, below, pw, Math.max(3, (y + h - FOOTER - below - 6) / Ui.LINE), Component.translatable("path.jaymoskingdoms." + c.key() + "." + p),
					Ui.GOLD, List.of(Component.translatable("path.jaymoskingdoms." + c.key() + "." + p + ".desc")));
				ready = have.isEmpty();
			}
		} else {
			Ui.detail(graphics, this.font, px, y + HEADER, pw, 5, this.title, Ui.GOLD, List.of(Component.translatable("gui.jaymoskingdoms.growth.paths_help")));
		}
		this.take.active = ready;
		this.take.setMessage(Component.translatable(this.feats ? "gui.jaymoskingdoms.growth.take" : "gui.jaymoskingdoms.growth.walk"));
		this.featsTab.active = !this.feats;
		this.pathsTab.active = this.feats;
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
