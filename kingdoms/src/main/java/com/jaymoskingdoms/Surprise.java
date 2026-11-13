package com.jaymoskingdoms;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Caught unawares (fifth edition's surprise, Andy's pick C4). A foe who didn't know you were there freezes for a
 * heartbeat ({@link #HEARTBEAT} ticks, {@link HeldFast}) when you spring the ambush: your first blow or arrow on a monster
 * that hadn't noticed you ({@link Senses#unaware}). And so do you when something ambushes you: the first blow from a
 * monster out of your sight, when you were at ease (no fight for {@link #AT_EASE} ticks), freezes you the same heartbeat.
 * Other ambushes (a band's, a companion's) call {@link #ambushed}.
 * <p>
 * Anyone who is never surprised (the Alert feat) is added with {@link #addImmunity}.
 */
public final class Surprise {
	/** How long the stillness lasts (ticks). */
	static final int HEARTBEAT = 14;
	/** A player who has had no fight for this long (ticks) is at ease for an ambush to catch. */
	static final int AT_EASE = 20 * 8;
	/** A blow from behind: the blow is outside this cosine of the player's look (negative: behind). */
	static final double BEHIND = 0.0;
	/** Nothing is startled again within this long of the last time (ticks). */
	static final int AGAIN = 20 * 6;

	private static final List<Predicate<LivingEntity>> IMMUNE = new ArrayList<>();
	/** When each was last startled (game time), and when each player last fought (game time). */
	private static final Map<LivingEntity, Long> STARTLED = new WeakHashMap<>(), FOUGHT = new WeakHashMap<>();

	private Surprise() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STARTLED.clear();
			FOUGHT.clear();
		});
		// being hit by a monster, or by anyone out for your blood (a band's ambush), you hadn't noticed, when at ease
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (victim instanceof ServerPlayer player && player.level() instanceof ServerLevel level && taken > 0.0F) {
				long now = level.getGameTime();
				Long fought = FOUGHT.get(player);
				boolean atEase = fought == null || now - fought > AT_EASE;
				FOUGHT.put(player, now);
				if (atEase && source.getEntity() instanceof Mob attacker && hostile(attacker, player) && outOfSight(player, attacker)) {
					ambushed(player);
				}
			}
		});
	}

	/** Adds a way of never being surprised (the Alert feat). */
	public static void addImmunity(Predicate<LivingEntity> never) {
		IMMUNE.add(never);
	}

	/** Whether {@code who} can't be surprised. */
	public static boolean immune(LivingEntity who) {
		for (Predicate<LivingEntity> p : IMMUNE) {
			if (p.test(who)) {
				return true;
			}
		}
		return false;
	}

	/** {@code who} is caught unawares: still for a heartbeat. Returns whether they were (not if they never are, or were just now). */
	public static boolean ambushed(LivingEntity who) {
		if (immune(who) || !(who.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		Long last = STARTLED.get(who);
		if (last != null && now - last < AGAIN) {
			return false;
		}
		if (!HeldFast.startle(who, HEARTBEAT)) {
			return false;
		}
		STARTLED.put(who, now);
		level.playSound(null, who.getX(), who.getEyeY(), who.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.9F, 1.7F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, who.getX(), who.getY(1.2), who.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
		return true;
	}

	/** A monster, or anyone else out for {@code player}'s blood: a bandit band, a raider, a war party. */
	private static boolean hostile(Mob attacker, ServerPlayer player) {
		return attacker instanceof Enemy || attacker.getTarget() == player;
	}

	/**
	 * Whether {@code player} is off their guard against {@code attacker}: no fight for a while, and the attacker out of their
	 * sight. The first blow of an ambush; the blow itself puts them on guard.
	 */
	public static boolean offGuard(ServerPlayer player, Mob attacker) {
		if (immune(player) || !hostile(attacker, player)) {
			return false;
		}
		Long fought = FOUGHT.get(player);
		return (fought == null || player.level().getGameTime() - fought > AT_EASE) && outOfSight(player, attacker);
	}

	/** Whether {@code attacker} is behind {@code player}'s eyes or out of their sight. */
	private static boolean outOfSight(ServerPlayer player, Mob attacker) {
		Vec3 look = player.getLookAngle();
		Vec3 to = attacker.getEyePosition().subtract(player.getEyePosition());
		return to.lengthSqr() < 1.0E-4 || look.dot(to.normalize()) < BEHIND || !player.hasLineOfSight(attacker);
	}

	/** {@code target} taking a blow or arrow from {@code source}: a monster that hadn't noticed the player freezes a heartbeat. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount > 0.0F && source.getEntity() instanceof ServerPlayer fighter) {
			// (one already trading blows isn't at ease for an ambush to catch)
			FOUGHT.put(fighter, fighter.level().getGameTime());
		}
		if (amount > 0.0F && source.getEntity() instanceof ServerPlayer sp && target instanceof Mob mob && !Senses.cooling(target, source)
			&& (source.getDirectEntity() == sp || source.getDirectEntity() instanceof Projectile) && Senses.unaware(mob, sp)) {
			ambushed(mob);
		}
		return amount;
	}
}
