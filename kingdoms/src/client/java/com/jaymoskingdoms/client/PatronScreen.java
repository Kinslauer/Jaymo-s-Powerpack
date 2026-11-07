package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import com.jaymoskingdoms.Turning;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.509) The patrons screen (docs/Patrons-Plan.md section 8): the eight patrons down the left (the ones you have not met
 * dim), and on the right the one you picked: its domain, what it takes as an offering, the deeds it likes, who it is cool to,
 * and, for your own patron, your favour, the four blessings and the boon. Names, offerings, favour and mechanics only: no lore.
 * Opened from a shrine (sneak and use), the skill screen (K) and the boon menu. Reads only the synced state and the item tags.
 */
final class PatronScreen extends Screen {
	static final int MAX_W = 400, MAX_H = 236, LIST_W = 112, LIST_ROW = 19, HEADER = 34, FOOTER = 24;
	private static final int OFFERING_ICONS = 8;

	private final @Nullable Screen parent;
	private @Nullable Patron picked;
	private int scroll;
	private int contentH;

	PatronScreen(@Nullable Screen parent, @Nullable Patron picked) {
		super(Component.translatable("gui.jaymoskingdoms.patrons.title"));
		this.parent = parent;
		this.picked = picked;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int panelH() {
		return Math.min(MAX_H, this.height - 8);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int paneX() {
		return left() + LIST_W;
	}

	private int paneW() {
		return panelW() - LIST_W;
	}

	private int rowY(int index) {
		return top() + HEADER + index * LIST_ROW;
	}

	private Patrons.State state() {
		return this.minecraft.player == null ? Patrons.State.EMPTY : Patrons.state(this.minecraft.player);
	}

	private boolean known(Patron p) {
		Patrons.State s = state();
		return s.known().contains(p.key) || p.key.equals(s.patron());
	}

	@Override
	protected void init() {
		if (this.picked == null) {
			Patron mine = state().patronOf();
			this.picked = mine != null ? mine : Patron.values()[0];
		}
		int y = top() + panelH() - FOOTER + 4;
		int right = paneX() + paneW() - 6;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 56, y, 56, 16).build());
	}

