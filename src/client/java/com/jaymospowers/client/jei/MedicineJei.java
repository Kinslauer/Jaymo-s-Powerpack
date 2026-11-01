package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Herbal;
import com.jaymospowers.core.wounds.MedicineItems;
import com.jaymospowers.core.wounds.Pain;
import com.jaymospowers.core.wounds.RagWashing;
import com.jaymospowers.core.wounds.Satchel;
import com.jaymospowers.core.wounds.herbs.Herbs;
import com.jaymospowers.core.wounds.Treatment;
import com.jaymospowers.power.web.KitchenCompat;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.14.5) Wounds phase 5: JEI's Medicine page, one entry per remedy of the medieval kit ({@link MedicineItems}), honey,
 * Kitchen's strong spirits (when Kitchen is loaded) and a red-hot blade, each with what it treats and how it's used.
 * The numbers are read off {@link Treatment} and {@link Pain}, so the page follows the tuning. Crafting recipes stay on
 * JEI's own crafting page. Only loaded by JEI (the {@code jei_mod_plugin} entrypoint), on the repo's own
 * {@link SimpleCategory}.
 */
public final class MedicineJei implements IModPlugin {
	/** Lines of notes under each entry. */
	static final int NOTES = 3;
	static final IRecipeType<SimpleCategory.Recipe> MEDICINE = IRecipeType.create(JaymosPowers.MOD_ID, "medicine", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("medicine");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(new SimpleCategory(gui, MEDICINE, Component.translatable("jei.jaymospowers.medicine"),
			MedicineItems.LINEN_BANDAGE, NOTES));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		add(recipes, MedicineItems.LINEN_BANDAGE, note("bandage.1"), note("bandage.2", num(Treatment.BANDAGE_HEAL), Treatment.BANDAGE_TICKS / 20),
			note("use_part"));
		add(recipes, MedicineItems.RAG, note("rag.1"), note("rag.2", num(Treatment.RAG_HEAL)), note("use_part"));
		add(recipes, MedicineItems.SPLINT, note("splint.1"), note("splint.2"), note("splint.3"));
		add(recipes, MedicineItems.NEEDLE_AND_THREAD, note("needle.1"), note("needle.2", num(Treatment.NEEDLE_HURT)),
			note("uses", MedicineItems.NEEDLE_USES));
		add(recipes, MedicineItems.SMELLING_SALTS, note("salts.1"), note("salts.2", Math.round(Treatment.ROUSE_CHANCE * 100.0F)),
			note("uses", MedicineItems.SALTS_USES));
		add(recipes, MedicineItems.LEECHES, note("leeches.1", Pain.LEECH_TICKS / 1200), note("leeches.2", num(Treatment.LEECH_COST)),
			note("leeches.3"));
		add(recipes, MedicineItems.WILLOW_BARK_TEA, note("tea.1", Pain.TEA_TICKS / 1200), note("tea.2", Pain.TEA_DELAY / 20), note("drink"));
		add(recipes, MedicineItems.POPPY_SYRUP, note("poppy.1", Pain.POPPY_TICKS / 1200), note("poppy.2"), note("poppy.3"));
		add(recipes, Items.HONEY_BOTTLE, note("clean.1"), note("clean.2"), note("sneak"));
		// (0.19.24) the herbs: wild plants (right click a grown one to pick, plant the herb to grow more) and what they make
		add(recipes, Herbs.COMFREY, note("herb.1"), note("herb.2"), note("herb.comfrey"));
		add(recipes, Herbs.YARROW, note("herb.1"), note("herb.2"), note("herb.yarrow"));
		add(recipes, Herbs.PLANTAIN, note("herb.1"), note("herb.2"), note("herb.plantain"));
		add(recipes, Herbs.WILD_GARLIC, note("herb.1"), note("herb.2"), note("herb.wild_garlic"));
		add(recipes, Herbs.CHAMOMILE, note("herb.1"), note("herb.2"), note("herb.chamomile"));
		add(recipes, MedicineItems.YARROW_POWDER, note("yarrow.1"), note("yarrow.2"), note("use_part"));
		add(recipes, MedicineItems.KNITBONE_POULTICE, note("knitbone.1", num(Herbal.KNIT_NOW), Herbal.KNIT_TICKS / 1200), note("knitbone.2"), note("use_part"));
		add(recipes, MedicineItems.BURN_SALVE, note("salve.1"), note("salve.2", num(Herbal.SALVE_HEAL), Herbal.SALVE_TICKS / 20), note("use_part"));
		add(recipes, MedicineItems.GARLIC_POULTICE, note("garlic.1"), note("garlic.2", Herbal.GARLIC_WARD_TICKS / 1200), note("use_part"));
		add(recipes, MedicineItems.CLEAN_RAG, note("clean_rag.1"), note("clean_rag.2", num(Treatment.RAG_HEAL)), note("use_part"));
		add(recipes, MedicineItems.HEALERS_SATCHEL, note("satchel.1", Satchel.CAPACITY), note("satchel.2", Math.round(Satchel.SHORTEN * 100.0F)),
			note("satchel.3"));
		add(recipes, MedicineItems.BONE_BROTH, note("broth.1", num(Herbal.BROTH_HEAL)), note("broth.2", Herbal.BROTH_COOL_TICKS / 1200), note("drink"));
		add(recipes, MedicineItems.CHAMOMILE_TEA, note("chamomile.1", Herbal.CALM_SLEEP_MULTIPLIER), note("chamomile.2"), note("drink"));
		add(recipes, MedicineItems.POSSET, note("posset.1"), note("posset.2", num(Herbal.POSSET_HEAL)), note("drink"));
		// washing and boiling rags: a dirty rag in, a clean one out
		recipes.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(MedicineItems.RAG)), List.of(new ItemStack(Items.WATER_BUCKET))),
			List.of(new ItemStack(MedicineItems.CLEAN_RAG)),
			List.of(note("wash.1", RagWashing.WASH_TICKS / 20), note("wash.2", Math.round(RagWashing.STAYS_DIRTY * 100.0F)), note("wash.3"))));
		recipes.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(MedicineItems.RAG)), List.of(new ItemStack(Items.CAULDRON)),
			List.of(new ItemStack(Items.CAMPFIRE), new ItemStack(Items.SOUL_CAMPFIRE), new ItemStack(Items.MAGMA_BLOCK), new ItemStack(Items.LAVA_BUCKET))),
			List.of(new ItemStack(MedicineItems.CLEAN_RAG)),
			List.of(note("boil.1", RagWashing.RAGS_PER_LEVEL), note("boil.2", RagWashing.BOIL_TICKS / 20), note("boil.3"))));
		// Kitchen's strong spirits, whichever item that is (none without Kitchen)
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (KitchenCompat.isStrongSpirits(stack)) {
				recipes.add(new SimpleCategory.Recipe(List.of(List.of(stack)), List.of(),
					List.of(note("clean.1"), note("spirits.2", Pain.DRUNK_NUMB), note("sneak"))));
			}
		}
		// cautery: any sword or axe, heated
		List<ItemStack> blades = List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE));
		List<ItemStack> fires = List.of(new ItemStack(Items.CAMPFIRE), new ItemStack(Items.LAVA_BUCKET));
		recipes.add(new SimpleCategory.Recipe(List.of(blades, fires), List.of(),
			List.of(note("cautery.1", Treatment.HEAT_TICKS / 20), note("cautery.2"), note("cautery.3", num(Treatment.CAUTERY_BURN)))));
		registration.addRecipes(MEDICINE, recipes);
	}

	private static void add(List<SimpleCategory.Recipe> recipes, Item item, Component... notes) {
		recipes.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(item))), List.of(), List.of(notes)));
	}

	/** Health points as they read: 3, 1.5. */
	private static String num(float value) {
		return value == Math.round(value) ? Integer.toString(Math.round(value)) : Float.toString(value);
	}

	private static Component note(String key, Object... args) {
		return Component.translatable("jei.jaymospowers.medicine." + key, args);
	}
}
