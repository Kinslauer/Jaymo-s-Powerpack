package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The bombardier beetle of the warm lands (savanna, jungle, badlands, desert): the crystal beetle's body repainted in
 * rust and blue-black, with its crystals taken off ({@code bbmodels/bombardier_beetle}). Kept to the three rules for
 * living things:
 * <ul>
 * <li><b>Sympathy.</b> It does what the real one does: it keeps to itself, and when a threat comes close (a player who
 * isn't sneaking, or anything that hurt it) it turns its tail and sprays a boiling burst ({@link #spray}). The spray is
 * a reserve of {@link #MAX_CHARGE}: each burst empties some of it, and it takes {@link #REFILL_TICKS} to make more, so a
 * beetle that has fired its last gives up and walks off.</li>
 * <li><b>Feedback.</b> Its glands refill as it rests ({@link #glands}); a colony's workers, pupae and queen
 * ({@link BeetleQueen}) are the same beetle at other stages of its life, and the colony eats the tree it lives in.</li>
 * <li><b>Stewardship.</b> Someone who creeps up (sneaking never counts as a threat) can milk the spray into a glass bottle
 * ({@link #mobInteract}) without harm: a bombardier gland, the fuel of the {@link GlandFlamer}. A beetle left alone costs
 * nobody anything.</li>
 * </ul>
 * A pupa (a colony's young, {@link #pupa}) is the same entity small and still, and hatches into a worker.
 */
public class BombardierBeetle extends ActingMonster {
	static final double HEALTH = 14.0, ARMOR = 4.0, SPEED = 0.22;
	/** Only what comes this close is a threat (blocks): the beetle's following range, so it never hunts. */
	static final double THREAT_RANGE = 3.5;
	/** The spray: its reach (blocks), its width (the least dot product with its facing), damage, seconds alight, and the move's length and landing tick. */
	static final double SPRAY_RANGE = 4.5, SPRAY_WIDTH = 0.6;
	static final float SPRAY_DAMAGE = 3.0F;
	static final int SPRAY_ALIGHT = 3, SPRAY_TICKS = 20, SPRAY_LANDS = 10;
	/** The reserve: how many bursts it holds, ticks to make one more, and ticks between bursts. */
	static final int MAX_CHARGE = 3, REFILL_TICKS = 300, SPRAY_GAP = 40;
	/** A bottle's milking: glands a beetle holds, and ticks to grow one. */
	static final int MAX_GLANDS = 2, GLAND_TICKS = 2400;
	/** A pupa's size (the scale attribute) and how long it takes to hatch (ticks). */
	static final double PUPA_SCALE = 0.4;
	static final int PUPA_TICKS = 2400;
	/** The steam of its spray (colour). */
	static final int STEAM = 0xE8D8C0;

	private static final String[] ACTIONS = {"attack"};

	/** Bursts in reserve, ticks to the next, and ticks to the next burst it may fire. */
	private int charge = MAX_CHARGE, refill, gap;
	/** Glands ready for a bottle, and ticks to the next one. */
	private int glands = 1, glandGrow = GLAND_TICKS;
	/** Ticks left as a pupa (0: grown). */
	private int pupaLeft;
	/** Of a colony: kept near its queen and in the world, until she is gone. */
	private boolean colony;
	/** Ticks with no queen about. */
	private int queenless;

	public BombardierBeetle(EntityType<? extends BombardierBeetle> type, Level level) {
		super(type, level);
		this.xpReward = 1;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ATTACK_DAMAGE, SPRAY_DAMAGE).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, THREAT_RANGE);
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
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> !target.isCrouching() && !this.pupa() && this.charge > 0));
	}

	public boolean pupa() {
		return this.pupaLeft > 0;
	}

	/** Makes it a pupa for {@code ticks}: small and still until it hatches. */
	public void setPupa(int ticks) {
		this.pupaLeft = ticks;
		this.getAttribute(Attributes.SCALE).setBaseValue(PUPA_SCALE);
	}

	public boolean colony() {
		return this.colony;
	}

	/** Makes it one of the colony kept by the queen whose home is {@code home}. */
	public void joinColony(BlockPos home, int radius) {
		this.colony = true;
		this.setPersistenceRequired();
		this.setHomeTo(home, radius);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.pupa()) {
			this.getNavigation().stop();
			this.setTarget(null);
			if (--this.pupaLeft == 0) {
				this.getAttribute(Attributes.SCALE).setBaseValue(1.0);
				level.playSound(null, this.blockPosition(), SoundEvents.TURTLE_EGG_HATCH, SoundSource.NEUTRAL, 0.6F, 1.4F);
			}
			return;
		}
		this.rest();
		if (this.colony && !(this instanceof BeetleQueen) && this.tickCount % 100 == 0) {
			this.checkQueen(level);
		}
		LivingEntity target = this.getTarget();
		if (this.acting("attack")) {
			if (this.actionRun() == SPRAY_LANDS && target != null) {
				this.spray(level, target);
			}
			return;
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		if (this.charge <= 0) {
			// nothing left to spray: it gives up and goes
			this.setTarget(null);
			return;
		}
		this.getLookControl().setLookAt(target);
		if (this.distanceTo(target) > this.sprayRange() - 1.0) {
			this.getNavigation().moveTo(target, 1.2);
		} else if (this.gap <= 0 && this.hasLineOfSight(target)) {
			this.getNavigation().stop();
			this.act("attack", SPRAY_TICKS);
		}
	}

	/** Its reserve and its glands come back with time. */
	private void rest() {
		if (this.gap > 0) {
			this.gap--;
		}
		if (this.charge < MAX_CHARGE && ++this.refill >= REFILL_TICKS) {
			this.refill = 0;
			this.charge++;
		}
		if (this.glands < MAX_GLANDS && --this.glandGrow <= 0) {
			this.glandGrow = GLAND_TICKS;
			this.glands++;
		}
	}

	/** A worker with no queen about for a while scatters: it leaves its home and lives wild, free to wander off. */
	private void checkQueen(ServerLevel level) {
		if (!level.getEntitiesOfClass(BeetleQueen.class, this.getBoundingBox().inflate(BeetleQueen.RANGE), q -> q.isAlive()).isEmpty()) {
			this.queenless = 0;
			return;
		}
		if ((this.queenless += 100) >= BeetleQueen.SCATTER_TICKS) {
			this.scatter();
		}
	}

	/** Leaves the colony: it is no longer held to a place or kept in the world. */
	void scatter() {
		this.colony = false;
		this.queenless = 0;
		this.clearHome();
		this.setTarget(null);
		Vec3 away = Vec3.directionFromRotation(0.0F, this.getRandom().nextFloat() * 360.0F);
		this.setDeltaMovement(away.x * 0.4, 0.3, away.z * 0.4);
	}

	protected double sprayRange() {
		return SPRAY_RANGE;
	}

	protected float sprayDamage() {
		return SPRAY_DAMAGE;
	}

	/** Spends a burst of the reserve on everything in front of it within reach that would fight it. */
	private void spray(ServerLevel level, LivingEntity target) {
		this.charge--;
		this.gap = SPRAY_GAP;
		this.refill = 0;
		Vec3 from = this.position().add(0.0, this.getBbHeight() * 0.5, 0.0);
		Vec3 aim = target.getBoundingBox().getCenter().subtract(from).normalize();
		double range = this.sprayRange();
		Fx.wave(level, from, aim, range, 1.6F, STEAM, 10);
		Fx.burst(level, from.x + aim.x * 1.5, from.y + aim.y * 1.5, from.z + aim.z * 1.5, STEAM, 1.2F, 18, 0.6, 0.4, 0.6);
		level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_BURN, SoundSource.HOSTILE, 0.9F, 1.6F);
		level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.4F, 1.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range), e -> e != this && e.isAlive() && this.strikes(e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(from);
			double distance = to.length();
			if (distance > range + e.getBbWidth() / 2.0 || distance < 0.01 || to.scale(1.0 / distance).dot(aim) < SPRAY_WIDTH) {
				continue;
			}
			if (e.hurtServer(level, this.damageSources().mobAttack(this), this.sprayDamage())) {
				e.setRemainingFireTicks(SPRAY_ALIGHT * 20);
			}
		}
	}

	/** A glass bottle, held out to a beetle that isn't alarmed, catches a gland of its spray. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.GLASS_BOTTLE) || this.pupa() || this.glands <= 0 || this.getTarget() != null) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			this.glands--;
			this.glandGrow = GLAND_TICKS;
			if (!player.isCreative()) {
				stack.shrink(1);
			}
			this.spawnAtLocation(level, new ItemStack(LabyrinthCraft.BEETLE_GLAND));
			level.playSound(null, this.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 0.8F, 1.2F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		// (its spray is its move)
		return false;
	}

	@Override
	public boolean isPushable() {
		return !this.pupa() && super.isPushable();
	}

	@Override
	public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.colony;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("charge", this.charge);
		output.putInt("glands", this.glands);
		output.putInt("pupa", this.pupaLeft);
		output.putBoolean("colony", this.colony);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.charge = input.getIntOr("charge", MAX_CHARGE);
		this.glands = input.getIntOr("glands", 1);
		this.colony = input.getBooleanOr("colony", false);
		int pupa = input.getIntOr("pupa", 0);
		if (pupa > 0) {
			this.setPupa(pupa);
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.pupa() ? null : SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.5F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SILVERFISH_STEP, 0.15F, 1.0F);
	}
}
