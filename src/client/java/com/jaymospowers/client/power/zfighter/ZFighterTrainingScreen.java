package com.jaymospowers.client.power.zfighter;

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
	private Button limitButton;

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
			graphics.text(this.font, Component.translatable("form.jaymospowers.zfighter." + form), x + 8, ry, form <= open ? COLOR_GOLD : COLOR_DIM);
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
						ZFighterTraining.NEXT_FORM_MASTERY / 100, Component.translatable("form.jaymospowers.zfighter." + (form - 1)),
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
				ZFighterTraining.LIMIT_TP[limit + 1]), x + 120, ly, COLOR_DIM);
		}
		graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter.limit_how", ZFighterInputClient.keyLabel(this.minecraft)),
			x + 8, ly + 12, COLOR_DIM);
		limitButton.visible = more;
		limitButton.active = more && (creative || data.tp() >= ZFighterTraining.LIMIT_TP[limit + 1]);

		// how TP comes
		int fy = y + height() - 22;
		for (var line : this.font.split(Component.translatable("screen.jaymospowers.zfighter.earn"), W - 16)) {
			graphics.text(this.font, line, x + 8, fy, COLOR_DIM);
			fy += 9;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
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
