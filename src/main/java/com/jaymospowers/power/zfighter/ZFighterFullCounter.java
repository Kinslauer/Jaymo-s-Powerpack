package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * Full Counter (Andy's Awakening pick: "guard and parry"). Raise the guard with Sneak held and for {@link #STANCE} ticks
 * whatever hits you, from any side, is turned back and you take none of it:
 * <ul>
 * <li>a blow, at {@link #MELEE} times its harm on whoever struck it;</li>
 * <li>an arrow, sent back at the archer ({@link ZFighterBracers#sendBack}); any other shot, its harm on whoever loosed it;</li>
 * <li>a blast, a spell or anything else someone did to you, {@link #BLAST} of its harm on them.</li>
 * </ul>
 * Harm nobody did (a fall, fire, drowning) is not turned. Then {@link #COOLDOWN} before the next. A counter is never itself
 * countered (two Full Counters don't bat one blow back and forth).
 */
final class ZFighterFullCounter {
	// ---- tuning ----
	/** How long the stance holds, the wait after (ticks). */
	static final int STANCE = 10, COOLDOWN = 120;
	/** The share of the harm turned back: a blow, a blast. */
	static final float MELEE = 2.0F, BLAST = 0.5F;

	private static final Map<UUID, Long> UNTIL = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** Set while a counter's own harm lands, so it isn't turned again. */
	private static boolean returning;

	private ZFighterFullCounter() {
	}

	static void forget(ServerPlayer player) {
		UNTIL.remove(player.getUUID());
		READY.remove(player.getUUID());
	}

	/** Sneak + raising the guard: the stance, if it's ready. */
	static void open(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (READY.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		UNTIL.put(player.getUUID(), now + STANCE);
		READY.put(player.getUUID(), now + STANCE + COOLDOWN);
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.5)).add(0.0, -0.4, 0.0);
		MagicFx.ring(level, at, player.getLookAngle(), 0.9F, ZFighterPower.kiColor(player), STANCE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	static boolean holding(ServerPlayer player) {
		Long until = UNTIL.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	/** What's left of {@code damage} in the stance: nothing, if it was turned back. */
	static float take(ServerPlayer player, DamageSource source, float damage) {
		Entity by = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (returning || damage <= 0.0F || by == null && direct == null || by == player || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return damage;
		}
		ServerLevel level = player.level();
		boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE) || direct instanceof Projectile;
		returning = true;
		try {
			if (direct instanceof AbstractArrow arrow) {
				ZFighterBracers.sendBack(player, arrow);
				arrow.discard();
			} else if (projectile) {
				Entity owner = direct instanceof Projectile shot && shot.getOwner() != null ? shot.getOwner() : by;
				if (direct != null) {
					direct.discard();
				}
				hit(level, player, owner, damage);
			} else if (direct instanceof LivingEntity && direct == by) {
				hit(level, player, by, damage * MELEE);
			} else {
				hit(level, player, by != null ? by : direct, damage * BLAST);
			}
		} finally {
			returning = false;
		}
		Vec3 at = player.getBoundingBox().getCenter();
		MagicFx.blast(level, at, 0.9F, ZFighterPower.kiColor(player), 8);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 2.0F);
		com.jaymospowers.core.Mastery.used(player);
		return 0.0F;
	}

	private static void hit(ServerLevel level, ServerPlayer player, Entity target, float harm) {
		if (!(target instanceof LivingEntity living) || !living.isAlive() || target == player) {
			return;
		}
		living.setInvulnerableTime(0);
		living.hurtServer(level, ZFighterKi.source(player), harm);
		ZFighterKi.push(living, living.position().subtract(player.position()), 0.6, 0.2);
		Vec3 c = living.getBoundingBox().getCenter();
		MagicFx.beam(level, player.getBoundingBox().getCenter(), c, 0.08F, ZFighterPower.kiColor(player), 6);
	}
}
