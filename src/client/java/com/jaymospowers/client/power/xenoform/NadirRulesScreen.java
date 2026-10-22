package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.xenoform.nadir.NadirNet;
import com.jaymospowers.power.xenoform.nadir.NadirRules;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Nadir's Rules screen (the Command, B): {@link NadirRules#SLOTS} rules, each a sentence of three words you click
 * through (WHO, WHAT, WHEN; sneak-click goes back) and a switch. Up to {@link NadirRules#MAX_ON} hold at once. Every
 * change goes straight to the server, which checks it ({@link NadirRules#clean}).
 */
public class NadirRulesScreen extends Screen {
	private static final int MAX_W = 340;
	private static final int ROW = 22;
	private static final int DETAIL_LINES = 4;
	private static final int ACCENT = 0xFF7A4FD0;
	private static final String[] NUMERALS = {"I", "II", "III", "IV", "V"};

	private final List<Integer> rules;

	public NadirRulesScreen(List<Integer> rules) {
		super(Component.translatable("screen.jaymospowers.nadir_rules"));
		this.rules = new ArrayList<>(NadirRules.clean(rules));
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(NadirNet.OpenRules.TYPE,
			(payload, context) -> context.client().gui.setScreen(new NadirRulesScreen(payload.rules())));
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int panelH() {
		return UiStyle.HEADER + NadirRules.SLOTS * ROW + 6 + UiStyle.detailHeight(DETAIL_LINES);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int rowY(int i) {
		return top() + UiStyle.HEADER + i * ROW;
	}

	/** The x of each of a row's four buttons, and their widths (label, who, what, when, on). */
	private int[] columns() {
		int w = panelW();
		int label = 22, on = 34, gap = 4;
		int rest = w - 12 - label - on - gap * 4;
		int who = rest * 28 / 100, what = rest * 38 / 100, when = rest - who - what;
		int x = left() + 6 + label + gap;
		return new int[] {x, who, x + who + gap, what, x + who + what + gap * 2, when, x + who + what + when + gap * 3, on};
	}

	@Override
	protected void init() {
		int[] c = columns();
		for (int i = 0; i < NadirRules.SLOTS; i++) {
			int slot = i;
			int y = rowY(i) + 3;
			addRenderableWidget(Button.builder(word(slot, 0), b -> {
				cycle(slot, 0, NadirRules.WHO.length);
				b.setMessage(word(slot, 0));
			}).bounds(c[0], y, c[1], 16).build());
			addRenderableWidget(Button.builder(word(slot, 1), b -> {
				cycle(slot, 1, NadirRules.WHAT.length);
				b.setMessage(word(slot, 1));
			}).bounds(c[2], y, c[3], 16).build());
			addRenderableWidget(Button.builder(word(slot, 2), b -> {
				cycle(slot, 2, NadirRules.WHEN.length);
				b.setMessage(word(slot, 2));
			}).bounds(c[4], y, c[5], 16).build());
			addRenderableWidget(Button.builder(onOff(slot), b -> {
				toggle(slot);
				this.rebuildWidgets();
			}).bounds(c[6], y, c[7], 16).build());
		}
	}

	private Component word(int slot, int field) {
		int v = rules.get(slot * NadirRules.FIELDS + field);
		String part = field == 0 ? "who." + NadirRules.WHO[v] : field == 1 ? "what." + NadirRules.WHAT[v] : "when." + NadirRules.WHEN[v];
		return Component.translatable("screen.jaymospowers.nadir_rules." + part);
	}

	private Component onOff(int slot) {
		boolean on = rules.get(slot * NadirRules.FIELDS + 3) == 1;
		return Component.translatable(on ? "screen.jaymospowers.nadir_rules.on" : "screen.jaymospowers.nadir_rules.off")
			.withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY);
	}

	private void cycle(int slot, int field, int count) {
		int i = slot * NadirRules.FIELDS + field;
		int by = Minecraft.getInstance().hasShiftDown() ? count - 1 : 1;
		rules.set(i, (rules.get(i) + by) % count);
		send();
	}

	/** Switches the rule; switching one on past the limit switches off the oldest other. */
	private void toggle(int slot) {
		int i = slot * NadirRules.FIELDS + 3;
		boolean on = rules.get(i) == 1;
		if (!on) {
			int count = 0;
			for (int s = 0; s < NadirRules.SLOTS; s++) {
				count += rules.get(s * NadirRules.FIELDS + 3);
			}
			for (int s = 0; s < NadirRules.SLOTS && count >= NadirRules.MAX_ON; s++) {
				if (s != slot && rules.get(s * NadirRules.FIELDS + 3) == 1) {
					rules.set(s * NadirRules.FIELDS + 3, 0);
					count--;
				}
			}
		}
		rules.set(i, on ? 0 : 1);
		send();
	}

	private void send() {
		ClientPlayNetworking.send(new NadirNet.SetRules(List.copyOf(rules)));
	}

	/** The rule as a sentence: "What I hit burns while sprinting." */
	private Component sentence(int slot) {
		return Component.translatable("screen.jaymospowers.nadir_rules.sentence", word(slot, 0), word(slot, 1), word(slot, 2));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		int w = panelW();
		UiStyle.panel(graphics, x, y, w, panelH(), ACCENT);
		UiStyle.header(graphics, this.font, x, y, w, new ItemStack(Items.ENDER_EYE), this.title,
			Component.translatable("screen.jaymospowers.nadir_rules.sub", NadirRules.MAX_ON), ACCENT, 0);
		int hovered = -1;
		for (int i = 0; i < NadirRules.SLOTS; i++) {
			int ry = rowY(i);
			boolean hover = UiStyle.over(mouseX, mouseY, x + 6, ry, w - 12, ROW - 2);
			if (hover) {
				hovered = i;
			}
			UiStyle.row(graphics, x + 6, ry, x + w - 6, ry + ROW - 2, i, hover);
			boolean on = rules.get(i * NadirRules.FIELDS + 3) == 1;
			UiStyle.line(graphics, this.font, Component.literal(NUMERALS[i]), x + 10, ry + 7, 18, on ? ACCENT : UiStyle.DIM);
		}
		int dy = y + panelH() - UiStyle.detailHeight(DETAIL_LINES);
		if (hovered >= 0) {
			UiStyle.detail(graphics, this.font, x, dy, w, DETAIL_LINES, sentence(hovered), ACCENT, List.of(Component.translatable(
				"screen.jaymospowers.nadir_rules.what." + NadirRules.WHAT[rules.get(hovered * NadirRules.FIELDS + 1)] + ".desc")));
		} else {
			UiStyle.detail(graphics, this.font, x, dy, w, DETAIL_LINES, null, ACCENT,
				List.of(Component.translatable("screen.jaymospowers.nadir_rules.help")));
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
