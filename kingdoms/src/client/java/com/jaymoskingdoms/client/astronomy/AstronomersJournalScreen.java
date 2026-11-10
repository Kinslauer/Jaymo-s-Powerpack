package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Skills;
import com.jaymoskingdoms.astronomy.AstronomersJournal;
import com.jaymoskingdoms.astronomy.Celestial;
import com.jaymoskingdoms.astronomy.Measurements;
import com.jaymoskingdoms.astronomy.StarDiscovery;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The Astronomer's Journal ({@link AstronomersJournal}): an open book of sepia ink on aged paper, its faint diagrams and
 * margin notes drawn into the page ({@code kingdoms/tools/gen_astronomy_journal_art.py}). Three parts, each a run of
 * pages turned two at a time:
 * <ul>
 * <li><b>Record</b>: the Scholar pillar's level and points, the stars discovered and figures joined, and every
 * measurement taken at an armillary sphere ({@link Measurements}), newest first;</li>
 * <li><b>Skills</b>: Astrometry, bought here with the Scholar's points through the skill tree's own spend
 * ({@link Skills.Spend});</li>
 * <li><b>Study</b>: how the sky's model works, with its units, formulas and worked examples, and where it stops being
 * the real sky.</li>
 * </ul>
 * Everything it shows is the server's, synced to this player; the pages are laid out again whenever that changes.
 */
public final class AstronomersJournalScreen extends Screen {
	/** The open book: its texture and size (GUI units; drawn 1:1, or smaller on a narrow screen). */
	static final Identifier SPREAD = JaymosKingdoms.id("textures/gui/astronomy/journal_spread.png");
	static final int BOOK_W = 384, BOOK_H = 240;
	/** Each page's text box, from the book's top left: the left page's x, the right page's x, the top, width and lines. */
	static final int LEFT_X = 24, RIGHT_X = 208, TEXT_Y = 20, TEXT_W = 152, LINES = 18, LINE = 10;
	/** Where the page links sit (from the book's top). */
	static final int FOOT_Y = 212;
	/** Inks, from the astronomy art's palette: the hand, the faint construction line, the red of a rubric, the brass. */
	static final int INK = 0xFF2B211A, FAINT = 0xFF7A6247, RUBRIC = 0xFF7A2E1E, BRASS = 0xFF8A6526;
	/** The study's chapters, in order ({@code gui.jaymoskingdoms.astronomy_journal.study.<key>.title} and {@code .body}). */
	static final String[] STUDY = {"sphere", "places", "time", "latitude", "longitude", "year", "precision", "limits"};
	static final String KEY = "gui.jaymoskingdoms.astronomy_journal.";

	enum Part {
		RECORD, SKILLS, STUDY
	}

	/** A line of a page: its text and ink, and whether it is the Astrometry link. */
	private record Line(FormattedCharSequence text, int color, boolean learn) {
	}

	/** Something on the book that answers a click (book units). */
	private record Link(int x, int y, int w, int h, Runnable action) {
	}

	/** The spread last open, kept for the rest of the session. */
	private static int lastSpread;

	private final List<List<Line>> pages = new ArrayList<>();
	private final Map<Part, Integer> starts = new EnumMap<>(Part.class);
	private final List<Link> links = new ArrayList<>();
	private int spread;
	/** The synced data the pages were laid out from, to notice when it changes. */
	private @Nullable Object seenProgress, seenEntries, seenStars;

	public AstronomersJournalScreen() {
		super(Component.translatable("item.jaymoskingdoms.astronomers_journal"));
		this.spread = lastSpread;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- laying the pages out ------------------------------------------------------------------------------------------

	private void layoutIfChanged() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Object progress = player.getAttached(Skills.PROGRESS), entries = player.getAttached(Measurements.ENTRIES),
			stars = player.getAttached(StarDiscovery.DISCOVERED);
		if (!this.pages.isEmpty() && progress == this.seenProgress && entries == this.seenEntries && stars == this.seenStars) {
			return;
		}
		this.seenProgress = progress;
		this.seenEntries = entries;
		this.seenStars = stars;
		layout(player);
	}

