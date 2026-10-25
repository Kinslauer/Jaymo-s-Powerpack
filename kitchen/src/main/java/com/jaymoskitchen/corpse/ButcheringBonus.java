package com.jaymoskitchen.corpse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The extras that only come from butchering properly, on a butcher block: hides, bones, feathers and the
 * occasional rare find. Chopping an animal up on the ground gets none of this.
 */
final class ButcheringBonus {
	private record Extra(Item item, int min, int max, float chance) {
	}

	private static final List<Extra> BONES = List.of(new Extra(Items.BONE, 1, 2, 0.4F));
	private static final List<Extra> HIDE = List.of(new Extra(Items.LEATHER, 1, 3, 0.7F), new Extra(Items.BONE, 1, 2, 0.4F));

	private static final Map<EntityType<?>, List<Extra>> EXTRAS = Map.ofEntries(
		Map.entry(EntityTypes.COW, HIDE),
		Map.entry(EntityTypes.MOOSHROOM, HIDE),
		Map.entry(EntityTypes.HORSE, HIDE),
		Map.entry(EntityTypes.DONKEY, HIDE),
		Map.entry(EntityTypes.MULE, HIDE),
		Map.entry(EntityTypes.LLAMA, HIDE),
		Map.entry(EntityTypes.CAMEL, HIDE),
		Map.entry(EntityTypes.GOAT, List.of(new Extra(Items.LEATHER, 1, 1, 0.4F), new Extra(Items.BONE, 1, 2, 0.4F))),
		Map.entry(EntityTypes.SHEEP, List.of(new Extra(Items.BONE, 1, 2, 0.4F), new Extra(Items.STRING, 1, 2, 0.25F),
			new Extra(Items.LEATHER, 1, 1, 0.15F))),
		Map.entry(EntityTypes.PIG, List.of(new Extra(Items.BONE, 1, 2, 0.4F), new Extra(Items.LEATHER, 1, 1, 0.15F))),
		Map.entry(EntityTypes.CHICKEN, List.of(new Extra(Items.FEATHER, 1, 3, 0.7F), new Extra(Items.EGG, 1, 1, 0.15F))),
		Map.entry(EntityTypes.RABBIT, List.of(new Extra(Items.RABBIT_HIDE, 1, 1, 0.6F), new Extra(Items.RABBIT_FOOT, 1, 1, 0.2F))));

	/**
	 * Animal fat from the fatty animals (rendered into tallow, {@link com.jaymoskitchen.tanning.Tanning}): the chance, and
	 * how much at most. Looked up when rolled, since the fat item is registered after this class could load.
	 */
	private record Fat(float chance, int max) {
	}

	private static final Map<EntityType<?>, Fat> FAT = Map.ofEntries(
		Map.entry(EntityTypes.PIG, new Fat(0.85F, 3)),
		Map.entry(EntityTypes.COW, new Fat(0.7F, 2)),
		Map.entry(EntityTypes.MOOSHROOM, new Fat(0.7F, 2)),
		Map.entry(EntityTypes.SHEEP, new Fat(0.6F, 2)),
		Map.entry(EntityTypes.GOAT, new Fat(0.4F, 1)),
		Map.entry(EntityTypes.HORSE, new Fat(0.4F, 2)),
		Map.entry(EntityTypes.DONKEY, new Fat(0.4F, 1)),
		Map.entry(EntityTypes.MULE, new Fat(0.4F, 1)),
		Map.entry(EntityTypes.LLAMA, new Fat(0.4F, 1)),
		Map.entry(EntityTypes.CAMEL, new Fat(0.5F, 2)),
		Map.entry(EntityTypes.HOGLIN, new Fat(0.8F, 3)));

	private ButcheringBonus() {
	}

	/** Rolls the extras for butchering this kind of animal on a block. */
	static List<ItemStack> roll(@Nullable EntityType<?> type, RandomSource random) {
		List<ItemStack> drops = new ArrayList<>();
		for (Extra extra : type == null ? BONES : EXTRAS.getOrDefault(type, BONES)) {
			if (random.nextFloat() < extra.chance()) {
				// (0.5.27) leather comes off as a hide, to be tanned (Hides)
				drops.add(com.jaymoskitchen.tanning.Hides.hideFor(
					new ItemStack(extra.item(), extra.min() + random.nextInt(extra.max() - extra.min() + 1))));
			}
		}
		Fat fat = type == null ? null : FAT.get(type);
		if (fat != null && random.nextFloat() < fat.chance()) {
			drops.add(new ItemStack(com.jaymoskitchen.tanning.Tanning.ANIMAL_FAT, 1 + random.nextInt(fat.max())));
		}
		return drops;
	}
}
