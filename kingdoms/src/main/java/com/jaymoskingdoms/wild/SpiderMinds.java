package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.97) Every spider is small and hunts like one. Sizes after More Babies' baby spiders (all rights reserved, by
 * mammut53: only its proportions are used, none of its code or art): spiders at half size, cave spiders at about two
 * thirds, hitbox and all, through the game's own scale attribute (saved with the spider, so corpses keep it). The
 * wall and ceiling climbing is Stormie's Spiders' (the 26.3 fork of NYF's Spiders, already in the pack); the hunting
 * on top of it is ours, after the ideas in Yuka (MIT, Mugen87's JavaScript game-AI library):
 * <ul>
 * <li><b>Perception and memory</b>: a spider remembers where it last saw its prey and which way it was going. In the
 * dark it hears anyone running or jumping near by, seen or not (sneak to slip past).</li>
 * <li><b>Pursuit</b>: it runs at where you're going to be, not where you are.</li>
 * <li><b>Offset pursuit</b>: spiders hunting the same prey spread round it and come in from every side.</li>
 * <li><b>Evade</b>, weighed fuzzy-logic style: badly hurt and with few others near, it runs from its attacker, then
 * comes back for more.</li>
 * <li><b>Search</b>: prey lost from sight, it goes where the prey was heading and casts about there for a while.</li>
 * <li><b>Wander</b>: idle spiders meander along smooth arcs instead of picking spots at random.</li>
 * <li><b>Ceiling ambush</b>: in the dark, a spider now and then climbs to a ceiling, hangs there, and drops on whoever
 * walks under it.</li>
 * </ul>
 * Day or night they keep the game's temper: in daylight they only fight back. The tarantula keeps its size.
 */
public final class SpiderMinds {
	/** Scale of a spider and of a cave spider (1 = the game's size). */
	static final double SPIDER_SCALE = 0.5, CAVE_SPIDER_SCALE = 0.65;
	/** A spider is hostile below this light value (the game's own spider rule). */
	static final float DARK = 0.5F;

	/** Memory: how long (ticks) it remembers where prey was, and how far ahead it guesses where prey went (ticks). */
	static final int MEMORY_TICKS = 300, SEARCH_LEAD_TICKS = 40;
	/** Prey unseen this long (ticks; Stormie's own target memory) or out of follow range is given up, and searched for. */
	static final int UNSEEN_TICKS = 200;
	/** Hearing: how far (blocks) it hears a runner or a jumper, how often it listens (ticks). */
	static final double HEAR_RANGE = 12.0;
	static final int LISTEN_EVERY = 10;

	/** Pursuit runs while prey is farther than this (blocks), and hands over to the bite (and Stormie's leap, 2-4 blocks)
	 * closer than {@link #CLOSE}. */
	static final double PURSUE_FROM = 4.5, CLOSE = 4.0;
	/** At most this many ticks of the prey's own movement are led. Blocks a tick a spider is reckoned to cover. */
	static final double LEAD_MAX_TICKS = 20.0, OWN_SPEED = 0.25;
	/** Offset pursuit: hunters within this range (blocks) of each other share the prey; each takes a slot this far out
	 * (inside {@link #PURSUE_FROM}, so it doesn't start chasing again from its slot), and goes in from there. */
	static final double PACK_RANGE = 16.0, SLOT_RADIUS = 3.0;
	/** Pursuit re-aims every so many ticks; the pack is counted every so many. */
	static final int AIM_EVERY = 5, PACK_EVERY = 20;

	/** Evade: hurt below FLEE_HEALTH it starts to weigh running; at or below PANIC_HEALTH it is fully afraid. */
	static final float FLEE_HEALTH = 0.45F, PANIC_HEALTH = 0.15F;
	/** It runs when the fear (0..1) passes this. Every other spider within ALLY_RANGE eases it (counts up to ALLIES). */
	static final float FLEE_AT = 0.5F;
	static final double ALLY_RANGE = 8.0;
	static final int ALLIES = 3;
	/** It runs this long, this far (blocks), then won't run again for COURAGE_TICKS. */
	static final int FLEE_TICKS = 100, FLEE_DISTANCE = 14, COURAGE_TICKS = 200;

	/** Search: how long it casts about where prey went (ticks), and how wide (blocks). */
	static final int SEARCH_TICKS = 160, SEARCH_RADIUS = 5;

	/** Wander (Yuka's circle ahead): radius, how far ahead it sits, and how much it jitters each pick (blocks). */
	static final double WANDER_RADIUS = 3.0, WANDER_DISTANCE = 6.0, WANDER_JITTER = 1.5;
	/** Chance a tick an idle spider sets off. */
	static final float WANDER_CHANCE = 1.0F / 80.0F;

	/** Ceiling ambush: chance a tick (idle, in the dark) it goes looking for a ceiling; how far up it looks (blocks). */
	static final float CEILING_CHANCE = 1.0F / 400.0F;
	static final int CEILING_UP = 6, CEILING_CLEAR = 3, CEILING_TRAVEL = 200;
	/** It hangs this long (plus up to as much again), then won't go up again for CEILING_REST. */
	static final int HANG_TICKS = 1200, CEILING_REST = 600;
	/** It drops on prey this close sideways (blocks) and below it; this hard. */
	static final double DROP_SIDEWAYS = 2.0, DROP_BELOW = 8.0, DROP_SPEED = 0.6;

	/** Seeing prey as the game's own target goals do (sight, invisibility, mob heads), searching and from a ceiling. */
	private static final TargetingConditions SEE_SEARCHING = TargetingConditions.forCombat().range(PACK_RANGE),
		SEE_BELOW = TargetingConditions.forCombat().range(DROP_BELOW * 1.5);

	/** The spiders already given these goals (goals aren't saved; a reload gives them again to the new spider). Only the
	 * spider's own goals hold its Mind, so nothing here keeps a spider alive. */
	private static final Set<Spider> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private SpiderMinds() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Spider spider) {
				size(spider);
				if (GIVEN.add(spider)) {
					Mind mind = new Mind(spider);
					GoalSelector goals = ((MobAccessor) spider).jaymoskingdoms$goals();
					// no flags: runs beside everything else
					goals.addGoal(0, new PerceiveGoal(mind));
					goals.addGoal(2, new EvadeGoal(mind));
					// beside Stormie's leap (3), which only wants prey 2-4 blocks off; before the bite (4)
					goals.addGoal(3, new PursuitGoal(mind));
					goals.addGoal(4, new SearchGoal(mind));
					goals.addGoal(4, new CeilingGoal(mind));
					goals.addGoal(5, new WanderGoal(mind));
				}
			}
		});
	}

	/** Shrinks a spider to its size (idempotent: the modifier replaces itself). */
	static void size(Spider spider) {
		AttributeInstance scale = spider.getAttribute(Attributes.SCALE);
		if (scale != null) {
			double to = spider instanceof CaveSpider ? CAVE_SPIDER_SCALE : SPIDER_SCALE;
			scale.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("spider_size"), to - 1.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	static boolean dark(Spider spider) {
		return spider.getLightLevelDependentMagicValue() < DARK;
	}

	static boolean prey(@Nullable LivingEntity who) {
		return who != null && who.isAlive() && !(who instanceof Player player && (player.isCreative() || player.isSpectator()));
	}

	/** What a spider knows: where it last saw its prey, which way that was going, and when. */
	static final class Mind {
		final Spider spider;
		@Nullable Vec3 lastSeen;
		Vec3 lastHeading = Vec3.ZERO;
		long seenAt = Long.MIN_VALUE;
		/** The prey it was last after (a new one starts its unseen count afresh). */
		@Nullable LivingEntity tracked;
		/** Game time it may run again / may go up to a ceiling again. */
		long braveAt, ceilingAt;
		/** Yuka's wander target: a point on a circle round the spot ahead, kept between picks so the path curves. */
		Vec3 wander = new Vec3(WANDER_RADIUS, 0.0, 0.0);

		Mind(Spider spider) {
			this.spider = spider;
		}

		long now() {
			return this.spider.level().getGameTime();
		}

		void saw(LivingEntity prey) {
			this.lastSeen = prey.position();
			this.lastHeading = prey.getKnownMovement().multiply(1.0, 0.0, 1.0);
			this.seenAt = now();
		}

		boolean remembers() {
			return this.lastSeen != null && now() - this.seenAt <= MEMORY_TICKS;
		}

		void forget() {
			this.lastSeen = null;
		}

		boolean idle() {
			return this.spider.getTarget() == null && !this.spider.isVehicle() && !this.spider.isPassenger();
		}
	}

	/** Sight and hearing feed the memory; a heard runner in the dark becomes prey. */
	private static final class PerceiveGoal extends Goal {
		private final Mind mind;

		PerceiveGoal(Mind mind) {
			this.mind = mind;
			this.setFlags(EnumSet.noneOf(Goal.Flag.class));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Spider spider = this.mind.spider;
			LivingEntity target = spider.getTarget();
			if (target != this.mind.tracked) {
				// a dead target reads as none (getTarget), so the one it was after tells whether it made a kill
				if (target == null && this.mind.tracked != null && !this.mind.tracked.isAlive()) {
					// nothing left to look for
					this.mind.forget();
				}
				this.mind.tracked = target;
				if (target != null) {
					this.mind.seenAt = this.mind.now();
				}
			}
			if (prey(target) && spider.getSensing().hasLineOfSight(target)) {
				this.mind.saw(target);
				return;
			}
			if (prey(target)) {
				double follow = spider.getAttributeValue(Attributes.FOLLOW_RANGE);
				if (spider.distanceToSqr(target) > follow * follow || this.mind.now() - this.mind.seenAt > UNSEEN_TICKS) {
					// lost: dropped, but where it was is kept for the search
					spider.setTarget(null);
				}
				return;
			}
			if (target != null || spider.tickCount % LISTEN_EVERY != 0 || !dark(spider)) {
				return;
			}
			Player heard = spider.level().getNearestPlayer(spider.getX(), spider.getY(), spider.getZ(), HEAR_RANGE,
				p -> p instanceof Player player && prey(player) && !player.isDiscrete() && (player.isSprinting() || !player.onGround()
					&& !player.isInWater() && !player.getAbilities().flying));
			if (heard != null) {
				spider.setTarget(heard);
				this.mind.saw(heard);
			}
		}
	}

	/** Badly hurt and short of friends, it runs from its attacker for a while, then comes back. */
	private static final class EvadeGoal extends Goal {
		private final Mind mind;
		private int ticks, repathAt;
		private @Nullable LivingEntity from;

		EvadeGoal(Mind mind) {
			this.mind = mind;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		/** Fuzzy fear: how badly hurt (0 above FLEE_HEALTH, 1 at PANIC_HEALTH), lessened by friends near by. */
		private float fear() {
			Spider spider = this.mind.spider;
			float health = spider.getHealth() / spider.getMaxHealth();
			float hurt = Mth.clamp((FLEE_HEALTH - health) / (FLEE_HEALTH - PANIC_HEALTH), 0.0F, 1.0F);
			if (hurt <= 0.0F) {
				return 0.0F;
			}
			int allies = spider.level().getEntitiesOfClass(Spider.class, spider.getBoundingBox().inflate(ALLY_RANGE),
				s -> s != spider && s.isAlive()).size();
			float alone = 1.0F - Math.min(allies, ALLIES) / (float) ALLIES;
			return hurt * (0.5F + 0.5F * alone);
		}

		@Override
		public boolean canUse() {
			Spider spider = this.mind.spider;
			// weighed now and then, not every tick (the goal selector only asks every other tick anyway)
			if (spider.getRandom().nextInt(5) != 0 || this.mind.now() < this.mind.braveAt || spider.isPassenger()) {
				return false;
			}
			LivingEntity threat = spider.getLastHurtByMob();
			if (threat == null || !threat.isAlive() || spider.tickCount - spider.getLastHurtByMobTimestamp() > FLEE_TICKS * 2) {
				threat = spider.getTarget();
			}
			if (!prey(threat) || spider.distanceToSqr(threat) > PACK_RANGE * PACK_RANGE || this.fear() < FLEE_AT) {
				return false;
			}
			this.from = threat;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.ticks < FLEE_TICKS && prey(this.from);
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.repathAt = 0;
			this.run();
		}

		@Override
		public void stop() {
			this.mind.braveAt = this.mind.now() + COURAGE_TICKS;
			this.from = null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.ticks++;
			if (this.ticks % 20 == 0 || this.mind.spider.getNavigation().isDone() && this.ticks >= this.repathAt) {
				this.run();
				this.repathAt = this.ticks + 10;
			}
		}

		/** Yuka's evade: away from where the threat is going to be, not where it is. */
		private void run() {
			LivingEntity from = this.from;
			if (from == null) {
				return;
			}
			Vec3 coming = from.position().add(from.getKnownMovement().multiply(10.0, 0.0, 10.0));
			Vec3 away = DefaultRandomPos.getPosAway(this.mind.spider, FLEE_DISTANCE, 7, coming);
			if (away != null) {
				this.mind.spider.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
			}
		}
	}

	/** Runs at where the prey will be, and with others on the same prey, at its own side of it. */
	private static final class PursuitGoal extends Goal {
		private final Mind mind;
		private int ticks;
		/** This spider's slot round the prey, and how many share it (1 = alone: no slot). */
		private int slot, pack = 1;

		PursuitGoal(Mind mind) {
			this.mind = mind;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Spider spider = this.mind.spider;
			LivingEntity target = spider.getTarget();
			return prey(target) && !spider.isPassenger() && spider.distanceToSqr(target) > PURSUE_FROM * PURSUE_FROM;
		}

		@Override
		public boolean canContinueToUse() {
			Spider spider = this.mind.spider;
			LivingEntity target = spider.getTarget();
			if (!prey(target)) {
				return false;
			}
			// the game's spider temper: in daylight it may lose interest any moment
			if (!dark(spider) && spider.getRandom().nextInt(100) == 0) {
				spider.setTarget(null);
				return false;
			}
			// alone it closes to the bite; in a pack it goes round to its own side first
			return this.pack > 1 ? !this.atSlot(target) && spider.distanceToSqr(target) > 2.0 * 2.0 : spider.distanceToSqr(target) > CLOSE * CLOSE;
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.mind.spider.setAggressive(true);
			this.countPack();
			this.aim();
		}

		@Override
		public void stop() {
			// still after it: the last path keeps closing in until the bite takes over (its own check runs once a second)
			if (!prey(this.mind.spider.getTarget())) {
				this.mind.spider.getNavigation().stop();
				this.mind.spider.setAggressive(false);
			}
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.ticks++;
			LivingEntity target = this.mind.spider.getTarget();
			if (target != null) {
				this.mind.spider.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
			if (this.ticks % PACK_EVERY == 0) {
				this.countPack();
			}
			if (this.ticks % AIM_EVERY == 0) {
				this.aim();
			}
		}

		/** The others on the same prey near by, in a fixed order (by id) so each keeps its slot. */
		private void countPack() {
			Spider spider = this.mind.spider;
			LivingEntity target = spider.getTarget();
			if (target == null) {
				return;
			}
			List<Spider> pack = spider.level().getEntitiesOfClass(Spider.class, target.getBoundingBox().inflate(PACK_RANGE),
				s -> s.isAlive() && s.getTarget() == target);
			pack.sort(Comparator.comparingInt(Spider::getId));
			this.pack = Math.max(1, pack.size());
			this.slot = Math.max(0, pack.indexOf(spider));
		}

		/** Its own spot round the prey (its slot), or the prey itself when it hunts alone. */
		private Vec3 slotOffset(LivingEntity target) {
			if (this.pack <= 1) {
				return Vec3.ZERO;
			}
			// slots spread evenly round the prey, the first behind it
			double facing = Math.toRadians(target.getYRot() + 90.0);
			double angle = facing + Math.PI + Math.PI * 2.0 * this.slot / this.pack;
			return new Vec3(Math.cos(angle) * SLOT_RADIUS, 0.0, Math.sin(angle) * SLOT_RADIUS);
		}

		private boolean atSlot(LivingEntity target) {
			return this.pack > 1 && this.mind.spider.position().distanceToSqr(target.position().add(this.slotOffset(target))) < 1.5;
		}

		/** Yuka's pursuit: lead the prey by the time it takes to get there; offset to its slot. */
		private void aim() {
			Spider spider = this.mind.spider;
			LivingEntity target = spider.getTarget();
			if (target == null) {
				return;
			}
			double lead = Math.min(LEAD_MAX_TICKS, spider.distanceTo(target) / OWN_SPEED);
			Vec3 at = target.position().add(target.getKnownMovement().multiply(lead, 0.0, lead)).add(this.slotOffset(target));
			if (!spider.getNavigation().moveTo(at.x, at.y, at.z, 1.0)) {
				spider.getNavigation().moveTo(target, 1.0);
			}
		}
	}

	/** Prey lost from sight: it goes where the prey was heading and casts about there. */
	private static final class SearchGoal extends Goal {
		private final Mind mind;
		private int ticks, repathAt;
		private Vec3 centre = Vec3.ZERO;

		SearchGoal(Mind mind) {
			this.mind = mind;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return this.mind.idle() && this.mind.remembers() && dark(this.mind.spider);
		}

		@Override
		public boolean canContinueToUse() {
			return this.mind.idle() && dark(this.mind.spider) && this.ticks < SEARCH_TICKS;
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.repathAt = 0;
			Vec3 seen = this.mind.lastSeen;
			this.centre = seen == null ? this.mind.spider.position() : seen.add(this.mind.lastHeading.scale(SEARCH_LEAD_TICKS));
			// what it remembered is spent on this search
			this.mind.forget();
			this.mind.spider.getNavigation().moveTo(this.centre.x, this.centre.y, this.centre.z, 1.0);
		}

		@Override
		public void stop() {
			this.mind.spider.getNavigation().stop();
		}

		@Override
		public void tick() {
			this.ticks++;
			Spider spider = this.mind.spider;
			// whoever turns up in sight meanwhile
			Player seen = spider.level().getNearestPlayer(spider, PACK_RANGE);
			if (prey(seen) && SEE_SEARCHING.test(getServerLevel(spider), spider, seen)) {
				spider.setTarget(seen);
				return;
			}
			if (spider.getNavigation().isDone() && this.ticks >= this.repathAt) {
				double x = this.centre.x + (spider.getRandom().nextDouble() * 2.0 - 1.0) * SEARCH_RADIUS;
				double z = this.centre.z + (spider.getRandom().nextDouble() * 2.0 - 1.0) * SEARCH_RADIUS;
				this.repathAt = this.ticks + (spider.getNavigation().moveTo(x, this.centre.y, z, 0.9) ? 5 : 20);
			}
		}
	}

	/** Yuka's wander: a point on a circle ahead, nudged a little each time, so idle spiders meander in curves. */
	private static final class WanderGoal extends Goal {
		private final Mind mind;
		private Vec3 to = Vec3.ZERO;

		WanderGoal(Mind mind) {
			this.mind = mind;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			Spider spider = this.mind.spider;
			if (!this.mind.idle() || spider.isInWater() || spider.getRandom().nextFloat() >= WANDER_CHANCE) {
				return false;
			}
			this.to = this.pick();
			return true;
		}

		@Override
		public void start() {
			this.mind.spider.getNavigation().moveTo(this.to.x, this.to.y, this.to.z, 0.8);
		}

		@Override
		public boolean canContinueToUse() {
			return this.mind.idle() && !this.mind.spider.getNavigation().isDone();
		}

		private Vec3 pick() {
			Spider spider = this.mind.spider;
			Vec3 jitter = new Vec3((spider.getRandom().nextDouble() * 2.0 - 1.0) * WANDER_JITTER, 0.0,
				(spider.getRandom().nextDouble() * 2.0 - 1.0) * WANDER_JITTER);
			Vec3 onCircle = this.mind.wander.add(jitter);
			onCircle = onCircle.lengthSqr() < 1.0E-4 ? new Vec3(WANDER_RADIUS, 0.0, 0.0) : onCircle.normalize().scale(WANDER_RADIUS);
			this.mind.wander = onCircle;
			// the circle sits WANDER_DISTANCE ahead of the spider's heading
			double yaw = Math.toRadians(spider.getYRot());
			Vec3 ahead = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).scale(WANDER_DISTANCE);
			return spider.position().add(ahead).add(onCircle);
		}
	}

	/** In the dark it climbs to a ceiling, hangs there, and drops on whoever walks under it. */
	private static final class CeilingGoal extends Goal {
		private final Mind mind;
		private @Nullable BlockPos spot;
		private int ticks, hangFor, repathAt;
		private boolean hanging;

		CeilingGoal(Mind mind) {
			this.mind = mind;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			Spider spider = this.mind.spider;
			return this.mind.idle() && this.mind.now() >= this.mind.ceilingAt && dark(spider) && !spider.isInWater()
				&& SpiderKinds.of(spider) != SpiderKinds.Kind.SWAMP && spider.getRandom().nextFloat() < CEILING_CHANCE && this.find();
		}

		@Override
		public boolean canContinueToUse() {
			BlockPos spot = this.spot;
			if (!this.mind.idle() || !dark(this.mind.spider) || spot == null) {
				return false;
			}
			// knocked or fallen off its ceiling: done
			return this.hanging ? this.ticks < this.hangFor && this.mind.spider.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) < 4.0
				: this.ticks < CEILING_TRAVEL;
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.repathAt = 0;
			this.hanging = false;
			this.hangFor = HANG_TICKS + this.mind.spider.getRandom().nextInt(HANG_TICKS + 1);
			BlockPos spot = this.spot;
			if (spot != null) {
				this.mind.spider.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
			}
		}

		@Override
		public void stop() {
			this.mind.ceilingAt = this.mind.now() + CEILING_REST;
			this.spot = null;
			this.hanging = false;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.ticks++;
			Spider spider = this.mind.spider;
			BlockPos spot = this.spot;
			if (spot == null) {
				return;
			}
			if (!this.hanging) {
				if (spider.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) < 1.0) {
					this.hanging = true;
					this.ticks = 0;
				} else if (spider.getNavigation().isDone() && this.ticks >= this.repathAt) {
					if (!spider.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0)) {
						// no way up after all
						this.ticks = CEILING_TRAVEL;
					}
					this.repathAt = this.ticks + 20;
				}
				return;
			}
			spider.getNavigation().stop();
			if (this.ticks % 5 == 0) {
				this.watch();
			}
		}

		/** Someone right under it: it lets go and drops on them. */
		private void watch() {
			Spider spider = this.mind.spider;
			Player prey = spider.level().getNearestPlayer(spider.getX(), spider.getY() - DROP_BELOW * 0.5, spider.getZ(), DROP_BELOW,
				p -> p instanceof Player player && prey(player));
			if (prey == null) {
				return;
			}
			double below = spider.getY() - prey.getY();
			double dx = prey.getX() - spider.getX(), dz = prey.getZ() - spider.getZ();
			if (below < 1.0 || below > DROP_BELOW || dx * dx + dz * dz > DROP_SIDEWAYS * DROP_SIDEWAYS
				|| !SEE_BELOW.test(getServerLevel(spider), spider, prey)) {
				return;
			}
			spider.setTarget(prey);
			this.mind.saw(prey);
			// off the ceiling (out of reach of its grip) and down at them
			spider.setPos(spider.getX(), spider.getY() - 0.25, spider.getZ());
			spider.setDeltaMovement(dx * 0.15, -DROP_SPEED, dz * 0.15);
			spider.needsSync = true;
			if (spider.level() instanceof ServerLevel level) {
				level.playSound(null, spider.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.6F);
			}
		}

		/** A ceiling overhead with open air under it (room for prey to walk), the air just under it to hang in. */
		private boolean find() {
			Spider spider = this.mind.spider;
			Level level = spider.level();
			BlockPos from = spider.blockPosition();
			for (int tries = 0; tries < 6; tries++) {
				BlockPos column = tries == 0 ? from : from.offset(spider.getRandom().nextInt(7) - 3, 0, spider.getRandom().nextInt(7) - 3);
				for (int up = CEILING_CLEAR; up <= CEILING_UP; up++) {
					BlockPos above = column.above(up);
					BlockState state = level.getBlockState(above);
					if (state.isAir()) {
						continue;
					}
					if (state.getCollisionShape(level, above).isEmpty() || !state.isFaceSturdy(level, above, Direction.DOWN)) {
						break;
					}
					BlockPos hang = above.below();
					if (!this.clearUnder(level, hang)) {
						break;
					}
					if (spider.getNavigation().createPath(hang, 0) == null) {
						break;
					}
					this.spot = hang;
					return true;
				}
			}
			return false;
		}

		private boolean clearUnder(Level level, BlockPos hang) {
			for (int i = 0; i < CEILING_CLEAR; i++) {
				BlockPos at = hang.below(i);
				if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
					return false;
				}
			}
			return true;
		}
	}
}
