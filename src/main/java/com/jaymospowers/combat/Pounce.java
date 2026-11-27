package com.jaymospowers.combat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import com.jaymospowers.power.darktide.DarktideStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Yellow leaps: a mob that throws itself at someone, the blow of its leap a yellow one ({@link Guard#UNBLOCKABLE_TAG}).
 * Andy (2026-11-26): "i really like the lunge the necrophages do, that should extend to our zombies and husks, and they
 * should be telegraphed with a yellow indicator spark, meaning you should dodge or parry, but you cant block. so now
 * there will be three teirs of attack, white, yellow and red".
 * <p>
 * Two halves. {@link #gate} holds a vanilla leap ({@code LeapAtTargetGoal}: spiders, wolves, cats, foxes, Kingdoms'
 * werewolf, mimic, liger, tarantula and wild beasts, through {@code LeapTellMixin}) at a player for {@link #LEAP_WINDUP}
 * ticks first: it crouches, still, and a yellow star shows ({@link AttackTells#tellYellow}). {@link #launch} follows
 * any leap, vanilla's or {@link LungeGoal}'s: for as long as it flies, touching its target is a yellow blow
 * ({@link #flights}). Parried (in the window: a perfect parry throws it down for longer), it drops sprawled where it
 * met the guard; dodged, it lands and sprawls past. A sprawled mob is stunned ({@link DarktideStatus#stun}) and crouched.
 * Only leaps at a player get the tell and the blow; a wolf leaping at a sheep is left as vanilla has it.
 * <p>
 * Public for the corpse-eaters (Kingdoms) and anything else that leaps: {@link #tell}, {@link #launch}, {@link #sprawl}.
 * Vampires' {@code BloodLeap} (Kingdoms) is only ever a player's own leap, so it has nothing to tell.
 */
public final class Pounce {
	/** Ticks a leap at a player is held for its tell, how long it stays ready for the goal to pick it up, and how far (blocks) the target may stray meanwhile. */
	public static final int LEAP_WINDUP = 12, READY_TICKS = 8;
	public static final double HOLD_RANGE = 7.0;
	/** The longest a leap is watched for a touch (ticks), and the first ticks it can't have landed in yet. */
	public static final int MAX_FLIGHT = 30, LIFT_OFF = 3;
	/** How far (blocks) past its box a leaper reaches the one it touches. */
	public static final double TOUCH = 0.3;
	/** Sprawl lengths (ticks): parried, perfectly parried, dodged. */
	public static final int PARRY_SPRAWL = 30, PERFECT_SPRAWL = 60, DODGE_SPRAWL = 25;

	private static final class Flight {
		final LivingEntity target;
		final long start;
		boolean tried, landed;

		Flight(LivingEntity target, long start) {
			this.target = target;
			this.start = start;
		}
	}

	private record Wind(LivingEntity target, long until) {
	}

	private static final Map<Mob, Flight> FLIGHTS = new IdentityHashMap<>();
	private static final Map<Mob, Wind> WINDING = new IdentityHashMap<>();
	private static final Map<Mob, Long> READY = new IdentityHashMap<>();
	private static final Map<Mob, Long> SPRAWLED = new IdentityHashMap<>();
	/** A flight that was parried, to end next tick (true: perfectly), after the parry's own shove. */
	private static final Map<Mob, Boolean> PARRIED = new IdentityHashMap<>();

	private Pounce() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Pounce::tick);
		Guard.PARRIED.register((player, attacker, perfect) -> {
			if (attacker instanceof Mob mob && FLIGHTS.containsKey(mob)) {
				PARRIED.put(mob, perfect);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FLIGHTS.clear();
			WINDING.clear();
			READY.clear();
			SPRAWLED.clear();
			PARRIED.clear();
		});
	}

	/** The yellow tell for a leap that is about to start: the star and the ping at {@code mob}, and the cue to {@code target}. */
	public static void tell(Mob mob, LivingEntity target, int windup) {
		if (mob.level() instanceof ServerLevel level) {
			AttackTells.tellYellow(level, mob, windup);
			IncomingBlows.aimed(mob, target, windup, false, true);
		}
	}

	/**
	 * The vanilla leap's gate ({@code LeapAtTargetGoal#canUse}; {@code can}: what vanilla said). For a leap at a player: one
	 * that vanilla would make starts a {@link #LEAP_WINDUP} wait with the tell instead (false), and when the wait is over the
	 * leap is let through (true) for {@link #READY_TICKS} ticks, so the goal can pick it up. Any other leap is vanilla's.
	 */
	public static boolean gate(Mob mob, LivingEntity target, boolean can) {
		if (!(target instanceof Player) || !(mob.level() instanceof ServerLevel)) {
			return can;
		}
		if (READY.containsKey(mob)) {
			return mob.onGround();
		}
		if (WINDING.containsKey(mob) || SPRAWLED.containsKey(mob) || DarktideStatus.stunned(mob)) {
			return false;
		}
		if (can) {
			long until = mob.level().getGameTime() + LEAP_WINDUP;
			WINDING.put(mob, new Wind(target, until));
			tell(mob, target, LEAP_WINDUP);
			mob.getNavigation().stop();
		}
		return false;
	}

	/** A leap at {@code target} has begun ({@code LeapAtTargetGoal#start} at a player, or {@link LungeGoal}): from here until it lands, touching {@code target} is a yellow blow. */
	public static void launch(Mob mob, LivingEntity target) {
		READY.remove(mob);
		if (mob.getPose() == Pose.CROUCHING) {
			mob.setPose(Pose.STANDING);
		}
		FLIGHTS.put(mob, new Flight(target, mob.level().getGameTime()));
	}

	/** Whether {@code mob} is in a leap that is still watched for its touch. */
	public static boolean flying(Mob mob) {
		return FLIGHTS.containsKey(mob);
	}

	/** Whether {@code mob} is down after a parried or dodged leap. */
	public static boolean sprawled(Mob mob) {
		return SPRAWLED.containsKey(mob);
	}

	/** {@code mob} sprawls for {@code ticks}: stunned and crouched, a puff of dust under it. */
	public static void sprawl(Mob mob, int ticks) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		SPRAWLED.put(mob, level.getGameTime() + ticks);
		DarktideStatus.stun(mob, ticks);
		mob.setPose(Pose.CROUCHING);
		mob.getNavigation().stop();
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_BIG_FALL, mob.getSoundSource(), 0.7F, 0.8F);
		BlockState ground = mob.getBlockStateOn();
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), mob.getX(), mob.getY() + 0.1, mob.getZ(), 10, 0.3, 0.05, 0.3, 0.1);
		}
	}

	private static void tick(MinecraftServer server) {
		if (FLIGHTS.isEmpty() && WINDING.isEmpty() && READY.isEmpty() && SPRAWLED.isEmpty() && PARRIED.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, Wind> entry : new ArrayList<>(WINDING.entrySet())) {
			winding(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<Mob, Long> entry : new ArrayList<>(READY.entrySet())) {
			Mob mob = entry.getKey();
			if (!mob.isAlive() || mob.level().getGameTime() > entry.getValue()) {
				READY.remove(mob);
			}
		}
		for (Map.Entry<Mob, Flight> entry : new ArrayList<>(FLIGHTS.entrySet())) {
			watch(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<Mob, Long> entry : new ArrayList<>(SPRAWLED.entrySet())) {
			Mob mob = entry.getKey();
			if (!mob.isAlive() || mob.level().getGameTime() >= entry.getValue()) {
				SPRAWLED.remove(mob);
				if (mob.getPose() == Pose.CROUCHING) {
					mob.setPose(Pose.STANDING);
				}
			}
		}
	}

	/** A leap held for its tell: still and crouched, looking at its target, until the wait is over or the target is gone. */
	private static void winding(Mob mob, Wind wind) {
		LivingEntity target = wind.target();
		long now = mob.level().getGameTime();
		if (!mob.isAlive() || mob.getTarget() != target || !target.isAlive() || mob.distanceToSqr(target) > HOLD_RANGE * HOLD_RANGE
			|| DarktideStatus.stunned(mob)) {
			WINDING.remove(mob);
			standUp(mob);
			return;
		}
		if (now >= wind.until()) {
			WINDING.remove(mob);
			standUp(mob);
			READY.put(mob, now + READY_TICKS);
			return;
		}
		mob.setPose(Pose.CROUCHING);
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
	}

	private static void standUp(Mob mob) {
		if (mob.getPose() == Pose.CROUCHING && !SPRAWLED.containsKey(mob)) {
			mob.setPose(Pose.STANDING);
		}
	}

	/** A leap in the air: parried, it drops where it is; touching its target is the blow; landing without one is a miss. */
	private static void watch(Mob mob, Flight flight) {
		if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) {
			FLIGHTS.remove(mob);
			PARRIED.remove(mob);
			return;
		}
		Boolean parried = PARRIED.remove(mob);
		if (parried != null) {
			// it lands sprawled at the guard: the parry's shove is undone
			FLIGHTS.remove(mob);
			Vec3 motion = mob.getDeltaMovement();
			mob.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
			mob.needsSync = true;
			sprawl(mob, parried ? PERFECT_SPRAWL : PARRY_SPRAWL);
			return;
		}
		long age = level.getGameTime() - flight.start;
		LivingEntity target = flight.target;
		if (age > MAX_FLIGHT || !target.isAlive() || target.level() != level) {
			FLIGHTS.remove(mob);
			return;
		}
		if (!flight.tried && mob.getBoundingBox().inflate(TOUCH).intersects(target.getBoundingBox())) {
			flight.tried = true;
			mob.swingForAttack(InteractionHand.MAIN_HAND);
			flight.landed = Guard.unblockable(mob, () -> mob.doHurtTarget(level, target));
			if (PARRIED.containsKey(mob)) {
				// parried as it touched: handled at the top next tick
				return;
			}
		}
		if (age >= LIFT_OFF && mob.onGround()) {
			FLIGHTS.remove(mob);
			if (!flight.landed) {
				// dodged (or fell short): it sprawls past
				sprawl(mob, DODGE_SPRAWL);
			}
		}
	}
}
