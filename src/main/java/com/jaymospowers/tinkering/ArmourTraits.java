package com.jaymospowers.tinkering;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The armour traits of Tool Station armour (Tinkers' own, from each plating's and maille's metal), after vanilla's armour and
 * enchantments: every level a worn, unbroken piece carries turns aside {@link #SHARE} of the harm it guards against, up to
 * {@link #MOST} in all.
 * <ul>
 * <li>Depth Protection (copper): any harm, by how deep you are, nothing at y {@link #DEPTH_TOP} and up to full at y 0.</li>
 * <li>Projectile Protection (iron): arrows, bolts and anything else shot or thrown.</li>
 * <li>Consecrated (silver): blows from the undead.</li>
 * <li>Melee Protection (cobalt): a blow struck by hand or blade, not shot.</li>
 * <li>Warded (manyullyn): magic.</li>
 * <li>Recurrent Protection (hepatizon): any harm, for {@link #RECURRENT_TICKS} after something last hurt you.</li>
 * </ul>
 */
public final class ArmourTraits {
	/** Share of the harm each level turns aside, and the most they turn aside together. */
	static final float SHARE = 0.04F, MOST = 0.6F;
	/** Depth Protection: from this height down to y 0 it grows to full. */
	static final int DEPTH_TOP = 64;
	/** Recurrent Protection: ticks after the last hurt that it holds. */
	static final int RECURRENT_TICKS = 100;

	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private ArmourTraits() {
	}

	/** {@code amount} of harm from {@code source} after the armour traits {@code wearer} has on. */
	public static float taken(LivingEntity wearer, DamageSource source, float amount) {
		if (amount <= 0 || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return amount;
		}
		float turned = 0;
		for (EquipmentSlot slot : ARMOUR) {
			ItemStack stack = wearer.getItemBySlot(slot);
			if (!(stack.getItem() instanceof TinkerArmourItem) || ToolStats.isBroken(stack)) {
				continue;
			}
			ToolStats.Stats stats = ToolStats.stats(stack);
			if (stats == null) {
				continue;
			}
			for (var trait : stats.traits().entrySet()) {
				turned += trait.getValue() * SHARE * guards(trait.getKey(), wearer, source);
			}
		}
		return turned <= 0 ? amount : amount * (1 - Math.min(MOST, turned));
	}

	/** How much of a level counts against this harm: 0 (it doesn't guard against it) to 1. */
	private static float guards(Trait trait, LivingEntity wearer, DamageSource source) {
		Entity attacker = source.getEntity();
		return switch (trait) {
			case DEPTH_PROTECTION -> Mth.clamp((DEPTH_TOP - (float) wearer.getY()) / DEPTH_TOP, 0.0F, 1.0F);
			case PROJECTILE_PROTECTION -> source.is(DamageTypeTags.IS_PROJECTILE) ? 1 : 0;
			case CONSECRATED -> attacker != null && attacker.is(EntityTypeTags.UNDEAD) ? 1 : 0;
			case MELEE_PROTECTION -> attacker instanceof LivingEntity && source.getDirectEntity() == attacker
				&& !source.is(DamageTypeTags.IS_PROJECTILE) ? 1 : 0;
			case WARDED -> source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) ? 1 : 0;
			case RECURRENT_PROTECTION -> wearer.getLastHurtByMobTimestamp() > 0
				&& wearer.tickCount - wearer.getLastHurtByMobTimestamp() < RECURRENT_TICKS ? 1 : 0;
			default -> 0;
		};
	}
}
