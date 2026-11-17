package com.jaymospowers.client.trials;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.client.menu.MenuTabs;
import com.jaymospowers.client.ui.FlatButton;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.trials.PowerTrials;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (Awakening pick) The trials page in the H menu ({@link PowerTrials}): the three open trials with their progress, a pin
 * and a swap button each, the swaps left and how far up the ladder you are; and the pinned trial on the HUD (top-left rail).
 */
public class TrialsScreen extends Screen {
	private static final int W = 300, ROW = 40, H = UiStyle.HEADER + PowerTrials.OPEN * ROW + 24;
	/** The HUD line's rail row (under the move hints), its padding and line height. */
	static final int HUD_ROW = 45, PAD = 3, LINE = 10;

	public TrialsScreen() {
		super(Component.translatable("menu.jaymospowers.tab.trials"));
	}

	public static void register() {
		MenuTabs.register("trials", 12, () -> new ItemStack(Items.TARGET), Component.translatable("menu.jaymospowers.tab.trials"),
			() -> Minecraft.getInstance().gui.setScreen(new TrialsScreen()), s -> s instanceof TrialsScreen, s -> ((TrialsScreen)s).corner(),
			() -> Minecraft.getInstance().player != null && Powers.of(Minecraft.getInstance().player) != null);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("trial_pin"), TrialsScreen::hud);
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(MenuTabs.ROOM, (this.height - H) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.clearWidgets();
		LocalPlayer player = this.minecraft.player;
		PowerTrials.State s = player == null ? null : PowerTrials.state(player);
		if (s == null) {
			return;
		}
		int x = left(), y = top() + UiStyle.HEADER;
		for (int i = 0; i < PowerTrials.OPEN; i++) {
			if (s.open[i] < 0) {
				continue;
			}
			int slot = i;
			addRenderableWidget(FlatButton.builder(Component.translatable(s.pinned == i ? "screen.jaymospowers.trials.unpin" : "screen.jaymospowers.trials.pin"),
				b -> act(0, slot)).bounds(x + W - 112, y + i * ROW + 20, 52, UiStyle.BUTTON_H).build());
			FlatButton swap = FlatButton.builder(Component.translatable("screen.jaymospowers.trials.swap"), b -> act(1, slot))
				.bounds(x + W - 56, y + i * ROW + 20, 50, UiStyle.BUTTON_H).build();
			swap.active = s.swaps > 0;
			addRenderableWidget(swap);
		}
	}

	private void act(int action, int slot) {
		// the answer comes back as the synced ladder, and the buttons follow it (tick)
		ClientPlayNetworking.send(new PowerTrials.Act(action, slot));
	}

	@Override
	public void tick() {
		// the ladder is synced from the server: keep the buttons in step with it
		if (this.minecraft.player != null && this.minecraft.player.tickCount % 10 == 0) {
			this.init();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		Power power = player == null ? null : Powers.of(player);
		int x = left(), y = top();
		int accent = power == null ? UiStyle.ACCENT : 0xFF000000 | power.color();
		UiStyle.panel(graphics, x, y, W, H, accent);
		PowerTrials.State s = player == null ? null : PowerTrials.state(player);
		Component sub = s == null ? Component.translatable("screen.jaymospowers.trials.none")
			: Component.translatable("screen.jaymospowers.trials.sub", s.doneCount(), PowerTrials.LADDER.size(), s.swaps, PowerTrials.MOST_SWAPS);
		UiStyle.header(graphics, this.font, x, y, W, new ItemStack(Items.TARGET), this.title, sub, accent, 0);
		if (s != null && power != null) {
			int open = 0;
			for (int i = 0; i < PowerTrials.OPEN; i++) {
				int index = s.open[i];
				if (index < 0) {
					continue;
				}
				open++;
				int ry = y + UiStyle.HEADER + i * ROW;
				PowerTrials.Trial trial = PowerTrials.LADDER.get(index);
				UiStyle.row(graphics, x + 4, ry, x + W - 4, ry + ROW - 2, i, false);
				UiStyle.line(graphics, this.font, PowerTrials.describe(power, index), x + 8, ry + 4, W - 16, s.pinned == i ? UiStyle.GOLD : 0xFFE8E0D0);
				UiStyle.bar(graphics, x + 8, ry + 15, W - 128, 3, s.progress[i] / (float)trial.need(), accent);
				graphics.text(this.font, Component.literal(s.progress[i] + " / " + trial.need()), x + 8, ry + 24, 0xFF9A9080);
			}
			if (open == 0) {
				UiStyle.line(graphics, this.font, Component.translatable(s.doneCount() >= PowerTrials.LADDER.size() ? "screen.jaymospowers.trials.all"
					: "screen.jaymospowers.trials.waiting"), x + 8, y + UiStyle.HEADER + 4, W - 16, 0xFFE8E0D0);
			}
		}
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.trials.how").withStyle(net.minecraft.ChatFormatting.ITALIC),
			x + 8, y + H - 14, W - 16, 0xFF9A9080);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The pinned trial, small, on the top-left rail. */
	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null) {
			return;
		}
		Power power = Powers.of(player);
		PowerTrials.State s = PowerTrials.state(player);
		if (power == null || s == null || s.pinned < 0 || s.open[s.pinned] < 0) {
			return;
		}
		int index = s.open[s.pinned];
		PowerTrials.Trial trial = PowerTrials.LADDER.get(index);
		Component line = Component.empty().append(PowerTrials.describe(power, index).copy().withColor(HudStyle.TEXT & 0xFFFFFF))
			.append(Component.literal("  " + s.progress[s.pinned] + "/" + trial.need()).withColor(HudStyle.GOLD & 0xFFFFFF));
		int w = minecraft.font.width(line) + PAD * 2, h = LINE + PAD * 2 - 1;
		int[] at = HudZones.place(HudZones.RAIL, "jaymospowers:trial_pin", HUD_ROW, w, h);
		if (at == null) {
			return;
		}
		graphics.fill(at[0], at[1], at[0] + w, at[1] + h, HudStyle.PLATE_SOFT & 0xFFFFFF | 0x60 << 24);
		graphics.text(minecraft.font, line, at[0] + PAD, at[1] + PAD, 0xFFFFFFFF, true);
	}
}
