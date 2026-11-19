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
 * <li>On the left, the forms you know of the tab's family, {@link #LIST_ROWS} at a time; the wheel scrolls them. Each has an
 * ADD chip (it goes in the picked slot, and the pick moves on to the next) or, once on the belt, a REMOVE chip.</li>
 * <li>In the middle, the belt's {@value Forms#BELT_SLOTS} slots, each with the form's cooldown and hunger as pills. Click one
 * to pick it; click the picked one again to empty it.</li>
 * <li>A last tab, Spells, once Spell Engine gives you any: the spells of your spell book, weapon and gear, to read (point at
 * one for what it does); they are bound at the Spell Binding Table as before.</li>
 * </ul>
 * Only what is on the belt shows on the skill bar and in the stance ({@link Forms#barForms}).
 */
public final class BeltScreen extends Screen {
	static final int WIDTH = 340, HEIGHT = 214, PAD = 8, HEAD = 22, TAB_H = 16, TAB_GAP = 3, ROW = 20, BUTTON_H = 17, LIST_ROWS = 6;
	/** The forms list on the left, the belt's slots in the middle (a slot is {@link #SLOT_ROW} tall: its button, then its pills). */
	static final int LIST_W = 158, SLOT_ROW = 28;
	/** The ADD / REMOVE chip at the end of a form's row. */
	static final int CHIP_W = 48;
	/** How the pills read a form: its cooldown in seconds (20 ticks), and the hunger it costs. */
	static final float TICKS_PER_SECOND = 20.0F;
	static final int PILL_COOLDOWN = 0xFF6AA8FF, PILL_HUNGER = 0xFFC8872A;

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
		return com.jaymoskingdoms.client.MenuCompat.centre(this.width, WIDTH);
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
		return left() + PAD;
	}

	/** The forms list's width: all of the page for the spells, which have no belt beside them. */
	private int listW() {
		return this.spells ? WIDTH - 2 * PAD - 8 : LIST_W;
	}

	private int slotX() {
		return listX() + LIST_W + PAD + 4;
	}

	private int slotW() {
		return WIDTH - PAD - (slotX() - left()) - 8;
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
		for (int i = 0; i < (this.spells ? 0 : Forms.BELT_SLOTS); i++) {
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
			}).bounds(slotX(), bodyY() + i * SLOT_ROW, slotW(), BUTTON_H).build()).accent(i == this.picked ? Ui.GOLD : Ui.DIM);
		}
		List<Forms.Form> listed = this.spells ? List.of() : listed(book);
		this.scroll = Math.max(0, Math.min(this.scroll, (this.spells ? spellRows.size() : listed.size()) - LIST_ROWS));
		for (int i = 0; i < LIST_ROWS && this.scroll + i < listed.size(); i++) {
			Forms.Form form = listed.get(this.scroll + i);
			int on = book.belt().indexOf(form.id());
			addRenderableWidget(FlatButton.builder(Component.translatable(on >= 0 ? "gui.jaymoskingdoms.belt.remove" : "gui.jaymoskingdoms.belt.add"), b -> {
				if (on >= 0) {
					assign(on, "");
				} else {
					assign(this.picked, form.id());
					this.picked = (this.picked + 1) % Forms.BELT_SLOTS;
				}
				rebuildWidgets();
			}).bounds(listX() + listW() - CHIP_W - 1, bodyY() + i * ROW, CHIP_W, BUTTON_H).build()).accent(on >= 0 ? Ui.GOLD : Ui.DIM);
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
		Ui.panelFramed(graphics, x, y, WIDTH, HEIGHT, Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + 14, y + 8, WIDTH - 28, Ui.GOLD);
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
			List<Forms.Form> listed = listed(book);
			int size = listed.size();
			for (int i = 0; i < LIST_ROWS && this.scroll + i < size; i++) {
				Forms.Form form = listed.get(this.scroll + i);
				int ry = bodyY() + i * ROW;
				boolean over = Ui.over(mouseX, mouseY, listX(), ry, LIST_W - CHIP_W - 2, BUTTON_H);
				Ui.row(graphics, listX(), ry, listX() + LIST_W - CHIP_W - 2, ry + BUTTON_H, this.scroll + i, over);
				Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.belt.form", form.name(), book.rank(form.id())), listX() + 4, ry + 5,
					LIST_W - CHIP_W - 10, book.belt().contains(form.id()) ? Ui.GOLD : Ui.TEXT);
			}
			// each slot's form, its cooldown and hunger as pills, or what the empty slot is for
			for (int i = 0; i < Forms.BELT_SLOTS; i++) {
				Forms.Form form = Forms.get(book.belt().get(i));
				int py = bodyY() + i * SLOT_ROW + BUTTON_H + 1;
				if (form == null) {
					Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.belt.empty_slot"), slotX() + 2, py + 2, slotW() - 4, Ui.DIM);
					continue;
				}
				int px = slotX() + 2;
				px += Ui.pill(graphics, this.font, px, py, Component.translatable("gui.jaymoskingdoms.belt.cooldown").getString(),
					String.format("%.1fs", form.cooldown() / TICKS_PER_SECOND), PILL_COOLDOWN) + 3;
				Ui.pill(graphics, this.font, px, py, Component.translatable("gui.jaymoskingdoms.belt.hunger").getString(), String.format("%.1f", form.hunger()), PILL_HUNGER);
			}
			if (book.known().isEmpty()) {
				Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.belt.none"), x + PAD, bodyY() + 4, WIDTH - 2 * PAD, Ui.DIM);
			} else if (size > LIST_ROWS) {
				Ui.scrollbar(graphics, listX() + LIST_W + 2, bodyY(), LIST_ROWS * ROW - 3, LIST_ROWS, size, this.scroll);
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
