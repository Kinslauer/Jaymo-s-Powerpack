package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import com.jaymoskingdoms.nether.Hornkin;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.184) Andy: "Villager and other ranged ai should not keep firing from the same position if their attacks fail,
 * instead they should reposition or try other tactics." One shared helper for every ranged attacker, so no shooter keeps
 * emptying its quiver from a spot where it can't hit.
 * <p>
 * <b>Watching the shots.</b> Every projectile a ranged mob looses (arrows, javelins, rocks, pistol and musket balls,
 * dynamite, magma bombs) is followed from the moment it enters the world. When it is gone it either struck the mob's
 * target (all well: the count of failures starts over), or it didn't: it fell short, flew wide, hit a wall, a shield or
 * one of the shooter's own side. That is a <i>failed shot</i>. A shooter that can't see its target while it wants to
 * shoot also fails, once every {@link #BLOCKED_TICKS}. Failures are forgotten after {@link #FORGET_TICKS} of calm.
 * <p>
 * <b>Changing tactics.</b> After {@link #FAIL_LIMIT} failures in a row the shooter picks a plan, and each time that doesn't
 * help the next one in line: step sideways ({@link Plan#STRAFE}), find a place with a clear line to the target
 * ({@link Plan#SEEK_SIGHT}), close in ({@link Plan#CLOSE_IN}) or back off ({@link Plan#BACK_OFF}), circle round to the
 * target's flank ({@link Plan#FLANK}), wait out of sight and come at them again ({@link Plan#COVER}), and for those who
 * carry a blade, give up shooting and fight hand to hand for a while ({@link #melee}). Folk, Hornkin, skeletons and
 * pillagers move through {@link RepositionGoal}; MCA's villagers through {@link #brainStep} (their brain is paused
 * while they move).
 * <p>
 * Cannons are fixed emplacements and sorcery isn't a projectile: neither is covered. Piglins' brains are left alone.
 */
public final class RangedTactics {
	/** Failed shots in a row before the shooter changes what it does. */
	static final int FAIL_LIMIT = 3;
	/** A shooter that has not failed for this long (ticks) starts with a clean slate. */
	static final int FORGET_TICKS = 600;
	/** Wanting to shoot but not seeing the target this long (ticks) counts as one failed shot. */
	static final int BLOCKED_TICKS = 40;
	/** A shot still in the air after this long (ticks) is written off, and one slower than this has stopped. */
	static final int SHOT_TIMEOUT = 120;
	static final double SHOT_STOPPED = 1.0E-4;
	/** The longest any one plan lasts (ticks), and how fast the shooter moves (a share of its speed). */
	static final int PLAN_TICKS = 100;
	static final double SPEED = 1.1;
	/** Sideways step (blocks) and how far off to the other plans' spots lie. */
	static final double STRAFE_MIN = 3.0, STRAFE_MAX = 6.0;
	static final double SIGHT_MIN = 2.0, SIGHT_MAX = 9.0;
	static final double CLOSE_MIN = 4.0, CLOSE_MAX = 8.0, CLOSE_LEAST_GAP = 4.0;
	static final double BACK_MIN = 5.0, BACK_MAX = 9.0;
	static final double COVER_MIN = 3.0, COVER_MAX = 8.0;
	/** How far round the target's flank it goes (degrees, least and most) and how far from the target it settles (blocks). */
	static final double FLANK_ANGLE_MIN = 60.0, FLANK_ANGLE_MAX = 100.0, FLANK_GAP_MIN = 6.0, FLANK_GAP_MAX = 14.0;
	/** Waiting in cover (ticks), and fighting hand to hand after giving up the bow (ticks). */
	static final int COVER_HOLD = 60, MELEE_TICKS = 160;
	/** Places tried for a plan, and the furthest from its target a shooter will settle (blocks). */
	static final int SAMPLES = 12;
	static final double RANGE = 28.0;
	/** The chance a shooter, repositioning, cries out. */
	static final float SPEAK_CHANCE = 0.3F;
	static final int SPEECH_LINES = 4;

	/** What a shooter does about failing. */
	enum Plan {
		NONE, STRAFE, SEEK_SIGHT, CLOSE_IN, BACK_OFF, FLANK, COVER
	}

	/** One shooter's record: failures, where it is in the plans, and the plan it is following. */
	private static final class State {
		@Nullable UUID target;
		int fails, plans, blocked;
		long lastFail;
		Plan plan = Plan.NONE;
		@Nullable Vec3 dest;
		long planEnds, planStarted, holdUntil, meleeUntil;
		boolean side;
		boolean holding;
	}

	/** A shot in the air. */
	private static final class Shot {
		final Projectile projectile;
		final Mob shooter;
		final UUID target;
		int age;
		boolean hit, blocked, other;

		Shot(Projectile projectile, Mob shooter, UUID target) {
			this.projectile = projectile;
			this.shooter = shooter;
			this.target = target;
		}
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();
	private static final Map<Projectile, Shot> BY_PROJECTILE = new WeakHashMap<>();
	private static final List<Shot> SHOTS = new ArrayList<>();
	/** Vanilla ranged mobs that have been given the reposition goal already. */
	private static final Set<Mob> GIVEN = java.util.Collections.newSetFromMap(new WeakHashMap<>());

	private RangedTactics() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RangedTactics::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(RangedTactics::tick);
	}

	// ---- who -------------------------------------------------------------------------------------------------------------

	/** Whether this mob changes its ways when its shots fail: the folk, Hornkin, skeletons, pillagers, and a realm's or camp's MCA guards. */
	static boolean reacts(Mob mob) {
		if (mob.isPassenger() || Downed.isDowned(mob)) {
			return false;
		}
		if (mob instanceof Villager v) {
			return !v.isBaby() && (Downed.isGuard(v) || Barbarians.isCampVillager(v));
		}
		return mob instanceof Folk || mob instanceof Hornkin || mob instanceof AbstractSkeleton || mob instanceof Pillager;
	}

	/** Whether it has a blade to fall back on when shooting fails: folk and villagers do, a skeleton's bow is all it has. */
	private static boolean canMelee(Mob mob) {
		return mob instanceof Folk || mob instanceof Villager;
	}

	private static void loaded(Entity entity) {
		if (entity instanceof Projectile projectile && !projectile.level().isClientSide() && projectile.getOwner() instanceof Mob shooter && reacts(shooter)) {
			LivingEntity target = shooter.getTarget();
			if (target != null && target != shooter) {
				Shot shot = new Shot(projectile, shooter, target.getUUID());
				SHOTS.add(shot);
				BY_PROJECTILE.put(projectile, shot);
			}
		} else if ((entity instanceof AbstractSkeleton || entity instanceof Pillager) && GIVEN.add((Mob)entity)) {
			// (the game's own bowmen and crossbowmen; our folk and the Hornkin add the goal themselves)
			((MobAccessor)entity).jaymoskingdoms$goals().addGoal(0, new RepositionGoal((Mob)entity));
		}
	}

	// ---- the shots ---------------------------------------------------------------------------------------------------------

	/** A shot's hit: on the target it is a success, in a shield or one of its own side's backs it is not. */
	private static boolean allowDamage(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (BY_PROJECTILE.isEmpty() || source.getDirectEntity() == null) {
			return true;
		}
		Shot shot = BY_PROJECTILE.get(source.getDirectEntity());
		if (shot == null) {
			return true;
		}
		if (victim.getUUID().equals(shot.target) || victim == shot.shooter.getTarget()) {
			if (victim.isBlocking()) {
				shot.blocked = true;
			} else {
				shot.hit = true;
			}
		} else if (friendly(shot.shooter, victim)) {
			shot.blocked = true;
		} else {
			shot.other = true;
		}
		return true;
	}

	private static boolean friendly(Mob shooter, LivingEntity victim) {
		if (shooter instanceof Folk s && victim instanceof Folk v) {
			return s.sameSide(v);
		}
		return victim instanceof Villager && !Barbarians.isCampVillager(victim) && !Barbarians.isCampVillager(shooter);
	}

	private static void tick(MinecraftServer server) {
		if (SHOTS.isEmpty()) {
			return;
		}
		for (int i = SHOTS.size() - 1; i >= 0; i--) {
			Shot shot = SHOTS.get(i);
			Projectile p = shot.projectile;
			shot.age++;
			boolean landed = p.isRemoved() || shot.age > SHOT_TIMEOUT
				|| shot.age > 3 && !(p instanceof Dynamite) && p.getDeltaMovement().lengthSqr() < SHOT_STOPPED;
			if (!landed && !shot.hit && !shot.blocked) {
				continue;
			}
			SHOTS.remove(i);
			BY_PROJECTILE.remove(p);
			Mob shooter = shot.shooter;
			if (!shooter.isAlive() || !(shooter.level() instanceof ServerLevel level)) {
				continue;
			}
			Entity target = level.getEntity(shot.target);
			if (!(target instanceof LivingEntity foe) || !foe.isAlive()) {
				// (the shot that killed it, or it died of something else: nothing to learn)
				succeed(shooter);
			} else if (shot.hit) {
				succeed(shooter);
			} else if (shot.blocked || !shot.other) {
				fail(shooter, foe);
			}
		}
	}

	private static State state(Mob shooter) {
		return STATES.computeIfAbsent(shooter, m -> new State());
	}

	/** A shot hit: the failures start over. */
	static void succeed(Mob shooter) {
		State s = STATES.get(shooter);
		if (s != null) {
			s.fails = 0;
			s.plans = 0;
			s.blocked = 0;
		}
	}

	/**
	 * It wants to shoot {@code target} and can't see it (call every tick that holds): every {@link #BLOCKED_TICKS} of that
	 * is a failure. From {@link Folk.Shoot}.
	 */
	static void blocked(Mob shooter, LivingEntity target) {
		if (!reacts(shooter)) {
			return;
		}
		State s = state(shooter);
		if (s.plan != Plan.NONE) {
			return;
		}
		if (++s.blocked >= BLOCKED_TICKS) {
			s.blocked = 0;
			fail(shooter, target);
		}
	}

	/** It sees its target again (a shooter that waits for a clear line stops counting). */
	static void seen(Mob shooter) {
		State s = STATES.get(shooter);
		if (s != null) {
			s.blocked = 0;
		}
	}

	/** A shot failed; the {@link #FAIL_LIMIT}th in a row sets the shooter on a new plan. */
	static void fail(Mob shooter, LivingEntity target) {
		if (!(shooter.level() instanceof ServerLevel level) || !reacts(shooter)) {
			return;
		}
		long now = level.getGameTime();
		State s = state(shooter);
		if (s.target == null || !s.target.equals(target.getUUID())) {
			// (a new target: a new start)
			s.target = target.getUUID();
			s.fails = 0;
			s.plans = 0;
			s.plan = Plan.NONE;
			s.blocked = 0;
		}
		if (now - s.lastFail > FORGET_TICKS) {
			s.fails = 0;
			s.plans = 0;
		}
		s.lastFail = now;
		if (s.plan != Plan.NONE || s.meleeUntil > now) {
			return;
		}
		if (++s.fails >= FAIL_LIMIT) {
			s.fails = 0;
			s.plans++;
			choose(level, shooter, target, s, now);
		}
	}

	// ---- the plans -----------------------------------------------------------------------------------------------------

	private static void choose(ServerLevel level, Mob shooter, LivingEntity target, State s, long now) {
		boolean sees = shooter.getSensing().hasLineOfSight(target);
		double gap = shooter.distanceTo(target);
		// what to try, in order: each failure after the last plan moves one down the list
		List<Plan> order = new ArrayList<>();
		if (sees) {
			order.add(Plan.STRAFE);
			order.add(gap > 12.0 ? Plan.CLOSE_IN : Plan.BACK_OFF);
			order.add(Plan.FLANK);
		} else {
			order.add(Plan.SEEK_SIGHT);
			order.add(Plan.CLOSE_IN);
			order.add(Plan.FLANK);
		}
		order.add(null);
		for (int i = 0; i < order.size(); i++) {
			Plan plan = order.get((s.plans - 1 + i) % order.size());
			if (plan == null) {
				if (canMelee(shooter)) {
					// the last resort of a plan that keeps failing: put the bow away and fight hand to hand
					s.meleeUntil = now + MELEE_TICKS;
					s.plans = 0;
					return;
				}
				plan = Plan.COVER;
			}
			Vec3 spot = spot(level, shooter, target, plan, s);
			if (spot != null) {
				begin(level, shooter, s, plan, spot, now);
				return;
			}
		}
		// nowhere to go: at least don't stand there (a short wait in place, then it tries again)
		s.plans = Math.max(0, s.plans - 1);
	}

	private static void begin(ServerLevel level, Mob shooter, State s, Plan plan, Vec3 spot, long now) {
		s.plan = plan;
		s.dest = spot;
		s.planStarted = now;
		s.planEnds = now + PLAN_TICKS;
		s.holding = false;
		s.holdUntil = 0L;
		if (plan == Plan.STRAFE) {
			s.side = !s.side;
		}
		if (level.getRandom().nextFloat() < SPEAK_CHANCE && (shooter instanceof Folk || shooter instanceof Villager)) {
			Speech.say(level, shooter, Component.translatable("speech.jaymoskingdoms.ranged." + plan.name().toLowerCase(java.util.Locale.ROOT) + "_"
				+ level.getRandom().nextInt(SPEECH_LINES)), Speech.YELLOW, 24.0);
		}
	}

	/** A place to go for {@code plan}, or null if none was found. */
	private static @Nullable Vec3 spot(ServerLevel level, Mob shooter, LivingEntity target, Plan plan, State s) {
		Vec3 me = shooter.position();
		Vec3 foe = target.position();
		Vec3 toFoe = new Vec3(foe.x - me.x, 0.0, foe.z - me.z);
		Vec3 ahead = toFoe.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : toFoe.normalize();
		Vec3 side = new Vec3(-ahead.z, 0.0, ahead.x).scale(s.side ? 1.0 : -1.0);
		switch (plan) {
			case STRAFE:
				return find(level, shooter, target, true, r -> me.add(side.scale(STRAFE_MIN + r.nextDouble() * (STRAFE_MAX - STRAFE_MIN)))
					.add(ahead.scale(r.nextDouble() * 3.0 - 1.5)));
			case SEEK_SIGHT:
				return find(level, shooter, target, true, r -> ring(r, me, SIGHT_MIN, SIGHT_MAX));
			case CLOSE_IN: {
				Function<RandomSource, Vec3> closer = r -> me.add(ahead.scale(CLOSE_MIN + r.nextDouble() * (CLOSE_MAX - CLOSE_MIN)))
					.add(side.scale(r.nextDouble() * 4.0 - 2.0));
				Vec3 found = find(level, shooter, target, true, closer);
				return found != null ? found : find(level, shooter, target, false, closer);
			}
			case BACK_OFF: {
				Function<RandomSource, Vec3> away = r -> me.subtract(ahead.scale(BACK_MIN + r.nextDouble() * (BACK_MAX - BACK_MIN)))
					.add(side.scale(r.nextDouble() * 4.0 - 2.0));
				Vec3 found = find(level, shooter, target, true, away);
				return found != null ? found : find(level, shooter, target, false, away);
			}
			case FLANK:
				return find(level, shooter, target, true, r -> {
					double turn = Math.toRadians(FLANK_ANGLE_MIN + r.nextDouble() * (FLANK_ANGLE_MAX - FLANK_ANGLE_MIN)) * (s.side ? 1.0 : -1.0);
					double gap = Math.max(FLANK_GAP_MIN, Math.min(shooter.distanceTo(target), FLANK_GAP_MAX));
					// from the target, back along where the shooter is, turned round to the side
					Vec3 back = ahead.scale(-1.0);
					double cos = Math.cos(turn), sin = Math.sin(turn);
					Vec3 dir = new Vec3(back.x * cos - back.z * sin, 0.0, back.x * sin + back.z * cos);
					return foe.add(dir.scale(gap));
				});
			case COVER:
				return find(level, shooter, target, false, r -> ring(r, me, COVER_MIN, COVER_MAX), p -> !clear(level, shooter, p, target));
			default:
				return null;
		}
	}

	private static Vec3 ring(RandomSource r, Vec3 centre, double least, double most) {
		double angle = r.nextDouble() * Math.PI * 2.0;
		double d = least + r.nextDouble() * (most - least);
		return centre.add(Math.cos(angle) * d, 0.0, Math.sin(angle) * d);
	}

	private static @Nullable Vec3 find(ServerLevel level, Mob shooter, LivingEntity target, boolean needSight, Function<RandomSource, Vec3> candidate) {
		return find(level, shooter, target, needSight, candidate, p -> true);
	}

	/** Tries {@link #SAMPLES} candidate spots: standing room, in reach of the target, a way there, and (if asked) a clear line to it. */
	private static @Nullable Vec3 find(ServerLevel level, Mob shooter, LivingEntity target, boolean needSight, Function<RandomSource, Vec3> candidate,
		java.util.function.Predicate<Vec3> extra) {
		RandomSource random = shooter.getRandom();
		for (int i = 0; i < SAMPLES; i++) {
			Vec3 c = candidate.apply(random);
			BlockPos ground = ground(level, c, shooter.getBlockY());
			if (ground == null) {
				continue;
			}
			Vec3 p = Vec3.atBottomCenterOf(ground);
			if (p.distanceTo(target.position()) > RANGE || p.distanceTo(target.position()) < CLOSE_LEAST_GAP * 0.5) {
				continue;
			}
			if (needSight && !clear(level, shooter, p, target) || !extra.test(p)) {
				continue;
			}
			Path path = shooter.getNavigation().createPath(ground, 0);
			if (path != null && path.canReach()) {
				return p;
			}
		}
		return null;
	}

	/** Standing room (air to stand in, a floor under) near {@code at}, within a few blocks of {@code y}. */
	private static @Nullable BlockPos ground(ServerLevel level, Vec3 at, int y) {
		BlockPos column = BlockPos.containing(at.x, y, at.z);
		for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
			BlockPos feet = column.offset(0, dy, 0);
			if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
				&& level.getFluidState(feet).isEmpty() && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
				return feet;
			}
		}
		return null;
	}

	/** Whether someone standing at {@code p} would have a clear line to {@code target}. */
	private static boolean clear(ServerLevel level, Mob shooter, Vec3 p, LivingEntity target) {
		Vec3 eye = p.add(0.0, shooter.getEyeHeight(), 0.0);
		return level.clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter)).getType() == HitResult.Type.MISS;
	}

	// ---- driving a plan ------------------------------------------------------------------------------------------------------

	/** It is following a plan just now (the shooter's goals and brain leave its legs to this). */
	static boolean active(Mob mob) {
		State s = STATES.get(mob);
		return s != null && s.plan != Plan.NONE;
	}

	/** It has put its bow away for a while and fights hand to hand ({@code Folk#shooting}). */
	public static boolean melee(Mob mob) {
		State s = STATES.get(mob);
		return s != null && s.meleeUntil > mob.level().getGameTime();
	}

	private static void end(State s) {
		s.plan = Plan.NONE;
		s.dest = null;
		s.holding = false;
		s.fails = 0;
		s.blocked = 0;
	}

	/** Moves the shooter along its plan. False once the plan is over (reached, timed out, or the target gone). */
	private static boolean drive(Mob mob) {
		State s = STATES.get(mob);
		if (s == null || s.plan == Plan.NONE || s.dest == null) {
			return false;
		}
		long now = mob.level().getGameTime();
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive() || now >= s.planEnds || mob.isPassenger() || Downed.isDowned(mob)) {
			end(s);
			return false;
		}
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		Vec3 d = s.dest;
		boolean there = mob.distanceToSqr(d.x, d.y, d.z) < 2.25 || mob.tickCount % 10 == 0 && mob.getNavigation().isDone() && now - s.planStarted > 10;
		boolean sees = mob.getSensing().hasLineOfSight(target);
		if (s.plan == Plan.COVER) {
			if (there && !s.holding) {
				s.holding = true;
				s.holdUntil = now + COVER_HOLD;
			}
			if (s.holding) {
				mob.getNavigation().stop();
				if (now >= s.holdUntil) {
					end(s);
					return false;
				}
				return true;
			}
		} else if (there || s.plan == Plan.SEEK_SIGHT && sees && now - s.planStarted > 10) {
			end(s);
			return false;
		}
		if (mob.tickCount % 10 == 0 || mob.getNavigation().isDone()) {
			mob.getNavigation().moveTo(d.x, d.y, d.z, SPEED);
		}
		return true;
	}

	/**
	 * For a mob whose brain, not goals, runs it (MCA's villagers): from the start of its server step; true to skip the
	 * brain this tick, the shooter being on the move.
	 */
	public static boolean brainStep(Mob mob) {
		if (!active(mob)) {
			return false;
		}
		if (mob instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		return drive(mob);
	}

	/** Runs the plan for a mob with goals. */
	public static final class RepositionGoal extends Goal {
		private final Mob mob;

		public RepositionGoal(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return active(this.mob);
		}

		@Override
		public boolean canContinueToUse() {
			return active(this.mob);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			drive(this.mob);
		}

		@Override
		public void stop() {
			if (!active(this.mob)) {
				this.mob.getNavigation().stop();
			}
		}
	}
}
