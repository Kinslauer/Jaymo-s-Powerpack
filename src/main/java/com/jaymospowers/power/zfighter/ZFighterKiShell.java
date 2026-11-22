package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.wounds.Downed;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ki Shell: a trained passive that soaks part of every blow from ki when not guarding.
 * Learned as a technique (index {@link ZFighterTraining#KI_SHELL}), always on while the fighter has it learned.
 * Reduces ki damage by a percentage based on mastery.
 */
public final class ZFighterKiShell {
	/** Base reduction (0.15 = 15%), plus per mastery level. */
	static final float BASE_REDUCTION = 0.15F;
	static final float REDUCTION_PER_MASTERY = 0.008F;

	/** Max reduction at full mastery. */
	public static final float MAX_REDUCTION = 0.5F;

	private ZFighterKiShell() {
	}

	/** Whether {@code player} has Ki Shell learned and it should apply. */
	public static boolean has(ServerPlayer player) {
		return ZFighterTraining.of(player).knows(ZFighterTraining.KI_SHELL);
	}

	/** The current reduction for {@code player} (0-1), based on mastery of his current form. */
	static void init() {
	}

	static void clear() {
	}

	public static float reduction(ServerPlayer player) {
		if (!has(player)) {
			return 0.0F;
		}
		int form = ZFighterPower.state(player).form();
		float mastery = ZFighterTraining.mastery(player, form);
		float reduction = BASE_REDUCTION + REDUCTION_PER_MASTERY * ZFighterTraining.of(player).stat(ZFighterTraining.KI_POWER);
		return Math.min(MAX_REDUCTION, reduction * (1.0F + mastery));
	}

	/**
	 * Called when {@code player} takes damage: if it's ki damage and he's not guarding, reduce it.
	 * Returns the damage after reduction (or original if no reduction applies).
	 */
	public static float onHurt(ServerPlayer player, DamageSource source, float damage) {
		if (!has(player)) {
			return damage;
		}
		ZFighterPower.Live live = ZFighterPower.live(player);
		if (live.guarding) {
			return damage; // guarding has its own reduction
		}
		// Check if the damage source is ki-based (our KI damage type or from a Z Fighter technique)
		if (isKiDamage(source)) {
			float red = reduction(player);
			return damage * (1.0F - red);
		}
		return damage;
	}

	/** Whether a damage source is a ki technique (for NPCs and other sources). */
	public static boolean isKiDamage(DamageSource source) {
		if (source.is(ZFighterKi.KI)) return true;
		var direct = source.getDirectEntity();
		if (direct != null && "Bolt".equals(direct.getClass().getSimpleName()) && direct.getClass().getEnclosingClass() != null && "ZFighterKi".equals(direct.getClass().getEnclosingClass().getSimpleName())) return true;
		var entity = source.getEntity();
		if (entity instanceof LivingEntity e) {
			var key = e.getType().builtInRegistryHolder().key();
			if (key != null && ZFighterPower.INSTANCE.id().getNamespace().equals(key.toString().split(":")[0])) return true;
		}
		return false;
	}
}
