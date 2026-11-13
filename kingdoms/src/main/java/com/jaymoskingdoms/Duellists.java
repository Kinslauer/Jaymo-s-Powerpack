package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Duellists (docs/BossRise-Plan.md, patch 1). Andy, on the pick list's "guards with real movesets": "PLEASE, this is
 * the MOST IMPORTANT part of the modpack, we NEED good combat". {@link MobCombat} already gives every armed humanoid its
 * weapon's combo, {@link MobShields} a guard and {@link MobParry} a parry; this is what goes on round the swing, for
 * every mob that fights that way (guards, crews, raiders, orcs, illagers, armed skeletons; friend and foe alike). The
 * idea of mobs that fight like duellists is Epic Fight's and its add-ons' (ideas only: our own code).
 * <ul>
 * <li><b>Footwork.</b> After a blow of its own a fighter doesn't stand and trade. It circles to a side for a moment
 * ({@link #CIRCLE_PUSH}), and the careful ({@link #careful}) step back out of reach instead ({@link #BACK_SPEED}). The
 * reckless and anyone behind a shield keep walking in. Nobody steps off a ledge or into water for it.</li>
 * <li><b>Closing blows.</b> From {@link #LUNGE_MIN} to {@link #LUNGE_MAX} blocks a fighter may run in and open with
 * its weapon's heaviest move ({@link MobCombat#lunge}), with the tell every swing has: the leap carries the blow, so
 * stepping aside makes it miss.</li>
 * <li><b>Punishing.</b> Eat or drink in front of a fighter, or turn your back on it, and it drops its guard and comes
 * at you: a closing blow at once from range, a quicker swing ({@link MobCombat#quicken}) up close.</li>
 * <li><b>Flinch.</b> A hard enough blow on a fighter that is winding up spoils its swing ({@link #FLINCH_SHARE} of its
 * health, more for a veteran; any two-handed weapon's blow). Bosses never flinch.</li>
 * <li><b>Roles.</b> The shield-bearer holds the front (no stepping back), and a leader with a band still about him
 * fights carefully and leaves the charging to them. When a chief or a raid captain falls to a player, the band's
 * nerve is tried by temperament ({@link #nerve}): the timid break and run ({@link Will#flee}), the brave never do.</li>
 * </ul>
 * Zombies and their kin have no footwork: they only ever walk at you.
 */
public final class Duellists {
	/** How long after its last attack tick a fighter is still in the fight (ticks), and how far its foe may get. */
	static final int ENGAGED_TICKS = 100;
	static final double ENGAGED_RANGE = 12.0;
	/** Circling: the sideways push each tick, how long a circle lasts (ticks, at least and at most), within this gap. */
	static final double CIRCLE_PUSH = 0.045;
	static final int CIRCLE_MIN = 10, CIRCLE_MAX = 24;
	static final double CIRCLE_GAP = 4.0;
	/** The step back: its speed out and up, the gap it is taken within, and the wait before another (ticks). */
	static final double BACK_SPEED = 0.42, BACK_UP = 0.12, BACK_GAP = 3.5;
	static final int BACK_COOLDOWN = 60;
	/** How often the careful step back after a blow, and everyone else. */
	static final float BACK_CAREFUL = 0.7F, BACK_PLAIN = 0.2F;
	/** The closing blow: from how far, its chance each tick in that band, its leap, and the wait before another. */
	static final double LUNGE_MIN = 4.0, LUNGE_MAX = 7.0;
	static final float LUNGE_CHANCE = 0.04F;
	static final double LUNGE_SPEED_PER_BLOCK = 0.16, LUNGE_SPEED_MIN = 0.6, LUNGE_SPEED_MAX = 1.1, LUNGE_UP = 0.28;
	static final int LUNGE_COOLDOWN = 160, LUNGE_COOLDOWN_SPREAD = 80;
	/** Punishing: the wait between two (ticks), how far behind you it must be (the dot of your look and the way to it). */
	static final int PUNISH_COOLDOWN = 50;
	static final double BACK_TURNED = -0.4;
	/** Flinch: the share of its health one blow must take to spoil a wind-up, for a plain fighter and a veteran. */
	static final float FLINCH_SHARE = 0.2F, FLINCH_VETERAN_SHARE = 0.35F;
	/** A leader counts as having a band with this many of his side fighting within this range. */
	static final int BAND_SIZE = 2;
	static final double BAND_RANGE = 10.0;
	/** The nerve test when a leader falls: how far it reaches, how long the broken run (ticks). */
	static final double NERVE_RANGE = 16.0;
	static final int NERVE_FLEE_TICKS = 200;

	private static final class Duel {
		@Nullable LivingEntity target;
		long seen, circleUntil, nextBack, nextLunge, nextPunish;
		double side = 1.0;
		boolean wasSwinging;
	}

	private static final Map<Mob, Duel> DUELS = new WeakHashMap<>();

	private Duellists() {
	}

	static void register() {
		if (!MobCombat.ENABLED) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(Duellists::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Duellists::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(Duellists::afterDeath);
	}

	/** {@code mob}'s melee goal or task is on {@code target} this tick ({@link MobCombat#attackTick}, {@link MobCombat#plainBlow}). */
	static void engaged(Mob mob, LivingEntity target) {
		if (!(mob.level() instanceof ServerLevel level) || mob instanceof Zombie) {
			return;
		}
		Duel duel = DUELS.computeIfAbsent(mob, m -> {
			Duel fresh = new Duel();
			// nobody opens a fight with a leap the moment it starts
			fresh.nextLunge = level.getGameTime() + 40;
			return fresh;
		});
		duel.target = target;
		duel.seen = level.getGameTime();
	}

	private static void tick(MinecraftServer server) {
		if (DUELS.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, Duel> entry : new ArrayList<>(DUELS.entrySet())) {
			Mob mob = entry.getKey();
			Duel duel = entry.getValue();
			if (mob == null) {
				continue;
			}
			LivingEntity target = duel.target;
			if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isRemoved() || target == null || !target.isAlive()
				|| target.level() != level || level.getGameTime() - duel.seen > ENGAGED_TICKS || MobCombat.gap(mob, target) > ENGAGED_RANGE) {
				DUELS.remove(mob);
				continue;
			}
			step(level, mob, target, duel, level.getGameTime());
		}
	}

	private static void step(ServerLevel level, Mob mob, LivingEntity target, Duel duel, long now) {
		boolean swinging = MobCombat.swinging(mob);
		boolean ended = duel.wasSwinging && !swinging;
		duel.wasSwinging = swinging;
		if (mob.isNoAi() || mob.isPassenger() || !mob.onGround() || mob.isInWater() || Downed.isDowned(mob)
			|| mob.entityTags().contains(MobCombat.WAITING_TAG)) {
			return;
		}
		double gap = MobCombat.gap(mob, target);
		boolean shield = MobShields.guarding(mob) || mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
		boolean heldBack = leaderWithBand(level, mob);
		// its own blow is done: step out of reach, or start round to a side
		if (ended && gap <= CIRCLE_GAP) {
			boolean back = !shield && !reckless(mob) && now >= duel.nextBack && gap <= BACK_GAP
				&& mob.getRandom().nextFloat() < (heldBack || careful(mob) ? BACK_CAREFUL : BACK_PLAIN);
			Vec3 away = flat(mob.position().subtract(target.position()));
			if (back && away != null && footing(level, mob, away, 1.6)) {
				duel.nextBack = now + BACK_COOLDOWN;
				mob.setDeltaMovement(away.scale(BACK_SPEED).add(0.0, BACK_UP, 0.0));
				mob.syncVelocity = true;
				mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			} else if (!shield) {
				duel.side = mob.getRandom().nextBoolean() ? 1.0 : -1.0;
				duel.circleUntil = now + CIRCLE_MIN + mob.getRandom().nextInt(CIRCLE_MAX - CIRCLE_MIN + 1);
			}
		}
		if (swinging) {
			return;
		}
		// round to a side between blows, facing the foe
		if (now < duel.circleUntil && gap <= CIRCLE_GAP) {
			Vec3 to = flat(target.position().subtract(mob.position()));
			if (to != null) {
				Vec3 across = new Vec3(-to.z, 0.0, to.x).scale(duel.side);
				if (!footing(level, mob, across, 1.0)) {
					duel.side = -duel.side;
					across = across.scale(-1.0);
				}
				if (footing(level, mob, across, 1.0)) {
					mob.setDeltaMovement(mob.getDeltaMovement().add(across.scale(CIRCLE_PUSH)));
					mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
				}
			}
		}
		boolean opening = target instanceof Player player && now >= duel.nextPunish && opening(mob, player);
		if (opening && gap < LUNGE_MIN) {
			// up close: the guard comes down and the next swing comes quicker
			duel.nextPunish = now + PUNISH_COOLDOWN;
			MobShields.drop(mob);
			MobCombat.quicken(mob);
			MobCombat.hasten(mob);
		}
		// the closing blow: by chance, or at once at someone who gave it an opening
		if (!heldBack && gap >= LUNGE_MIN && gap <= LUNGE_MAX && now >= duel.nextLunge && mob.getSensing().hasLineOfSight(target)
			&& (opening || mob.getRandom().nextFloat() < LUNGE_CHANCE)) {
			Vec3 to = flat(target.position().subtract(mob.position()));
			if (to != null && Math.abs(target.getY() - mob.getY()) <= 2.0 && MobCombat.lunge(mob, target)) {
				duel.nextLunge = now + LUNGE_COOLDOWN + mob.getRandom().nextInt(LUNGE_COOLDOWN_SPREAD);
				if (opening) {
					duel.nextPunish = now + PUNISH_COOLDOWN;
				}
				MobShields.drop(mob);
				double speed = Math.clamp(gap * LUNGE_SPEED_PER_BLOCK, LUNGE_SPEED_MIN, LUNGE_SPEED_MAX);
				mob.setDeltaMovement(to.scale(speed).add(0.0, LUNGE_UP, 0.0));
				mob.syncVelocity = true;
				mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
				duel.wasSwinging = true;
			}
		}
	}

	/** A player who has left themselves open to {@code mob}: eating or drinking, or with their back to it. */
	static boolean opening(Mob mob, Player player) {
		if (player.isCreative() || player.isSpectator() || !mob.getSensing().hasLineOfSight(player)) {
			return false;
		}
		if (player.isUsingItem() && player.getUseItem().has(DataComponents.CONSUMABLE)) {
			return true;
		}
		Vec3 to = flat(mob.position().subtract(player.position()));
		Vec3 look = flat(player.getLookAngle());
		return to != null && look != null && look.dot(to) < BACK_TURNED;
	}

	/** Fighters who step back out of reach: the timid, the suspicious and the gloomy, and anyone who knows a weapon's whole form. */
	static boolean careful(Mob mob) {
		return Temperaments.is(mob, Temperaments.Temperament.TIMID) || Temperaments.is(mob, Temperaments.Temperament.SUSPICIOUS)
			|| Temperaments.is(mob, Temperaments.Temperament.GLOOMY) || WeaponForms.experienced(mob) && !reckless(mob);
	}

	/** Fighters who never give ground: the hotheaded, the brave and the spiteful. */
	static boolean reckless(Mob mob) {
		return Temperaments.is(mob, Temperaments.Temperament.HOTHEADED) || Temperaments.is(mob, Temperaments.Temperament.BRAVE)
			|| Temperaments.is(mob, Temperaments.Temperament.SPITEFUL);
	}

	/** A chief, a ruler or a raid captain. */
	static boolean leader(Mob mob) {
		return mob instanceof Folk folk && folk.leader() || mob instanceof Raider raider && raider.isCaptain();
	}

	/** A leader with at least {@link #BAND_SIZE} of his own side still fighting near him. */
	private static boolean leaderWithBand(ServerLevel level, Mob mob) {
		if (!leader(mob)) {
			return false;
		}
		String side = Comrades.side(mob);
		return level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(BAND_RANGE),
			other -> other != mob && other.isAlive() && other.getTarget() != null && side.equals(Comrades.side(other))).size() >= BAND_SIZE;
	}

	/** Ground to stand on {@code reach} blocks along {@code way}: nothing in the way at the feet, something under them, no water. */
	private static boolean footing(ServerLevel level, Mob mob, Vec3 way, double reach) {
		BlockPos at = BlockPos.containing(mob.getX() + way.x * reach, mob.getY() + 0.2, mob.getZ() + way.z * reach);
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getFluidState(at).isEmpty()
			&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
	}

	/** {@code v} flattened and of length one, or null when it has no length on the ground. */
	private static @Nullable Vec3 flat(Vec3 v) {
		Vec3 ground = new Vec3(v.x, 0.0, v.z);
		return ground.lengthSqr() < 1.0E-4 ? null : ground.normalize();
	}

	/** The flinch: a hard blow from a player on a fighter that is winding up spoils the swing. */
	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (blocked || taken <= 0.0F || !(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !mob.isAlive()
			|| !MobCombat.blowWithin(mob, MobCombat.MAX_SWING_TICKS) || BossBars.boss(mob)) {
			return;
		}
		if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) {
			return;
		}
		MobCombat.Moves weapon = BetterCombatBridge.moves(player.getMainHandItem());
		float share = WeaponForms.experienced(mob) ? FLINCH_VETERAN_SHARE : FLINCH_SHARE;
		if (!(weapon != null && weapon.twoHanded()) && taken < mob.getMaxHealth() * share) {
			return;
		}
		MobCombat.checkBlow(mob);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, mob.getSoundSource(), 0.8F, 0.7F);
		level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY(0.7), mob.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
	}

	/** A chief or raid captain killed by a player: those of his side near enough to see it are tried ({@link #nerve}). */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Mob fallen) || !(fallen.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}
		boolean chief = fallen instanceof Folk folk && folk.leader() && folk.style() == FactionType.BARBARIAN
			|| fallen instanceof Raider raider && raider.isCaptain();
		if (!chief) {
			return;
		}
		String side = Comrades.side(fallen);
		long now = level.getGameTime();
		for (Mob other : level.getEntitiesOfClass(Mob.class, fallen.getBoundingBox().inflate(NERVE_RANGE),
			m -> m != fallen && m.isAlive() && !leader(m) && side.equals(Comrades.side(m)))) {
			if (other.getRandom().nextFloat() < nerve(other)) {
				Will.flee(other, killer, now, NERVE_FLEE_TICKS);
			}
		}
	}

	/** The chance a fighter breaks and runs when its leader falls, by temperament. */
	static float nerve(Mob mob) {
		Temperaments.Temperament t = Temperaments.of(mob);
		if (t == null) {
			return 0.0F;
		}
		return switch (t) {
			case TIMID -> 1.0F;
			case GLOOMY, SUSPICIOUS -> 0.6F;
			case GREEDY -> 0.4F;
			case BRAVE, HOTHEADED, PROUD, DUTIFUL, SPITEFUL -> 0.0F;
			default -> 0.15F;
		};
	}
}
