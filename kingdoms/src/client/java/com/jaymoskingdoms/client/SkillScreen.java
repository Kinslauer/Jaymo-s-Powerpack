package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Careers;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Renown;
import com.jaymoskingdoms.Skills;
import com.jaymoskingdoms.Stats;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.52) The skill tree ({@link Skills}), on the {@code K} key. (0.7.40) Restyled like the H screen: a header with the
 * pillar's level, its experience bar and the points to spend; a tab per pillar; its perks as a tree of flat tiles (press
 * one to spend points on it); and a box at the bottom that shows the hovered perk in full, or else what the pillar
 * levels on, your class and your alignment ({@link Renown}). The server checks every spend and syncs the result back.
 * (0.7.57) A fourth tab, Attributes ({@link Stats}): your character level (every pillar's level added up), the
 * attribute points it earns, and the six attributes as rows of ten pips (yours, your heritage's, your class's).
 * (0.7.220) A fifth tab, Jobs ({@link Careers}): every job with your rank in it, where you hold it and its orders, and
 * (hovered) what each rank's perk does.
 * (0.7.514) The three pillars' tabs became one web ({@link SkillWeb}): a big circle, Strength on top, Survival lower
 * right, Stealth lower left, every page a branch running outward, and bridge perks on the borders. Drag to move round it,
 * scroll to zoom; pointing at a perk lights the path to it and says what it leads to.
 */
final class SkillScreen extends Screen {
	/**
	 * (0.7.540) The Attributes and Jobs panel: as tall as the screen allows up to {@link #PANEL_H}, the rows from
	 * {@link #TREE_Y} down to the detail box ({@link #DETAIL_H} tall, {@link #DETAIL_LINES} lines) just above the footer.
	 * Rows that don't fit scroll (the wheel), clipped to their own area, so they never run under the detail box.
	 */
	static final int PANEL_W = 340, PANEL_H = 300, HEADER = 34, TAB_W = 78, TAB_H = 16, STAT_ROW = 16,
		TREE_Y = 56, DETAIL_LINES = 5, DETAIL_H = 54, FOOTER = 22, XP_BAR_W = 120;
	/** (0.7.540) The attribute bar: ten segments of ten points each. */
	static final int SEG_W = 13, SEG_GAP = 2, SEGMENTS = 10, LOCKED = 0xFF0E0E14;

	private static KeyMapping key;
	/** (0.7.514) The web's header and footer heights, its zoom range and how much a scroll notch zooms. */
	static final int WEB_HEADER = 32, WEB_FOOTER = 22, WEB_CARD_W = 236;
	static final double ZOOM_MIN = 0.35, ZOOM_MAX = 2.6, FIT_MAX = 1.2, ZOOM_STEP = 1.15, NODE = 6.0;
	static final int COLOR_LOCKED_RIM = 0xFF4A4A56, COLOR_LOCKED_FILL = 0xFF16161C, COLOR_OPEN_FILL = 0xFF24202C, COLOR_PATH = 0xFFFFFFFF,
		COLOR_RING = 0x22FFFFFF, COLOR_BORDER = 0x40FFFFFF, COLOR_BACK = 0xF00A0A12;
	/** Where the web was left: moved by this much (screen pixels), at this zoom (NaN until first shown, then fitted). */
	private static double panX, panY, zoom = Double.NaN;
	/** A press on the web: where, and the perk under it (it's learnt on release if the mouse hardly moved). */
	private double pressX, pressY;
	private boolean pressed;
	private Skills.@Nullable Perk pressedPerk;
	/** (0.7.57) Whether the Attributes tab is the one shown. */
	private static boolean stats;
	/** (0.7.220) Whether the Jobs tab is the one shown. */
	private static boolean jobs;
	/** (0.7.220) The Jobs tab's rows. */
	static final int JOB_ROW = 12, JOB_ACCENT = 0xFFC8A060;
	/** (0.7.540) How far the Attributes or Jobs rows are scrolled (pixels). */
	private static int listScroll;

	SkillScreen() {
		super(Component.translatable("gui.jaymoskingdoms.skills.title"));
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("skills"));
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.skills", InputConstants.KEY_K, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new SkillScreen());
				}
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Skills.Progress progress() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null ? Skills.Progress.EMPTY : Skills.of(minecraft.player);
	}

	static int accent(Skills.Pillar pillar) {
		return switch (pillar) {
			case SURVIVAL -> Ui.SURVIVAL;
			case STEALTH -> Ui.STEALTH;
			case STRENGTH -> Ui.STRENGTH;
		};
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 12);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int panelH() {
		return Math.min(PANEL_H, this.height - 8);
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	/** (0.7.540) Where the detail box starts, just above the footer. */
	private int detailY() {
		return top() + panelH() - FOOTER - DETAIL_H;
	}

	/** (0.7.540) The rows' area: from under the tabs to a little above the detail box. */
	private int listTop() {
		return top() + TREE_Y;
	}

	private int listBottom() {
		return detailY() - 4;
	}

	/** (0.7.540) The rows' height, all of them, on the tab shown. */
	private int listContent() {
		return jobs ? Careers.Job.values().length * JOB_ROW : stats ? shownStats().size() * STAT_ROW : 0;
	}

	private int maxScroll() {
		return Math.max(0, listContent() - (listBottom() - listTop()));
	}

	/** (0.7.540) Whether {@code mouseY} is over the rows' area (rows scrolled out of it can't be pointed at). */
	private boolean inList(double mouseY) {
		return mouseY >= listTop() && mouseY < listBottom();
	}

	@Override
	protected void init() {
		if (Double.isNaN(zoom)) {
			fit();
		}
		if (stats || jobs) {
			// the Attributes and Jobs tabs keep their panel: the web's tab, then theirs
			int tabs = 3;
			int tabW = Math.min(TAB_W, (panelW() - 12 - 4 * (tabs - 1)) / tabs);
			int x = left() + (panelW() - tabW * tabs - 4 * (tabs - 1)) / 2, y = top() + HEADER + 2;
			tabs(x, y, tabW);
			int fy = top() + panelH() - FOOTER + 3;
			int right = left() + panelW() - 6;
			addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 60, fy, 60, 16).build());
			// (0.7.82) the world tiers, beside Done
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.tier.button"), b -> this.minecraft.gui.setScreen(new TierScreen(this)))
				.bounds(right - 60 - 4 - 80, fy, 80, 16).build());
			return;
		}
		// the web: the tabs at the header's right, the buttons along the foot
		int tabW = Math.min(TAB_W, Math.max(40, (this.width - 12) / 8));
		tabs(this.width - 6 - tabW * 3 - 8, 8, tabW);
		int fy = this.height - WEB_FOOTER + 3, right = this.width - 6;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 60, fy, 60, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.tier.button"), b -> this.minecraft.gui.setScreen(new TierScreen(this)))
			.bounds(right - 60 - 4 - 80, fy, 80, 16).build());
		// (0.7.361) Valor's page, from Strength
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.valor.button"), b -> this.minecraft.gui.setScreen(new ValorScreen(this)))
			.bounds(right - 60 - 4 - 80 - 4 - 60, fy, 60, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.skills.web.center"), b -> fit())
			.bounds(right - 60 - 4 - 80 - 4 - 60 - 4 - 60, fy, 60, 16).build());
	}

	/** The three tabs: the web, Attributes, Jobs. */
	private void tabs(int x, int y, int tabW) {
		Button webTab = Button.builder(Component.translatable("gui.jaymoskingdoms.skills.web.tab"), b -> {
			stats = false;
			jobs = false;
			rebuildWidgets();
		}).bounds(x, y, tabW, TAB_H).build();
		webTab.active = stats || jobs;
		addRenderableWidget(webTab);
		x += tabW + 4;
		Button statsTab = Button.builder(Component.translatable("gui.jaymoskingdoms.stats.tab"), b -> {
			listScroll = 0;
			stats = true;
			jobs = false;
			rebuildWidgets();
		}).bounds(x, y, tabW, TAB_H).build();
		statsTab.active = !stats;
		addRenderableWidget(statsTab);
		x += tabW + 4;
		Button jobsTab = Button.builder(Component.translatable("gui.jaymoskingdoms.jobs.tab"), b -> {
			listScroll = 0;
			jobs = true;
			stats = false;
			rebuildWidgets();
		}).bounds(x, y, tabW, TAB_H).build();
		jobsTab.active = !jobs;
		addRenderableWidget(jobsTab);
	}

	// ---- (0.7.514) ---------------------------------------------------------------------------------------------

	/** The whole web in view, centred, as big as fits (never past {@link #FIT_MAX}). */
	private void fit() {
		double[] b = SkillWeb.bounds();
		double pad = 14.0, w = Math.max(60, this.width - 2 * pad), h = Math.max(60, this.height - WEB_HEADER - WEB_FOOTER - 2 * pad);
		zoom = Math.max(ZOOM_MIN, Math.min(FIT_MAX, Math.min(w / (b[2] - b[0]), h / (b[3] - b[1]))));
		panX = -(b[0] + b[2]) / 2.0 * zoom;
		panY = -(b[1] + b[3]) / 2.0 * zoom;
	}

	private boolean onWeb() {
		return !stats && !jobs;
	}

	private double centerX() {
		return this.width / 2.0 + panX;
	}

	private double centerY() {
		return WEB_HEADER + (this.height - WEB_HEADER - WEB_FOOTER) / 2.0 + panY;
	}

	private double sx(SkillWeb.Spot spot) {
		return centerX() + spot.x() * zoom;
	}

	private double sy(SkillWeb.Spot spot) {
		return centerY() + spot.y() * zoom;
	}

	/** A perk's half size on screen: capstones (2 points or more) and bridges a little bigger. */
	private int half(Skills.Perk perk) {
		double size = NODE * (perk.cost >= 2 || perk.bridge() ? 1.3 : 1.0) * zoom;
		return (int)Math.max(2, Math.min(12, Math.round(size)));
	}

	private boolean inWebArea(double mouseX, double mouseY) {
		return mouseY >= WEB_HEADER && mouseY < this.height - WEB_FOOTER;
	}

	private Skills.@Nullable Perk perkAt(double mouseX, double mouseY) {
		if (!onWeb() || !inWebArea(mouseX, mouseY)) {
			return null;
		}
		Skills.Perk best = null;
		double bestD = Double.MAX_VALUE;
		for (Skills.Perk perk : Skills.Perk.values()) {
			SkillWeb.Spot spot = SkillWeb.SPOTS.get(perk);
			if (spot == null) {
				continue;
			}
			double dx = sx(spot) - mouseX, dy = sy(spot) - mouseY, d = dx * dx + dy * dy, r = half(perk) + 2;
			if (d <= r * r && d < bestD) {
				best = perk;
				bestD = d;
			}
		}
		return best;
	}

	/** The pillar crest under the mouse, if any. */
	private Skills.@Nullable Pillar crestAt(double mouseX, double mouseY) {
		if (!onWeb() || !inWebArea(mouseX, mouseY)) {
			return null;
		}
		for (Skills.Pillar p : Skills.Pillar.values()) {
			SkillWeb.Spot spot = SkillWeb.CRESTS.get(p);
			double r = Math.max(5, 9 * zoom);
			if (Math.abs(sx(spot) - mouseX) <= r && Math.abs(sy(spot) - mouseY) <= r) {
				return p;
			}
		}
		return null;
	}

	/** A straight line {@code w} pixels thick from one point to another (the pose turned to lie along it). */
	private static void segment(GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1, int w, int color) {
		double dx = x1 - x0, dy = y1 - y0;
		int len = (int)Math.round(Math.sqrt(dx * dx + dy * dy));
		if (len <= 0) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate((float)x0, (float)y0);
		graphics.pose().rotate((float)Math.atan2(dy, dx));
		graphics.fill(0, -w / 2, len, w - w / 2, color);
		graphics.pose().popMatrix();
	}

	/** A dashed line (a bridge's ties). */
	private static void dashed(GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1, int w, int color) {
		double dx = x1 - x0, dy = y1 - y0, len = Math.sqrt(dx * dx + dy * dy);
		int dashes = (int)Math.max(1, len / 6.0);
		for (int i = 0; i < dashes; i += 2) {
			double a = i / (double)dashes, b = Math.min(1.0, (i + 1) / (double)dashes);
			segment(graphics, x0 + dx * a, y0 + dy * a, x0 + dx * b, y0 + dy * b, w, color);
		}
	}

	/** A square {@code h} each way from its middle: a black edge, its rim, then its fill. */
	private static void tile(GuiGraphicsExtractor graphics, int x, int y, int h, int rim, int fill) {
		graphics.fill(x - h - 1, y - h - 1, x + h + 1, y + h + 1, 0xFF000000);
		graphics.fill(x - h, y - h, x + h, y + h, rim);
		graphics.fill(x - h + 1, y - h + 1, x + h - 1, y + h - 1, fill);
	}

	/** A diamond (a square turned a quarter): the bridges, and the middle. */
	private static void diamond(GuiGraphicsExtractor graphics, double x, double y, int h, int rim, int fill) {
		graphics.pose().pushMatrix();
		graphics.pose().translate((float)x, (float)y);
		graphics.pose().rotate((float)Math.PI / 4F);
		tile(graphics, 0, 0, Math.max(2, (int)Math.round(h * 0.85)), rim, fill);
		graphics.pose().popMatrix();
	}

	private static int darker(int color, float share) {
		int r = Math.round(((color >> 16) & 0xFF) * share), g = Math.round(((color >> 8) & 0xFF) * share), b = Math.round((color & 0xFF) * share);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** The perks on the way to {@code perk}: it, and everything it needs (for a bridge, the side you have, or else all). */
	private static java.util.Set<Skills.Perk> pathTo(Skills.Perk perk, Skills.Progress shown) {
		java.util.Set<Skills.Perk> out = java.util.EnumSet.noneOf(Skills.Perk.class);
		java.util.ArrayDeque<Skills.Perk> todo = new java.util.ArrayDeque<>();
		todo.add(perk);
		while (!todo.isEmpty()) {
			Skills.Perk p = todo.poll();
			if (!out.add(p)) {
				continue;
			}
			List<Skills.Perk> needs = p.requires;
			if (p.bridge()) {
				List<Skills.Perk> have = needs.stream().filter(n -> shown.rank(n) > 0).toList();
				if (!have.isEmpty()) {
					needs = have.subList(0, 1);
				}
			}
			todo.addAll(needs);
		}
		return out;
	}

	/** The web: rings, borders, the ties between perks, the perks, the crests and the branches' names. */
	private void web(GuiGraphicsExtractor graphics, Skills.Progress shown, int mouseX, int mouseY, Skills.@Nullable Perk hovered) {
		graphics.fill(0, 0, this.width, this.height, COLOR_BACK);
		// a few faint stars, the same each time
		java.util.Random stars = new java.util.Random(83L);
		for (int i = 0; i < 160; i++) {
			int x = stars.nextInt(Math.max(1, this.width)), y = stars.nextInt(Math.max(1, this.height));
			graphics.fill(x, y, x + 1, y + 1, (0x20 + stars.nextInt(0x50)) << 24 | 0xC8C8FF);
		}
		graphics.enableScissor(0, WEB_HEADER, this.width, this.height - WEB_FOOTER);
		double cx = centerX(), cy = centerY();
		// the rings
		for (int ring = 0; ring < SkillWeb.RINGS; ring++) {
			double r = (SkillWeb.R0 + ring * SkillWeb.STEP) * zoom;
			int dots = (int)Math.max(24, 2 * Math.PI * r / 5.0);
			for (int i = 0; i < dots; i++) {
				double a = 2 * Math.PI * i / dots;
				int x = (int)Math.round(cx + r * Math.cos(a)), y = (int)Math.round(cy + r * Math.sin(a));
				graphics.fill(x, y, x + 1, y + 1, COLOR_RING);
			}
		}
		// the borders between pillars
		double outer = (SkillWeb.R0 + (SkillWeb.RINGS - 0.5) * SkillWeb.STEP) * zoom, inner = (SkillWeb.CREST_R + 14) * zoom;
		for (Skills.Pillar p : Skills.Pillar.values()) {
			double a = Math.toRadians(SkillWeb.from(p));
			segment(graphics, cx + inner * Math.cos(a), cy + inner * Math.sin(a), cx + outer * Math.cos(a), cy + outer * Math.sin(a), 1, COLOR_BORDER);
		}
		java.util.Set<Skills.Perk> path = hovered == null ? java.util.Set.of() : pathTo(hovered, shown);
		int thick = zoom >= 1.2 ? 3 : 2;
		// the middle to each crest, each crest to its gates
		for (Skills.Pillar p : Skills.Pillar.values()) {
			SkillWeb.Spot crest = SkillWeb.CRESTS.get(p);
			segment(graphics, cx, cy, sx(crest), sy(crest), thick, Ui.GOLD);
		}
		for (java.util.Map.Entry<SkillWeb.Branch, SkillWeb.Spot> e : SkillWeb.GATES.entrySet()) {
			SkillWeb.Spot crest = SkillWeb.CRESTS.get(e.getKey().pillar);
			segment(graphics, sx(crest), sy(crest), sx(e.getValue()), sy(e.getValue()), thick, accent(e.getKey().pillar));
		}
		// the ties: from what a perk needs to it (a root from its branch's gate)
		for (Skills.Perk perk : Skills.Perk.values()) {
			SkillWeb.Spot to = SkillWeb.SPOTS.get(perk);
			if (to == null) {
				continue;
			}
			int accent = perk.bridge() ? Ui.GOLD : accent(perk.pillar);
			boolean learnt = shown.rank(perk) > 0;
			if (!perk.bridge() && perk.requires.stream().noneMatch(n -> SkillWeb.BRANCHES.get(n) == SkillWeb.BRANCHES.get(perk))) {
				SkillWeb.Spot gate = SkillWeb.GATES.get(SkillWeb.BRANCHES.get(perk));
				if (gate != null && perk.requires.isEmpty()) {
					int color = path.contains(perk) ? COLOR_PATH : learnt ? Ui.GOLD : darker(accent, 0.8F);
					segment(graphics, sx(gate), sy(gate), sx(to), sy(to), learnt || path.contains(perk) ? thick : thick - 1, color);
				}
			}
			for (Skills.Perk need : perk.requires) {
				SkillWeb.Spot from = SkillWeb.SPOTS.get(need);
				if (from == null) {
					continue;
				}
				boolean have = shown.rank(need) > 0, lit = path.contains(perk) && path.contains(need);
				int color = lit ? COLOR_PATH : have && learnt ? Ui.GOLD : have ? darker(accent, 0.8F) : COLOR_LOCKED_RIM;
				int w = lit || have ? thick : Math.max(1, thick - 1);
				if (perk.bridge()) {
					dashed(graphics, sx(from), sy(from), sx(to), sy(to), w, color);
				} else {
					segment(graphics, sx(from), sy(from), sx(to), sy(to), w, color);
				}
			}
		}
		// the perks
		var player = this.minecraft.player;
		for (Skills.Perk perk : Skills.Perk.values()) {
			SkillWeb.Spot spot = SkillWeb.SPOTS.get(perk);
			if (spot == null) {
				continue;
			}
			int x = (int)Math.round(sx(spot)), y = (int)Math.round(sy(spot)), h = half(perk);
			int rank = shown.rank(perk), accent = perk.bridge() ? Ui.GOLD : accent(perk.pillar);
			boolean open = shown.blocked(perk) == null && (player == null || !com.jaymoskingdoms.Knowledge.lockedPerk(player, perk)
				&& !com.jaymoskingdoms.Learnables.classLocked(player, perk));
			int rim = perk == hovered ? COLOR_PATH : rank > 0 ? Ui.GOLD : open ? accent : COLOR_LOCKED_RIM;
			int fill = rank >= perk.maxRank ? darker(accent, 0.75F) : rank > 0 ? darker(accent, 0.5F) : open ? COLOR_OPEN_FILL : COLOR_LOCKED_FILL;
			if (perk.bridge()) {
				diamond(graphics, x, y, h + 1, rim, fill);
			} else {
				tile(graphics, x, y, h, rim, fill);
				if (perk.cost >= 2 && h >= 4) {
					// a capstone: a second rim inside
					graphics.fill(x - h + 2, y - h + 2, x + h - 2, y - h + 3, rim);
					graphics.fill(x - h + 2, y + h - 3, x + h - 2, y + h - 2, rim);
				}
			}
			if (rank > 0 && h >= 4) {
				// a glint in the corner
				graphics.fill(x - h + 1, y - h + 1, x - h + 3, y - h + 3, 0x90FFFFFF);
			}
			// the ranks: a pip each under it, the learnt ones gold
			if (perk.maxRank > 1 && zoom >= 0.6) {
				int pw = 2, gap = 1, total = perk.maxRank * (pw + gap) - gap, px = x - total / 2, py = y + h + 3;
				for (int i = 0; i < perk.maxRank; i++) {
					graphics.fill(px + i * (pw + gap), py, px + i * (pw + gap) + pw, py + 2, i < rank ? Ui.GOLD : Ui.BAR_BACK);
				}
			}
			// names, once zoomed in far enough to have room for them
			if (zoom >= 1.5 || perk == hovered) {
				Component name = Component.translatable("skill.jaymoskingdoms.perk." + perk.key());
				float scale = perk == hovered ? 1.0F : 0.75F;
				int tw = this.font.width(name);
				graphics.pose().pushMatrix();
				graphics.pose().translate(x, y + h + (perk.maxRank > 1 ? 7 : 4));
				graphics.pose().scale(scale, scale);
				graphics.fill(-tw / 2 - 2, -1, tw - tw / 2 + 2, 9, 0xA0000000);
				graphics.text(this.font, name, -tw / 2, 0, rank > 0 ? Ui.TEXT : open ? Ui.DIM : Ui.HINT);
				graphics.pose().popMatrix();
			}
		}
		// the middle and the crests
		int hub = (int)Math.max(5, Math.round(10 * zoom));
		diamond(graphics, cx, cy, hub, Ui.GOLD, 0xFF2A2210);
		Skills.Pillar crestHover = crestAt(mouseX, mouseY);
		for (Skills.Pillar p : Skills.Pillar.values()) {
			SkillWeb.Spot crest = SkillWeb.CRESTS.get(p);
			int r = (int)Math.max(4, Math.round(8 * zoom));
			tile(graphics, (int)Math.round(sx(crest)), (int)Math.round(sy(crest)), r, p == crestHover ? COLOR_PATH : accent(p), darker(accent(p), 0.35F));
		}
		for (java.util.Map.Entry<SkillWeb.Branch, SkillWeb.Spot> e : SkillWeb.GATES.entrySet()) {
			int x = (int)Math.round(sx(e.getValue())), y = (int)Math.round(sy(e.getValue())), r = (int)Math.max(1, Math.round(2.5 * zoom));
			graphics.fill(x - r, y - r, x + r, y + r, accent(e.getKey().pillar));
		}
		// the branches' names
		for (SkillWeb.Label label : SkillWeb.LABELS) {
			Component name = Component.translatable("gui.jaymoskingdoms.skills.branch." + label.key());
			int tw = this.font.width(name), x = (int)Math.round(sx(label.at())), y = (int)Math.round(sy(label.at()));
			graphics.text(this.font, name, x - tw / 2, y - 4, accent(label.pillar()));
		}
		graphics.disableScissor();
	}

	/** The header across the top: each pillar's level, experience and points; the tabs sit at its right. */
	private void webHeader(GuiGraphicsExtractor graphics, Skills.Progress shown) {
		graphics.fill(0, 0, this.width, WEB_HEADER, 0xF0101018);
		graphics.fill(0, WEB_HEADER - 1, this.width, WEB_HEADER, 0x80FFC21A);
		int tabsW = Math.min(TAB_W, Math.max(40, (this.width - 12) / 8)) * 3 + 8 + 12;
		Skills.Pillar[] order = {Skills.Pillar.STRENGTH, Skills.Pillar.SURVIVAL, Skills.Pillar.STEALTH};
		int blockW = Math.max(60, Math.min(150, (this.width - 12 - tabsW) / order.length));
		int x = 6;
		for (Skills.Pillar p : order) {
			int accent = accent(p), level = shown.level(p), xp = shown.xp(p), points = shown.points(p), w = blockW - 8;
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.level", Component.translatable("skill.jaymoskingdoms." + p.key()), level),
				x, 4, w, accent);
			if (level >= p.maxLevel) {
				Ui.bar(graphics, x, 14, w, 2, 1.0F, accent);
			} else {
				Ui.bar(graphics, x, 14, w, 2, (xp - Skills.total(level)) / (float)Math.max(1, Skills.toNext(level)), accent);
			}
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.points", points), x, 19, w, points > 0 ? Ui.GOOD : Ui.DIM);
			x += blockW;
		}
	}

	/** The card at the bottom left: the hovered perk in full, a hovered crest's pillar, or else how the web works. */
	private void card(GuiGraphicsExtractor graphics, Skills.Progress shown, Skills.@Nullable Perk hovered, Skills.@Nullable Pillar crest) {
		Component name;
		int nameColor;
		List<Component> text = new ArrayList<>();
		var player = this.minecraft.player;
		if (hovered != null) {
			int rank = shown.rank(hovered), accent = hovered.bridge() ? Ui.GOLD : accent(hovered.pillar);
			name = Component.translatable("skill.jaymoskingdoms.perk." + hovered.key()).append("  " + rank + "/" + hovered.maxRank);
			nameColor = accent;
			text.add(Component.translatable("skill.jaymoskingdoms.perk." + hovered.key() + ".desc"));
			Component cost = Component.translatable("gui.jaymoskingdoms.skills.cost", hovered.cost);
			if (hovered.minLevel > 0) {
				cost = Component.empty().append(cost).append("  ·  ").append(Component.translatable("gui.jaymoskingdoms.skills.needs_level", hovered.minLevel));
			}
			text.add(cost.copy().withColor(Ui.DIM));
			if (hovered.bridge() && hovered.other != null) {
				text.add(Component.translatable("gui.jaymoskingdoms.skills.bridge", Component.translatable("skill.jaymoskingdoms." + hovered.pillar.key()),
					Component.translatable("skill.jaymoskingdoms." + hovered.other.key())).withColor(Ui.GOLD));
			}
			if (!hovered.requires.isEmpty()) {
				text.add(Component.translatable(hovered.bridge() ? "gui.jaymoskingdoms.skills.needs_any" : "gui.jaymoskingdoms.skills.needs", names(hovered.requires))
					.withColor(Ui.DIM));
			}
			List<Skills.Perk> leads = SkillWeb.leadsTo(hovered);
			if (!leads.isEmpty()) {
				text.add(Component.translatable("gui.jaymoskingdoms.skills.leads", names(leads)).withColor(0xFFF0D28C));
			}
			String blocked = shown.blocked(hovered);
			// (0.7.492) a higher technique wants Knowledge 4
			if (blocked == null && player != null && com.jaymoskingdoms.Knowledge.lockedPerk(player, hovered)) {
				blocked = "gui.jaymoskingdoms.skills.blocked.knowledge";
			}
			// (0.7.516) a class skill of another class
			if (blocked == null && player != null && com.jaymoskingdoms.Learnables.classLocked(player, hovered)) {
				blocked = "gui.jaymoskingdoms.skills.blocked.class";
			}
			java.util.Set<com.jaymoskingdoms.Classes.PlayerClass> forClasses = com.jaymoskingdoms.Learnables.classesFor(hovered);
			if (forClasses != null) {
				text.add(Component.translatable("gui.jaymoskingdoms.skills.for_classes", classNames(forClasses)).withColor(Ui.DIM));
			}
			if (shown.ranks().containsKey(com.jaymoskingdoms.Learnables.taughtKey(hovered))) {
				text.add(Component.translatable("gui.jaymoskingdoms.skills.taught").withColor(Ui.GOOD));
			}
			text.add(blocked == null ? Component.translatable("gui.jaymoskingdoms.skills.learn").withColor(Ui.GOOD)
				: Component.translatable(blocked).withColor(blocked.endsWith(".max") ? accent : Ui.BAD));
		} else if (crest != null) {
			name = Component.translatable("skill.jaymoskingdoms." + crest.key());
			nameColor = accent(crest);
			text.add(Component.translatable("gui.jaymoskingdoms.skills.hint." + crest.key()));
		} else {
			name = this.title;
			nameColor = Ui.GOLD;
			text.add(Component.translatable("gui.jaymoskingdoms.skills.web.help").withColor(Ui.DIM));
			Classes.PlayerClass cls = player == null ? null : Classes.classOf(player);
			text.add(cls == null ? Component.translatable("gui.jaymoskingdoms.skills.class.none").withColor(Ui.DIM)
				: Component.translatable("gui.jaymoskingdoms.skills.class", Component.translatable("class.jaymoskingdoms." + cls.key())).withColor(Ui.GOLD));
			// (your alignment: the pillar you've made your own, as others see it over your head)
			Skills.Pillar aligned = Renown.alignment(shown);
			text.add(aligned == null ? Component.translatable("gui.jaymoskingdoms.skills.alignment.none", Renown.ALIGN_LEVEL).withColor(Ui.DIM)
				: Component.translatable("gui.jaymoskingdoms.skills.alignment", Component.translatable("title.jaymoskingdoms." + aligned.key()
					+ (shown.level(aligned) >= aligned.maxLevel ? ".master" : ""))).withColor(accent(aligned)));
		}
		int w = Math.min(WEB_CARD_W, this.width / 2 - 8);
		int lines = Math.min(DETAIL_LINES + 3, Math.max(2, Ui.detailLines(this.font, w, name, text)));
		int h = lines * Ui.LINE + 10, x = 6, y = this.height - WEB_FOOTER - h - 4;
		graphics.fill(x, y, x + w, y + h, Ui.PANEL);
		graphics.fill(x, y, x + 2, y + h, nameColor);
		Ui.detail(graphics, this.font, x, y, w, lines, name, nameColor, text);
	}

	/** (0.7.516) Classes' names, joined with commas. */
	private static Component classNames(java.util.Set<com.jaymoskingdoms.Classes.PlayerClass> classes) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		boolean first = true;
		for (com.jaymoskingdoms.Classes.PlayerClass cls : classes) {
			if (!first) {
				out.append(", ");
			}
			out.append(Component.translatable("class.jaymoskingdoms." + cls.key()));
			first = false;
		}
		return out;
	}

	/** Perks' names, joined with commas. */
	private static Component names(List<Skills.Perk> perks) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < perks.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(Component.translatable("skill.jaymoskingdoms.perk." + perks.get(i).key()));
		}
		return out;
	}

	// ---- (0.7.57) the Attributes tab --------------------------------------------------------------------------------

	/** (0.7.212) The rows shown: all but Arcana, which shows once you're attuned. */
	private List<Stats.Stat> shownStats() {
		List<Stats.Stat> out = new ArrayList<>();
		for (Stats.Stat stat : Stats.Stat.values()) {
			if (this.minecraft.player == null || Stats.shown(this.minecraft.player, stat)) {
				out.add(stat);
			}
		}
		return out;
	}

	private int statY(Stats.Stat stat) {
		return listTop() + Math.max(0, shownStats().indexOf(stat)) * STAT_ROW - listScroll;
	}

	private Stats.@Nullable Stat statAt(double mouseX, double mouseY) {
		if (!stats || !inList(mouseY)) {
			return null;
		}
		for (Stats.Stat stat : shownStats()) {
			if (Ui.over((int)mouseX, (int)mouseY, left() + 8, statY(stat), panelW() - 16, STAT_ROW - 2)) {
				return stat;
			}
		}
		return null;
	}

	/** (0.7.540) Where point {@code p} (0 to {@link Stats#MAX}) falls on the segmented bar starting at {@code x}. */
	private static int barX(int x, int p) {
		int per = Stats.MAX / SEGMENTS, seg = Math.min(SEGMENTS - 1, p / per);
		return x + seg * (SEG_W + SEG_GAP) + Math.round((p - seg * per) * SEG_W / (float)per);
	}

	/** (0.7.540) Fills points {@code from} to {@code to} of the segmented bar, segment by segment (the gaps stay empty). */
	private static void barFill(GuiGraphicsExtractor graphics, int x, int y0, int y1, int from, int to, int color) {
		int per = Stats.MAX / SEGMENTS;
		for (int i = 0; i < SEGMENTS; i++) {
			int lo = Math.max(from, i * per), hi = Math.min(to, (i + 1) * per);
			if (lo < hi) {
				graphics.fill(barX(x, lo), y0, i * per + per == hi ? x + i * (SEG_W + SEG_GAP) + SEG_W : barX(x, hi), y1, color);
			}
		}
	}

	/** The header, a row per attribute (a bar of ten segments of ten points, your tier's cap notched) and the detail box. */
	private void attributes(GuiGraphicsExtractor graphics, Skills.Progress shown, int mouseX, int mouseY) {
		int x = left(), y = top(), w = panelW();
		int accent = Ui.GOLD;
		Ui.panel(graphics, x, y, w, panelH(), accent);
		var player = this.minecraft.player;
		int level = Stats.characterLevel(shown), points = player == null ? 0 : Stats.points(player, shown);
		int cap = player == null ? Stats.TIER_CAP[0] : Stats.cap(player), tier = player == null ? 0 : Stats.tier(player);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.stats.level", level), x + 9, y + 8, w / 2 - 12, accent);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.stats.points", points), x + 9, y + 19, w / 2 - 12, points > 0 ? Ui.GOOD : Ui.DIM);
		Heritage.Kind kind = player == null ? null : Heritage.of(player);
		Classes.PlayerClass cls = player == null ? null : Classes.classOf(player);
		Ui.lineRight(graphics, this.font, kind == null ? Component.translatable("gui.jaymoskingdoms.stats.heritage.none")
			: Component.translatable("gui.jaymoskingdoms.stats.heritage", Component.translatable("heritage.jaymoskingdoms." + kind.key())), x + w - 9, y + 8, w / 2 - 12,
			HeritageScreen.ACCENT);
		Ui.lineRight(graphics, this.font, cls == null ? Component.translatable("gui.jaymoskingdoms.skills.class.none")
			: Component.translatable("gui.jaymoskingdoms.skills.class", Component.translatable("class.jaymoskingdoms." + cls.key())), x + w - 9, y + 19, w / 2 - 12, Ui.GOLD);
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);

		listScroll = Math.clamp(listScroll, 0, maxScroll());
		Stats.Stat hovered = statAt(mouseX, mouseY);
		int barX = x + 84, numX = barX + SEGMENTS * (SEG_W + SEG_GAP) + 4, plusX = x + w - 18, rowRight = x + w - (maxScroll() > 0 ? 10 : 8);
		graphics.enableScissor(x + 1, listTop(), x + w - 1, listBottom());
		for (Stats.Stat stat : shownStats()) {
			int ry = statY(stat);
			if (ry + STAT_ROW <= listTop() || ry >= listBottom()) {
				continue;
			}
			Ui.row(graphics, x + 8, ry, rowRight, ry + STAT_ROW - 2, stat.ordinal(), stat == hovered);
			int spent = Stats.spent(shown, stat), fromHeritage = player == null ? 0 : Stats.fromHeritage(player, stat);
			int fromClass = player == null ? 0 : Stats.fromClass(player, stat);
			int fromStory = player == null ? 0 : Stats.fromStory(player, stat);
			int value = Math.min(cap, spent + fromHeritage + fromClass + fromStory);
			String blocked = player == null ? "" : Stats.blocked(player, shown, stat);
			Ui.line(graphics, this.font, Component.translatable("stat.jaymoskingdoms." + stat.key()), x + 13, ry + 3, barX - x - 18, value > 0 ? Ui.TEXT : Ui.DIM);
			// heritage's (teal), then class's (gold), then (0.7.216) your story's (parchment), then yours (white); empty to the cap, dark past it
			int y0 = ry + 4, y1 = ry + 10, h = Math.min(value, fromHeritage), c = Math.min(value, h + fromClass), st = Math.min(value, c + fromStory);
			barFill(graphics, barX, y0, y1, 0, cap, Ui.BAR_BACK);
			barFill(graphics, barX, y0, y1, cap, Stats.MAX, LOCKED);
			barFill(graphics, barX, y0, y1, 0, h, HeritageScreen.ACCENT);
			barFill(graphics, barX, y0, y1, h, c, Ui.GOLD);
			barFill(graphics, barX, y0, y1, c, st, LifeStoryScreen.ACCENT);
			barFill(graphics, barX, y0, y1, st, value, Ui.TEXT);
			if (cap < Stats.MAX) {
				int nx = barX(barX, cap) - (cap % (Stats.MAX / SEGMENTS) == 0 ? SEG_GAP / 2 + 1 : 0);
				graphics.fill(nx, ry + 2, nx + 1, ry + 12, accent);
			}
			Ui.line(graphics, this.font, Component.literal(value + "/" + Stats.MAX), numX, ry + 3, plusX - numX - 2, value >= cap ? accent : Ui.TEXT);
			if (blocked == null) {
				graphics.text(this.font, "+", plusX, ry + 3, Ui.GOOD);
			}
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, x + w - 7, listTop(), listBottom() - listTop(), listBottom() - listTop(), listContent(), listScroll);

		Component name;
		List<Component> text = new ArrayList<>();
		Component tierName = com.jaymoskingdoms.WorldTiers.Tier.byOrdinal(tier).title();
		if (hovered != null && player != null) {
			int value = Stats.value(player, shown, hovered);
			name = Component.translatable("stat.jaymoskingdoms." + hovered.key()).append("  " + value + "/" + Stats.MAX);
			text.add(Component.translatable("stat.jaymoskingdoms." + hovered.key() + ".desc"));
			text.add(Stats.now(hovered, value).copy().withColor(accent));
			if (hovered == Stats.Stat.ARCANA) {
				// (0.7.212) the mana itself: how much now, the most, how fast it comes back
				text.add(Component.translatable("gui.jaymoskingdoms.stats.mana", Math.round(com.jaymoskingdoms.Mana.current(player)),
					Math.round(com.jaymoskingdoms.Mana.max(player)), String.format(java.util.Locale.ROOT, "%.1f", com.jaymoskingdoms.Mana.regen(player)))
					.withColor(0x6FA8FF));
			}
			text.add(Component.translatable("gui.jaymoskingdoms.stats.from", Stats.spent(shown, hovered), Stats.fromHeritage(player, hovered),
				Stats.fromClass(player, hovered), Stats.fromStory(player, hovered)).withColor(Ui.DIM));
			String blocked = Stats.blocked(player, shown, hovered);
			if (blocked == null) {
				text.add(Component.translatable("gui.jaymoskingdoms.stats.raise", Stats.SHIFT_STEP).withColor(Ui.GOOD));
			} else if (blocked.endsWith(".tier")) {
				text.add(Component.translatable(blocked, cap, tierName).withColor(accent));
			} else {
				text.add(Component.translatable(blocked).withColor(blocked.endsWith(".max") ? accent : Ui.BAD));
			}
		} else {
			name = Component.translatable("gui.jaymoskingdoms.stats.tab");
			text.add(Component.translatable("gui.jaymoskingdoms.stats.cap", tierName, cap, Stats.MAX).withColor(accent));
			MutableComponent caps = Component.empty();
			for (int i = 0; i < Stats.TIER_CAP.length; i++) {
				com.jaymoskingdoms.WorldTiers.Tier t = com.jaymoskingdoms.WorldTiers.Tier.byOrdinal(i);
				caps.append(i == 0 ? Component.empty() : Component.literal(", ")).append(t.title().copy().withColor(t.color)).append(" " + Stats.TIER_CAP[i]);
			}
			text.add(Component.translatable("gui.jaymoskingdoms.stats.caps", caps).withColor(Ui.DIM));
			text.add(Component.translatable("gui.jaymoskingdoms.stats.hint", Stats.POINTS_PER_LEVEL).withColor(Ui.DIM));
			text.add(Component.translatable("gui.jaymoskingdoms.stats.legend").withColor(Ui.HINT));
		}
		Ui.detail(graphics, this.font, x, detailY(), w, DETAIL_LINES, name, accent, text);
	}

	// ---- (0.7.220) the Jobs tab --------------------------------------------------------------------------------------

	private int jobY(int index) {
		return listTop() + index * JOB_ROW - listScroll;
	}

	private Careers.@Nullable Job jobAt(double mouseX, double mouseY) {
		if (!jobs || !inList(mouseY)) {
			return null;
		}
		Careers.Job[] all = Careers.Job.values();
		for (int i = 0; i < all.length; i++) {
			if (Ui.over((int)mouseX, (int)mouseY, left() + 8, jobY(i), panelW() - 16, JOB_ROW)) {
				return all[i];
			}
		}
		return null;
	}

	/** The header (jobs held, your stall), a row per job (rank, where, its experience, orders open) and the detail box. */
	private void jobs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left(), y = top(), w = panelW();
		int accent = JOB_ACCENT;
		Ui.panel(graphics, x, y, w, panelH(), accent);
		Careers.Career c = this.minecraft.player == null ? Careers.Career.EMPTY : Careers.of(this.minecraft.player);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.jobs.title"), x + 9, y + 8, w / 2 - 12, accent);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.jobs.held", c.posts().size()), x + 9, y + 19, w / 2 - 12,
			c.posts().isEmpty() ? Ui.DIM : Ui.TEXT);
		Ui.lineRight(graphics, this.font, c.stall().isEmpty() ? Component.translatable("gui.jaymoskingdoms.jobs.no_stall")
			: Component.translatable("gui.jaymoskingdoms.jobs.stall", c.stall().get().getX(), c.stall().get().getZ()), x + w - 9, y + 8, w / 2 - 12,
			c.stall().isEmpty() ? Ui.DIM : Ui.GOOD);
		Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.jobs.hint"), x + w - 9, y + 19, w / 2 - 12, Ui.HINT);
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);

		listScroll = Math.clamp(listScroll, 0, maxScroll());
		Careers.Job hovered = jobAt(mouseX, mouseY);
		Careers.Job[] all = Careers.Job.values();
		int rowRight = x + w - (maxScroll() > 0 ? 10 : 8);
		int barW = 50, townX = x + 130, barX = rowRight - 42 - barW;
		graphics.enableScissor(x + 1, listTop(), x + w - 1, listBottom());
		for (int i = 0; i < all.length; i++) {
			Careers.Job job = all[i];
			int ry = jobY(i), xp = c.xpIn(job.key), rank = c.rank(job.key);
			if (ry + JOB_ROW <= listTop() || ry >= listBottom()) {
				continue;
			}
			Careers.Post post = c.post(job.key);
			Ui.row(graphics, x + 8, ry, rowRight, ry + JOB_ROW - 1, i, job == hovered);
			if (post != null) {
				graphics.fill(x + 8, ry, x + 10, ry + JOB_ROW - 1, accent);
			}
			Component name = xp > 0 || post != null ? Careers.title(job, rank) : job.title();
			Ui.line(graphics, this.font, name, x + 13, ry + 2, townX - x - 18, post != null ? Ui.TEXT : xp > 0 ? Ui.DIM : Ui.HINT);
			Ui.line(graphics, this.font, post == null ? Component.translatable("gui.jaymoskingdoms.jobs.not_held") : Component.literal(post.town()), townX, ry + 2,
				barX - townX - 6, post == null ? Ui.HINT : Ui.TEXT);
			int next = Careers.nextRankXp(rank), from = rank == Careers.MASTER ? Careers.MASTER_XP : rank == Careers.JOURNEYMAN ? Careers.JOURNEYMAN_XP : 0;
			float share = next < 0 ? 1.0F : (xp - from) / (float)Math.max(1, next - from);
			Ui.bar(graphics, barX, ry + 5, barW, 2, share, rank == Careers.MASTER ? Ui.GOLD : accent);
			if (post != null) {
				Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.jobs.orders", post.orders().size()), rowRight - 4, ry + 2, 36,
					post.orders().isEmpty() ? Ui.DIM : Ui.GOOD);
			}
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, x + w - 7, listTop(), listBottom() - listTop(), listBottom() - listTop(), listContent(), listScroll);

		Component title;
		List<Component> text = new ArrayList<>();
		if (hovered != null) {
			int rank = c.rank(hovered.key), xp = c.xpIn(hovered.key), next = Careers.nextRankXp(rank);
			title = Careers.title(hovered, rank).copy().append("  " + (next < 0 ? xp : xp + "/" + next));
			text.add(Component.translatable("job.jaymoskingdoms." + hovered.key + ".desc"));
			for (int r = Careers.JOURNEYMAN; r <= Careers.MASTER; r++) {
				text.add(Component.translatable("gui.jaymoskingdoms.jobs.perk", Component.translatable("job.jaymoskingdoms.rank." + r),
					Component.translatable("job.jaymoskingdoms." + hovered.key + ".perk." + r)).withColor(rank >= r ? Ui.GOOD : Ui.DIM));
			}
			Careers.Post post = c.post(hovered.key);
			if (post != null) {
				for (Careers.Order o : post.orders()) {
					text.add(Component.empty().append(Careers.describe(null, o)).append("  " + o.progress() + "/" + o.count()).withColor(Ui.TEXT));
				}
			}
		} else {
			title = Component.translatable("gui.jaymoskingdoms.jobs.tab");
			text.add(Component.translatable("gui.jaymoskingdoms.jobs.about").withColor(Ui.DIM));
			text.add(Component.translatable("gui.jaymoskingdoms.jobs.ranks", Careers.JOURNEYMAN_XP, Careers.MASTER_XP).withColor(Ui.HINT));
		}
		Ui.detail(graphics, this.font, x, detailY(), w, DETAIL_LINES, title, accent, text);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Stats.Stat stat = statAt(event.x(), event.y());
		if (stat != null) {
			if (this.minecraft.player != null && Stats.blocked(this.minecraft.player, progress(), stat) == null) {
				// (0.7.540) shift-click: ten at once
				ClientPlayNetworking.send(new Stats.Raise(stat.ordinal(), event.hasShiftDown() ? Stats.SHIFT_STEP : 1));
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (onWeb() && inWebArea(event.x(), event.y())) {
			// a press on the web: a drag moves it, a click (hardly moved) on a perk learns it
			pressed = true;
			pressX = event.x();
			pressY = event.y();
			pressedPerk = perkAt(event.x(), event.y());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (pressed && onWeb()) {
			panX += dx;
			panY += dy;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressed) {
			pressed = false;
			Skills.Perk perk = pressedPerk;
			pressedPerk = null;
			double moved = Math.abs(event.x() - pressX) + Math.abs(event.y() - pressY);
			if (perk != null && moved < 4.0 && perk == perkAt(event.x(), event.y()) && progress().blocked(perk) == null
				&& (this.minecraft.player == null || !com.jaymoskingdoms.Knowledge.lockedPerk(this.minecraft.player, perk)
					&& !com.jaymoskingdoms.Learnables.classLocked(this.minecraft.player, perk))) {
				ClientPlayNetworking.send(new Skills.Spend(perk.ordinal()));
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (onWeb() && scrollY != 0.0) {
			// zoom about the mouse: the point under it stays put
			double before = zoom;
			zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom * (scrollY > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP)));
			double ux = (mouseX - centerX()) / before, uy = (mouseY - centerY()) / before;
			panX += ux * before - ux * zoom;
			panY += uy * before - uy * zoom;
			return true;
		}
		if ((stats || jobs) && scrollY != 0.0 && maxScroll() > 0) {
			// (0.7.540) the Attributes or Jobs rows, a row a notch
			listScroll = Math.clamp(listScroll - (int)Math.signum(scrollY) * (jobs ? JOB_ROW : STAT_ROW), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Skills.Progress shown = progress();
		if (jobs) {
			jobs(graphics, mouseX, mouseY);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		if (stats) {
			attributes(graphics, shown, mouseX, mouseY);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		Skills.Perk hovered = pressed && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) >= 4.0 ? null : perkAt(mouseX, mouseY);
		web(graphics, shown, mouseX, mouseY, hovered);
		webHeader(graphics, shown);
		graphics.fill(0, this.height - WEB_FOOTER, this.width, this.height, 0xF0101018);
		card(graphics, shown, hovered, hovered == null ? crestAt(mouseX, mouseY) : null);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
