package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.client.Ui;
import com.jaymoskingdoms.client.ui.FlatButton;
import com.jaymoskingdoms.forms.Forms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The belt menu (Andy: "stances should be set through a menu, and the icons are too numerous now, so maybe a tab along
 * the top of the screen or a scroll wheel"). It is the character menu's Forms tab ({@code MenuCompat}), and still opens
 * at a training post or a dummy; the belt can be changed anywhere now.
 * <ul>
 * <li>Along the top, a tab for each weapon family you know a form of.</li>
 * <li>On the left, the belt's {@value Forms#BELT_SLOTS} slots. Click one to pick it; click the picked one again to empty it.</li>
 * <li>On the right, the forms you know of the tab's family, {@link #LIST_ROWS} at a time; the wheel scrolls them. Click one
 * to put it in the picked slot, and the pick moves on to the next slot.</li>
 * <li>A last tab, Spells, once Spell Engine gives you any: the spells of your spell book, weapon and gear, to read (point at
 * one for what it does); they are bound at the Spell Binding Table as before.</li>
 * </ul>
 * Only what is on the belt shows on the skill bar and in the stance ({@link Forms#barForms}).
 */
public final class BeltScreen extends Screen {
	static final int WIDTH = 330, HEIGHT = 196, PAD = 8, HEAD = 22, TAB_H = 16, TAB_GAP = 3, ROW = 20, BUTTON_H = 17, LIST_ROWS = 6;
	static final int SLOT_W = 140;

	private Forms.@Nullable Family tab;
	private int picked, scroll;
	/** Whether the Spells tab is the one open (the spells Spell Engine gives you now, to read, {@link SpellList}). */
	private boolean spells;

	public BeltScreen() {
		super(Component.translatable("gui.jaymoskingdoms.belt.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (this.width - WIDTH) / 2;
	}

	private int top() {
		return Math.max(4 + com.jaymoskingdoms.client.MenuCompat.STRIP, (this.height - HEIGHT) / 2);
	}

	/** Where the character menu hangs its tabs. */
	public int[] corner() {
		return new int[] {left(), top()};
	}

	private Forms.@Nullable Book book() {
		return this.minecraft == null || this.minecraft.player == null ? null : Forms.book(this.minecraft.player);
	}

	/** The families you know a form of, in their own order. */
	private List<Forms.Family> families(Forms.Book book) {
		List<Forms.Family> out = new ArrayList<>();
		for (String id : book.known()) {
			Forms.Form form = Forms.get(id);
			if (form != null && !out.contains(form.family())) {
				out.add(form.family());
			}
		}
		out.sort(java.util.Comparator.naturalOrder());
		return out;
	}

	/** The forms you know of the open tab's family. */
	private List<Forms.Form> listed(Forms.Book book) {
		List<Forms.Form> out = new ArrayList<>();
		for (String id : book.known()) {
			Forms.Form form = Forms.get(id);
			if (form != null && form.family() == this.tab) {
				out.add(form);
			}
		}
		return out;
	}

	private int listX() {
		return left() + PAD + SLOT_W + PAD;
	}

	private int listW() {
		return WIDTH - PAD - (listX() - left()) - 8;
	}

	private int bodyY() {
		return top() + HEAD + TAB_H + 8;
	}

	@Override
	protected void init() {
		Forms.Book book = book();
		if (book == null) {
			return;
		}
		List<Forms.Family> families = families(book);
		List<SpellList.Row> spellRows = SpellList.of(this.minecraft.player);
		// with spells but no forms, the Spells tab is the page
		this.spells = !spellRows.isEmpty() && (this.spells || families.isEmpty());
		if (this.tab == null || !families.contains(this.tab)) {
			// the family of what is in your hand, else the first you know
			this.tab = families.isEmpty() ? null : families.getFirst();
			for (Forms.Family family : families) {
				if (!this.minecraft.player.getMainHandItem().isEmpty() && family.holds(this.minecraft.player.getMainHandItem())) {
					this.tab = family;
				}
			}
		}
		int x = left() + PAD, y = top() + HEAD;
		int tabs = families.size() + (spellRows.isEmpty() ? 0 : 1);
		int tabW = tabs == 0 ? 0 : Math.min(72, (WIDTH - 2 * PAD - (tabs - 1) * TAB_GAP) / tabs);
		for (int i = 0; i < families.size(); i++) {
			Forms.Family family = families.get(i);
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.belt.family." + family.key()), b -> {
				this.tab = family;
				this.spells = false;
				this.scroll = 0;
				rebuildWidgets();
			}).bounds(x + i * (tabW + TAB_GAP), y, tabW, TAB_H).build()).accent(family == this.tab && !this.spells ? Ui.GOLD : Ui.DIM);
		}
		if (!spellRows.isEmpty()) {
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.belt.spells"), b -> {
				this.spells = true;
				this.scroll = 0;
				rebuildWidgets();
			}).bounds(x + families.size() * (tabW + TAB_GAP), y, tabW, TAB_H).build()).accent(this.spells ? Ui.GOLD : Ui.DIM);
		}
		for (int i = 0; i < Forms.BELT_SLOTS; i++) {
			int slot = i;
			Forms.Form form = Forms.get(book.belt().get(i));
			Component label = form == null ? Component.translatable("gui.jaymoskingdoms.belt.bare", i + 1)
				: Component.translatable("gui.jaymoskingdoms.belt.slot", i + 1, form.name(), book.rank(form.id()));
			addRenderableWidget(FlatButton.builder(label, b -> {
				if (this.picked == slot && form != null) {
					assign(slot, "");
				}
				this.picked = slot;
				rebuildWidgets();
			}).bounds(x, bodyY() + i * ROW, SLOT_W, BUTTON_H).build()).accent(i == this.picked ? Ui.GOLD : Ui.DIM);
		}
		List<Forms.Form> listed = this.spells ? List.of() : listed(book);
		this.scroll = Math.max(0, Math.min(this.scroll, (this.spells ? spellRows.size() : listed.size()) - LIST_ROWS));
		int listW = listW();
		for (int i = 0; i < LIST_ROWS && this.scroll + i < listed.size(); i++) {
			Forms.Form form = listed.get(this.scroll + i);
			boolean worn = book.belt().contains(form.id());
			addRenderableWidget(FlatButton.builder(Component.translatable(worn ? "gui.jaymoskingdoms.belt.worn" : "gui.jaymoskingdoms.belt.form", form.name(),
				book.rank(form.id())), b -> {
					assign(this.picked, form.id());
					this.picked = (this.picked + 1) % Forms.BELT_SLOTS;
					rebuildWidgets();
				}).bounds(listX(), bodyY() + i * ROW, listW, BUTTON_H).build()).accent(worn ? Ui.GOLD : Ui.DIM);
		}
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.belt.done"), b -> onClose())
			.bounds(left() + WIDTH - PAD - 60, top() + HEIGHT - 22, 60, 16).build());
	}

	private void assign(int slot, String form) {
		if (ClientPlayNetworking.canSend(Forms.Assign.TYPE)) {
			ClientPlayNetworking.send(new Forms.Assign(slot, form));
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		Forms.Book book = book();
		if (book != null && scrollY != 0.0) {
			int max = Math.max(0, (this.spells ? SpellList.of(this.minecraft.player).size() : listed(book).size()) - LIST_ROWS);
			int next = Math.max(0, Math.min(max, this.scroll + (scrollY > 0.0 ? -1 : 1)));
			if (next != this.scroll) {
				this.scroll = next;
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private List<String> shown = List.of();

	@Override
	public void tick() {
		// the belt comes back from the server a moment after a click: keep the buttons in step
		Forms.Book book = book();
		if (book != null && !book.belt().equals(this.shown)) {
			this.shown = book.belt();
			rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, WIDTH, HEIGHT, Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + 9, y + 8, WIDTH - 18, Ui.GOLD);
		Forms.Book book = book();
		List<Component> tip = null;
		if (book != null && this.spells) {
			// the spells you have now, the passive ones dim; pointing at one reads it out
			List<SpellList.Row> rows = SpellList.of(this.minecraft.player);
			for (int i = 0; i < LIST_ROWS && this.scroll + i < rows.size(); i++) {
				SpellList.Row row = rows.get(this.scroll + i);
				int ry = bodyY() + i * ROW;
				boolean over = Ui.over(mouseX, mouseY, listX(), ry, listW(), BUTTON_H);
				Ui.row(graphics, listX(), ry, listX() + listW(), ry + BUTTON_H, this.scroll + i, over);
				Ui.line(graphics, this.font, row.passive() ? Component.translatable("gui.jaymoskingdoms.belt.spells.passive", row.name()) : row.name(),
					listX() + 5, ry + 5, listW() - 10, row.passive() ? Ui.DIM : Ui.TEXT);
				if (over) {
					tip = SpellList.describe(row, this.minecraft.player);
				}
			}
			if (rows.size() > LIST_ROWS) {
				Ui.scrollbar(graphics, x + WIDTH - PAD - 4, bodyY(), LIST_ROWS * ROW - 3, LIST_ROWS, rows.size(), this.scroll);
			}
		} else if (book != null) {
			int size = listed(book).size();
			if (book.known().isEmpty()) {
				Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.belt.none"), x + PAD, bodyY() + 4, WIDTH - 2 * PAD, Ui.DIM);
			} else if (size > LIST_ROWS) {
				Ui.scrollbar(graphics, x + WIDTH - PAD - 4, bodyY(), LIST_ROWS * ROW - 3, LIST_ROWS, size, this.scroll);
			}
		}
		Ui.line(graphics, this.font, Component.translatable(this.spells ? "gui.jaymoskingdoms.belt.spells.hint" : "gui.jaymoskingdoms.belt.hint"), x + PAD, y + HEIGHT - 18, WIDTH - 2 * PAD - 68, Ui.DIM);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (tip != null) {
			Ui.tip(graphics, this.font, tip, mouseX, mouseY);
		}
	}

	/** Whether the player has a Spell Engine spell to show, so the menu's tab is there for a caster who knows no form. */
	public static boolean hasSpells(net.minecraft.world.entity.player.Player player) {
		return !SpellList.of(player).isEmpty();
	}
}
