package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Sparks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "if they hit skip the story, their start is randomized and ALL creation options are moved to a tab within the MCA
 * creator". The Life tab of MCA's start-up creator ({@code McaCreatorTabMixin}), there only for one who skipped the life
 * story: everything the story would have decided, set outright. Four small pages: your life (man or woman first, then
 * heritage, look, who raised you, childhood, class and its option, the power's affinity) and your nature (ideal, bond,
 * flaw), one thing at a time as a card; a harder life (flaws and switches) and the presets
 * ({@link CharacterPresets}) in MCA's own buttons. The picks themselves are {@link LifeStoryScreen}'s;
 * nothing is sent till the creator is finished ({@link #seal}), so the story is lived once.
 */
public final class CreatorTab {
	/** The tab's page id in MCA's editor (its label: {@code gui.villager_editor.page.jaymo_life}). */
	public static final String PAGE = "jaymo_life";
	/** MCA's data column (its DATA_WIDTH), the tab's top under MCA's page buttons (from the screen's middle), a row's height and pitch, and an arrow's width. */
	static final int W = 175, TOP = -80, ROW_H = 16, PITCH = 18, ARROW = 16;
	/** The tab's foot (from the screen's middle, level with the foot of MCA's Done), a card's picture well and its picture's scale, and its text box's padding and the gap between paragraphs. */
	static final int BOTTOM = 118, WELL = 52, ICON_SCALE = 3, PAD = 4, GAP = 4;
	private static final String[] SUBS = {"life", "harder", "nature", "presets"};
	private static final String[] SPARKS = {"ideal", "bond", "flaw"};
	private static final Item[] SPARK_ICONS = {Items.NETHER_STAR, Items.LEAD, Items.FERMENTED_SPIDER_EYE};
	/** The page shown, and the card shown on the Life page and on the Nature page. */
	private static int sub, lifeAt, natureAt;

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

	/**
	 * Andy: "Each of these current sliders should have a square showing an example or picture like the life story, and the
	 * bottom lets you move to the next parameter... the text sliding back and forth in the button should be in a box below
	 * the picture square so the paragraph can be read." One thing to set, alone on the page: what it is and how far along,
	 * its pick's picture (the arrows either side step the pick), the pick's name, and under it a box with all there is to
	 * read of it, wrapped, scrolled by the wheel when it runs past the box.
	 */
	private static final class Card extends AbstractWidget {
		private final Font font;
		private final Component header;
		private final ItemStack icon;
		private final List<FormattedCharSequence> name;
		/** The box's lines; null is the gap between two paragraphs. */
		private final List<@Nullable FormattedCharSequence> lines = new ArrayList<>();
		private final int textH;
		private int scroll;

		Card(Font font, int x, int y, int h, Component header, ItemStack icon, Component name, List<Component> text) {
			super(x, y, W, h, name);
			this.font = font;
			this.header = header;
			this.icon = icon;
			List<FormattedCharSequence> split = font.split(name, W - 4);
			this.name = split.subList(0, Math.min(2, split.size()));
			int th = 0;
			for (Component paragraph : text) {
				if (!this.lines.isEmpty()) {
					this.lines.add(null);
					th += GAP;
				}
				List<FormattedCharSequence> wrapped = font.split(paragraph, W - 2 * PAD - 4);
				this.lines.addAll(wrapped);
				th += wrapped.size() * Ui.LINE;
			}
			this.textH = th;
		}

		/** The picture well's top, from the card's. */
		static int wellTop() {
			return Ui.LINE + 2;
		}

		private int boxTop() {
			return getY() + wellTop() + WELL + 3 + this.name.size() * Ui.LINE + 2;
		}

		private int view() {
			return getY() + getHeight() - boxTop() - 2 * PAD;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX(), y = getY(), mid = x + W / 2;
			graphics.centeredText(this.font, this.header, mid, y + 1, Ui.DIM);
			int wx = x + (W - WELL) / 2, wy = y + wellTop();
			Ui.well(graphics, wx, wy, WELL, WELL);
			if (!this.icon.isEmpty()) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(wx + (WELL - 16 * ICON_SCALE) / 2F, wy + (WELL - 16 * ICON_SCALE) / 2F);
				graphics.pose().scale(ICON_SCALE, ICON_SCALE);
				graphics.item(this.icon, 0, 0);
				graphics.pose().popMatrix();
			}
			for (int i = 0; i < this.name.size(); i++) {
				graphics.centeredText(this.font, this.name.get(i), mid, wy + WELL + 3 + i * Ui.LINE, Ui.GOLD);
			}
			int top = boxTop(), bottom = y + getHeight(), view = view();
			Ui.well(graphics, x, top, W, bottom - top);
			this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.textH - view));
			graphics.enableScissor(x + 1, top + PAD, x + W - 1, bottom - PAD);
			int ty = top + PAD - this.scroll;
			for (FormattedCharSequence line : this.lines) {
				if (line == null) {
					ty += GAP;
					continue;
				}
				if (ty + Ui.LINE > top && ty < bottom) {
					graphics.text(this.font, line, x + PAD, ty, Ui.TEXT);
				}
				ty += Ui.LINE;
			}
			graphics.disableScissor();
			Ui.scrollbar(graphics, x + W - 4, top + 2, bottom - top - 4, view, this.textH, this.scroll);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
			if (this.textH <= view()) {
				return false;
			}
			this.scroll = Math.clamp(this.scroll - (int)Math.round(scrollY * 2 * Ui.LINE), 0, this.textH - view());
			return true;
		}

		/** Nothing to press: a click falls through. */
		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return false;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	/**
	 * One thing to set, as a {@link Card} from {@code y} down to the tab's foot: the arrows either side of its picture step
	 * the pick, the two buttons under it go to the thing before and the next ({@code at} of {@code count}, by {@code go}).
	 */
	private static void card(Consumer<AbstractWidget> add, Runnable rebuild, Font font, int x, int y, int bottom, Component label, int at, int count,
			ItemStack icon, Component name, List<Component> text, Consumer<Integer> step, Consumer<Integer> go) {
		int navY = bottom - ROW_H, half = (W - 2) / 2;
		Component header = Component.empty().append(label).append(" " + (at + 1) + "/" + count);
		add.accept(new Card(font, x, y, navY - 3 - y, header, icon, name, text));
		int ay = y + Card.wellTop() + (WELL - ROW_H) / 2, ax = x + (W - WELL) / 2;
		add.accept(button(Component.literal("<"), ax - ARROW - 6, ay, ARROW, () -> step.accept(-1), rebuild));
		add.accept(button(Component.literal(">"), ax + WELL + 6, ay, ARROW, () -> step.accept(1), rebuild));
		Button back = button(Component.translatable("gui.jaymoskingdoms.creator.prev"), x, navY, half, () -> go.accept(at - 1), rebuild);
		back.active = at > 0;
		add.accept(back);
		Button next = button(Component.translatable("gui.jaymoskingdoms.creator.next"), x + half + 2, navY, half, () -> go.accept(at + 1), rebuild);
		next.active = at < count - 1;
		add.accept(next);
	}

	/**
	 * Puts the tab's widgets on MCA's creator, in its data column. {@code add}: the screen's addRenderableWidget;
	 * {@code rebuild}: the page set again, after any press (the cards follow the picks). {@code female} and
	 * {@code setFemale}: the body the creator has made, and its own switch for it (the Life page's first card).
	 */
	public static void build(int width, int height, Font font, Consumer<AbstractWidget> add, Runnable rebuild, boolean female, Consumer<Boolean> setFemale) {
		LifeStoryScreen.genderFromCreator(female ? 1 : 0);
		int x = width / 2, y = height / 2 + TOP, bottom = height / 2 + BOTTOM;
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
				List<LifeStoryScreen.Field> fields = new ArrayList<>();
				for (LifeStoryScreen.Field f : LifeStoryScreen.Field.values()) {
					if (LifeStoryScreen.shown(f)) {
						fields.add(f);
					}
				}
				lifeAt = Math.clamp(lifeAt, 0, fields.size() - 1);
				LifeStoryScreen.Field f = fields.get(lifeAt);
				card(add, rebuild, font, x, y, bottom, LifeStoryScreen.fieldLabel(f), lifeAt, fields.size(), LifeStoryScreen.fieldIcon(f), LifeStoryScreen.fieldValue(f),
					LifeStoryScreen.fieldText(f), dir -> {
						if (f == LifeStoryScreen.Field.GENDER) {
							setFemale.accept(!female);
						} else {
							LifeStoryScreen.cycle(f, dir);
						}
					}, to -> lifeAt = to);
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
				natureAt = Math.clamp(natureAt, 0, SPARKS.length - 1);
				int kind = natureAt;
				List<String> offered = Sparks.options(null, kind);
				int at = LifeStoryScreen.spark(kind);
				String key = at >= 1 && at <= offered.size() ? "spark." + SPARKS[kind] + "." + offered.get(at - 1) : null;
				card(add, rebuild, font, x, y, bottom, Component.translatable("gui.jaymoskingdoms.story.sparks." + SPARKS[kind]), kind, SPARKS.length,
					new ItemStack(SPARK_ICONS[kind]), Component.translatable(key == null ? "gui.jaymoskingdoms.creator.none" : key),
					List.of(Component.translatable(key == null ? "gui.jaymoskingdoms.story.sparks.about" : key + ".fx")),
					dir -> LifeStoryScreen.cycleSpark(kind, dir), to -> natureAt = to);
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