	private void layout(LocalPlayer player) {
		this.pages.clear();
		this.starts.clear();
		Skills.Progress skills = Skills.of(player);
		List<Measurements.Entry> entries = Measurements.of(player);
		List<Integer> discovered = player.getAttachedOrElse(StarDiscovery.DISCOVERED, List.of());

		// the record
		begin(Part.RECORD);
		List<Line> lines = new ArrayList<>();
		heading(lines, "record.title");
		int level = skills.level(Skills.Pillar.SCHOLAR), xp = skills.xp(Skills.Pillar.SCHOLAR), max = Skills.Pillar.SCHOLAR.maxLevel;
		text(lines, Component.translatable(KEY + "record.level", level, max), INK);
		text(lines, level >= max ? Component.translatable(KEY + "record.mastered")
			: Component.translatable(KEY + "record.xp", xp - Skills.total(level), Skills.toNext(level)), FAINT);
		int points = skills.points(Skills.Pillar.SCHOLAR);
		text(lines, Component.translatable(KEY + "record.points", points), points > 0 ? RUBRIC : FAINT);
		blank(lines);
		text(lines, Component.translatable(KEY + "record.stars", discovered.size(), StarCatalog.stars().size()), INK);
		text(lines, Component.translatable(KEY + "record.figures", joined(), Constellations.all().size()), INK);
		blank(lines);
		int starPlaces = 0, longitudes = 0, months = 0;
		Measurements.Entry latitude = null;
		for (Measurements.Entry e : entries) {
			if (e.key().startsWith(Measurements.STAR)) {
				starPlaces++;
			} else if (e.key().startsWith(Measurements.LONGITUDE)) {
				longitudes++;
			} else if (e.key().startsWith(Measurements.MONTH)) {
				months++;
			} else if (e.key().equals(Measurements.LATITUDE)) {
				latitude = e;
			}
		}
		text(lines, Component.translatable(KEY + "record.count", entries.size()), INK);
		text(lines, Component.translatable(KEY + "record.count.stars", starPlaces), INK);
		text(lines, latitude == null ? Component.translatable(KEY + "record.count.latitude.none")
			: Component.translatable(KEY + "record.count.latitude", degrees(latitude.a(), latitude.resolution())), INK);
		text(lines, Component.translatable(KEY + "record.count.longitudes", longitudes), INK);
		text(lines, Component.translatable(KEY + "record.count.months", months, Measurements.SKY_MONTHS), INK);
		blank(lines);
		text(lines, Component.translatable(KEY + "record.how"), FAINT);
		blank(lines);
		heading(lines, "record.list");
		if (entries.isEmpty()) {
			text(lines, Component.translatable(KEY + "record.list.empty"), FAINT);
		}
		for (int i = entries.size() - 1; i >= 0; i--) {
			Measurements.Entry e = entries.get(i);
			text(lines, Component.translatable(KEY + "record.day", e.day() + 1, what(e)), INK);
			text(lines, values(e), FAINT);
		}
		flow(lines);

		// the skills
		begin(Part.SKILLS);
		lines = new ArrayList<>();
		heading(lines, "skills.title");
		text(lines, Component.translatable(KEY + "record.points", points), points > 0 ? RUBRIC : FAINT);
		blank(lines);
		Skills.Perk perk = Skills.Perk.ASTROMETRY;
		int rank = skills.rank(perk);
		text(lines, Component.translatable(KEY + "skills.astrometry", rank, perk.maxRank).withStyle(ChatFormatting.BOLD), INK);
		text(lines, Component.translatable(KEY + "skills.resolution", step(Measurements.resolution(rank))), INK);
		if (rank < perk.maxRank) {
			text(lines, Component.translatable(KEY + "skills.next", step(Measurements.resolution(rank + 1))), FAINT);
		}
		String blocked = skills.blocked(perk);
		if (blocked == null) {
			for (FormattedCharSequence seq : this.font.split(Component.translatable(KEY + "skills.learn", perk.cost), TEXT_W)) {
				lines.add(new Line(seq, RUBRIC, true));
			}
		} else {
			text(lines, Component.translatable(blocked), FAINT);
		}
		blank(lines);
		int lonRank = Measurements.longitudeRank();
		text(lines, Component.translatable(rank >= lonRank ? KEY + "skills.longitude.open" : KEY + "skills.longitude.closed", lonRank), rank >= lonRank ? INK : FAINT);
		blank(lines);
		paragraphs(lines, Component.translatable(KEY + "skills.astrometry.desc"), INK);
		blank(lines);
		paragraphs(lines, Component.translatable(KEY + "skills.pillar"), FAINT);
		flow(lines);

		// the study
		begin(Part.STUDY);
		for (String chapter : STUDY) {
			lines = new ArrayList<>();
			heading(lines, "study." + chapter + ".title");
			paragraphs(lines, Component.translatable(KEY + "study." + chapter + ".body", studyArgs(chapter)), INK);
			flow(lines);
		}
		this.spread = Mth.clamp(this.spread, 0, spreads() - 1);
	}

