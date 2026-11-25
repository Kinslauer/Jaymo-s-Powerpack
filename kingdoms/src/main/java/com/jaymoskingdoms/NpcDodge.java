package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 7 of the fighting AI (band styles, st-dodge; the ideas are Hostile Humans', none of its code): bandits and assassins roll out of
 * a blow. Our own roll for NPCs, after the player's dodge in Jaymo's Powers: no NPC roll pose exists (Powers' tumble is drawn on players
 * only), so the roll is shown as what it is: a quick low hop to the side and back, crouched, with a whoosh and a puff of dust.
 * <ul>
 * <li><b>Who</b> ({@link #dodger}): a bandit ({@link #bandit}: a bandit camp's raider or chief, or anyone in a bandit's mask) or an
 * assassin ({@link Assassins#is}), in no plate ({@link MobCombat#light}), on its feet and on the ground.</li>
 * <li><b>A blow it sees coming</b> ({@link #incoming}): an NPC's swing winding up at it (Better Combat's, a plain blow) or the game's melee
 * blow coming due ({@code MeleeAttackGoalMixin}, MCA's melee task): if the swinger is in front of it ({@link #SEEN_ANGLE}) it reads
 * the blow {@link #READ_CHANCE} of the time and rolls {@link #ROLL_LEAD} ticks before it lands, out of its reach. A blow that comes at
 * once is rolled out of as it comes (and misses).</li>
 * <li><b>A player's blow</b> has no wind-up to read, so it is rolled out of as it lands (no damage), on the same rules.</li>
 * <li>One roll every {@link #ROLL_COOLDOWN} ticks, only where there's ground to land on (never off a drop or into lava). Never while
 * its shield is up, mid-swing, drawing a bow, carried, in water, downed or making an arrest.</li>
 * </ul>
 * The old sidestep in {@link MobCombat} leaves dodgers to this.
 */
public final class NpcDodge {
	/** The chance a blow it sees coming is read and rolled out of; the most it may be off its front (degrees). */
	static final float READ_CHANCE = 0.5F;
	static final double SEEN_ANGLE = 75.0;
	/** It rolls this long (ticks) before the blow lands; a blow is read once in this long (ticks). */
	public static final int ROLL_LEAD = 3;
	static final int READ_GAP = 12;
	/** The roll's push (blocks a tick), its hop, and how much of it is back from the blow (the rest is to the side). */
	static final double ROLL_SPEED = 0.7, ROLL_UP = 0.25, ROLL_BACK = 0.6;
	/** It stays low this long (ticks); the least time between two rolls (ticks). */
	static final int ROLL_TICKS = 7, ROLL_COOLDOWN = 70;

	private static final class State {
		long ready, poseUntil, rollAt;
		@Nullable LivingEntity readOf, rollFrom;
		long readUntil;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private NpcDodge() {
	}

	/** From {@link BandStyles#register}. */
	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NpcDodge::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(NpcDodge::allowDamage);
	}

	// ---- who -------------------------------------------------------------------------------------------------------------

	/** A bandit: in a bandit's mask, or a raider or chief of a bandits' camp (folk or MCA villager). */
	static boolean bandit(Mob mob) {
		if (Bandits.MASK != null && mob.getItemBySlot(EquipmentSlot.HEAD).is(Bandits.MASK)) {
			return true;
		}
		if (!(mob instanceof Worker w) || w.role() != Folk.BARBARIAN && w.role() != Folk.CHIEF) {
			return false;
		}
		MinecraftServer server = mob.level().getServer();
		return server != null && IllagerSites.BANDIT.equals(Camps.kind(server, w.factionId()));
	}

	/** One who rolls out of blows: a bandit or an assassin, in no plate. */
	public static boolean dodger(Mob mob) {
		return (Assassins.is(mob) || bandit(mob)) && MobCombat.light(mob);
	}

	/** Whether it can roll just now. */
	private static boolean able(Mob mob) {
		return mob.isAlive() && mob.onGround() && !mob.isPassenger() && !mob.isSleeping() && !mob.isInWater() && !mob.isInLava() && !Downed.isDowned(mob)
			&& !Arrests.busy(mob) && !MobCombat.swinging(mob) && !mob.isUsingItem() && !MobShields.guarding(mob);
	}

	/** {@code from} is within {@link #SEEN_ANGLE} of where {@code mob} looks, and in its sight. */
	private static boolean sees(Mob mob, LivingEntity from) {
		Vec3 to = new Vec3(from.getX() - mob.getX(), 0.0, from.getZ() - mob.getZ());
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		double angle = Math.toDegrees(Math.acos(Math.clamp(to.normalize().dot(Vec3.directionFromRotation(0.0F, mob.getYHeadRot())), -1.0, 1.0)));
		return angle <= SEEN_ANGLE && mob.getSensing().hasLineOfSight(from);
	}

	/** Whether {@code mob} is rolling just now (an MCA villager's brain waits). */
	static boolean rolling(Mob mob) {
		State s = STATES.get(mob);
		return s != null && mob.level().getGameTime() < s.poseUntil;
	}

	// ---- reading a blow --------------------------------------------------------------------------------------------------

	/**
	 * (MobCombat's swing, the game's melee goal, MCA's melee task) {@code attacker}'s blow at {@code target} lands in {@code ticks}: a
	 * dodger that sees it may roll out of it, {@link #ROLL_LEAD} before. True if it has rolled now (a blow due now then misses).
	 */
	public static boolean incoming(LivingEntity target, Mob attacker, int ticks) {
		if (!(target instanceof Mob dodger) || !(dodger.level() instanceof ServerLevel level) || Downed.isDowned(target) || !dodger(dodger)) {
			return false;
		}
		long now = level.getGameTime();
		State s = STATES.computeIfAbsent(dodger, m -> new State());
		if (now < s.ready || s.rollAt > now || s.readOf == attacker && now < s.readUntil) {
			return false;
		}
		s.readOf = attacker;
		s.readUntil = now + Math.max(0, ticks) + READ_GAP;
		if (!able(dodger) || !sees(dodger, attacker) || dodger.getRandom().nextFloat() >= READ_CHANCE) {
			return false;
		}
		if (ticks <= ROLL_LEAD) {
			return roll(level, dodger, attacker, now);
		}
		s.rollAt = now + ticks - ROLL_LEAD;
		s.rollFrom = attacker;
		return false;
	}

	/** A player's blow lands on a dodger (no wind-up to read): it may roll out of it as it comes, and the blow misses. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof Mob dodger) || !(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof Player player)
			|| source.getDirectEntity() != player || source.isCreativePlayer() || !dodger(dodger)) {
			return true;
		}
		long now = level.getGameTime();
		State s = STATES.computeIfAbsent(dodger, m -> new State());
		if (now < s.ready || dodger.hurtTime > 0 || !able(dodger) || !sees(dodger, player) || dodger.getRandom().nextFloat() >= READ_CHANCE) {
			return true;
		}
		return !roll(level, dodger, player, now);
	}

	// ---- the roll --------------------------------------------------------------------------------------------------------

	/** Rolls {@code dodger} out of {@code from}'s blow: to a side with ground to land on, and back. False (no roll) if there's none. */
	private static boolean roll(ServerLevel level, Mob dodger, LivingEntity from, long now) {
		Vec3 away = new Vec3(dodger.getX() - from.getX(), 0.0, dodger.getZ() - from.getZ());
		away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, dodger.getYRot()).reverse() : away.normalize();
		Vec3 side = new Vec3(-away.z, 0.0, away.x).scale(dodger.getRandom().nextBoolean() ? 1.0 : -1.0);
		Vec3[] ways = {side.add(away.scale(ROLL_BACK)).normalize(), side.reverse().add(away.scale(ROLL_BACK)).normalize(), away};
		Vec3 dir = null;
		for (Vec3 way : ways) {
			if (Blows.footing(level, dodger, way) && Blows.footing(level, dodger, way.scale(2.0))) {
				dir = way;
				break;
			}
		}
		if (dir == null) {
			return false;
		}
		State s = STATES.computeIfAbsent(dodger, m -> new State());
		s.ready = now + ROLL_COOLDOWN;
		s.poseUntil = now + ROLL_TICKS;
		s.rollAt = 0;
		s.rollFrom = null;
		dodger.getNavigation().stop();
		dodger.setDeltaMovement(dir.x * ROLL_SPEED, ROLL_UP, dir.z * ROLL_SPEED);
		dodger.syncVelocity = true;
		// (low through the roll: the crouch shows on every humanoid body)
		dodger.setPose(Pose.CROUCHING);
		dodger.getLookControl().setLookAt(from, 30.0F, 30.0F);
		level.playSound(null, dodger.getX(), dodger.getY(), dodger.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, dodger.getSoundSource(), 0.9F, 1.4F);
		level.playSound(null, dodger.getX(), dodger.getY(), dodger.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), dodger.getSoundSource(), 0.7F, 1.3F);
		level.sendParticles(ParticleTypes.POOF, dodger.getX(), dodger.getY() + 0.1, dodger.getZ(), 8, 0.35, 0.05, 0.35, 0.02);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, State> e : new ArrayList<>(STATES.entrySet())) {
			Mob mob = e.getKey();
			State s = e.getValue();
			if (mob == null || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				STATES.remove(mob);
				continue;
			}
			long now = level.getGameTime();
			if (s.rollAt > 0 && now >= s.rollAt) {
				LivingEntity from = s.rollFrom;
				s.rollAt = 0;
				s.rollFrom = null;
				// (still wound up, its blow still to fall)
				if (from instanceof Mob attacker && attacker.isAlive() && MobCombat.blowWithin(attacker, ROLL_LEAD + 1) && able(mob) && !Downed.isDowned(mob)) {
					roll(level, mob, attacker, now);
				}
			}
			if (s.poseUntil > 0 && now >= s.poseUntil) {
				s.poseUntil = 0;
				if (mob.getPose() == Pose.CROUCHING) {
					mob.setPose(Pose.STANDING);
				}
			}
			if (!mob.isAlive() || s.poseUntil == 0 && s.rollAt == 0 && now > s.ready + 1200 && now > s.readUntil) {
				STATES.remove(mob);
			}
		}
	}
}
