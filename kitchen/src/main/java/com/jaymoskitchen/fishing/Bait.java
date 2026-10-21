package com.jaymoskitchen.fishing;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fishing bait (after Better With Mods' baited rods, but never required). Fish Bait held in the other hand from the
 * rod makes the fish come sooner (like Lure) and the catch a little luckier (like Luck of the Sea); one bait goes per
 * fish landed. With or without bait, water that's shallow under the bobber or roofed over (no sky above it) bites
 * slower.
 */
public final class Bait {
	/** Ticks bait takes off the wait for a fish (Lure takes 100 a level); the luck it adds to the catch. */
	static final int BAIT_SPEED = 100;
	static final float BAIT_LUCK = 1.0F;
	/** Shallow or roofed water: the wait for a fish is this many times as long. Shallow is under this many blocks deep. */
	static final float SHALLOW_SLOW = 1.5F;
	static final int SHALLOW_DEPTH = 2;

	public static Item FISH_BAIT;

	private Bait() {
	}

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("fish_bait"));
		FISH_BAIT = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(FISH_BAIT));
	}

	/** The bait in the hand that isn't holding the rod, or empty. */
	static ItemStack bait(Player player) {
		ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
		ItemStack other = main.getItem() instanceof FishingRodItem ? off : off.getItem() instanceof FishingRodItem ? main : ItemStack.EMPTY;
		return other.is(FISH_BAIT) ? other : ItemStack.EMPTY;
	}

	/** A fresh wait of {@code ticks} for a fish to come to the hook at {@code pos}: slower in shallow or roofed water, faster baited. */
	public static int lured(FishingHook hook, BlockPos pos, int ticks) {
		Level level = hook.level();
		float t = ticks;
		if (shallow(level, pos) || !level.canSeeSky(pos.above())) {
			t *= SHALLOW_SLOW;
		}
		Player owner = hook.getPlayerOwner();
		if (owner != null && !bait(owner).isEmpty()) {
			t -= BAIT_SPEED;
		}
		return Math.max(1, Math.round(t));
	}

	private static boolean shallow(Level level, BlockPos pos) {
		for (int d = 1; d < SHALLOW_DEPTH; d++) {
			if (!level.getFluidState(pos.below(d)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** A fish is landed with {@code luck}: if the angler is baited, one bait goes and the catch is luckier. */
	public static float caught(FishingHook hook, float luck) {
		Player owner = hook.getPlayerOwner();
		ItemStack bait = owner == null ? ItemStack.EMPTY : bait(owner);
		if (bait.isEmpty()) {
			return luck;
		}
		if (!owner.hasInfiniteMaterials()) {
			bait.shrink(1);
		}
		return luck + BAIT_LUCK;
	}
}
