package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Commandments;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * (0.7.535) A patron's book of commandments ({@link Commandments}): an open book, its ten on two pages (I to V, VI to X),
 * and, for the patron you're sworn to, the ones you've broken struck through in red and "Broken: n / 10" at the foot.
 */
final class CommandmentsScreen extends Screen {
	static final int PAGE_W = 132, H = 176, SPINE = 6, PAD = 9, GAP = 4;
	static final int W = PAGE_W * 2 + SPINE;
	/** Parchment, its shade at the edges, the gilt frame, the red spine, the ink, the struck ink. */
	static final int PAPER = 0xFFF4E6C4, PAPER_EDGE = 0xFFE2CC98, GILT = 0xFFB8862B, GILT_DARK = 0xFF7A5618, SPINE_RED = 0xFF8E1C1C,
		SPINE_DARK = 0xFF5A0F0F, INK = 0xFF3A2A18, STRUCK = 0xFFA02020, FAINT = 0xFF8A7656;

	private final Patron patron;

	private CommandmentsScreen(Patron patron) {
		super(Component.translatable("gui.jaymoskingdoms.commandments.title"));
		this.patron = patron;
	}

	static void register() {
		Commandments.open = key -> {
			Patron p = Patron.byKey(key);
			if (p != null) {
				Minecraft.getInstance().gui.setScreen(new CommandmentsScreen(p));
			}
		};
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - H) / 2;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		page(graphics, x, y);
		page(graphics, x + PAGE_W + SPINE, y);
		// the spine
		graphics.fill(x + PAGE_W, y - 2, x + PAGE_W + SPINE, y + H + 2, SPINE_DARK);
		graphics.fill(x + PAGE_W + 1, y - 2, x + PAGE_W + SPINE - 1, y + H + 2, SPINE_RED);

		Minecraft mc = Minecraft.getInstance();
		Commandments.Kept kept = mc.player == null ? Commandments.Kept.EMPTY : Commandments.state(mc.player);
		boolean mine = mc.player != null && Patrons.of(mc.player) == this.patron && kept.patron().equals(this.patron.key);

		// the title, gilt rules either side of the patron's name
		int lx = x + PAD, lw = PAGE_W - 2 * PAD;
		int ty = y + PAD;
		Component title = this.title.copy().withStyle(net.minecraft.ChatFormatting.BOLD);
		graphics.text(this.font, title, lx + (lw - this.font.width(title)) / 2, ty, INK, false);
		Component name = this.patron.named();
		int nameY = ty + 11;
		graphics.text(this.font, name, lx + (lw - this.font.width(name)) / 2, nameY, 0xFF000000 | darker(this.patron.colour), false);
		ornament(graphics, lx + lw / 2, nameY + 11);

		int left = nameY + 17;
		int right = y + PAD;
		for (int n = 0; n < Commandments.COUNT; n++) {
			boolean first = n < Commandments.COUNT / 2;
			int px = first ? lx : x + PAGE_W + SPINE + PAD;
			boolean struck = mine && kept.broke(n);
			Component line = Component.literal(Commandments.numeral(n) + ". ").append(Commandments.text(this.patron, n));
			if (struck) {
				line = line.copy().withStyle(net.minecraft.ChatFormatting.STRIKETHROUGH);
			}
			List<FormattedCharSequence> wrapped = this.font.split(line, lw);
			int py = first ? left : right;
			for (FormattedCharSequence seq : wrapped) {
				graphics.text(this.font, seq, px, py, struck ? STRUCK : INK, false);
				py += this.font.lineHeight;
			}
			py += GAP + 2;
			if (first) {
				left = py;
			} else {
				right = py;
			}
		}

		// the foot of the right page
		int rx = x + PAGE_W + SPINE + PAD;
		int fy = y + H - PAD - this.font.lineHeight;
		ornament(graphics, rx + lw / 2, fy - 6);
		Component foot = mine ? Component.translatable("gui.jaymoskingdoms.commandments.broken", kept.count(), Commandments.COUNT)
			: Component.translatable("gui.jaymoskingdoms.commandments.not_sworn");
		graphics.text(this.font, foot, rx + (lw - this.font.width(foot)) / 2, fy, mine ? INK : FAINT, false);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** One page: parchment, a darker edge, a double gilt frame. */
	private static void page(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x - 2, y - 2, x + PAGE_W + 2, y + H + 2, GILT_DARK);
		graphics.fill(x - 1, y - 1, x + PAGE_W + 1, y + H + 1, PAPER_EDGE);
		graphics.fill(x + 1, y + 1, x + PAGE_W - 1, y + H - 1, PAPER);
		int i = 4;
		graphics.fill(x + i, y + i, x + PAGE_W - i, y + i + 1, GILT);
		graphics.fill(x + i, y + H - i - 1, x + PAGE_W - i, y + H - i, GILT);
		graphics.fill(x + i, y + i, x + i + 1, y + H - i, GILT);
		graphics.fill(x + PAGE_W - i - 1, y + i, x + PAGE_W - i, y + H - i, GILT);
		// the corners' studs
		for (int cx : new int[] {x + i - 1, x + PAGE_W - i - 2}) {
			for (int cy : new int[] {y + i - 1, y + H - i - 2}) {
				graphics.fill(cx, cy, cx + 3, cy + 3, GILT_DARK);
			}
		}
	}

	/** A short gilt rule with a stud in the middle. */
	private static void ornament(GuiGraphicsExtractor graphics, int cx, int y) {
		graphics.fill(cx - 30, y, cx + 30, y + 1, GILT);
		graphics.fill(cx - 2, y - 1, cx + 2, y + 2, GILT_DARK);
	}

	/** The patron's colour darkened to read on parchment (the Truth's white turns a warm grey). */
	private static int darker(int argb) {
		int r = (argb >> 16 & 0xFF) * 55 / 100, g = (argb >> 8 & 0xFF) * 55 / 100, b = (argb & 0xFF) * 55 / 100;
		return r << 16 | g << 8 | b;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