	private @Nullable Patron rowAt(int mouseX, int mouseY) {
		Patron[] all = Patron.values();
		for (int i = 0; i < all.length; i++) {
			if (Ui.over(mouseX, mouseY, left() + 4, rowY(i), LIST_W - 8, LIST_ROW - 2)) {
				return all[i];
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Patron row = rowAt((int)event.x(), (int)event.y());
		if (row != null) {
			this.picked = row;
			this.scroll = 0;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int view = panelH() - HEADER - FOOTER - 2;
		this.scroll = Math.max(0, Math.min(Math.max(0, this.contentH - view), this.scroll - (int)(scrollY * 12)));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW(), h = panelH();
		Patrons.State s = state();
		Patron mine = s.patronOf();
		Ui.panel(graphics, x, y, w, h, mine != null ? mine.colour : Ui.GOLD);
		list(graphics, mouseX, mouseY, s, mine);
		int px = paneX(), pw = paneW();
		header(graphics, px, y, pw, s, mine);
		Ui.rule(graphics, px + 6, px + pw - 6, y + HEADER - 4);
		Patron p = this.picked;
		if (p != null) {
			detail(graphics, p, s, mine, px, y + HEADER, pw, y + h - FOOTER - 2);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Your standing, under the title. */
	private void header(GuiGraphicsExtractor graphics, int px, int y, int pw, Patrons.State s, @Nullable Patron mine) {
		Ui.line(graphics, this.font, this.title, px + 8, y + 7, pw - 16, Ui.TEXT);
		Component standing;
		int color = Ui.DIM;
		if (mine != null) {
			standing = Component.translatable("gui.jaymoskingdoms.patrons.sworn", mine.named(), s.swornDay() + 1,
				Component.translatable("patron.jaymoskingdoms.tier." + s.tier()));
			color = mine.colour;
			// (0.7.513) praying towards another patron: your own will mark you
			Patron to = Patron.byKey(s.streak().patron());
			if (to != null && to != mine && s.streak().days() > 0 && s.streak().lastDay() >= today() - 1) {
				standing = Component.translatable("gui.jaymoskingdoms.patrons.turning", to.named(), s.streak().days(), Patrons.SWEAR_DAYS);
				color = Ui.BAD;
			}
		} else if (s.streak().days() > 0) {
			Patron streaking = Patron.byKey(s.streak().patron());
			standing = Component.translatable("gui.jaymoskingdoms.patrons.streak", streaking == null ? "" : streaking.named(), s.streak().days(), Patrons.SWEAR_DAYS);
		} else {
			standing = Component.translatable("gui.jaymoskingdoms.patrons.unsworn");
		}
		Ui.line(graphics, this.font, standing, px + 8, y + 18, pw - 16, color);
		long longest = daysLeftMost(s);
		if (longest > 0L) {
			Component trials = Component.translatable("gui.jaymoskingdoms.patrons.trials", longest);
			Ui.lineRight(graphics, this.font, trials, px + pw - 8, y + 7, pw / 2 - 8, Ui.BAD);
		}
	}

	/** (0.7.513) The clock's shrine day on this client, or 0 with no world. */
	private long today() {
		return this.minecraft.level == null ? 0L : Patrons.day(this.minecraft.level.getOverworldClockTime());
	}

	private static boolean isTrial(Patrons.Mark m) {
		return Turning.TRIAL.equals(m.kind()) || Turning.TRIAL_EASED_KIND.equals(m.kind());
	}

	/** (0.7.513) The most days left of any live trial. */
	private long daysLeftMost(Patrons.State s) {
		long today = today(), most = 0L;
		for (Patrons.Mark m : s.marks()) {
			if (isTrial(m) && m.day() > today) {
				most = Math.max(most, m.day() - today);
			}
		}
		return most;
	}

	/** The eight patrons down the left. */
	private void list(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Patrons.State s, @Nullable Patron mine) {
		int x = left(), y = top();
		graphics.fill(x + LIST_W - 1, y + 4, x + LIST_W, y + panelH() - 4, Ui.ROW);
		Patron[] all = Patron.values();
		Patron hover = rowAt(mouseX, mouseY);
		for (int i = 0; i < all.length; i++) {
			Patron p = all[i];
			int ry = rowY(i);
			boolean picked = p == this.picked, known = known(p);
			graphics.fill(x + 4, ry, x + LIST_W - 4, ry + LIST_ROW - 2, picked ? Ui.ROW_PICKED : p == hover ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW_ALT : 0x0CFFFFFF);
			graphics.fill(x + 4, ry, x + 6, ry + LIST_ROW - 2, p == mine ? Ui.GOLD : known ? p.colour : Ui.HINT);
			Component name = known ? p.named() : Component.translatable("patron.jaymoskingdoms.unknown");
			Ui.line(graphics, this.font, name, x + 10, ry + 5, LIST_W - 18, !known ? Ui.HINT : p == mine || picked ? p.colour : p == hover ? Ui.TEXT : Ui.DIM);
			// (0.7.513) a notch for each scar of this patron (up to 3), and a bar while its trials run
			if (this.minecraft.player != null) {
				int scars = Math.min(3, Turning.scars(this.minecraft.player, p));
				for (int n = 0; n < scars; n++) {
					graphics.fill(x + LIST_W - 14 - n * 3, ry + 3, x + LIST_W - 12 - n * 3, ry + 5, Ui.BAD);
				}
				if (Turning.trial(this.minecraft.player, p) > 0.0F) {
					graphics.fill(x + LIST_W - 6, ry, x + LIST_W - 4, ry + LIST_ROW - 2, Ui.BAD);
				}
			}
		}
	}

	/** One wrapped paragraph at {@code y} in {@code color}; the y after it. */
	private int paragraph(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color, int clipTop, int clipBottom) {
		for (FormattedCharSequence line : this.font.split(text, width)) {
			if (y + Ui.LINE > clipTop && y < clipBottom) {
				graphics.text(this.font, line, x, y, color);
			}
			y += Ui.LINE;
		}
		return y;
	}

	private void detail(GuiGraphicsExtractor graphics, Patron p, Patrons.State s, @Nullable Patron mine, int px, int top, int pw, int bottom) {
		graphics.enableScissor(px, top, px + pw, bottom);
		int x = px + 10, width = pw - 22, y = top - this.scroll + 2;
		int clipTop = top, clipBottom = bottom;
		if (!known(p)) {
			y = paragraph(graphics, Component.translatable("patron.jaymoskingdoms.unknown"), x, y, width, Ui.HINT, clipTop, clipBottom);
			y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.unknown_hint"), x, y + 4, width, Ui.DIM, clipTop, clipBottom);
			this.contentH = y + this.scroll - top;
			graphics.disableScissor();
			return;
		}
		y = paragraph(graphics, p.named(), x, y, width, p.colour, clipTop, clipBottom);
		y = paragraph(graphics, Component.translatable("patron.jaymoskingdoms." + p.key + ".domain"), x, y, width, Ui.DIM, clipTop, clipBottom);
		// offerings
		y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.offerings"), x, y + 4, width, Ui.TEXT, clipTop, clipBottom);
		List<ItemStack> icons = new ArrayList<>();
		for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(p.offerings)) {
			if (icons.size() >= OFFERING_ICONS) {
				break;
			}
			icons.add(new ItemStack(holder.value()));
		}
		for (int i = 0; i < icons.size(); i++) {
			if (y + 16 > clipTop && y < clipBottom) {
				graphics.item(icons.get(i), x + i * 18, y);
			}
		}
		y += 20;
		// deeds
		y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.deeds"), x, y, width, Ui.TEXT, clipTop, clipBottom);
		for (int i = 1; i <= 2; i++) {
			y = paragraph(graphics, Component.literal("- ").append(Component.translatable("patron.jaymoskingdoms." + p.key + ".deed." + i)), x, y, width, Ui.DIM, clipTop, clipBottom);
		}
		// rivals
		y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.rivals", rivals(p)), x, y + 4, width, Ui.DIM, clipTop, clipBottom);
		y = marks(graphics, p, s, x, y, width, clipTop, clipBottom);
		if (mine != null && p != mine) {
			y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.turn_warning", p.named(), mine.named()), x, y + 4, width, Ui.HINT, clipTop, clipBottom);
		}
		if (p == mine) {
			y = ownPatron(graphics, p, s, x, y + 6, width, clipTop, clipBottom);
		}
		this.contentH = y + this.scroll - top;
		graphics.disableScissor();
	}

	/** (0.7.513) Your scars and trials of {@code p}, when you have any. */
	private int marks(GuiGraphicsExtractor graphics, Patron p, Patrons.State s, int x, int y, int width, int clipTop, int clipBottom) {
		if (s.marks().isEmpty() || this.minecraft.player == null) {
			return y;
		}
		int scars = Turning.scars(this.minecraft.player, p);
		long today = today(), left = 0L;
		boolean eased = false, any = false;
		for (Patrons.Mark m : s.marks()) {
			if (isTrial(m) && m.day() > today && m.patron().equals(p.key)) {
				any = true;
				if (m.day() - today > left) {
					left = m.day() - today;
					eased = Turning.TRIAL_EASED_KIND.equals(m.kind());
				}
			}
		}
		if (scars <= 0 && !any) {
			return y;
		}
		y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.marks"), x, y + 4, width, Ui.TEXT, clipTop, clipBottom);
		if (scars > 0) {
			y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.scarred", scars), x, y, width, Ui.BAD, clipTop, clipBottom);
			y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.scar." + p.key), x + 6, y, width - 6, Ui.DIM, clipTop, clipBottom);
		}
		if (any) {
			y = paragraph(graphics, Component.translatable(eased ? "gui.jaymoskingdoms.patrons.trial.eased" : "gui.jaymoskingdoms.patrons.trial", left), x, y, width, Ui.BAD, clipTop, clipBottom);
			y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.trial." + p.key), x + 6, y, width - 6, Ui.DIM, clipTop, clipBottom);
			y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.trial.shunned", p.named()), x + 6, y, width - 6, Ui.DIM, clipTop, clipBottom);
			if (!eased) {
				y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.trial.rite_hint"), x, y + 2, width, Ui.HINT, clipTop, clipBottom);
			}
		}
		return y;
	}

	private static Component rivals(Patron p) {
		if (p == Patron.TRUTH) {
			return Component.translatable("gui.jaymoskingdoms.patrons.rivals.seven");
		}
		Component out = Patron.TRUTH.named();
		if (p.rival != null) {
			out = Component.empty().append(p.rival.named()).append(", ").append(out);
		}
		if (p == Patron.LANTERN_WARDEN) {
			out = Component.empty().append(Component.translatable("gui.jaymoskingdoms.patrons.rivals.dark")).append(", ").append(out);
		}
		return out;
	}

	/** Favour, the four blessings and the boon, for your own patron. */
	private int ownPatron(GuiGraphicsExtractor graphics, Patron p, Patrons.State s, int x, int y, int width, int clipTop, int clipBottom) {
		y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.favour", s.favour(), Patrons.FAVOUR_MAX), x, y, width, Ui.TEXT, clipTop, clipBottom);
		if (y + 6 > clipTop && y < clipBottom) {
			Ui.bar(graphics, x, y, width, 5, s.favour() / (float)Patrons.FAVOUR_MAX, p.colour);
			for (int at : Patrons.TIER_AT) {
				int tx = x + Math.round(width * at / (float)Patrons.FAVOUR_MAX);
				graphics.fill(tx, y - 1, tx + 1, y + 6, Ui.TEXT);
			}
		}
		y += 10;
		for (int tier = 1; tier <= 4; tier++) {
			boolean lit = s.tier() >= tier;
			int name = lit ? p.colour : Ui.HINT;
			y = paragraph(graphics, Component.translatable("blessing.jaymoskingdoms." + p.key + "." + tier), x, y + 2, width, name, clipTop, clipBottom);
			y = paragraph(graphics, Component.translatable("blessing.jaymoskingdoms." + p.key + "." + tier + ".desc"), x + 6, y, width - 6, lit ? Ui.DIM : Ui.HINT, clipTop, clipBottom);
		}
		y = paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.boon").append(": ").append(BoonScreen.stateLine(s, this.minecraft)), x, y + 4, width,
			s.tier() >= 4 ? Ui.GOOD : Ui.HINT, clipTop, clipBottom);
		return paragraph(graphics, Component.translatable("gui.jaymoskingdoms.patrons.decay", Patrons.GRACE_DAYS), x, y + 2, width, Ui.HINT, clipTop, clipBottom);
	}
}