	/** The tuning numbers a study chapter quotes, so its text follows the constants. */
	private static Object[] studyArgs(String chapter) {
		return switch (chapter) {
			case "longitude" -> new Object[] {step(Measurements.LONGITUDE_STEP_DEG), Math.round(Measurements.LONGITUDE_STEP_DEG / Celestial.LON_DEG_PER_BLOCK),
				Measurements.longitudeRank(), step(Measurements.resolution(Measurements.longitudeRank()))};
			case "precision" -> new Object[] {Measurements.XP_STAR, Measurements.XP_LATITUDE, Measurements.XP_LONGITUDE, Measurements.XP_SKY_MONTH,
				Measurements.STARS_PER_READING, Measurements.READING_TICKS / 20, Skills.Perk.ASTROMETRY.maxRank};
			default -> new Object[0];
		};
	}

	/** Starts {@code part} on a new spread. */
	private void begin(Part part) {
		if (this.pages.size() % 2 == 1) {
			this.pages.add(List.of());
		}
		this.starts.put(part, this.pages.size());
	}

	/** Lays {@code lines} out from the top of the next page, as many pages as they need. */
	private void flow(List<Line> lines) {
		for (int i = 0; i < lines.size(); i += LINES) {
			this.pages.add(List.copyOf(lines.subList(i, Math.min(lines.size(), i + LINES))));
		}
	}

	private void heading(List<Line> lines, String key) {
		text(lines, Component.translatable(KEY + key).withStyle(ChatFormatting.BOLD), RUBRIC);
		blank(lines);
	}

	private void text(List<Line> lines, Component text, int color) {
		for (FormattedCharSequence seq : this.font.split(text, TEXT_W)) {
			lines.add(new Line(seq, color, false));
		}
	}

	/** A text whose paragraphs are split by newlines, with a blank line between them. */
	private void paragraphs(List<Line> lines, Component text, int color) {
		String[] paras = text.getString().split("\n");
		for (int i = 0; i < paras.length; i++) {
			if (i > 0) {
				blank(lines);
			}
			text(lines, Component.literal(paras[i]), color);
		}
	}

	private static void blank(List<Line> lines) {
		lines.add(new Line(FormattedCharSequence.EMPTY, INK, false));
	}

