package com.jaymoskitchen.client.jei;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * (0.11.31) One JEI page for one of our ways of making things: up to {@link #MAX_INPUTS} input slots (each cycling
 * through what may go there), an arrow, the result, and a line or two of how it's done underneath.
 */
final class SimpleCategory implements IRecipeCategory<SimpleCategory.Recipe> {
	/** A recipe as JEI shows it: each input slot's choices, the result's (empty for none), and the notes below. */
	record Recipe(List<List<ItemStack>> inputs, List<ItemStack> output, List<Component> notes) {
	}

	static final int MAX_INPUTS = 4;
	private static final int SLOT = 18, ARROW = 24, TEXT_LINE = 10, WIDTH = SLOT * MAX_INPUTS + 4 + ARROW + 4 + SLOT + 40;

	/** Lines of notes each page needs, from its recipes as registered: a long note wraps onto more than one. */
	private static final Map<IRecipeType<?>, Integer> LINES = new HashMap<>();

	private final IRecipeType<Recipe> type;
	private final Component title;
	private final IDrawable icon;
	private final int notes;

	SimpleCategory(IGuiHelper gui, IRecipeType<Recipe> type, Component title, ItemLike icon, int notes) {
		this.type = type;
		this.title = title;
		this.icon = gui.createDrawableItemLike(icon);
		this.notes = notes;
	}

	@Override
	public IRecipeType<Recipe> getRecipeType() {
		return type;
	}

	@Override
	public Component getTitle() {
		return title;
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return SLOT + 4 + Math.max(notes, LINES.getOrDefault(type, 0)) * TEXT_LINE;
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
		List<List<ItemStack>> inputs = recipe.inputs();
		for (int i = 0; i < inputs.size() && i < MAX_INPUTS; i++) {
			builder.addInputSlot(1 + i * SLOT, 1).setStandardSlotBackground().addItemStacks(inputs.get(i));
		}
		if (!recipe.output().isEmpty()) {
			builder.addOutputSlot(outputX(), 1).setOutputSlotBackground().addItemStacks(recipe.output());
		}
	}

	@Override
	public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
		builder.addRecipeArrow().setPosition(arrowX(), 1);
		int y = SLOT + 4;
		for (Component note : recipe.notes()) {
			// one text line each, so JEI never cuts a long note short
			for (FormattedText line : lines(note)) {
				builder.addText(line, WIDTH, TEXT_LINE).setPosition(0, y).setColor(0xFF404040);
				y += TEXT_LINE;
			}
		}
	}

	/** Registers a page's recipes, leaving room on it for every line their notes wrap onto. */
	static void addRecipes(IRecipeRegistration registration, IRecipeType<Recipe> type, List<Recipe> recipes) {
		for (Recipe recipe : recipes) {
			int lines = 0;
			for (Component note : recipe.notes()) {
				lines += lines(note).size();
			}
			LINES.merge(type, lines, Math::max);
		}
		registration.addRecipes(type, recipes);
	}

	/** A note split into the lines it takes across the page (an empty note still takes one). */
	private static List<FormattedText> lines(Component note) {
		List<FormattedText> lines = Minecraft.getInstance().font.getSplitter().splitLines(note, WIDTH, Style.EMPTY);
		return lines.isEmpty() ? List.of(FormattedText.EMPTY) : lines;
	}

	private static int arrowX() {
		return SLOT * MAX_INPUTS + 4;
	}

	private static int outputX() {
		return arrowX() + ARROW + 4 + 1;
	}
}
