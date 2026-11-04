package com.jaymoskingdoms.client;

import com.jaymoskingdoms.DiplomacyNet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.519) A sovereign's desk ({@link DiplomacyNet.Desk}): the realms in reach on the left; for the one picked, its creed,
 * the treaties in force (renounce with a second click), the four kinds and their odds, and Send an Envoy. Nothing is decided
 * here: the server checks every act again, and the screen comes again with what happened written under it.
 */
final class RulersDeskScreen extends Screen {
	static final int W = 360, H = 220, PAD = 8, LIST_W = 150, ROW_H = 12, ROWS = 12, BUTTON_H = 16;
	/** Bronze. */
	static final int ACCENT = 0xFFB89A5E;
	/** A second click on Renounce counts within this long (ms). */
	static final long SURE_MS = 3000L;
	private static final String[] KINDS = {"recognition", "passage", "charter", "aid"};

	private final DiplomacyNet.Desk desk;
	private int picked;
	private int scroll;
	private int kind;
	private int armedKind = -1;
	private long armedAt;
	private final Map<Integer, Button> renounce = new HashMap<>();
	private final Button[] kindButtons = new Button[KINDS.length];

	RulersDeskScreen(DiplomacyNet.Desk desk, @Nullable RulersDeskScreen was) {
		super(Component.translatable("gui.jaymoskingdoms.desk.title", desk.realm()));
		this.desk = desk;
		this.picked = was != null ? was.picked : desk.rows().isEmpty() ? -1 : desk.rows().get(0).id();
		this.scroll = was != null ? was.scroll : 0;
		this.kind = was != null ? was.kind : 0;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DiplomacyNet.Desk.TYPE, (payload, context) -> {
			RulersDeskScreen was = context.client().gui.screen() instanceof RulersDeskScreen s ? s : null;
			context.client().gui.setScreen(new RulersDeskScreen(payload, was));
		});
	}

	private DiplomacyNet.@Nullable Row row() {
		for (DiplomacyNet.Row r : this.desk.rows()) {
			if (r.id() == this.picked) {
				return r;
			}
		}
		return null;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	private int rightX() {
		return left() + PAD + LIST_W + PAD;
	}

	private int rightW() {
		return left() + W - PAD - rightX();
	}

	@Override
	protected void init() {
		this.renounce.clear();
		int x = left(), y = top();
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.desk.close"), b -> onClose())
			.bounds(x + W - PAD - 70, y + H - PAD - BUTTON_H, 70, BUTTON_H).build());
		DiplomacyNet.Row r = row();
		if (r == null) {
			return;
		}
		int rx = rightX(), rw = rightW();
		for (int i = 0; i < r.kinds().size(); i++) {
			int idx = indexOf(r.kinds().get(i));
			Button b = Button.builder(Component.translatable("gui.jaymoskingdoms.desk.renounce"), btn -> renounce(r, idx))
				.bounds(rx + rw - 62, y + 46 + i * 13, 62, 12).build();
			this.renounce.put(idx, b);
			addRenderableWidget(b);
		}
		int kw = (rw - 4) / 2;
		for (int k = 0; k < KINDS.length; k++) {
			int kk = k;
			Button b = Button.builder(Component.translatable("treaty.jaymoskingdoms." + KINDS[k]), btn -> {
				this.kind = kk;
				rebuildWidgets();
			}).bounds(rx + (k % 2) * (kw + 4), y + 102 + (k / 2) * 16, kw, 14).build();
			int odds = r.odds().get(k);
			b.active = odds != -1;
			b.setTooltip(Tooltip.create(odds == -1 ? closed(r, k) : Component.translatable("treaty.jaymoskingdoms." + KINDS[k] + ".about")));
			this.kindButtons[k] = b;
			addRenderableWidget(b);
		}
		Button send = Button.builder(Component.translatable("gui.jaymoskingdoms.desk.send", this.desk.cost()), b -> act(DiplomacyNet.SEND, r.id(), this.kind))
			.bounds(rx, y + 148, rw, BUTTON_H).build();
		send.active = r.odds().get(this.kind) != -1 && !r.envoyOut();
		addRenderableWidget(send);
	}

	private Component closed(DiplomacyNet.Row r, int k) {
		if (r.atWar()) {
			return Component.translatable("gui.jaymoskingdoms.desk.at_war");
		}
		if (k != 0 && !r.kinds().contains(KINDS[0])) {
			return Component.translatable("gui.jaymoskingdoms.desk.needs_recognition");
		}
		return Component.translatable("treaty.jaymoskingdoms." + KINDS[k] + ".about");
	}

	private static int indexOf(String key) {
		for (int i = 0; i < KINDS.length; i++) {
			if (KINDS[i].equals(key)) {
				return i;
			}
		}
		return 0;
	}

	private void act(int action, int realm, int kind) {
		ClientPlayNetworking.send(new DiplomacyNet.DeskAct(action, realm, kind));
	}

	private void renounce(DiplomacyNet.Row r, int idx) {
		long now = Util.getMillis();
		if (this.armedKind == idx && now - this.armedAt < SURE_MS) {
			this.armedKind = -1;
			act(DiplomacyNet.RENOUNCE, r.id(), idx);
			return;
		}
		this.armedKind = idx;
		this.armedAt = now;
		Button b = this.renounce.get(idx);
		if (b != null) {
			b.setMessage(Component.translatable("gui.jaymoskingdoms.desk.renounce_sure"));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int lx = left() + PAD, ly = top() + 34;
		List<DiplomacyNet.Row> rows = this.desk.rows();
		for (int i = 0; i < ROWS && this.scroll + i < rows.size(); i++) {
			if (Ui.over((int)event.x(), (int)event.y(), lx, ly + i * ROW_H, LIST_W, ROW_H)) {
				this.picked = rows.get(this.scroll + i).id();
				this.armedKind = -1;
				rebuildWidgets();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int max = Math.max(0, this.desk.rows().size() - ROWS);
		int was = this.scroll;
		this.scroll = Math.max(0, Math.min(max, this.scroll - (int)Math.signum(scrollY)));
		return this.scroll != was || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		if (this.armedKind >= 0 && Util.getMillis() - this.armedAt >= SURE_MS) {
			Button b = this.renounce.get(this.armedKind);
			if (b != null) {
				b.setMessage(Component.translatable("gui.jaymoskingdoms.desk.renounce"));
			}
			this.armedKind = -1;
		}
		Ui.panel(graphics, x, y, W, H, ACCENT);
		Ui.line(graphics, this.font, this.title, x + PAD, y + 7, W - 2 * PAD, ACCENT);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.desk.treasury", this.desk.treasury()), x + PAD, y + 18, W - 2 * PAD, Ui.DIM);
		Ui.rule(graphics, x + 6, x + W - 6, y + 30);
		int lx = x + PAD, ly = y + 34;
		List<DiplomacyNet.Row> rows = this.desk.rows();
		if (rows.isEmpty()) {
			Component none = this.desk.status().equals("gui.jaymoskingdoms.desk.vassal") ? Component.translatable(this.desk.status(), this.desk.realm())
				: Component.translatable("gui.jaymoskingdoms.desk.none");
			graphics.text(this.font, none, lx, ly + 2, Ui.DIM);
		}
		for (int i = 0; i < ROWS && this.scroll + i < rows.size(); i++) {
			DiplomacyNet.Row r = rows.get(this.scroll + i);
			int ry = ly + i * ROW_H;
			boolean hover = Ui.over(mouseX, mouseY, lx, ry, LIST_W, ROW_H);
			Ui.row(graphics, lx, ry, lx + LIST_W, ry + ROW_H, this.scroll + i, hover);
			if (r.id() == this.picked) {
				graphics.fill(lx, ry, lx + LIST_W, ry + ROW_H, Ui.ROW_PICKED);
			}
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.desk.row", r.name(),
				Component.translatable("relation.jaymoskingdoms." + r.relation()), r.tension()), lx + 2, ry + 2, LIST_W - 4, r.playerRealm() ? ACCENT : Ui.TEXT);
		}
		Ui.scrollbar(graphics, lx + LIST_W - 2, ly, ROWS * ROW_H, ROWS * ROW_H, rows.size() * ROW_H, this.scroll * ROW_H);
		DiplomacyNet.Row r = row();
		int rx = rightX(), rw = rightW();
		if (r != null) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.desk.creed", Component.translatable("creed.jaymoskingdoms." + r.creed())), rx, y + 34, rw,
				Ui.DIM);
			for (int i = 0; i < r.kinds().size(); i++) {
				Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.desk.in_force", Component.translatable("treaty.jaymoskingdoms." + r.kinds().get(i)),
					r.daysLeft().get(i)), rx, y + 48 + i * 13, rw - 66, Ui.TEXT);
			}
			int odds = r.odds().get(this.kind);
			if (odds != -1) {
				String key = odds == 3 ? "player" : odds == 2 ? "likely" : odds == 1 ? "maybe" : "unlikely";
				Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.desk.odds." + key), rx, y + 136, rw, odds == 2 ? Ui.GOOD : odds == 0 ? Ui.BAD : Ui.TEXT);
			}
			if (r.envoyOut()) {
				Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.desk.envoy_out"), rx, y + 168, rw, Ui.DIM);
			}
		}
		if (!this.desk.status().isEmpty() && !this.desk.status().equals("gui.jaymoskingdoms.desk.vassal")) {
			Ui.line(graphics, this.font, Component.translatable(this.desk.status()), x + PAD, y + 184, W - 2 * PAD - 80, Ui.TEXT);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		Button picked = this.kind >= 0 && this.kind < this.kindButtons.length ? this.kindButtons[this.kind] : null;
		if (r != null && picked != null && picked.active) {
			int bx = picked.getX(), by = picked.getY(), bw = picked.getWidth(), bh = picked.getHeight();
			graphics.fill(bx - 1, by - 1, bx + bw + 1, by, ACCENT);
			graphics.fill(bx - 1, by + bh, bx + bw + 1, by + bh + 1, ACCENT);
			graphics.fill(bx - 1, by, bx, by + bh, ACCENT);
			graphics.fill(bx + bw, by, bx + bw + 1, by + bh, ACCENT);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
