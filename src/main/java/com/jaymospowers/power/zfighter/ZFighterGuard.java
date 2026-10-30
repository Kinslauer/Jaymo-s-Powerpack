package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.Guard;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The guard (right click held, bare-handed): forearms crossed before him.
 * <ul>
 * <li>Melee blows and projectiles from the front {@link #FRONT_DEGREES}° lose {@link #BLOCK_SHARE} of their damage, paid
 * in ki ({@link #KI_PER_DAMAGE} a point blocked); with no ki left the blow goes through whole. Never falls, fire,
 * explosions or anything from behind.</li>
 * <li>(0.18.24) Parrying is the one parry everything shares ({@link Guard}): the same perfect and parry windows from the
 * raise, lock-on bonus, mashing rule and unparryable heavy blows, the same stun, shove and riposte (his next punch), and
 * the same timing bar under the crosshair. A parry takes nothing and gives +{@link #PARRY_KI} ki, and wears the
 * attacker's poise.</li>
 * </ul>
 * The guard slows him like charging does; everyone sees the crossed arms ({@link #GUARD}).
 */
public final class ZFighterGuard {
	static final float BLOCK_SHARE = 0.5F;
	static final float KI_PER_DAMAGE = 0.5F;
	static final double FRONT_DEGREES = 150.0;
	static final float PARRY_KI = 10.0F;

	/** He holds his guard up, for everyone's client (the crossed arms). Not saved. */
	public static final AttachmentType<Boolean> GUARD = AttachmentRegistry.create(JaymosPowers.id("zfighter_guard"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private ZFighterGuard() {
	}

	public static boolean guarding(net.minecraft.world.entity.player.Player player) {
		return Boolean.TRUE.equals(player.getAttached(GUARD));
	}

	/** From the client: right click held (bare-handed) or let go. */
	static void set(ServerPlayer player, boolean up) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		up = up && player.isAlive() && !player.isSpectator() && player.getMainHandItem().isEmpty() && !Powers.silenced(player)
			&& !ZFighterLineage.ape(player);
		if (up == live.guarding) {
			return;
		}
		live.guarding = up;
		if (up) {
			live.guardStart = player.level().getGameTime();
			player.setAttached(GUARD, true);
			Guard.powerRaised(player);
		} else {
			player.removeAttached(GUARD);
			Guard.powerLowered(player);
		}
		ZFighterPower.refresh(player, live);
	}

	/** Dropped when the power goes or he dies. */
	static void drop(ServerPlayer player, ZFighterPower.Live live) {
		live.guarding = false;
		player.removeAttached(GUARD);
		Guard.powerLowered(player);
	}

	/** What's left of {@code damage} after his guard (the victim side of the damage hook). */
	static float guard(ServerPlayer player, ZFighterPower.Live live, DamageSource source, float damage) {
		if (!live.guarding || damage <= 0.0F) {
			return damage;
		}
		boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
		Entity direct = source.getDirectEntity();
		boolean melee = !projectile && direct instanceof LivingEntity && direct == source.getEntity();
		if (!projectile && !melee || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_EXPLOSION)
			|| source.is(DamageTypeTags.IS_FALL) || !projectile && source.is(DamageTypeTags.IS_FIRE) || !inFront(player, source.getSourcePosition())) {
			return damage;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Guard.Timing timing = Guard.timing(player, source, (int) (now - live.guardStart));
		if (timing.parried()) {
			parry(player, live, melee ? (LivingEntity) direct : null, source.getSourcePosition(), timing == Guard.Timing.PERFECT);
			ZFighterGrowth.guarded(player, true);
			return 0.0F;
		}
		float blocked = damage * BLOCK_SHARE;
		float cost = blocked * KI_PER_DAMAGE * ZFighterAndroid.costFactor(player);
		if (!player.isCreative() && !live.raging()) {
			if (live.ki < cost) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 0.6F);
				return damage;
			}
			live.ki -= cost;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 1.3F);
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.6));
		level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y - 0.4, at.z, 6, 0.2, 0.2, 0.2, 0.05);
		ZFighterGrowth.guarded(player, false);
		return damage - blocked;
	}

	/** The shared parry ({@link Guard#parried}: stun, shove, riposte, cue), then his own: ki back, ki dust, poise worn. */
	private static void parry(ServerPlayer player, ZFighterPower.Live live, LivingEntity attacker, Vec3 from, boolean perfect) {
		Guard.parried(player, attacker, from, perfect);
		ServerLevel level = player.level();
		live.ki = Math.min(ZFighterPower.maxKi(player), live.ki + PARRY_KI);
		Mastery.used(player);
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.8));
		level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y - 0.3, at.z, 12, 0.25, 0.25, 0.25, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.4F);
		if (attacker != null && attacker.isAlive()) {
			ZFighterPoise.wear(attacker, ZFighterPoise.PARRY_WEAR);
		}
	}

	private static boolean inFront(ServerPlayer player, Vec3 from) {
		if (from == null) {
			return false;
		}
		Vec3 to = from.subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		return to.normalize().dot(look) >= Math.cos(Math.toRadians(FRONT_DEGREES / 2.0));
	}
}