	/** The constellation figures whose every star is discovered. */
	private static int joined() {
		Discovery discovery = Discovery.getOrCreate(Minecraft.getInstance());
		if (discovery == null) {
			return 0;
		}
		List<Star> stars = StarCatalog.stars();
		int n = 0;
		for (Constellation figure : Constellations.all()) {
			boolean all = !figure.segments().isEmpty();
			for (int[] seg : figure.segments()) {
				for (int index : seg) {
					if (index < 0 || index >= stars.size() || !discovery.isDiscovered(stars.get(index).hip())) {
						all = false;
					}
				}
			}
			if (all) {
				n++;
			}
		}
		return n;
	}

	/** What a measurement is of. */
	private static Component what(Measurements.Entry e) {
		String key = e.key();
		if (key.startsWith(Measurements.STAR)) {
			int hip = Integer.parseInt(key.substring(Measurements.STAR.length()));
			Star star = StarCatalog.byHip(hip);
			return star == null || star.name() == null || star.name().isEmpty()
				? Component.translatable(KEY + "record.star.unnamed", hip, star == null ? "?" : star.con())
				: Component.translatable(KEY + "record.star.named", star.name(), hip);
		}
		if (key.equals(Measurements.LATITUDE)) {
			return Component.translatable(KEY + "record.what.latitude");
		}
		if (key.startsWith(Measurements.LONGITUDE)) {
			return Component.translatable(KEY + "record.what.longitude");
		}
		return Component.translatable(KEY + "record.what.month", (int) e.a());
	}

	/** A measurement's values, to the step they were taken at. */
	private static Component values(Measurements.Entry e) {
		String key = e.key();
		if (key.startsWith(Measurements.STAR)) {
			return Component.translatable(KEY + "record.values.star", degrees(e.a(), e.resolution()), degrees(e.b(), e.resolution()), step(e.resolution()));
		}
		if (key.equals(Measurements.LATITUDE)) {
			return Component.translatable(KEY + "record.values.latitude", degrees(e.a(), e.resolution()), step(e.resolution()));
		}
		if (key.startsWith(Measurements.LONGITUDE)) {
			return Component.translatable(KEY + "record.values.longitude", degrees(e.a(), e.resolution()), step(e.resolution()));
		}
		return Component.translatable(KEY + "record.values.month", degrees(e.b(), e.resolution()), step(e.resolution()));
	}

	/** Decimal places that show a value taken at {@code step}. */
	private static int places(double step) {
		return step >= 0.95 ? 0 : step >= 0.095 ? 1 : 2;
	}

	static String degrees(double value, double step) {
		return String.format(Locale.ROOT, "%." + places(step) + "f\u00B0", value);
	}

	static String step(double step) {
		return String.format(Locale.ROOT, "%." + (places(step) + 1) + "f\u00B0", step);
	}

	// ---- turning -------------------------------------------------------------------------------------------------------

	private int spreads() {
		return Math.max(1, (this.pages.size() + 1) / 2);
	}

	private void turn(int to) {
		int next = Mth.clamp(to, 0, spreads() - 1);
		if (next != this.spread) {
			this.spread = next;
			lastSpread = next;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
		}
	}

	private Part partAt(int spread) {
		Part at = Part.RECORD;
		for (Part p : Part.values()) {
			Integer start = this.starts.get(p);
			if (start != null && start / 2 <= spread) {
				at = p;
			}
		}
		return at;
	}

	// ---- drawing and clicks --------------------------------------------------------------------------------------------

	/** How much the book is shrunk to fit the screen (1: drawn 1:1). */
	private float scale() {
		return Math.min(1.0F, Math.min((this.width - 8) / (float) BOOK_W, (this.height - 8) / (float) BOOK_H));
	}

	private float left() {
		return (this.width - BOOK_W * scale()) / 2.0F;
	}

