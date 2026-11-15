package com.jaymospowers.armory;

import com.jaymospowers.tinkering.WeaponParts;
import com.jaymospowers.tinkering.smithing.Blueprints;
import com.jaymospowers.tinkering.smithing.Forging;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Weapon;

/**
 * (medieval picks, cv-weapons) The arms of the old age's peoples (Lore: the old age's peoples), each people its own: the
 * sands' hooked <b>sickle-sword</b>, the plains' short <b>legion blade</b> and <b>tall shield</b> and their leaf-bladed
 * <b>spear</b> and <b>round shield</b>, Blossom's <b>broad sabre</b>, the north's <b>bearded axe</b>. Ideas from
 * Civilizations (all rights reserved: nothing of it is copied; the names, numbers, art and code are ours).
 * <ul>
 * <li>They lie in the old ruins and in their guards' hands (Jaymo's Kingdoms). Each is iron, and its head is forged like
 * any other ({@code tinkering/WeaponParts}), but only from a <b>pattern</b>: the anvil won't start one unless its smith
 * carries a blueprint of its shape, which comes only from taking a found piece apart at the Drafting Table
 * ({@code smithing/Patterns}). No cast is made of one and the Part Builder doesn't shape it
 * ({@link WeaponParts#OLD_AGE}).</li>
 * <li>Each swings with its own Better Combat moves ({@code data/jaymospowers/weapon_attributes}): the sickle-sword's
 * hooking cuts, the legion blade's quick stabs, the spear's reach, the sabre's broad sweeps, the axe's chops. The
 * sickle-sword's hook and the bearded axe's beard drag a raised shield down.</li>
 * </ul>
 * Art, models and Better Combat files: {@code tools/gen_old_age_arms.py}.
 */
public final class OldAgeArms {
	// ---- tuning ----
	/** Damage and attack speed over iron's (as a sword's and an axe's are given). */
	static final float SICKLE_DAMAGE = 3.5F, SICKLE_SPEED = -2.5F;
	static final float LEGION_DAMAGE = 2.5F, LEGION_SPEED = -2.0F;
	static final float SPEAR_DAMAGE = 3.0F, SPEAR_SPEED = -2.7F;
	static final float SABRE_DAMAGE = 4.0F, SABRE_SPEED = -2.6F;
	static final float AXE_DAMAGE = 5.5F, AXE_SPEED = -3.0F;
	/** Seconds a shield the sickle-sword hooks, or the bearded axe drags, stays down. */
	static final float SICKLE_SHIELD_BREAK = 3.0F, AXE_SHIELD_BREAK = 4.0F;
	/** The tall shield: slow to raise, wide; the round shield: quick, middling (Armory's: 0.15 s, 90 degrees). */
	static final float TALL_RAISE = 0.3F, TALL_ARC = 140.0F, ROUND_RAISE = 0.15F, ROUND_ARC = 100.0F;
	/** How long each shield lasts. */
	static final int TALL_DURABILITY = 520, ROUND_DURABILITY = 400;

	public static Item SICKLE_SWORD, LEGION_SWORD, TALL_SHIELD, LEAF_SPEAR, OLD_ROUND_SHIELD, BROAD_SABRE, BEARDED_AXE;

	private OldAgeArms() {
	}

	static void init() {
		SICKLE_SWORD = Armory.item("sickle_sword", Item::new, p -> p.sword(ToolMaterial.IRON, SICKLE_DAMAGE, SICKLE_SPEED)
			.component(DataComponents.WEAPON, new Weapon(1, SICKLE_SHIELD_BREAK)));
		LEGION_SWORD = Armory.item("legion_sword", Item::new, p -> p.sword(ToolMaterial.IRON, LEGION_DAMAGE, LEGION_SPEED));
		LEAF_SPEAR = Armory.item("leaf_spear", Item::new, p -> p.sword(ToolMaterial.IRON, SPEAR_DAMAGE, SPEAR_SPEED));
		BROAD_SABRE = Armory.item("broad_sabre", Item::new, p -> p.sword(ToolMaterial.IRON, SABRE_DAMAGE, SABRE_SPEED));
		BEARDED_AXE = Armory.item("bearded_axe", Item::new, p -> p.axe(ToolMaterial.IRON, AXE_DAMAGE, AXE_SPEED)
			.component(DataComponents.WEAPON, new Weapon(1, AXE_SHIELD_BREAK)));
		TALL_SHIELD = Armory.item("tall_shield", Item::new, p -> shield(p, TALL_DURABILITY, TALL_RAISE, TALL_ARC));
		OLD_ROUND_SHIELD = Armory.item("old_round_shield", Item::new, p -> shield(p, ROUND_DURABILITY, ROUND_RAISE, ROUND_ARC));
		// an old age's head is forged only from its pattern
		Forging.CHECKS.add((smith, anvil, option) -> oldAge(option) && Blueprints.find(smith, option) == null
			? Component.translatable("message.jaymospowers.smithing.needs_pattern", Blueprints.shapeName(Blueprints.shape(option))) : null);
	}

	/** {@code option} forges one of the old age's heads. */
	static boolean oldAge(Forging.Option option) {
		for (String part : WeaponParts.OLD_AGE) {
			if (option.id().startsWith("weapon/" + part + "/")) {
				return true;
			}
		}
		return false;
	}

	private static Item.Properties shield(Item.Properties p, int durability, float raise, float arc) {
		return p.durability(durability)
			.repairable(Items.IRON_INGOT)
			.enchantable(ToolMaterial.IRON.enchantmentValue())
			.equippableUnswappable(EquipmentSlot.OFFHAND)
			.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(raise, 1.0F,
				List.of(new BlocksAttacks.DamageReduction(arc, Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK);
	}

}
