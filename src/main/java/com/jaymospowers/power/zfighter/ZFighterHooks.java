package com.jaymospowers.power.zfighter;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** What the Z Fighter's mixins ask (the rest of the power stays package-private). */
public final class ZFighterHooks {
	private ZFighterHooks() {
	}

	public static float modifyDamage(LivingEntity victim, DamageSource source, float damage) {
		// (0.17.60) then the difficulty he wished for
		// (Awakening pick) a body left airborne, flat or pinned takes a Z Fighter's or a boxer's next hit harder (past the punch cap)
		// (Awakening pick) Ability Power: a power's own moves hit harder
		return com.jaymospowers.core.AbilityPower.damage(victim, source,
			ZFighterWishExtras.scaled(victim, ZFighterStates.apply(victim, source, ZFighterBattle.modifyDamage(victim, source, damage))));
	}
}
