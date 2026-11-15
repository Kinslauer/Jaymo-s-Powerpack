package com.jaymospowers.power.chrono.future;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Andy (2026-11-15): "This flashlight should only be found and available in the Future like all the guns." Illuminated's
 * flashlight (Lambda License, by LambdAurora and Akarys; shipped unchanged, read by id, never linked) is future tech, the
 * way TaCZ's modern guns are ({@code armory/TaczGuns}): its recipe is gone ({@code tinkering/Uncraftable}), it's in no
 * creative tab (so not in JEI either), and the only ones in the game are found in the future: in its chests (Lost
 * Cities' and every other chest loot rolled there, {@link #CHEST_CHANCE}) and in the off hands of the corpse zombies,
 * the dead who were people once ({@link #carry}), lit, so a beam swings through the dark ruins as they come. One brought
 * back from the future is yours to keep. Nothing here without Illuminated.
 */
public final class FutureFlashlights {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("illuminated");
	public static final Identifier ID = Identifier.fromNamespaceAndPath("illuminated", "flashlight");
	/** Illuminated's on/off switch on the flashlight (a boolean component). */
	static final Identifier ON = Identifier.fromNamespaceAndPath("illuminated", "on");

	/** The chance a chest's loot rolled in the future holds a flashlight. */
	static final float CHEST_CHANCE = 0.15F;
	/** The chance a corpse zombie carries one, and the chance it drops when it's put down. */
	static final float CARRY_CHANCE = 0.3F, DROP_CHANCE = 0.5F;

	private FutureFlashlights() {
	}

	static void init() {
		if (!LOADED) {
			return;
		}
		CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
			output.getDisplayStacks().removeIf(FutureFlashlights::is);
			output.getSearchTabStacks().removeIf(FutureFlashlights::is);
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			var key = table.unwrapKey();
			if (key.isEmpty() || !key.get().identifier().getPath().startsWith("chests/")
				|| context.getLevel().dimension() != Future.FUTURE || context.getRandom().nextFloat() >= CHEST_CHANCE) {
				return;
			}
			ItemStack light = flashlight(false);
			if (!light.isEmpty()) {
				drops.add(light);
			}
		});
	}

	/** Whether {@code stack} is Illuminated's flashlight. */
	public static boolean is(ItemStack stack) {
		return LOADED && !stack.isEmpty() && ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/** A flashlight, switched on or off; empty without Illuminated. */
	@SuppressWarnings("unchecked")
	static ItemStack flashlight(boolean on) {
		Item item = LOADED ? BuiltInRegistries.ITEM.getValue(ID) : Items.AIR;
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ON);
		if (on && type != null) {
			stack.set((DataComponentType<Boolean>) type, true);
		}
		return stack;
	}

	/** Now and then a corpse zombie (one of the dead who were people) comes with a lit flashlight in its off hand. */
	static void carry(Mob corpse, RandomSource random) {
		if (!LOADED || random.nextFloat() >= CARRY_CHANCE || !corpse.getOffhandItem().isEmpty()) {
			return;
		}
		ItemStack light = flashlight(true);
		if (!light.isEmpty()) {
			corpse.setItemSlot(EquipmentSlot.OFFHAND, light);
			corpse.setDropChance(EquipmentSlot.OFFHAND, DROP_CHANCE);
		}
	}
}
