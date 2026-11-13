package com.jaymoskingdoms.inhabitants;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Impaler: a spiked hunter of the deep caves, ported from Inhabitants (JeremySeq / Team Synapse, MIT; the model,
 * texture and animations are theirs, by Catalyst: kingdoms/art/impaler-CREDITS.md). The behaviour is written fresh for
 * Kingdoms after Inhabitants' own. Andy (2026-11-12): "impaler should lurk in caves at night".
 *
 * <p>It comes up into the caves only after dark, and lurks: it stands still in the black, turning its head, until it
 * sees something to eat (it sees a long way). Then it runs, very fast, and bites, or throws the spikes off its arm at
 * anything out of reach; the spikes grow back slowly, so it has only so many. Hurt, it screams, and the stalactites over
 * the cave shake loose. By day one with nothing to hunt goes back down into the rock.
 *
 * <p>A fed one stops hunting: once it has killed, it leaves everything alone for a while (unless it is hit), so a cave
 * that feeds its Impaler gets quieter, not busier. Its spikes concuss what they hit ({@link ImpalerGear}); dead, it gives
 * up the ones still on its arm, so one that has thrown them all leaves none.
 *
 * <p>Its coat follows its rock, as Inhabitants' four do (Andy 2026-11-13: "sure"): dark slate in the dripstone caves,
 * rust-brown under the badlands (Inhabitants' Forlorn Hollows coat; that biome isn't in the pack), the plain tan
 * elsewhere, and now and then, anywhere, an albino.
 */
public class Impaler extends Monster implements GeoEntity {
	static final double HEALTH = 40.0, SPEED = 0.24, ARMOR = 4.0, FOLLOW = 48.0, KNOCKBACK_RESISTANCE = 0.4;
	/** Its run at a foe, as a speed modifier: very fast. */
	static final double RUN = 2.0;
	/** It gives up a foe this far off (blocks). */
	static final double FORGET_RANGE = 56.0;
	/** The bite: in this reach (blocks), landing this many ticks into the 1.25 s animation, again after this long (ticks). */
	static final double BITE_REACH = 2.8;
	static final int BITE_HIT_TICK = 13, BITE_TICKS = 25, BITE_COOLDOWN = 30;
	static final float BITE_DAMAGE = 8.0F;
	/**
	 * The spike throw: at a foe between these ranges it can see (blocks), it stands and throws; the spikes leave its arm
	 * this many ticks into the 3 s animation, this many at a time, at this speed and spread. It carries this many throws'
	 * worth, and one grows back every this many ticks.
	 */
	static final double THROW_MIN = 6.0, THROW_MAX = 24.0;
	static final int THROW_RELEASE_TICK = 27, THROW_TICKS = 60, THROW_COOLDOWN = 80, VOLLEY = 3, SPIKES = 3, SPIKE_REGROW = 1200;
	static final float SPIKE_SPEED = 1.5F, SPIKE_SPREAD = 6.0F;
	/**
	 * The scream: when it is hurt, at most this often (ticks); the stalactites over the cave this far round it (blocks
	 * sideways, and up) fall this many ticks in, as the animation's own trigger has it.
	 */
	static final int SCREAM_COOLDOWN = 400, SCREAM_TICKS = 50, SCREAM_DROP_TICK = 14, SCREAM_RADIUS = 10, SCREAM_UP = 12;
	/** Sated after a kill: this long (ticks) it hunts nothing that hasn't hit it. */
	static final int SATED_TICKS = 6000;
	/** By day, with no foe and no one within this (blocks), it goes back down into the rock. */
	static final double GO_DOWN_ALONE = 24.0;
	/** Spawning: only below this many blocks under sea level, out of the sky, at night, and never within this of another. */
	static final int SPAWN_BELOW_SEA = 20;
	static final double SPAWN_APART = 40.0;
	public static final int SPAWN_WEIGHT = 6;
	/** One in this many is born albino, wherever it is. */
	static final int ALBINO_ONE_IN = 25;
	/** What it leaves, dead: this many spikes for each throw's worth still on its arm (up to, at random). */
	static final int SPIKES_DROPPED_EACH = 2;

