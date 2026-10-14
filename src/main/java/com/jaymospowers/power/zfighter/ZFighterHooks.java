package com.jaymospowers.power.zfighter;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** What the Z Fighter's mixins ask (the rest of the power stays package-private). */
public final class ZFighterHooks {
	private ZFighterHooks() {
	}

	public static boolean refusesAttack(Player player) {
		return ZFighterBattle.refusesAttack(player);
	}

	public static float modifyDamage(LivingEntity victim, DamageSource source, float damage) {
		return ZFighterBattle.modifyDamage(victim, source, damage);
	}
}