	private float top() {
		return (this.height - BOOK_H * scale()) / 2.0F;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		layoutIfChanged();
		float k = scale();
		int mx = (int) ((mouseX - left()) / k), my = (int) ((mouseY - top()) / k);
		graphics.pose().pushMatrix();
		graphics.pose().translate(left(), top());
		graphics.pose().scale(k, k);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SPREAD, 0, 0, 0.0F, 0.0F, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
		this.links.clear();
		page(graphics, this.spread * 2, LEFT_X, mx, my);
		page(graphics, this.spread * 2 + 1, RIGHT_X, mx, my);
		footer(graphics, mx, my);
		graphics.pose().popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void page(GuiGraphicsExtractor graphics, int index, int x, int mx, int my) {
		if (index >= this.pages.size()) {
			return;
		}
		List<Line> lines = this.pages.get(index);
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			int y = TEXT_Y + i * LINE;
			int color = line.color;
			if (line.learn) {
				int w = this.font.width(line.text);
				boolean over = mx >= x && mx < x + w && my >= y - 1 && my < y + LINE - 1;
				color = over ? BRASS : RUBRIC;
				graphics.fill(x, y + 9, x + w, y + 10, color);
				this.links.add(new Link(x, y - 1, w, LINE, this::learn));
			}
			graphics.text(this.font, line.text, x, y, color, false);
		}
	}

	/** The parts on the left page's foot, the page turns and page numbers on the right's. */
	private void footer(GuiGraphicsExtractor graphics, int mx, int my) {
		Part now = partAt(this.spread);
		int x = LEFT_X;
		for (Part p : Part.values()) {
			Component name = Component.translatable(KEY + "part." + p.name().toLowerCase(Locale.ROOT));
			int w = this.font.width(name);
			boolean over = mx >= x && mx < x + w && my >= FOOT_Y - 1 && my < FOOT_Y + LINE;
			int color = p == now ? RUBRIC : over ? BRASS : FAINT;
			graphics.text(this.font, name, x, FOOT_Y, color, false);
			if (p == now) {
				graphics.fill(x, FOOT_Y + 9, x + w, FOOT_Y + 10, RUBRIC);
			}
			Integer start = this.starts.get(p);
			if (start != null) {
				this.links.add(new Link(x, FOOT_Y - 1, w, LINE, () -> turn(start / 2)));
			}
			x += w + 12;
		}
		Component back = Component.literal("\u2039 ");
		Component on = Component.literal(" \u203A");
		Component number = Component.translatable(KEY + "page", this.spread + 1, spreads());
		int nw = this.font.width(number), right = RIGHT_X + TEXT_W;
		int bw = this.font.width(back), ow = this.font.width(on);
		int bx = right - ow - nw - bw;
		boolean overBack = this.spread > 0 && mx >= bx - 4 && mx < bx + bw && my >= FOOT_Y - 1 && my < FOOT_Y + LINE;
		boolean overOn = this.spread < spreads() - 1 && mx >= right - ow && mx < right + 4 && my >= FOOT_Y - 1 && my < FOOT_Y + LINE;
		graphics.text(this.font, back, bx, FOOT_Y, this.spread > 0 ? overBack ? BRASS : INK : FAINT, false);
		graphics.text(this.font, number, bx + bw, FOOT_Y, FAINT, false);
		graphics.text(this.font, on, right - ow, FOOT_Y, this.spread < spreads() - 1 ? overOn ? BRASS : INK : FAINT, false);
		this.links.add(new Link(bx - 4, FOOT_Y - 1, bw + 4, LINE, () -> turn(this.spread - 1)));
		this.links.add(new Link(right - ow, FOOT_Y - 1, ow + 4, LINE, () -> turn(this.spread + 1)));
	}

	private void learn() {
		ClientPlayNetworking.send(new Skills.Spend(Skills.Perk.ASTROMETRY.ordinal()));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		float k = scale();
		double mx = (event.x() - left()) / k, my = (event.y() - top()) / k;
		for (Link link : List.copyOf(this.links)) {
			if (mx >= link.x && mx < link.x + link.w && my >= link.y && my < link.y + link.h) {
				link.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		turn(this.spread + (scrollY < 0 ? 1 : -1));
		return true;
	}
}
