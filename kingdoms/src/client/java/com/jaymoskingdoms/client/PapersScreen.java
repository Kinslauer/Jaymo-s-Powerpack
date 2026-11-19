package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Papers;
import com.jaymoskingdoms.Renown;
import com.jaymoskingdoms.Skills;
import com.jaymoskingdoms.client.ui.FlatButton;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * Your papers ({@link Papers}): who you are on paper, on a card of parchment in a gold frame. From the Character page's
 * "Show my papers" link it is your own; a player who sneak-uses you with an empty hand has shown theirs, and it opens on
 * your screen (or, with a screen already open, as a line in chat).
 */
public final class PapersScreen extends Screen {
	static final int W = 250, PAD = 12, ROW = 15;
	/** The pill colours: rank in the realm's gold, level in a calm blue. */
	static final int PILL_LEVEL = 0xFF6AA8FF;

	private final Papers.Card card;

	private PapersScreen(Papers.Card card) {
		super(Component.translatable("gui.jaymoskingdoms.papers.title", card.owner()));
		this.card = card;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Papers.Card.TYPE, (card, context) -> {
			Minecraft minecraft = context.client();
			if (card.mine() || minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new PapersScreen(card));
			} else {
				minecraft.player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.papers.shown_you", card.owner()));
			}
		});
		// sneak-use on another player with an empty hand: show them yours
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof Player other) || other == player || !player.isShiftKeyDown()
				|| !player.getMainHandItem().isEmpty() || !ClientPlayNetworking.canSend(Papers.Show.TYPE)) {
				return InteractionResult.PASS;
			}
			ClientPlayNetworking.send(new Papers.Show(other.getId()));
			return InteractionResult.SUCCESS;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (client.gui.screen() instanceof PapersScreen) {
				client.gui.setScreen(null);
			}
		});
	}

	/** Asks the server for your own papers; they open when they arrive. */
	static void askOwn() {
		if (ClientPlayNetworking.canSend(Papers.Show.TYPE)) {
			ClientPlayNetworking.send(new Papers.Show(-1));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<Component[]> rows() {
		List<Component[]> rows = new ArrayList<>();
		Component none = Component.translatable("menu.jaymoskingdoms.character.none");
		rows.add(new Component[] {Component.translatable("menu.jaymoskingdoms.character.heritage"),
			card.heritage().isEmpty() ? none : Component.translatable("heritage.jaymoskingdoms." + card.heritage())});
		Component cls = card.cls().isEmpty() ? none : Component.translatable("class.jaymoskingdoms." + card.cls());
		rows.add(new Component[] {Component.translatable("menu.jaymoskingdoms.character.class"),
			card.second().isEmpty() ? cls : Component.translatable("menu.jaymoskingdoms.character.pair", cls, Component.translatable("class.jaymoskingdoms." + card.second()))});
		rows.add(new Component[] {Component.translatable("menu.jaymoskingdoms.character.realm"), card.realm().isEmpty() ? none : Component.literal(card.realm())});
		Skills.Pillar pillar = card.renown() == 0 ? null : Renown.titlePillar(card.renown());
		rows.add(new Component[] {Component.translatable("menu.jaymoskingdoms.character.renown"), pillar == null ? Component.translatable("menu.jaymoskingdoms.character.unknown")
			: Component.translatable("title.jaymoskingdoms." + pillar.key() + (Renown.titleMaster(card.renown()) ? ".master" : ""))});
		return rows;
	}

	private int height() {
		return PAD + 30 + rows().size() * ROW + 4 + 18 + 4 + 20 + PAD;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - height()) / 2);
	}

	@Override
	protected void init() {
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.papers.close"), b -> onClose())
			.bounds(left() + W / 2 - 30, top() + height() - PAD - 16, 60, 16).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), h = height();
		Ui.panelFramed(graphics, x, y, W, h, Ui.GOLD);
		// the sheet
		int sx = x + 8, sy = y + 8, sw = W - 16, sh = h - 16 - 22;
		graphics.fill(sx, sy, sx + sw, sy + sh, Ui.PAPER);
		graphics.outline(sx, sy, sw, sh, Ui.PAPER_EDGE);
		Ui.lineCentred(graphics, this.font, this.title, x + W / 2, sy + 8, sw - 16, Ui.INK);
		graphics.fill(sx + 12, sy + 20, sx + sw - 12, sy + 21, Ui.PAPER_EDGE);
		int ry = sy + 28;
		for (Component[] r : rows()) {
			Ui.line(graphics, this.font, r[0], sx + 12, ry, 70, Ui.INK_FAINT);
			Ui.line(graphics, this.font, r[1], sx + 86, ry, sw - 98, Ui.INK);
			ry += ROW;
		}
		// standing in the realm, and the level, as pills
		int px = sx + 12;
		if (!card.realm().isEmpty()) {
			px += Ui.pill(graphics, this.font, px, ry + 2, Component.translatable(card.rank()).getString(), Integer.toString(card.standing()), 0xFF000000 | card.color()) + 4;
		}
		Ui.pill(graphics, this.font, px, ry + 2, Component.translatable("menu.jaymoskingdoms.character.level_label").getString(), Integer.toString(card.level()), PILL_LEVEL);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Puts "Show my papers" on the Character page's Record panel (Jaymo's Powers' {@code MenuTabs.characterLink}). */
	static void link(Class<?> menu) {
		try {
			menu.getMethod("characterLink", java.util.function.Supplier.class, Runnable.class).invoke(null,
				(java.util.function.Supplier<Component>)() -> Component.translatable("gui.jaymoskingdoms.papers.link"), (Runnable)PapersScreen::askOwn);
		} catch (ReflectiveOperationException | LinkageError e) {
			// an older Powers without the link: the papers still open by sneak-using a player
		}
	}

}
