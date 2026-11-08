package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Parties;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.7.649) The character menu's Party tab (Jaymo's Powers' menu, {@link MenuCompat}): your party ({@link Parties}), each
 * member with their health and hunger and how far they are, and buttons that do what the {@code /party} commands do:
 * invite a player near you, accept or turn down an invite, leave, and for the leader, fighting each other on or off.
 */
final class PartyScreen extends Screen {
	static final int PANEL_W = 300, PANEL_H = 210, HEADER = 34, ROW = 22, NEAR_ROW = 18, FOOTER = 24;
	/** How near (blocks) a player must be to show under "Invite". The most shown. */
	static final double NEAR = 32.0;
	static final int MAX_NEAR = 4;
	static final int HEALTH = 0xFFD84040, LOW = 0xFFFF9020, HUNGER = 0xFFC8872A, LEADER = Ui.GOLD;

	PartyScreen() {
		super(Component.translatable("menu.jaymoskingdoms.party.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 40);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	private boolean inParty() {
		return PartyClient.party().size() >= 2;
	}

	private boolean leads() {
		LocalPlayer me = this.minecraft.player;
		for (Parties.Entry e : PartyClient.party()) {
			if (me != null && e.id().equals(me.getUUID())) {
				return e.leader();
			}
		}
		return false;
	}

	/** Players near you who aren't in your party. */
	private List<Player> near() {
		List<Player> out = new ArrayList<>();
		LocalPlayer me = this.minecraft.player;
		if (me == null) {
			return out;
		}
		for (Player p : me.level().players()) {
			if (p == me || p.distanceTo(me) > NEAR || PartyClient.party().stream().anyMatch(e -> e.id().equals(p.getUUID()))) {
				continue;
			}
			out.add(p);
			if (out.size() >= MAX_NEAR) {
				break;
			}
		}
		return out;
	}

	private void command(String command) {
		if (this.minecraft.player != null) {
			this.minecraft.player.connection.sendCommand(command);
		}
	}

	@Override
	protected void init() {
		int x = left(), w = panelW(), fy = top() + PANEL_H - FOOTER + 4;
		if (inParty()) {
			addRenderableWidget(Button.builder(Component.translatable("menu.jaymoskingdoms.party.leave"), b -> {
				command("party leave");
				onClose();
			}).bounds(x + 6, fy, 70, 16).build());
			if (leads()) {
				addRenderableWidget(Button.builder(Component.translatable("menu.jaymoskingdoms.party.pvp_on"), b -> command("party pvp on"))
					.bounds(x + 80, fy, 70, 16).build());
				addRenderableWidget(Button.builder(Component.translatable("menu.jaymoskingdoms.party.pvp_off"), b -> command("party pvp off"))
					.bounds(x + 154, fy, 70, 16).build());
			}
		} else {
			addRenderableWidget(Button.builder(Component.translatable("menu.jaymoskingdoms.party.accept"), b -> {
				command("party accept");
				onClose();
			}).bounds(x + 6, fy, 90, 16).build());
			addRenderableWidget(Button.builder(Component.translatable("menu.jaymoskingdoms.party.decline"), b -> command("party decline"))
				.bounds(x + 100, fy, 90, 16).build());
		}
		// invite someone near you
		int ny = nearTop();
		for (Player p : near()) {
			String name = p.getGameProfile().name();
			addRenderableWidget(Button.builder(Component.translatable("menu.jaymoskingdoms.party.invite"), b -> command("party invite " + name))
				.bounds(x + w - 66, ny, 60, 16).build());
			ny += NEAR_ROW;
		}
	}

	/** Where the "Invite" rows start: under the members. */
	private int nearTop() {
		int members = Math.max(1, PartyClient.party().size());
		int bottom = top() + PANEL_H - FOOTER - 4 - MAX_NEAR * NEAR_ROW;
		return Math.min(top() + HEADER + 2 + members * ROW + 14, bottom);
	}

	@Override
	public void tick() {
		// the list changes as people come and go: keep the buttons in step
		if (this.minecraft.player != null && this.minecraft.player.tickCount % 20 == 0) {
			rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer me = this.minecraft.player;
		int x = left(), y = top(), w = panelW();
		Ui.panel(graphics, x, y, w, PANEL_H, Ui.GOLD);
		graphics.item(new ItemStack(Items.SHIELD), x + 8, y + 8);
		Ui.line(graphics, this.font, this.title, x + 28, y + 7, w - 36, Ui.GOLD);
		List<Parties.Entry> party = PartyClient.party();
		Ui.line(graphics, this.font, inParty() ? Component.translatable("menu.jaymoskingdoms.party.count", party.size(), Parties.MAX_SIZE)
			: Component.translatable("menu.jaymoskingdoms.party.none"), x + 28, y + 18, w - 36, Ui.DIM);
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);
		int ry = y + HEADER + 2;
		if (me != null && inParty()) {
			for (int i = 0; i < party.size(); i++) {
				Parties.Entry e = party.get(i);
				boolean over = Ui.over(mouseX, mouseY, x + 6, ry, w - 12, ROW - 2);
				Ui.row(graphics, x + 6, ry, x + w - 6, ry + ROW - 2, i, over);
				Component where = e.id().equals(me.getUUID()) ? Component.translatable("menu.jaymoskingdoms.party.you") : PartyClient.whereIs(me, e);
				int whereW = this.font.width(where);
				Ui.line(graphics, this.font, e.name(), x + 10, ry + 2, w - 30 - whereW, e.leader() ? LEADER : Ui.TEXT);
				graphics.text(this.font, where, x + w - 10 - whereW, ry + 2, Ui.DIM, false);
				float share = e.maxHealth() <= 0.0F ? 0.0F : Mth.clamp(e.health() / e.maxHealth(), 0.0F, 1.0F);
				int bw = w - 20;
				Ui.bar(graphics, x + 10, ry + 12, bw, 3, share, share < 0.3F ? LOW : HEALTH);
				Ui.bar(graphics, x + 10, ry + 16, bw, 1, Mth.clamp(e.food() / 20.0F, 0.0F, 1.0F), HUNGER);
				ry += ROW;
			}
		} else {
			for (var line : this.font.split(Component.translatable("menu.jaymoskingdoms.party.how"), w - 20)) {
				graphics.text(this.font, line, x + 10, ry + 2, Ui.DIM, false);
				ry += Ui.LINE + 1;
			}
		}
		int ny = nearTop();
		List<Player> near = near();
		Ui.line(graphics, this.font, Component.translatable(near.isEmpty() ? "menu.jaymoskingdoms.party.nobody_near" : "menu.jaymoskingdoms.party.near"),
			x + 10, ny - 11, w - 20, Ui.GOLD);
		for (Player p : near) {
			Ui.line(graphics, this.font, p.getDisplayName(), x + 10, ny + 4, w - 90, Ui.TEXT);
			ny += NEAR_ROW;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
