package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.57) The heritage screen ({@link Heritage}), the step of character creation between MCA's destiny and the class,
 * in the class screen's look: every heritage down the left, and on the right the one you point at or picked: its free
 * attribute points, its gift and its flaw as striped rows, and a box at the bottom with its story. Pressing one picks
 * it; Choose seals it, for life. It can't be shut without picking.
 * (0.7.75) Rows wrap instead of being cut off, and the pane scrolls with the mouse wheel when it runs long; pointing at
 * it while it overflows scrolls it slowly on its own, to the end and back.
 * (0.7.106) An earned heritage not yet woken shows as "???" with a hint of how it's woken, and can't be picked. The rows
 * shrink to fit when there are more heritages than the panel is tall, dropping their icons when too small for them.
 * (0.7.184) The Blood Rite's mode is gone: an earned blood passes to your children ({@code Lineage}).
 */
final class HeritageScreen extends Screen {
	static final int MAX_W = 420, MAX_H = 300, LIST_W = 118, LIST_ROW = 19, MIN_ROW = 11, ICON_ROW = 17, HEADER = 34, FOOTER = 24;
	/** The heritage screen's accent: a deep teal, apart from the class screen's gold. */
	static final int ACCENT = 0xFF4FB8A8;
	/** After Choose, how long the heritage may take to come back before the screen is put up again (ms). */
	static final long CHOOSE_GRACE_MS = 3000L;
	/** Pixels the pane moves for one notch of the wheel; its own slow scroll on hover (pixels a second), and the pause at each end (ms). */
	static final int SCROLL_STEP = 12, AUTO_SPEED = 14;
	static final long AUTO_PAUSE_MS = 1500L;
	/** (0.7.80) The lead line under the story ("Your heritage gives..."): shown this long the first time the screen opens, fading out over the last of it (ms). */
	static final long LEAD_MS = 5000L, LEAD_FADE_MS = 1500L;

	private final boolean forced;
	private Heritage.@Nullable Kind picked;
	private @Nullable Button choose;
	/** How far down the right pane is scrolled, and for which heritage (it starts over for each one). */
	private int scroll;
	private Heritage.@Nullable Kind scrolled;
	private int contentH, viewH;
	/** The slow scroll on hover: when it began, and how far it had got (-1: not running). */
	private long autoFrom = -1L;

	/** The server asked for a heritage that can't be put off: the screen comes back whenever it's shut. */
	private static boolean owed;
	private static long quietUntil;
	/** When the lead line first showed (0: not yet); once gone it stays gone, whichever heritage or screen. */
	private static long leadFrom;

