package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;

/**
 * Light armour out in the world: a mob in the full mailed set runs at its target, and a bastion's piglin brute sometimes
 * studs its own hide with scrap (some of it enchanted), which keeps it from zombifying in the Overworld. The Armored Foes
 * equipment tables that hand the sets to other mobs are data (data/armoredfoes/loot_table/equipment).
 */
public final class LightFoes {
	// ===== the knobs ================================================================================================
	/** A mob in a full mailed set runs this much faster (a share of its base speed) while it has a target; checked every this many ticks. */
	static final double MAILED_RUN = 0.15;
	static final int CHECK_EVERY = 20;
	/** The share of piglin brutes that wear studded leather, and of those the share whose pieces are enchanted. */
	static final float BRUTE_STUDDED = 0.3F, STUDDED_ENCHANTED = 0.4F;
	// ================================================================================================================

	private static final Identifier RUN_ID = JaymosPowers.id("mailed_run");
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private LightFoes() {
	}

	/** {@code body} wears every piece of {@code set} ({@link LightArmour#MAILED} or {@link LightArmour#STUDDED}). */
	public static boolean wearsSet(LivingEntity body, Item[] set) {
		for (int i = 0; i < 4; i++) {
			if (!body.getItemBySlot(ARMOUR[i]).is(set[i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * (Every tick of a mob's {@code aiStep}) A mob wearing a mailed jack checks, once a second, whether it is in the full
	 * set and has a target: if so it runs, and the modifier comes off again when it doesn't.
	 */
	public static void tick(Mob mob) {
		if (mob.level().isClientSide() || mob.tickCount % CHECK_EVERY != 0) {
			return;
		}
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean has = speed.getModifier(RUN_ID) != null;
		boolean worn = mob.getItemBySlot(EquipmentSlot.CHEST).is(LightArmour.MAILED[1]);
		if (!worn && !has) {
			return;
		}
		boolean runs = worn && mob.getTarget() != null && wearsSet(mob, LightArmour.MAILED);
		if (runs && !has) {
			speed.addTransientModifier(new AttributeModifier(RUN_ID, MAILED_RUN, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		} else if (!runs && has) {
			speed.removeModifier(RUN_ID);
		}
	}

	/** (A mob's {@code finalizeSpawn}) A bastion's piglin brute, placed with the bastion and wearing nothing yet, sometimes comes in a full studded set. */
	public static void spawned(Mob mob, EntitySpawnReason reason, DifficultyInstance difficulty) {
		if (!(mob instanceof PiglinBrute) || mob.level().isClientSide() || reason != EntitySpawnReason.STRUCTURE) {
			return;
		}
		for (EquipmentSlot slot : ARMOUR) {
			if (!mob.getItemBySlot(slot).isEmpty()) {
				return;
			}
		}
		RandomSource random = mob.getRandom();
		if (random.nextFloat() >= BRUTE_STUDDED) {
			return;
		}
		boolean enchanted = random.nextFloat() < STUDDED_ENCHANTED;
		for (int i = 0; i < 4; i++) {
			ItemStack piece = new ItemStack(LightArmour.STUDDED[i]);
			if (enchanted) {
				EnchantmentHelper.enchantItemFromProvider(piece, mob.level().registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, random);
			}
			mob.setItemSlot(ARMOUR[i], piece);
		}
	}

	/** {@code piglin} is a brute in a full studded set: it doesn't zombify. */
	public static boolean steady(LivingEntity piglin) {
		return piglin instanceof PiglinBrute && wearsSet(piglin, LightArmour.STUDDED);
	}
}
