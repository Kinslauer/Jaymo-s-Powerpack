package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Retaking;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The character menu's Story tab ({@link MenuCompat}): the Retaking ({@link Retaking}), for every player. The five acts
 * down the left (done, yours, or not reached, which show no name); on the right the picked act's milestones as plain
 * goals, ticked when done. It says what to do, never what it means. Asks the server when it opens.
 * <p>
 * Below the acts, the player's own road if their power has one ({@link RoadCompat}: the Z Fighter's sagas), its goals the
 * same plain way, with a button to its own screen.
 */
final class RetakingScreen extends Screen {
	static final int MAX_W = 360, MAX_H = 210, LIST_W = 118, LIST_ROW = 19, HEADER = 34, GOAL_ROW = 4, ROAD_GAP = 6, ROAD_BUTTON_W = 96;
	static final ItemStack ROAD_ICON = new ItemStack(Items.COMPASS);
	static final ItemStack[] ICONS = {new ItemStack(Items.CAMPFIRE), new ItemStack(Items.CHISELED_STONE_BRICKS), new ItemStack(Items.FIREWORK_ROCKET),
		new ItemStack(Items.SOUL_LANTERN), new ItemStack(Items.NETHER_STAR)};

	/** The server's last answer. */
	static Retaking.@Nullable State state;

	private Retaking.@Nullable Act picked;
	/** The road row picked instead of an act. */
	private boolean roadPicked;
	private @Nullable Button roadButton;

