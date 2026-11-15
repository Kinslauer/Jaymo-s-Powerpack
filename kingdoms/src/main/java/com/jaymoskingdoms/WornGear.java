package com.jaymoskingdoms;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * Andy (2026-11-15): "pickups in general should have low durability". Gear taken from a mob's hands (a downed guard or
 * illager disarmed, a weapon knocked loose by a spell) has seen hard use: most of it comes away with
 * {@link #WORN_MIN}-{@link #WORN_MAX} of its durability left, and only {@link #GOOD_CHANCE} of the time is it a good
 * piece ({@link #GOOD_MIN}-{@link #GOOD_MAX} left). Jaymo's Kitchen wears gear stripped off a body by the same numbers.
 * Never repairs what was already more worn, and leaves alone what the mob picked up (a player's own blade).
 */
public final class WornGear {
	/** How often a piece comes away in good shape, and how much durability (a share of the whole) is left either way. */
	static final float GOOD_CHANCE = 0.08F, WORN_MIN = 0.03F, WORN_MAX = 0.25F, GOOD_MIN = 0.55F, GOOD_MAX = 0.90F;

	private WornGear() {
	}

	/** Wears {@code stack} (in place) as a piece of a mob's gear that just came into a player's reach. */
	public static void wear(ItemStack stack, RandomSource random) {
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return;
		}
		int max = stack.getMaxDamage();
		float left = random.nextFloat() < GOOD_CHANCE ? GOOD_MIN + random.nextFloat() * (GOOD_MAX - GOOD_MIN)
			: WORN_MIN + random.nextFloat() * (WORN_MAX - WORN_MIN);
		int remaining = Math.max(1, Math.round(max * left));
		stack.setDamageValue(Math.max(stack.getDamageValue(), max - remaining));
	}

	/** Whether {@code holder}'s gear in {@code slot} is something it picked up (vanilla keeps those as they were). */
	public static boolean pickedUp(LivingEntity holder, EquipmentSlot slot) {
		return holder instanceof Mob mob && mob.getDropChances().isPreserved(slot);
	}
}
