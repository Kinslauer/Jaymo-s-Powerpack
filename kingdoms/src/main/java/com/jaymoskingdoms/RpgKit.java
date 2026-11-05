package com.jaymoskingdoms;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * (0.7.520) The RPG Series' kit (Wizards, Archers, Rogues & Warriors, shipped unchanged in the pack) where Kingdoms hands gear
 * out: class starts ({@link Classes#gear}), guards and archers ({@link Outfits}), court wizards and hermits. Every id is looked
 * up, so without the mods the old gear stays.
 */
public final class RpgKit {
	/** A veteran guard's chance of a glaive, and of a double axe, instead of a sword or spear. */
	static final float VETERAN_GLAIVE = 0.3F, VETERAN_DOUBLE_AXE = 0.15F;
	/** A realm archer's chance of the Archers' tunic, and of its hood. */
	static final float ARCHER_TUNIC = 0.5F, ARCHER_HOOD = 0.4F;
	/** A slain court wizard's chance of dropping a wand of their school. */
	static final float WIZARD_WAND_DROP = 0.35F;

	private RpgKit() {
	}

	static Optional<Item> item(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
	}

	/** One of {@code id}, or {@code fallback} (also an id) when the mod is missing. */
	static ItemStack or(String id, String fallback) {
		return item(id).or(() -> item(fallback)).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	/** The Wizards wand for a Mage's first school (warding is Frost; mending, no school, the novice's). */
	static String wand(String option) {
		return switch (option) {
			case "fire" -> "wizards:wand_fire";
			case "arcane" -> "wizards:wand_arcane";
			case "warding" -> "wizards:wand_frost";
			default -> "wizards:wand_novice";
		};
	}

	/** A random school's wand, for a court wizard's body. */
	static Optional<Item> randomWand(RandomSource random) {
		return item(switch (random.nextInt(3)) {
			case 0 -> "wizards:wand_fire";
			case 1 -> "wizards:wand_frost";
			default -> "wizards:wand_arcane";
		});
	}

	/** Puts the Wizards' pointed hat on a court wizard or hermit, over the realm's robes ({@link Uniforms}), if bareheaded. */
	static void robe(net.minecraft.world.entity.Mob wizard) {
		var head = net.minecraft.world.entity.EquipmentSlot.HEAD;
		if (wizard.getItemBySlot(head).isEmpty()) {
			item("wizards:wizard_robe_head").ifPresent(hat -> {
				wizard.setItemSlot(head, new ItemStack(hat));
				wizard.setDropChance(head, 0.0F);
			});
		}
	}
}
