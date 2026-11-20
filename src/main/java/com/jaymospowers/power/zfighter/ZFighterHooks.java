package com.jaymospowers.power.zfighter;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * What the Z Fighter's mixins ask (the rest of the power stays package-private).
 */
public final class ZFighterHooks {
	private ZFighterHooks() {
	}

	public static float modifyDamage(LivingEntity victim, DamageSource source, float damage) {
		// (0.17.60) then the difficulty he wished for
		// (Awakening pick) a body left airborne, flat or pinned takes a Z Fighter's or a boxer's next hit harder (past the punch cap)
		// (Awakening pick) Ability Power: a power's own moves hit harder
		float dmg = com.jaymospowers.core.AbilityPower.damage(victim, source,
			ZFighterWishExtras.scaled(victim, ZFighterStates.apply(victim, source,
				ZFighterBattle.modifyDamage(victim, source, com.jaymospowers.core.brawl.Brawl.guard(victim, source, damage)))));
		
		// Ki Shell: soak part of every blow from ki when not guarding, paid from ki
		if (victim instanceof ServerPlayer player && com.jaymospowers.core.Powers.has(player, ZFighterPower.INSTANCE)) {
			ZFighterPower.Live live = ZFighterPower.live(player);
			if (ZFighterKiShell.has(player) && !live.guarding) {
				if (ZFighterKiShell.isKiDamage(source)) {
					float reduction = ZFighterKiShell.reduction(player);
					if (reduction > 0.0F) {
						float soaked = damage * reduction;
						// Pay ki for what we soak
						float kiCost = soaked * 0.5F; // 0.5 ki per damage soaked
						if (live.ki >= kiCost) {
							live.ki -= kiCost;
							return damage - soaked;
						}
					}
				}
			}
		}
		return dmg;
	}
}
