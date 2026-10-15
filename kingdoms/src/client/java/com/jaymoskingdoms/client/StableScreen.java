package com.jaymoskingdoms.client;

import com.jaymoskingdoms.StableNet;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Equine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * (0.5.47) A stablehand's or farrier's screen: your horses (board, sell, shoe), the ones you keep here (take out for
 * the fee), today's horses for sale, the tack shop. A long list scrolls. The server decides everything; a button is
 * sent back and the screen comes again with what happened written under the list. (0.7.40) Restyled like the H screen:
 * striped rows that light up under the mouse, and a box under the list with the hovered row in full (or what happened).
 */
final class StableScreen extends Screen {
	static final int PANEL_W = 360, PAD = 8, HEADER = 34, FOOTER = 24, DETAIL_LINES = 3, HEADING_H = 14, ROW_H = 24, ICON_W = 28, EMPTY_H = 12, SCROLL_STEP = 12, BUTTON_H = 16;

	/** One line of the list: a heading, a section's "none" line, or a row; {@code y} from the list's top. */
	private record Line(int y, int h, Component heading, StableNet.@Nullable Row row) {
	}

	/** A row's button and where it sits before scrolling. */
	private record Placed(Button button, int y) {
	}

	/** How a drawn horse is turned (degrees): side on, head to the left. */
	static final float HORSE_TURN = 270.0F;
	/** The horses drawn so far, by coat (kept between screens; a few dozen at most). */
	private static final Map<Equine, @Nullable AbstractHorse> MODELS = new HashMap<>();

	private final StableNet.Open open;
	private final List<Line> lines = new ArrayList<>();
	private final List<Placed> placed = new ArrayList<>();
	private int contentH;
	private int scroll;
	/** Where the mouse was when the screen came: until it moves, the box shows what just happened, not the row under it. */
	private int firstX = Integer.MIN_VALUE, firstY;
	private boolean moved;

	StableScreen(StableNet.Open open, int scroll) {
		super(open.title());
		this.open = open;
		this.scroll = scroll;
	}

	int scroll() {
		return this.scroll;
	}

