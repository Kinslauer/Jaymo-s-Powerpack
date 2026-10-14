package com.jaymospowers.power.zfighter;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** What the Z Fighter's mixins ask (the rest of the power stays package-private). */
public final class ZFighterHooks {
	private ZFighterHooks() {
	}

	public static float modifyDamage(LivingEntity victim, DamageSource source, float damage) {
		return ZFighterBattle.modifyDamage(victim, source, damage);
	}
}
