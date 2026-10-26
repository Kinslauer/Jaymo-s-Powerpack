package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.Corruption;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Phials;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.alchemy.codex.Codex;
import com.jaymospowers.alchemy.codex.CodexNet;
import com.jaymospowers.client.UiStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Apothecary's Codex: a tab for each {@link Research.Category}, with its entries drawn where they say ({@code col},
 * {@code row}) as icons joined to their parents by lines; known ones lit, ones whose parents you know dim, the rest
 * hidden (forbidden lore stays hidden until your corruption reaches it). Click an entry for its text and price and
 * unlock it with the insight the lens has earned. A last tab lists the essences you have discovered, with what each is
 * made of. Drag (or scroll) to move over a tab that doesn't fit. Drawn in the H screen's style ({@link UiStyle}).
 * Works for any set of entries: other parts of alchemy register their own.
 */
public class CodexScreen extends Screen {
	static final int MAX_W = 380, MAX_H = 250;
	/** Space between the entries' grid cells, and the size of an entry's box. */
	static final int CELL = 38, NODE = 24, MARGIN = 14;
	static final int TAB_H = 22, FOOT_H = 16, COST_H = 22;
	static final int GOOD = 0xFF6BE07A, BAD = 0xFFE06B6B, LINE_DIM = 0xFF4A4A5C, BOX = 0xFF0C0C12, BOX_EDGE = 0xFF50505C;
	static final int ROW_H = 18;
	/** Each tab's accent: the categories in order, then the essences page. */
	static final int[] ACCENTS = {0xFFC9B8FF, 0xFFE2A04A, 0xFF9B4DFF, 0xFFB05AD8, 0xFFF5C542, 0xFF6BD0FF, 0xFF7FE0D8};

	/** Where the screen was left, so it opens on the same tab. */
	private static int lastTab;

	private int tab = lastTab;
	private final int[] panX = new int[ACCENTS.length], panY = new int[ACCENTS.length];
	private final boolean[] panned = new boolean[ACCENTS.length];
	private int listScroll;
	private @Nullable String picked;

	public CodexScreen() {
		super(Component.translatable("screen.jaymospowers.codex"));
	}

	private static int tabs() {
		return Research.Category.values().length + 1;
	}

	private boolean essencePage() {
		return tab == Research.Category.values().length;
	}

	private int accent() {
		return ACCENTS[Mth.clamp(tab, 0, ACCENTS.length - 1)];
	}

	private ItemStack tabIcon(int i) {
		return switch (i) {
			case 0 -> new ItemStack(Items.BOOK);
			case 1 -> new ItemStack(Items.CAULDRON);
			case 2 -> new ItemStack(Items.AMETHYST_SHARD);
			case 3 -> new ItemStack(Items.SPIDER_EYE);
			case 4 -> new ItemStack(Items.BLAZE_ROD);
			case 5 -> new ItemStack(Items.ENCHANTED_BOOK);
			default -> Phials.of(Essence.ARCANA, Phials.SIZE);
		};
	}

	// --- layout ---

	private int panelW() {
		return Math.min(MAX_W, width - 12);
	}

	private int panelH() {
		return Math.min(MAX_H, height - 8);
	}

	private int left() {
		return (width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (height - panelH()) / 2);
	}

	private int detailLines() {
		return panelH() >= 220 ? 4 : 3;
	}

	private int footY() {
		return top() + panelH() - FOOT_H;
	}

	private int costY() {
		return footY() - COST_H;
	}

	private int detailY() {
		return costY() - UiStyle.detailHeight(detailLines());
	}

	private int graphX() {
		return left() + 4;
	}

	private int graphY() {
		return top() + UiStyle.HEADER + TAB_H + 2;
	}

	private int graphW() {
		return panelW() - 8;
	}

	private int graphH() {
		return detailY() - 3 - graphY();
	}

	private int tabW() {
		return (panelW() - 12) / tabs();
	}

	// --- what the player sees ---

	private enum State {
		KNOWN, UNLOCKABLE
	}

	private record Node(Research.Entry entry, State state, int x, int y) {
	}

	private LocalPlayer player() {
		return minecraft.player;
	}

