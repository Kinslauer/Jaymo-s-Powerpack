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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * (0.5.45) A stablehand's or farrier's screen: your horses (board, sell, shoe), the ones you keep here (take out for
 * the fee), today's horses for sale, the tack shop. A long list scrolls. The server decides everything; a button is
 * sent back and the screen comes again with what happened written under the list.
 */
final class StableScreen extends Screen {
	static final int PANEL_W = 360, PAD = 8, HEADER = 34, FOOTER = 26, HEADING_H = 14, ROW_H = 24, ICON_W = 28, EMPTY_H = 12, SCROLL_STEP = 12, BUTTON_H = 16;

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

	private int noteLines() {
		return open.note().getString().isEmpty() ? 0 : Math.min(3, this.font.split(open.note(), PANEL_W - PAD * 2).size());
	}

	private int panelHeight() {
		int want = HEADER + contentH + noteLines() * 10 + 4 + FOOTER;
		return Math.min(this.height - 8, want);
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelHeight()) / 2);
	}

	private int viewTop() {
		return top() + HEADER;
	}

	private int viewHeight() {
		return panelHeight() - HEADER - noteLines() * 10 - 4 - FOOTER;
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
		int bottom = top() + panelHeight() - FOOTER + 4;
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.stable.quests"),
			b -> ClientPlayNetworking.send(new StableNet.Act(open.workerId(), StableNet.QUESTS, 0))).bounds(this.width / 2 - 84, bottom, 80, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(this.width / 2 + 4, bottom, 80, 18).build());
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
		graphics.fill(x, y, x + PANEL_W, y + h, QuestScreen.COLOR_PANEL);
		edge(graphics, x, y, PANEL_W, h, QuestScreen.COLOR_EDGE);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 7, QuestScreen.COLOR_GOLD);
		graphics.centeredText(this.font, open.subtitle(), this.width / 2, y + 19, QuestScreen.COLOR_DIM);
		int top = viewTop(), view = viewHeight();
		ItemStack hovered = ItemStack.EMPTY;
		graphics.enableScissor(x + 1, top, x + PANEL_W - 1, top + view);
		for (Line line : lines) {
			int ly = top + line.y() - scroll;
			if (ly + line.h() < top || ly > top + view) {
				continue;
			}
			StableNet.Row r = line.row();
			if (r == null) {
				boolean heading = line.h() == HEADING_H;
				graphics.text(this.font, line.heading(), x + PAD + (heading ? 0 : 6), ly + 3, heading ? QuestScreen.COLOR_GOLD : QuestScreen.COLOR_DIM);
				if (heading) {
					graphics.fill(x + PAD, ly + HEADING_H - 2, x + PANEL_W - PAD, ly + HEADING_H - 1, 0x60B08A3C);
				}
				continue;
			}
			graphics.fill(x + PAD - 2, ly + 1, x + PANEL_W - PAD + 2, ly + ROW_H - 1, QuestScreen.COLOR_CARD);
			AbstractHorse model = r.horse().map(this::model).orElse(null);
			if (model != null && drawHorse(graphics, model, x + PAD, ly + 1, x + PAD + ICON_W, ly + ROW_H - 1)) {
				// drawn
			} else {
				graphics.item(r.icon(), x + PAD + 6, ly + 4);
				graphics.itemDecorations(this.font, r.icon(), x + PAD + 6, ly + 4);
				if (mouseX >= x + PAD + 6 && mouseX < x + PAD + 22 && mouseY >= ly + 4 && mouseY < ly + 20 && mouseY >= top && mouseY < top + view) {
					hovered = r.icon();
				}
			}
			int textX = x + PAD + ICON_W + 4;
			int buttonsW = 0;
			for (StableNet.Button b : r.buttons()) {
				buttonsW += this.font.width(b.text()) + 15;
			}
			int textW = x + PANEL_W - PAD - buttonsW - textX - 2;
			graphics.text(this.font, first(r.label(), textW), textX, ly + 3, QuestScreen.COLOR_TEXT);
			graphics.text(this.font, first(r.detail(), textW), textX, ly + 13, QuestScreen.COLOR_DIM);
		}
		graphics.disableScissor();
		if (maxScroll() > 0) {
			// a thumb on the right edge
			int barH = Math.max(12, view * view / contentH);
			int barY = top + (view - barH) * scroll / maxScroll();
			graphics.fill(x + PANEL_W - 4, barY, x + PANEL_W - 2, barY + barH, QuestScreen.COLOR_EDGE);
		}
		int ny = top + view + 2;
		List<FormattedCharSequence> note = this.font.split(open.note(), PANEL_W - PAD * 2);
		for (int i = 0; i < Math.min(3, note.size()); i++) {
			graphics.centeredText(this.font, note.get(i), this.width / 2, ny, QuestScreen.COLOR_TEXT);
			ny += 10;
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

	/** {@code text}'s first line within {@code width}. */
	private FormattedCharSequence first(Component text, int width) {
		List<FormattedCharSequence> split = this.font.split(text, Math.max(20, width));
		return split.isEmpty() ? FormattedCharSequence.EMPTY : split.getFirst();
	}

	private static void edge(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}
}
