package com.jaymoskitchen.corpse;

import com.jaymoskitchen.butchery.Butchery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * What butchering gives depends on what's left of the body. Worked out once, the first time it is worked on, and taken from
 * its loot: less meat and hide where a limb or the head is gone (cut away or eaten) and where the flesh is eaten down
 * to bone; rotten flesh in place of the meat that rotted ({@link Wear#DARK}); cooked meat where it burned, and no hide
 * ({@link Wear#CHAR}). A hide torn at the kill (mauled, bruised) was already lessened ({@link CleanKill}).
 */
final class Yield {
	/** The share of meat and hide each lost limb takes, and the head. */
	static final float LIMB_SHARE = 0.15F, HEAD_SHARE = 0.08F;
	/** The most a body's missing parts take. */
	static final float LOST_MOST = 0.7F;
	/** The char level from which the hide is burnt away, and the meat cooked right through. */
	static final int HIDE_BURNS_AT = 2, COOKED_AT = 2;

	private Yield() {
	}

	/** Changes {@code loot} (the body's) to what's left of {@code corpse}. */
	static void settle(CorpseEntity corpse, ServerLevel level, List<ItemStack> loot) {
		int wear = corpse.wear();
		int missing = corpse.missing();
		float lost = Math.min(LOST_MOST, Integer.bitCount(missing & CorpseEntity.LIMBS) * LIMB_SHARE
			+ ((missing & CorpseEntity.PART_HEAD) != 0 ? HEAD_SHARE : 0.0F));
		float bare = Wear.get(wear, Wear.BARE) / (float) Wear.LEVELS;
		float rotten = Wear.get(wear, Wear.DARK) / (float) Wear.LEVELS;
		int charred = Wear.get(wear, Wear.CHAR);
		boolean burnt = charred > 0 || corpse.burnTicks() > 0;
		List<ItemStack> settled = new ArrayList<>();
		for (ItemStack stack : loot) {
			if (stack.isEmpty()) {
				continue;
			}
			int count = stack.getCount();
			if (stack.is(Butchery.SKINS)) {
				// hide: less where it's cut or torn away, none where it burned
				int keep = burnt && charred >= HIDE_BURNS_AT ? 0 : count - Math.round(count * Math.max(lost, bare * 0.5F));
				add(settled, stack.copyWithCount(keep));
				continue;
			}
			if (!stack.has(DataComponents.FOOD) || stack.is(Items.ROTTEN_FLESH)) {
				add(settled, stack);
				continue;
			}
			// meat: what's eaten to the bone or cut away is gone (a little always left unless it's all gone)
			int keep = count - Math.round(count * Math.min(1.0F, Math.max(lost, bare)));
			if (keep <= 0 && bare < 1.0F && lost < LOST_MOST) {
				keep = 1;
			}
			// what rotted is rotten flesh
			int spoilt = Math.min(keep, Math.round(keep * rotten));
			add(settled, new ItemStack(Items.ROTTEN_FLESH, spoilt));
			keep -= spoilt;
			// what burned is cooked (all of it past the first char; half of it before)
			int cooked = burnt ? (charred >= COOKED_AT ? keep : (keep + 1) / 2) : 0;
			if (cooked > 0) {
				add(settled, cook(level, stack, cooked));
			}
			add(settled, stack.copyWithCount(keep - cooked));
		}
		loot.clear();
		loot.addAll(settled);
	}

	private static void add(List<ItemStack> into, ItemStack stack) {
		if (!stack.isEmpty()) {
			into.add(stack);
		}
	}

	/** {@code count} of {@code raw} cooked as a campfire cooks it (as it was, if it doesn't cook). */
	private static ItemStack cook(ServerLevel level, ItemStack raw, int count) {
		ItemStack one = raw.copyWithCount(1);
		return level.recipeAccess().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(one), level)
			.map(recipe -> recipe.value().assemble(new SingleRecipeInput(one)))
			.filter(result -> !result.isEmpty())
			.map(result -> result.copyWithCount(Math.min(result.getMaxStackSize(), result.getCount() * count)))
			.orElse(raw.copyWithCount(count));
	}
}
