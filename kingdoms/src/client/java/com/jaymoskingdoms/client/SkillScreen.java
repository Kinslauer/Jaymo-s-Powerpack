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
import com.jaymoskingdoms.client.ui.FlatButton;
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
import net.minecraft.world.item.ItemStack;
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
 * The web is spread wide and opens zoomed in, every node carries its own picture ({@link SkillIcons}), a click picks a
 * node and a second one learns it, and framed panels hold the rest: your levels, points and attributes on the left, the
 * picked perk in full on the right, the keys along the foot.
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
	/** The bar's share trained in the weights (Might only, {@link Stats#trainMight}). */
	static final int TRAINED = 0xFFC0503A;

	private static KeyMapping key;
	/** The web's top and bottom bars, its zoom range, how much a scroll notch zooms and a node's half size at zoom 1. */
	static final int WEB_HEADER = 22, WEB_FOOTER = 22;
	static final double ZOOM_MIN = 0.1, ZOOM_MAX = 2.4, ZOOM_STEP = 1.15, NODE = 9.0;
	/** The zoom the web opens at and goes to for a picked perk; a perk picked below {@link #FOCUS_ZOOM} is brought up to it. */
	static final double OPEN_ZOOM = 1.0, FOCUS_ZOOM = 0.8;
	/** A node shows its picture from this half size up; perks' names and the gates' names show from these zooms up. */
	static final int ICON_FROM = 6;
	static final double NAMES_FROM = 1.0, GATE_NAMES_FROM = 0.6;
	/** How fast the view glides to where it is headed (a share per second, eased). */
	static final double GLIDE = 10.0;
	/** The left panel's rows, the title's rules and the room the foot's buttons take. */
	static final int PILLAR_ROW = 26, RAIL_STAT_ROW = 12, TITLE_RULE = 34, FOOT_BUTTONS = 346;
	static final int COLOR_LOCKED_RIM = 0xFF4A4A56, COLOR_LOCKED_FILL = 0xFF16161C, COLOR_OPEN_FILL = 0xFF24202C, COLOR_PATH = 0xFFFFFFFF,
		COLOR_RING = 0x22FFFFFF, COLOR_BORDER = 0x40FFFFFF, COLOR_BACK = 0xF00A0A12, COLOR_FRAME = 0xF00E0E16, COLOR_FRAME_LINE = 0xFF8C752A,
		COLOR_OVER = 0x30FFFFFF, COLOR_DIMMED = 0xB0101016;
	/** Where the web was left: moved by this much (screen pixels), at this zoom (NaN until first shown). */
	private static double panX, panY, zoom = Double.NaN;
	/** Where the view is headed: it glides there ({@link #glide}). */
	private static double targetX, targetY, targetZoom;
	private long lastFrame;
	/** The perk picked: the right panel shows it, and a second click, Enter or the panel's button learns it. */
	private static Skills.@Nullable Perk selected;
	/** A press on the web: where, and the perk under it (it's picked on release if the mouse hardly moved). */
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

	/** Whether the Attributes/Jobs panel is collapsed to a thin tab on the left. */
	private static boolean panelCollapsed;

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
			case SCHOLAR -> 0xFF000000 | Skills.Pillar.SCHOLAR.color;
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

	/** (0.7.649) The panel's top-left corner, where Jaymo's Powers' character menu hangs its tabs ({@link MenuCompat}). */
	int[] corner() {
		return new int[] {left(), top()};
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
			open();
		}
		if (stats || jobs) {
			// the Attributes and Jobs tabs keep their panel: the web's tab, then theirs
			int tabs = 3;
			int tabW = Math.min(TAB_W, (panelW() - 12 - 4 * (tabs - 1)) / tabs);
			int x = left() + (panelW() - tabW * tabs - 4 * (tabs - 1)) / 2, y = top() + HEADER + 2;
			tabs(x, y, tabW);
			int fy = top() + panelH() - FOOTER + 3;
			int right = left() + panelW() - 6;
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 60, fy, 60, 16).build());
			// (0.7.82) the world tiers, beside Done
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.tier.button"), b -> this.minecraft.gui.setScreen(new TierScreen(this)))
				.bounds(right - 60 - 4 - 80, fy, 80, 16).build());
			addRenderableWidget(FlatButton.builder(Component.translatable("standing.jaymoskingdoms.button"), b -> this.minecraft.gui.setScreen(new StandingScreen(this)))
				.bounds(right - 60 - 4 - 80 - 4 - 70, fy, 70, 16).build());
			// a small arrow on the panel's left edge collapses it; the strip on the left brings it back
			if (!panelCollapsed) {
				addRenderableWidget(FlatButton.builder(Component.literal("<"), b -> {
					panelCollapsed = true;
					rebuildWidgets();
				}).bounds(left() - 20, top() + panelH() / 2 - 10, 16, 16).build());
			}
			return;
		}
		// the web: the tabs at the header's right, the buttons along the foot
		int tabW = Math.min(TAB_W, Math.max(40, (this.width - 12) / 8));
		tabs(this.width - 6 - tabW * 3 - 8, 3, tabW);
		int fy = this.height - WEB_FOOTER + 3, right = this.width - 6;
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 60, fy, 60, 16).build());
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.tier.button"), b -> this.minecraft.gui.setScreen(new TierScreen(this)))
			.bounds(right - 60 - 4 - 80, fy, 80, 16).build());
		// (0.7.361) Valor's page, from Strength
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.valor.button"), b -> this.minecraft.gui.setScreen(new ValorScreen(this)))
			.bounds(right - 60 - 4 - 80 - 4 - 60, fy, 60, 16).build());
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.skills.web.center"), b -> fit())
			.bounds(right - 60 - 4 - 80 - 4 - 60 - 4 - 60, fy, 60, 16).build());
		addRenderableWidget(FlatButton.builder(Component.translatable("standing.jaymoskingdoms.button"), b -> this.minecraft.gui.setScreen(new StandingScreen(this)))
			.bounds(right - 60 - 4 - 80 - 4 - 60 - 4 - 60 - 4 - 70, fy, 70, 16).build());
	}

	/** The three tabs: the web, Attributes, Jobs. */
	private void tabs(int x, int y, int tabW) {
		Button webTab = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.skills.web.tab"), b -> {
			stats = false;
			jobs = false;
			rebuildWidgets();
		}).bounds(x, y, tabW, TAB_H).build();
		webTab.active = stats || jobs;
		addRenderableWidget(webTab);
		x += tabW + 4;
		Button statsTab = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.stats.tab"), b -> {
			listScroll = 0;
			stats = true;
			jobs = false;
			rebuildWidgets();
		}).bounds(x, y, tabW, TAB_H).build();
		statsTab.active = !stats;
		addRenderableWidget(statsTab);
		x += tabW + 4;
		Button jobsTab = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.jobs.tab"), b -> {
			listScroll = 0;
			jobs = true;
			stats = false;
			rebuildWidgets();
		}).bounds(x, y, tabW, TAB_H).build();
		jobsTab.active = !jobs;
		addRenderableWidget(jobsTab);
	}

	// ---- the web ---------------------------------------------------------------------------------------------------

	private static final Skills.Pillar[] ORDER = {Skills.Pillar.STRENGTH, Skills.Pillar.SURVIVAL, Skills.Pillar.STEALTH};

	private boolean onWeb() {
		return !stats && !jobs;
	}

	/** The web takes the mouse: the web tab itself, or a list tab whose panel is collapsed away. */
	private boolean webOnScreen() {
		return onWeb() || (panelCollapsed && (stats || jobs));
	}

	/** The left panel's width (your pillars and attributes); a collapsed Attributes or Jobs panel leaves only its strip there. */
	private int railL() {
		return onWeb() ? Math.clamp(this.width / 5, 84, 112) : 30;
	}

	/** The right panel's width (the skill picked or pointed at). */
	private int railR() {
		return Math.clamp(this.width * 3 / 10, 120, 176);
	}

	/** The middle of the web's own area, between the two panels and the bars. */
	private double areaX() {
		return railL() + (this.width - railL() - railR()) / 2.0;
	}

	private double areaY() {
		return WEB_HEADER + (this.height - WEB_HEADER - WEB_FOOTER) / 2.0;
	}

	/** Heads the view for {@code spot} in the middle, at zoom {@code z}. */
	private static void look(SkillWeb.Spot spot, double z) {
		targetZoom = z;
		targetX = -spot.x() * z;
		targetY = -spot.y() * z;
	}

	/** The first view: close in on the first rings of your strongest pillar, so every node there reads at once. */
	private void open() {
		Skills.Progress shown = progress();
		Skills.Pillar best = Skills.Pillar.STRENGTH;
		for (Skills.Pillar p : ORDER) {
			if (shown.level(p) > shown.level(best)) {
				best = p;
			}
		}
		look(SkillWeb.focus(best), OPEN_ZOOM);
		panX = targetX;
		panY = targetY;
		zoom = targetZoom;
	}

	/** Heads for the whole web in view, centred, as big as fits (never past {@link #OPEN_ZOOM}). */
	private void fit() {
		double[] b = SkillWeb.bounds();
		double pad = 14.0, w = Math.max(60, this.width - railL() - railR() - 2 * pad), h = Math.max(60, this.height - WEB_HEADER - WEB_FOOTER - 2 * pad);
		targetZoom = Math.max(ZOOM_MIN, Math.min(OPEN_ZOOM, Math.min(w / (b[2] - b[0]), h / (b[3] - b[1]))));
		targetX = -(b[0] + b[2]) / 2.0 * targetZoom;
		targetY = -(b[1] + b[3]) / 2.0 * targetZoom;
	}

	/** Moves the view a share of the way to where it is headed, by the time since the last frame. */
	private void glide() {
		long now = System.nanoTime();
		double dt = this.lastFrame == 0L ? 1.0 : Math.min(0.1, (now - this.lastFrame) / 1.0e9);
		this.lastFrame = now;
		double k = 1.0 - Math.exp(-dt * GLIDE);
		zoom += (targetZoom - zoom) * k;
		panX += (targetX - panX) * k;
		panY += (targetY - panY) * k;
	}

	private double centerX() {
		return areaX() + panX;
	}

	private double centerY() {
		return areaY() + panY;
	}

	private double sx(SkillWeb.Spot spot) {
		return centerX() + spot.x() * zoom;
	}

	private double sy(SkillWeb.Spot spot) {
		return centerY() + spot.y() * zoom;
	}

	/** A perk's half size on screen: capstones (2 points or more) and bridges a little bigger. */
	private int half(Skills.Perk perk) {
		double size = NODE * (perk.cost >= 2 || perk.bridge() ? 1.25 : 1.0) * zoom;
		return (int)Math.max(2, Math.min(20, Math.round(size)));
	}

	private boolean inWebArea(double mouseX, double mouseY) {
		return mouseX >= railL() && mouseX < this.width - railR() && mouseY >= WEB_HEADER && mouseY < this.height - WEB_FOOTER;
	}

	private Skills.@Nullable Perk perkAt(double mouseX, double mouseY) {
		if (!webOnScreen() || !inWebArea(mouseX, mouseY)) {
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
		if (!webOnScreen() || !inWebArea(mouseX, mouseY)) {
			return null;
		}
		for (Skills.Pillar p : Skills.Pillar.values()) {
			SkillWeb.Spot spot = SkillWeb.CRESTS.get(p);
			if (spot == null) {
				continue;
			}
			double r = Math.max(5, 14 * zoom);
			if (Math.abs(sx(spot) - mouseX) <= r && Math.abs(sy(spot) - mouseY) <= r) {
				return p;
			}
		}
		return null;
	}

	/** Whether {@code perk} can be learnt (or raised) right now. */
	private boolean canLearn(Skills.Progress shown, Skills.Perk perk) {
		var player = this.minecraft.player;
		return shown.blocked(perk) == null && (player == null || !com.jaymoskingdoms.Knowledge.lockedPerk(player, perk)
			&& !com.jaymoskingdoms.Learnables.classLocked(player, perk));
	}

	/** Picks {@code perk} for the right panel, and brings it to the middle if it sits near the edge or is too small to read. */
	private void select(Skills.Perk perk) {
		selected = perk;
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		SkillWeb.Spot spot = SkillWeb.SPOTS.get(perk);
		if (spot == null) {
			return;
		}
		double w = this.width - railL() - railR(), h = this.height - WEB_HEADER - WEB_FOOTER;
		if (zoom < FOCUS_ZOOM || Math.abs(sx(spot) - areaX()) > w * 0.3 || Math.abs(sy(spot) - areaY()) > h * 0.3) {
			look(spot, Math.max(targetZoom, OPEN_ZOOM));
		}
	}

	/** Spends a point on {@code perk} if it can be learnt (the server checks it again). */
	private void learn(Skills.Perk perk) {
		if (canLearn(progress(), perk)) {
			ClientPlayNetworking.send(new Skills.Spend(perk.ordinal()));
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
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

	/** A 1 pixel outline. */
	private static void outline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
		graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
	}

	/** An item drawn to fill a node {@code h} each way from its middle. */
	private static void icon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int h) {
		float s = (2 * h - 4) / 16F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x - 8 * s, y - 8 * s);
		graphics.pose().scale(s, s);
		graphics.item(stack, 0, 0);
		graphics.pose().popMatrix();
	}

	/**
	 * A side panel or bar of the web's screen: dark like the sky behind it in both screen looks, a thin dim-gold line
	 * just inside its edge and a gold stud in each corner.
	 */
	private static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, COLOR_FRAME);
		outline(graphics, x + 2, y + 2, x + w - 2, y + h - 2, COLOR_FRAME_LINE);
		for (int i = 0; i < 4; i++) {
			int cx = i % 2 == 0 ? x + 2 : x + w - 3, cy = i < 2 ? y + 2 : y + h - 3;
			graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF000000);
			graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, Ui.GOLD);
		}
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

	/** A name on a dark plate, centred on {@code x} with its top at {@code y}, drawn at {@code scale}. */
	private void plate(GuiGraphicsExtractor graphics, Component name, int x, int y, float scale, int color) {
		int tw = this.font.width(name);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.fill(-tw / 2 - 2, -1, tw - tw / 2 + 2, 9, 0xA0000000);
		graphics.text(this.font, name, -tw / 2, 0, color);
		graphics.pose().popMatrix();
	}

	/** The web: rings, borders, the ties between perks, the perks with their pictures, the crests and the branches' names. */
	private void web(GuiGraphicsExtractor graphics, Skills.Progress shown, int mouseX, int mouseY, Skills.@Nullable Perk hovered) {
		graphics.fill(0, 0, this.width, this.height, COLOR_BACK);
		// a few faint stars, the same each time
		java.util.Random stars = new java.util.Random(83L);
		for (int i = 0; i < 160; i++) {
			int x = stars.nextInt(Math.max(1, this.width)), y = stars.nextInt(Math.max(1, this.height));
			graphics.fill(x, y, x + 1, y + 1, (0x20 + stars.nextInt(0x50)) << 24 | 0xC8C8FF);
		}
		int left = railL(), right = this.width - railR(), top = WEB_HEADER, bottom = this.height - WEB_FOOTER;
		graphics.enableScissor(left, top, right, bottom);
		double cx = centerX(), cy = centerY();
		// the rings
		for (int ring = 0; ring < SkillWeb.RINGS; ring++) {
			double r = (SkillWeb.R0 + ring * SkillWeb.STEP) * zoom;
			int dots = (int)Math.max(24, 2 * Math.PI * r / 6.0);
			for (int i = 0; i < dots; i++) {
				double a = 2 * Math.PI * i / dots;
				int x = (int)Math.round(cx + r * Math.cos(a)), y = (int)Math.round(cy + r * Math.sin(a));
				if (x >= left && x < right && y >= top && y < bottom) {
					graphics.fill(x, y, x + 1, y + 1, COLOR_RING);
				}
			}
		}
		// the borders between pillars
		double outer = (SkillWeb.R0 + (SkillWeb.RINGS - 0.5) * SkillWeb.STEP) * zoom, inner = (SkillWeb.CREST_R + 24) * zoom;
		for (Skills.Pillar p : Skills.Pillar.values()) {
			if (!p.web()) {
				continue;
			}
			double a = Math.toRadians(SkillWeb.from(p));
			segment(graphics, cx + inner * Math.cos(a), cy + inner * Math.sin(a), cx + outer * Math.cos(a), cy + outer * Math.sin(a), 1, COLOR_BORDER);
		}
		// the path lit: to the perk pointed at, or else to the one picked
		Skills.Perk lead = hovered != null ? hovered : selected;
		java.util.Set<Skills.Perk> path = lead == null ? java.util.Set.of() : pathTo(lead, shown);
		int thick = zoom >= 1.2 ? 3 : zoom >= 0.45 ? 2 : 1;
		// the middle to each crest, each crest to its gates
		for (Skills.Pillar p : Skills.Pillar.values()) {
			SkillWeb.Spot crest = SkillWeb.CRESTS.get(p);
			if (crest == null) {
				continue;
			}
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
					segment(graphics, sx(gate), sy(gate), sx(to), sy(to), learnt || path.contains(perk) ? thick : Math.max(1, thick - 1), color);
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
		for (Skills.Perk perk : Skills.Perk.values()) {
			SkillWeb.Spot spot = SkillWeb.SPOTS.get(perk);
			if (spot == null) {
				continue;
			}
			int x = (int)Math.round(sx(spot)), y = (int)Math.round(sy(spot)), h = half(perk);
			if (x < left - 80 || x > right + 80 || y < top - 40 || y > bottom + 40) {
				continue;
			}
			int rank = shown.rank(perk), accent = perk.bridge() ? Ui.GOLD : accent(perk.pillar);
			boolean open = canLearn(shown, perk);
			int rim = perk == hovered ? COLOR_PATH : rank > 0 ? Ui.GOLD : open ? accent : COLOR_LOCKED_RIM;
			int fill = rank >= perk.maxRank ? darker(accent, 0.75F) : rank > 0 ? darker(accent, 0.5F) : open ? COLOR_OPEN_FILL : COLOR_LOCKED_FILL;
			if (perk == selected) {
				// the one picked: a gold square standing off it
				outline(graphics, x - h - 4, y - h - 4, x + h + 4, y + h + 4, Ui.GOLD);
			}
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
			if (h >= ICON_FROM) {
				// its own picture; dimmed while it can't be learnt yet
				icon(graphics, SkillIcons.of(perk), x, y, perk.bridge() ? h - 2 : h);
				if (rank == 0 && !open) {
					int d = perk.bridge() ? h - 4 : h - 1;
					graphics.fill(x - d, y - d, x + d, y + d, COLOR_DIMMED);
				}
			} else if (rank > 0 && h >= 4) {
				// a glint in the corner
				graphics.fill(x - h + 1, y - h + 1, x - h + 3, y - h + 3, 0x90FFFFFF);
			}
			// the ranks: a pip each under it, the learnt ones gold
			boolean pips = perk.maxRank > 1 && zoom >= 0.45;
			if (pips) {
				int pw = zoom >= 1.0 ? 3 : 2, gap = 1, total = perk.maxRank * (pw + gap) - gap, px = x - total / 2, py = y + h + 3;
				for (int i = 0; i < perk.maxRank; i++) {
					graphics.fill(px + i * (pw + gap), py, px + i * (pw + gap) + pw, py + 2, i < rank ? Ui.GOLD : Ui.BAR_BACK);
				}
			}
			// names, once zoomed in far enough to have room for them
			boolean marked = perk == hovered || perk == selected;
			if (zoom >= NAMES_FROM || marked) {
				plate(graphics, Component.translatable("skill.jaymoskingdoms.perk." + perk.key()), x, y + h + (pips ? 8 : 5), marked ? 1.0F : 0.75F,
					rank > 0 || marked ? Ui.TEXT : open ? Ui.DIM : Ui.HINT);
			}
		}
		// the middle and the crests
		int hub = (int)Math.max(5, Math.round(16 * zoom));
		diamond(graphics, cx, cy, hub, Ui.GOLD, 0xFF2A2210);
		Skills.Pillar crestHover = crestAt(mouseX, mouseY);
		for (Skills.Pillar p : Skills.Pillar.values()) {
			SkillWeb.Spot crest = SkillWeb.CRESTS.get(p);
			if (crest == null) {
				continue;
			}
			int r = (int)Math.max(4, Math.round(13 * zoom)), x = (int)Math.round(sx(crest)), y = (int)Math.round(sy(crest));
			tile(graphics, x, y, r, p == crestHover ? COLOR_PATH : accent(p), darker(accent(p), 0.35F));
			if (r >= ICON_FROM) {
				icon(graphics, SkillIcons.of(p), x, y, r);
			}
			if (zoom >= GATE_NAMES_FROM) {
				plate(graphics, Component.translatable("skill.jaymoskingdoms." + p.key()), x, y + r + 4, 1.0F, accent(p));
			}
		}
		for (java.util.Map.Entry<SkillWeb.Branch, SkillWeb.Spot> e : SkillWeb.GATES.entrySet()) {
			int x = (int)Math.round(sx(e.getValue())), y = (int)Math.round(sy(e.getValue())), r = (int)Math.max(1, Math.round(4 * zoom));
			graphics.fill(x - r - 1, y - r - 1, x + r + 1, y + r + 1, 0xFF000000);
			graphics.fill(x - r, y - r, x + r, y + r, accent(e.getKey().pillar));
			if (zoom >= GATE_NAMES_FROM) {
				// the branch's name at its gate too, so it reads from the middle
				plate(graphics, Component.translatable("gui.jaymoskingdoms.skills.branch." + e.getKey().key()), x, y + r + 3, 0.75F, accent(e.getKey().pillar));
			}
		}
		// the branches' names, past their last ring
		for (SkillWeb.Label label : SkillWeb.LABELS) {
			Component name = Component.translatable("gui.jaymoskingdoms.skills.branch." + label.key());
			int tw = this.font.width(name), x = (int)Math.round(sx(label.at())), y = (int)Math.round(sy(label.at()));
			graphics.text(this.font, name, x - tw / 2, y - 4, accent(label.pillar()));
		}
		graphics.disableScissor();
	}

	/** The bar across the top: the screen's name over the web, between two gold rules; the tabs sit at its right. */
	private void topBar(GuiGraphicsExtractor graphics) {
		frame(graphics, 0, 0, this.width, WEB_HEADER);
		int cx = (int)areaX(), tw = this.font.width(this.title);
		graphics.text(this.font, this.title, cx - tw / 2, 7, Ui.GOLD);
		for (int side = -1; side <= 1; side += 2) {
			int x0 = cx + side * (tw / 2 + 8), x1 = cx + side * (tw / 2 + 8 + TITLE_RULE);
			graphics.fill(Math.min(x0, x1), 10, Math.max(x0, x1), 11, COLOR_FRAME_LINE);
			graphics.fill(x1 - 1, 9, x1 + 2, 12, Ui.GOLD);
		}
	}

	/** The bar along the foot: what the mouse and keys do; the buttons sit at its right. */
	private void bottomBar(GuiGraphicsExtractor graphics) {
		frame(graphics, 0, this.height - WEB_FOOTER, this.width, WEB_FOOTER);
		if (onWeb()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.web.keys"), 8, this.height - WEB_FOOTER + 7,
				this.width - FOOT_BUTTONS - 20, Ui.DIM);
		}
	}

	/** Where pillar {@code i}'s block starts in the left panel, and where its attribute rows start. */
	private static int railPillarY(int i) {
		return WEB_HEADER + 31 + i * PILLAR_ROW;
	}

	private static int railStatsY() {
		return railPillarY(ORDER.length) + 32;
	}

	/**
	 * The left panel: your character level and attribute points, each pillar's level, experience and points (press one to
	 * go to its part of the web), your class and alignment, and your attributes (press them to open their tab).
	 */
	private void leftRail(GuiGraphicsExtractor graphics, Skills.Progress shown, int mouseX, int mouseY) {
		int w = railL(), y = WEB_HEADER, h = this.height - WEB_HEADER - WEB_FOOTER, tx = 8, tw = w - 16;
		frame(graphics, 0, y, w, h);
		graphics.enableScissor(3, y + 3, w - 3, y + h - 3);
		var player = this.minecraft.player;
		int points = player == null ? 0 : Stats.points(player, shown);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.stats.level", Stats.characterLevel(shown)), tx, y + 8, tw, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.stats.points", points), tx, y + 18, tw, points > 0 ? Ui.GOOD : Ui.DIM);
		graphics.fill(6, y + 28, w - 6, y + 29, COLOR_FRAME_LINE);
		for (int i = 0; i < ORDER.length; i++) {
			Skills.Pillar p = ORDER[i];
			int py = railPillarY(i), accent = accent(p), level = shown.level(p), spend = shown.points(p);
			if (Ui.over(mouseX, mouseY, 4, py, w - 8, PILLAR_ROW - 2)) {
				graphics.fill(4, py, w - 4, py + PILLAR_ROW - 2, COLOR_OVER);
			}
			graphics.fill(4, py, 6, py + PILLAR_ROW - 2, accent);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.level", Component.translatable("skill.jaymoskingdoms." + p.key()), level),
				tx + 2, py + 1, tw - 2, accent);
			Ui.bar(graphics, tx + 2, py + 11, tw - 2, 2,
				level >= p.maxLevel ? 1.0F : (shown.xp(p) - Skills.total(level)) / (float)Math.max(1, Skills.toNext(level)), accent);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.points", spend), tx + 2, py + 15, tw - 2, spend > 0 ? Ui.GOOD : Ui.DIM);
		}
		int cy = railPillarY(ORDER.length);
		graphics.fill(6, cy + 1, w - 6, cy + 2, COLOR_FRAME_LINE);
		Classes.PlayerClass cls = player == null ? null : Classes.classOf(player);
		Ui.line(graphics, this.font, cls == null ? Component.translatable("gui.jaymoskingdoms.skills.class.none")
			: Component.translatable("gui.jaymoskingdoms.skills.class", Component.translatable("class.jaymoskingdoms." + cls.key())), tx, cy + 6, tw,
			cls == null ? Ui.DIM : Ui.GOLD);
		// (your alignment: the pillar you've made your own, as others see it over your head)
		Skills.Pillar aligned = Renown.alignment(shown);
		Ui.line(graphics, this.font, aligned == null ? Component.translatable("gui.jaymoskingdoms.skills.alignment.none", Renown.ALIGN_LEVEL)
			: Component.translatable("gui.jaymoskingdoms.skills.alignment", Component.translatable("title.jaymoskingdoms." + aligned.key()
				+ (shown.level(aligned) >= aligned.maxLevel ? ".master" : ""))), tx, cy + 16, tw, aligned == null ? Ui.DIM : accent(aligned));
		graphics.fill(6, cy + 28, w - 6, cy + 29, COLOR_FRAME_LINE);
		int sy = railStatsY(), cap = player == null ? Stats.TIER_CAP[0] : Stats.cap(player);
		boolean overStats = mouseX < w && mouseY >= sy && mouseY < y + h;
		for (Stats.Stat stat : shownStats()) {
			int value = player == null ? 0 : Stats.value(player, shown, stat);
			Ui.line(graphics, this.font, Component.translatable("stat.jaymoskingdoms." + stat.key()), tx, sy, tw - 22, overStats ? Ui.TEXT : Ui.DIM);
			Ui.lineRight(graphics, this.font, Component.literal(String.valueOf(value)), w - 8, sy, 20, value >= cap ? Ui.GOLD : Ui.TEXT);
			Ui.bar(graphics, tx, sy + 9, tw, 1, value / (float)Stats.MAX, Ui.GOLD);
			sy += RAIL_STAT_ROW;
		}
		graphics.disableScissor();
	}

	/** Where the right panel's Learn button sits: x, y, width, height. */
	private int[] learnBox() {
		int w = railR();
		return new int[] {this.width - w + 8, this.height - WEB_FOOTER - 24, w - 16, 16};
	}

	/**
	 * The right panel: the perk pointed at, or else the one picked, in full (its picture, name, branch and rank, what it
	 * does, costs, needs and leads to) with the button that learns the picked one; a pointed-at crest's pillar; or else
	 * how the web works.
	 */
	private void rightRail(GuiGraphicsExtractor graphics, Skills.Progress shown, Skills.@Nullable Perk hovered, Skills.@Nullable Pillar crest, int mouseX,
		int mouseY) {
		int w = railR(), x = this.width - w, y = WEB_HEADER, h = this.height - WEB_HEADER - WEB_FOOTER, ty = y + 8;
		frame(graphics, x, y, w, h);
		Skills.Perk perk = hovered != null ? hovered : selected;
		List<Component> text = new ArrayList<>();
		int accent = Ui.GOLD;
		if (perk != null) {
			int rank = shown.rank(perk);
			accent = perk.bridge() ? Ui.GOLD : accent(perk.pillar);
			graphics.fill(x + 8, ty, x + 30, ty + 22, accent);
			graphics.fill(x + 9, ty + 1, x + 29, ty + 21, COLOR_OPEN_FILL);
			icon(graphics, SkillIcons.of(perk), x + 19, ty + 11, 10);
			Ui.line(graphics, this.font, Component.translatable("skill.jaymoskingdoms.perk." + perk.key()), x + 35, ty + 2, w - 43, accent);
			SkillWeb.Branch branch = SkillWeb.BRANCHES.get(perk);
			Component where = branch == null ? Component.translatable("skill.jaymoskingdoms." + perk.pillar.key())
				: Component.translatable("gui.jaymoskingdoms.skills.branch." + branch.key());
			Ui.line(graphics, this.font, Component.empty().append(where).append("  " + rank + "/" + perk.maxRank), x + 35, ty + 12, w - 43, Ui.DIM);
			ty += 28;
			describe(text, shown, perk);
		} else if (crest != null) {
			accent = accent(crest);
			Ui.line(graphics, this.font, Component.translatable("skill.jaymoskingdoms." + crest.key()), x + 9, ty, w - 18, accent);
			ty += 12;
			text.add(Component.translatable("gui.jaymoskingdoms.skills.hint." + crest.key()).withColor(Ui.TEXT));
		} else {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.web.pick"), x + 9, ty, w - 18, accent);
			ty += 12;
			text.add(Component.translatable("gui.jaymoskingdoms.skills.web.help").withColor(Ui.DIM));
		}
		boolean button = perk != null && perk == selected;
		int[] box = learnBox();
		int bottom = button ? box[1] - 4 : y + h - 6;
		Ui.detail(graphics, this.font, x, ty, w, Math.max(1, (bottom - ty - 5) / Ui.LINE), null, accent, text);
		if (button) {
			int rank = shown.rank(perk);
			Component label = rank >= perk.maxRank ? Component.translatable("gui.jaymoskingdoms.skills.web.learnt")
				: rank > 0 ? Component.translatable("gui.jaymoskingdoms.skills.web.raise", rank + 1, perk.maxRank)
				: Component.translatable("gui.jaymoskingdoms.skills.web.learn");
			Ui.button(graphics, this.font, box[0], box[1], box[2], box[3], label, accent, Ui.over(mouseX, mouseY, box[0], box[1], box[2], box[3]),
				canLearn(shown, perk));
		}
	}

	/** What {@code perk} does, costs, needs and leads to, and whether it can be learnt (the right panel's text). */
	private void describe(List<Component> text, Skills.Progress shown, Skills.Perk perk) {
		var player = this.minecraft.player;
		int accent = perk.bridge() ? Ui.GOLD : accent(perk.pillar);
		text.add(Component.translatable("skill.jaymoskingdoms.perk." + perk.key() + ".desc").withColor(Ui.TEXT));
		Component cost = Component.translatable("gui.jaymoskingdoms.skills.cost", perk.cost);
		if (perk.minLevel > 0) {
			cost = Component.empty().append(cost).append("  ·  ").append(Component.translatable("gui.jaymoskingdoms.skills.needs_level", perk.minLevel));
		}
		text.add(cost.copy().withColor(Ui.DIM));
		if (perk.bridge() && perk.other != null) {
			text.add(Component.translatable("gui.jaymoskingdoms.skills.bridge", Component.translatable("skill.jaymoskingdoms." + perk.pillar.key()),
				Component.translatable("skill.jaymoskingdoms." + perk.other.key())).withColor(Ui.GOLD));
		}
		if (!perk.requires.isEmpty()) {
			text.add(Component.translatable(perk.bridge() ? "gui.jaymoskingdoms.skills.needs_any" : "gui.jaymoskingdoms.skills.needs", names(perk.requires))
				.withColor(Ui.DIM));
		}
		List<Skills.Perk> leads = SkillWeb.leadsTo(perk);
		if (!leads.isEmpty()) {
			text.add(Component.translatable("gui.jaymoskingdoms.skills.leads", names(leads)).withColor(0xFFF0D28C));
		}
		String blocked = shown.blocked(perk);
		// a higher technique wants Knowledge 4
		if (blocked == null && player != null && com.jaymoskingdoms.Knowledge.lockedPerk(player, perk)) {
			blocked = "gui.jaymoskingdoms.skills.blocked.knowledge";
		}
		// a class skill of another class
		if (blocked == null && player != null && com.jaymoskingdoms.Learnables.classLocked(player, perk)) {
			blocked = "gui.jaymoskingdoms.skills.blocked.class";
		}
		java.util.Set<com.jaymoskingdoms.Classes.PlayerClass> forClasses = com.jaymoskingdoms.Learnables.classesFor(perk);
		if (forClasses != null) {
			text.add(Component.translatable("gui.jaymoskingdoms.skills.for_classes", classNames(forClasses)).withColor(Ui.DIM));
		}
		if (shown.ranks().containsKey(com.jaymoskingdoms.Learnables.taughtKey(perk))) {
			text.add(Component.translatable("gui.jaymoskingdoms.skills.taught").withColor(Ui.GOOD));
		}
		text.add(blocked == null ? Component.translatable("gui.jaymoskingdoms.skills.learn").withColor(Ui.GOOD)
			: Component.translatable(blocked).withColor(blocked.endsWith(".max") ? accent : Ui.BAD));
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
		Ui.window(graphics, x, y, w, panelH());
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
			int fromTraining = player == null ? 0 : Stats.fromTraining(player, stat);
			int value = Math.min(cap, spent + fromHeritage + fromClass + fromStory + fromTraining);
			String blocked = player == null ? "" : Stats.blocked(player, shown, stat);
			Ui.line(graphics, this.font, Component.translatable("stat.jaymoskingdoms." + stat.key()), x + 13, ry + 3, barX - x - 18, value > 0 ? Ui.TEXT : Ui.DIM);
			// heritage's (teal), then class's (gold), then (0.7.216) your story's (parchment), then trained in the weights (red), then
			// yours (white); empty to the cap, dark past it
			int y0 = ry + 4, y1 = ry + 10, h = Math.min(value, fromHeritage), c = Math.min(value, h + fromClass), st = Math.min(value, c + fromStory);
			int tr = Math.min(value, st + fromTraining);
			barFill(graphics, barX, y0, y1, 0, cap, Ui.BAR_BACK);
			barFill(graphics, barX, y0, y1, cap, Stats.MAX, LOCKED);
			barFill(graphics, barX, y0, y1, 0, h, HeritageScreen.ACCENT);
			barFill(graphics, barX, y0, y1, h, c, Ui.GOLD);
			barFill(graphics, barX, y0, y1, c, st, LifeStoryScreen.ACCENT);
			barFill(graphics, barX, y0, y1, st, tr, TRAINED);
			barFill(graphics, barX, y0, y1, tr, value, Ui.TEXT);
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
			if (hovered == Stats.Stat.MIGHT && Stats.trained(player) > 0.0) {
				// trained in the weights: the points, and how far the next one has come
				double trained = Stats.trained(player);
				text.add(Component.translatable("gui.jaymoskingdoms.stats.trained_might", Stats.fromTraining(player, hovered),
					(int)Math.floor((trained - Math.floor(trained)) * 100.0)).withColor(TRAINED));
			}
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
		Ui.window(graphics, x, y, w, panelH());
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
		// when the panel is collapsed away, the strip on the left brings it back
		if (panelCollapsed && (stats || jobs)) {
			int tabX = 4, tabY = 4, tabW = 24, tabH = this.height - 8;
			if (event.x() >= tabX && event.x() < tabX + tabW && event.y() >= tabY && event.y() < tabY + tabH) {
				panelCollapsed = false;
				rebuildWidgets();
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
		}

		// a collapsed panel must not take clicks: the rows behind it are invisible, so a click would spend unseen
		if (!panelCollapsed) {
			Stats.Stat stat = statAt(event.x(), event.y());
			if (stat != null) {
				if (this.minecraft.player != null && Stats.blocked(this.minecraft.player, progress(), stat) == null) {
					// (0.7.540) shift-click: ten at once
					ClientPlayNetworking.send(new Stats.Raise(stat.ordinal(), event.hasShiftDown() ? Stats.SHIFT_STEP : 1));
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				}
				return true;
			}
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (onWeb()) {
			int mx = (int)event.x(), my = (int)event.y();
			int[] box = learnBox();
			if (selected != null && Ui.over(mx, my, box[0], box[1], box[2], box[3])) {
				learn(selected);
				return true;
			}
			// the left panel: a pillar's block goes to its part of the web, the attributes open their tab
			for (int i = 0; i < ORDER.length; i++) {
				if (Ui.over(mx, my, 4, railPillarY(i), railL() - 8, PILLAR_ROW - 2)) {
					look(SkillWeb.focus(ORDER[i]), Math.max(targetZoom, OPEN_ZOOM));
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					return true;
				}
			}
			if (mx < railL() && my >= railStatsY() && my < this.height - WEB_FOOTER) {
				listScroll = 0;
				stats = true;
				rebuildWidgets();
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
		}
		if (webOnScreen() && inWebArea(event.x(), event.y())) {
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
		if (pressed && webOnScreen()) {
			panX += dx;
			panY += dy;
			targetX += dx;
			targetY += dy;
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
			if (Math.abs(event.x() - pressX) + Math.abs(event.y() - pressY) < 4.0) {
				// a click, not a drag: the first on a perk picks it, the second learns it; a crest goes to its pillar
				Skills.Pillar crest = perk == null ? crestAt(event.x(), event.y()) : null;
				if (perk != null && perk == perkAt(event.x(), event.y())) {
					if (perk == selected) {
						learn(perk);
					} else {
						select(perk);
					}
				} else if (crest != null) {
					look(SkillWeb.focus(crest), Math.max(targetZoom, OPEN_ZOOM));
				} else if (perk == null) {
					selected = null;
				}
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (onWeb() && selected != null && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			learn(selected);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (webOnScreen() && scrollY != 0.0) {
			// zoom about the mouse: the point under it stays put
			double before = targetZoom;
			targetZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, targetZoom * (scrollY > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP)));
			double ux = (mouseX - areaX() - targetX) / before, uy = (mouseY - areaY() - targetY) / before;
			targetX += ux * (before - targetZoom);
			targetY += uy * (before - targetZoom);
			return true;
		}
		if (!panelCollapsed && (stats || jobs) && scrollY != 0.0 && maxScroll() > 0) {
			// the Attributes or Jobs rows, a row a notch
			listScroll = Math.clamp(listScroll - (int)Math.signum(scrollY) * (jobs ? JOB_ROW : STAT_ROW), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Skills.Progress shown = progress();
		if ((jobs || stats) && !panelCollapsed) {
			if (jobs) {
				jobs(graphics, mouseX, mouseY);
			} else {
				attributes(graphics, shown, mouseX, mouseY);
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		glide();
		Skills.Perk hovered = pressed && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) >= 4.0 ? null : perkAt(mouseX, mouseY);
		web(graphics, shown, mouseX, mouseY, hovered);
		topBar(graphics);
		bottomBar(graphics);
		rightRail(graphics, shown, hovered, hovered == null ? crestAt(mouseX, mouseY) : null, mouseX, mouseY);
		if (onWeb()) {
			leftRail(graphics, shown, mouseX, mouseY);
		} else {
			// the Attributes or Jobs panel is collapsed away: the strip on the left brings it back
			int tabX = 4, tabY = 4, tabW = 24, tabH = this.height - 8;
			graphics.fill(tabX, tabY, tabX + tabW, tabY + tabH, 0x80101018);
			graphics.fill(tabX, tabY, tabX + 2, tabY + tabH, 0xFFFFC21A);
			int cx = tabX + tabW - 8, cy = tabY + tabH / 2;
			graphics.fill(cx - 4, cy - 4, cx, cy, 0xFFFFC21A);
			graphics.fill(cx - 4, cy, cx, cy + 4, 0xFFFFC21A);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

}