	int workerId() {
		return this.open.workerId();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelHeight() {
		int want = HEADER + contentH + detailH() + FOOTER;
		return Math.min(this.height - 8, want);
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelHeight()) / 2);
	}

	/** The box under the list: the hovered row in full, or what just happened. */
	private int detailH() {
		return 10 + DETAIL_LINES * Ui.LINE + 4;
	}

	private int viewTop() {
		return top() + HEADER;
	}

	private int viewHeight() {
		return panelHeight() - HEADER - detailH() - FOOTER;
	}

	private int maxScroll() {
		return Math.max(0, contentH - viewHeight());
	}

	@Override
	protected void init() {
		lines.clear();
		placed.clear();
		int y = 0;
		for (StableNet.Section s : open.sections()) {
			lines.add(new Line(y, HEADING_H, s.heading(), null));
			y += HEADING_H;
			if (s.rows().isEmpty() && !s.empty().getString().isEmpty()) {
				lines.add(new Line(y, EMPTY_H, s.empty(), null));
				y += EMPTY_H;
			}
			for (StableNet.Row r : s.rows()) {
				lines.add(new Line(y, ROW_H, Component.empty(), r));
				y += ROW_H;
			}
			y += 4;
		}
		contentH = y;
		scroll = Math.min(scroll, maxScroll());
		int right = left() + PANEL_W - PAD;
		for (Line line : lines) {
			if (line.row() == null) {
				continue;
			}
			int bx = right;
			List<StableNet.Button> buttons = line.row().buttons();
			for (int i = buttons.size() - 1; i >= 0; i--) {
				StableNet.Button b = buttons.get(i);
				int w = this.font.width(b.text()) + 12;
				bx -= w;
				Button button = Button.builder(b.text(), x -> ClientPlayNetworking.send(new StableNet.Act(open.workerId(), b.action(), b.arg())))
					.bounds(bx, 0, w, BUTTON_H).build();
				button.active = b.active();
				addRenderableWidget(button);
				placed.add(new Placed(button, line.y() + (ROW_H - BUTTON_H) / 2));
				bx -= 3;
			}
		}
		place();
		int bottom = top() + panelHeight() - FOOTER + 4, edge = left() + PANEL_W - 6;
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.stable.quests"),
			b -> ClientPlayNetworking.send(new StableNet.Act(open.workerId(), StableNet.QUESTS, 0))).bounds(left() + 6, bottom, 70, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(edge - 60, bottom, 60, 16).build());
	}

	/** Moves the rows' buttons with the scroll; those not wholly in view are hidden. */
	private void place() {
		int top = viewTop(), view = viewHeight();
		for (Placed p : placed) {
			int y = p.y() - scroll;
			p.button().setY(top + y);
			p.button().visible = y >= 0 && y + BUTTON_H <= view;
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int was = scroll;
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int)Math.round(scrollY * SCROLL_STEP)));
		if (scroll != was) {
			place();
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), h = panelHeight();
		Ui.panel(graphics, x, y, PANEL_W, h, Ui.GOLD);
		Ui.line(graphics, this.font, this.title, x + 9, y + 7, PANEL_W - 18, Ui.GOLD);
		Ui.line(graphics, this.font, open.subtitle(), x + 9, y + 18, PANEL_W - 18, Ui.DIM);
		Ui.rule(graphics, x + 6, x + PANEL_W - 6, y + HEADER - 4);
		int top = viewTop(), view = viewHeight();
		ItemStack hovered = ItemStack.EMPTY;
		StableNet.Row hoveredRow = null;
		boolean inView = mouseY >= top && mouseY < top + view;
		graphics.enableScissor(x + 1, top, x + PANEL_W - 1, top + view);
		int index = 0;
		for (Line line : lines) {
			int ly = top + line.y() - scroll;
			StableNet.Row r = line.row();
			if (r == null) {
				index = 0;
			}
			if (ly + line.h() < top || ly > top + view) {
				index++;
				continue;
			}
			if (r == null) {
				boolean heading = line.h() == HEADING_H;
				graphics.text(this.font, line.heading(), x + PAD + (heading ? 0 : 6), ly + 3, heading ? Ui.GOLD : Ui.DIM);
				if (heading) {
					Ui.rule(graphics, x + PAD, x + PANEL_W - PAD, ly + HEADING_H - 2);
				}
				continue;
			}
			boolean hover = inView && Ui.over(mouseX, mouseY, x + PAD - 2, ly + 1, PANEL_W - PAD * 2 + 4, ROW_H - 2);
			Ui.row(graphics, x + PAD - 2, ly + 1, x + PANEL_W - PAD + 2, ly + ROW_H - 1, index++, hover);
			if (hover) {
				hoveredRow = r;
			}
			AbstractHorse model = r.horse().map(this::model).orElse(null);
			if (model != null && drawHorse(graphics, model, x + PAD, ly + 1, x + PAD + ICON_W, ly + ROW_H - 1)) {
				// drawn
			} else {
				graphics.item(r.icon(), x + PAD + 6, ly + 4);
				graphics.itemDecorations(this.font, r.icon(), x + PAD + 6, ly + 4);
				if (inView && Ui.over(mouseX, mouseY, x + PAD + 6, ly + 4, 16, 16)) {
					hovered = r.icon();
				}
			}
			int textX = x + PAD + ICON_W + 4;
			int buttonsW = 0;
			for (StableNet.Button b : r.buttons()) {
				buttonsW += this.font.width(b.text()) + 15;
			}
			int textW = x + PANEL_W - PAD - buttonsW - textX - 2;
			Ui.line(graphics, this.font, r.label(), textX, ly + 3, textW, Ui.TEXT);
			Ui.line(graphics, this.font, r.detail(), textX, ly + 13, textW, Ui.DIM);
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, x + PANEL_W - 4, top, view, view, contentH, scroll);
		// the box under the list: the hovered row in full, or what just happened (until the mouse moves off the button pressed)
		if (firstX == Integer.MIN_VALUE) {
			firstX = mouseX;
			firstY = mouseY;
		}
		moved |= Math.abs(mouseX - firstX) + Math.abs(mouseY - firstY) > 4;
		if (hoveredRow != null && (moved || open.note().getString().isEmpty())) {
			Ui.detail(graphics, this.font, x, top + view, PANEL_W, DETAIL_LINES, hoveredRow.label(), Ui.TEXT, List.of(hoveredRow.detail().copy().withColor(Ui.DIM)));
		} else if (!open.note().getString().isEmpty()) {
			Ui.detail(graphics, this.font, x, top + view, PANEL_W, DETAIL_LINES, Component.empty(), Ui.TEXT, List.of(open.note().copy().withColor(Ui.TEXT)));
		} else {
			Ui.detail(graphics, this.font, x, top + view, PANEL_W, DETAIL_LINES, Component.empty(), Ui.TEXT,
				List.of(Component.translatable("gui.jaymoskingdoms.stable.hover").withColor(Ui.HINT)));
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}

	/** A horse of {@code e} to draw in its coat (one per coat, made once; null if it can't be made). */
	private @Nullable AbstractHorse model(Equine e) {
		return MODELS.computeIfAbsent(e, k -> {
			if (this.minecraft == null || this.minecraft.level == null) {
				return null;
			}
			AbstractHorse horse = k.breed().elephant() ? Equestrian.WAR_ELEPHANT.create(this.minecraft.level, EntitySpawnReason.LOAD)
				: EntityTypes.HORSE.create(this.minecraft.level, EntitySpawnReason.LOAD);
			if (horse != null) {
				// (never in the world: an id no real entity has, which rendering asks for)
				horse.setId(-1000 - MODELS.size());
				horse.setAttached(Equestrian.EQUINE, k);
			}
			return horse;
		});
	}

	/** {@code horse} side on, standing in the box; false (the icon instead) if it couldn't be drawn. */
	private static boolean drawHorse(GuiGraphicsExtractor graphics, AbstractHorse horse, int x0, int y0, int x1, int y1) {
		EntityRenderState state;
		try {
			EntityRenderer<? super AbstractHorse, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(horse);
			state = renderer.createRenderState(horse, 1.0F);
		} catch (RuntimeException e) {
			return false;
		}
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = HORSE_TURN;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		// (side on it's longer than its box is wide, and its head stands above the box)
		float size = Math.min((x1 - x0) / Math.max(1.0F, state.boundingBoxWidth * 1.9F), (y1 - y0) / Math.max(1.0F, state.boundingBoxHeight * 1.35F));
		Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI);
		graphics.entity(state, size, new Vector3f(0.0F, state.boundingBoxHeight * 0.62F, 0.0F), rotation, null, x0, y0, x1, y1);
		return true;
	}
}
