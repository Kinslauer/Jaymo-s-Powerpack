package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.net.PowerPayloads;
import com.jaymospowers.power.chrono.ChronoDirectives;
import com.jaymospowers.power.chrono.ChronoPower;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The powers screen (H), styled like the Z Fighter training screen: a header with the power and its mastery, one
 * striped row per key (the key, the ability's short name, its text cut short) and a box at the bottom that shows the
 * hovered row in full. In creative a list of every power runs down the left, to look through and choose; in survival
 * it's just your power (or that it's still dormant).
 */
public class PowerScreen extends Screen {
	private static final int MAX_W = 380;
	/** The creative list of powers down the left. */
	private static final int LIST_W = 112;
	private static final int LIST_ROW = 20;
	private static final int HEADER = 34;
	private static final int ROW = 16;
	/** The box at the bottom that shows the hovered row's whole text: tall enough for the longest row, within these. */
	private static final int DETAIL_MIN_LINES = 4;
	private static final int DETAIL_MAX_LINES = 9;
	private static final int FOOTER = 22;
	/** The widest the short-name column gets. */
	private static final int NAME_W = 136;
	private static final int COLOR_PANEL = 0xD0101018;
	private static final int COLOR_ROW = 0x30FFFFFF;
	private static final int COLOR_ROW_ALT = 0x18FFFFFF;
	private static final int COLOR_ROW_HOVER = 0x50FFFFFF;
	private static final int COLOR_CHIP = 0xA0000000;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA8A8B8;
	private static final int COLOR_BAR_BACK = 0xFF303030;

	private final List<Power> powers = new ArrayList<>(Powers.all());
	/** The power being shown (creative can look through any of them). */
	private @Nullable Power selected;
	/** How many key rows are scrolled past, when they don't all fit. */
	private int scroll;
	private @Nullable Button chooseButton;
	/** The echo's orders, then Record ({@link ChronoDirectives#RECORD_BUTTON}). */
	private final List<Button> orderButtons = new ArrayList<>();

	public PowerScreen() {
		super(Component.translatable("screen.jaymospowers.title"));
		// outside creative, the screen is about your own power only
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		Power own = player == null ? null : Powers.of(player);
		if (player != null && !player.isCreative()) {
			this.powers.clear();
			if (own != null) {
				this.powers.add(own);
			}
		} else {
			// a power fate can't give (Xenoform: the band only) isn't offered to pick, unless it's already yours
			this.powers.removeIf(power -> !power.fated() && power != own);
		}
		this.selected = own != null && this.powers.contains(own) ? own : this.powers.isEmpty() ? null : this.powers.getFirst();
	}

	private boolean creative() {
		return this.minecraft.player != null && this.minecraft.player.isCreative();
	}

	private @Nullable Power current() {
		return this.minecraft.player == null ? null : Powers.of(this.minecraft.player);
	}

	/** The player, for their own power (its lines follow their state); null for the others. */
	private @Nullable Player owner(Power power) {
		return this.minecraft.player != null && power == current() ? this.minecraft.player : null;
	}

	private List<KeyGuide.Line> lines() {
		return selected == null ? List.of() : KeyGuide.lines(selected, owner(selected));
	}

	/** Whether the panel ends in a row of buttons: creative's choose/clear, or a Timeless Chrono's echo orders. */
	private boolean footer() {
		return creative() || orders();
	}

	/** (0.15.143) A Timeless Chrono's echo takes orders from here. */
	private boolean orders() {
		Player player = this.minecraft.player;
		return player != null && !player.isCreative() && Powers.of(player) == ChronoPower.INSTANCE && ChronoDirectives.commandable(player);
	}

	private int listW() {
		return creative() ? LIST_W : 0;
	}

	private int panelW() {
		return Math.min(creative() ? MAX_W + 40 : MAX_W, this.width - 12);
	}

	/** The most key rows the panel makes room for (in creative, the most any power has). */
	private int rowsWanted() {
		// creative keeps one height for every power, so the panel doesn't jump as you look through them
		int rows = Math.max(1, lines().size());
		if (creative()) {
			for (Power power : powers) {
				rows = Math.max(rows, KeyGuide.lines(power, owner(power)).size());
			}
		}
		return rows;
	}

