package com.jaymoskitchen.tanning;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.butchery.Butchery;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Hides that remember their animal (Andy's skeleton and hide plan, 2026-11-13: "ill take your reccomendation"). A hide
 * is stamped with the animal it came off ({@link #HIDE_OF}, the animal's entity type id) where it drops, and every step
 * of tanning carries the stamp on: raw, salted, dried, wet tanned, leather, the bark-soaked tanned leather and its three
 * finishes. Stamped leather stacks only with leather of the same animal; old leather stays plain.
 *
 * <p>Crafting takes the animal most of its hides came from ({@link #majority}); a tie, or any plain hide in the mix,
 * gives a plain result. Leather armour made of one animal's leather takes that animal's small trait ({@link HideTraits}),
 * which nothing in game lists (Andy: "the traits are fine, but dont list them anywhere in game"): the tooltip names only
 * the leather ("Cowhide"). Two hides of one animal make that animal's rug ({@link #RUGS}).
 */
public final class HideOrigin {
	public static DataComponentType<Identifier> HIDE_OF;

	/** Rugs by animal: two hides of one animal make its own rug, tinted and named for it. */
	public static final Map<Identifier, Block> RUGS = new LinkedHashMap<>();
	/** The animals with a rug of their own (the vanilla and Naturalist animals that give hides), by rug name. */
	public static final Map<String, String> RUG_ANIMALS = new LinkedHashMap<>();

	static {
		for (String vanilla : List.of("cow", "mooshroom", "horse", "donkey", "mule", "llama", "camel", "goat", "pig", "sheep", "hoglin")) {
			RUG_ANIMALS.put(vanilla, "minecraft:" + vanilla);
		}
		for (String naturalist : List.of("zebra", "deer", "rhino", "alligator")) {
			RUG_ANIMALS.put(naturalist, "naturalist:" + naturalist);
		}
	}

	private HideOrigin() {
	}

	public static void register() {
		HIDE_OF = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("hide_of"),
			DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build());
	}

	/** Called by {@link Butchery} as it registers the plain rug: each animal's own. */
	public static void registerRugs(java.util.function.Function<String, Block> rug) {
		for (Map.Entry<String, String> animal : RUG_ANIMALS.entrySet()) {
			RUGS.put(Identifier.parse(animal.getValue()), rug.apply(animal.getKey() + "_hide_rug"));
		}
	}

	private static @Nullable Set<Item> carriers;

	/** What carries a hide's animal through tanning and into what's made of it. */
	static Set<Item> carriers() {
		if (carriers == null) {
			carriers = Set.of(Hides.HIDE, Hides.RAW_HIDE, Hides.SALTED_HIDE, Hides.DRIED_HIDE, Hides.WET_TANNED_HIDE, Items.LEATHER,
			Tanning.TANNED_LEATHER, Butchery.SUPPLE_LEATHER, Butchery.HARNESS_LEATHER, Butchery.HARDENED_LEATHER, Items.LEATHER_HELMET,
			Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
		}
		return carriers;
	}

	public static boolean carries(ItemStack stack) {
		return !stack.isEmpty() && (carriers().contains(stack.getItem()) || stack.is(Items.RABBIT_HIDE));
	}

	/** The animal a hide or a piece of leather came off, or null for a plain one. A rabbit's own hide is always a rabbit's. */
	public static @Nullable Identifier of(ItemStack stack) {
		if (stack.is(Items.RABBIT_HIDE)) {
			return Identifier.withDefaultNamespace("rabbit");
		}
		return HIDE_OF == null ? null : stack.get(HIDE_OF);
	}

	/** Stamps a fresh hide (or the leather an animal drops) with the animal it came off. */
	public static ItemStack stamp(ItemStack stack, @Nullable EntityType<?> animal) {
		if (animal != null && HIDE_OF != null && carriers().contains(stack.getItem())) {
			stack.set(HIDE_OF, BuiltInRegistries.ENTITY_TYPE.getKey(animal));
		}
		return stack;
	}

	/** {@code to} made from {@code from}: it remembers the same animal. */
	public static ItemStack carry(ItemStack from, ItemStack to) {
		Identifier animal = of(from);
		if (animal != null && HIDE_OF != null && carriers().contains(to.getItem())) {
			to.set(HIDE_OF, animal);
		}
		return to;
	}

	/**
	 * The animal most of these hides came from (only hides and leather count, not the knife or the salt), or null if any
	 * of them is plain or two animals tie.
	 */
	public static @Nullable Identifier majority(List<ItemStack> inputs) {
		Map<Identifier, Integer> counts = new HashMap<>();
		for (ItemStack stack : inputs) {
			if (!carries(stack)) {
				continue;
			}
			Identifier animal = of(stack);
			if (animal == null) {
				return null;
			}
			counts.merge(animal, 1, Integer::sum);
		}
		Identifier best = null;
		int most = 0;
		boolean tie = false;
		for (Map.Entry<Identifier, Integer> entry : counts.entrySet()) {
			if (entry.getValue() > most) {
				best = entry.getKey();
				most = entry.getValue();
				tie = false;
			} else if (entry.getValue() == most) {
				tie = true;
			}
		}
		return tie ? null : best;
	}

	/**
	 * A crafted result made from these inputs: a carrier remembers their animal (and leather armour takes its trait); a
	 * plain rug made of one animal's hides becomes that animal's rug.
	 */
	public static ItemStack crafted(ItemStack result, List<ItemStack> inputs) {
		if (result.isEmpty() || HIDE_OF == null) {
			return result;
		}
		boolean rug = result.is(Butchery.HIDE_RUG.asItem());
		if (!rug && !carriers().contains(result.getItem())) {
			return result;
		}
		Identifier animal = majority(inputs);
		if (animal == null) {
			return result;
		}
		if (rug) {
			Block own = RUGS.get(animal);
			return own == null ? result : new ItemStack(own, result.getCount());
		}
		ItemStack stamped = result.copy();
		stamped.set(HIDE_OF, animal);
		HideTraits.onMade(stamped, animal);
		return stamped;
	}

	/** "Cowhide", "Zebra hide": the leather's name, from the animal's. */
	public static net.minecraft.network.chat.MutableComponent leatherName(Identifier animal) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, animal);
		Component name = BuiltInRegistries.ENTITY_TYPE.getOptional(key).map(EntityType::getDescription)
			.orElse(Component.literal(animal.getPath()));
		return Component.translatable("tooltip.jaymoskitchen.hide_of", name);
	}
}
