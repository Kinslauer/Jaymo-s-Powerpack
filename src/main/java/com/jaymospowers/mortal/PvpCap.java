package com.jaymospowers.mortal;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterRealism;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A cap on what one player's abilities can do to another in a single hit: the server setting {@code pvp_ability_cap}
 * in {@code config/jaymospowers-server.properties}, a share of the struck player's full health (0.25 = no ability hit
 * takes more than a quarter of it). 0, the default, is off.
 * <p>
 * An ability's hit is any harm one player does another that is not a plain blow of the hand or a weapon in it, or that
 * lands while the attacker's power is at work (its tick or one of its keys running): a ki blast, a spell, a thrown
 * wave, the blow an ability adds to a swing. A plain swing, an arrow and a thrown trident are never capped.
 */
public final class PvpCap {
	private PvpCap() {
	}

	/** (PvpCapMixin) What {@code damage} from {@code source} becomes on {@code victim}. */
	public static float capped(LivingEntity victim, DamageSource source, float damage) {
		float cap = ZFighterRealism.pvpAbilityCap();
		if (cap <= 0.0F || !(victim instanceof Player) || !(source.getEntity() instanceof Player attacker) || attacker == victim) {
			return damage;
		}
		boolean plain = (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker || source.is(DamageTypes.ARROW)
			|| source.is(DamageTypes.TRIDENT)) && !Powers.actingNow();
		return plain ? damage : Math.min(damage, victim.getMaxHealth() * cap);
	}
}