	private @Nullable State stateOf(Research.Entry entry) {
		LocalPlayer player = player();
		if (Knowledge.knows(player, entry.id())) {
			return State.KNOWN;
		}
		for (String parent : entry.parents()) {
			if (!Knowledge.knows(player, parent)) {
				return null;
			}
		}
		return entry.corruption() > 0 && Corruption.total(player) < entry.corruption() ? null : State.UNLOCKABLE;
	}

	private List<Node> nodes() {
		List<Node> out = new ArrayList<>();
		if (essencePage()) {
			return out;
		}
		Research.Category category = Research.Category.values()[tab];
		int minCol = Integer.MAX_VALUE, minRow = Integer.MAX_VALUE, maxCol = 0, maxRow = 0;
		List<Research.Entry> shown = new ArrayList<>();
		List<State> states = new ArrayList<>();
		for (Research.Entry entry : Research.all()) {
			State state = entry.category() == category ? stateOf(entry) : null;
			if (state != null) {
				shown.add(entry);
				states.add(state);
				minCol = Math.min(minCol, entry.col());
				minRow = Math.min(minRow, entry.row());
				maxCol = Math.max(maxCol, entry.col());
				maxRow = Math.max(maxRow, entry.row());
			}
		}
		if (shown.isEmpty()) {
			return out;
		}
		// the grid opens centred on first sight, and can be dragged anywhere that keeps its content in view
		int left = minCol * CELL, right = maxCol * CELL + NODE, top = minRow * CELL, bottom = maxRow * CELL + NODE;
		int ax = MARGIN - left, bx = graphW() - right - MARGIN, ay = MARGIN - top, by = graphH() - bottom - MARGIN;
		if (!panned[tab]) {
			panX[tab] = (ax + bx) / 2;
			panY[tab] = (ay + by) / 2;
			panned[tab] = true;
		}
		panX[tab] = Mth.clamp(panX[tab], Math.min(ax, bx), Math.max(ax, bx));
		panY[tab] = Mth.clamp(panY[tab], Math.min(ay, by), Math.max(ay, by));
		for (int i = 0; i < shown.size(); i++) {
			Research.Entry entry = shown.get(i);
			out.add(new Node(entry, states.get(i), graphX() + panX[tab] + entry.col() * CELL, graphY() + panY[tab] + entry.row() * CELL));
		}
		return out;
	}

	private @Nullable Node nodeAt(List<Node> nodes, int mouseX, int mouseY) {
		if (!UiStyle.over(mouseX, mouseY, graphX(), graphY(), graphW(), graphH())) {
			return null;
		}
		for (Node node : nodes) {
			if (UiStyle.over(mouseX, mouseY, node.x(), node.y(), NODE, NODE)) {
				return node;
			}
		}
		return null;
	}

	private @Nullable Node pickedNode(List<Node> nodes) {
		for (Node node : nodes) {
			if (node.entry().id().equals(picked)) {
				return node;
			}
		}
		return null;
	}

	private boolean affordable(Research.Entry entry) {
		return player().isCreative() || Knowledge.of(player()).insight().covers(entry.cost());
	}

	// --- drawing ---

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW();
		int accent = accent();
		UiStyle.panel(graphics, x, y, w, panelH(), accent);
		int found = Knowledge.of(player()).seen().size();
		UiStyle.header(graphics, font, x, y, w, new ItemStack(Codex.CODEX), title,
			Component.translatable("gui.jaymospowers.codex.sub", player().isCreative() ? Essence.values().length : found, Essence.values().length),
			accent, 0);
		drawTabs(graphics, mouseX, mouseY);

		List<Node> nodes = nodes();
		Node hovered = nodeAt(nodes, mouseX, mouseY);
		graphics.fill(graphX(), graphY(), graphX() + graphW(), graphY() + graphH(), 0x40000000);
		graphics.enableScissor(graphX(), graphY(), graphX() + graphW(), graphY() + graphH());
		if (essencePage()) {
			drawEssences(graphics, mouseX, mouseY);
		} else {
			drawGraph(graphics, nodes, hovered);
		}
		graphics.disableScissor();
		if (!essencePage() && nodes.isEmpty()) {
			UiStyle.lineCentred(graphics, font, Component.translatable("gui.jaymospowers.codex.empty"), graphX() + graphW() / 2,
				graphY() + graphH() / 2 - 4, graphW() - 16, UiStyle.HINT);
		}

