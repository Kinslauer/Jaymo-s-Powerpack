package com.jaymospowers.client.sound;

import com.jaymospowers.client.UiStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The sound browser (Andy's ImmersiveFX pick, built as our own): every sound you heard in the last minute, newest first,
 * then any you've turned down; type in the box to find any sound the game knows. Each row shows the sound's name (its
 * subtitle, else its id), how often it played lately, and its volume as a bar: click the bar to set it (in 5% steps),
 * click the name to mute or unmute, right-click to put it back to full. "Thin repeats" drops a sound's extra starts when
 * it fires more than a few times in half a second ({@link SoundTuning}). Our own power sounds are in it like any other.
 * Opened from Music &amp; Sounds (the Sound Browser button) or a key (unbound at first, under Jaymo's Powers).
 */
public class SoundBrowserScreen extends Screen {
	private static final int MAX_W = 380, ROW_H = 18, BAR_W = 90, ACCENT = 0xFF6FB7D8, MUTED = 0xFFB04040;
	/** At most this many sounds found by a search are listed. */
	static final int SEARCH_MAX = 300;

	private final @Nullable Screen parent;
	private EditBox search;
	private Button thin;
	private List<Identifier> rows = new ArrayList<>();
	private int scroll;
	private int refresh;

	public SoundBrowserScreen(@Nullable Screen parent) {
		super(Component.translatable("screen.jaymospowers.sound_browser"));
		this.parent = parent;
	}

	private int panelW() {
		return Math.min(MAX_W, width - 12);
	}

	private int left() {
		return (width - panelW()) / 2;
	}

	private int top() {
		return 6;
	}

	private int listTop() {
		return top() + UiStyle.HEADER + 22;
	}

	private int listBottom() {
		return height - 22;
	}

	private int visible() {
		return Math.max(1, (listBottom() - listTop()) / ROW_H);
	}

	@Override
	protected void init() {
		int x = left(), w = panelW(), y = top() + UiStyle.HEADER;
		String text = search == null ? "" : search.getValue();
		search = addRenderableWidget(new EditBox(font, x + 8, y, w - 8 - 8 - 96 - 66, 16, Component.translatable("screen.jaymospowers.sound_browser.search")));
		search.setHint(Component.translatable("screen.jaymospowers.sound_browser.search"));
		search.setValue(text);
		search.setResponder(s -> {
			scroll = 0;
			rebuild();
		});
		thin = addRenderableWidget(Button.builder(thinLabel(), b -> {
			SoundTuning.setThinning(!SoundTuning.thinning());
			b.setMessage(thinLabel());
		}).bounds(x + w - 8 - 62 - 96, y - 2, 94, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.sound_browser.reset"), b -> {
			SoundTuning.resetAll();
			rebuild();
		}).bounds(x + w - 8 - 62, y - 2, 62, 20).build());
		rebuild();
	}

	private static Component thinLabel() {
		return Component.translatable(SoundTuning.thinning() ? "screen.jaymospowers.sound_browser.thin_on" : "screen.jaymospowers.sound_browser.thin_off");
	}

	private void rebuild() {
		String text = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		Set<Identifier> out = new LinkedHashSet<>();
		if (text.isEmpty()) {
			for (var e : SoundTuning.heard()) {
				out.add(e.getKey());
			}
			out.addAll(SoundTuning.changed());
		} else {
			List<Identifier> all = new ArrayList<>(minecraft.getSoundManager().getAvailableSounds());
			all.sort(null);
			for (Identifier id : all) {
				if (out.size() >= SEARCH_MAX) {
					break;
				}
				if (id.toString().contains(text) || name(id).getString().toLowerCase(Locale.ROOT).contains(text)) {
					out.add(id);
				}
			}
		}
		rows = new ArrayList<>(out);
		scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible()));
	}

	@Override
	public void tick() {
		// the heard list moves while you watch
		if (++refresh >= 20 && search.getValue().isBlank()) {
			refresh = 0;
			rebuild();
		}
	}

	private Component name(Identifier id) {
		WeighedSoundEvents event = minecraft.getSoundManager().getSoundEvent(id);
		Component subtitle = event == null ? null : event.getSubtitle();
		return subtitle != null ? subtitle : Component.literal(id.toString());
	}

	private int at(double mouseX, double mouseY) {
		int x = left() + 6, w = panelW() - 12;
		if (mouseX < x || mouseX >= x + w || mouseY < listTop()) {
			return -1;
		}
		int i = (int)((mouseY - listTop()) / ROW_H);
		return i < visible() && scroll + i < rows.size() ? scroll + i : -1;
	}

	private int barX() {
		return left() + panelW() - 10 - BAR_W;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = at(event.x(), event.y());
		if (i >= 0) {
			Identifier id = rows.get(i);
			if (event.button() == 1) {
				SoundTuning.set(id, 1.0F);
			} else if (event.x() >= barX() - 2) {
				SoundTuning.set(id, (float)Mth.clamp((event.x() - barX()) / BAR_W, 0.0, 1.0));
			} else {
				SoundTuning.set(id, SoundTuning.level(id) > 0.0F ? 0.0F : 1.0F);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int)Math.signum(scrollY) * 3, 0, Math.max(0, rows.size() - visible()));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW();
		UiStyle.panel(graphics, x, y, w, listBottom() - y + 16, ACCENT);
		Component sub = Component.translatable(search.getValue().isBlank() ? "screen.jaymospowers.sound_browser.heard" : "screen.jaymospowers.sound_browser.found",
			rows.size());
		UiStyle.header(graphics, font, x, y, w, null, title, sub, ACCENT, 0);
		int hovered = at(mouseX, mouseY);
		int shown = Math.min(visible(), rows.size() - scroll);
		if (rows.isEmpty()) {
			UiStyle.line(graphics, font, Component.translatable("screen.jaymospowers.sound_browser.none"), x + 10, listTop() + 4, w - 20, UiStyle.HINT);
		}
		for (int r = 0; r < shown; r++) {
			int i = scroll + r;
			Identifier id = rows.get(i);
			int ry = listTop() + r * ROW_H;
			UiStyle.row(graphics, x + 6, ry, x + w - 6, ry + ROW_H - 2, i, i == hovered);
			float level = SoundTuning.level(id);
			int count = SoundTuning.count(id);
			Component countText = Component.literal(count > 0 ? "x" + count : "");
			int countW = font.width(countText);
			int barX = barX();
			UiStyle.line(graphics, font, name(id), x + 10, ry + 4, barX - 46 - (x + 10) - countW, level <= 0.0F ? MUTED : UiStyle.TEXT);
			UiStyle.lineRight(graphics, font, countText, barX - 40, ry + 4, countW, UiStyle.DIM);
			UiStyle.lineRight(graphics, font, Component.literal(level <= 0.0F ? "off" : Math.round(level * 100) + "%"), barX - 4, ry + 4, 34,
				level <= 0.0F ? MUTED : UiStyle.DIM);
			UiStyle.bar(graphics, barX, ry + 5, BAR_W, 6, level, level <= 0.0F ? MUTED : ACCENT);
		}
		if (rows.size() > visible()) {
			UiStyle.scrollbar(graphics, x + w - 5, listTop(), visible() * ROW_H, visible(), rows.size(), scroll);
		}
		if (hovered >= 0) {
			UiStyle.line(graphics, font, Component.literal(rows.get(hovered).toString()), x + 10, listBottom() + 3, w - 20,
				UiStyle.HINT);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
