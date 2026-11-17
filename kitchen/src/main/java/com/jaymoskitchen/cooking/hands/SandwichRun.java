package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Step;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A sandwich stacked on the board: the layers put there in order (bread first, bread last, the fillings between) are
 * cut through with the knife and come out as one sandwich that is as filling as the layers were. Three different fillings
 * or more make it a fine one (the "made well" mark). It is done in the moment of the cut: there is nothing to time.
 */
final class SandwichRun extends Run {
	/** Different fillings for a fine sandwich. */
	static final int FINE_FILLINGS = 3;

	private final List<ItemStack> layers;

	SandwichRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		this.layers = new ArrayList<>(table.taken);
	}

	@Override
	void tick(int now) {
		int nutrition = 0;
		float saturation = 0.0F;
		Set<Item> fillings = new HashSet<>();
		List<Component> names = new ArrayList<>();
		for (ItemStack layer : layers) {
			FoodProperties food = layer.get(DataComponents.FOOD);
			if (food != null) {
				nutrition += food.nutrition();
				saturation += food.saturation();
			}
			if (!Step.BREAD.matches(layer)) {
				fillings.add(layer.getItem());
			}
			names.add(layer.getHoverName());
		}
		boolean fine = fillings.size() >= FINE_FILLINGS;
		int total = nutrition;
		float fed = saturation;
		endWith((fine ? Prep.Result.PERFECT : Prep.Result.GOOD).ordinal(), () -> {
			ItemStack sandwich = new ItemStack(HandsBlocks.SANDWICH);
			HandsBlocks.stamp(sandwich, total, fed, names);
			table.handOver(this, sandwich);
			table.playBlock(SoundEvents.PLAYER_LEVELUP, fine ? 1.0F : 0.6F, 1.4F);
		});
	}

	@Override
	void leave() {
		// it was only ever a moment: what was stacked comes out as it is
		tick(0);
	}

	@Override
	void fill(List<Integer> ints, List<Float> floats) {
	}
}
