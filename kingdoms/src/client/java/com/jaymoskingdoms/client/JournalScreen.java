package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FactionType;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Journal;
import com.jaymoskingdoms.QuestPast;
import com.jaymoskingdoms.Tales;
import java.util.ArrayList;
import java.util.List;
import com.jaymoskingdoms.client.ui.FlatButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * (0.5.49) The traveller's journal ({@link Journal}): three pages. <b>Quests</b>: each quest you carry, who gave it, its
 * story, what's left and which way it lies. <b>Map</b>: the land about you as a map draws it (only what your eyes have
 * loaded; the rest is blank parchment), with you, your quests' places and the settlements on it, and zoom. <b>Reputation</b>:
 * what each settlement you know thinks of you. (0.7.40) Restyled like the H screen: a header, striped rows that light up
 * under the mouse, and on the quests page a box at the bottom with the hovered (or pressed) quest in full. The illustrated
 * tabs show the current page and light up under the mouse. Two more pages: <b>Stories</b>, each tale you're in or have
 * seen the end of, with what's open now and the notes written so far; <b>Past</b>, the quests you finished, failed or gave
 * up. On the quests page a quest's pin tracks that quest alone on screen ({@link QuestClient}).
 */
final class JournalScreen extends Screen {
	static final int PANEL_W = 400, PANEL_H = 280, HEADER = 34, TAB_H = 20, BODY_Y = 60, FOOTER = 24, QUEST_ROW = 18,
		REP_ROW = 24, DETAIL_LINES = 7;
	/** The map's largest size on screen, one drawn cell's size, and room kept for its legend (px; blocks per cell for zoom). */
	static final int MAX_MAP_SIZE = 176, CELL = 2, MAP_SIDEBAR = 150;
	private static final Identifier TAB_ICONS = JaymosKingdoms.id("textures/gui/journal/tabs.png");
	static final int[] ZOOMS = {1, 2, 4, 8, 16, 32};
	static final int DEFAULT_ZOOM = 2;
	static final int COLOR_TEXT = Ui.TEXT, COLOR_DIM = Ui.DIM, COLOR_ROYAL = 0xFFFF6A50, COLOR_GOLD = Ui.GOLD, COLOR_DONE = 0xFF66EE66,
		COLOR_PARCHMENT = Ui.PAPER, COLOR_HATED = 0xFFE04040;
	static final int QUESTS = 0, MAP = 1, REPUTATION = 2, STORIES = 3, PAST = 4;
	/** The pages' tabs, in order; the first three have their icons in {@link #TAB_ICONS}, the rest show an item. */
	private static final String[] TABS = {"quests", "map", "reputation", "stories", "past"};
	/** A tab's width beside its name (icon and edges), the gap between two tabs, and the size of a quest's pin (px). */
	static final int TAB_PAD = 31, TAB_GAP = 4, PIN = 9;
	/** Stories: the list's widest, a row's height, and where the picked tale's text starts under its name. Past: an entry's head, and the gap to the next. */
	static final int STORY_LIST_W = 130, STORY_ROW = 18, STORY_HEAD = 31, PAST_HEAD = 14, PAST_GAP = 2;
	static final int COLOR_ENDED = COLOR_DONE, COLOR_ENDED_BADLY = COLOR_HATED;

	private static int lastPage = QUESTS, lastZoom = DEFAULT_ZOOM;

	private final Journal.Book book;
	private final KeyMapping key;
	private int page = lastPage, zoom = lastZoom, scroll;
	/** The quest last pressed, shown in the box when none is pointed at. */
	private int selected;
	/** Each tab's width, for this panel. */
	private int[] tabWidths = new int[TABS.length];
	/** Stories: the tale picked, the one its text was last wrapped for, that text, and how far it's scrolled (lines). */
	private int story, storyWrapped = -1, textScroll;
	private List<StoryLine> storyText = List.of();
	/** Past: each entry's text, wrapped. */
	private List<List<FormattedCharSequence>> pastText = List.of();

	/** One line of a tale's page: a note's first line has a mark before it. */
	private record StoryLine(FormattedCharSequence text, int color, boolean note, boolean first) {
	}
	/** The drawn map (ARGB per cell, row by row), its centre when drawn and the zoom and cell count it was drawn at. */
	private int[] cells;
	private int drawnX, drawnZ, drawnZoom = -1, drawnSize = -1;

