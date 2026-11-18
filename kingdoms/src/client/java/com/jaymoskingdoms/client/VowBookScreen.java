package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.VowBook;
import com.jaymoskingdoms.client.ui.FlatButton;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * A Paladin's or Warlock's book of vows ({@link VowBook}). Top: the vow's own terms (fixed) and the terms sworn of your own
 * (click one to change it to another, right click to strike it out; "Swear a term" adds one). Below: one rule line, and one
 * more for each sworn term. Click a part of a rule to turn it to the next choice (right click: the one before); "Pick" marks
 * the rule a sneak and use of the book enacts. Write sends it all; striking out a term already sworn breaks it.
 */
final class VowBookScreen extends Screen {
	private static final int ACCENT = 0xFFE8C66A, STRUCK = 0xFFE05050;
	private static final int W = 380, PAD = 8, ROW = 30, TERM_W = 118, TERM_GAP = 4;
	private static final int[] COL_X = {6, 110, 220, 270, 320}, COL_W = {100, 106, 46, 46, 34};

	/** The rule picked, kept while the game runs (the server is told on each Write). */
	private static int picked;

	private final Player player;
	private final boolean oath;
	private final Component vowName;
	private final List<String> own;
	private final List<String> swearable;
	private final List<String> wasSworn;
	private final List<String> sworn;
	private final List<Integer> allowed;
	private final List<int[]> rules = new ArrayList<>();
	private final int strongest;

	private VowBookScreen(Player player) {
		super(Component.translatable("item.jaymoskingdoms.vow_book"));
		this.player = player;
		this.oath = VowBook.vowClass(player) == PlayerClass.PALADIN;
		String vow = VowBook.vow(player);
		Patron patron = Patron.byKey(vow);
		this.vowName = this.oath ? Component.translatable("option.jaymoskingdoms.oath." + vow) : patron != null ? patron.named() : Component.literal(vow);
		this.own = List.copyOf(VowBook.ownTerms(player));
		this.swearable = VowBook.swearable(player);
		this.wasSworn = List.copyOf(VowBook.sworn(player));
		this.sworn = new ArrayList<>(this.wasSworn);
		this.allowed = VowBook.allowed(player);
		for (int r : VowBook.written(player)) {
			this.rules.add(new int[] {VowBook.who(r), Math.max(0, this.allowed.indexOf(VowBook.what(r))), VowBook.strength(r), VowBook.length(r)});
		}
		this.strongest = VowBook.strongest(player);
	}

