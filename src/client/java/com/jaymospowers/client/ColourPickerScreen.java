package com.jaymospowers.client;

import com.jaymospowers.client.ui.FlatButton;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The one colour picker for the pack: preset swatches, the colours picked lately, a hue strip (light to dark down it), a
 * hex box, the old colour beside the new one, and Apply, Reset and Cancel. Every change is handed to {@code tryOn} as it
 * happens (a live try-on); Apply hands the colour to {@code apply} and closes, Cancel puts the old colour back through
 * {@code tryOn} and closes, Reset goes back to the old colour and stays. Kingdoms and Kitchen keep their own copy of this
 * screen when they need one (they don't depend on this mod).
 */
public final class ColourPickerScreen extends Screen {
	/** The presets (0xRRGGBB), one row of {@link #PER_ROW}. */
	public static final int[] PRESETS = {0xFFD84A, 0x5AC8FF, 0xFF4A4A, 0x7CFF6A, 0xC070FF, 0xFFFFFF, 0xFF8A2A, 0xFF70C8,
		0x2A2A33, 0x8A8A99, 0x3A5AFF, 0x1FA67A, 0x8A4A1F, 0xFFE9C0, 0xB01030, 0x6A2AA0};
	static final int PER_ROW = 8, SWATCH = 16, GAP = 3;
	/** How many recent colours are kept (this session). */
	static final int RECENT = 8;
	static final int W = 8 * (SWATCH + GAP) + 20 + 70, H = 188;
	/** The hue strip: its size, and how many columns it draws. */
	static final int STRIP_W = 8 * (SWATCH + GAP) - GAP, STRIP_H = 40;
	static final int PANEL = 0xD0101018;

	private static final Deque<Integer> RECENTS = new ArrayDeque<>();

	private final @Nullable Screen previous;
	private final int old;
	private final IntConsumer tryOn, apply;
	private int colour;
	private EditBox hex;
	private boolean typing;

	/** {@code old}: the colour now (0xRRGGBB); {@code tryOn}: called with each colour as it's tried; {@code apply}: with the one kept. */
	public ColourPickerScreen(@Nullable Screen previous, int old, IntConsumer tryOn, IntConsumer apply) {
		super(Component.translatable("screen.jaymospowers.colour.title"));
		this.previous = previous;
		this.old = old & 0xFFFFFF;
		this.colour = this.old;
		this.tryOn = tryOn;
		this.apply = apply;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	private int gridX() {
		return left() + 10;
	}

	private int presetsY() {
		return top() + 26;
	}

	private int recentY() {
		return presetsY() + 2 * (SWATCH + GAP) + 12;
	}

	private int stripY() {
		return recentY() + SWATCH + GAP + 6;
	}

	@Override
	protected void init() {
		int x = gridX();
		int by = top() + H - 24;
		hex = addRenderableWidget(new EditBox(this.font, left() + W - 70 - 6, top() + 26, 64, 14, Component.translatable("screen.jaymospowers.colour.hex")));
		hex.setMaxLength(7);
		hex.setValue(hexOf(colour));
		hex.setResponder(s -> {
			if (typing) {
				return;
			}
			Integer parsed = parse(s);
			if (parsed != null) {
				colour = parsed;
				tryOn.accept(colour);
			}
		});
		addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymospowers.colour.apply"), b -> keep()).bounds(x, by, 60, 16).build());
		addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymospowers.colour.reset"), b -> set(old)).bounds(x + 64, by, 50, 16).build());
		addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymospowers.colour.cancel"), b -> onClose()).bounds(x + 118, by, 50, 16).build());
	}

	/** Tries {@code value} on now and shows it in the hex box. */
	private void set(int value) {
		colour = value & 0xFFFFFF;
		typing = true;
		hex.setValue(hexOf(colour));
		typing = false;
		tryOn.accept(colour);
	}

	private void keep() {
		RECENTS.remove(colour);
		RECENTS.addFirst(colour);
		while (RECENTS.size() > RECENT) {
			RECENTS.removeLast();
		}
		apply.accept(colour);
		this.minecraft.gui.setScreen(previous);
	}

	@Override
	public void onClose() {
		tryOn.accept(old);
		this.minecraft.gui.setScreen(previous);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** {@code #RRGGBB} for a colour. */
	static String hexOf(int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}

	/** A colour from {@code RRGGBB}, {@code #RRGGBB} or the three-digit short form; null if it isn't one. */
	static @Nullable Integer parse(String text) {
		String s = text.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		if (s.length() == 3) {
			s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
		}
		if (s.length() != 6) {
			return null;
		}
		try {
			return Integer.parseInt(s, 16);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** The colour at {@code (u, v)} of the strip: hue across; down it, white to the hue in the top half and the hue to black below. */
	static int stripColour(float u, float v) {
		float hue = Mth.clamp(u, 0.0F, 1.0F);
		if (v < 0.5F) {
			return Mth.hsvToRgb(hue, v * 2.0F, 1.0F) & 0xFFFFFF;
		}
		return Mth.hsvToRgb(hue, 1.0F, 1.0F - (v - 0.5F) * 2.0F) & 0xFFFFFF;
	}

	private int swatchAt(double mx, double my, int y0, int count) {
		int col = (int)Math.floor((mx - gridX()) / (SWATCH + GAP)), row = (int)Math.floor((my - y0) / (SWATCH + GAP));
		if (col < 0 || col >= PER_ROW || row < 0 || mx - gridX() - col * (SWATCH + GAP) >= SWATCH || my - y0 - row * (SWATCH + GAP) >= SWATCH) {
			return -1;
		}
		int i = row * PER_ROW + col;
		return i < count ? i : -1;
	}

	private boolean overStrip(double mx, double my) {
		return mx >= gridX() && mx < gridX() + STRIP_W && my >= stripY() && my < stripY() + STRIP_H;
	}

	private void pickStrip(double mx, double my) {
		set(stripColour((float)((mx - gridX()) / STRIP_W), (float)((my - stripY()) / STRIP_H)));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int preset = swatchAt(event.x(), event.y(), presetsY(), PRESETS.length);
		if (preset >= 0) {
			set(PRESETS[preset]);
			return true;
		}
		int recent = swatchAt(event.x(), event.y(), recentY(), RECENTS.size());
		if (recent >= 0) {
			set(RECENTS.stream().skip(recent).findFirst().orElse(colour));
			return true;
		}
		if (overStrip(event.x(), event.y())) {
			pickStrip(event.x(), event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (overStrip(event.x(), event.y())) {
			pickStrip(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), gx = gridX();
		UiStyle.panelFramed(graphics, x, y, W, H, UiStyle.ACCENT);
		graphics.centeredText(this.font, this.title, x + W / 2, y + 8, UiStyle.ACCENT);
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.colour.presets"), gx, y + 16, 120, UiStyle.DIM);
		for (int i = 0; i < PRESETS.length; i++) {
			swatch(graphics, gx + i % PER_ROW * (SWATCH + GAP), presetsY() + i / PER_ROW * (SWATCH + GAP), PRESETS[i]);
		}
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.colour.recent"), gx, recentY() - 10, 120, UiStyle.DIM);
		int n = 0;
		for (int c : RECENTS) {
			swatch(graphics, gx + n % PER_ROW * (SWATCH + GAP), recentY() + n / PER_ROW * (SWATCH + GAP), c);
			n++;
		}
		for (int i = 0; i < STRIP_W; i++) {
			float u = i / (float)STRIP_W;
			graphics.fillGradient(gx + i, stripY(), gx + i + 1, stripY() + STRIP_H / 2, 0xFF000000 | stripColour(u, 0.0F), 0xFF000000 | stripColour(u, 0.5F));
			graphics.fillGradient(gx + i, stripY() + STRIP_H / 2, gx + i + 1, stripY() + STRIP_H, 0xFF000000 | stripColour(u, 0.5F), 0xFF000000 | stripColour(u, 1.0F));
		}
		graphics.outline(gx - 1, stripY() - 1, STRIP_W + 2, STRIP_H + 2, UiStyle.WINDOW_SHADE);
		// the old colour beside the new one, right of the hex box
		int sx = x + W - 70 - 6;
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.colour.hex"), sx, y + 16, 64, UiStyle.DIM);
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.colour.old_new"), sx, y + 48, 64, UiStyle.DIM);
		graphics.fill(sx, y + 60, sx + 32, y + 92, 0xFF000000 | old);
		graphics.fill(sx + 32, y + 60, sx + 64, y + 92, 0xFF000000 | colour);
		graphics.outline(sx - 1, y + 59, 66, 34, UiStyle.WINDOW_SHADE);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int hover = swatchAt(mouseX, mouseY, presetsY(), PRESETS.length);
		if (hover >= 0) {
			UiStyle.tip(graphics, this.font, Component.literal(hexOf(PRESETS[hover])), mouseX, mouseY);
		}
	}

	private void swatch(GuiGraphicsExtractor graphics, int x, int y, int rgb) {
		graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | rgb);
		graphics.outline(x - 1, y - 1, SWATCH + 2, SWATCH + 2, rgb == colour ? UiStyle.TEXT : UiStyle.WINDOW_SHADE);
	}
}
