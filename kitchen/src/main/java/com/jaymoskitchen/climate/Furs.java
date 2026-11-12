package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Furs for linings (Andy's TFC Hardrock picks): a wolf's, a bear's (polar, and Naturalist's bear and black bear), and the wild
 * fur of Naturalist's lions and tigers. Each comes off the beast when it dies; a tailor sews them in
 * ({@link Tailoring}, {@link Lining}). They're cured pelts, so they don't rot.
 */
public final class Furs {
	public static Item WOLF_FUR, BEAR_FUR, WILD_FUR;

	/** Chance (out of 1) a wolf leaves a fur, and the most a bear or a great cat leaves. */
	static final float WOLF_CHANCE = 0.7F;
	static final int BEAR_MOST = 2, WILD_MOST = 2;

	private static final List<String> BEARS = List.of("entities/polar_bear", "entities/bear", "entities/black_bear");
	private static final List<String> WILD = List.of("entities/lion", "entities/tiger");

	private Furs() {
	}

	static void register() {
		WOLF_FUR = item("wolf_fur");
		BEAR_FUR = item("bear_fur");
		WILD_FUR = item("wild_fur");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(WOLF_FUR);
			output.accept(BEAR_FUR);
			output.accept(WILD_FUR);
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			String path = table.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
			if (path.equals("entities/wolf")) {
				if (context.getRandom().nextFloat() < WOLF_CHANCE) {
					drops.add(new ItemStack(WOLF_FUR));
				}
			} else if (BEARS.contains(path)) {
				drops.add(new ItemStack(BEAR_FUR, 1 + context.getRandom().nextInt(BEAR_MOST)));
			} else if (WILD.contains(path)) {
				drops.add(new ItemStack(WILD_FUR, 1 + context.getRandom().nextInt(WILD_MOST)));
			}
		});
	}

	private static Item item(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}
}
