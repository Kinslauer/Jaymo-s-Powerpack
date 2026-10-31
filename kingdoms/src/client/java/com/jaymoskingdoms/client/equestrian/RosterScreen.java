package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.client.Ui;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Herd;
import com.jaymoskingdoms.equestrian.Herding;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.305, ideas from Icy's Better Horses) The roster: every horse you own, nearest first, with where it is and what
 * it was last told, and two buttons a row: Call (it comes to you, from anywhere: it gallops over if it's close, else it's
 * fetched to your side) and Home (it sets off for the home you gave it). Opened from the command wheel; the list comes
 * from the server ({@link Herding.Roster}).
 */
final class RosterScreen extends Screen {
	private static final int W = 276, ROW = 22, ROWS = 7, HEADER = 22, FOOTER = 24;
	private static final int BUTTON_W = 34, BUTTON_H = 14;

	/** The last list the server sent (null until it answers). */
	private static @Nullable List<Herding.Entry> roster;
	private int scroll;

	RosterScreen() {
		super(Component.translatable("screen.jaymoskingdoms.roster"));
		roster = null;
		ClientPlayNetworking.send(new Herding.AskRoster());
	}

	/** (From {@code EquestrianClient}) the server's answer: shown if the roster's open. */
	static void receive(Herding.Roster payload) {
		roster = List.copyOf(payload.horses());
		if (Minecraft.getInstance().gui.screen() instanceof RosterScreen screen) {
			screen.scroll = Mth.clamp(screen.scroll, 0, screen.maxScroll());
			screen.rebuildWidgets();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - height()) / 2;
	}

	private static int height() {
		return HEADER + ROWS * ROW + FOOTER;
	}

	private int maxScroll() {
		return roster == null ? 0 : Math.max(0, roster.size() - ROWS);
	}

	@Override
	protected void init() {
		int x = left(), y = top();
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(x + W - 62, y + height() - FOOTER + 5, 56, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskingdoms.roster.refresh"), b -> {
			ClientPlayNetworking.send(new Herding.AskRoster());
		}).bounds(x + 6, y + height() - FOOTER + 5, 56, 16).build());
		if (roster == null) {
			return;
		}
		for (int i = 0; i < ROWS && i + this.scroll < roster.size(); i++) {
			Herding.Entry e = roster.get(i + this.scroll);
			int ry = y + HEADER + i * ROW + (ROW - BUTTON_H) / 2;
			int bx = x + W - 8 - BUTTON_W * 2 - 4;
			Button call = addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskingdoms.roster.call"),
				b -> act(e, Herding.CALL)).bounds(bx, ry, BUTTON_W, BUTTON_H).build());
			Button home = addRenderableWidget(Button.builder(Component.translatable("screen.jaymoskingdoms.roster.home"),
				b -> act(e, Herding.SEND_HOME)).bounds(bx + BUTTON_W + 4, ry, BUTTON_W, BUTTON_H).build());
			call.active = !e.foal();
			home.active = e.order() != Herd.Order.HOME.ordinal();
		}
	}

	private void act(Herding.Entry e, int act) {
		ClientPlayNetworking.send(new Herding.RosterAct(e.id(), act));
		onClose();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int was = this.scroll;
		this.scroll = Mth.clamp(this.scroll - (int)Math.signum(scrollY), 0, maxScroll());
		if (this.scroll != was) {
			rebuildWidgets();
		}
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, W, height(), Ui.GOLD);
		graphics.text(this.font, this.title, x + 8, y + 8, Ui.GOLD, false);
		if (roster == null || roster.isEmpty()) {
			Component note = Component.translatable(roster == null ? "screen.jaymoskingdoms.roster.asking" : "screen.jaymoskingdoms.roster.none");
			Ui.line(graphics, this.font, note, x + 8, y + HEADER + 6, W - 16, Ui.DIM);
		} else {
			if (roster.size() > ROWS) {
				String page = (this.scroll + 1) + "-" + Math.min(roster.size(), this.scroll + ROWS) + " / " + roster.size();
				graphics.text(this.font, page, x + W - 8 - this.font.width(page), y + 8, Ui.HINT, false);
			}
			for (int i = 0; i < ROWS && i + this.scroll < roster.size(); i++) {
				row(graphics, roster.get(i + this.scroll), x, y + HEADER + i * ROW, i, mouseX, mouseY);
			}
			Ui.scrollbar(graphics, x + W - 3, y + HEADER, ROWS * ROW, ROWS, roster.size(), this.scroll);
		}
		Ui.rule(graphics, x + 4, x + W - 4, y + height() - FOOTER);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** One horse: its name and sex over its breed, where it is and what it's doing. */
	private void row(GuiGraphicsExtractor graphics, Herding.Entry e, int x, int y, int index, int mouseX, int mouseY) {
		int textW = W - 16 - BUTTON_W * 2 - 8;
		Ui.row(graphics, x + 4, y, x + W - 4, y + ROW, index, Ui.over(mouseX, mouseY, x + 4, y, W - 8, ROW));
		Breed breed = Breed.byId(e.breed());
		Component breedName = Component.translatable(breed.key());
		MutableComponent name = (e.name().isEmpty() ? breedName.copy() : Component.literal(e.name())).withColor(Ui.TEXT);
		String sex = Herding.symbol(e.sex());
		if (!sex.isEmpty()) {
			name.append(Component.literal(" " + sex).withColor(e.sex() == Herd.MARE ? 0xFFFF9AC8 : 0xFF8AC8FF));
		}
		Ui.line(graphics, this.font, name, x + 8, y + 2, textW, Ui.TEXT);
		MutableComponent where;
		var player = this.minecraft.player;
		boolean sameWorld = player != null && player.level().dimension().identifier().equals(e.dimension());
		if (!sameWorld) {
			where = Component.translatable("screen.jaymoskingdoms.roster.elsewhere", e.dimension().getPath());
		} else {
			int far = Herding.flat(e.pos(), player.position());
			where = Component.translatable("screen.jaymoskingdoms.roster.far", far, e.pos().getX(), e.pos().getZ());
		}
		MutableComponent info = Component.empty();
		if (!e.name().isEmpty()) {
			info.append(breedName).append(Component.literal(" · "));
		}
		// (0.7.444) its coat, once the roster has seen it since
		if (!e.coat().isEmpty()) {
			info.append(Component.translatable("coat.jaymoskingdoms." + e.coat())).append(Component.literal(" · "));
		}
		info.append(where).append(Component.literal(" · ")).append(Component.translatable(Herd.Order.byId(e.order()).key()));
		if (!e.here()) {
			info.append(Component.literal(" · ")).append(Component.translatable("screen.jaymoskingdoms.roster.away"));
		}
		Ui.line(graphics, this.font, info, x + 8, y + 12, textW, Ui.DIM);
	}
}
