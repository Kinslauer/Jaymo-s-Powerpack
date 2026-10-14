package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterPower;
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
	private static final int W = 330;
	private static final int ROW = 13;
	private static final int COLOR_PANEL = 0xD0101018;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA8A8B8;
	private static final int COLOR_ACCENT = 0xFF000000 | ZFighterPower.COLOR;
	private static final int COLOR_GOLD = 0xFF000000 | ZFighterPower.SUPER_COLOR;
	private static final String[] STAT_KEYS = {"strength", "toughness", "vitality", "ki_power", "energy"};

	private final List<Button> statButtons = new ArrayList<>();
	private final List<Button> formButtons = new ArrayList<>();
	private final List<Button> techniqueButtons = new ArrayList<>();
	private final List<Button> lineageButtons = new ArrayList<>();
	private Button limitButton;
	private Button tabButton;
	/** Which page: the stats, forms and Limit Break, or the techniques. */
	private static boolean techniquesPage;

	public ZFighterTrainingScreen() {
		super(Component.translatable("screen.jaymospowers.zfighter.title"));
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - height()) / 2);
	}

	private static int height() {
		return 30 + 12 + ZFighterTraining.STATS * ROW + 16 + 12 + ZFighterTraining.FORMS * (ROW * 2) + 16 + ROW * 2 + 30;
	}

	private int statsY() {
		return top() + 42;
	}

	private int formsY() {
		return statsY() + ZFighterTraining.STATS * ROW + 28;
	}

	private int limitY() {
		return formsY() + ZFighterTraining.FORMS * ROW * 2 + 16;
	}

	@Override
	protected void init() {
		statButtons.clear();
		formButtons.clear();
		techniqueButtons.clear();
		lineageButtons.clear();
		for (int i = ZFighterLineage.EARTHLING; i < ZFighterLineage.LINEAGES; i++) {
			int lineage = i;
			lineageButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter.choose"),
				b -> send(ZFighterNet.PICK_LINEAGE, lineage)).bounds(left() + W - 56, lineageY() + (i - 1) * LINEAGE_ROW - 2, 50, 12).build()));
		}
		tabButton = addRenderableWidget(Button.builder(Component.empty(), b -> techniquesPage = !techniquesPage)
			.bounds(left() + W - 86, top() + 4, 80, 12).build());
		for (int i = 0; i < ZFighterTraining.TECHNIQUES; i++) {
			int technique = i;
			techniqueButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> techniqueAction(technique))
				.bounds(left() + W - 56, techniquesY() + i * TECH_ROW - 2, 50, 12).build()));
		}
		for (int i = 0; i < ZFighterTraining.STATS; i++) {
			int stat = i;
			statButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> send(ZFighterNet.BUY_STAT, stat))
				.bounds(left() + W - 26, statsY() + i * ROW - 2, 20, 12).build()));
		}
		for (int form = 2; form <= ZFighterTraining.FORMS; form++) {
			int f = form;
			formButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter.learn"), b -> send(ZFighterNet.BUY_FORM, f))
				.bounds(left() + W - 56, formsY() + (form - 1) * ROW * 2 - 2, 50, 12).build()));
		}
		limitButton = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter.learn"), b -> send(ZFighterNet.BUY_LIMIT, 0))
			.bounds(left() + W - 56, limitY() - 2, 50, 12).build());
	}

	private static final int LINEAGE_ROW = 44;

	private int lineageY() {
		return top() + 52;
	}

	private int techniquesY() {
		return top() + 42;
	}

	private static final int TECH_ROW = 24;

	private void techniqueAction(int technique) {
		ZFighterTraining.Data data = ZFighterTraining.of(this.minecraft.player);
		send(data.knows(technique) ? ZFighterNet.EQUIP_TECHNIQUE : ZFighterNet.BUY_TECHNIQUE, technique);
	}

	private static void send(int action, int index) {
		ClientPlayNetworking.send(new ZFighterNet.Train(action, index));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
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
		graphics.fill(x, y, x + W, y + height(), COLOR_PANEL);
		graphics.fill(x, y, x + W, y + 2, COLOR_ACCENT);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 6, COLOR_ACCENT);
		graphics.centeredText(this.font, Component.translatable("screen.jaymospowers.zfighter.tp", data.tp()), this.width / 2, y + 18, COLOR_TEXT);
		boolean picking = ZFighterLineage.of(player) == ZFighterLineage.NONE;
		for (Button button : lineageButtons) {
			button.visible = picking;
		}
		if (picking) {
			// first, who he is: the lineage is picked once
			tabButton.visible = false;
			for (List<Button> buttons : List.of(statButtons, formButtons, techniqueButtons)) {
				for (Button button : buttons) {
					button.visible = false;
				}
			}
			limitButton.visible = false;
			graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter.lineage"), x + 8, lineageY() - 16, COLOR_GOLD);
			for (int i = ZFighterLineage.EARTHLING; i < ZFighterLineage.LINEAGES; i++) {
				int ry = lineageY() + (i - 1) * LINEAGE_ROW;
				String key = "lineage.jaymospowers.zfighter." + ZFighterLineage.KEYS[i];
				graphics.text(this.font, Component.translatable(key), x + 8, ry, 0xFF000000 | ZFighterLineage.color(i, 1));
				int ly = ry + 11;
				for (var line : this.font.split(Component.translatable(key + ".desc"), W - 76)) {
					graphics.text(this.font, line, x + 14, ly, COLOR_DIM);
					ly += 9;
				}
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		tabButton.visible = true;
		tabButton.setMessage(Component.translatable(techniquesPage ? "screen.jaymospowers.zfighter.tab_training" : "screen.jaymospowers.zfighter.tab_techniques"));
		for (Button button : statButtons) {
			button.visible = !techniquesPage;
		}
		limitButton.visible = !techniquesPage;
		for (Button button : techniqueButtons) {
			button.visible = techniquesPage;
		}
		if (techniquesPage) {
			for (Button button : formButtons) {
				button.visible = false;
			}
			techniques(graphics, player, data, creative, x);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}

		// stats
		int cost = ZFighterTraining.statCost(data);
		graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter.stats", cost), x + 8, statsY() - 12, COLOR_GOLD);
		for (int i = 0; i < ZFighterTraining.STATS; i++) {
			int level = data.stat(i);
			int ry = statsY() + i * ROW;
			graphics.text(this.font, Component.translatable("stat.jaymospowers.zfighter." + STAT_KEYS[i]), x + 8, ry, COLOR_TEXT);
			graphics.text(this.font, level + "/" + ZFighterTraining.STAT_CAP, x + 82, ry, COLOR_DIM);
			graphics.text(this.font, Component.translatable("stat.jaymospowers.zfighter." + STAT_KEYS[i] + ".effect", effect(i, level)), x + 122, ry,
				COLOR_DIM);
			statButtons.get(i).active = level < ZFighterTraining.STAT_CAP && (creative || data.tp() >= cost);
		}

		// forms
		graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter.forms"), x + 8, formsY() - 12, COLOR_GOLD);
		int open = ZFighterTraining.formsOpen(player);
		for (int form = 1; form <= ZFighterTraining.FORMS; form++) {
			int ry = formsY() + (form - 1) * ROW * 2;
			int mastery = data.mastery(form);
			graphics.text(this.font, Component.translatable(ZFighterLineage.formKey(player, form)), x + 8, ry, form <= open ? COLOR_GOLD : COLOR_DIM);
			if (form <= open) {
				graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter.mastery",
					String.format(Locale.ROOT, "%.1f", mastery / 100.0)), x + 82, ry, COLOR_TEXT);
				int bar = 120;
				graphics.fill(x + 82, ry + 10, x + 82 + bar, ry + 12, 0xFF303030);
				graphics.fill(x + 82, ry + 10, x + 82 + bar * mastery / ZFighterTraining.MASTERY_MAX, ry + 12, COLOR_GOLD);
			}
			if (form > open) {
				Component need = form == 1 ? Component.translatable("screen.jaymospowers.zfighter.super_how")
					: Component.translatable("screen.jaymospowers.zfighter.form_needs", ZFighterTraining.FORM_TP[form],
						ZFighterTraining.NEXT_FORM_MASTERY / 100, Component.translatable(ZFighterLineage.formKey(player, form - 1)),
						ZFighterTraining.FORM_HARDSHIP[form], ZFighterPower.hardship(player));
				var lines = this.font.split(need, W - 82 - (form >= 2 ? 62 : 8));
				for (int l = 0; l < Math.min(2, lines.size()); l++) {
					graphics.text(this.font, lines.get(l), x + 82, ry + l * 10, COLOR_DIM);
				}
			}
			if (form >= 2) {
				Button button = formButtons.get(form - 2);
				button.visible = form > open;
				button.active = form == open + 1 && (ZFighterTraining.formBlocker(player, form) == null || creative && open >= 1);
			}
		}

		// Limit Break
		int ly = limitY();
		int limit = data.limit();
		graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter.limit", limit), x + 8, ly, 0xFFFF6A50);
		boolean more = limit + 1 < ZFighterTraining.LIMIT_TP.length;
		if (more) {
			graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter.limit_next", limit + 1,
				ZFighterTraining.limitCost(player, limit + 1)), x + 120, ly, COLOR_DIM);
		}
		graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter.limit_how", ZFighterInputClient.keyLabel(this.minecraft)),
			x + 8, ly + 12, COLOR_DIM);
		limitButton.visible = more;
		limitButton.active = more && (creative || data.tp() >= ZFighterTraining.limitCost(player, limit + 1));

		// how TP comes
		int fy = y + height() - 22;
		for (var line : this.font.split(Component.translatable("screen.jaymospowers.zfighter.earn"), W - 16)) {
			graphics.text(this.font, line, x + 8, fy, COLOR_DIM);
			fy += 9;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The techniques page: each one, what it does, its price or the key it's on. */
	private void techniques(GuiGraphicsExtractor graphics, LocalPlayer player, ZFighterTraining.Data data, boolean creative, int x) {
		for (int i = 0; i < ZFighterTraining.TECHNIQUES; i++) {
			int ry = techniquesY() + i * TECH_ROW;
			String key = ZFighterTraining.TECHNIQUE_KEYS[i];
			boolean known = data.knows(i);
			boolean spirit = i == ZFighterTraining.SPIRIT_BOMB;
			boolean equipped = !spirit && (ZFighterTraining.charged(i) ? data.charged() == i : data.quick() == i);
			String slot = spirit ? "C" : ZFighterTraining.charged(i) ? "V" : "G";
			graphics.text(this.font, Component.translatable(ZFighterLineage.techniqueKey(player, i)), x + 8, ry, known ? COLOR_GOLD : COLOR_TEXT);
			graphics.text(this.font, "[" + slot + "]", x + 130, ry, COLOR_DIM);
			Component status;
			if (equipped) {
				status = Component.translatable("screen.jaymospowers.zfighter.on_key", slot);
			} else if (known) {
				status = Component.translatable(spirit ? "screen.jaymospowers.zfighter.spirit_known" : "screen.jaymospowers.zfighter.known");
			} else {
				status = Component.translatable("screen.jaymospowers.zfighter.tp_cost", ZFighterTraining.TECHNIQUE_TP[i]);
			}
			graphics.text(this.font, status, x + 160, ry, COLOR_DIM);
			var lines = this.font.split(Component.translatable(ZFighterLineage.techniqueKey(player, i) + ".desc"), W - 70);
			if (!lines.isEmpty()) {
				graphics.text(this.font, lines.get(0), x + 8, ry + 10, COLOR_DIM);
			}
			Button button = techniqueButtons.get(i);
			if (!known) {
				String blocker = ZFighterTraining.techniqueBlocker(player, i);
				button.setMessage(Component.translatable("screen.jaymospowers.zfighter.learn"));
				button.active = blocker == null || creative && "screen.jaymospowers.zfighter.need_tp".equals(blocker);
				if (blocker != null && !button.active && button.isHovered()) {
					graphics.setTooltipForNextFrame(Component.translatable(blocker), button.getX(), button.getY());
				}
			} else {
				button.setMessage(Component.translatable("screen.jaymospowers.zfighter.equip"));
				button.visible = !spirit && !equipped;
				button.active = true;
			}
		}
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