		drawDetail(graphics, mouseX, mouseY, nodes);
		drawInsight(graphics, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		drawTooltips(graphics, mouseX, mouseY, hovered);
	}

	private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int tw = tabW(), ty = top() + UiStyle.HEADER;
		for (int i = 0; i < tabs(); i++) {
			int tx = left() + 6 + i * tw;
			boolean on = i == tab, hover = UiStyle.over(mouseX, mouseY, tx, ty, tw, TAB_H);
			graphics.fill(tx, ty, tx + tw - 2, ty + TAB_H - 1, on ? UiStyle.ROW_PICKED : hover ? UiStyle.ROW_HOVER : UiStyle.ROW_ALT);
			if (on) {
				graphics.fill(tx, ty + TAB_H - 3, tx + tw - 2, ty + TAB_H - 1, ACCENTS[i]);
			}
			graphics.item(tabIcon(i), tx + (tw - 2 - 16) / 2, ty + 2);
		}
	}

	private void drawGraph(GuiGraphicsExtractor graphics, List<Node> nodes, @Nullable Node hovered) {
		// lines first, from each entry to its parents that are on this tab
		for (Node node : nodes) {
			for (String parent : node.entry().parents()) {
				for (Node other : nodes) {
					if (other.entry().id().equals(parent)) {
						connect(graphics, other, node, node.state() == State.KNOWN ? accent() : LINE_DIM);
					}
				}
			}
		}
		for (Node node : nodes) {
			boolean known = node.state() == State.KNOWN;
			boolean isPicked = node.entry().id().equals(picked), hover = node == hovered;
			int edge = isPicked ? 0xFFFFFFFF : known ? accent() : hover ? 0xFF8A8A9A : BOX_EDGE;
			graphics.fill(node.x() - 1, node.y() - 1, node.x() + NODE + 1, node.y() + NODE + 1, edge);
			graphics.fill(node.x(), node.y(), node.x() + NODE, node.y() + NODE, known ? 0xFF1C1A2C : BOX);
			ItemStack icon = new ItemStack(node.entry().icon().get());
			graphics.item(icon, node.x() + (NODE - 16) / 2, node.y() + (NODE - 16) / 2);
			if (!known) {
				graphics.fill(node.x(), node.y(), node.x() + NODE, node.y() + NODE, hover ? 0x60000000 : 0x90000000);
			}
		}
	}

	/** An L of lines from one box to another: out along the row, then along the column. */
	private void connect(GuiGraphicsExtractor graphics, Node from, Node to, int colour) {
		int x1 = from.x() + NODE / 2, y1 = from.y() + NODE / 2, x2 = to.x() + NODE / 2, y2 = to.y() + NODE / 2;
		int mid = x1 == x2 ? x1 : x1 < x2 ? to.x() - (CELL - NODE) / 2 : to.x() + NODE + (CELL - NODE) / 2;
		if (y1 == y2) {
			graphics.fill(Math.min(x1, x2), y1 - 1, Math.max(x1, x2) + 1, y1 + 1, colour);
			return;
		}
		if (x1 == x2) {
			graphics.fill(x1 - 1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, colour);
			return;
		}
		graphics.fill(Math.min(x1, mid), y1 - 1, Math.max(x1, mid) + 1, y1 + 1, colour);
		graphics.fill(mid - 1, Math.min(y1, y2), mid + 1, Math.max(y1, y2) + 1, colour);
		graphics.fill(Math.min(mid, x2), y2 - 1, Math.max(mid, x2) + 1, y2 + 1, colour);
	}

	/** The essences page: each discovered essence with what it is made of and the insight you hold in it. */
	private void drawEssences(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<Essence> seen = seenEssences();
		int rows = Math.max(1, graphH() / ROW_H);
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, seen.size() - rows));
		if (seen.isEmpty()) {
			UiStyle.lineCentred(graphics, font, Component.translatable("gui.jaymospowers.codex.no_essences"), graphX() + graphW() / 2,
				graphY() + graphH() / 2 - 4, graphW() - 16, UiStyle.HINT);
			return;
		}
		EssenceList insight = Knowledge.of(player()).insight();
		for (int i = 0; i < rows && listScroll + i < seen.size(); i++) {
			Essence e = seen.get(listScroll + i);
			int ry = graphY() + i * ROW_H;
			UiStyle.row(graphics, graphX() + 2, ry, graphX() + graphW() - 6, ry + ROW_H - 1, i, UiStyle.over(mouseX, mouseY, graphX(), ry, graphW(), ROW_H));
			CodexIcons.draw(graphics, e, graphX() + 6, ry + 1, 16);
			Component name = Component.translatable(e.key()).withColor(CodexIcons.colour(e));
			graphics.text(font, name, graphX() + 28, ry + 5, 0xFFFFFFFF);
			Component made = e.primal() ? Component.translatable("gui.jaymospowers.codex.primal")
				: Component.translatable("gui.jaymospowers.codex.made_of", Component.translatable(e.a.key()), Component.translatable(e.b.key()));
			UiStyle.line(graphics, font, made, graphX() + 28 + 72, ry + 5, graphW() - 28 - 72 - 70, UiStyle.DIM);
			UiStyle.lineRight(graphics, font, Component.translatable("gui.jaymospowers.codex.insight_of", insight.get(e)), graphX() + graphW() - 10, ry + 5, 60,
				insight.get(e) > 0 ? GOOD : UiStyle.HINT);
		}
		UiStyle.scrollbar(graphics, graphX() + graphW() - 3, graphY(), rows * ROW_H, rows, seen.size(), listScroll);
	}

	private List<Essence> seenEssences() {
		List<Essence> out = new ArrayList<>();
		for (Essence e : Essence.values()) {
			if (Knowledge.hasSeen(player(), e)) {
				out.add(e);
			}
		}
		return out;
	}

	private void drawDetail(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<Node> nodes) {
		Node node = pickedNode(nodes);
		int x = left(), w = panelW();
		if (node == null) {
			UiStyle.detail(graphics, font, x, detailY(), w, detailLines(), null, accent(),
				List.of(Component.translatable(essencePage() ? "gui.jaymospowers.codex.hint_essences" : "gui.jaymospowers.codex.hint")));
			return;
		}
		Research.Entry entry = node.entry();
		boolean known = node.state() == State.KNOWN;
		UiStyle.detail(graphics, font, x, detailY(), w, detailLines(), Component.translatable(entry.key()), accent(),
			List.of(Component.translatable(entry.key() + ".text")));
		if (known) {
			UiStyle.lineRight(graphics, font, Component.translatable("gui.jaymospowers.codex.known"), x + w - 10, detailY() + 5, 80, GOOD);
			return;
		}
		// the price, against what you hold
		int cx = x + 9, cy = costY() + 5;
		if (entry.cost().isEmpty()) {
			graphics.text(font, Component.translatable("gui.jaymospowers.codex.free"), cx, cy + 2, UiStyle.DIM);
		} else {
			EssenceList held = Knowledge.of(player()).insight();
			boolean free = player().isCreative();
			for (Map.Entry<Essence, Integer> en : com.jaymospowers.alchemy.codex.Lens.sorted(entry.cost())) {
				Essence e = en.getKey();
				boolean seen = Knowledge.hasSeen(player(), e);
				CodexIcons.drawKnown(graphics, font, player(), e, cx, cy, 12);
				String text = seen ? held.get(e) + "/" + en.getValue() : "x" + en.getValue();
				graphics.text(font, text, cx + 14, cy + 2, free || seen && held.get(e) >= en.getValue() ? GOOD : BAD);
				cx += 14 + font.width(text) + 10;
			}
		}
		boolean can = affordable(entry);
		int bx = buttonX(), by = costY() + 3;
		boolean hover = UiStyle.over(mouseX, mouseY, bx, by, buttonW(), 14);
		graphics.fill(bx, by, bx + buttonW(), by + 14, can ? hover ? 0xC0603CB0 : 0xA0402880 : 0x60303030);
		graphics.fill(bx, by + 13, bx + buttonW(), by + 14, can ? accent() : UiStyle.HINT);
		UiStyle.lineCentred(graphics, font, Component.translatable("gui.jaymospowers.codex.unlock"), bx + buttonW() / 2, by + 3, buttonW() - 4,
			can ? UiStyle.TEXT : UiStyle.HINT);
	}

	private int buttonW() {
		return 64;
	}

	private int buttonX() {
		return left() + panelW() - 8 - buttonW();
	}

	/** The foot: the insight you hold, essence by essence. */
	private void drawInsight(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left(), y = footY();
		UiStyle.rule(graphics, x + 6, x + panelW() - 6, y);
		int cx = x + 9;
		graphics.text(font, Component.translatable("gui.jaymospowers.codex.insight"), cx, y + 5, UiStyle.DIM);
		cx += font.width(Component.translatable("gui.jaymospowers.codex.insight")) + 8;
		EssenceList insight = Knowledge.of(player()).insight();
		boolean any = false;
		for (Essence e : Essence.values()) {
			int n = insight.get(e);
			if (n <= 0 || !Knowledge.hasSeen(player(), e)) {
				continue;
			}
			String text = String.valueOf(n);
			if (cx + 12 + 2 + font.width(text) > x + panelW() - 8) {
				graphics.text(font, "...", cx, y + 5, UiStyle.DIM);
				return;
			}
			CodexIcons.draw(graphics, e, cx, y + 3, 11);
			graphics.text(font, text, cx + 13, y + 5, 0xFF000000 | CodexIcons.colour(e));
			cx += 13 + font.width(text) + 8;
			any = true;
		}
		if (!any) {
			graphics.text(font, Component.translatable("gui.jaymospowers.codex.none"), cx, y + 5, UiStyle.HINT);
		}
	}

	private void drawTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY, @Nullable Node hovered) {
		int tw = tabW(), ty = top() + UiStyle.HEADER;
		for (int i = 0; i < tabs(); i++) {
			if (UiStyle.over(mouseX, mouseY, left() + 6 + i * tw, ty, tw, TAB_H)) {
				String key = i < Research.Category.values().length ? Research.Category.values()[i].name().toLowerCase() : "essences";
				graphics.setTooltipForNextFrame(font, Component.translatable("gui.jaymospowers.codex.tab." + key), mouseX, mouseY);
				return;
			}
		}
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, Component.translatable(hovered.entry().key()), mouseX, mouseY);
		}
	}

	// --- input ---

	private void setTab(int i) {
		tab = i;
		lastTab = i;
		picked = null;
		listScroll = 0;
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int) event.x(), my = (int) event.y();
		if (event.button() == 0) {
			int tw = tabW(), ty = top() + UiStyle.HEADER;
			for (int i = 0; i < tabs(); i++) {
				if (UiStyle.over(mx, my, left() + 6 + i * tw, ty, tw, TAB_H)) {
					setTab(i);
					return true;
				}
			}
			List<Node> nodes = nodes();
			Node selected = pickedNode(nodes);
			if (selected != null && selected.state() == State.UNLOCKABLE && affordable(selected.entry())
				&& UiStyle.over(mx, my, buttonX(), costY() + 3, buttonW(), 14)) {
				ClientPlayNetworking.send(new CodexNet.Unlock(selected.entry().id()));
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
			Node hit = nodeAt(nodes, mx, my);
			if (hit != null) {
				picked = hit.entry().id();
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0 && !essencePage() && UiStyle.over((int) event.x(), (int) event.y(), graphX(), graphY(), graphW(), graphH())) {
			panX[tab] += (int) Math.round(dx);
			panY[tab] += (int) Math.round(dy);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (UiStyle.over((int) x, (int) y, graphX(), graphY(), graphW(), graphH())) {
			if (essencePage()) {
				listScroll = Math.max(0, listScroll - (int) Math.signum(scrollY));
			} else {
				panY[tab] += (int) Math.round(scrollY * CELL / 2.0);
				panX[tab] += (int) Math.round(scrollX * CELL / 2.0);
			}
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
