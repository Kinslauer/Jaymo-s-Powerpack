package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.xenoform.BandStyle;
import com.jaymospowers.power.xenoform.BandUpgrades;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoForms;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.123, Xeno-Band patch 7) The band screen (B as yourself): three tabs. <b>Modules</b>: the modules the band has learned
 * from plans, fitted into its {@link BandUpgrades#SLOTS} slots once reforged. <b>Skills</b>: band points (from mastery) spent
 * on quick change, the death save, scan reach, a bigger charge and a faster recharge. <b>Aliens</b>: forget one, or wipe the
 * band clean (each asks twice). (0.18.126, patch 9) <b>Looks</b>: the band's glow, its face plate and spikes, and each
 * alien's look ({@link BandStyle}), beside a turning preview of you. Every button goes to the server
 * ({@link BandUpgrades.Action}), which reopens the screen.
 */
public class BandScreen extends Screen {
	private static final int MAX_W = 360;
	private static final int ROW = 20;
	private static final int TABS = 22;
	private static final int DETAIL_LINES = 4;
	private static final int ACCENT = 0xFF5CF07A;
	private static final String[] TAB_KEYS = {"modules", "skills", "aliens", "looks"};
	/** The Looks tab: its band rows before the aliens', the preview's width and size, and how fast it turns (radians a tick). */
	private static final int BAND_ROWS = 3, PREVIEW_W = 64, PREVIEW_SCALE = 28;
	private static final float PREVIEW_TURN = 0.04F;

	/** The tab and alien page, kept across the server's reopening. */
	private static int tab, page;

	private final int points;
	/** An alien (or "wipe") clicked once: the next click on it does it. */
	private @Nullable String armed;
	private final List<Component[]> details = new ArrayList<>();
	/** What the buttons were built from: rebuilt when the synced band or upgrades change (they may land after the reopening). */
	private @Nullable Object builtUp, builtBand, builtStyle;

	public BandScreen(int points) {
		super(Component.translatable("screen.jaymospowers.band"));
		this.points = points;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BandUpgrades.Open.TYPE, (payload, context) -> context.client().gui.setScreen(new BandScreen(payload.points())));
	}

	private Player player() {
		return Minecraft.getInstance().player;
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int rows() {
		int room = this.height - 8 - UiStyle.HEADER - TABS - UiStyle.detailHeight(DETAIL_LINES) - ROW - 6;
		return Math.max(3, Math.min(8, room / ROW));
	}

	private int panelH() {
		return UiStyle.HEADER + TABS + rows() * ROW + ROW + 6 + UiStyle.detailHeight(DETAIL_LINES);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int rowY(int i) {
		return top() + UiStyle.HEADER + TABS + i * ROW;
	}

	private void send(String what, String arg) {
		ClientPlayNetworking.send(new BandUpgrades.Action(what, arg));
	}

	@Override
	protected void init() {
		details.clear();
		builtUp = player().getAttached(BandUpgrades.UPGRADES);
		builtBand = player().getAttached(XenoformState.BAND);
		builtStyle = player().getAttached(BandStyle.STYLE);
		int x = left(), w = panelW();
		int tabW = (w - 12 - 4 * (TAB_KEYS.length - 1)) / TAB_KEYS.length;
		for (int i = 0; i < TAB_KEYS.length; i++) {
			int t = i;
			Button b = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.band.tab." + TAB_KEYS[i]), btn -> {
				tab = t;
				armed = null;
				rebuildWidgets();
			}).bounds(x + 6 + i * (tabW + 4), top() + UiStyle.HEADER, tabW, 16).build());
			b.active = tab != i;
		}
		int bx = x + w - 6 - 80;
		switch (tab) {
			case 0 -> modules(bx);
			case 1 -> skills(bx);
			case 2 -> aliens(bx);
			default -> looks(bx);
		}
	}

	private void modules(int bx) {
		BandUpgrades.Upgrades up = BandUpgrades.get(player());
		int i = 0;
		for (BandUpgrades.Module m : BandUpgrades.Module.values()) {
			boolean known = up.knows(m), fitted = up.fitted().contains(m.id);
			Component label = known ? m.title() : Component.translatable("screen.jaymospowers.band.unknown");
			details.add(new Component[] {label, Component.translatable(known ? "band.jaymospowers.module." + m.id + ".desc" : "screen.jaymospowers.band.unknown.desc")});
			Button b = addRenderableWidget(Button.builder(Component.translatable(fitted ? "screen.jaymospowers.band.unfit" : "screen.jaymospowers.band.fit"),
				btn -> send(fitted ? "unfit" : "fit", m.id)).bounds(bx, rowY(i) + 2, 80, 16).build());
			b.active = up.reforged() && known && (fitted || up.fitted().size() < BandUpgrades.SLOTS);
			i++;
		}
	}

	private void skills(int bx) {
		BandUpgrades.Upgrades up = BandUpgrades.get(player());
		int i = 0;
		for (BandUpgrades.Skill s : BandUpgrades.Skill.values()) {
			int rank = up.rank(s);
			details.add(new Component[] {s.title(), Component.translatable("band.jaymospowers.skill." + s.id + ".desc")});
			Button b = addRenderableWidget(Button.builder(rank >= s.ranks ? Component.translatable("screen.jaymospowers.band.owned")
				: Component.translatable("screen.jaymospowers.band.buy", s.cost), btn -> send("buy", s.id)).bounds(bx, rowY(i) + 2, 80, 16).build());
			b.active = rank < s.ranks && points >= s.cost;
			i++;
		}
	}

	private List<String> aliensHeld() {
		return XenoformState.band(player()).unlocked();
	}

	private void aliens(int bx) {
		List<String> held = aliensHeld();
		int rows = rows();
		int pages = Math.max(1, (held.size() + rows - 1) / rows);
		page = Math.min(page, pages - 1);
		for (int i = 0; i < rows; i++) {
			int n = page * rows + i;
			if (n >= held.size()) {
				break;
			}
			String id = held.get(n);
			XenoForm form = XenoForms.get(id);
			Component name = form == null ? Component.literal(id) : form.name();
			details.add(new Component[] {name, Component.translatable("screen.jaymospowers.band.forget.desc")});
			boolean sure = id.equals(armed);
			addRenderableWidget(Button.builder(Component.translatable(sure ? "screen.jaymospowers.band.sure" : "screen.jaymospowers.band.forget")
				.withStyle(sure ? ChatFormatting.RED : ChatFormatting.RESET), btn -> {
				if (id.equals(armed)) {
					armed = null;
					send("forget", id);
				} else {
					armed = id;
					rebuildWidgets();
				}
			}).bounds(bx, rowY(i) + 2, 80, 16).build());
		}
		int fy = rowY(rows) + 2, x = left();
		Button prev = addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
			page--;
			rebuildWidgets();
		}).bounds(x + 6, fy, 20, 16).build());
		prev.active = page > 0;
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
			page++;
			rebuildWidgets();
		}).bounds(x + 30, fy, 20, 16).build());
		next.active = page < pages - 1;
		boolean sure = "wipe".equals(armed);
		addRenderableWidget(Button.builder(Component.translatable(sure ? "screen.jaymospowers.band.wipe_sure" : "screen.jaymospowers.band.wipe")
			.withStyle(sure ? ChatFormatting.RED : ChatFormatting.RESET), btn -> {
			if ("wipe".equals(armed)) {
				armed = null;
				send("wipe", "");
			} else {
				armed = "wipe";
				rebuildWidgets();
			}
		}).bounds(x + panelW() - 6 - 120, fy, 120, 16).build());
	}

	/** (0.18.126) The Looks tab: the glow, the plate, the spikes, then a page of the band's aliens and their looks. */
	private void looks(int bx) {
		BandStyle.Style style = BandStyle.get(player());
		int glow = BandStyle.glow(player());
		details.add(new Component[] {Component.translatable("screen.jaymospowers.band.glow", Component.literal("\u25A0").withColor(glow)),
			Component.translatable("screen.jaymospowers.band.glow.desc")});
		lookButton(bx, 0, Component.translatable("screen.jaymospowers.band.change"), "glow", "");
		details.add(new Component[] {Component.translatable("screen.jaymospowers.band.plate", Component.translatable("band.jaymospowers.plate." + style.plate())),
			Component.translatable("screen.jaymospowers.band.plate.desc")});
		lookButton(bx, 1, Component.translatable("screen.jaymospowers.band.change"), "plate", "");
		details.add(new Component[] {Component.translatable("screen.jaymospowers.band.spikes",
			Component.translatable(style.spikes() ? "screen.jaymospowers.band.on" : "screen.jaymospowers.band.off")),
			Component.translatable("screen.jaymospowers.band.spikes.desc")});
		lookButton(bx, 2, Component.translatable(style.spikes() ? "screen.jaymospowers.band.take_off" : "screen.jaymospowers.band.put_on"), "spikes", "");
		List<String> held = aliensHeld();
		int per = Math.max(1, rows() - BAND_ROWS);
		int pages = Math.max(1, (held.size() + per - 1) / per);
		page = Math.min(page, pages - 1);
		for (int i = 0; i < per; i++) {
			int n = page * per + i;
			if (n >= held.size()) {
				break;
			}
			String id = held.get(n);
			XenoForm form = XenoForms.get(id);
			Component name = form == null ? Component.literal(id) : form.name();
			BandStyle.Look look = style.look(id);
			details.add(new Component[] {Component.translatable("screen.jaymospowers.band.look", name, Component.translatable("band.jaymospowers.look." + look.id)),
				Component.translatable("screen.jaymospowers.band.look.desc")});
			lookButton(bx, BAND_ROWS + i, Component.translatable("screen.jaymospowers.band.change"), "look", id);
		}
		pager(pages);
	}

	private void lookButton(int bx, int row, Component label, String what, String arg) {
		addRenderableWidget(Button.builder(label, btn -> send(what, arg)).bounds(bx, rowY(row) + 2, 80, 16).build());
	}

	/** The page buttons under the rows. */
	private void pager(int pages) {
		int fy = rowY(rows()) + 2, x = left();
		Button prev = addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
			page--;
			rebuildWidgets();
		}).bounds(x + 6, fy, 20, 16).build());
		prev.active = page > 0;
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
			page++;
			rebuildWidgets();
		}).bounds(x + 30, fy, 20, 16).build());
		next.active = page < pages - 1;
	}

	/** (0.18.126) The Looks tab's preview: you, turning slowly, between the rows' names and their buttons. */
	private void preview(GuiGraphicsExtractor graphics, float a) {
		int x1 = left() + panelW() - 6 - 80 - 8, x0 = x1 - PREVIEW_W;
		int y0 = rowY(0), y1 = rowY(rows()) - 2;
		graphics.fill(x0, y0, x1, y1, 0x60000000);
		// a mouse that circles the box: the body follows it round
		float t = (player().tickCount + a) * PREVIEW_TURN;
		float mx = (x0 + x1) / 2.0F + net.minecraft.util.Mth.sin(t) * PREVIEW_W, my = y0 + (y1 - y0) * 0.35F;
		net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x0, y0, x1, y1, PREVIEW_SCALE, 0.0625F,
			mx, my, player());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (builtUp != player().getAttached(BandUpgrades.UPGRADES) || builtBand != player().getAttached(XenoformState.BAND)
			|| builtStyle != player().getAttached(BandStyle.STYLE)) {
			rebuildWidgets();
		}
		int x = left(), y = top(), w = panelW();
		BandUpgrades.Upgrades up = BandUpgrades.get(player());
		UiStyle.panel(graphics, x, y, w, panelH(), ACCENT);
		Component sub = switch (tab) {
			case 0 -> up.reforged() ? Component.translatable("screen.jaymospowers.band.sub.slots", up.fitted().size(), BandUpgrades.SLOTS)
				: Component.translatable("screen.jaymospowers.band.sub.unforged");
			case 1 -> Component.translatable("screen.jaymospowers.band.sub.points", points, BandUpgrades.earned(player()));
			case 2 -> Component.translatable("screen.jaymospowers.band.sub.aliens", aliensHeld().size());
			default -> Component.translatable("screen.jaymospowers.band.sub.looks");
		};
		UiStyle.header(graphics, this.font, x, y, w, new ItemStack(com.jaymospowers.power.xenoform.XenoBandItem.BAND), this.title, sub, ACCENT, 0);
		int hovered = -1;
		int textW = tab == 3 ? w - 12 - 80 - 12 - PREVIEW_W - 8 : w - 12 - 80 - 12;
		for (int i = 0; i < details.size(); i++) {
			int ry = rowY(i);
			boolean hover = UiStyle.over(mouseX, mouseY, x + 6, ry, w - 12, ROW - 2);
			if (hover) {
				hovered = i;
			}
			UiStyle.row(graphics, x + 6, ry, x + w - 6, ry + ROW - 2, i, hover);
			Component label = details.get(i)[0];
			int color = UiStyle.TEXT;
			if (tab == 0) {
				BandUpgrades.Module m = BandUpgrades.Module.values()[i];
				color = up.fits(m) ? ACCENT : up.knows(m) ? UiStyle.TEXT : UiStyle.HINT;
			} else if (tab == 1) {
				BandUpgrades.Skill s = BandUpgrades.Skill.values()[i];
				int rank = up.rank(s);
				label = Component.translatable("screen.jaymospowers.band.rank", label, rank, s.ranks);
				color = rank > 0 ? ACCENT : UiStyle.TEXT;
			} else if (tab == 3) {
				color = i < BAND_ROWS ? ACCENT : UiStyle.TEXT;
			} else if (page * rows() + i < aliensHeld().size()) {
				XenoForm form = XenoForms.get(aliensHeld().get(page * rows() + i));
				color = form == null ? UiStyle.TEXT : 0xFF000000 | form.color;
			}
			UiStyle.line(graphics, this.font, label, x + 10, ry + 6, textW, color);
		}
		if (tab == 3) {
			preview(graphics, a);
		}
		int dy = y + panelH() - UiStyle.detailHeight(DETAIL_LINES);
		if (hovered >= 0) {
			UiStyle.detail(graphics, this.font, x, dy, w, DETAIL_LINES, details.get(hovered)[0], ACCENT, List.of(details.get(hovered)[1]));
		} else {
			UiStyle.detail(graphics, this.font, x, dy, w, DETAIL_LINES, null, ACCENT,
				List.of(Component.translatable("screen.jaymospowers.band.help." + TAB_KEYS[tab])));
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
