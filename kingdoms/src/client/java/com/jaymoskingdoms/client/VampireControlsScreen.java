package com.jaymoskingdoms.client;

import com.jaymoskingdoms.NightGifts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The H menu's Controls page (You), for a vampire or a dhampir: every move they have, what each costs in blood, and the few that
 * are not on the skill bar. Andy, on what felt wrong in play: "it doesnt feel as coherent or fluid as any of the other powers"; the
 * moves are all on Spell Engine's bar now, so this page only lists them (the bar's own Spells tab reads each one out), with the
 * inputs that stay on keys: the right-click leap, giving in, lapping and the stakes. A dhampir sees only what a dhampir has, free.
 */
final class VampireControlsScreen extends Screen {
	private static final int W = 270, H = 196, PAD = 10, ROW = 24, HEAD = 36, FOOT = 18;
	/** The moves of the bar, in {@code FormSkills.NIGHT_MOVES} order, and the shapes a dhampir has none of. */
	private static final String[] MOVES = {"dash", "pound", "gaze", "feed", "bat", "colony", "mist", "stare", "rake", "rend", "pounce", "roar"};
	private static final int FIRST_SHAPE = NightGifts.BAT, LAST_SHAPE = NightGifts.STARE;
	private int scroll;

	VampireControlsScreen() {
		super(Component.translatable("menu.jaymoskingdoms.tab.controls"));
	}

	/** Whether the page is open to the local player: a vampire or a dhampir. */
	static boolean available() {
		LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		NightGifts.Gift gift = player == null ? null : NightGifts.gift(player);
		return gift != null && gift.blood();
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

	/** One line of the list: a heading, or a move (name, what it does, and its cost or key on the right). */
	private record Entry(Component name, Component text, Component right, int color, boolean heading) {
	}

	private List<Entry> entries() {
		List<Entry> list = new ArrayList<>();
		LocalPlayer player = this.minecraft.player;
		NightGifts.Gift gift = player == null ? null : NightGifts.gift(player);
		if (gift == null || !gift.blood()) {
			return list;
		}
		boolean dhampir = gift.kind() == NightGifts.DHAMPIR;
		list.add(new Entry(Component.translatable("gui.jaymoskingdoms.controls.vampire.bar"),
			Component.translatable(dhampir ? "gui.jaymoskingdoms.controls.dhampir.note" : "gui.jaymoskingdoms.controls.vampire.note"), Component.empty(), Ui.GOLD, true));
		for (int i = 0; i < MOVES.length; i++) {
			if (dhampir && i >= FIRST_SHAPE && i <= LAST_SHAPE) {
				continue;
			}
			int cost = NightGifts.costOf(i);
			Component right = dhampir || cost == 0 ? Component.translatable(cost == 0 && !dhampir ? "gui.jaymoskingdoms.controls.key.bar" : "gui.jaymoskingdoms.controls.free")
				: Component.translatable("gui.jaymoskingdoms.controls.cost", cost);
			list.add(new Entry(Component.translatable("gui.jaymoskingdoms.controls.vampire." + MOVES[i]),
				Component.translatable("gui.jaymoskingdoms.controls.vampire." + MOVES[i] + ".text"), right, Ui.TEXT, false));
		}
		list.add(new Entry(Component.translatable("gui.jaymoskingdoms.controls.vampire.keys"), Component.empty(), Component.empty(), Ui.GOLD, true));
		list.add(key("leap", Component.translatable("gui.jaymoskingdoms.controls.key.use")));
		list.add(key("giveup", WerewolfKey.name()));
		list.add(key("lap", Component.translatable("gui.jaymoskingdoms.controls.key.sneak_use")));
		list.add(key("stakes", Component.translatable("gui.jaymoskingdoms.controls.key.sneak_scroll")));
		return list;
	}

	private static Entry key(String id, Component key) {
		return new Entry(Component.translatable("gui.jaymoskingdoms.controls.vampire." + id), Component.translatable("gui.jaymoskingdoms.controls.vampire." + id + ".text"),
			key, Ui.TEXT, false);
	}

	private int listTop() {
		return top() + HEAD;
	}

	private int listHeight() {
		return H - HEAD - FOOT;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll -= (int)Math.signum(scrollY) * ROW;
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = W - 2 * PAD;
		Ui.panel(graphics, x, y, W, H, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.controls.title"), x + PAD, y + 8, w, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.controls.vampire"), x + PAD, y + 20, w, Ui.DIM);
		Ui.rule(graphics, x + 6, x + W - 6, y + HEAD - 5);
		List<Entry> entries = entries();
		int total = entries.size() * ROW;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, total - listHeight()));
		graphics.enableScissor(x + PAD - 2, listTop(), x + W - PAD + 2, listTop() + listHeight());
		for (int i = 0; i < entries.size(); i++) {
			int ry = listTop() + i * ROW - this.scroll;
			if (ry + ROW <= listTop() || ry >= listTop() + listHeight()) {
				continue;
			}
			Entry e = entries.get(i);
			Ui.row(graphics, x + PAD, ry, x + W - PAD - 6, ry + ROW - 2, i, false);
			Ui.line(graphics, this.font, e.name(), x + PAD + 4, ry + 3, w - 90, e.color());
			if (!e.right().getString().isEmpty()) {
				Ui.lineRight(graphics, this.font, e.right(), x + W - PAD - 10, ry + 3, 80, Ui.GOLD);
			}
			if (!e.text().getString().isEmpty()) {
				Ui.line(graphics, this.font, e.text(), x + PAD + 4, ry + 13, w - 16, Ui.DIM);
			}
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, x + W - PAD - 2, listTop(), listHeight(), listHeight(), total, this.scroll);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