	RetakingScreen() {
		super(Component.translatable("menu.jaymoskingdoms.story.title"));
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Retaking.State.TYPE, (payload, context) -> state = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state = null);
	}

	@Override
	protected void init() {
		if (ClientPlayNetworking.canSend(Retaking.Ask.TYPE)) {
			ClientPlayNetworking.send(new Retaking.Ask());
		}
		this.roadButton = null;
		if (RoadCompat.title() != null) {
			int px = left() + LIST_W, pw = panelW() - LIST_W;
			this.roadButton = addRenderableWidget(Button.builder(Component.translatable("menu.jaymoskingdoms.story.road_open"), b -> RoadCompat.open(this))
				.bounds(px + pw - ROAD_BUTTON_W - 8, top() + panelH() - 22, ROAD_BUTTON_W, 16).build());
			this.roadButton.visible = false;
		}
	}

	/** The road's row, under the acts. */
	private int roadY() {
		return rowY(Retaking.Act.values().length) + ROAD_GAP;
	}

	private boolean overRoad(int mouseX, int mouseY) {
		return this.roadButton != null && Ui.over(mouseX, mouseY, left() + 4, roadY(), LIST_W - 8, LIST_ROW - 2);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int panelH() {
		return Math.min(MAX_H, this.height - 8);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	private int rowY(int index) {
		return top() + HEADER + index * LIST_ROW;
	}

	private Retaking.Act current() {
		return Retaking.actAt(state == null ? 0 : state.level());
	}

	/** An act is reached once the one before it is closed. */
	private boolean reached(Retaking.Act act) {
		return state != null && state.level() >= act.ordinal();
	}

	private boolean closed(Retaking.Act act) {
		return state != null && state.level() >= act.rung();
	}

	private Retaking.@Nullable Act rowAt(int mouseX, int mouseY) {
		Retaking.Act[] all = Retaking.Act.values();
		for (int i = 0; i < all.length; i++) {
			if (Ui.over(mouseX, mouseY, left() + 4, rowY(i), LIST_W - 8, LIST_ROW - 2)) {
				return all[i];
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Retaking.Act row = rowAt((int)event.x(), (int)event.y());
		if (overRoad((int)event.x(), (int)event.y())) {
			this.roadPicked = true;
			this.picked = null;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		if (row != null) {
			this.roadPicked = false;
			this.picked = row;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private Component actName(Retaking.Act act) {
		return reached(act) ? Component.translatable("retaking.jaymoskingdoms.act." + act.key) : Component.translatable("retaking.jaymoskingdoms.act.unknown");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW(), h = panelH();
		Ui.panel(graphics, x, y, w, h, Ui.GOLD);
		Retaking.Act current = current();
		Retaking.Act[] all = Retaking.Act.values();

		// the acts down the left
		Ui.line(graphics, this.font, this.title, x + 8, y + 8, LIST_W - 12, Ui.TEXT);
		Ui.line(graphics, this.font, state == null ? Component.translatable("menu.jaymoskingdoms.story.asking")
			: Component.translatable("menu.jaymoskingdoms.story.sub", current.ordinal() + 1), x + 8, y + 19, LIST_W - 12, Ui.DIM);
		graphics.fill(x + LIST_W - 1, y + 4, x + LIST_W, y + h - 4, Ui.ROW);
		Retaking.Act hover = rowAt(mouseX, mouseY);
		Retaking.Act shown = hover != null ? hover : this.picked != null ? this.picked : current;
		for (int i = 0; i < all.length; i++) {
			Retaking.Act act = all[i];
			int ry = rowY(i);
			graphics.fill(x + 4, ry, x + LIST_W - 4, ry + LIST_ROW - 2,
				act == this.picked ? Ui.ROW_PICKED : act == hover ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW_ALT : 0x0CFFFFFF);
			if (act == current && state != null) {
				graphics.fill(x + 4, ry, x + 6, ry + LIST_ROW - 2, Ui.GOLD);
			}
			if (reached(act)) {
				graphics.item(ICONS[i], x + 8, ry + 1);
			}
			int color = !reached(act) ? Ui.HINT : closed(act) ? Ui.GOOD : act == shown ? Ui.GOLD : Ui.TEXT;
			Ui.line(graphics, this.font, actName(act), x + 27, ry + 5, LIST_W - 34, color);
		}

		// the road, under the acts
		boolean roadHover = overRoad(mouseX, mouseY);
		boolean roadShown = this.roadButton != null && (roadHover || this.roadPicked && hover == null);
		if (this.roadButton != null) {
			int ry = roadY();
			graphics.fill(x + 4, ry, x + LIST_W - 4, ry + LIST_ROW - 2, this.roadPicked ? Ui.ROW_PICKED : roadHover ? Ui.ROW_HOVER : Ui.ROW_ALT);
			graphics.item(ROAD_ICON, x + 8, ry + 1);
			Component road = RoadCompat.title();
			Ui.line(graphics, this.font, road == null ? Component.empty() : road, x + 27, ry + 5, LIST_W - 34, roadShown ? Ui.GOLD : Ui.TEXT);
			this.roadButton.visible = roadShown;
			if (roadShown) {
				road(graphics, road, x + LIST_W, y, w - LIST_W, h);
				super.extractRenderState(graphics, mouseX, mouseY, a);
				return;
			}
		}

		// the picked act's goals
		int px = x + LIST_W, pw = w - LIST_W;
		Ui.line(graphics, this.font, Component.translatable("menu.jaymoskingdoms.story.act", shown.ordinal() + 1, actName(shown)), px + 10, y + 8, pw - 20,
			reached(shown) ? Ui.GOLD : Ui.DIM);
		Ui.line(graphics, this.font, Component.translatable(closed(shown) ? "menu.jaymoskingdoms.story.closed"
			: reached(shown) ? "menu.jaymoskingdoms.story.open" : "menu.jaymoskingdoms.story.locked"), px + 10, y + 19, pw - 20,
			closed(shown) ? Ui.GOOD : reached(shown) ? Ui.DIM : Ui.BAD);
		Ui.rule(graphics, px + 6, px + w - LIST_W - 6, y + HEADER - 4);
		int ry = y + HEADER, bottom = y + h - 6, textW = pw - 34;
		if (!reached(shown)) {
			for (FormattedCharSequence line : this.font.split(Component.translatable("menu.jaymoskingdoms.story.not_yet"), pw - 20)) {
				graphics.text(this.font, line, px + 10, ry + 2, Ui.DIM, false);
				ry += Ui.LINE + 1;
			}
		} else {
			List<Retaking.Milestone> goals = new ArrayList<>();
			for (Retaking.Milestone m : Retaking.MILESTONES) {
				if (m.act() == shown) {
					goals.add(m);
				}
			}
			int n = 0;
			for (Retaking.Milestone m : goals) {
				boolean done = state != null && state.done().contains(m.name());
				Component goal = m.name().equals("errands") && !done && state != null
					? Component.translatable("retaking.jaymoskingdoms.goal.errands_count", Math.min(state.errands(), Retaking.ERRANDS), Retaking.ERRANDS)
					: Component.translatable("retaking.jaymoskingdoms.goal." + m.name());
				List<FormattedCharSequence> split = this.font.split(goal, textW);
				int rh = split.size() * Ui.LINE + GOAL_ROW;
				if (ry + rh > bottom) {
					break;
				}
				Ui.row(graphics, px + 6, ry, px + pw - 6, ry + rh - 1, n++, false);
				graphics.text(this.font, Component.literal(done ? "✔" : "○"), px + 11, ry + 2, done ? Ui.GOOD : Ui.DIM, false);
				for (int l = 0; l < split.size(); l++) {
					graphics.text(this.font, split.get(l), px + 24, ry + 2 + l * Ui.LINE, done ? Ui.DIM : Ui.TEXT, false);
				}
				ry += rh;
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The road's goals, the acts' way: a tick when done, a ring when not. */
	private void road(GuiGraphicsExtractor graphics, @Nullable Component title, int px, int y, int pw, int h) {
		Ui.line(graphics, this.font, title == null ? Component.empty() : title, px + 10, y + 8, pw - 20, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("menu.jaymoskingdoms.story.road_sub"), px + 10, y + 19, pw - 20, Ui.DIM);
		Ui.rule(graphics, px + 6, px + pw - 6, y + HEADER - 4);
		int ry = y + HEADER, bottom = y + h - 26, textW = pw - 34, n = 0;
		for (java.util.Map.Entry<Boolean, Component> goal : RoadCompat.goals()) {
			boolean done = goal.getKey();
			List<FormattedCharSequence> split = this.font.split(goal.getValue(), textW);
			int rh = split.size() * Ui.LINE + GOAL_ROW;
			if (ry + rh > bottom) {
				break;
			}
			Ui.row(graphics, px + 6, ry, px + pw - 6, ry + rh - 1, n++, false);
			graphics.text(this.font, Component.literal(done ? "✔" : "○"), px + 11, ry + 2, done ? Ui.GOOD : Ui.DIM, false);
			for (int l = 0; l < split.size(); l++) {
				graphics.text(this.font, split.get(l), px + 24, ry + 2 + l * Ui.LINE, done ? Ui.DIM : Ui.TEXT, false);
			}
			ry += rh;
		}
	}
}