	JournalScreen(Journal.Book book, KeyMapping key) {
		super(Component.translatable("item.jaymoskingdoms.journal"));
		this.book = book;
		this.key = key;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 12 - MenuCompat.SIDE);
	}

	private int panelH() {
		return Math.min(PANEL_H, this.height - 8 - MenuCompat.STRIP);
	}

	private int left() {
		return MenuCompat.centre(this.width, panelW());
	}

	private int top() {
		return Math.max(4 + MenuCompat.STRIP, (this.height - panelH()) / 2);
	}

	private int mapSize() {
		int fit = Math.min(MAX_MAP_SIZE, Math.min(panelW() - MAP_SIDEBAR, panelH() - BODY_Y - FOOTER - 12));
		return Math.max(96, fit / CELL * CELL);
	}

	private int tabW(int which) {
		return tabWidths[which];
	}

	private int tabX(int which) {
		int x = left() + 10;
		for (int i = 0; i < which; i++) {
			x += tabWidths[i] + TAB_GAP;
		}
		return x;
	}

	/** The tabs share the panel's width by what each one's name needs (a name still too long for its tab slides). */
	private void layTabs() {
		int room = panelW() - 20 - (TABS.length - 1) * TAB_GAP, need = 0;
		for (int i = 0; i < TABS.length; i++) {
			tabWidths[i] = TAB_PAD + this.font.width(Component.translatable("journal.jaymoskingdoms.tab." + TABS[i]));
			need += tabWidths[i];
		}
		for (int i = 0; i < TABS.length; i++) {
			tabWidths[i] = tabWidths[i] * room / need;
		}
	}

	/** (0.7.649) The panel's top-left corner, where Jaymo's Powers' character menu hangs its tabs ({@link MenuCompat}). */
	int[] corner() {
		return new int[] {left(), top()};
	}

	private int detailY() {
		return top() + panelH() - FOOTER - (10 + DETAIL_LINES * Ui.LINE + 2);
	}

	private int questRows() {
		return Math.max(1, (detailY() - 4 - top() - BODY_Y) / QUEST_ROW);
	}

	private int repRows() {
		return Math.max(1, (panelH() - FOOTER - 4 - BODY_Y) / REP_ROW);
	}

	private int storyRows() {
		return Math.max(1, (panelH() - FOOTER - 4 - BODY_Y) / STORY_ROW);
	}

	private int storyListW() {
		return Math.min(STORY_LIST_W, panelW() / 3);
	}

	/** Where the picked tale's page starts, right of the list. */
	private int storyTextX() {
		return left() + 6 + storyListW() + 7;
	}

	private int storyTextRows() {
		return Math.max(1, (storyRows() * STORY_ROW - STORY_HEAD - 2) / Ui.LINE);
	}

	private int pastView() {
		return panelH() - FOOTER - 4 - BODY_Y;
	}

	private int pastHeight(int index) {
		int lines = pastText.get(index).size();
		return PAST_HEAD + lines * Ui.LINE + (lines > 0 ? 2 : 0);
	}

	/** The furthest the past page scrolls: the first entry from which all the rest fit. */
	private int pastMaxScroll() {
		int room = pastView(), i = pastText.size();
		while (i > 0 && room >= pastHeight(i - 1)) {
			room -= pastHeight(--i) + PAST_GAP;
		}
		return Math.min(i, Math.max(0, pastText.size() - 1));
	}

	@Override
	protected void init() {
		layTabs();
		storyWrapped = -1;
		pastText = new ArrayList<>();
		for (QuestPast.Entry e : book.past()) {
			pastText.add(e.text().getString().isEmpty() ? List.of() : this.font.split(e.text(), panelW() - 26));
		}
		int fy = top() + panelH() - FOOTER + 4;
		if (page == MAP) {
			int bx = left() + 12 + mapSize() + 12;
			Button in = FlatButton.builder(Component.literal("+"), b -> zoom(-1)).bounds(bx, top() + BODY_Y, 20, 16).build();
			Button out = FlatButton.builder(Component.literal("-"), b -> zoom(1)).bounds(bx + 24, top() + BODY_Y, 20, 16).build();
			in.active = zoom > 0;
			out.active = zoom < ZOOMS.length - 1;
			addRenderableWidget(in);
			addRenderableWidget(out);
		}
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left() + panelW() - 66, fy, 60, 16).build());
		// ((0.7.618, Rivals patch 3) the ladders of captains you know of)
		addRenderableWidget(FlatButton.builder(Component.translatable("ladder.jaymoskingdoms.button"), b -> LadderScreen.ask()).bounds(left() + 6, fy, 70, 16).build());
	}

	private void show(int which) {
		page = lastPage = which;
		scroll = 0;
		textScroll = 0;
		rebuildWidgets();
	}

	private void zoom(int by) {
		zoom = lastZoom = Mth.clamp(zoom + by, 0, ZOOMS.length - 1);
		rebuildWidgets();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (key.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (page == MAP) {
			if (scrollY != 0) {
				zoom(scrollY > 0 ? -1 : 1);
			}
			return true;
		}
		if (page == STORIES && x >= storyTextX()) {
			textScroll = Mth.clamp(textScroll - (int)Math.signum(scrollY), 0, Math.max(0, storyText.size() - storyTextRows()));
			return true;
		}
		int rows = switch (page) {
			case QUESTS -> book.entries().size() - questRows();
			case STORIES -> book.stories().size() - storyRows();
			case PAST -> pastMaxScroll();
			default -> (int)book.places().stream().filter(Journal.Place::rated).count() - repRows();
		};
		scroll = Mth.clamp(scroll - (int)Math.signum(scrollY), 0, Math.max(0, rows));
		return true;
	}

	private int tabAt(int mouseX, int mouseY) {
		int y = top() + HEADER + 2;
		for (int i = 0; i < TABS.length; i++) {
			if (Ui.over(mouseX, mouseY, tabX(i), y, tabW(i), TAB_H)) {
				return i;
			}
		}
		return -1;
	}

	private void click() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int)event.x(), mouseY = (int)event.y();
		int tab = tabAt(mouseX, mouseY);
		if (tab >= 0) {
			if (tab != page) {
				show(tab);
				click();
			}
			return true;
		}
		if (page == QUESTS) {
			int index = questAt(mouseX, mouseY);
			if (index >= 0) {
				String quest = book.entries().get(index).quest();
				if (!quest.isEmpty() && overPin(mouseX, mouseY, index)) {
					// (the same pin again lets go of it)
					QuestClient.pin(quest.equals(QuestClient.pinned()) ? "" : quest);
				} else {
					selected = index;
				}
				click();
				return true;
			}
		}
		if (page == STORIES) {
			int index = storyAt(mouseX, mouseY);
			if (index >= 0) {
				story = index;
				click();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW();
		Ui.panel(graphics, x, y, w, panelH(), COLOR_GOLD);
		// the header: the journal, and what this page holds
		graphics.item(new ItemStack(Journal.JOURNAL), x + 8, y + 8);
		Ui.line(graphics, this.font, this.title, x + 28, y + 8, w - 36, COLOR_GOLD);
		long rated = book.places().stream().filter(Journal.Place::rated).count();
		Component sub = switch (page) {
			case MAP -> Component.translatable("journal.jaymoskingdoms.sub.map", mapSize() * ZOOMS[zoom] / CELL);
			case REPUTATION -> Component.translatable("journal.jaymoskingdoms.sub.reputation", rated);
			case STORIES -> {
				long open = book.stories().stream().filter(s -> s.state() == 0).count();
				yield Component.translatable("journal.jaymoskingdoms.sub.stories", open, book.stories().size() - open);
			}
			case PAST -> Component.translatable("journal.jaymoskingdoms.sub.past", book.past().size());
			default -> Component.translatable("journal.jaymoskingdoms.sub.quests", book.entries().size());
		};
		Ui.line(graphics, this.font, sub, x + 28, y + 18, w - 36, COLOR_DIM);
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);
		tabs(graphics, mouseX, mouseY);
		int bodyTop = y + BODY_Y;
		switch (page) {
			case MAP -> map(graphics, x + 12, bodyTop, mouseX, mouseY);
			case REPUTATION -> reputation(graphics, x, bodyTop, mouseX, mouseY);
			case STORIES -> stories(graphics, x, bodyTop, mouseX, mouseY);
			case PAST -> past(graphics, x, bodyTop, mouseX, mouseY);
			default -> quests(graphics, x, bodyTop, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Original journal icons and labels, with the same hover and picked fills as the newer screens' chips. */
	private void tabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = top() + HEADER + 2;
		for (int i = 0; i < TABS.length; i++) {
			int x = tabX(i), w = tabW(i);
			boolean picked = page == i, hover = Ui.over(mouseX, mouseY, x, y, w, TAB_H);
			graphics.fill(x, y, x + w, y + TAB_H, picked ? Ui.ROW_PICKED : hover ? Ui.ROW_HOVER : Ui.CHIP);
			graphics.fill(x, y + TAB_H - 2, x + w, y + TAB_H, picked ? COLOR_GOLD : hover ? Ui.ROW_HOVER : Ui.ROW);
			if (i < 3) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, TAB_ICONS, x + 5, y + 2, i * 16.0F, 0.0F, 16, 16, 16, 16, 48, 16);
			} else {
				graphics.item(new ItemStack(i == STORIES ? Items.WRITABLE_BOOK : Items.CLOCK), x + 5, y + 2);
			}
			Ui.line(graphics, this.font, Component.translatable("journal.jaymoskingdoms.tab." + TABS[i]), x + 26, y + 6, w - 31,
				picked ? COLOR_GOLD : hover ? COLOR_TEXT : COLOR_DIM);
		}
	}

	// ---- quests ----------------------------------------------------------------------------------------------------------

	private int questAt(int mouseX, int mouseY) {
		int x = left(), top = top() + BODY_Y;
		int end = Math.min(book.entries().size(), scroll + questRows());
		for (int i = scroll; i < end; i++) {
			if (Ui.over(mouseX, mouseY, x + 6, top + (i - scroll) * QUEST_ROW, panelW() - 12, QUEST_ROW - 1)) {
				return i;
			}
		}
		return -1;
	}

	private static int questColor(Journal.Entry e) {
		return e.ready() ? COLOR_DONE : e.royal() ? COLOR_ROYAL : COLOR_GOLD;
	}

	/** Whether the mouse is on the pin of the quest in row {@code index} (a little more than the pin itself). */
	private boolean overPin(int mouseX, int mouseY, int index) {
		return Ui.over(mouseX, mouseY, left() + panelW() - 10 - PIN - 2, top() + BODY_Y + (index - scroll) * QUEST_ROW + 2, PIN + 4, PIN + 4);
	}

	/** A quest's pin: a small box, filled while that quest is the one tracked on screen. */
	private void pin(GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean hover) {
		int rim = hover ? COLOR_TEXT : on ? COLOR_GOLD : COLOR_DIM;
		graphics.fill(x, y, x + PIN, y + PIN, Ui.CHIP);
		graphics.fill(x, y, x + PIN, y + 1, rim);
		graphics.fill(x, y + PIN - 1, x + PIN, y + PIN, rim);
		graphics.fill(x, y + 1, x + 1, y + PIN - 1, rim);
		graphics.fill(x + PIN - 1, y + 1, x + PIN, y + PIN - 1, rim);
		if (on) {
			graphics.fill(x + 3, y + 3, x + PIN - 3, y + PIN - 3, COLOR_GOLD);
		}
	}

	/** A row per quest (its name, what's left, which way it lies), and the hovered or pressed one in full under them. */
	private void quests(GuiGraphicsExtractor graphics, int x, int top, int mouseX, int mouseY) {
		int w = panelW();
		List<Journal.Entry> entries = book.entries();
		Ui.well(graphics, x + 5, top - 1, w - 10, questRows() * QUEST_ROW + 2);
		if (entries.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("journal.jaymoskingdoms.no_quests"), x + w / 2, top + 20, COLOR_DIM);
			return;
		}
		scroll = Mth.clamp(scroll, 0, Math.max(0, entries.size() - questRows()));
		selected = Mth.clamp(selected, 0, entries.size() - 1);
		LocalPlayer player = this.minecraft.player;
		int hovered = questAt(mouseX, mouseY);
		int end = Math.min(entries.size(), scroll + questRows());
		int nameW = Math.min(150, (w - 24) / 2);
		for (int i = scroll; i < end; i++) {
			Journal.Entry e = entries.get(i);
			int ry = top + (i - scroll) * QUEST_ROW;
			int color = questColor(e);
			graphics.fill(x + 6, ry, x + w - 6, ry + QUEST_ROW - 1, i == hovered ? Ui.ROW_HOVER : i == selected ? Ui.ROW_PICKED : i % 2 == 0 ? Ui.ROW : Ui.ROW_ALT);
			graphics.fill(x + 6, ry, x + 8, ry + QUEST_ROW - 1, color);
			Ui.line(graphics, this.font, e.title(), x + 12, ry + 4, nameW, color);
			int right = x + w - 10;
			// (the pin, on the quests that can be tracked; its room is kept on every row so the columns line up)
			if (!e.quest().isEmpty()) {
				boolean on = e.quest().equals(QuestClient.pinned()), over = i == hovered && overPin(mouseX, mouseY, i);
				pin(graphics, right - PIN, ry + 4, on, over);
				if (over) {
					Ui.tip(graphics, this.font, Component.translatable(on ? "journal.jaymoskingdoms.pin.off" : "journal.jaymoskingdoms.pin.on"), mouseX, mouseY);
				}
			}
			right -= PIN + 6;
			if (e.target().isPresent() && player != null && book.here()) {
				Component where = where(player, e.target().get());
				int ww = this.font.width(where);
				graphics.text(this.font, where, right - ww, ry + 4, COLOR_DIM);
				right -= ww + 8;
			}
			int textX = x + 12 + nameW + 8;
			Ui.line(graphics, this.font, e.objective(), textX, ry + 4, right - textX, e.ready() ? COLOR_DONE : COLOR_DIM);
		}
		Ui.scrollbar(graphics, x + w - 4, top, questRows() * QUEST_ROW, questRows(), entries.size(), scroll);
		Journal.Entry shown = entries.get(hovered >= 0 ? hovered : selected);
		List<Component> text = new ArrayList<>();
		text.add(shown.giver().copy().withColor(COLOR_DIM));
		text.add(shown.text());
		text.add(shown.objective().copy().withColor(shown.ready() ? COLOR_DONE : COLOR_GOLD));
		Ui.detail(graphics, this.font, x, detailY(), w, DETAIL_LINES, shown.title(), questColor(shown), text);
	}

	/** How far and which way {@code pos} lies from the player. */
	static Component where(LocalPlayer player, BlockPos pos) {
		double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
		int distance = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
		double angle = Math.toDegrees(Math.atan2(dx, dz));
		String[] points = {"s", "se", "e", "ne", "n", "nw", "w", "sw"};
		int i = (int)Math.floorMod(Math.round(angle / 45.0), 8L);
		return Component.translatable("quest.jaymoskingdoms.tracker.where", distance, Component.translatable("quest.jaymoskingdoms.tracker." + points[i]));
	}

	// ---- stories ---------------------------------------------------------------------------------------------------------

	private int storyAt(int mouseX, int mouseY) {
		int x = left(), top = top() + BODY_Y;
		int end = Math.min(book.stories().size(), scroll + storyRows());
		for (int i = scroll; i < end; i++) {
			if (Ui.over(mouseX, mouseY, x + 6, top + (i - scroll) * STORY_ROW, storyListW(), STORY_ROW - 1)) {
				return i;
			}
		}
		return -1;
	}

	private static int storyColor(Tales.Story s) {
		return s.state() == 0 ? COLOR_GOLD : s.state() == 1 ? COLOR_ENDED : COLOR_ENDED_BADLY;
	}

	/** The picked tale's page wrapped to {@code width}: what's open now (or how it ended), then its notes, oldest first. */
	private List<StoryLine> wrap(Tales.Story s, int width) {
		List<StoryLine> out = new ArrayList<>();
		for (FormattedCharSequence line : this.font.split(s.now(), width)) {
			out.add(new StoryLine(line, COLOR_TEXT, false, false));
		}
		if (!out.isEmpty() && !s.lines().isEmpty()) {
			out.add(new StoryLine(FormattedCharSequence.EMPTY, COLOR_TEXT, false, false));
		}
		for (Component note : s.lines()) {
			boolean first = true;
			for (FormattedCharSequence line : this.font.split(note, width - 6)) {
				out.add(new StoryLine(line, COLOR_DIM, true, first));
				first = false;
			}
		}
		return out;
	}

	/**
	 * A row per tale down the left (the ones under way first, then the ones told), and the pressed one's page beside
	 * them: its name, how it stands, what's open now and the notes written so far, which the wheel scrolls.
	 */
	private void stories(GuiGraphicsExtractor graphics, int x, int top, int mouseX, int mouseY) {
		int w = panelW(), rows = storyRows(), h = rows * STORY_ROW + 2;
		List<Tales.Story> stories = book.stories();
		if (stories.isEmpty()) {
			Ui.well(graphics, x + 5, top - 1, w - 10, h);
			graphics.centeredText(this.font, Component.translatable("journal.jaymoskingdoms.no_stories"), x + w / 2, top + 20, COLOR_DIM);
			return;
		}
		int listW = storyListW();
		Ui.well(graphics, x + 5, top - 1, listW + 2, h);
		scroll = Mth.clamp(scroll, 0, Math.max(0, stories.size() - rows));
		story = Mth.clamp(story, 0, stories.size() - 1);
		int hovered = storyAt(mouseX, mouseY);
		int end = Math.min(stories.size(), scroll + rows);
		for (int i = scroll; i < end; i++) {
			Tales.Story s = stories.get(i);
			int ry = top + (i - scroll) * STORY_ROW;
			graphics.fill(x + 6, ry, x + 6 + listW, ry + STORY_ROW - 1, i == hovered ? Ui.ROW_HOVER : i == story ? Ui.ROW_PICKED : i % 2 == 0 ? Ui.ROW : Ui.ROW_ALT);
			graphics.fill(x + 6, ry, x + 8, ry + STORY_ROW - 1, storyColor(s));
			Ui.line(graphics, this.font, s.title(), x + 12, ry + 4, listW - 9, s.state() == 0 ? COLOR_TEXT : COLOR_DIM);
		}
		Ui.scrollbar(graphics, x + 6 + listW + 2, top, rows * STORY_ROW, rows, stories.size(), scroll);
		// the pressed tale's page
		Tales.Story shown = stories.get(story);
		int px = storyTextX(), pw = x + w - 6 - px;
		Ui.well(graphics, px - 1, top - 1, pw + 2, h);
		Ui.line(graphics, this.font, shown.title(), px + 5, top + 4, pw - 10, COLOR_TEXT);
		Ui.line(graphics, this.font, Component.translatable("journal.jaymoskingdoms.story.state." + Mth.clamp(shown.state(), 0, 2)), px + 5, top + 14, pw - 10,
			storyColor(shown));
		Ui.rule(graphics, px + 4, px + pw - 4, top + STORY_HEAD - 5);
		if (storyWrapped != story) {
			storyText = wrap(shown, pw - 10);
			storyWrapped = story;
			textScroll = 0;
		}
		int view = storyTextRows();
		textScroll = Mth.clamp(textScroll, 0, Math.max(0, storyText.size() - view));
		for (int i = textScroll; i < Math.min(storyText.size(), textScroll + view); i++) {
			StoryLine line = storyText.get(i);
			int ly = top + STORY_HEAD + (i - textScroll) * Ui.LINE;
			if (line.first()) {
				graphics.fill(px + 5, ly + 3, px + 7, ly + 5, storyColor(shown));
			}
			graphics.text(this.font, line.text(), px + (line.note() ? 11 : 5), ly, line.color());
		}
		Ui.scrollbar(graphics, x + w - 4, top + STORY_HEAD, view * Ui.LINE, view, storyText.size(), textScroll);
	}

	// ---- past ------------------------------------------------------------------------------------------------------------

	private static int pastColor(int how) {
		return how == QuestPast.DONE ? COLOR_DONE : how == QuestPast.FAILED ? COLOR_HATED : COLOR_DIM;
	}

	/** The quests you're done with, newest first: each one's name, how it went, the day, and under them how it ended. */
	private void past(GuiGraphicsExtractor graphics, int x, int top, int mouseX, int mouseY) {
		int w = panelW(), view = pastView();
		List<QuestPast.Entry> past = book.past();
		Ui.well(graphics, x + 5, top - 1, w - 10, view + 2);
		if (past.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("journal.jaymoskingdoms.no_past"), x + w / 2, top + 20, COLOR_DIM);
			return;
		}
		int most = pastMaxScroll();
		scroll = Mth.clamp(scroll, 0, most);
		int y = top;
		for (int i = scroll; i < past.size(); i++) {
			int h = pastHeight(i);
			// (one that doesn't fit waits for the wheel; the first is drawn whatever its length, cut at the page's foot)
			if (i > scroll && y + h > top + view) {
				break;
			}
			QuestPast.Entry e = past.get(i);
			int bottom = Math.min(y + h, top + view), color = pastColor(e.how());
			Ui.row(graphics, x + 6, y, x + w - 6, bottom, i, Ui.over(mouseX, mouseY, x + 6, y, w - 12, bottom - y));
			graphics.fill(x + 6, y, x + 8, bottom, color);
			int right = x + w - 10;
			Component day = Component.translatable("journal.jaymoskingdoms.past.day", e.day());
			graphics.text(this.font, day, right - this.font.width(day), y + 3, COLOR_DIM);
			right -= this.font.width(day) + 8;
			Component how = Component.translatable("journal.jaymoskingdoms.past.how." + Mth.clamp(e.how(), 0, 2));
			graphics.text(this.font, how, right - this.font.width(how), y + 3, color);
			right -= this.font.width(how) + 8;
			Ui.line(graphics, this.font, e.title(), x + 12, y + 3, right - x - 12, COLOR_TEXT);
			int ly = y + PAST_HEAD;
			for (FormattedCharSequence line : pastText.get(i)) {
				if (ly + Ui.LINE > top + view) {
					break;
				}
				graphics.text(this.font, line, x + 12, ly, COLOR_DIM);
				ly += Ui.LINE;
			}
			y += h + PAST_GAP;
		}
		Ui.scrollbar(graphics, x + w - 4, top, view, past.size() - most, past.size(), scroll);
	}

	// ---- map -------------------------------------------------------------------------------------------------------------

	private void map(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		LocalPlayer player = this.minecraft.player;
		ClientLevel level = this.minecraft.level;
		if (player == null || level == null) {
			return;
		}
		int size = mapSize();
		int n = size / CELL;
		int scale = ZOOMS[zoom];
		if (cells == null || drawnZoom != zoom || drawnSize != size) {
			cells = draw(level, MapBand.at(level, player.getY()), player.getBlockX(), player.getBlockZ(), scale, n);
			drawnX = player.getBlockX();
			drawnZ = player.getBlockZ();
			drawnZoom = zoom;
			drawnSize = size;
		}
		Ui.well(graphics, x - 1, y - 1, size + 2, size + 2);
		for (int row = 0; row < n; row++) {
			int run = 0;
			// (a row's cells of one colour are filled as one)
			for (int col = 1; col <= n; col++) {
				if (col == n || cells[row * n + col] != cells[row * n + run]) {
					graphics.fill(x + run * CELL, y + row * CELL, x + col * CELL, y + (row + 1) * CELL, cells[row * n + run]);
					run = col;
				}
			}
		}
		List<Component> hover = new ArrayList<>();
		double half = size / 2.0;
		double perPixel = scale / (double)CELL;
		if (book.here()) {
			for (Journal.Place p : book.places()) {
				int color = 0xFF000000 | p.color();
				int[] at = marker(x, y, p.seat(), perPixel, half);
				boolean camp = p.type() == FactionType.BARBARIAN.ordinal();
				int markerSize = p.realm() ? 4 : 3;
				graphics.fill(at[0] - markerSize - 1, at[1] - markerSize - 1, at[0] + markerSize + 1, at[1] + markerSize + 1, camp ? 0xFF8A1010 : 0xFF000000);
				graphics.fill(at[0] - markerSize, at[1] - markerSize, at[0] + markerSize, at[1] + markerSize, color);
				if (Math.abs(mouseX - at[0]) <= markerSize + 1 && Math.abs(mouseY - at[1]) <= markerSize + 1) {
					hover.add(Component.literal(p.name()));
					hover.add(Component.translatable(p.kind()).withColor(COLOR_DIM));
					if (p.rated()) {
						hover.add(Component.translatable("journal.jaymoskingdoms.rep", Component.translatable("journal.jaymoskingdoms.word." + p.word()), p.reputation())
							.withColor(repColor(p.reputation())));
					}
				}
			}
			int number = 0;
			for (Journal.Entry e : book.entries()) {
				number++;
				if (e.target().isEmpty()) {
					continue;
				}
				int[] at = marker(x, y, e.target().get(), perPixel, half);
				int color = e.ready() ? COLOR_DONE : e.royal() ? COLOR_ROYAL : COLOR_GOLD;
				graphics.fill(at[0] - 4, at[1] - 5, at[0] + 4, at[1] + 5, 0xFF000000);
				String label = Integer.toString(number);
				graphics.centeredText(this.font, label, at[0], at[1] - 4, color);
				if (Math.abs(mouseX - at[0]) <= 4 && Math.abs(mouseY - at[1]) <= 5) {
					hover.add(e.title().copy().withColor(color));
					hover.add(e.objective().copy().withColor(COLOR_DIM));
				}
			}
		}
		// you: a white arrowhead, pointing the way you face
		int[] me = marker(x, y, player.blockPosition(), perPixel, half);
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		for (int i = -1; i <= 3; i++) {
			int px = me[0] + (int)Math.round(fx * i), pz = me[1] + (int)Math.round(fz * i);
			graphics.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFF000000);
		}
		for (int i = -1; i <= 3; i++) {
			int px = me[0] + (int)Math.round(fx * i), pz = me[1] + (int)Math.round(fz * i);
			graphics.fill(px - 1, pz - 1, px + 1, pz + 1, 0xFFFFFFFF);
		}
		// beside it: the legend, where you stand
		int lx = x + size + 12, ly = y + 24;
		legend(graphics, lx, ly, 0xFFFFFFFF, "you");
		legend(graphics, lx, ly += 12, COLOR_GOLD, "quest");
		legend(graphics, lx, ly += 12, COLOR_ROYAL, "decree");
		legend(graphics, lx, ly += 12, COLOR_DONE, "ready");
		legend(graphics, lx, ly += 12, 0xFF808080, "settlement");
		ly += 18;
		graphics.text(this.font, Component.translatable("journal.jaymoskingdoms.at", player.getBlockX(), player.getBlockZ()), lx, ly, COLOR_DIM);
		if (!book.here()) {
			for (FormattedCharSequence line : this.font.split(Component.translatable("journal.jaymoskingdoms.elsewhere"), left() + panelW() - 9 - lx)) {
				graphics.text(this.font, line, lx, ly += 10, COLOR_DIM);
			}
		}
		if (!hover.isEmpty()) {
			Ui.tip(graphics, this.font, hover, mouseX, mouseY);
		}
	}

	private void legend(GuiGraphicsExtractor graphics, int x, int y, int color, String what) {
		graphics.fill(x, y, x + 7, y + 7, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + 6, y + 6, color);
		Ui.line(graphics, this.font, Component.translatable("journal.jaymoskingdoms.legend." + what), x + 11, y, left() + panelW() - 9 - x - 11, COLOR_TEXT);
	}

	/** Where {@code pos} goes on the map (screen px), held to its edge when it's off it. */
	private int[] marker(int x, int y, BlockPos pos, double perPixel, double half) {
		double px = (pos.getX() - drawnX) / perPixel + half, pz = (pos.getZ() - drawnZ) / perPixel + half;
		int size = mapSize();
		return new int[] {x + (int)Mth.clamp(px, 3, size - 3), y + (int)Mth.clamp(pz, 4, size - 4)};
	}

	/**
	 * The land about ({@code cx}, {@code cz}) as a map draws it: one sample per cell, shaded by the slope from the north. Only
	 * the ground of {@code band} (in a stacked world, the realm you stand in, not the Aether's islands over it).
	 */
	private static int[] draw(ClientLevel level, MapBand.Band band, int cx, int cz, int scale, int n) {
		int[] out = new int[n * n];
		int[] heights = new int[n];
		for (int row = -1; row < n; row++) {
			int z = cz + (row - n / 2) * scale;
			for (int col = 0; col < n; col++) {
				int x = cx + (col - n / 2) * scale;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					if (row >= 0) {
						out[row * n + col] = COLOR_PARCHMENT;
					}
					heights[col] = Integer.MIN_VALUE;
					continue;
				}
				int top = MapBand.surface(level, band, x, z);
				BlockPos pos = new BlockPos(x, top, z);
				BlockState state = level.getBlockState(pos);
				MapColor color = state.getMapColor(level, pos);
				int above = heights[col];
				heights[col] = top;
				if (row < 0) {
					continue;
				}
				if (color == MapColor.NONE || top <= band.bottom()) {
					out[row * n + col] = COLOR_PARCHMENT;
					continue;
				}
				MapColor.Brightness light = above == Integer.MIN_VALUE || color == MapColor.WATER ? MapColor.Brightness.NORMAL
					: top > above ? MapColor.Brightness.HIGH : top < above ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				out[row * n + col] = 0xFF000000 | color.calculateARGBColor(light);
			}
		}
		return out;
	}

	// ---- reputation ------------------------------------------------------------------------------------------------------

	/** A row per settlement that rates you: its colour, name, kind and way, and what it thinks of you on a bar. */
	private void reputation(GuiGraphicsExtractor graphics, int x, int top, int mouseX, int mouseY) {
		List<Journal.Place> rated = book.places().stream().filter(Journal.Place::rated).toList();
		int w = panelW();
		Ui.well(graphics, x + 5, top - 1, w - 10, repRows() * REP_ROW + 2);
		if (rated.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("journal.jaymoskingdoms.no_places"), x + w / 2, top + 20, COLOR_DIM);
			return;
		}
		scroll = Mth.clamp(scroll, 0, Math.max(0, rated.size() - repRows()));
		LocalPlayer player = this.minecraft.player;
		int end = Math.min(rated.size(), scroll + repRows());
		for (int i = scroll; i < end; i++) {
			Journal.Place p = rated.get(i);
			int ty = top + (i - scroll) * REP_ROW;
			boolean hover = Ui.over(mouseX, mouseY, x + 6, ty, w - 12, REP_ROW - 2);
			Ui.row(graphics, x + 6, ty, x + w - 6, ty + REP_ROW - 2, i, hover);
			graphics.fill(x + 6, ty, x + 8, ty + REP_ROW - 2, 0xFF000000 | p.color());
			int bw = 90, bx = x + w - bw - 12;
			Ui.line(graphics, this.font, Component.literal(p.name()), x + 12, ty + 2, bx - x - 20, COLOR_TEXT);
			Component kind = Component.translatable(p.kind());
			if (player != null && book.here()) {
				kind = Component.translatable("journal.jaymoskingdoms.kind_where", kind, where(player, p.seat()));
			}
			Ui.line(graphics, this.font, kind, x + 12, ty + 11, bx - x - 20, COLOR_DIM);
			// the bar: -1000 at its left, 1000 at its right, the middle marked
			int by = ty + 13;
			graphics.fill(bx, by, bx + bw, by + 3, Ui.BAR_BACK);
			int mid = bx + bw / 2;
			int at = bx + (int)((Mth.clamp(p.reputation(), -1000, 1000) + 1000) / 2000.0 * bw);
			graphics.fill(Math.min(mid, at), by, Math.max(mid, at) + 1, by + 3, repColor(p.reputation()));
			graphics.fill(mid, by - 1, mid + 1, by + 4, COLOR_DIM);
			Component word = Component.translatable("journal.jaymoskingdoms.rep", Component.translatable("journal.jaymoskingdoms.word." + p.word()), p.reputation());
			graphics.text(this.font, word, bx + bw - this.font.width(word), ty + 2, repColor(p.reputation()));
		}
		Ui.scrollbar(graphics, x + w - 4, top, repRows() * REP_ROW, repRows(), rated.size(), scroll);
	}

	static int repColor(int rep) {
		return rep <= -100 ? COLOR_HATED : rep < 100 ? COLOR_DIM : rep < 400 ? 0xFFB0E070 : COLOR_DONE;
	}
}
