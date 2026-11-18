package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Parties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.jaymoskingdoms.client.ui.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.649) The character menu's Party tab (Jaymo's Powers' menu, {@link MenuCompat}), in the roster shape
 * ({@link RosterLayout}): your party ({@link Parties}) down the left, each member with their state (you, how far, or in
 * another world) and a health bar; on the right the member you point at or picked (health, hunger, where), then the
 * players near you to invite, and below, buttons that do what the {@code /party} commands do: invite, accept or turn down
 * an invite, leave, and for the leader, fighting each other on or off.
 */
final class PartyScreen extends Screen {
	static final int PANEL_W = 340, PANEL_H = 214, FOOTER = 24, NEAR_ROW = 18;
	/** How near (blocks) a player must be to show under "Invite". The most shown. */
	static final double NEAR = 32.0;
	static final int MAX_NEAR = 4;
	static final int HEALTH = 0xFFD84040, LOW = 0xFFFF9020, HUNGER = 0xFFC8872A, LEADER = Ui.GOLD;

	/** The member picked in the list (the pointed-at one shows instead while the mouse is on a row). */
	private @Nullable UUID picked;

	PartyScreen() {
		super(Component.translatable("menu.jaymoskingdoms.party.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 40 - MenuCompat.SIDE);
	}

	private int left() {
		return MenuCompat.centre(this.width, panelW());
	}

	private int top() {
		return Math.max(4 + MenuCompat.STRIP, (this.height - PANEL_H) / 2);
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
			addRenderableWidget(FlatButton.builder(Component.translatable("menu.jaymoskingdoms.party.leave"), b -> {
				command("party leave");
				onClose();
			}).bounds(x + 8, fy, 70, 16).build());
			if (leads()) {
				addRenderableWidget(FlatButton.builder(Component.translatable("menu.jaymoskingdoms.party.pvp_on"), b -> command("party pvp on"))
					.bounds(x + 82, fy, 70, 16).build());
				addRenderableWidget(FlatButton.builder(Component.translatable("menu.jaymoskingdoms.party.pvp_off"), b -> command("party pvp off"))
					.bounds(x + 156, fy, 70, 16).build());
			}
		} else {
			addRenderableWidget(FlatButton.builder(Component.translatable("menu.jaymoskingdoms.party.accept"), b -> {
				command("party accept");
				onClose();
			}).bounds(x + 8, fy, 90, 16).build());
			addRenderableWidget(FlatButton.builder(Component.translatable("menu.jaymoskingdoms.party.decline"), b -> command("party decline"))
				.bounds(x + 102, fy, 90, 16).build());
		}
		// invite someone near you, in the detail panel
		int ny = nearTop();
		for (Player p : near()) {
			String name = p.getGameProfile().name();
			addRenderableWidget(FlatButton.builder(Component.translatable("menu.jaymoskingdoms.party.invite"), b -> command("party invite " + name))
				.bounds(x + w - RosterLayout.PAD - 62, ny, 56, 16).build());
			ny += NEAR_ROW;
		}
	}

	/** Where the "Invite" rows start: in the detail panel, under the member's card. */
	private int nearTop() {
		return top() + PANEL_H - FOOTER - 6 - MAX_NEAR * NEAR_ROW + 2;
	}

	/** The list's top. */
	private int listTop() {
		return RosterLayout.bodyY(top()) + 3;
	}

	/** The height of the two panels' wells. */
	private int wellH() {
		return PANEL_H - RosterLayout.HEAD - 6 - FOOTER - 4;
	}

	@Override
	public void tick() {
		// the list changes as people come and go: keep the buttons in step
		if (this.minecraft.player != null && this.minecraft.player.tickCount % 20 == 0) {
			rebuildWidgets();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		List<Parties.Entry> party = PartyClient.party();
		for (int i = 0; i < party.size() && inParty(); i++) {
			if (Ui.over((int)event.x(), (int)event.y(), RosterLayout.listX(left()), listTop() + i * RosterLayout.ROW, RosterLayout.LIST_W, RosterLayout.ROW - 2)) {
				this.picked = party.get(i).id();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer me = this.minecraft.player;
		int x = left(), y = top(), w = panelW();
		List<Parties.Entry> party = PartyClient.party();
		RosterLayout.frame(graphics, this.font, x, y, w, PANEL_H, this.title, inParty()
			? Component.translatable("menu.jaymoskingdoms.party.count", party.size(), Parties.MAX_SIZE) : Component.translatable("menu.jaymoskingdoms.party.none"));
		RosterLayout.wells(graphics, x, y, w, wellH());
		int dx = RosterLayout.detailX(x), dw = RosterLayout.detailW(w), dy = RosterLayout.bodyY(y);
		Parties.Entry shown = null;
		if (me != null && inParty()) {
			int lx = RosterLayout.listX(x);
			for (int i = 0; i < party.size(); i++) {
				Parties.Entry e = party.get(i);
				int ry = listTop() + i * RosterLayout.ROW;
				boolean over = Ui.over(mouseX, mouseY, lx, ry, RosterLayout.LIST_W, RosterLayout.ROW - 2);
				boolean pick = e.id().equals(this.picked);
				RosterLayout.row(graphics, lx, ry, i, pick, over);
				Ui.line(graphics, this.font, e.name(), lx + 6, ry + 3, RosterLayout.LIST_W - 12, e.leader() ? LEADER : Ui.TEXT);
				float share = e.maxHealth() <= 0.0F ? 0.0F : Mth.clamp(e.health() / e.maxHealth(), 0.0F, 1.0F);
				Ui.bar(graphics, lx + 6, ry + 14, RosterLayout.LIST_W - 12, 3, share, share < 0.3F ? LOW : HEALTH);
				if (over) {
					shown = e;
				} else if (pick && shown == null) {
					shown = e;
				}
			}
			if (shown == null) {
				shown = party.stream().filter(e -> e.id().equals(me.getUUID())).findFirst().orElse(party.getFirst());
			}
			// the member's card
			Ui.line(graphics, this.font, shown.name(), dx + 8, dy + 7, dw - 16, shown.leader() ? LEADER : Ui.TEXT);
			Component where = shown.id().equals(me.getUUID()) ? Component.translatable("menu.jaymoskingdoms.party.you") : PartyClient.whereIs(me, shown);
			int py = dy + 21;
			int px = dx + 8;
			px += Ui.pill(graphics, this.font, px, py, Component.translatable("menu.jaymoskingdoms.party.health").getString(),
				Mth.ceil(shown.health()) + "/" + Mth.ceil(shown.maxHealth()), HEALTH) + 3;
			px += Ui.pill(graphics, this.font, px, py, Component.translatable("menu.jaymoskingdoms.party.hunger").getString(), Mth.ceil(shown.food()) + "/20", HUNGER) + 3;
			if (shown.leader()) {
				Ui.pill(graphics, this.font, px, py, Component.translatable("menu.jaymoskingdoms.party.leader").getString(), "", LEADER);
			}
			Ui.line(graphics, this.font, where, dx + 8, py + 18, dw - 16, Ui.DIM);
		} else {
			int ry = dy + 7;
			for (var line : this.font.split(Component.translatable("menu.jaymoskingdoms.party.how"), dw - 16)) {
				graphics.text(this.font, line, dx + 8, ry, Ui.DIM, false);
				ry += Ui.LINE + 1;
			}
		}
		int ny = nearTop();
		List<Player> near = near();
		Ui.rule(graphics, dx + 6, dx + dw - 6, ny - 14);
		Ui.line(graphics, this.font, Component.translatable(near.isEmpty() ? "menu.jaymoskingdoms.party.nobody_near" : "menu.jaymoskingdoms.party.near"),
			dx + 8, ny - 11, dw - 16, Ui.GOLD);
		for (Player p : near) {
			Ui.line(graphics, this.font, p.getDisplayName(), dx + 8, ny + 4, dw - 84, Ui.TEXT);
			ny += NEAR_ROW;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
