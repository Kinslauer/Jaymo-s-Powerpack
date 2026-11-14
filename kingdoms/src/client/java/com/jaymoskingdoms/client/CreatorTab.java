package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Sparks;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "if they hit skip the story, their start is randomized and ALL creation options are moved to a tab within the MCA
 * creator". The Life tab of MCA's start-up creator ({@code McaCreatorTabMixin}), there only for one who skipped the life
 * story: everything the story would have decided, set outright, in MCA's own buttons. Four small pages: your life (heritage,
 * look, who raised you, childhood, class and its option, the power's affinity), a harder life (flaws and switches), your
 * nature (ideal, bond, flaw) and the presets ({@link CharacterPresets}). The rows themselves are {@link LifeStoryScreen}'s;
 * nothing is sent till the creator is finished ({@link #seal}), so the story is lived once.
 */
public final class CreatorTab {
	/** The tab's page id in MCA's editor (its label: {@code gui.villager_editor.page.jaymo_life}). */
	public static final String PAGE = "jaymo_life";
	/** MCA's data column (its DATA_WIDTH), the tab's top under MCA's page buttons (from the screen's middle), a row's height and pitch, and an arrow's width. */
	static final int W = 175, TOP = -80, ROW_H = 16, PITCH = 18, ARROW = 16;
	private static final String[] SUBS = {"life", "harder", "nature", "presets"};
	private static final String[] SPARKS = {"ideal", "bond", "flaw"};
	private static int sub;

	private CreatorTab() {
	}

	/** Whether the story was skipped for this tab: MCA's creator opens with the story untold, and shows it. */
	public static boolean on() {
		return LifeStoryScreen.tabbed();
	}

	/** The heritage the rows make now (its key), for the creator's figure. */
	public static @Nullable String heritage() {
		return LifeStoryScreen.heritageKey();
	}

	/** The look the rows make now (the variant's key, null for the heritage's first), for the creator's figure. */
	public static @Nullable String variant() {
		return LifeStoryScreen.variantKey();
	}

	/** The creator is finished: the story is sealed as the rows stand, for the man or woman the creator made. */
	public static void seal(boolean female) {
		LifeStoryScreen.sealFromTab(female ? 1 : 0);
	}

	private static Button button(Component label, int x, int y, int w, Runnable press, Runnable rebuild) {
		return Button.builder(label, b -> {
			press.run();
			rebuild.run();
		}).bounds(x, y, w, ROW_H).build();
	}

	/** One row: an arrow back, the label (a click steps on), an arrow on. */
	private static void row(Consumer<AbstractWidget> add, Runnable rebuild, int x, int y, Component label, @Nullable Component tip, Consumer<Integer> step) {
		add.accept(button(Component.literal("<"), x, y, ARROW, () -> step.accept(-1), rebuild));
		Button main = button(label, x + ARROW + 2, y, W - 2 * ARROW - 4, () -> step.accept(1), rebuild);
		if (tip != null) {
			main.setTooltip(Ui.tipOf(tip));
		}
		add.accept(main);
		add.accept(button(Component.literal(">"), x + W - ARROW, y, ARROW, () -> step.accept(1), rebuild));
	}

	private static @Nullable Component lines(List<Component> lines) {
		if (lines.isEmpty()) {
			return null;
		}
		MutableComponent out = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			out.append(i == 0 ? "" : "\n").append(lines.get(i));
		}
		return out;
	}

	/**
	 * Puts the tab's widgets on MCA's creator, in its data column. {@code add}: the screen's addRenderableWidget;
	 * {@code rebuild}: the page set again, after any press (labels and rows follow the picks).
	 */
	public static void build(int width, int height, Font font, Consumer<AbstractWidget> add, Runnable rebuild) {
		int x = width / 2, y = height / 2 + TOP;
		int subW = W / SUBS.length;
		for (int i = 0; i < SUBS.length; i++) {
			int page = i;
			Button b = button(Component.translatable("gui.jaymoskingdoms.creator.sub." + SUBS[i]), x + i * subW, y, subW - 1, () -> sub = page, rebuild);
			b.active = sub != i;
			add.accept(b);
		}
		y += PITCH + 4;
		switch (sub) {
			case 0 -> {
				for (LifeStoryScreen.Field f : LifeStoryScreen.Field.values()) {
					if (!LifeStoryScreen.shown(f)) {
						continue;
					}
					Component label = Component.empty().append(LifeStoryScreen.fieldLabel(f)).append(" ").append(LifeStoryScreen.fieldValue(f));
					row(add, rebuild, x, y, label, lines(LifeStoryScreen.fieldEffects(f)), dir -> LifeStoryScreen.cycle(f, dir));
					y += PITCH;
				}
			}
			case 1 -> {
				for (int i = 0; i < 8; i++) {
					int choice = i;
					Button b = button(Component.literal(LifeStoryScreen.chosen(i) ? "[x] " : "[  ] ").append(LifeStoryScreen.choiceName(i)), x, y, W,
						() -> LifeStoryScreen.flip(choice), rebuild);
					b.active = LifeStoryScreen.choosable(i);
					b.setTooltip(Ui.tipOf(LifeStoryScreen.effect(i)));
					add.accept(b);
					// (the switches a little apart from the flaws)
					y += i == 3 ? PITCH + 4 : PITCH;
				}
			}
			case 2 -> {
				for (int kind = 0; kind < SPARKS.length; kind++) {
					int k = kind;
					List<String> offered = Sparks.options(null, kind);
					int at = LifeStoryScreen.spark(kind);
					String key = at >= 1 && at <= offered.size() ? "spark." + SPARKS[kind] + "." + offered.get(at - 1) : null;
					Component label = Component.translatable("gui.jaymoskingdoms.story.sparks." + SPARKS[kind]).append(": ")
						.append(key == null ? Component.translatable("gui.jaymoskingdoms.creator.none") : Component.translatable(key));
					row(add, rebuild, x, y, label, key == null ? Component.translatable("gui.jaymoskingdoms.story.sparks.about") : Component.translatable(key + ".fx"),
						dir -> LifeStoryScreen.cycleSpark(k, dir));
					y += PITCH;
				}
			}
			default -> {
				EditBox name = new EditBox(font, x + 1, y, W - 2, ROW_H, Component.translatable("gui.jaymoskingdoms.creator.preset.name"));
				name.setMaxLength(CharacterPresets.MAX_NAME);
				name.setHint(Component.translatable("gui.jaymoskingdoms.creator.preset.name"));
				name.setValue(LifeStoryScreen.presetTyped);
				name.setResponder(typed -> LifeStoryScreen.presetTyped = typed);
				add.accept(name);
				y += PITCH + 2;
				List<CharacterPresets.Preset> saved = CharacterPresets.all();
				int half = (W - 2) / 2;
				add.accept(button(Component.translatable("gui.jaymoskingdoms.creator.preset.save"), x, y, half, LifeStoryScreen::savePreset, rebuild));
				Button delete = button(Component.translatable("gui.jaymoskingdoms.creator.preset.delete"), x + half + 2, y, half, () -> {
					CharacterPresets.delete(LifeStoryScreen.presetTyped);
					LifeStoryScreen.presetAt = -1;
				}, rebuild);
				delete.active = CharacterPresets.named(LifeStoryScreen.presetTyped) != null;
				add.accept(delete);
				y += PITCH;
				int at = LifeStoryScreen.presetAt;
				Component load = saved.isEmpty() ? Component.translatable("gui.jaymoskingdoms.creator.preset.none")
					: at >= 0 && at < saved.size() ? Component.translatable("gui.jaymoskingdoms.creator.preset.loaded", saved.get(at).name, at + 1, saved.size())
					: Component.translatable("gui.jaymoskingdoms.creator.preset.load", saved.size());
				Button next = button(load, x, y, W, () -> LifeStoryScreen.loadNextPreset(1), rebuild);
				next.active = !saved.isEmpty();
				next.setTooltip(Ui.tipOf(Component.translatable("gui.jaymoskingdoms.creator.preset.about")));
				add.accept(next);
				y += PITCH + 6;
				add.accept(button(Component.translatable("gui.jaymoskingdoms.creator.random"), x, y, W, () -> LifeStoryScreen.randomize(true), rebuild));
			}
		}
	}
}
