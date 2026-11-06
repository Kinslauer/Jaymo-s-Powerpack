package com.jaymoskingdoms.client.compat;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.TierHidingClient;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * (TierHiding) JEI leaves out every item hidden from this player ({@link TierHidingClient}): off its list, and off every
 * recipe page that makes it or needs it (JEI hides a recipe whose ingredients it no longer shows). Each version of an
 * item JEI knows (enchanted books, potions, forged parts) goes with it. Only loaded by JEI, through its entrypoint.
 */
@JeiPlugin
public final class TierJei implements IModPlugin {
	private static final Set<UidContext> EVERYWHERE = EnumSet.allOf(UidContext.class);
	private static IJeiRuntime runtime;
	private static List<ItemStack> hiddenNow = List.of();
	private static Set<Item> hiddenItems = Set.of();
	private static boolean listening;

	@Override
	public Identifier getPluginUid() {
		return JaymosKingdoms.id("tier_hiding");
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
		hiddenNow = List.of();
		hiddenItems = Set.of();
		if (!listening) {
			listening = true;
			TierHidingClient.listen(TierJei::apply);
		}
		apply();
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
		hiddenNow = List.of();
		hiddenItems = Set.of();
	}

	private static void apply() {
		if (runtime == null) {
			return;
		}
		try {
			var visibility = runtime.getJeiHelpers().getIngredientVisibility();
			Set<Item> hide = TierHidingClient.hidden();
			List<ItemStack> show = new ArrayList<>();
			List<ItemStack> now = new ArrayList<>();
			for (ItemStack stack : hiddenNow) {
				(hide.contains(stack.getItem()) ? now : show).add(stack);
			}
			if (!show.isEmpty()) {
				visibility.unhideIngredients(VanillaTypes.ITEM_STACK, show, EVERYWHERE);
			}
			List<ItemStack> fresh = new ArrayList<>();
			if (!hide.isEmpty()) {
				for (ItemStack stack : runtime.getIngredientManager().getAllItemStacks()) {
					if (hide.contains(stack.getItem()) && !hiddenItems.contains(stack.getItem())) {
						fresh.add(stack);
					}
				}
			}
			if (!fresh.isEmpty()) {
				visibility.hideIngredients(VanillaTypes.ITEM_STACK, fresh, EVERYWHERE);
				now.addAll(fresh);
			}
			hiddenItems = Set.copyOf(hide);
			hiddenNow = List.copyOf(now);
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Tier hiding couldn't update JEI", e);
		}
	}
}
