package com.jaymospowers.core;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * (0.11.75) Two-handed weapons (Better Combat's {@code two_handed}: claymores, war axes, glaives, spears, scythes,
 * quarterstaffs and every other mod's) reach further and sweep a bigger area than one-handed ones. Only touched from
 * the Better Combat mixins, so only ever loaded when Better Combat is.
 */
public final class TwoHanded {
	/** Reach (the swing box's length and the server's hit check) times this. */
	public static final double REACH = 1.25;
	/** The swing box's width and height times this. */
	public static final double SWEEP = 1.35;
	/** A swing's arc (degrees) times this, up to a full circle. */
	public static final double ARC = 1.25;

	private TwoHanded() {
	}

	public static boolean is(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		try {
			WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
			return attributes != null && attributes.isTwoHanded();
		} catch (RuntimeException e) {
			return false;
		}
	}

	public static double arc(double angle) {
		return angle <= 0.0 ? angle : Math.min(360.0, angle * ARC);
	}
}