	static void register() {
		VowBook.open = player -> Minecraft.getInstance().gui.setScreen(new VowBookScreen(player));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int lines() {
		return Math.min(VowBook.MOST, 1 + this.sworn.size());
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int termsY() {
		return top() + 34 + ownLines() * 10 + 4;
	}

	private int rulesY() {
		return termsY() + Ui.BUTTON_H + 22;
	}

	private int panelH() {
		return 34 + ownLines() * 10 + 4 + Ui.BUTTON_H + 22 + VowBook.MOST * ROW + 30;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int ownLines() {
		return Math.max(1, this.font == null ? 1 : this.font.split(ownText(), W - PAD * 2).size());
	}

	private Component ownText() {
		List<String> parts = new ArrayList<>();
		for (String t : this.own) {
			parts.add(Component.translatable("message.jaymoskingdoms.vow.term." + t).getString());
		}
		return Component.translatable("screen.jaymoskingdoms.vow_book.terms", String.join("; no ", parts));
	}

	@Override
	protected void init() {
		this.clearWidgets();
		int x = left();
		// the sworn terms: click to change, right click (mouseClicked) to strike out
		int ty = termsY();
		for (int i = 0; i < this.sworn.size(); i++) {
			int at = i;
			addRenderableWidget(FlatButton.builder(term(this.sworn.get(i)), b -> {
				this.sworn.set(at, nextTerm(this.sworn.get(at), 1));
				this.init();
			}).bounds(x + PAD + i * (TERM_W + TERM_GAP), ty, TERM_W, Ui.BUTTON_H).build());
		}
		if (this.sworn.size() < VowBook.MOST - 1 && this.sworn.size() < this.swearable.size()) {
			addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymoskingdoms.vow_book.swear"), b -> {
				this.sworn.add(nextTerm("", 1));
				this.init();
			}).bounds(x + PAD + this.sworn.size() * (TERM_W + TERM_GAP), ty, TERM_W, Ui.BUTTON_H).build());
		}
		// the rules
		while (this.rules.size() > lines()) {
			this.rules.removeLast();
		}
		int y = rulesY();
		for (int i = 0; i < this.rules.size(); i++) {
			int[] rule = this.rules.get(i);
			int row = i;
			for (int part = 0; part < 4; part++) {
				int p = part;
				addRenderableWidget(FlatButton.builder(label(rule, part), b -> turn(row, p, 1))
					.bounds(x + COL_X[part], y + i * ROW, COL_W[part], Ui.BUTTON_H).build());
			}
			addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymoskingdoms.vow_book.pick"), b -> {
				picked = row;
				this.init();
			}).bounds(x + COL_X[4], y + i * ROW, COL_W[4], Ui.BUTTON_H).build().accent(row == picked ? ACCENT : Ui.DIM));
			addRenderableWidget(FlatButton.builder(Component.literal("x"), b -> {
				this.rules.remove(row);
				this.init();
			}).bounds(x + W - 22, y + i * ROW, 16, Ui.BUTTON_H).build());
		}
		int bottom = top() + panelH() - 22;
		if (this.rules.size() < lines() && !this.allowed.isEmpty()) {
			addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymoskingdoms.vow_book.add"), b -> {
				this.rules.add(new int[] {VowBook.SELF, 0, 1, 1});
				this.init();
			}).bounds(x + PAD, bottom, 100, Ui.BUTTON_H).build());
		}
		addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymoskingdoms.vow_book.repeal"), b -> ClientPlayNetworking.send(new VowBook.Repeal()))
			.bounds(x + W / 2 - 50, bottom, 100, Ui.BUTTON_H).build());
		addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymoskingdoms.vow_book.write"), b -> {
			List<Integer> packed = new ArrayList<>();
			for (int[] r : this.rules) {
				packed.add(VowBook.pack(r[0], this.allowed.get(Math.min(r[1], this.allowed.size() - 1)), r[2], r[3]));
			}
			picked = Math.max(0, Math.min(picked, packed.size() - 1));
			ClientPlayNetworking.send(new VowBook.Write(packed, List.copyOf(this.sworn), picked));
			this.onClose();
		}).bounds(x + W - 106, bottom, 100, Ui.BUTTON_H).build());
	}

	/** The next term after {@code from} (by {@code by}) that isn't sworn already. */
	private String nextTerm(String from, int by) {
		int start = Math.max(-1, this.swearable.indexOf(from));
		int n = this.swearable.size();
		for (int k = 1; k <= n; k++) {
			String t = this.swearable.get(Math.floorMod(start + by * k, n));
			if (t.equals(from) || !this.sworn.contains(t)) {
				return t;
			}
		}
		return from;
	}

	private static Component term(String term) {
		return Component.translatable("screen.jaymoskingdoms.vow_book.own", Component.translatable("message.jaymoskingdoms.vow.term." + term));
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 1) {
			int x = left();
			// right click a sworn term: struck out
			int ty = termsY();
			for (int i = 0; i < this.sworn.size(); i++) {
				int bx = x + PAD + i * (TERM_W + TERM_GAP);
				if (event.x() >= bx && event.x() < bx + TERM_W && event.y() >= ty && event.y() < ty + Ui.BUTTON_H) {
					this.sworn.remove(i);
					this.init();
					return true;
				}
			}
			// right click a part of a rule: the choice before
			int y = rulesY();
			for (int i = 0; i < this.rules.size(); i++) {
				for (int part = 0; part < 4; part++) {
					if (event.x() >= x + COL_X[part] && event.x() < x + COL_X[part] + COL_W[part] && event.y() >= y + i * ROW
						&& event.y() < y + i * ROW + Ui.BUTTON_H) {
						turn(i, part, -1);
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void turn(int row, int part, int by) {
		int[] r = this.rules.get(row);
		int count = switch (part) {
			case 0 -> VowBook.WHO_COUNT;
			case 1 -> Math.max(1, this.allowed.size());
			case 2 -> this.strongest;
			default -> VowBook.SECONDS.length;
		};
		if (part == 2) {
			r[2] = Math.floorMod(r[2] - 1 + by, count) + 1;
		} else {
			r[part] = Math.floorMod(r[part] + by, count);
		}
		this.init();
	}

	private int what(int[] r) {
		return this.allowed.isEmpty() ? VowBook.MEND : this.allowed.get(Math.min(r[1], this.allowed.size() - 1));
	}

	private Component label(int[] r, int part) {
		return switch (part) {
			case 0 -> Component.translatable("vow.jaymoskingdoms.who." + VowBook.WHO_NAMES[r[0]]);
			case 1 -> Component.translatable("vow.jaymoskingdoms.what." + VowBook.WHAT_NAMES[what(r)] + (r[0] == VowBook.SELF ? ".self" : ""));
			case 2 -> Component.translatable("screen.jaymoskingdoms.vow_book.strength", r[2]);
			default -> Component.translatable("screen.jaymoskingdoms.vow_book.seconds", VowBook.SECONDS[r[3]]);
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(g, x, y, W, panelH(), ACCENT);
		Component title = Component.translatable(this.oath ? "screen.jaymoskingdoms.vow_book.oath" : "screen.jaymoskingdoms.vow_book.pact", this.vowName);
		Ui.line(g, this.font, title, x + PAD, y + 8, W - PAD * 2, ACCENT);
		Ui.line(g, this.font, Component.translatable("screen.jaymoskingdoms.vow_book.terms_hint"), x + PAD, y + 20, W - PAD * 2, Ui.DIM);
		Ui.rule(g, x + 6, x + W - 6, y + 31);
		int ly = y + 34;
		for (var line : this.font.split(ownText(), W - PAD * 2)) {
			g.text(this.font, line, x + PAD, ly, Ui.TEXT);
			ly += 10;
		}
		// a term sworn before, now struck out: it will break
		List<String> struck = new ArrayList<>(this.wasSworn);
		struck.removeAll(this.sworn);
		int sy = termsY() + Ui.BUTTON_H + 3;
		if (!struck.isEmpty()) {
			List<String> names = new ArrayList<>();
			for (String t : struck) {
				names.add(Component.translatable("message.jaymoskingdoms.vow.term." + t).getString());
			}
			Ui.line(g, this.font, Component.translatable(this.oath ? "message.jaymoskingdoms.vow_book.struck.oath" : "message.jaymoskingdoms.vow_book.struck.pact",
				String.join(", ", names)), x + PAD, sy, W - PAD * 2, STRUCK);
		}
		Ui.line(g, this.font, Component.translatable("screen.jaymoskingdoms.vow_book.rules", this.rules.size(), lines(), VowBook.atOnce(this.player), this.strongest),
			x + PAD, rulesY() - 11, W - PAD * 2, ACCENT);
		for (int i = 0; i < this.rules.size(); i++) {
			int[] r = this.rules.get(i);
			int packed = VowBook.pack(r[0], what(r), r[2], r[3]);
			Component cost = Component.translatable(this.oath ? "screen.jaymoskingdoms.vow_book.cost.grace" : "screen.jaymoskingdoms.vow_book.cost.credit",
				Math.round(VowBook.cost(packed)));
			Component line = i == picked ? Component.translatable("screen.jaymoskingdoms.vow_book.picked") : Component.translatable("screen.jaymoskingdoms.vow_book.line", cost);
			Ui.line(g, this.font, line, x + 8, rulesY() + i * ROW + Ui.BUTTON_H + 2, W - 16, i == picked ? ACCENT : Ui.DIM);
		}
		if (this.rules.isEmpty()) {
			Ui.line(g, this.font, Component.translatable("screen.jaymoskingdoms.vow_book.no_lines"), x + 8, rulesY() + 4, W - 16, Ui.TEXT);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}
}
