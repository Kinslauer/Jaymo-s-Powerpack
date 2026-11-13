package com.jaymoskingdoms;

import com.jaymoskingdoms.beasts.ActingMonster;
import com.jaymoskingdoms.beasts.Beasts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A goblin scout, drawn from the goblin_scout Blockbench model Andy gave us (hood, pack, dagger and buckler; its own
 * idle, walk, attack and cackle): the eyes of a goblin village. Each village keeps a few ranging about outside its ring
 * ({@link #hire}, when it rises and as it grows). A scout sees further than the village's goblins do; when it first
 * spots one of the village's prey ({@link Goblins#prey}, so a player passing for a goblin walks by) it stops and
 * cackles, and every goblin of its village in earshot comes for what it saw. Then it fights for itself: quick, a
 * dagger's jab, and a buckler that turns aside half the arrows shot at its front. With its village gone it runs wild
 * and goes for any player, with nobody to call.
 */
public class GoblinScout extends ActingMonster {
	static final double HEALTH = 14.0, ARMOR = 2.0, DAMAGE = 3.0, SPEED = 0.34;
	/** How far it spots prey (the village's goblins see {@link Goblins#SIGHT}). */
	static final double SIGHT = 32.0;
	/** How many a new village sends out, the most it keeps, how far from its fire they range, and how far it counts them. */
	static final int START = 2, MAX = 3, HOME_RADIUS = 40;
	static final double COUNT_RANGE = 72.0;
	/** The chance a village short of scouts sends one out, each day it grows. */
	static final float HIRE_CHANCE = 0.3F;
	/** Its cackle: how long it stands laughing (the animation's length), how far it carries, and how long before it calls again. */
	static final int CACKLE_TICKS = 28, CACKLE_AGAIN = 600;
	static final double CACKLE_RANGE = 40.0;
	/** Its jab: the animation's length, the tick the dagger lands, its reach, and the ticks between jabs. */
	static final int JAB_TICKS = 14, JAB_LANDS = 6, JAB_GAP = 22;
	static final double JAB_REACH = 1.4;
	/** The share of arrows from its front its buckler turns, and how far to the front counts (the dot of its facing and the way to the shot). */
	static final float BLOCK_CHANCE = 0.5F;
	static final double BLOCK_DOT = 0.3;

	private static final String[] ACTIONS = {"attack", "cackle"};

	/** The goblin village it scouts for; -1 for none. */
	private int camp = -1;
	private int jabGap;
	/** The game time before which it won't cackle again. */
	private long quietUntil;

	public GoblinScout(EntityType<? extends GoblinScout> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, SIGHT);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, (target, level) -> this.prey(target)));
	}

	/** On its side: goblins, the village's barrel and sellswords, other scouts. */
	private boolean ally(LivingEntity e) {
		return e instanceof GoblinScout || this.camp >= 0 && (Goblins.isGoblin(e) || e instanceof WarBarrel b && !b.friendly() || e instanceof ScalebaneArcher);
	}

	/** What it goes for on sight: its village's prey, or (wild) any player. */
	private boolean prey(LivingEntity target) {
		if (this.ally(target)) {
			return false;
		}
		if (this.camp < 0) {
			return target instanceof Player player && !player.isCreative() && !player.isSpectator();
		}
		return Goblins.prey(this.camp, target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && this.ally(target) ? null : target);
	}

	@Override
	protected boolean strikes(LivingEntity e) {
		return !this.ally(e) && (e == this.getTarget() || e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == this);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.camp >= 0 && this.tickCount % 100 == 0) {
			Camps.CampData data = Camps.data(level.getServer(), this.camp);
			if (Factions.get(level.getServer(), this.camp) == null || data == null || data.clearedAt() >= 0) {
				// its village is gone: nobody to call, nowhere to keep to
				this.camp = -1;
				this.clearHome();
			} else if (!this.hasHome()) {
				this.setHomeTo(data.center(), HOME_RADIUS);
			}
		}
		if (this.jabGap > 0) {
			this.jabGap--;
		}
		LivingEntity target = this.getTarget();
		if (this.acting("cackle")) {
			this.getNavigation().stop();
			if (target != null) {
				this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
			return;
		}
		if (this.acting("attack")) {
			if (this.actionRun() == JAB_LANDS) {
				this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), JAB_REACH, true, 0.3, 0.0);
			}
			return;
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		if (level.getGameTime() >= this.quietUntil && this.hasLineOfSight(target)) {
			this.cackle(level, target);
			return;
		}
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		this.getNavigation().moveTo(target, 1.2);
		if (this.jabGap <= 0 && this.distanceTo(target) < JAB_REACH + 0.6) {
			this.jabGap = JAB_GAP;
			this.act("attack", JAB_TICKS);
			this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.6F);
		}
	}

	/** It has seen {@code target}: it laughs, and the goblins of its village in earshot come for what it saw. */
	private void cackle(ServerLevel level, LivingEntity target) {
		this.quietUntil = level.getGameTime() + CACKLE_AGAIN;
		this.act("cackle", CACKLE_TICKS);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 2.5F, 1.7F);
		if (this.camp < 0) {
			return;
		}
		int camp = this.camp;
		for (Worker w : Workers.near(level, this.getBoundingBox().inflate(CACKLE_RANGE),
			x -> x.factionId() == camp && Goblins.isGoblin(x.role()) && x.mob().isAlive() && !Downed.isDowned(x.mob()))) {
			if (Rulers.target(w.mob()) == null) {
				Rulers.charge(w.mob(), target);
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// (its blows are its jabs)
		return false;
	}

	/** Its buckler turns aside some of what is shot at its front. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Vec3 from = source.getSourcePosition();
		if (from != null && source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.BYPASSES_SHIELD)
			&& this.getRandom().nextFloat() < BLOCK_CHANCE) {
			Vec3 to = from.subtract(this.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 0.01 && to.normalize().dot(Vec3.directionFromRotation(0.0F, this.yBodyRot)) > BLOCK_DOT) {
				this.playSound(SoundEvents.SHIELD_BLOCK.value(), 0.8F, 1.4F);
				return false;
			}
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("camp", this.camp);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.camp = input.getIntOr("camp", -1);
	}

	@Override
	public float getVoicePitch() {
		return 1.7F;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		// (a lookout keeps quiet until it has something to laugh at)
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITCH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITCH_DEATH;
	}

	// ---- sending them out ------------------------------------------------------------------------------------------------

	/**
	 * Goblin village {@code camp} (fire at {@code fire}) sends out scouts: {@code count} of them, never past {@link #MAX}
	 * about it. Only while the fire's ground is loaded.
	 */
	static void hire(ServerLevel level, int camp, BlockPos fire, int count) {
		if (Beasts.GOBLIN_SCOUT == null || !level.isLoaded(fire)) {
			return;
		}
		int near = level.getEntitiesOfClass(GoblinScout.class, new AABB(fire).inflate(COUNT_RANGE), s -> s.camp == camp).size();
		RandomSource random = level.getRandom();
		for (int i = 0; i < count && near < MAX; i++, near++) {
			GoblinScout scout = Beasts.GOBLIN_SCOUT.create(level, EntitySpawnReason.EVENT);
			if (scout == null) {
				return;
			}
			BlockPos at = GoblinVillages.spot(level, fire, random);
			scout.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			scout.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			scout.camp = camp;
			scout.setHomeTo(fire, HOME_RADIUS);
			scout.setPersistenceRequired();
			level.addFreshEntity(scout);
		}
	}

	/** A day's growth: a village short of scouts now and then sends another out. */
	static void grow(ServerLevel level, int camp, BlockPos fire, int days) {
		MinecraftServer server = level.getServer();
		Camps.CampData data = Camps.data(server, camp);
		if (Factions.get(server, camp) != null && data != null && data.clearedAt() < 0 && level.getRandom().nextFloat() < 1.0F - Math.pow(1.0F - HIRE_CHANCE, days)) {
			hire(level, camp, fire, 1);
		}
	}
}
