package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ClassPerks;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.0) The class screen ({@link Classes}), laid out like a character-creation screen: a row of cards, each a class's
 * name over a gold rule, its portrait (its signature gear, drawn large), a table of what it's good with
 * ({@link ClassPerks#card}) and the gear it starts with ({@link Classes#kit}). Four cards show at once; the arrows
 * turn to the other four. Pressing a card picks it; a Mage or a Sorcerer then picks their first spell or element from
 * a row under the cards; Choose seals it. Opened by the server the first time a player comes into a world (then it
 * can't be shut without picking) and when a Writ of Rebirth is read.
 */
final class ClassScreen extends Screen {
	static final int CARDS_SHOWN = 4, CARD_GAP = 6, CARD_H = 196, PORTRAIT_H = 56, BUTTON_H = 20, PORTRAIT_SCALE = 3;
	static final int COLOR_PANEL = SkillScreen.COLOR_PANEL, COLOR_EDGE = SkillScreen.COLOR_EDGE, COLOR_TEXT = SkillScreen.COLOR_TEXT,
		COLOR_DIM = SkillScreen.COLOR_DIM, COLOR_GOLD = SkillScreen.COLOR_GOLD, COLOR_CARD = 0xF0141210, COLOR_CARD_PICKED = 0xF0241E14,
		COLOR_ROW = 0x30FFFFFF;

	private final boolean forced;
	/** Which four cards show (0 or 1). */
	private int page;
	/** The card pressed, and (for a Mage or a Sorcerer) the option pressed under it. */
	private @Nullable PlayerClass picked;
	private int option = -1;

	ClassScreen(boolean forced) {
		super(Component.translatable("gui.jaymoskingdoms.class.title"));
		this.forced = forced;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Classes.Open.TYPE, (payload, context) -> context.client().gui.setScreen(new ClassScreen(payload.forced())));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !this.forced;
	}

	private int cardW() {
		return Math.min(112, (this.width - 40 - CARD_GAP * (CARDS_SHOWN - 1)) / CARDS_SHOWN);
	}

	private int rowLeft() {
		return (this.width - (cardW() * CARDS_SHOWN + CARD_GAP * (CARDS_SHOWN - 1))) / 2;
	}

	private int cardTop() {
		return Math.max(14, (this.height - CARD_H) / 2 - 10);
	}

	private int cardX(int index) {
		return rowLeft() + (index % CARDS_SHOWN) * (cardW() + CARD_GAP);
	}

	private List<PlayerClass> shown() {
		PlayerClass[] all = PlayerClass.values();
		int from = this.page * CARDS_SHOWN;
		return List.of(all).subList(from, Math.min(all.length, from + CARDS_SHOWN));
	}

	@Override
	protected void init() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && Classes.classOf(minecraft.player) != null) {
			onClose();
			return;
		}
		int top = cardTop(), w = cardW();
		// the cards: each a button the size of the card, drawn over by the card itself
		List<PlayerClass> shown = shown();
		for (int i = 0; i < shown.size(); i++) {
			PlayerClass cls = shown.get(i);
			Button card = Button.builder(Component.empty(), b -> {
				this.picked = cls;
				this.option = -1;
				rebuildWidgets();
			}).bounds(cardX(i), top, w, CARD_H).build();
			card.setTooltip(Tooltip.create(describe("class.jaymoskingdoms." + cls.key())));
			card.setAlpha(0.0F);
			addRenderableWidget(card);
		}
		int pages = (PlayerClass.values().length + CARDS_SHOWN - 1) / CARDS_SHOWN;
		Button prev = Button.builder(Component.literal("<"), b -> {
			this.page = (this.page + pages - 1) % pages;
			rebuildWidgets();
		}).bounds(rowLeft() - 18, top + CARD_H / 2 - 10, 14, 20).build();
		Button next = Button.builder(Component.literal(">"), b -> {
			this.page = (this.page + 1) % pages;
			rebuildWidgets();
		}).bounds(rowLeft() + w * CARDS_SHOWN + CARD_GAP * (CARDS_SHOWN - 1) + 4, top + CARD_H / 2 - 10, 14, 20).build();
		addRenderableWidget(prev);
		addRenderableWidget(next);
		// under the cards: the pick's options (a spell, an element), then Choose
		int y = top + CARD_H + 6;
		if (this.picked != null && !this.picked.options.isEmpty()) {
			List<String> options = this.picked.options;
			int ow = Math.min(72, (this.width - 40 - 4 * (options.size() - 1)) / options.size());
			int ox = (this.width - (ow * options.size() + 4 * (options.size() - 1))) / 2;
			for (int i = 0; i < options.size(); i++) {
				int index = i;
				String key = "class.jaymoskingdoms.option." + options.get(i);
				Button button = Button.builder(index == this.option ? Component.translatable(key).withStyle(ChatFormatting.GOLD) : Component.translatable(key), b -> {
					this.option = index;
					rebuildWidgets();
				}).bounds(ox + i * (ow + 4), y, ow, BUTTON_H).build();
				button.setTooltip(Tooltip.create(describe(key)));
				addRenderableWidget(button);
			}
			y += BUTTON_H + 4;
		}
		Button choose = Button.builder(this.picked == null ? Component.translatable("gui.jaymoskingdoms.class.choose")
			: Component.translatable("gui.jaymoskingdoms.class.confirm", Component.translatable("class.jaymoskingdoms." + this.picked.key())), b -> {
				if (this.picked != null) {
					ClientPlayNetworking.send(new Classes.Choose(this.picked.ordinal(), this.option));
					onClose();
				}
			}).bounds(this.width / 2 - (this.forced ? 60 : 92), y, 120, BUTTON_H).build();
		choose.active = this.picked != null && (this.picked.options.isEmpty() || this.option >= 0);
		addRenderableWidget(choose);
		if (!this.forced) {
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.class.later"), b -> onClose())
				.bounds(this.width / 2 + 32, y, 60, BUTTON_H).build());
		}
	}

	private static Component describe(String key) {
		return Component.translatable(key).withStyle(ChatFormatting.GOLD).append("\n").append(Component.translatable(key + ".desc").withStyle(ChatFormatting.WHITE));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && Classes.classOf(minecraft.player) != null) {
			onClose();
			return;
		}
		int top = cardTop(), w = cardW();
		graphics.centeredText(this.font, this.title, this.width / 2, top - 12, COLOR_GOLD);
		List<PlayerClass> shown = shown();
		for (int i = 0; i < shown.size(); i++) {
			card(graphics, shown.get(i), cardX(i), top, w);
		}
		// the lead, or what the pick's option does
		Component lead = this.picked != null && this.option >= 0
			? Component.translatable("class.jaymoskingdoms.option." + this.picked.options.get(this.option) + ".desc")
			: Component.translatable("gui.jaymoskingdoms.class.lead");
		int y = top + CARD_H + 6 + (this.picked != null && !this.picked.options.isEmpty() ? BUTTON_H + 4 : 0) + BUTTON_H + 4;
		for (FormattedCharSequence line : this.font.split(lead, Math.min(this.width - 20, 320))) {
			graphics.centeredText(this.font, line, this.width / 2, y, COLOR_DIM);
			y += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** One class's card: name, rule, portrait, the stat table, the starting gear. */
	private void card(GuiGraphicsExtractor graphics, PlayerClass cls, int x, int y, int w) {
		boolean picked = cls == this.picked;
		graphics.fill(x, y, x + w, y + CARD_H, picked ? COLOR_CARD_PICKED : COLOR_CARD);
		SkillScreen.edge(graphics, x, y, w, CARD_H, picked ? COLOR_GOLD : COLOR_EDGE);
		// the name over its gold rule
		Component name = Component.translatable("class.jaymoskingdoms." + cls.key());
		graphics.centeredText(this.font, name, x + w / 2, y + 5, picked ? COLOR_GOLD : COLOR_TEXT);
		graphics.fill(x + 6, y + 16, x + w - 6, y + 17, COLOR_EDGE);
		// the portrait: its signature gear, drawn large
		List<ItemStack> kit = Classes.kit(cls, cls.options.isEmpty() ? "" : cls.options.getFirst());
		if (!kit.isEmpty() && !kit.getFirst().isEmpty()) {
			int px = x + w / 2 - 8 * PORTRAIT_SCALE, py = y + 20 + (PORTRAIT_H - 16 * PORTRAIT_SCALE) / 2;
			graphics.pose().pushMatrix();
			graphics.pose().scale(PORTRAIT_SCALE, PORTRAIT_SCALE);
			graphics.item(kit.getFirst(), px / PORTRAIT_SCALE, py / PORTRAIT_SCALE);
			graphics.pose().popMatrix();
		}
		// the table
		int ty = y + 20 + PORTRAIT_H + 4;
		graphics.fill(x + 4, ty, x + w - 4, ty + 1, COLOR_EDGE);
		ty += 3;
		List<Component> lines = ClassPerks.card(cls);
		for (int i = 0; i < lines.size(); i++) {
			if (i % 2 == 0) {
				graphics.fill(x + 4, ty - 1, x + w - 4, ty + 10, COLOR_ROW);
			}
			graphics.text(this.font, lines.get(i), x + 7, ty, COLOR_TEXT);
			ty += 11;
		}
		// the starting gear, as a row of icons
		graphics.fill(x + 4, ty + 1, x + w - 4, ty + 2, COLOR_EDGE);
		int n = 0;
		for (ItemStack stack : kit) {
			if (!stack.isEmpty()) {
				n++;
			}
		}
		int gx = x + w / 2 - (n * 18) / 2, gy = y + CARD_H - 22;
		for (ItemStack stack : kit) {
			if (stack.isEmpty()) {
				continue;
			}
			graphics.item(stack, gx, gy);
			graphics.itemDecorations(this.font, stack, gx, gy);
			gx += 18;
		}
	}
}
