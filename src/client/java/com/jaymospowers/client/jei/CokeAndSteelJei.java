package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.Tin;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.coke.BlastFurnaceEntity;
import com.jaymospowers.tinkering.coke.CharcoalPit;
import com.jaymospowers.tinkering.coke.CokeAndSteel;
import com.jaymospowers.tinkering.coke.CokeOvenEntity;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Andy's GT New Horizons picks, patch 4 ({@link CokeAndSteel}, {@link Tin}): JEI's Coke Oven page (each bake, its time and
 * the creosote it leaves), Charcoal Pit page (the heap, its cover and its days; a charred log's charcoal), Brick Blast
 * Furnace page (iron and coal coke into steel, the time and the ash) and Tin Ore page (what tin ore and its raw block
 * drop), and how to build the oven, the furnace and the pit, layer by layer. The bronze alloy, the melting of tin and the
 * bronze parts are on Tinkering's pages ({@link TinkeringJei}), the kiln's new bricks on {@link KilnJei}, tin sand's
 * panning on {@link StoneAgeJei}, the grid recipes on JEI's crafting page. Only loaded by JEI, on {@link SimpleCategory}.
 */
public final class CokeAndSteelJei implements IModPlugin {
	static final IRecipeType<SimpleCategory.Recipe> COKE_OVEN = type("coke_oven"), CHARCOAL_PIT = type("charcoal_pit"),
		BLAST_FURNACE = type("brick_blast_furnace"), TIN_ORE = type("tin_ore");
	static final int TICKS_PER_SECOND = 20;

	private static IRecipeType<SimpleCategory.Recipe> type(String id) {
		return IRecipeType.create(JaymosPowers.MOD_ID, id, SimpleCategory.Recipe.class);
	}

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("coke_and_steel");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(
			new SimpleCategory(gui, COKE_OVEN, Component.translatable("jei.jaymospowers.coke_oven"), CokeAndSteel.COKE_OVEN, 2),
			new SimpleCategory(gui, CHARCOAL_PIT, Component.translatable("jei.jaymospowers.charcoal_pit"), CokeAndSteel.CHARRED_LOG, 2),
			new SimpleCategory(gui, BLAST_FURNACE, Component.translatable("jei.jaymospowers.brick_blast_furnace"), CokeAndSteel.BLAST_FURNACE, 2),
			new SimpleCategory(gui, TIN_ORE, Component.translatable("jei.jaymospowers.tin_ore"), Tin.TIN_ORE, 1));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(COKE_OVEN, CokeAndSteel.COKE_OVEN);
		registration.addCraftingStation(BLAST_FURNACE, CokeAndSteel.BLAST_FURNACE);
		registration.addCraftingStation(CHARCOAL_PIT, Items.FLINT_AND_STEEL);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<SimpleCategory.Recipe> bakes = new ArrayList<>();
		for (CokeOvenEntity.Bake bake : CokeOvenEntity.all()) {
			List<ItemStack> inputs = matching(bake.input()::test);
			if (!inputs.isEmpty()) {
				bakes.add(new SimpleCategory.Recipe(List.of(inputs), List.of(new ItemStack(bake.output())), List.of(
					Component.translatable("jei.jaymospowers.coke_oven.time", bake.ticks() / TICKS_PER_SECOND),
					Component.translatable("jei.jaymospowers.coke_oven.creosote", bake.creosote(), CokeOvenEntity.TANK))));
			}
		}
		bakes.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(CokeAndSteel.COKE_OVEN)), List.of(new ItemStack(Items.BUCKET),
			new ItemStack(com.jaymospowers.ground.StoneAge.CLAY_BUCKET))), List.of(new ItemStack(CokeAndSteel.CREOSOTE_BUCKET),
			new ItemStack(CokeAndSteel.CLAY_CREOSOTE_BUCKET)), List.of(Component.translatable("jei.jaymospowers.coke_oven.draw", CokeOvenEntity.BUCKET),
			Component.translatable("jei.jaymospowers.coke_oven.full"))));
		SimpleCategory.addRecipes(registration, COKE_OVEN, bakes);

		List<ItemStack> logs = matching(s -> s.is(net.minecraft.tags.ItemTags.LOGS_THAT_BURN));
		List<ItemStack> cover = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block.defaultBlockState().is(CharcoalPit.COVER) && block.asItem() != Items.AIR) {
				cover.add(new ItemStack(block));
			}
		}
		SimpleCategory.addRecipes(registration, CHARCOAL_PIT, List.of(
			new SimpleCategory.Recipe(List.of(logs, cover, List.of(new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.FIRE_CHARGE))),
				List.of(new ItemStack(CokeAndSteel.CHARRED_LOG)), List.of(Component.translatable("jei.jaymospowers.charcoal_pit.days", CharcoalPit.BURN_DAYS),
				Component.translatable("jei.jaymospowers.charcoal_pit.air"))),
			new SimpleCategory.Recipe(List.of(List.of(new ItemStack(CokeAndSteel.CHARRED_LOG))), List.of(new ItemStack(Items.CHARCOAL, CharcoalPit.CHARCOAL_PER_LOG)),
				List.of(Component.translatable("jei.jaymospowers.charcoal_pit.break"), Component.empty()))));

		SimpleCategory.addRecipes(registration, BLAST_FURNACE, List.of(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(Items.IRON_INGOT)),
			List.of(new ItemStack(CokeAndSteel.COAL_COKE, BlastFurnaceEntity.COKE_PER_STEEL))), List.of(new ItemStack(Tinkering.STEEL_INGOT)),
			List.of(Component.translatable("jei.jaymospowers.brick_blast_furnace.time", BlastFurnaceEntity.STEEL_TICKS / TICKS_PER_SECOND / 60),
				Component.empty()), List.of(new ItemStack(CokeAndSteel.ASH, BlastFurnaceEntity.ASH_PER_STEEL)))));

		SimpleCategory.addRecipes(registration, TIN_ORE, List.of(
			new SimpleCategory.Recipe(List.of(List.of(new ItemStack(Tin.TIN_ORE), new ItemStack(Tin.DEEPSLATE_TIN_ORE))), List.of(new ItemStack(Tin.RAW_TIN)),
				List.of(Component.translatable("jei.jaymospowers.tin_ore.where"))),
			new SimpleCategory.Recipe(List.of(List.of(new ItemStack(Tin.TIN_SAND))), List.of(new ItemStack(Tin.TIN_SAND)),
				List.of(Component.translatable("jei.jaymospowers.tin_sand.where")))));

		registration.addIngredientInfo(CokeAndSteel.COKE_OVEN, layers("coke_oven", 4));
		registration.addIngredientInfo(CokeAndSteel.COKE_OVEN_BRICKS, layers("coke_oven", 4));
		registration.addIngredientInfo(CokeAndSteel.BLAST_FURNACE, layers("brick_blast_furnace", 5));
		registration.addIngredientInfo(CokeAndSteel.FIREBRICKS, layers("brick_blast_furnace", 5));
		registration.addIngredientInfo(CokeAndSteel.CHARRED_LOG, layers("charcoal_pit", 4));
		registration.addIngredientInfo(CokeAndSteel.TREATED_PLANKS, Component.translatable("jei.jaymospowers.treated_planks"));
		registration.addIngredientInfo(CokeAndSteel.ASH, Component.translatable("jei.jaymospowers.ash"));
		registration.addIngredientInfo(CokeAndSteel.CREOSOTE_BUCKET, Component.translatable("jei.jaymospowers.creosote"));
		registration.addIngredientInfo(CokeAndSteel.CLAY_CREOSOTE_BUCKET, Component.translatable("jei.jaymospowers.creosote"));
	}

	/** How to build it: a heading line, then one line a layer (jei.jaymospowers.<id>.build.1 to .n). */
	private static Component[] layers(String id, int lines) {
		Component[] out = new Component[lines];
		for (int i = 0; i < lines; i++) {
			out[i] = Component.translatable("jei.jaymospowers." + id + ".build." + (i + 1));
		}
		return out;
	}

	private static List<ItemStack> matching(java.util.function.Predicate<ItemStack> test) {
		List<ItemStack> out = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (test.test(stack)) {
				out.add(stack);
			}
		}
		return out;
	}
}
