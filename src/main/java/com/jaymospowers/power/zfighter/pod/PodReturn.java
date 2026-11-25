package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.fuel.PodFuel;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Packing for home. Lifting off from a far world takes a return stage (an item, spent on the lift) as well as the fuel the way
 * home burns: the star map lists both before you launch, and says if the pod will be short. A stage is carried in the pilot's pack
 * or the pod's cargo hold; the hold's are used first.
 */
public final class PodReturn {
	public static Item STAGE;
	private static boolean ready;

	private PodReturn() {
	}

	/** Registers the return stage. Called once, from {@link PodTravel#init}. */
	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("return_stage"));
		STAGE = Registry.register(BuiltInRegistries.ITEM, key, new PodFuel.TipItem(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON),
			"item.jaymospowers.return_stage.tooltip"));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(STAGE));
	}

	/** How many return stages {@code player} has packed: in the hold of {@code pod} (when it has one) and in their pack. */
	public static int packed(ServerPlayer player, PodEntity pod) {
		int count = 0;
		if (PodModules.has(pod, PodModules.Kind.HOLD)) {
			count += countIn(pod.holdContainer());
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(STAGE)) {
				count += inventory.getItem(i).getCount();
			}
		}
		return count;
	}

	private static int countIn(SimpleContainer container) {
		int count = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(STAGE)) {
				count += container.getItem(i).getCount();
			}
		}
		return count;
	}

	/** Spends one return stage, the hold's first; false when there is none. */
	public static boolean spend(ServerPlayer player, PodEntity pod) {
		if (PodModules.has(pod, PodModules.Kind.HOLD) && spendFrom(pod.holdContainer())) {
			return true;
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(STAGE)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static boolean spendFrom(SimpleContainer container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(STAGE)) {
				stack.shrink(1);
				container.setChanged();
				return true;
			}
		}
		return false;
	}
}
