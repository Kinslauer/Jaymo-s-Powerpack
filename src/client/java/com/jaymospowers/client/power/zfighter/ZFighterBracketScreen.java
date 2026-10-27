package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterBracketNet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * (0.17.58, Andy's NOEA picks) The tournament's bracket. Opens when you are entered in a tournament (and when the bracket
 * is drawn), and by {@code /zfighter bracket} (or {@code /tournament}) at any time, showing the tournament that is on or
 * the last one's final board. A bracket is drawn round by round with the winners going through, the bout that is on
 * lit, who is up next and the sign-up clock; a last-one-standing fight is a list of who is still in and who went out,
 * why, and to whom. The server sends the board whenever it changes ({@link ZFighterBracketNet}), so it stays up to date
 * while you watch. Escape closes it.
 */
public class ZFighterBracketScreen extends Screen {
	private static final int ACCENT = 0xFFFFC840;
	private static final int WON = 0xFF80E080, LOST = 0xFF707080, LIVE = 0x60FFC840, STILL_IN = 0xFF80E080, CHAMPION = 0xFFFFD860, FOE = 0xFF9FC8FF;
	private static final int BOX_H = 24, ROW_H = 11, GAP = 14, LIST_ROW = 14;

	private static ZFighterBracketNet.Board board = ZFighterBracketNet.Board.NONE;
	/** When the board last arrived (ms), so a clock on it can run down between packets. */
	private static long received;

	public ZFighterBracketScreen() {
		super(Component.translatable("screen.jaymospowers.zfighter_bracket"));
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterBracketNet.Show.TYPE, (payload, ctx) -> {
			board = payload.board();
			received = System.currentTimeMillis();
			if (payload.open() && !(ctx.client().gui.screen() instanceof ZFighterBracketScreen)) {
				ctx.client().gui.setScreen(new ZFighterBracketScreen());
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(this.width - 16, 400);
	}

	private int panelH() {
		return Math.min(this.height - 16, 252);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return (this.height - panelH()) / 2;
	}

	/** A name from the wire: a marked key is translated, nothing is "?". */
	private static String name(String wire) {
		if (wire.isEmpty()) {
			return "?";
		}
		return wire.startsWith(ZFighterBracketNet.KEY) ? Component.translatable(wire.substring(ZFighterBracketNet.KEY.length())).getString() : wire;
	}

	/** Seconds left on the board's clock now. */
	private static int seconds() {
		return Math.max(0, board.ticks() / 20 - (int)((System.currentTimeMillis() - received) / 1000L));
	}

	private static String clock(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		int w = panelW();
		int h = panelH();
		UiStyle.panel(graphics, x, y, w, h, ACCENT);
		ZFighterBracketNet.Board b = board;
		if (!b.any()) {
			UiStyle.header(graphics, this.font, x, y, w, null, this.title, null, ACCENT, 0);
			UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_bracket.none"), x + w / 2, y + h / 2 - 4, w - 16, UiStyle.DIM);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		Component title = Component.translatable(b.royale() ? "screen.jaymospowers.zfighter_bracket.title_royale" : "screen.jaymospowers.zfighter_bracket.title", b.kingdom());
		Component sub = switch (b.phase()) {
			case ZFighterBracketNet.SIGN_UP -> Component.translatable("screen.jaymospowers.zfighter_bracket.signup", clock(seconds()));
			case ZFighterBracketNet.OVER -> Component.translatable("screen.jaymospowers.zfighter_bracket.over", name(b.champion()));
			default -> b.royale() ? Component.translatable("screen.jaymospowers.zfighter_bracket.fighting", clock(seconds()), standing(b))
				: Component.translatable("screen.jaymospowers.zfighter_bracket.round", b.round() + 1, Math.max(1, b.rounds()));
		};
		UiStyle.header(graphics, this.font, x, y, w, null, title, sub, ACCENT, 0);
		int bodyTop = y + UiStyle.HEADER + 2;
		int bodyBottom = y + h - 24;
		if (b.royale()) {
			drawList(graphics, b, x + 8, bodyTop, w - 16, bodyBottom - bodyTop);
		} else if (b.matches().isEmpty()) {
			// sign-up for a bracket: who has entered
			drawList(graphics, b, x + 8, bodyTop, w - 16, bodyBottom - bodyTop);
		} else {
			drawBracket(graphics, b, x + 8, bodyTop, w - 16, bodyBottom - bodyTop);
		}
		UiStyle.rule(graphics, x + 6, x + w - 6, y + h - 22);
		drawFooter(graphics, b, x + 8, y + h - 18, w - 16);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private static int standing(ZFighterBracketNet.Board b) {
		int n = 0;
		for (ZFighterBracketNet.Fighter f : b.fighters()) {
			if (f.place() == 0) {
				n++;
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- the bracket

	private void drawBracket(GuiGraphicsExtractor graphics, ZFighterBracketNet.Board b, int x, int y, int w, int h) {
		int rounds = Math.max(1, b.rounds());
		int columnW = (w - GAP * (rounds - 1)) / rounds;
		List<List<ZFighterBracketNet.Match>> byRound = new ArrayList<>();
		for (int r = 0; r < rounds; r++) {
			byRound.add(new ArrayList<>());
		}
		for (ZFighterBracketNet.Match m : b.matches()) {
			if (m.round() >= 0 && m.round() < rounds) {
				byRound.get(m.round()).add(m);
			}
		}
		int first = Math.max(1, byRound.get(0).size());
		double slot = Math.min((double)h / first, BOX_H + 10.0);
		double[] lastMid = new double[0];
		for (int r = 0; r < rounds; r++) {
			List<ZFighterBracketNet.Match> ms = byRound.get(r);
			int cx = x + r * (columnW + GAP);
			UiStyle.lineCentred(graphics, this.font, Component.translatable(r == rounds - 1 ? "screen.jaymospowers.zfighter_bracket.final"
				: "screen.jaymospowers.zfighter_bracket.round_n", r + 1), cx + columnW / 2, y - 1, columnW, UiStyle.HINT);
			double[] mids = new double[ms.size()];
			for (int i = 0; i < ms.size(); i++) {
				ZFighterBracketNet.Match m = ms.get(i);
				double mid;
				if (r == 0) {
					mid = y + 10 + slot * (i + 0.5);
				} else {
					double a = lastMid.length > i * 2 ? lastMid[i * 2] : y + 10 + slot * (i * 2 + 0.5);
					double c = lastMid.length > i * 2 + 1 ? lastMid[i * 2 + 1] : a;
					mid = (a + c) / 2.0;
				}
				mids[i] = mid;
				drawMatch(graphics, m, cx, (int)Math.round(mid) - BOX_H / 2, columnW);
				if (r < rounds - 1) {
					graphics.fill(cx + columnW, (int)Math.round(mid), cx + columnW + GAP / 2, (int)Math.round(mid) + 1, UiStyle.ROW_HOVER);
				}
				if (r > 0 && lastMid.length > i * 2) {
					// the two bouts that fed this one meet at a line, which runs across to it
					int vx = cx - GAP / 2;
					int top = (int)Math.round(Math.min(lastMid[i * 2], lastMid.length > i * 2 + 1 ? lastMid[i * 2 + 1] : lastMid[i * 2]));
					int bottom = (int)Math.round(Math.max(lastMid[i * 2], lastMid.length > i * 2 + 1 ? lastMid[i * 2 + 1] : lastMid[i * 2]));
					graphics.fill(vx, top, vx + 1, bottom + 1, UiStyle.ROW_HOVER);
					graphics.fill(vx, (int)Math.round(mid), cx, (int)Math.round(mid) + 1, UiStyle.ROW_HOVER);
				}
			}
			lastMid = mids;
		}
	}

	private void drawMatch(GuiGraphicsExtractor graphics, ZFighterBracketNet.Match m, int x, int y, int w) {
		graphics.fill(x, y, x + w, y + BOX_H, m.live() ? LIVE : UiStyle.ROW);
		if (m.live()) {
			graphics.fill(x, y, x + 2, y + BOX_H, ACCENT);
		}
		drawSide(graphics, m.a(), m.aPlayer(), m.winner() == ZFighterBracketNet.FIRST, m.winner() == ZFighterBracketNet.SECOND, x + 4, y + 2, w - 6);
		drawSide(graphics, m.b(), m.bPlayer(), m.winner() == ZFighterBracketNet.SECOND, m.winner() == ZFighterBracketNet.FIRST, x + 4, y + 2 + ROW_H, w - 6);
	}

	private void drawSide(GuiGraphicsExtractor graphics, String wire, boolean player, boolean won, boolean lost, int x, int y, int w) {
		int color = wire.isEmpty() ? UiStyle.HINT : won ? WON : lost ? LOST : player ? UiStyle.TEXT : FOE;
		UiStyle.line(graphics, this.font, Component.literal((won ? "> " : "") + name(wire)), x, y, w, color);
	}

	// ---------------------------------------------------------------- the list

	private void drawList(GuiGraphicsExtractor graphics, ZFighterBracketNet.Board b, int x, int y, int w, int h) {
		List<ZFighterBracketNet.Fighter> fighters = b.fighters();
		if (fighters.isEmpty()) {
			UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_bracket.nobody"), x + w / 2, y + h / 2 - 4, w, UiStyle.DIM);
			return;
		}
		int columns = fighters.size() > h / LIST_ROW ? 2 : 1;
		int columnW = (w - 6 * (columns - 1)) / columns;
		int perColumn = Math.max(1, h / LIST_ROW);
		for (int i = 0; i < fighters.size() && i < perColumn * columns; i++) {
			ZFighterBracketNet.Fighter f = fighters.get(i);
			int cx = x + (i / perColumn) * (columnW + 6);
			int cy = y + (i % perColumn) * LIST_ROW;
			UiStyle.row(graphics, cx, cy, cx + columnW, cy + LIST_ROW - 1, i, false);
			boolean in = f.place() == 0;
			boolean winner = f.place() == 1 && b.phase() == ZFighterBracketNet.OVER;
			int color = winner ? CHAMPION : in ? (f.player() ? UiStyle.TEXT : FOE) : LOST;
			UiStyle.line(graphics, this.font, Component.literal(name(f.name())), cx + 14, cy + 3, columnW - 16, color);
			if (in) {
				graphics.fill(cx + 4, cy + 4, cx + 9, cy + 9, b.phase() == ZFighterBracketNet.SIGN_UP ? ACCENT : STILL_IN);
			} else {
				UiStyle.lineRight(graphics, this.font, outText(f), cx + columnW - 3, cy + 3, columnW / 2, UiStyle.DIM);
				UiStyle.line(graphics, this.font, Component.literal(winner ? "1" : "#" + f.place()), cx + 2, cy + 3, 12, winner ? CHAMPION : UiStyle.HINT);
			}
		}
	}

	private static Component outText(ZFighterBracketNet.Fighter f) {
		Component how = Component.translatable("screen.jaymospowers.zfighter_bracket.how." + f.how());
		if (f.how().isEmpty()) {
			return Component.empty();
		}
		return f.by().isEmpty() ? how : Component.translatable("screen.jaymospowers.zfighter_bracket.out_by", how, name(f.by()));
	}

	// ---------------------------------------------------------------- the footer

	private void drawFooter(GuiGraphicsExtractor graphics, ZFighterBracketNet.Board b, int x, int y, int w) {
		if (b.phase() == ZFighterBracketNet.OVER) {
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_bracket.champion", name(b.champion())), x, y, w, CHAMPION);
			return;
		}
		if (b.phase() == ZFighterBracketNet.SIGN_UP) {
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_bracket.step_in"), x, y, w, UiStyle.DIM);
			return;
		}
		if (b.royale()) {
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_bracket.royale_rules"), x, y, w, UiStyle.DIM);
			return;
		}
		ZFighterBracketNet.Match live = null;
		ZFighterBracketNet.Match next = null;
		for (ZFighterBracketNet.Match m : b.matches()) {
			if (m.live() && live == null) {
				live = m;
			} else if (!m.live() && m.winner() == ZFighterBracketNet.UNDECIDED && !m.a().isEmpty() && !m.b().isEmpty() && next == null) {
				next = m;
			}
		}
		if (live != null) {
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_bracket.live", name(live.a()), name(live.b())), x, y, w, ACCENT);
		}
		if (next != null) {
			UiStyle.lineRight(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_bracket.next", name(next.a()), name(next.b())),
				x + w, y, live == null ? w : w / 2 - 4, UiStyle.DIM);
		}
	}
}