	HeritageScreen(boolean forced) {
		super(Component.translatable("gui.jaymoskingdoms.heritage.title"));
		this.forced = forced;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Heritage.Open.TYPE, (payload, context) -> {
			owed |= payload.forced();
			context.client().gui.setScreen(new HeritageScreen(payload.forced()));
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!owed || minecraft.player == null) {
				return;
			}
			if (Heritage.of(minecraft.player) != null) {
				owed = false;
			} else if (minecraft.gui.screen() == null && System.currentTimeMillis() > quietUntil) {
				minecraft.gui.setScreen(new HeritageScreen(true));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> owed = false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !this.forced;
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

	private int paneX() {
		return left() + LIST_W;
	}

	private int paneW() {
		return panelW() - LIST_W;
	}

	/** (0.7.106) A list row's height: as tall as {@link #LIST_ROW}, shrunk so every heritage fits. */
	private int rowH() {
		int fit = (panelH() - HEADER - 4) / Heritage.Kind.values().length;
		return Math.clamp(fit, MIN_ROW, LIST_ROW);
	}

	private int rowY(int index) {
		return top() + HEADER + index * rowH();
	}

	/** (0.7.106) An earned heritage the player hasn't woken: a "???" with a hint. */
	private static boolean hidden(Heritage.Kind kind) {
		Minecraft minecraft = Minecraft.getInstance();
		return kind.earned && (minecraft.player == null || !Heritage.unlocked(minecraft.player, kind) && Heritage.of(minecraft.player) != kind);
	}

	/** (0.7.106) Whether {@code kind} can be picked here: any that isn't hidden. */
	private boolean pickable(Heritage.Kind kind) {
		return !hidden(kind);
	}

	private static Component name(Heritage.Kind kind) {
		return hidden(kind) ? Component.translatable("gui.jaymoskingdoms.heritage.unknown") : Component.translatable("heritage.jaymoskingdoms." + kind.key());
	}

	private static boolean done() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && Heritage.of(minecraft.player) != null;
	}

	@Override
	protected void init() {
		if (done()) {
			onClose();
			return;
		}
		int y = top() + panelH() - FOOTER + 4;
		int right = paneX() + paneW() - 6;
		if (!this.forced) {
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.class.later"), b -> onClose()).bounds(right - 56, y, 56, 16).build());
			right -= 60;
		}
		this.choose = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.class.choose"), b -> {
			if (this.picked != null) {
				ClientPlayNetworking.send(new Heritage.Choose(this.picked.ordinal()));
				quietUntil = System.currentTimeMillis() + CHOOSE_GRACE_MS;
				onClose();
			}
		}).bounds(right - 120, y, 120, 16).build());
	}

	private Heritage.@Nullable Kind rowAt(int mouseX, int mouseY) {
		Heritage.Kind[] all = Heritage.Kind.values();
		int rh = rowH();
		for (int i = 0; i < all.length; i++) {
			int ry = rowY(i);
			if (ry + rh > top() + panelH() - 4) {
				break;
			}
			if (Ui.over(mouseX, mouseY, left() + 4, ry, LIST_W - 8, rh - 2)) {
				return all[i];
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Heritage.Kind row = rowAt((int)event.x(), (int)event.y());
		if (row != null && pickable(row)) {
			this.picked = row;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (done()) {
			onClose();
			return;
		}
		int x = left(), y = top(), w = panelW(), h = panelH();
		int accent = accent();
		Ui.panel(graphics, x, y, w, h, accent);
		list(graphics, mouseX, mouseY);
		Heritage.Kind hover = rowAt(mouseX, mouseY);
		Heritage.Kind kind = hover != null ? hover : this.picked != null ? this.picked : firstPickable();
		int px = paneX(), pw = paneW();

		// the header: its icon, its name, and whether it's yours yet
		boolean hidden = hidden(kind);
		if (!hidden) {
			graphics.item(new ItemStack(kind.icon), px + 8, y + 8);
		}
		Ui.line(graphics, this.font, name(kind), px + 28, y + 7, pw - 36, kind == this.picked ? accent : Ui.TEXT);
		Ui.line(graphics, this.font, Component.translatable(hidden ? "gui.jaymoskingdoms.heritage.earned"
			: kind == this.picked ? "gui.jaymoskingdoms.heritage.picked"
			: "gui.jaymoskingdoms.heritage.press"), px + 28, y + 18, pw - 36, Ui.DIM);
		Ui.rule(graphics, px + 6, px + pw - 6, y + HEADER - 4);

		// the free points, the gift (green) and the flaw (red), a row each, wrapped; then its story. All of it scrolls.
		if (kind != this.scrolled) {
			this.scrolled = kind;
			this.scroll = 0;
			this.autoFrom = -1L;
		}
		int textW = pw - 20;
		int viewTop = y + HEADER, viewBottom = y + h - FOOTER - 2;
		this.viewH = viewBottom - viewTop;
		List<Component> rows = hidden ? List.of(Component.translatable("heritage.jaymoskingdoms." + kind.key() + ".hint")) : kind.card();
		List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
		int contentH = 0;
		for (Component row : rows) {
			List<FormattedCharSequence> split = this.font.split(row, textW);
			wrapped.add(split);
			contentH += split.size() * Ui.LINE + 4;
		}
		List<Component> text = new ArrayList<>();
		text.add(Component.translatable(hidden ? "gui.jaymoskingdoms.heritage.earned_desc" : "heritage.jaymoskingdoms." + kind.key() + ".desc"));
		int lead = leadColor();
		if (this.picked == null && lead != 0) {
			text.add(Component.translatable("gui.jaymoskingdoms.heritage.lead").withColor(lead));
		}
		Component name = name(kind);
		int storyLines = Ui.detailLines(this.font, pw, name, text);
		int storyTop = contentH + 6;
		contentH = storyTop + 5 + storyLines * Ui.LINE + 4;
		this.contentH = contentH;
		autoScroll(Ui.over(mouseX, mouseY, px, viewTop, pw, this.viewH));
		this.scroll = Math.clamp(this.scroll, 0, Math.max(0, contentH - this.viewH));

		graphics.enableScissor(px + 1, viewTop, px + pw - 1, viewBottom);
		int ry = viewTop - this.scroll;
		for (int i = 0; i < rows.size(); i++) {
			List<FormattedCharSequence> split = wrapped.get(i);
			int rh = split.size() * Ui.LINE + 4;
			Ui.row(graphics, px + 6, ry, px + pw - 6, ry + rh - 1, i, false);
			int color = hidden ? Ui.DIM : i == rows.size() - 1 ? Ui.BAD : i == rows.size() - 2 ? Ui.GOOD : Ui.TEXT;
			for (int l = 0; l < split.size(); l++) {
				graphics.text(this.font, split.get(l), px + 10, ry + 2 + l * Ui.LINE, color);
			}
			ry += rh;
		}
		Ui.detail(graphics, this.font, px, viewTop - this.scroll + storyTop, pw, storyLines + 1, name, accent, text);
		graphics.disableScissor();
		Ui.scrollbar(graphics, px + pw - 4, viewTop, this.viewH, this.viewH, contentH, this.scroll);

		if (this.choose != null) {
			this.choose.setMessage(this.picked == null ? Component.translatable("gui.jaymoskingdoms.class.choose")
				: Component.translatable("gui.jaymoskingdoms.heritage.confirm", Component.translatable("heritage.jaymoskingdoms." + this.picked.key())));
			this.choose.active = this.picked != null;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.contentH > this.viewH && mouseX >= paneX()) {
			this.scroll = Math.clamp(this.scroll - (int)Math.round(scrollY * SCROLL_STEP), 0, this.contentH - this.viewH);
			// (the wheel takes over from the slow scroll until the mouse leaves)
			this.autoFrom = Long.MAX_VALUE;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** The lead line's colour now (fading from the hint grey into the panel), or 0 once its time is up. */
	private static int leadColor() {
		long now = System.currentTimeMillis();
		if (leadFrom == 0L) {
			leadFrom = now;
		}
		long left = LEAD_MS - (now - leadFrom);
		if (left <= 0L) {
			return 0;
		}
		float f = Math.min(1.0F, left / (float)LEAD_FADE_MS);
		int r = Math.round(0x10 + (0x70 - 0x10) * f), g = Math.round(0x10 + (0x70 - 0x10) * f), b = Math.round(0x18 + (0x80 - 0x18) * f);
		return r << 16 | g << 8 | Math.max(1, b);
	}

	/** Pointing at a pane that runs long scrolls it slowly down, waits, and goes back to the top, over and over. */
	private void autoScroll(boolean hovering) {
		int max = this.contentH - this.viewH;
		if (!hovering || max <= 0) {
			this.autoFrom = -1L;
			return;
		}
		long now = System.currentTimeMillis();
		if (this.autoFrom == Long.MAX_VALUE) {
			return;
		}
		if (this.autoFrom < 0L) {
			this.autoFrom = now;
		}
		long run = max * 1000L / AUTO_SPEED;
		long t = (now - this.autoFrom) % (AUTO_PAUSE_MS * 2 + run);
		this.scroll = t < AUTO_PAUSE_MS ? 0 : (int)Math.min(max, (t - AUTO_PAUSE_MS) * AUTO_SPEED / 1000L);
	}

	/** What the right pane shows before anything's pointed at or picked: the first that can be picked. */
	private Heritage.Kind firstPickable() {
		for (Heritage.Kind kind : Heritage.Kind.values()) {
			if (pickable(kind)) {
				return kind;
			}
		}
		return Heritage.Kind.values()[0];
	}

	/** The screen's accent: teal. */
	private int accent() {
		return ACCENT;
	}

	/** Every heritage down the left, the picked one marked; (0.7.106) those that can't be picked here greyed. */
	private void list(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left(), y = top();
		int accent = accent();
		Ui.line(graphics, this.font, this.title, x + 8, y + 8, LIST_W - 12, Ui.TEXT);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.heritage.sub"),
			x + 8, y + 19, LIST_W - 12, Ui.DIM);
		graphics.fill(x + LIST_W - 1, y + 4, x + LIST_W, y + panelH() - 4, Ui.ROW);
		Heritage.Kind[] all = Heritage.Kind.values();
		Heritage.Kind hover = rowAt(mouseX, mouseY);
		int rh = rowH();
		boolean icons = rh >= ICON_ROW;
		for (int i = 0; i < all.length; i++) {
			Heritage.Kind kind = all[i];
			int ry = rowY(i);
			if (ry + rh > y + panelH() - 4) {
				break;
			}
			boolean picked = kind == this.picked;
			boolean open = pickable(kind);
			graphics.fill(x + 4, ry, x + LIST_W - 4, ry + rh - 2,
				picked ? Ui.ROW_PICKED : kind == hover && open ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW_ALT : 0x0CFFFFFF);
			if (picked) {
				graphics.fill(x + 4, ry, x + 6, ry + rh - 2, accent);
			}
			if (icons && !hidden(kind)) {
				graphics.item(new ItemStack(kind.icon), x + 8, ry + 1);
			}
			int tx = icons ? x + 27 : x + 10;
			Ui.line(graphics, this.font, name(kind), tx, ry + (rh - 2 - 8) / 2 + 1, x + LIST_W - 7 - tx,
				picked ? accent : !open ? Ui.HINT : kind == hover ? Ui.TEXT : Ui.DIM);
		}
	}
}
