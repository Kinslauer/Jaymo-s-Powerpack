package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterGrowth;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterSlots;
import com.jaymospowers.power.zfighter.ZFighterTeleport;
import com.jaymospowers.power.zfighter.ZFighterTraining;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The training screen (X): Training Points to spend on the five stats, the forms past Super (each with its mastery and
 * what it still needs) and Limit Break's tiers. Everything is checked again on the server.
 */
public class ZFighterTrainingScreen extends Screen {
	private static final int MAX_W = 340;
	private static final int H = 236;
	private static final int ROW = 16;
	private static final int FORM_ROW = 24;
	/** The box at the bottom that shows the hovered row's full description. */
	private static final int DETAIL_H = 40;
	private static final int BUTTON_W = 54;
	/** (0.17.46) The small wheel button left of each technique's Use button. */
	private static final int WHEEL_W = 14;
	private static final int COLOR_PANEL = 0xD0101018;
	private static final int COLOR_ROW = 0x30FFFFFF;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA8A8B8;
	private static final int COLOR_ACCENT = 0xFF000000 | ZFighterPower.COLOR;
	private static final int COLOR_GOLD = 0xFF000000 | ZFighterPower.SUPER_COLOR;
	private static final int COLOR_LIMIT = 0xFFFF6A50;
	private static final String[] STAT_KEYS = {"strength", "toughness", "vitality", "ki_power", "energy"};
	private static final String[] TAB_KEYS = {"tab_stats", "tab_forms", "tab_techniques"};
	private static final int STATS_PAGE = 0;
	private static final int FORMS_PAGE = 1;
	private static final int TECHNIQUES_PAGE = 2;

	private final List<Button> statButtons = new ArrayList<>();
	private final List<Button> formButtons = new ArrayList<>();
	private final List<Button> techniqueButtons = new ArrayList<>();
	/** (0.17.46) Each technique's wheel button: on the technique wheel (Y) or off it. */
	private final List<Button> wheelButtons = new ArrayList<>();
	private final List<Button> tabButtons = new ArrayList<>();
	private Button limitButton;
	/** (0.17.46) The stats page's aura style button: each press picks the next style. */
	private Button auraButton;
	/** Which page is open (kept between openings). */
	private static int page;
	/** (0.17.45) The techniques page shows this many rows; the wheel scrolls the rest. */
	private static final int TECH_ROWS = 8;
	private static int techScroll;

