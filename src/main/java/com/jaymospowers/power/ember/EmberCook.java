package com.jaymospowers.power.ember;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Cook in your hands (Andy's pick; the power users use their powers in daily life): an Emberborn sneaking with something
 * raw in the off hand (anything a furnace, smoker or campfire cooks into food) cooks it, one at a time, {@link #TICKS}
 * ticks an item, with smoke wisps and a sizzle. The cooked food goes into the inventory, or drops at their feet. Letting
 * go of sneaking, or changing what is in the off hand, loses the item's progress.
 */
public final class EmberCook {
	/** Ticks to cook one item. */
	static final int TICKS = 20 * 8;

	private static final class Live {
		int progress;
		ItemStack of = ItemStack.EMPTY;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private EmberCook() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isShiftKeyDown() && player.isAlive() && !player.isSpectator() && EmberKindle.ember(player)
					&& !player.getOffhandItem().isEmpty()) {
					tick(player);
				} else {
					LIVE.remove(player.getUUID());
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LIVE.clear());
	}

	private static ItemStack result(ServerLevel level, ItemStack raw) {
		SingleRecipeInput input = new SingleRecipeInput(raw);
		ItemStack out = cooked(level, RecipeType.SMELTING, input);
		if (out.isEmpty()) {
			out = cooked(level, RecipeType.SMOKING, input);
		}
		if (out.isEmpty()) {
			out = cooked(level, RecipeType.CAMPFIRE_COOKING, input);
		}
		return out.has(DataComponents.FOOD) ? out : ItemStack.EMPTY;
	}

	private static <T extends AbstractCookingRecipe> ItemStack cooked(ServerLevel level, RecipeType<T> type, SingleRecipeInput input) {
		return level.recipeAccess().getRecipeFor(type, input, level).map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	private static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		ItemStack held = player.getOffhandItem();
		ItemStack out = result(level, held);
		if (out.isEmpty()) {
			LIVE.remove(player.getUUID());
			return;
		}
		Live live = LIVE.computeIfAbsent(player.getUUID(), k -> new Live());
		if (!ItemStack.isSameItemSameComponents(live.of, held)) {
			live.of = held.copyWithCount(1);
			live.progress = 0;
		}
		live.progress++;
		double y = player.getY() + 1.0;
		if (live.progress % 8 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), y, player.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
		}
		if (live.progress % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 0.5F, 1.3F);
		}
		if (live.progress >= TICKS) {
			live.progress = 0;
			held.shrink(1);
			player.getInventory().placeItemBackInInventory(out.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
			level.sendParticles(ParticleTypes.FLAME, player.getX(), y, player.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 0.8F);
		}
	}
}