	/** Tall enough for every row (or the creative list), clamped to the screen. */
	private int panelH() {
		int h = HEADER + rowsWanted() * ROW + 6 + 14 + detailWanted() * 9 + (footer() ? FOOTER : 0);
		if (creative()) {
			h = Math.max(h, HEADER + powers.size() * LIST_ROW + FOOTER + 8);
		}
		return Math.min(h, this.height - 8);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	/** Where the right-hand pane (the selected power) starts. */
	private int paneX() {
		return left() + listW();
	}

	private int paneW() {
		return panelW() - listW();
	}

	private int rowsY() {
		return top() + HEADER;
	}

	private int detailY() {
		return top() + panelH() - detailH() - (footer() ? FOOTER : 0);
	}

	/** Lines in the detail box: as many as it wants, unless the rows need the room (never under the minimum). */
	private int detailLines() {
		int room = (panelH() - HEADER - rowsWanted() * ROW - 6 - 14 - (footer() ? FOOTER : 0)) / 9;
		return Math.max(DETAIL_MIN_LINES, Math.min(detailWanted(), room));
	}

	/** The hovered row's name and every line of the longest row's text (or the description). */
	private int detailWanted() {
		int wrap = paneW() - 18;
		int most = 0;
		// creative keeps one size for every power, so the panel doesn't jump as you look through them
		for (Power power : creative() ? powers : selected == null ? List.<Power>of() : List.of(selected)) {
			most = Math.max(most, this.font.split(power.description(), wrap).size() + 1);
			for (KeyGuide.Line line : KeyGuide.lines(power, owner(power))) {
				most = Math.max(most, 1 + this.font.split(line.text(), wrap).size());
			}
		}
		return Mth.clamp(most, DETAIL_MIN_LINES, DETAIL_MAX_LINES);
	}

	private int detailH() {
		return 10 + detailLines() * 9 + 4;
	}

	/** How many key rows fit above the detail box. */
	private int visibleRows() {
		return Math.max(1, (detailY() - 6 - rowsY()) / ROW);
	}

	private int maxScroll() {
		return Math.max(0, lines().size() - visibleRows());
	}

	@Override
	protected void init() {
		chooseButton = null;
		orderButtons.clear();
		if (orders()) {
			int y = top() + panelH() - FOOTER + 3;
			int count = ChronoDirectives.RECORD_BUTTON + 1;
			int gap = 3;
			int bw = (paneW() - 12 - gap * (count - 1)) / count;
			for (int i = 0; i < count; i++) {
				int button = i;
				String name = i == ChronoDirectives.RECORD_BUTTON ? "record"
					: ChronoDirectives.Directive.values()[i].name().toLowerCase(java.util.Locale.ROOT);
				Button b = Button.builder(Component.translatable("screen.jaymospowers.order." + name),
						press -> ClientPlayNetworking.send(new ChronoDirectives.Order(button)))
					.bounds(paneX() + 6 + i * (bw + gap), y, bw, 16)
					.tooltip(Tooltip.create(Component.translatable("screen.jaymospowers.order." + name + ".desc")))
					.build();
				orderButtons.add(addRenderableWidget(b));
			}
			return;
		}
		if (!creative()) {
			return;
		}
		int y = top() + panelH() - FOOTER + 3;
		chooseButton = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.choose"), b -> {
			if (selected != null) {
				choose(selected.id().toString());
			}
		}).bounds(paneX() + paneW() - 86, y, 80, 16).build());
		Button clear = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.clear"), b -> choose(""))
			.bounds(left() + 6, y, LIST_W - 4, 16).build());
		clear.active = current() != null;
	}

	private void choose(String id) {
		ClientPlayNetworking.send(new PowerPayloads.ChoosePower(id));
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (creative()) {
			int x = left();
			for (int i = 0; i < powers.size(); i++) {
				int ry = rowsY() + i * LIST_ROW;
				if (over((int)event.x(), (int)event.y(), x + 4, ry, LIST_W - 4, LIST_ROW - 2) && ry + LIST_ROW <= top() + panelH() - FOOTER) {
					selected = powers.get(i);
					scroll = 0;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int)Math.signum(scrollY), 0, maxScroll());
		return true;
	}

	private static boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	/** One line of text, cut with "..." if it would run past {@code width}. */
	private void line(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		String s = text.getString();
		if (this.font.width(s) > width) {
			s = this.font.plainSubstrByWidth(s, Math.max(0, width - this.font.width("..."))) + "...";
		}
		graphics.text(this.font, s, x, y, color);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		scroll = Mth.clamp(scroll, 0, maxScroll());
		int x = left();
		int y = top();
		int w = panelW();
		int h = panelH();
		Power power = selected;
		int accent = power == null ? COLOR_DIM : 0xFF000000 | power.color();
		graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
		graphics.fill(x, y, x + w, y + 2, accent);

		if (creative()) {
			list(graphics, mouseX, mouseY);
		}
		if (power == null) {
			// dormant: nothing to show but that
			graphics.centeredText(this.font, this.title, this.width / 2, y + 8, COLOR_TEXT);
			var split = this.font.split(Component.translatable("screen.jaymospowers.dormant"), w - 24);
			for (int i = 0; i < split.size(); i++) {
				graphics.centeredText(this.font, split.get(i), this.width / 2, y + 24 + i * 10, COLOR_DIM);
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		header(graphics, power, accent);
		Component detailName = null;
		Component detail = power.description();
		int detailColor = accent;
		List<KeyGuide.Line> lines = lines();
		int px = paneX();
		int pw = paneW();
		int chipW = chipWidth(lines);
		int nameX = px + 10 + chipW + 6;
		int nameW = Math.min(NAME_W, nameWidth(lines));
		int textX = nameX + nameW + 8;
		int end = Math.min(lines.size(), scroll + visibleRows());
		for (int i = scroll; i < end; i++) {
			KeyGuide.Line line = lines.get(i);
			int ry = rowsY() + (i - scroll) * ROW;
			boolean hover = over(mouseX, mouseY, px + 6, ry, pw - 12, ROW - 1);
			graphics.fill(px + 6, ry, px + pw - 6, ry + ROW - 1, hover ? COLOR_ROW_HOVER : i % 2 == 0 ? COLOR_ROW : COLOR_ROW_ALT);
			// the key, in a dark chip
			graphics.fill(px + 9, ry + 2, px + 9 + chipW, ry + ROW - 3, COLOR_CHIP);
			graphics.fill(px + 9, ry + ROW - 4, px + 9 + chipW, ry + ROW - 3, line.locked() ? COLOR_DIM : accent);
			String key = this.font.plainSubstrByWidth(line.key().getString(), chipW - 4);
			graphics.centeredText(this.font, Component.literal(key), px + 9 + chipW / 2, ry + 4, line.locked() ? COLOR_DIM : COLOR_TEXT);
			Component name = line.name() != null ? line.name() : line.text();
			line(graphics, name, nameX, ry + 4, nameW, line.color());
			if (line.name() != null && !line.locked()) {
				line(graphics, Component.literal(glance(line)), textX, ry + 4, px + pw - 10 - textX, COLOR_DIM);
			}
			if (hover) {
				detailName = name;
				detail = line.text();
				detailColor = line.color();
			}
		}
		if (maxScroll() > 0) {
			// a thin scrollbar beside the rows
			int track = visibleRows() * ROW;
			int thumb = Math.max(8, track * visibleRows() / lines.size());
			int at = rowsY() + (track - thumb) * scroll / maxScroll();
			graphics.fill(px + pw - 4, rowsY(), px + pw - 2, rowsY() + track, 0x40FFFFFF);
			graphics.fill(px + pw - 4, at, px + pw - 2, at + thumb, 0xC0FFFFFF);
		}

		// the detail box: the hovered row in full, or the power's description
		int dy = detailY();
		graphics.fill(px + 6, dy, px + pw - 6, dy + 1, COLOR_ROW);
		int ty = dy + 5;
		int room = detailLines();
		if (detailName != null) {
			graphics.text(this.font, detailName, px + 9, ty, detailColor);
			ty += 10;
			room--;
		}
		List<FormattedCharSequence> split = this.font.split(detail, pw - 18);
		for (int i = 0; i < Math.min(room, split.size()); i++) {
			graphics.text(this.font, split.get(i), px + 9, ty + i * 9, detailName == null ? COLOR_DIM : COLOR_TEXT);
		}
		if (split.size() > room && room > 0) {
			// more than fits (a very small window): say so
			graphics.text(this.font, "...", px + 9 + this.font.width(split.get(room - 1)), ty + (room - 1) * 9, COLOR_DIM);
		}
		if (detailName == null && split.size() <= room - 1) {
			graphics.text(this.font, Component.translatable("screen.jaymospowers.hover_hint"), px + 9, ty + Math.min(room - 1, split.size()) * 9 + 2,
				0xFF707080);
		}
		if (!orderButtons.isEmpty()) {
			Integer shown = this.minecraft.player == null ? null : this.minecraft.player.getAttached(ChronoDirectives.SHOWN);
			int value = shown == null ? 0 : shown;
			boolean recording = (value & ChronoDirectives.RECORDING_FLAG) != 0;
			int directive = value & ~ChronoDirectives.RECORDING_FLAG;
			for (int i = 0; i < orderButtons.size(); i++) {
				Button b = orderButtons.get(i);
				if (i == ChronoDirectives.RECORD_BUTTON) {
					b.setMessage(Component.translatable(recording ? "screen.jaymospowers.order.stop" : "screen.jaymospowers.order.record"));
				} else {
					b.active = recording || i != directive;
				}
			}
		}
		if (chooseButton != null) {
			boolean active = power == current();
			chooseButton.setMessage(Component.translatable(active ? "screen.jaymospowers.current" : "screen.jaymospowers.choose"));
			chooseButton.active = !active;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The power's icon and name (as its fate has shaped it), and how far its mastery has come. */
	private void header(GuiGraphicsExtractor graphics, Power power, int accent) {
		int px = paneX();
		int pw = paneW();
		int y = top();
		Player player = owner(power);
		graphics.item(power.icon(), px + 8, y + 8);
		Component name = player != null ? PowerHud.title(power, Fate.state(player), player) : power.name();
		Component sub = creative() ? Component.translatable(power == current() ? "screen.jaymospowers.current" : "screen.jaymospowers.creative_hint")
			: Component.translatable("screen.jaymospowers.yours");
		int barW = 84;
		int textW = pw - 32 - barW - 14;
		line(graphics, name, px + 28, y + 7, textW, accent);
		line(graphics, sub, px + 28, y + 18, textW, COLOR_DIM);

		float progress = player != null && !player.isCreative() ? Mastery.progress(player) : -2.0F;
		if (progress > -2.0F) {
			int bx = px + pw - barW - 8;
			Component label = progress < 0.0F ? Component.translatable("screen.jaymospowers.mastered")
				: Component.translatable("screen.jaymospowers.mastery_bar", Math.round(progress * 100.0F));
			line(graphics, label, bx, y + 7, barW, progress < 0.0F ? accent : COLOR_TEXT);
			graphics.fill(bx, y + 19, bx + barW, y + 22, COLOR_BAR_BACK);
			graphics.fill(bx, y + 19, bx + Math.round(barW * (progress < 0.0F ? 1.0F : progress)), y + 22, accent);
		}
		graphics.fill(px + 6, y + HEADER - 4, px + pw - 6, y + HEADER - 3, COLOR_ROW);
	}

	/** Creative: every power down the left, the one shown highlighted. */
	private void list(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left();
		int y = top();
		graphics.text(this.font, this.title, x + 8, y + 8, COLOR_TEXT);
		graphics.fill(x + LIST_W - 1, y + 4, x + LIST_W, y + panelH() - FOOTER - 2, COLOR_ROW);
		Power current = current();
		int bottom = y + panelH() - FOOTER;
		for (int i = 0; i < powers.size(); i++) {
			Power power = powers.get(i);
			int ry = rowsY() + i * LIST_ROW;
			if (ry + LIST_ROW > bottom) {
				break;
			}
			boolean shown = power == selected;
			boolean hover = over(mouseX, mouseY, x + 4, ry, LIST_W - 8, LIST_ROW - 2);
			graphics.fill(x + 4, ry, x + LIST_W - 4, ry + LIST_ROW - 2, shown ? 0x40FFFFFF : hover ? COLOR_ROW_HOVER : i % 2 == 0 ? COLOR_ROW_ALT : 0x0CFFFFFF);
			if (shown) {
				graphics.fill(x + 4, ry, x + 6, ry + LIST_ROW - 2, 0xFF000000 | power.color());
			}
			graphics.item(power.icon(), x + 8, ry + 1);
			line(graphics, power.name(), x + 27, ry + 5, LIST_W - 34, power == current ? 0xFF000000 | power.color() : shown ? COLOR_TEXT : COLOR_DIM);
		}
	}

	/** The row's text without its leading name ("Training Field (once...": "once..."), since the name is beside it. */
	private static String glance(KeyGuide.Line line) {
		String text = line.text().getString();
		String name = line.name() == null ? "" : line.name().getString();
		if (!name.isEmpty() && text.startsWith(name)) {
			String rest = text.substring(name.length()).stripLeading();
			if (rest.startsWith(":") || rest.startsWith(",") || rest.startsWith(";")) {
				rest = rest.substring(1).stripLeading();
			} else if (rest.startsWith("(") && rest.contains(")")) {
				// "(hold): ..." keeps the hold, drops the brackets
				int close = rest.indexOf(')');
				String inside = rest.substring(1, close);
				String after = rest.substring(close + 1).stripLeading();
				after = after.startsWith(":") ? after.substring(1).stripLeading() : after;
				rest = inside.length() <= 8 ? capitalise(inside) + ": " + after : capitalise(inside) + ". " + after;
			}
			if (!rest.isEmpty()) {
				return capitalise(rest);
			}
		}
		return text;
	}

	private static String capitalise(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Wide enough for the widest key name (Left Shift, say), within reason. */
	private int chipWidth(List<KeyGuide.Line> lines) {
		int widest = 0;
		for (KeyGuide.Line line : lines) {
			widest = Math.max(widest, this.font.width(line.key()));
		}
		return Mth.clamp(widest + 8, 16, 44);
	}

	private int nameWidth(List<KeyGuide.Line> lines) {
		int widest = 40;
		for (KeyGuide.Line line : lines) {
			widest = Math.max(widest, this.font.width(line.name() != null ? line.name() : line.text()));
		}
		return widest;
	}
}