	public ZFighterTrainingScreen() {
		super(Component.translatable("screen.jaymospowers.zfighter.title"));
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	private int buttonX() {
		return left() + panelW() - BUTTON_W - 6;
	}

	/** Where each page's rows start, under the title, TP and tabs. */
	private int contentY() {
		return top() + 52;
	}

	private int detailY() {
		return top() + H - DETAIL_H - 4;
	}

	@Override
	protected void init() {
		statButtons.clear();
		formButtons.clear();
		techniqueButtons.clear();
		wheelButtons.clear();
		tabButtons.clear();
		int w = panelW();
		int tabW = (w - 16) / 3;
		for (int i = 0; i < 3; i++) {
			int tab = i;
			tabButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter." + TAB_KEYS[i]), b -> page = tab)
				.bounds(left() + 8 + i * (tabW + 0), top() + 30, tabW - 2, 16).build()));
		}
		for (int i = 0; i < ZFighterTraining.STATS; i++) {
			int stat = i;
			statButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> send(ZFighterNet.BUY_STAT, stat))
				.bounds(left() + w - 26, contentY() + 12 + i * ROW - 3, 20, 14).build()));
		}
		for (int form = 2; form <= ZFighterTraining.FORMS; form++) {
			int f = form;
			formButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter.learn"), b -> send(ZFighterNet.BUY_FORM, f))
				.bounds(buttonX(), contentY() + (form - 1) * FORM_ROW - 3, BUTTON_W, 14).build()));
		}
		// (0.17.60) the HUD editor, and the first-time tip
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter.hud_button"),
			b -> this.minecraft.gui.setScreen(new ZFighterHudEditor(this))).bounds(left() + 8, top() + 7, 44, 14).build());
		// (0.17.61) the Story screen: sagas and side jobs
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter.story_button"),
			b -> this.minecraft.gui.setScreen(new ZFighterStoryScreen(this))).bounds(left() + 56, top() + 7, 44, 14).build());
		ZFighterTips.show(ZFighterTips.Tip.TRAINING);
		auraButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			int next = (com.jaymospowers.power.zfighter.ZFighterAura.style(this.minecraft.player) + 1) % com.jaymospowers.power.zfighter.ZFighterAura.STYLES;
			send(ZFighterNet.AURA_STYLE, next);
		}).bounds(buttonX(), auraY() - 3, BUTTON_W, 14).build());
		limitButton = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter.learn"), b -> send(ZFighterNet.BUY_LIMIT, 0))
			.bounds(buttonX(), limitY() - 3, BUTTON_W, 14).build());
		for (int i = 0; i < ZFighterTraining.TECHNIQUES; i++) {
			int technique = i;
			techniqueButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> techniqueAction(technique))
				.bounds(buttonX(), contentY() + i * ROW - 3, BUTTON_W, 14).build()));
			wheelButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> send(ZFighterNet.WHEEL_SLOT, technique))
				.bounds(buttonX() - WHEEL_W - 2, contentY() + i * ROW - 3, WHEEL_W, 14).build()));
		}
	}

	/** (0.17.46) The aura style row, under the five stats. */
	private int auraY() {
		return contentY() + 12 + ZFighterTraining.STATS * ROW + 6;
	}

	private int limitY() {
		return contentY() + ZFighterTraining.FORMS * FORM_ROW + 8;
	}

	private void techniqueAction(int technique) {
		ZFighterTraining.Data data = ZFighterTraining.of(this.minecraft.player);
		if (technique == ZFighterTraining.KI_WEAPON && data.knows(technique) && data.quick() == technique) {
			// (0.17.45) the ki weapon's button picks its shape (and puts it on G)
			ClientPlayNetworking.send(new com.jaymospowers.power.zfighter.ZFighterCombat.Shape());
			return;
		}
		send(data.knows(technique) ? ZFighterNet.EQUIP_TECHNIQUE : ZFighterNet.BUY_TECHNIQUE, technique);
	}

	private static void send(int action, int index) {
		ClientPlayNetworking.send(new ZFighterNet.Train(action, index));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (page == TECHNIQUES_PAGE && scrollY != 0.0) {
			techScroll = Math.max(0, Math.min(ZFighterTraining.TECHNIQUES - TECH_ROWS, techScroll - (int)Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** One line of text, cut with "..." if it would run past {@code width}. */
	private void line(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		String s = text.getString();
		if (this.font.width(s) > width) {
			s = this.font.plainSubstrByWidth(s, width - this.font.width("...")) + "...";
		}
		graphics.text(this.font, s, x, y, color);
	}

	/** Wrapped text, at most {@code lines} lines. */
	private void wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int lines, int color) {
		var split = this.font.split(text, width);
		for (int i = 0; i < Math.min(lines, split.size()); i++) {
			graphics.text(this.font, split.get(i), x, y + i * 9, color);
		}
	}

	private static boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private static void show(List<Button> buttons, boolean visible) {
		for (Button button : buttons) {
			button.visible = visible;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		boolean creative = player.isCreative();
		int x = left();
		int y = top();
		int w = panelW();
		graphics.fill(x, y, x + w, y + H, COLOR_PANEL);
		graphics.fill(x, y, x + w, y + 2, COLOR_ACCENT);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 6, COLOR_ACCENT);
		graphics.centeredText(this.font, Component.translatable("screen.jaymospowers.zfighter.tp", data.tp()), this.width / 2, y + 18, COLOR_TEXT);
		show(statButtons, page == STATS_PAGE);
		show(formButtons, page == FORMS_PAGE);
		limitButton.visible = page == FORMS_PAGE;
		auraButton.visible = page == STATS_PAGE;
		show(techniqueButtons, page == TECHNIQUES_PAGE);
		show(wheelButtons, page == TECHNIQUES_PAGE);
		for (int i = 0; i < tabButtons.size(); i++) {
			tabButtons.get(i).active = page != i;
		}
		Component detail = switch (page) {
			case FORMS_PAGE -> forms(graphics, player, data, creative, x, w, mouseX, mouseY);
			case TECHNIQUES_PAGE -> techniques(graphics, player, data, x, w, mouseX, mouseY);
			default -> stats(graphics, player, data, x, w, mouseX, mouseY);
		};
		int dy = detailY();
		graphics.fill(x + 6, dy - 3, x + w - 6, dy - 2, COLOR_ROW);
		wrapped(graphics, detail, x + 8, dy, w - 16, 4, COLOR_DIM);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private Component stats(GuiGraphicsExtractor graphics, LocalPlayer player, ZFighterTraining.Data data, int x, int w, int mouseX, int mouseY) {
		int cost = ZFighterTraining.statCost(player);
		line(graphics, Component.translatable("screen.jaymospowers.zfighter.stats", cost), x + 8, contentY(), w - 16, COLOR_GOLD);
		ZFighterGrowth.Data growth = ZFighterGrowth.of(player);
		Component detail = Component.translatable("screen.jaymospowers.zfighter.earn");
		for (int i = 0; i < ZFighterTraining.STATS; i++) {
			int level = data.stat(i);
			int ry = contentY() + 12 + i * ROW;
			graphics.fill(x + 6, ry - 4, x + w - 6, ry + 11, i % 2 == 0 ? COLOR_ROW : 0x18FFFFFF);
			line(graphics, Component.translatable("stat.jaymospowers.zfighter." + STAT_KEYS[i]), x + 10, ry, 66, COLOR_TEXT);
			graphics.text(this.font, level + "/" + ZFighterTraining.STAT_CAP, x + 80, ry, COLOR_DIM);
			line(graphics, Component.translatable("stat.jaymospowers.zfighter." + STAT_KEYS[i] + ".effect", effect(i, level)), x + 118, ry, w - 118 - 32,
				COLOR_DIM);
			// the stat's own training bar: a thin line under the row
			float fill = level >= ZFighterTraining.STAT_CAP ? 1.0F : Math.min(1.0F, growth.progress(i) / ZFighterGrowth.bar(level));
			int barW = w - 10 - 32;
			graphics.fill(x + 10, ry + 9, x + 10 + barW, ry + 10, 0xFF303030);
			graphics.fill(x + 10, ry + 9, x + 10 + Math.round(barW * fill), ry + 10, COLOR_ACCENT);
			statButtons.get(i).active = level < ZFighterTraining.STAT_CAP && data.tp() >= cost;
			if (over(mouseX, mouseY, x + 6, ry - 4, w - 38, ROW - 1)) {
				detail = growthDetail(player, growth, i, level, fill);
			}
		}
		// (0.17.46) the aura style row: what his ki looks like when it flares
		int style = com.jaymospowers.power.zfighter.ZFighterAura.style(player);
		int ay = auraY();
		graphics.fill(x + 6, ay - 4, x + w - 6, ay + 11, COLOR_ROW);
		line(graphics, Component.translatable("screen.jaymospowers.zfighter_aura.label"), x + 10, ay, 66, COLOR_TEXT);
		auraButton.setMessage(Component.translatable("screen.jaymospowers.zfighter_aura." + com.jaymospowers.power.zfighter.ZFighterAura.KEYS[style]));
		if (over(mouseX, mouseY, x + 6, ay - 4, w - 12, ROW - 1)) {
			detail = Component.translatable("screen.jaymospowers.zfighter_aura." + com.jaymospowers.power.zfighter.ZFighterAura.KEYS[style] + ".desc");
		}
		return detail;
	}

	/** A stat's own training: how far its bar is, and how fresh each of its methods still is today. */
	private static Component growthDetail(LocalPlayer player, ZFighterGrowth.Data growth, int stat, int level, float fill) {
		Component name = Component.translatable("stat.jaymospowers.zfighter." + STAT_KEYS[stat]);
		var text = level >= ZFighterTraining.STAT_CAP ? Component.translatable("screen.jaymospowers.zfighter.growth_max", name).copy()
			: Component.translatable("screen.jaymospowers.zfighter.growth", name, String.format(Locale.ROOT, "%.1f", fill * 100.0F)).copy();
		long day = ZFighterGrowth.day(player);
		boolean first = true;
		for (int m = 0; m < ZFighterGrowth.METHODS; m++) {
			if (ZFighterGrowth.METHOD_STAT[m] != stat) {
				continue;
			}
			text.append(first ? " " : ", ").append(Component.translatable("screen.jaymospowers.zfighter.growth_method",
				Component.translatable("training.jaymospowers.zfighter." + ZFighterGrowth.METHOD_KEYS[m]), Math.round(growth.factor(m, day) * 100.0F)));
			first = false;
		}
		return text;
	}

	private Component forms(GuiGraphicsExtractor graphics, LocalPlayer player, ZFighterTraining.Data data, boolean creative, int x, int w, int mouseX,
		int mouseY) {
		Component detail = Component.translatable("screen.jaymospowers.zfighter.forms_hint");
		int open = ZFighterTraining.formsOpen(player);
		int textW = w - BUTTON_W - 24;
		for (int form = 1; form <= ZFighterTraining.FORMS; form++) {
			int ry = contentY() + (form - 1) * FORM_ROW;
			graphics.fill(x + 6, ry - 4, x + w - 6, ry + FORM_ROW - 6, form % 2 == 1 ? COLOR_ROW : 0x18FFFFFF);
			Component name = Component.translatable(ZFighterLineage.formKey(player, form));
			line(graphics, name, x + 10, ry, 90, form <= open ? COLOR_GOLD : COLOR_DIM);
			Component need = form == 1 ? Component.translatable("screen.jaymospowers.zfighter.super_how")
				: Component.translatable("screen.jaymospowers.zfighter.form_needs", ZFighterTraining.FORM_TP[form],
					ZFighterTraining.NEXT_FORM_MASTERY / 100, Component.translatable(ZFighterLineage.formKey(player, form - 1)),
					ZFighterTraining.FORM_HARDSHIP[form], ZFighterPower.hardship(player));
			if (form <= open) {
				int mastery = data.mastery(form);
				line(graphics, Component.translatable("screen.jaymospowers.zfighter.mastery", String.format(Locale.ROOT, "%.1f", mastery / 100.0)),
					x + 104, ry, textW - 98, COLOR_TEXT);
				int bar = Math.min(120, textW - 98);
				graphics.fill(x + 104, ry + 10, x + 104 + bar, ry + 12, 0xFF303030);
				graphics.fill(x + 104, ry + 10, x + 104 + bar * mastery / ZFighterTraining.MASTERY_MAX, ry + 12, COLOR_GOLD);
			} else {
				line(graphics, Component.translatable(form == 1 ? "screen.jaymospowers.zfighter.locked_hardship" : "screen.jaymospowers.zfighter.locked_hover"),
					x + 104, ry, textW - 98, COLOR_DIM);
			}
			if (over(mouseX, mouseY, x + 6, ry - 4, w - 12, FORM_ROW - 2)) {
				detail = form <= open ? Component.translatable("screen.jaymospowers.zfighter.form_open", name) : need;
			}
			if (form >= 2) {
				Button button = formButtons.get(form - 2);
				button.visible = form > open;
				button.active = form == open + 1 && data.tp() >= ZFighterTraining.FORM_TP[form]
					&& (ZFighterTraining.formBlocker(player, form) == null || creative && open >= 1);
			}
		}

		// Limit Break
		int ly = limitY();
		int limit = data.limit();
		graphics.fill(x + 6, ly - 4, x + w - 6, ly + 20, 0x30FF6A50);
		line(graphics, Component.translatable("screen.jaymospowers.zfighter.limit", limit), x + 10, ly, textW, COLOR_LIMIT);
		boolean more = limit + 1 < ZFighterTraining.LIMIT_TP.length;
		if (more) {
			line(graphics, Component.translatable("screen.jaymospowers.zfighter.limit_next", limit + 1, ZFighterTraining.limitCost(player, limit + 1)),
				x + 10, ly + 10, textW, COLOR_DIM);
		}
		limitButton.visible = more;
		limitButton.active = more && data.tp() >= ZFighterTraining.limitCost(player, limit + 1);
		if (over(mouseX, mouseY, x + 6, ly - 4, w - 12, 24)) {
			detail = Component.translatable("screen.jaymospowers.zfighter.limit_how", ZFighterInputClient.keyLabel(this.minecraft));
		}
		return detail;
	}

	/** The techniques page: each one, its key, its price or where it is; hover one for what it does. */
	private Component techniques(GuiGraphicsExtractor graphics, LocalPlayer player, ZFighterTraining.Data data, int x, int w, int mouseX,
		int mouseY) {
		Component detail = Component.translatable("screen.jaymospowers.zfighter.techniques_hint");
		int statusX = x + 136;
		// scrollbar for the rows past the first screenful
		int track = TECH_ROWS * ROW;
		graphics.fill(x + w - 4, contentY() - 4, x + w - 3, contentY() - 4 + track, 0x30FFFFFF);
		int thumb = track * TECH_ROWS / ZFighterTraining.TECHNIQUES;
		int thumbY = contentY() - 4 + (track - thumb) * techScroll / Math.max(1, ZFighterTraining.TECHNIQUES - TECH_ROWS);
		graphics.fill(x + w - 4, thumbY, x + w - 3, thumbY + thumb, COLOR_ACCENT);
		for (int i = 0; i < ZFighterTraining.TECHNIQUES; i++) {
			int row = i - techScroll;
			if (row < 0 || row >= TECH_ROWS) {
				techniqueButtons.get(i).visible = false;
				wheelButtons.get(i).visible = false;
				continue;
			}
			int ry = contentY() + row * ROW;
			techniqueButtons.get(i).setY(ry - 3);
			String key = ZFighterLineage.techniqueKey(player, i);
			boolean known = data.knows(i);
			boolean spirit = i == ZFighterTraining.SPIRIT_BOMB;
			// (0.17.45) Instant Teleport lives on B, in place of Flash Step
			boolean teleport = i == ZFighterTraining.TELEPORT;
			boolean equipped = teleport ? ZFighterTeleport.equipped(player) : !spirit && (ZFighterTraining.charged(i) ? data.charged() == i : data.quick() == i);
			String slot = spirit ? "C" : teleport ? "B" : ZFighterTraining.charged(i) ? "V" : "G";
			graphics.fill(x + 6, ry - 4, x + w - 6, ry + 11, i % 2 == 0 ? COLOR_ROW : 0x18FFFFFF);
			graphics.text(this.font, slot, x + 10, ry, COLOR_ACCENT);
			line(graphics, Component.translatable(key), x + 22, ry, statusX - x - 26, known ? COLOR_GOLD : COLOR_TEXT);
			Component status;
			if (equipped) {
				status = Component.translatable("screen.jaymospowers.zfighter.on_key", slot);
			} else if (known) {
				status = Component.translatable(spirit ? "screen.jaymospowers.zfighter.spirit_known" : "screen.jaymospowers.zfighter.known");
			} else {
				status = Component.translatable("screen.jaymospowers.zfighter.tp_cost", ZFighterTraining.techniqueCost(player, i));
			}
			line(graphics, status, statusX, ry, buttonX() - WHEEL_W - 2 - statusX - 4, COLOR_DIM);
			Button wheel = wheelButtons.get(i);
			wheel.setY(ry - 3);
			wheel.visible = known && ZFighterSlots.fits(i);
			boolean onWheel = ZFighterSlots.of(player).contains(i);
			wheel.setMessage(Component.literal(onWheel ? "-" : "+").withColor(onWheel ? COLOR_GOLD : 0xFFFFFFFF));
			Button button = techniqueButtons.get(i);
			String blocker = null;
			if (!known) {
				blocker = ZFighterTraining.techniqueBlocker(player, i);
				button.setMessage(Component.translatable("screen.jaymospowers.zfighter.learn"));
				button.active = blocker == null;
			} else {
				button.setMessage(Component.translatable(teleport && equipped ? "screen.jaymospowers.zfighter_senses.flash_step" : "screen.jaymospowers.zfighter.equip"));
				button.visible = teleport || !spirit && !equipped;
				button.active = true;
				if (i == ZFighterTraining.KI_WEAPON && equipped) {
					// (0.17.45) on G, the ki weapon's button is its shape
					button.setMessage(Component.translatable("technique.jaymospowers.zfighter.ki_weapon."
						+ com.jaymospowers.power.zfighter.ZFighterKiWeapon.SHAPE_KEYS[com.jaymospowers.power.zfighter.ZFighterKiWeapon.shape(player)]));
					button.visible = true;
				}
			}
			if (wheel.visible && wheel.isHovered()) {
				detail = Component.translatable(onWheel ? "screen.jaymospowers.zfighter_wheel.take_off" : "screen.jaymospowers.zfighter_wheel.add");
			} else if (over(mouseX, mouseY, x + 6, ry - 4, w - 12, ROW)) {
				Component desc = Component.translatable(key + ".desc");
				detail = blocker != null && !button.active ? desc.copy().append(" ").append(Component.translatable(blocker)) : desc;
			}
		}
		return detail;
	}

	/** What {@code level} levels of stat {@code index} give, as shown. */
	private static String effect(int index, int level) {
		return switch (index) {
			case ZFighterTraining.STRENGTH -> String.format(Locale.ROOT, "%.1f", level * ZFighterTraining.STRENGTH_PER_LEVEL);
			case ZFighterTraining.TOUGHNESS -> String.format(Locale.ROOT, "%.1f", level * ZFighterTraining.TOUGHNESS_PER_LEVEL * 100.0);
			case ZFighterTraining.VITALITY -> String.format(Locale.ROOT, "%.1f", level * ZFighterTraining.VITALITY_PER_LEVEL / 2.0);
			case ZFighterTraining.KI_POWER -> String.format(Locale.ROOT, "%.1f", level * ZFighterTraining.KI_POWER_PER_LEVEL * 100.0);
			default -> Integer.toString(level * ZFighterTraining.ENERGY_PER_LEVEL);
		};
	}
}
