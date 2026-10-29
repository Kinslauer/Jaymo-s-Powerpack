package com.jaymospowers.client.power.web;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteMind;
import com.jaymospowers.power.web.WebSymbioteState;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * (0.17.150) The symbiote's Status page, reached from the X radial's Mind chip (Andy: "Strain, stage name, stress, mood
 * and any request, in one place | Add but in another menu within the X radial"). One panel: its strain, the bond and the
 * stage it's at, trust, mood, stress, satiety and any request with the time left; and under them the standing orders
 * ({@link WebSymbioteMind}): Protect Me, Hunt, Hide, or At ease. Whether it obeys an order is a roll on its trust
 * (server side); the lit button is the order it holds. Everything is read from the synced state and mind.
 */
final class WebSymbioteStatusScreen extends Screen {
	private static final int W = 250, H = 196;
	private static final int ACCENT = 0xFF8A5AB0;
	private static final int ROW_H = 13;
	private static final int BAR_W = 70, BAR_H = 4;
	private static final int BUTTON_W = 56, BUTTON_H = 14, BUTTON_GAP = 5;
	private static final int[] ORDERS = {WebSymbioteMind.PROTECT, WebSymbioteMind.HUNT, WebSymbioteMind.HIDE, WebSymbioteMind.ORDER_NONE};

	private int hovered = -1;

	WebSymbioteStatusScreen() {
		super(Component.translatable("screen.jaymospowers.web_symbiote_status"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		// no blur: the world stays in view
	}

	@Override
	public void tick() {
		LocalPlayer player = minecraft.player;
		if (player == null || !WebSymbiote.isBonded(player) || !player.isAlive()) {
			onClose();
		}
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return Math.max(6, (height - H) / 2);
	}

	private int buttonX(int i) {
		int total = ORDERS.length * BUTTON_W + (ORDERS.length - 1) * BUTTON_GAP;
		return left() + (W - total) / 2 + i * (BUTTON_W + BUTTON_GAP);
	}

	private int buttonY() {
		return top() + 34 + ROW_H * 8 + 18;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		for (int i = 0; i < ORDERS.length; i++) {
			if (UiStyle.over((int)event.x(), (int)event.y(), buttonX(i), buttonY(), BUTTON_W, BUTTON_H)) {
				ClientPlayNetworking.send(new WebSymbioteMind.Order(ORDERS[i]));
				LocalPlayer player = minecraft.player;
				if (player != null) {
					player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS,
						0.4F, 1.2F, false);
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		WebSymbioteMind.Mind mind = WebSymbioteMind.mind(player);
		WebSymbioteMind.Mood mood = mind.moodNow();
		long now = player.level().getGameTime();
		int x = left();
		int y = top();
		int h = H;
		UiStyle.panel(graphics, x, y, W, h, ACCENT);
		UiStyle.header(graphics, font, x, y, W, null, title,
			Component.translatable("gui.jaymospowers.web_symbiote.status.strain." + state.kind().name().toLowerCase(Locale.ROOT))
				.append(" - ").append(Component.translatable("gui.jaymospowers.web_symbiote.stage." + WebSymbioteMind.stageName(state.bond()))),
			ACCENT, 0);
		int rowY = y + UiStyle.HEADER;
		int labelX = x + 10;
		int valueX = x + 78;
		int row = 0;

		row(graphics, row++, rowY, labelX, "strain", Component.translatable("gui.jaymospowers.web_symbiote.status.strain."
			+ state.kind().name().toLowerCase(Locale.ROOT)), UiStyle.TEXT, valueX);
		bar(graphics, row++, rowY, labelX, "bond", Component.translatable("gui.jaymospowers.web_symbiote.status.bond_value", state.bond(), WebSymbiote.BOND_MAX),
			state.bond() / (float)WebSymbiote.BOND_MAX, ACCENT, valueX);
		row(graphics, row++, rowY, labelX, "stage", Component.translatable("gui.jaymospowers.web_symbiote.stage." + WebSymbioteMind.stageName(state.bond())),
			UiStyle.TEXT, valueX);
		bar(graphics, row++, rowY, labelX, "trust", Component.translatable("gui.jaymospowers.web_symbiote.status.bond_value", mind.trust(), WebSymbioteMind.TRUST_MAX),
			mind.trust() / (float)WebSymbioteMind.TRUST_MAX, 0xFF6AB08A, valueX);
		row(graphics, row++, rowY, labelX, "mood", Component.translatable("gui.jaymospowers.web_symbiote.mood." + mood.key()), WebSymbioteMindUi.TEXT[mood.ordinal()], valueX);
		bar(graphics, row++, rowY, labelX, "stress", Component.translatable("gui.jaymospowers.web_symbiote.status.bond_value", state.stress(), WebSymbiote.STRESS_MAX),
			state.stress() / (float)WebSymbiote.STRESS_MAX, state.stress() >= WebSymbioteMind.COILED_STRESS ? 0xFFB02030 : 0xFFB09A5A, valueX);
		bar(graphics, row++, rowY, labelX, "satiety", Component.translatable("gui.jaymospowers.web_symbiote.status.bond_value", state.satiety(), WebSymbiote.SATIETY_MAX),
			state.satiety() / (float)WebSymbiote.SATIETY_MAX, state.satiety() <= WebSymbiote.STARVING ? 0xFFB02030 : ACCENT, valueX);
		// the request, with the time it has left
		Component request;
		int requestColor = UiStyle.TEXT;
		if (mind.req() == WebSymbioteMind.REQ_NONE) {
			request = Component.translatable("gui.jaymospowers.web_symbiote.status.request_none");
			requestColor = UiStyle.DIM;
		} else {
			long left = Math.max(0L, mind.reqEnd() - now);
			String time = String.format(Locale.ROOT, "%d:%02d", left / 1200L, left / 20L % 60L);
			request = Component.translatable("gui.jaymospowers.web_symbiote.status.request_left",
				Component.translatable("gui.jaymospowers.web_symbiote.request." + WebSymbioteMind.requestName(mind.req())), time);
			if (left < 1200L) {
				requestColor = 0xFFFF9A6A;
			}
		}
		row(graphics, row, rowY, labelX, "request", request, requestColor, valueX);

		// the standing orders
		int orderY = y + UiStyle.HEADER + ROW_H * 8 + 2;
		UiStyle.rule(graphics, x + 6, x + W - 6, orderY);
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.web_symbiote.status.orders"), x + 10, orderY + 4, W - 20, ACCENT);
		hovered = -1;
		for (int i = 0; i < ORDERS.length; i++) {
			int bx = buttonX(i);
			int by = buttonY();
			boolean over = UiStyle.over(mouseX, mouseY, bx, by, BUTTON_W, BUTTON_H);
			boolean held = mind.order() == ORDERS[i];
			if (over) {
				hovered = i;
			}
			graphics.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, over ? UiStyle.ROW_HOVER : held ? UiStyle.ROW_PICKED : UiStyle.ROW);
			graphics.outline(bx, by, BUTTON_W, BUTTON_H, held ? ACCENT : 0x40FFFFFF);
			Component label = Component.translatable("gui.jaymospowers.web_symbiote.order." + WebSymbioteMind.orderName(ORDERS[i]));
			graphics.text(font, label, bx + BUTTON_W / 2 - font.width(label) / 2, by + 3, held ? UiStyle.TEXT : UiStyle.DIM);
			if (held) {
				graphics.fill(bx + 3, by + BUTTON_H - 3, bx + BUTTON_W - 3, by + BUTTON_H - 1, ACCENT);
			}
		}
		// what the hovered button (or, failing that, the mood) means
		Component tip = hovered >= 0
			? Component.translatable("gui.jaymospowers.web_symbiote.order." + WebSymbioteMind.orderName(ORDERS[hovered]) + ".tip")
			: Component.translatable("gui.jaymospowers.web_symbiote.mood." + mood.key() + ".tip");
		UiStyle.line(graphics, font, tip, x + 10, buttonY() + BUTTON_H + 5, W - 20, UiStyle.HINT);
		UiStyle.lineRight(graphics, font, Component.translatable("gui.jaymospowers.web_symbiote.status.close"), x + W - 8, y + h - 12, 60, UiStyle.HINT);
	}

	private void row(GuiGraphicsExtractor graphics, int index, int rowY, int labelX, String label, Component value, int color, int valueX) {
		int y = rowY + index * ROW_H;
		UiStyle.row(graphics, left() + 4, y - 1, left() + W - 4, y + ROW_H - 2, index, false);
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.web_symbiote.status." + label), labelX, y + 1, 64, UiStyle.DIM);
		UiStyle.line(graphics, font, value, valueX, y + 1, W - (valueX - left()) - 10, color);
	}

	private void bar(GuiGraphicsExtractor graphics, int index, int rowY, int labelX, String label, Component value, float share, int color, int valueX) {
		row(graphics, index, rowY, labelX, label, value, UiStyle.TEXT, valueX);
		int y = rowY + index * ROW_H;
		UiStyle.bar(graphics, left() + W - 10 - BAR_W, y + 4, BAR_W, BAR_H, Mth.clamp(share, 0.0F, 1.0F), color);
	}
}
