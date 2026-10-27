package com.jaymospowers.power.zfighter;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** What the Z Fighter's mixins ask (the rest of the power stays package-private). */
public final class ZFighterHooks {
	private ZFighterHooks() {
	}

	public static float modifyDamage(LivingEntity victim, DamageSource source, float damage) {
		// (0.17.58) then the difficulty he wished for
		return ZFighterWishExtras.scaled(victim, ZFighterBattle.modifyDamage(victim, source, damage));
	}
}
