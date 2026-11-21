package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Andy 2026-11-16: "mutant zombies should attack EVERYTHING around them, other hostile mobs included") Mutant
 * Monsters' Mutant Zombie (an unchanged jar, so this works from outside it) goes for the nearest living thing it sees:
 * players, villagers, animals and every other monster alike, whatever is closest first ({@link #PRIORITY}). It spares
 * only its own kind: other mutant zombies and the zombies a mutant's roar raised from the ground (they carry the mod's
 * {@code TrackSummonerGoal}). The game's own rule still holds that ordinary zombies can't hurt a mutant.
 * <p>The rest answer it sensibly:
 * <ul>
 * <li>Anything able to fight that a mutant strikes turns on it when it has no foe of its own ({@link #struck}); most
 * monsters and neutral beasts already do (their own hurt-by goal), this catches the ones without one.</li>
 * <li>Plain animals (cattle, sheep, pigs, horses...; not wolves, bears or bees, which fight) run from a mutant they can
 * see within {@link #FLEE_FROM} blocks ({@link Flee}), rather than stand about waiting to be eaten.</li>
 * </ul>
 * Every mob's {@code canAttack} rules still apply ({@code mixin.ThrallTargetMixin}: white flags, the Reaper's shroud,
 * the downed and so on), and a ghost in Limbo is still unseen.
 * <p>Andy (2026-11-18, a mutant through a pillager camp): "it may be a bit too strong, i do think that many pillagers should
 * have been able to take one". Out of the jar it gets back up from its first three deaths (at a quarter of its health,
 * after seven seconds no blade can touch it), so a crowd had to kill 260-odd health. Now it gets up only {@link #LIVES}
 * more time, and has {@link #HEALTH_CUT} less health ({@link #toned}): about 160 in all. Burning still keeps it down.
 * Its slams hit harder instead (Jaymo's Powers' crushing blows: thrown, torn, the downed burst).
 */
final class MutantRampage {
	/** The mod's own: the Mutant Zombie. */
	static final Identifier MUTANT_ZOMBIE = Identifier.fromNamespaceAndPath("mutantmonsters", "mutant_zombie");
	/** The rampage's place among the mutant's targets: beside its iron-golem goal (1), ahead of players (2) and villagers
	 * (3), behind its own hurt-by (0). Whatever is nearest is fair game, a player as much as a cow. */
	static final int PRIORITY = 1;
	/** A plain animal runs from a mutant it can see this near (blocks)... */
	static final double FLEE_FROM = 12.0;
	/** ...this far away (blocks, across; and up or down)... */
	static final int FLEE_REACH = 16, FLEE_RISE = 7;
	/** ...at this speed (a share of its own; a panic's is 1.25 to 2). */
	static final double FLEE_SPEED = 1.6;
	/** The goal Mutant Monsters gives each zombie a mutant's roar raised (by its class's simple name: no compile-time link). */
	static final String RAISED_GOAL = "TrackSummonerGoal";

	/** How many times a mutant zombie may get back up from death (the mod's own: 3). */
	static final int LIVES = 1;
	/** Health taken off a mutant zombie's most (the mod's own: 150). */
	static final double HEALTH_CUT = 25.0;
	/** The mod's field holding a mutant zombie's lives left (synced data, a byte; by name: no compile-time link). */
	static final String LIVES_FIELD = "DATA_REMAINING_LIVES";
	private static final Identifier TONED = JaymosKingdoms.id("mutant_toned");

	/** The mutant zombies loaded now, on any level (weak: an unloaded or dead one drops out on its own too). */
	private static final Set<Mob> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

	private MutantRampage() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				LIVE.remove(mob);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> struck(entity, source.getEntity()));
	}

	/** {@code e} is a Mutant Zombie. */
	static boolean mutant(Entity e) {
		return e != null && MUTANT_ZOMBIE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
	}

	private static void loaded(Entity entity) {
		if (!(entity instanceof Mob mob)) {
			return;
		}
		if (mutant(mob)) {
			LIVE.add(mob);
			toned(mob);
			((MobAccessor)mob).jaymoskingdoms$targets().addGoal(PRIORITY, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, true,
				(target, level) -> prey(target)));
		} else if (mob instanceof Animal animal && skittish(animal)) {
			((MobAccessor)animal).jaymoskingdoms$goals().addGoal(1, new Flee(animal));
		}
	}

	/**
	 * A mutant zombie toned down for a crowd to beat: at most {@link #LIVES} more lives, {@link #HEALTH_CUT} off its most
	 * health (kept on the mob, so it's taken off once).
	 */
	private static void toned(Mob mob) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && !health.hasModifier(TONED)) {
			health.addPermanentModifier(new AttributeModifier(TONED, -HEALTH_CUT, AttributeModifier.Operation.ADD_VALUE));
			mob.setHealth(Math.min(mob.getHealth(), mob.getMaxHealth()));
		}
		EntityDataAccessor<Byte> lives = livesField(mob.getClass());
		if (lives != null && mob.getEntityData().get(lives) > LIVES) {
			mob.getEntityData().set(lives, (byte) LIVES);
		}
	}

	private static @Nullable EntityDataAccessor<Byte> livesAccessor;
	private static boolean livesLooked;

	/** The mod's lives-left data on a mutant zombie's class, or null if it isn't there (another version of the mod). */
	@SuppressWarnings("unchecked")
	private static @Nullable EntityDataAccessor<Byte> livesField(Class<?> type) {
		if (!livesLooked) {
			livesLooked = true;
			for (Class<?> c = type; c != null && livesAccessor == null; c = c.getSuperclass()) {
				try {
					Field field = c.getDeclaredField(LIVES_FIELD);
					field.setAccessible(true);
					if (field.get(null) instanceof EntityDataAccessor<?> accessor && accessor.serializer() == EntityDataSerializers.BYTE) {
						livesAccessor = (EntityDataAccessor<Byte>) accessor;
					}
				} catch (ReflectiveOperationException | RuntimeException e) {
					// not on this class
				}
			}
			if (livesAccessor == null) {
				JaymosKingdoms.LOGGER.warn("Mutant zombie lives not found ({}): it keeps the mod's own", type.getName());
			}
		}
		return livesAccessor;
	}

	/** Everything living is the rampage's quarry, but its own kind and a stand for armour. */
	static boolean prey(LivingEntity target) {
		return target.isAlive() && !(target instanceof ArmorStand) && !mutant(target) && !raised(target);
	}

	/** {@code e} is a zombie a mutant's roar raised from the ground. */
	static boolean raised(Entity e) {
		if (!(e instanceof Mob mob)) {
			return false;
		}
		for (WrappedGoal goal : ((MobAccessor)mob).jaymoskingdoms$goals().getAvailableGoals()) {
			if (RAISED_GOAL.equals(goal.getGoal().getClass().getSimpleName())) {
				return true;
			}
		}
		return false;
	}

	/** A plain animal that runs on its own legs and goals (not a fighting beast, not one moved by a brain alone). */
	private static boolean skittish(Animal animal) {
		return !(animal instanceof NeutralMob) && !(animal instanceof Enemy)
			&& !((MobAccessor)animal).jaymoskingdoms$goals().getAvailableGoals().isEmpty();
	}

	/**
	 * A mutant struck {@code victim}: one able to fight, with no foe of its own, turns on it. A plain animal has no blow
	 * to give (no attack damage) and runs instead ({@link Flee}, or its own panic).
	 */
	private static void struck(LivingEntity victim, Entity attacker) {
		if (!(victim instanceof Mob mob) || !(attacker instanceof LivingEntity mutant) || !mutant(mutant) || mutant(mob) || !mob.isAlive()) {
			return;
		}
		if (mob.getTarget() != null && mob.getTarget().isAlive()) {
			return;
		}
		boolean fights = mob instanceof RangedAttackMob || mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE);
		if (fights && !(mob instanceof Animal a && skittish(a)) && mob.canAttack(mutant)) {
			mob.setTarget(mutant);
		}
	}

	/** The nearest live mutant zombie {@code mob} can see within {@link #FLEE_FROM}, or null. */
	private static Mob nearestSeen(Mob mob) {
		Mob best = null;
		double bestSq = FLEE_FROM * FLEE_FROM;
		for (Mob m : LIVE) {
			if (m.level() != mob.level() || !m.isAlive() || m.isRemoved()) {
				continue;
			}
			double sq = m.distanceToSqr(mob);
			if (sq < bestSq && mob.getSensing().hasLineOfSight(m)) {
				best = m;
				bestSq = sq;
			}
		}
		return best;
	}

	/** A plain animal runs from a mutant zombie it sees near (not while ridden or on a lead: its rider or keeper leads). */
	static final class Flee extends Goal {
		private final PathfinderMob mob;
		private Vec3 to;

		Flee(PathfinderMob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (LIVE.isEmpty() || this.mob.isVehicle() || this.mob.isLeashed()) {
				return false;
			}
			Mob from = nearestSeen(this.mob);
			if (from == null) {
				return false;
			}
			this.to = DefaultRandomPos.getPosAway(this.mob, FLEE_REACH, FLEE_RISE, from.position());
			return this.to != null;
		}

		@Override
		public void start() {
			this.mob.getNavigation().moveTo(this.to.x, this.to.y, this.to.z, FLEE_SPEED);
		}

		@Override
		public boolean canContinueToUse() {
			return !this.mob.getNavigation().isDone();
		}
	}
}
