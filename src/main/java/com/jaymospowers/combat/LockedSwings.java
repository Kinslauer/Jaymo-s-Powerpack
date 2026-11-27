package com.jaymospowers.combat;

import com.jaymospowers.armory.Lash;
import com.jaymospowers.core.LockOn;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;

/**
 * Andy (2026-11-27): "i no longer want attacks that are locked on to hit anyone but who is locked on. With the exception
 * of flails and chain axe, as they swing in a wide area regardless. The point of the lock on is to be precise and the
 * swings cover the same arc they do now otherwise."
 *
 * <p>While a player is locked on ({@link LockOn}), a weapon swing only strikes the locked foe: Better Combat's swing,
 * which attacks each thing in its arc one by one, skips everyone else ({@code mixin.LockedSwingMixin} on
 * {@code Player.attack}), and anything else the swing would hurt while it lasts (vanilla's sweep, a cleave off the blow,
 * a spear's stab along its line) is turned away ({@link #allowDamage}). A flail or the chain axe ({@link Lash#isFlail})
 * still hits all it sweeps through. Not locked on, nothing changes. Powers and thrown or shot things are not swings and
 * aren't touched; Kingdoms' Weapon Forms keep to the locked foe on their side ({@code CombatArts.strike}).
 */
public final class LockedSwings {
	private LockedSwings() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(LockedSwings::allowDamage);
	}

	/** Whether {@code player}'s swing may strike {@code target}: always when not locked on, else only the locked foe (or any, with a flail). */
	public static boolean strikes(Player player, Entity target) {
		if (!(player instanceof ServerPlayer server)) {
			return true;
		}
		LivingEntity lock = LockOn.target(server);
		if (lock == null || target == lock || wide(player)) {
			return true;
		}
		// (the dragon is struck through its parts)
		return target instanceof EnderDragonPart part && part.parentMob == lock;
	}

	/** A weapon that sweeps wide whatever the lock: a flail or the chain axe. */
	static boolean wide(Player player) {
		return Lash.isFlail(player.getMainHandItem());
	}

	/** Anyone but the locked foe hurt by a locked-on player's own swing while it lasts goes unhurt. */
	private static boolean allowDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		return !(source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && entity != player
			&& Blows.swinging(player) && !strikes(player, entity));
	}
}
