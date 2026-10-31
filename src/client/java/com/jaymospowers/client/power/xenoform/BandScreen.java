package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.client.UiStyle;
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
 * band clean (each asks twice). Every button goes to the server ({@link BandUpgrades.Action}), which reopens the screen.
 */
public class BandScreen extends Screen {
	private static final int MAX_W = 360;
	private static final int ROW = 20;
	private static final int TABS = 22;
	private static final int DETAIL_LINES = 4;
	private static final int ACCENT = 0xFF5CF07A;
	private static final String[] TAB_KEYS = {"modules", "skills", "aliens"};

	/** The tab and alien page, kept across the server's reopening. */
	private static int tab, page;

	private final int points;
	/** An alien (or "wipe") clicked once: the next click on it does it. */
	private @Nullable String armed;
	private final List<Component[]> details = new ArrayList<>();
	/** What the buttons were built from: rebuilt when the synced band or upgrades change (they may land after the reopening). */
	private @Nullable Object builtUp, builtBand;

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
		int x = left(), w = panelW();
		int tabW = (w - 12 - 8) / 3;
		for (int i = 0; i < 3; i++) {
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
			default -> aliens(bx);
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

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (builtUp != player().getAttached(BandUpgrades.UPGRADES) || builtBand != player().getAttached(XenoformState.BAND)) {
			rebuildWidgets();
		}
		int x = left(), y = top(), w = panelW();
		BandUpgrades.Upgrades up = BandUpgrades.get(player());
		UiStyle.panel(graphics, x, y, w, panelH(), ACCENT);
		Component sub = switch (tab) {
			case 0 -> up.reforged() ? Component.translatable("screen.jaymospowers.band.sub.slots", up.fitted().size(), BandUpgrades.SLOTS)
				: Component.translatable("screen.jaymospowers.band.sub.unforged");
			case 1 -> Component.translatable("screen.jaymospowers.band.sub.points", points, BandUpgrades.earned(player()));
			default -> Component.translatable("screen.jaymospowers.band.sub.aliens", aliensHeld().size());
		};
		UiStyle.header(graphics, this.font, x, y, w, new ItemStack(com.jaymospowers.power.xenoform.XenoBandItem.BAND), this.title, sub, ACCENT, 0);
		int hovered = -1;
		int textW = w - 12 - 80 - 12;
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
			} else if (page * rows() + i < aliensHeld().size()) {
				XenoForm form = XenoForms.get(aliensHeld().get(page * rows() + i));
				color = form == null ? UiStyle.TEXT : 0xFF000000 | form.color;
			}
			UiStyle.line(graphics, this.font, label, x + 10, ry + 6, textW, color);
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
