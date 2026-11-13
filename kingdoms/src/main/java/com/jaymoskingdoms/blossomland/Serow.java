package com.jaymoskingdoms.blossomland;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The serow: a dark, shaggy goat-antelope of the steep cherry slopes, always alone.
 * <ul>
 * <li><b>What it does.</b> It keeps to one hillside all its life ({@link #HOME_RANGE} blocks round where it was
 * first seen). When it sees someone at a distance it does not run: it stands quite still and stares
 * ({@link #STARE_FROM} to {@link #STARE_TO} blocks), and only if they come closer does it go, straight up the
 * slope, where a fall means nothing to it.</li>
 * <li><b>What comes back.</b> It browses the grass and fern of its hillside short ({@link #BROWSE_EVERY}), so its
 * home ground is the open, close-cropped one.</li>
 * <li><b>Looked after.</b> Left unhunted it is on the same slope every day, so anyone who keeps their distance can
 * come back and find it. Nothing is lost by passing it by.</li>
 * </ul>
 */
public class Serow extends BlossomAnimal {
	static final double HEALTH = 16.0, SPEED = 0.24;
	static final int HOME_RANGE = 24, BROWSE_EVERY = 1800, GRAZE_TICKS = 40;
	static final double STARE_FROM = 7.0, STARE_TO = 18.0;
	static final int STARE_TICKS = 200;

	private int browseIn;
	private boolean homed;

	public Serow(EntityType<? extends Serow> type, Level level) {
		super(type, level);
		this.browseIn = this.random.nextInt(BROWSE_EVERY);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.STEP_HEIGHT, 1.1)
			.add(Attributes.SAFE_FALL_DISTANCE, 16.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, this.fleePredators(14.0F, 1.6));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.9));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, (float) STARE_FROM, 1.5, 1.9));
		this.goalSelector.addGoal(3, new Stare());
		this.goalSelector.addGoal(4, new SeekBlock(8, 3, 100, 0.9, 1.6) {
			@Override
			protected boolean wants() {
				return Serow.this.browseIn == 0;
			}

			@Override
			protected boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
				return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN);
			}

			@Override
			protected void arrive(ServerLevel level, BlockPos pos) {
				level.destroyBlock(pos, false, Serow.this);
				Serow.this.headDown(GRAZE_TICKS);
				Serow.this.browseIn = BROWSE_EVERY;
			}
		});
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.browseIn > 0) {
			this.browseIn--;
		}
		if (!this.homed && this.onGround()) {
			this.homed = true;
			this.setHomeTo(this.blockPosition(), HOME_RANGE);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GOAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GOAT_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.8F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.homed) {
			output.putLong("home", this.getHomePosition().asLong());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		long home = input.getLongOr("home", Long.MIN_VALUE);
		if (home != Long.MIN_VALUE) {
			this.homed = true;
			this.setHomeTo(BlockPos.of(home), HOME_RANGE);
		}
	}

	/** Stock still, head up, eyes on whoever it has seen, for as long as they keep their distance. */
	private final class Stare extends Goal {
		private Player seen;
		private int left;

		Stare() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Serow me = Serow.this;
			if ((me.tickCount + me.getId()) % 20 != 0) {
				return false;
			}
			Player near = me.level().getNearestPlayer(me, STARE_TO);
			if (near == null || near.isSpectator() || me.distanceToSqr(near) < STARE_FROM * STARE_FROM || !me.hasLineOfSight(near)) {
				return false;
			}
			this.seen = near;
			return true;
		}

		@Override
		public void start() {
			this.left = STARE_TICKS;
			Serow.this.getNavigation().stop();
		}

		@Override
		public boolean canContinueToUse() {
			return this.left > 0 && this.seen.isAlive() && Serow.this.distanceToSqr(this.seen) < STARE_TO * STARE_TO;
		}

		@Override
		public void tick() {
			this.left--;
			Serow.this.getLookControl().setLookAt(this.seen, 30.0F, 30.0F);
		}
	}
}
