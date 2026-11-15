package com.jaymospowers.tinkering.smithing;

import java.util.Optional;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Andy (2026-11-15): "diamond gear should be removed ... netherite should remain as plating but for steel armor". Diamond
 * gear was already uncraftable; this takes it out of what the world hands out. Any diamond armour, tool or weapon (vanilla's,
 * the Armory's, WeaponMod's) that comes out of a loot table (chests, mob drops, bartering) or is on a villager's or
 * trader's offers becomes its plainer kin: armour the Armory's steel plate (Jaymo's Kingdoms) or iron without it, the rest
 * the iron one of the same kind. Enchantments, names and the rest of the stack come along. Pieces already in a world stay.
 */
public final class NoDiamondGear {
	private NoDiamondGear() {
	}

	static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> drops.replaceAll(NoDiamondGear::swap));
		// before GradedLoot's late phase, so the steel or iron that replaces it gets its grade
		UseEntityCallback.EVENT.register(Event.DEFAULT_PHASE, (player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && entity instanceof AbstractVillager villager && villager.getTradingPlayer() == null) {
				swap(villager.getOffers());
			}
			return InteractionResult.PASS;
		});
	}

	/** Every offer that sells diamond gear sells its steel or iron kin instead (same price, uses and the rest). */
	private static void swap(MerchantOffers offers) {
		for (int i = 0; i < offers.size(); i++) {
			MerchantOffer o = offers.get(i);
			ItemStack kin = swap(o.getResult());
			if (kin != o.getResult()) {
				MerchantOffer n = new MerchantOffer(o.getItemCostA(), o.getItemCostB(), kin, o.getUses(), o.getMaxUses(), o.getXp(),
					o.getPriceMultiplier(), o.getDemand());
				offers.set(i, n);
			}
		}
	}

	/** {@code stack} as its steel or iron kin if it's diamond gear; anything else as it is (the same stack). */
	public static ItemStack swap(ItemStack stack) {
		if (stack.isEmpty() || !(stack.isDamageableItem() || stack.has(DataComponents.EQUIPPABLE))) {
			return stack;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String path = id.getPath();
		if (!path.matches("(.*[_.])?diamond([_.].*)?") || path.equals("diamond")) {
			return stack;
		}
		String iron = path.replaceFirst("diamond", "iron");
		Optional<Item> kin = Optional.empty();
		for (String piece : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
			if (path.equals("diamond_" + piece)) {
				kin = item("jaymoskingdoms", "steel_plate_" + piece);
			}
		}
		if (kin.isEmpty()) {
			kin = item(id.getNamespace(), iron).or(() -> item("minecraft", iron));
		}
		return kin.map(stack::transmuteCopy).orElse(stack);
	}

	private static Optional<Item> item(String namespace, String path) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(namespace, path));
	}
}