	/** Its coats (textures/entity/impaler/{@code name}.png, each with a glowing {@code _glowmask}). */
	public enum Coat {
		DEFAULT("default"), DRIPSTONE("dripstone"), FORLORN("forlorn_hollows"), ALBINO("albino");

		public final String texture;

		Coat(String texture) {
			this.texture = texture;
		}
	}

	enum Act { NONE, BITING, THROWING, SCREAMING }

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
	static final RawAnimation BITE = RawAnimation.begin().thenPlay("bite");
	static final RawAnimation THROW = RawAnimation.begin().thenPlay("spike throw");
	static final RawAnimation SCREAM = RawAnimation.begin().thenPlay("scream");
	static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");

	private static final EntityDataAccessor<Integer> SPIKES_LEFT = SynchedEntityData.defineId(Impaler.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> COAT = SynchedEntityData.defineId(Impaler.class, EntityDataSerializers.INT);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** (Server) what it is in the middle of, and for how many more ticks; ticks until it may bite, throw or scream again. */
	private Act act = Act.NONE;
	private int actLeft, biteCooldown, throwCooldown, screamCooldown;
	/** (Server) ticks until its next spike grows back, and until it is hungry again. */
	private int regrow, sated;

	public Impaler(EntityType<? extends Impaler> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ARMOR, ARMOR).add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE).add(Attributes.STEP_HEIGHT, 1.0);
	}

	/** Its natural spawn: deep in a dark cave, out of the sky, while it's night above, and none other near. */
	public static boolean checkSpawn(EntityType<Impaler> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		if (!(level instanceof ServerLevel server) || pos.getY() > level.getSeaLevel() - SPAWN_BELOW_SEA || server.canSeeSky(pos)
			|| !server.isDarkOutside() || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) {
			return false;
		}
		return server.getEntitiesOfClass(Impaler.class, new AABB(pos).inflate(SPAWN_APART), Entity::isAlive).isEmpty();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(SPIKES_LEFT, SPIKES);
		entityData.define(COAT, Coat.DEFAULT.ordinal());
	}

	public Coat coat() {
		Coat[] coats = Coat.values();
		return coats[Mth.clamp(this.entityData.get(COAT), 0, coats.length - 1)];
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		this.entityData.set(COAT, coatFor(level, this.blockPosition(), this.random).ordinal());
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	/** The coat of one born here: its rock's colour, or rarely an albino. */
	static Coat coatFor(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		if (random.nextInt(ALBINO_ONE_IN) == 0) {
			return Coat.ALBINO;
		}
		Holder<Biome> biome = level.getBiome(pos);
		if (biome.is(Biomes.DRIPSTONE_CAVES)) {
			return Coat.DRIPSTONE;
		}
		return biome.is(BiomeTags.IS_BADLANDS) ? Coat.FORLORN : Coat.DEFAULT;
	}

	/** How many throws' worth of spikes it has on its arm. */
	public int spikes() {
		return this.entityData.get(SPIKES_LEFT);
	}

	boolean busy() {
		return this.act != Act.NONE;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new HuntGoal());
		// no wander: it lurks where it is, looking about
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !Impaler.this.busy() && super.canUse();
			}
		});
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
			@Override
			public boolean canUse() {
				return Impaler.this.sated <= 0 && super.canUse();
			}
		});
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true) {
			@Override
			public boolean canUse() {
				return Impaler.this.sated <= 0 && super.canUse();
			}
		});
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.biteCooldown > 0) {
			this.biteCooldown--;
		}
		if (this.throwCooldown > 0) {
			this.throwCooldown--;
		}
		if (this.screamCooldown > 0) {
			this.screamCooldown--;
		}
		if (this.sated > 0) {
			this.sated--;
		}
		if (this.spikes() < SPIKES && --this.regrow <= 0) {
			this.entityData.set(SPIKES_LEFT, this.spikes() + 1);
			this.regrow = SPIKE_REGROW;
		}
		this.forgetFoe();
		if (this.actLeft > 0) {
			this.acting(level);
		}
		if (this.tickCount % 40 == 0 && this.getTarget() == null && !this.busy() && level.isBrightOutside() && !this.isPersistenceRequired()
			&& level.getNearestPlayer(this, GO_DOWN_ALONE) == null) {
			this.goDown(level);
		}
	}

	/** It gives up a foe that is gone, far off, or no fair game (creative, spectating). */
	private void forgetFoe() {
		LivingEntity foe = this.getTarget();
		if (foe != null && (!foe.isAlive() || this.distanceTo(foe) > FORGET_RANGE || foe instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(null);
		}
	}

	private void start(Act act, int ticks, String animation) {
		this.act = act;
		this.actLeft = ticks;
		this.getNavigation().stop();
		this.setSprinting(false);
		this.triggerAnim("action", animation);
	}

	private void acting(ServerLevel level) {
		this.getNavigation().stop();
		LivingEntity foe = this.getTarget();
		if (foe != null) {
			this.getLookControl().setLookAt(foe, 60.0F, 60.0F);
		}
		int into = switch (this.act) {
			case BITING -> BITE_TICKS;
			case THROWING -> THROW_TICKS;
			case SCREAMING -> SCREAM_TICKS;
			case NONE -> 0;
		} - this.actLeft;
		if (this.act == Act.BITING && into == BITE_HIT_TICK && foe != null && this.distanceTo(foe) <= BITE_REACH + 0.5) {
			this.doHurtTarget(level, foe);
		} else if (this.act == Act.THROWING && into == THROW_RELEASE_TICK && foe != null) {
			this.volley(level, foe);
		} else if (this.act == Act.SCREAMING && into == SCREAM_DROP_TICK) {
			this.shakeLoose(level);
		}
		if (--this.actLeft <= 0) {
			this.act = Act.NONE;
		}
	}

	/** Its spikes, off the arm at its foe. */
	private void volley(ServerLevel level, LivingEntity foe) {
		if (this.spikes() <= 0) {
			return;
		}
		this.entityData.set(SPIKES_LEFT, this.spikes() - 1);
		if (this.regrow <= 0) {
			this.regrow = SPIKE_REGROW;
		}
		Vec3 from = this.getEyePosition();
		for (int i = 0; i < VOLLEY; i++) {
			ImpalerSpike spike = new ImpalerSpike(level, this);
			spike.setPos(from.x, from.y - 0.4, from.z);
			double dx = foe.getX() - from.x, dz = foe.getZ() - from.z;
			double dy = foe.getY(0.5) - (from.y - 0.4) + Math.sqrt(dx * dx + dz * dz) * 0.08;
			spike.shoot(dx, dy, dz, SPIKE_SPEED, SPIKE_SPREAD);
			level.addFreshEntity(spike);
		}
		this.playSound(SoundEvents.POINTED_DRIPSTONE_BREAK, 1.0F, 0.8F);
	}

	/** Its scream shakes the stalactites over the cave loose: each one hanging near it falls. */
	private void shakeLoose(ServerLevel level) {
		BlockPos at = this.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-SCREAM_RADIUS, 0, -SCREAM_RADIUS), at.offset(SCREAM_RADIUS, SCREAM_UP, SCREAM_RADIUS))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof SpeleothemBlock && state.getValue(SpeleothemBlock.TIP_DIRECTION) == Direction.DOWN
				&& !(level.getBlockState(pos.above()).getBlock() instanceof SpeleothemBlock)) {
				// its root, so the whole column comes down (the block's own tick drops it, as a broken ceiling does)
				level.scheduleTick(pos.immutable(), state.getBlock(), 2 + this.random.nextInt(8));
			}
		}
	}

	/** Back into the rock it came up out of. */
	private void goDown(ServerLevel level) {
		BlockState under = level.getBlockState(this.blockPosition().below());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), this.getX(), this.getY() + 0.3, this.getZ(), 30, 0.5, 0.3, 0.5, 0.1);
		this.playSound(SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 0.6F);
		this.discard();
	}

	@Override
	public void awardKillScore(Entity killed, DamageSource source) {
		super.awardKillScore(killed, source);
		this.fed();
	}

	/** It has eaten: it lets everything be for a while. */
	private void fed() {
		this.sated = SATED_TICKS;
		this.setTarget(null);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive()) {
			this.sated = 0;
			if (this.screamCooldown <= 0 && this.act != Act.THROWING) {
				this.screamCooldown = SCREAM_COOLDOWN;
				this.start(Act.SCREAMING, SCREAM_TICKS, "scream");
				this.playSound(ImpalerGear.IMPALER_SCREAM, 2.5F, 1.0F);
			} else if (!this.busy()) {
				this.triggerAnim("hurt", "hurt");
			}
		}
		return hurt;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Impaler>("move", 4, test -> {
			if (!test.isMoving()) {
				return test.setAndContinue(IDLE);
			}
			return test.setAndContinue(test.animatable().isSprinting() ? RUN_ANIM : WALK);
		}));
		controllers.add(new AnimationController<Impaler>("action", test -> PlayState.STOP).triggerableAnim("bite", BITE)
			.triggerableAnim("spike throw", THROW).triggerableAnim("scream", SCREAM));
		controllers.add(new AnimationController<Impaler>("hurt", test -> PlayState.STOP).triggerableAnim("hurt", HURT));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return ImpalerGear.IMPALER_IDLE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ImpalerGear.IMPALER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ImpalerGear.IMPALER_DEATH;
	}

	/** The spikes still on its arm. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		int count = 0;
		for (int i = 0; i < this.spikes(); i++) {
			count += 1 + this.random.nextInt(SPIKES_DROPPED_EACH);
		}
		if (count > 0) {
			this.spawnAtLocation(level, new ItemStack(ImpalerGear.IMPALER_SPIKE, count));
		}
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SPIDER_STEP, 0.4F, 0.6F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("spikes", this.spikes());
		output.putInt("sated", this.sated);
		output.putString("coat", this.coat().texture);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(SPIKES_LEFT, Mth.clamp(input.getIntOr("spikes", SPIKES), 0, SPIKES));
		this.sated = input.getIntOr("sated", 0);
		String coat = input.getStringOr("coat", Coat.DEFAULT.texture);
		for (Coat c : Coat.values()) {
			if (c.texture.equals(coat)) {
				this.entityData.set(COAT, c.ordinal());
			}
		}
	}

	/** It runs its foe down and bites; at a foe it can see but can't reach, it throws its spikes. */
	final class HuntGoal extends Goal {
		HuntGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return Impaler.this.getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			Impaler.this.setSprinting(false);
			Impaler.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			Impaler self = Impaler.this;
			LivingEntity foe = self.getTarget();
			if (foe == null || self.busy()) {
				return;
			}
			self.getLookControl().setLookAt(foe, 60.0F, 60.0F);
			double d = self.distanceTo(foe);
			if (d <= BITE_REACH) {
				self.getNavigation().stop();
				self.setSprinting(false);
				if (self.biteCooldown <= 0) {
					self.biteCooldown = BITE_COOLDOWN;
					self.start(Act.BITING, BITE_TICKS, "bite");
					self.playSound(ImpalerGear.IMPALER_ATTACK, 1.2F, 1.0F);
				}
				return;
			}
			boolean stuck = self.getNavigation().isDone() || self.getNavigation().isStuck();
			if (d >= THROW_MIN && d <= THROW_MAX && self.spikes() > 0 && self.throwCooldown <= 0 && self.hasLineOfSight(foe)
				&& (stuck || self.random.nextInt(60) == 0)) {
				self.throwCooldown = THROW_COOLDOWN;
				self.start(Act.THROWING, THROW_TICKS, "spike throw");
				self.playSound(ImpalerGear.IMPALER_SPIKES, 1.2F, 1.0F);
				return;
			}
			self.setSprinting(true);
			if (self.tickCount % 5 == 0 || self.getNavigation().isDone()) {
				self.getNavigation().moveTo(foe, RUN);
			}
		}
	}
}
