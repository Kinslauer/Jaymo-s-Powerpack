package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Tables;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The dice and cards table ({@link Tables}): who is seated and what they hold (other players' hidden cards drawn face
 * down), the pot and stake, and the buttons for whatever you may do now. It shows only what the server last sent; every
 * button is a request ({@link Tables.Act}) the server may refuse. Closing the screen leaves the table.
 */
final class GameScreen extends Screen {
	static final int W = 330, H = 226, PAD = 10, ROW_H = 36;
	static final int FELT = 0xFF1C4A32, FELT_EDGE = 0xFF0F2A1D, CARD = 0xFFF2EBD8, CARD_BACK = 0xFF3A3A8C, RED = 0xFFB02020, INK = 0xFF202020;
	private static final String SUITS = "SHDC";
	private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

	private static Tables.State last;
	private Tables.State state;
	private Button deal, hit, stand, cheat, stakeLess, stakeMore, game;
	private boolean left;

	private GameScreen(Tables.State state) {
		super(Component.translatable("table.jaymoskingdoms.title"));
		this.state = state;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Tables.State.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (payload.closed()) {
				if (mc.gui.screen() instanceof GameScreen s) {
					s.left = true;
					s.onClose();
				}
				return;
			}
			if (mc.gui.screen() instanceof GameScreen s) {
				s.state = payload;
				s.sync();
			} else if (mc.gui.screen() == null) {
				mc.gui.setScreen(new GameScreen(payload));
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	private static void send(int action, int arg) {
		ClientPlayNetworking.send(new Tables.Act(action, arg));
	}

	@Override
	protected void init() {
		int x = left(), y = top() + H - 52;
		this.game = addRenderableWidget(Button.builder(Component.empty(), b -> send(Tables.GAME, this.state.game() + 1)).bounds(x + PAD, y, 84, 20).build());
		this.stakeLess = addRenderableWidget(Button.builder(Component.literal("-"), b -> send(Tables.STAKE, -1)).bounds(x + PAD + 88, y, 20, 20).build());
		this.stakeMore = addRenderableWidget(Button.builder(Component.literal("+"), b -> send(Tables.STAKE, 1)).bounds(x + PAD + 110, y, 20, 20).build());
		this.deal = addRenderableWidget(Button.builder(Component.empty(), b -> send(Tables.DEAL, 0)).bounds(x + PAD + 136, y, 80, 20).build());
		this.hit = addRenderableWidget(Button.builder(Component.translatable("table.jaymoskingdoms.hit"), b -> send(Tables.HIT, 0)).bounds(x + PAD, y + 24, 60, 20).build());
		this.stand = addRenderableWidget(Button.builder(Component.translatable("table.jaymoskingdoms.stand"), b -> send(Tables.STAND, 0)).bounds(x + PAD + 64, y + 24, 60, 20).build());
		this.cheat = addRenderableWidget(Button.builder(Component.empty(), b -> send(Tables.CHEAT, 0)).bounds(x + PAD + 128, y + 24, 96, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("table.jaymoskingdoms.leave"), b -> onClose()).bounds(x + W - PAD - 70, y + 24, 70, 20).build());
		sync();
	}

	private void sync() {
		if (this.deal == null) {
			return;
		}
		boolean idle = this.state.phase() == Tables.IDLE;
		this.game.setMessage(Component.translatable("table.jaymoskingdoms.game." + this.state.game()));
		this.game.active = idle;
		this.stakeLess.active = idle && this.state.stake() > 0;
		this.stakeMore.active = idle && this.state.stake() < Tables.STAKES.length - 1;
		this.deal.setMessage(Component.translatable(this.state.game() == 0 ? "table.jaymoskingdoms.roll" : "table.jaymoskingdoms.deal"));
		this.deal.active = this.state.canDeal();
		boolean cards = this.state.game() == 1;
		this.hit.visible = cards;
		this.stand.visible = cards;
		this.hit.active = this.state.yourTurn();
		this.stand.active = this.state.yourTurn();
		boolean armed = this.state.entries().stream().anyMatch(e -> e.you() && e.armed());
		this.cheat.setMessage(Component.translatable(armed ? "table.jaymoskingdoms.cheat_on" : "table.jaymoskingdoms.cheat"));
		this.cheat.active = this.state.phase() != Tables.RESULT;
	}

	@Override
	public void onClose() {
		if (!this.left) {
			this.left = true;
			send(Tables.LEAVE, 0);
		}
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(g, x, y, W, H, Ui.GOLD);
		g.fill(x + 6, y + 22, x + W - 6, y + H - 58, FELT_EDGE);
		g.fill(x + 7, y + 23, x + W - 7, y + H - 59, FELT);
		Component head = Component.translatable("table.jaymoskingdoms.head." + this.state.game(), Tables.STAKES[this.state.stake()], this.state.pot());
		Ui.line(g, this.font, head, x + PAD, y + 8, W - PAD * 2, Ui.GOLD);
		int row = y + 26;
		for (Tables.Entry e : this.state.entries()) {
			drawEntry(g, e, x + 10, row);
			row += ROW_H;
		}
		if (this.state.entries().isEmpty()) {
			g.centeredText(this.font, Component.translatable("table.jaymoskingdoms.empty"), x + W / 2, y + 60, Ui.DIM);
		}
		Ui.line(g, this.font, this.state.note(), x + PAD, y + H - 74 + 12, W - PAD * 2, Ui.TEXT);
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void drawEntry(GuiGraphicsExtractor g, Tables.Entry e, int x, int y) {
		int color = switch (e.status()) {
			case Tables.WON -> Ui.GOOD;
			case Tables.LOST, Tables.BUST -> Ui.BAD;
			case Tables.CAUGHT -> 0xFFFF8040;
			case Tables.TURN -> Ui.GOLD;
			default -> Ui.TEXT;
		};
		String name = (e.house() ? "[" + e.name() + "]" : e.name()) + (e.you() ? " *" : "");
		g.text(this.font, name, x, y + 2, color);
		g.text(this.font, Component.translatable("table.jaymoskingdoms.status." + e.status()).getString()
			+ (e.total() >= 0 ? "  " + e.total() : ""), x, y + 14, Ui.DIM);
		int cx = x + 112;
		for (int c : e.cards()) {
			if (this.state.game() == 0) {
				drawDie(g, cx, y + 4, c);
				cx += 24;
			} else {
				drawCard(g, cx, y + 1, c);
				cx += 22;
			}
		}
	}

	private void drawDie(GuiGraphicsExtractor g, int x, int y, int value) {
		g.fill(x, y, x + 20, y + 20, INK);
		g.fill(x + 1, y + 1, x + 19, y + 19, value < 0 ? CARD_BACK : CARD);
		if (value >= 0) {
			g.centeredText(this.font, Integer.toString(value), x + 10, y + 6, INK);
		}
	}

	private void drawCard(GuiGraphicsExtractor g, int x, int y, int card) {
		g.fill(x, y, x + 20, y + 30, INK);
		g.fill(x + 1, y + 1, x + 19, y + 29, card < 0 ? CARD_BACK : CARD);
		if (card >= 0) {
			int suit = card / 13;
			int ink = suit == 1 || suit == 2 ? RED : INK;
			g.text(this.font, RANKS[card % 13], x + 3, y + 3, ink);
			g.text(this.font, Character.toString(SUITS.charAt(suit)), x + 3, y + 17, ink);
		}
	}
}
