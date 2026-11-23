package com.jaymoskingdoms.client;

import com.jaymoskingdoms.DialogueCue.Speaker;
import com.jaymoskingdoms.IllagerCard;
import com.jaymoskingdoms.TalkNet;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Talking with an illager ({@link IllagerCard}), laid out like MCA's villager screen as Kingdoms shapes it (Andy,
 * 2026-11-23: "these pillager interactions should look and work just like MCA"). MCA's own screen can't be opened for
 * an illager (it is made for MCA's villagers and asks MCA's server about them), so this one follows it: who they are in
 * tooltip-style lines down the top left (name, what they are, mood, nature, how they take you) with the hearts icon
 * under them (pointing at it shows how many), and the buttons in one column on the right in the dark purple panel
 * ({@code McaButtonColumnMixin}), with MCA's {@code <<} back arrow to its left.
 * <ul>
 * <li>The first page has Talk, Interact and Trade, as MCA's. Talk opens what you can say (their name, a chat, what they
 * want, word of their hold, goodbye); Interact opens Gift, Talk Them Round and buying off the posters.</li>
 * <li>Gift is MCA's gift mode: the buttons go grey, the scroll wheel walks the hotbar, and Enter or the right mouse button
 * gives what you now hold; {@code <<} or Escape leaves gift mode.</li>
 * <li>A parley, the trade list and Talk Them Round's moves stand straight in the column.</li>
 * <li>The screen stays open from one answer to the next, as MCA's does: an answer greys the column until their reply
 * (the next card) comes, which takes its place where you were. An answer that ends the talk (goodbye, a parley's way
 * out, a threat) shuts it, as does a reply that never comes ({@link #WAIT_TICKS}).</li>
 * <li>With MCA in the pack the talk's letterbox camera runs as for a villager ({@link DialogueCinematic}): what they say
 * goes in its box low on the left, and the column comes up once they've finished saying it. Without MCA this screen
 * draws the box itself.</li>
 * </ul>
 * Picks go back as {@link TalkNet.Answer} with the option's place on the card.
 */
final class IllagerTalkScreen extends Screen {
	/** MCA's column: right edge 10 in from the screen's, at least 80 wide, rows from y 10, 21 apart (with our column mixin). */
	static final int RIGHT_GAP = 10, MIN_WIDTH = 80, MAX_WIDTH = 150, LABEL_PAD = 12, TOP = 10, ROW = 21, BUTTON_H = 20;
	/** The panel's margin round the column, the back arrow's size and its gap to the panel. */
	static final int PANEL_PAD = 3, ARROW = 20, ARROW_GAP = 2;
	static final int PANEL_FILL = 0xC0100010, BORDER_TOP = 0x905000FF, BORDER_BOTTOM = 0x9028007F;
	/** MCA's info lines: x, the first line's y, and the step between lines. */
	static final int INFO_X = 10, INFO_Y = 28, INFO_STEP = 17;
	/** MCA's heart icon: where it sits (screen pixels, drawn 1.5 times over) and how far pointing at it reaches. */
	static final int HEART_X = 20, HEART_Y = 120, HEART_HOVER = 24;
	static final float ICON_SCALE = 1.5F;
	/** MCA's red, black and gold hearts in its icon sheet (when MCA is in the pack), and the hearts that make a gold one. */
	static final Identifier MCA_ICONS = Identifier.fromNamespaceAndPath("mca", "textures/gui.png");
	static final int RED_U = 80, BLACK_U = 96, GOLD_U = 112, GOLD_HEARTS = 100;
	/** Without MCA, the game's own hearts. */
	static final Identifier HEART = Identifier.withDefaultNamespace("hud/heart/full"), HEART_BLACK = Identifier.withDefaultNamespace("hud/heart/withered_full"),
		HEART_GOLD = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
	/** Mood colours, spiteful to pleased. */
	static final ChatFormatting[] MOOD_COLORS = {ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.DARK_GREEN};
	/** How long an answer waits for their reply before the screen shuts (ticks). */
	static final int WAIT_TICKS = 60;
	/** The words box when this screen draws it (no MCA): its margin, width, padding, and gap above the screen's foot. */
	static final int BOX_MARGIN = 10, BOX_WIDTH = 220, BOX_PAD = 6, BOX_FOOT = 40, NAME_COLOR = 0xFFFFD27A;

	private enum Page {
		MAIN, TALK, INTERACT
	}

	private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");
	/** A reply that comes for a talk just shut (Escape while it was on its way) doesn't open it again: whose, and until when (ms). */
	static final long SHUT_MS = 3000L;
	private static int shutOn = -1;
	private static long shutUntil;

	private IllagerCard card;
	private Page page = Page.MAIN;
	private boolean gift;
	/** Ticks since an answer went out with no card back yet (-1: not waiting). */
	private int waiting = -1;
	/** Something they said since the last answer, for the box (it stands over the card's own words). */
	private boolean heard;
	/** The words box (when this screen draws it): what was last said. */
	private Component said;
	/** The column's buttons on this page, for the panel. */
	private final List<AbstractWidget> column = new ArrayList<>();

	private IllagerTalkScreen(IllagerCard card) {
		super(card.name());
		this.card = card;
		this.said = IllagerWords.resolve(card.words());
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(IllagerCard.TYPE, (card, context) -> {
			if (context.client().gui.screen() instanceof IllagerTalkScreen open && open.card.entityId() == card.entityId()) {
				open.update(card);
			} else if (card.entityId() != shutOn || System.currentTimeMillis() > shutUntil) {
				context.client().gui.setScreen(new IllagerTalkScreen(card));
				if (MCA) {
					// the talk camera, as for a villager, and their first words in its box
					DialogueCinematic.start(context.client(), card.entityId());
					DialogueCinematic.said(card.entityId(), IllagerWords.resolve(card.words()));
				}
			}
		});
	}

	/** {@code entityId} said {@code text} (SpeechBubbles): if it's the illager this screen is open on, it goes in the words box. */
	static void heard(int entityId, Component text) {
		if (Minecraft.getInstance().gui.screen() instanceof IllagerTalkScreen open && open.card.entityId() == entityId) {
			open.said = text;
			open.heard = true;
		}
	}

	/** The illager this screen is open on (for {@link DialogueCinematic}'s camera). */
	int speaker() {
		return this.card.entityId();
	}

	/** Their next card (their reply to an answer): in this screen's place, on the same page where it can be. */
	private void update(IllagerCard next) {
		boolean wasPaged = paged(this.card);
		this.card = next;
		this.waiting = -1;
		if (!paged(next) || !wasPaged) {
			this.page = Page.MAIN;
			this.gift = false;
		}
		if (!this.heard) {
			this.said = IllagerWords.resolve(next.words());
			if (MCA) {
				DialogueCinematic.said(next.entityId(), this.said);
			}
		}
		this.heard = false;
		if (MCA) {
			DialogueCinematic.cue(Minecraft.getInstance(), next.entityId(), Speaker.VILLAGER);
		}
		rebuildWidgets();
	}

	/** Whether the card is MCA's pages (Talk, Interact, Trade), not a list standing straight in the column. */
	private static boolean paged(IllagerCard card) {
		for (String slot : card.slots()) {
			if (!slot.equals(IllagerCard.LIST) && !slot.equals(IllagerCard.BACK)) {
				return true;
			}
		}
		return false;
	}

	private boolean any(String... slots) {
		for (String slot : this.card.slots()) {
			for (String s : slots) {
				if (slot.equals(s)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.column.clear();
		List<Button> rows = new ArrayList<>();
		Button arrow = null;
		if (!paged(this.card)) {
			for (int i = 0; i < this.card.options().size(); i++) {
				if (this.card.slots().get(i).equals(IllagerCard.BACK)) {
					arrow = answer(Component.literal("<<"), i);
				} else {
					rows.add(answer(this.card.options().get(i), i));
				}
			}
		} else if (this.page == Page.MAIN) {
			if (any(IllagerCard.TALK)) {
				rows.add(Button.builder(Component.translatable("illager_talk.jaymoskingdoms.screen.talk"), b -> turn(Page.TALK)).build());
			}
			if (any(IllagerCard.INTERACT, IllagerCard.GIFT)) {
				rows.add(Button.builder(Component.translatable("illager_talk.jaymoskingdoms.screen.interact"), b -> turn(Page.INTERACT)).build());
			}
			rows.addAll(slotted(IllagerCard.MAIN));
		} else {
			arrow = Button.builder(Component.literal("<<"), b -> {
				if (this.gift) {
					this.gift = false;
				} else {
					this.page = Page.MAIN;
				}
				rebuildWidgets();
			}).build();
			if (this.page == Page.INTERACT) {
				for (int i = 0; i < this.card.options().size(); i++) {
					if (this.card.slots().get(i).equals(IllagerCard.GIFT)) {
						Button b = Button.builder(this.card.options().get(i), btn -> {
							this.gift = true;
							rebuildWidgets();
						}).build();
						b.active = this.card.enabled().get(i);
						rows.add(b);
					}
				}
			}
			rows.addAll(slotted(this.page == Page.TALK ? IllagerCard.TALK : IllagerCard.INTERACT));
		}
		// as wide as the longest label needs, never narrower than MCA's, never past a third of the screen
		int cap = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, this.width / 3));
		int want = MIN_WIDTH;
		for (Button b : rows) {
			want = Math.max(want, this.font.width(b.getMessage()) + LABEL_PAD);
		}
		int wide = Math.min(want, cap), right = this.width - RIGHT_GAP, left = right - wide;
		int y = TOP;
		for (Button b : rows) {
			b.setRectangle(wide, BUTTON_H, left, y);
			if (this.font.width(b.getMessage()) > wide - 4) {
				b.setTooltip(Tooltip.create(b.getMessage()));
			}
			if (this.gift || this.waiting >= 0) {
				b.active = false;
			}
			addRenderableWidget(b);
			this.column.add(b);
			y += ROW;
		}
		if (arrow != null) {
			arrow.setRectangle(ARROW, ARROW, left - PANEL_PAD - ARROW_GAP - ARROW, TOP);
			arrow.active = arrow.active && this.waiting < 0;
			addRenderableWidget(arrow);
			this.column.add(arrow);
		}
	}

	/** The card's answers in {@code slot}, as buttons. */
	private List<Button> slotted(String slot) {
		List<Button> list = new ArrayList<>();
		for (int i = 0; i < this.card.options().size(); i++) {
			if (this.card.slots().get(i).equals(slot)) {
				list.add(answer(this.card.options().get(i), i));
			}
		}
		return list;
	}

	private Button answer(Component label, int option) {
		Button b = Button.builder(label, btn -> pick(option)).build();
		b.active = this.card.enabled().get(option);
		return b;
	}

	private void turn(Page to) {
		this.page = to;
		rebuildWidgets();
	}

	/** Answers with option {@code option}: the column greys until their reply, or the screen shuts if it ends the talk. */
	private void pick(int option) {
		ClientPlayNetworking.send(new TalkNet.Answer(this.card.entityId(), option));
		this.heard = false;
		if (MCA) {
			DialogueCinematic.cue(Minecraft.getInstance(), this.card.entityId(), Speaker.PLAYER);
		}
		if (this.card.ends().get(option)) {
			shut();
			return;
		}
		this.waiting = 0;
		rebuildWidgets();
	}

	/** In gift mode: give what's held now, if anything. */
	private void give() {
		Player self = Minecraft.getInstance().player;
		if (self == null || self.getMainHandItem().isEmpty() || this.waiting >= 0) {
			return;
		}
		for (int i = 0; i < this.card.slots().size(); i++) {
			if (this.card.slots().get(i).equals(IllagerCard.GIFT)) {
				pick(i);
				return;
			}
		}
	}

	@Override
	public void tick() {
		if (this.waiting >= 0 && ++this.waiting > WAIT_TICKS) {
			shut();
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		// (as MCA's: the scroll wheel walks the hotbar, so a gift can be picked with the screen up)
		Player self = Minecraft.getInstance().player;
		if (self != null && dy != 0.0) {
			int slot = self.getInventory().getSelectedSlot();
			self.getInventory().setSelectedSlot(dy < 0.0 ? (slot + 1) % 9 : (slot + 8) % 9);
		}
		return super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (this.gift && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			give();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.gift && event.key() == InputConstants.KEY_ESCAPE) {
			this.gift = false;
			rebuildWidgets();
			return true;
		}
		if (this.gift && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			give();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (this.waiting < 0) {
			// (no answer on its way: tell them you've gone; one on its way already closed the menu)
			ClientPlayNetworking.send(new TalkNet.Answer(this.card.entityId(), -1));
		}
		shut();
	}

	private void shut() {
		if (this.waiting >= 0) {
			shutOn = this.card.entityId();
			shutUntil = System.currentTimeMillis() + SHUT_MS;
		}
		super.onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// (none, as MCA's: the world and the one you're talking to stay in view)
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// the column comes up once they've finished saying their line (DialogueCinematic), as on a villager's screen
		boolean up = DialogueCinematic.answersUp();
		for (AbstractWidget w : this.column) {
			w.visible = up;
		}
		if (up) {
			panel(graphics);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		info(graphics);
		hearts(graphics, mouseX, mouseY);
		if (!MCA) {
			box(graphics);
		}
	}

	/** The dark purple panel behind the column (not the back arrow), as {@code McaButtonColumnMixin} draws MCA's. */
	private void panel(GuiGraphicsExtractor g) {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
		for (AbstractWidget w : this.column) {
			if (w.getWidth() > ARROW) {
				x0 = Math.min(x0, w.getX());
				y0 = Math.min(y0, w.getY());
				x1 = Math.max(x1, w.getRight());
				y1 = Math.max(y1, w.getBottom());
			}
		}
		if (x0 > x1) {
			return;
		}
		x0 -= PANEL_PAD;
		y0 -= PANEL_PAD;
		x1 += PANEL_PAD;
		y1 += PANEL_PAD;
		g.fill(x0, y0, x1, y1, PANEL_FILL);
		g.fillGradient(x0, y0, x0 + 1, y1, BORDER_TOP, BORDER_BOTTOM);
		g.fillGradient(x1 - 1, y0, x1, y1, BORDER_TOP, BORDER_BOTTOM);
		g.fill(x0, y0, x1, y0 + 1, BORDER_TOP);
		g.fill(x0, y1 - 1, x1, y1, BORDER_BOTTOM);
	}

	/** MCA's lines down the top left: name (in gift mode, how to give and what you hold), what they are, mood, nature, regard. */
	private void info(GuiGraphicsExtractor g) {
		List<Component> lines = new ArrayList<>();
		if (this.gift) {
			lines.add(Component.translatable("illager_talk.jaymoskingdoms.screen.give"));
			Player self = Minecraft.getInstance().player;
			ItemStack held = self == null ? ItemStack.EMPTY : self.getMainHandItem();
			lines.add(held.isEmpty() ? Component.translatable("illager_talk.jaymoskingdoms.screen.holding_none")
				: Component.translatable("illager_talk.jaymoskingdoms.screen.holding", held.getHoverName()));
		} else {
			lines.add(IntroNames.hide(this.card.name()));
			if (!this.card.kind().getString().equals(this.card.name().getString())) {
				lines.add(this.card.kind());
			}
		}
		int step = Math.max(0, Math.min(MOOD_COLORS.length - 1, this.card.moodStep()));
		lines.add(Component.translatable("illager_talk.jaymoskingdoms.screen.mood", this.card.mood()).withStyle(MOOD_COLORS[step]));
		lines.add(Component.translatable("illager_talk.jaymoskingdoms.screen.nature", this.card.nature()).withStyle(ChatFormatting.WHITE));
		lines.add(Component.translatable("illager_talk.jaymoskingdoms.screen.attitude", this.card.attitude()).withStyle(ChatFormatting.WHITE));
		int y = INFO_Y;
		for (Component line : lines) {
			tooltip(g, List.of(line), INFO_X, y);
			y += INFO_STEP;
		}
	}

	/** MCA's heart: red, black when they bear you ill will, gold at the most; pointing at it shows how many. */
	private void hearts(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int hearts = this.card.hearts();
		if (MCA) {
			g.pose().pushMatrix();
			g.pose().scale(ICON_SCALE, ICON_SCALE);
			g.blit(RenderPipelines.GUI_TEXTURED, MCA_ICONS, (int) (HEART_X / ICON_SCALE), (int) (HEART_Y / ICON_SCALE),
				hearts < 0 ? BLACK_U : hearts >= GOLD_HEARTS ? GOLD_U : RED_U, 0, 16, 16, 256, 256);
			g.pose().popMatrix();
		} else {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, hearts < 0 ? HEART_BLACK : hearts >= GOLD_HEARTS ? HEART_GOLD : HEART, HEART_X, HEART_Y, 18, 18);
		}
		if (mouseX > HEART_X && mouseX < HEART_X + HEART_HOVER && mouseY > HEART_Y && mouseY < HEART_Y + HEART_HOVER) {
			tooltip(g, List.of(Component.translatable("illager_talk.jaymoskingdoms.screen.hearts", hearts)), HEART_X + 16, HEART_Y + 20);
		}
	}

	/** A tooltip-style label at {@code x, y}, as MCA draws its lines. */
	private void tooltip(GuiGraphicsExtractor g, List<Component> text, int x, int y) {
		List<ClientTooltipComponent> parts = new ArrayList<>();
		for (Component c : text) {
			parts.add(ClientTooltipComponent.create(c.getVisualOrderText()));
		}
		g.tooltip(this.font, parts, x, y, DefaultTooltipPositioner.INSTANCE, null, false);
	}

	/** Without MCA (no talk camera): their name and last words in a box low on the left, as the camera's box. */
	private void box(GuiGraphicsExtractor g) {
		List<FormattedCharSequence> rows = this.font.split(this.said, BOX_WIDTH - BOX_PAD * 2);
		Component name = IntroNames.hide(this.card.name());
		int rowHeight = this.font.lineHeight + 1;
		int textWidth = this.font.width(name);
		for (FormattedCharSequence row : rows) {
			textWidth = Math.max(textWidth, this.font.width(row));
		}
		int x0 = BOX_MARGIN, x1 = x0 + textWidth + BOX_PAD * 2;
		int y1 = this.height - BOX_FOOT, y0 = y1 - (BOX_PAD * 2 + (rows.size() + 1) * rowHeight + 2);
		g.fill(x0, y0, x1, y1, PANEL_FILL);
		g.fillGradient(x0, y0, x0 + 1, y1, BORDER_TOP, BORDER_BOTTOM);
		g.fillGradient(x1 - 1, y0, x1, y1, BORDER_TOP, BORDER_BOTTOM);
		g.fill(x0, y0, x1, y0 + 1, BORDER_TOP);
		g.fill(x0, y1 - 1, x1, y1, BORDER_BOTTOM);
		int y = y0 + BOX_PAD;
		g.text(this.font, name.getVisualOrderText(), x0 + BOX_PAD, y, NAME_COLOR);
		y += rowHeight + 2;
		for (FormattedCharSequence row : rows) {
			g.text(this.font, row, x0 + BOX_PAD, y, 0xFFFFFFFF);
			y += rowHeight;
		}
	}
}
