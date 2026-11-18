package com.jaymospowers.client.power.rules;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.ui.FlatButton;
import com.jaymospowers.power.rules.Rules;
import com.jaymospowers.power.rules.RulesPower;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Lawgiver's rulebook ({@link RulesPower}, G): up to five rules, each a sentence of four parts. Click a part to turn it
 * to the next choice (right click: the one before); the line under each says it in full and what it costs in ink. Strength
 * stops at what the Lawgiver can hold yet. Write sends it to the server.
 */
public class RuleBookScreen extends Screen {
	private static final int ACCENT = 0xFFE8C66A;
	private static final int TEXT = 0xFFE8E0D0, DIM = 0xFF9A9080;
	private static final int ROW = 34, W = 360;
	private static final int[] COL_X = {6, 116, 236, 288}, COL_W = {108, 118, 50, 50};

	private final List<int[]> rules = new ArrayList<>();
	private final int strongest;

	public RuleBookScreen(Player player) {
		super(Component.translatable("screen.jaymospowers.rules.title"));
		List<Integer> written = player.getAttached(Rules.WRITTEN);
		for (int r : written == null || written.isEmpty() ? Rules.first() : written) {
			this.rules.add(new int[] {Rules.who(r), Rules.what(r), Rules.strength(r), Rules.length(r)});
		}
		this.strongest = Rules.strongest(player);
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Rules.OpenBook.TYPE, (payload, ctx) -> {
			if (ctx.client().player != null) {
				ctx.client().gui.setScreen(new RuleBookScreen(ctx.client().player));
			}
		});
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int panelH() {
		return UiStyle.HEADER + Rules.MOST * ROW + 30;
	}

	@Override
	protected void init() {
		this.clearWidgets();
		int x = left(), y = top() + UiStyle.HEADER;
		for (int i = 0; i < this.rules.size(); i++) {
			int[] rule = this.rules.get(i);
			int row = i;
			for (int part = 0; part < 4; part++) {
				int p = part;
				addRenderableWidget(FlatButton.builder(label(rule, part), b -> turn(row, p, 1))
					.bounds(x + COL_X[part], y + i * ROW, COL_W[part], UiStyle.BUTTON_H).build());
			}
			addRenderableWidget(FlatButton.builder(Component.literal("x"), b -> {
				this.rules.remove(row);
				this.init();
			}).bounds(x + W - 22, y + i * ROW, 16, UiStyle.BUTTON_H).build());
		}
		int bottom = top() + panelH() - 22;
		if (this.rules.size() < Rules.MOST) {
			addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymospowers.rules.add"), b -> {
				this.rules.add(new int[] {Rules.SELF, Rules.MEND, 1, 1});
				this.init();
			}).bounds(x + 6, bottom, 100, UiStyle.BUTTON_H).build());
		}
		addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymospowers.rules.write"), b -> {
			List<Integer> packed = new ArrayList<>();
			for (int[] r : this.rules) {
				packed.add(Rules.pack(r[0], r[1], r[2], r[3]));
			}
			ClientPlayNetworking.send(new Rules.Write(packed));
			this.onClose();
		}).bounds(x + W - 106, bottom, 100, UiStyle.BUTTON_H).build());
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 1) {
			// right click: the choice before
			int x = left(), y = top() + UiStyle.HEADER;
			for (int i = 0; i < this.rules.size(); i++) {
				for (int part = 0; part < 4; part++) {
					if (event.x() >= x + COL_X[part] && event.x() < x + COL_X[part] + COL_W[part] && event.y() >= y + i * ROW
						&& event.y() < y + i * ROW + UiStyle.BUTTON_H) {
						turn(i, part, -1);
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void turn(int row, int part, int by) {
		int[] r = this.rules.get(row);
		int count = switch (part) {
			case 0 -> Rules.WHO_COUNT;
			case 1 -> Rules.WHAT_COUNT;
			case 2 -> this.strongest;
			default -> Rules.SECONDS.length;
		};
		if (part == 2) {
			r[2] = Math.floorMod(r[2] - 1 + by, count) + 1;
		} else {
			r[part] = Math.floorMod(r[part] + by, count);
		}
		this.init();
	}

	private static Component label(int[] r, int part) {
		return switch (part) {
			case 0 -> Component.translatable("rule.jaymospowers.who." + Rules.WHO_NAMES[r[0]]);
			case 1 -> Component.translatable("rule.jaymospowers.what." + Rules.WHAT_NAMES[r[1]] + (r[0] == Rules.SELF ? ".self" : ""));
			case 2 -> Component.translatable("screen.jaymospowers.rules.strength", r[2]);
			default -> Component.translatable("screen.jaymospowers.rules.seconds", Rules.SECONDS[r[3]]);
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		UiStyle.panel(graphics, x, y, W, panelH(), ACCENT);
		UiStyle.header(graphics, this.font, x, y, W, null, this.title,
			Component.translatable("screen.jaymospowers.rules.sub", Rules.atOnce(Minecraft.getInstance().player), this.strongest), ACCENT, 0);
		for (int i = 0; i < this.rules.size(); i++) {
			int[] r = this.rules.get(i);
			int packed = Rules.pack(r[0], r[1], r[2], r[3]);
			Component line = Component.translatable("screen.jaymospowers.rules.line", i + 1, Math.round(Rules.cost(packed)));
			graphics.text(this.font, line, x + 8, y + UiStyle.HEADER + i * ROW + UiStyle.BUTTON_H + 3, DIM);
		}
		if (this.rules.isEmpty()) {
			graphics.text(this.font, Component.translatable("screen.jaymospowers.rules.empty"), x + 8, y + UiStyle.HEADER + 4, TEXT);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
