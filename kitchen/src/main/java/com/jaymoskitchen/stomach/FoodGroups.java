package com.jaymoskitchen.stomach;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Step;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Food groups (Andy's TFC Hardrock picks): grain, fruit, vegetables, protein and dairy. What's in your stomach's slots
 * ({@link Stomach}) is counted by group: {@link #BALANCED} or more groups at once adds {@link #BALANCED_HEALTH} health
 * (two hearts), {@link #VARIED} adds half that, and a stomach of one group alone (two meals or more) takes
 * {@link #SAMEY_HEALTH} off. A Kitchen dish counts every group its ingredients do. Shown on the stomach screen.
 */
public final class FoodGroups {
	public enum Group { GRAIN, FRUIT, VEGETABLE, PROTEIN, DAIRY }

	static final int BALANCED = 4, VARIED = 3;
	static final float BALANCED_HEALTH = 4.0F, SAMEY_HEALTH = 2.0F;

	private static final List<String> GRAIN_WORDS = List.of("wheat", "flour", "dough", "bread", "rice", "oat", "corn", "pasta", "noodle", "barley",
		"cookie", "pie", "cake", "biscuit", "bun", "toast");
	private static final List<String> DAIRY_WORDS = List.of("milk", "cheese", "butter", "cream", "yogurt");
	private static final List<String> PROTEIN_WORDS = List.of("egg", "beef", "pork", "chicken", "mutton", "rabbit", "cod", "salmon", "fish", "meat",
		"ham", "bacon", "sausage", "steak", "loin", "rib", "leg", "venison", "squid", "crab", "shrimp", "bean", "nut");
	private static final List<String> FRUIT_WORDS = List.of("apple", "berry", "berries", "melon", "fruit", "cherry", "grape", "pumpkin", "peach", "lemon");
	private static final List<String> VEG_WORDS = List.of("carrot", "potato", "beetroot", "cabbage", "tomato", "onion", "mushroom", "kelp", "lettuce",
		"pepper", "leek", "garlic", "bulb", "vegetable", "salad");

	private FoodGroups() {
	}

	/** The groups {@code item} counts toward (a dish: its ingredients'). */
	public static Set<Group> of(Item item) {
		EnumSet<Group> groups = EnumSet.noneOf(Group.class);
		Dish dish = ModItems.dishOf(item);
		if (dish != null) {
			for (Step step : dish.steps()) {
				if (!step.isAction() && step.available()) {
					groups.addAll(plain(step.item()));
				}
			}
			if (!groups.isEmpty()) {
				return groups;
			}
		}
		groups.addAll(plain(item));
		return groups;
	}

	private static Set<Group> plain(Item item) {
		EnumSet<Group> groups = EnumSet.noneOf(Group.class);
		ItemStack stack = new ItemStack(item);
		if (stack.is(ConventionalItemTags.BREAD_FOODS) || stack.is(ConventionalItemTags.COOKIE_FOODS) || stack.is(ConventionalItemTags.DOUGH_FOODS)
			|| stack.is(ConventionalItemTags.PIE_FOODS)) {
			groups.add(Group.GRAIN);
		}
		if (stack.is(ConventionalItemTags.FRUIT_FOODS) || stack.is(ConventionalItemTags.BERRY_FOODS)) {
			groups.add(Group.FRUIT);
		}
		if (stack.is(ConventionalItemTags.VEGETABLE_FOODS)) {
			groups.add(Group.VEGETABLE);
		}
		if (stack.is(ConventionalItemTags.RAW_MEAT_FOODS) || stack.is(ConventionalItemTags.COOKED_MEAT_FOODS) || stack.is(ConventionalItemTags.RAW_FISH_FOODS)
			|| stack.is(ConventionalItemTags.COOKED_FISH_FOODS) || stack.is(RawFood.RAW_MEATS)) {
			groups.add(Group.PROTEIN);
		}
		if (item == Items.MILK_BUCKET) {
			groups.add(Group.DAIRY);
		}
		if (groups.isEmpty()) {
			String path = BuiltInRegistries.ITEM.getKey(item).getPath();
			words(path, GRAIN_WORDS, Group.GRAIN, groups);
			words(path, DAIRY_WORDS, Group.DAIRY, groups);
			words(path, PROTEIN_WORDS, Group.PROTEIN, groups);
			words(path, FRUIT_WORDS, Group.FRUIT, groups);
			words(path, VEG_WORDS, Group.VEGETABLE, groups);
		}
		return groups;
	}

	private static void words(String path, List<String> words, Group group, Set<Group> into) {
		for (String word : words) {
			if (path.contains(word)) {
				into.add(group);
				return;
			}
		}
	}

	/** Every group in the stomach now. */
	public static Set<Group> eaten(List<Stomach.Meal> meals) {
		EnumSet<Group> groups = EnumSet.noneOf(Group.class);
		for (Stomach.Meal meal : meals) {
			groups.addAll(of(meal.item()));
		}
		return groups;
	}

	/** The health a diet adds (or takes off). */
	public static float bonus(List<Stomach.Meal> meals) {
		int count = eaten(meals).size();
		if (count >= BALANCED) {
			return BALANCED_HEALTH;
		}
		if (count >= VARIED) {
			return BALANCED_HEALTH / 2.0F;
		}
		long food = meals.stream().filter(m -> !m.drink()).count();
		return count <= 1 && food >= 2 ? -SAMEY_HEALTH : 0.0F;
	}
}
