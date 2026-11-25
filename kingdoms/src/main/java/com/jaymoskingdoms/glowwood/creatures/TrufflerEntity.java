package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * A mossy boar. It sniffs the ground and now and then digs up a Truffle; it smells Truffle Soil from 8 blocks and
 * roots it out (two truffles, the soil turns to coarse dirt). Breeds with Truffles.
 */
public class TrufflerEntity extends Animal {
	public static final byte EVENT_SNIFF = 106;
	public static final byte EVENT_DIG = 107;

	public final AnimationState sniffState = new AnimationState();
	public final AnimationState digState = new AnimationState();

	public TrufflerEntity(EntityType<? extends TrufflerEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	/** Natural spawns: on dirt-like ground (grass, podzol, mycelium, moss) in daylight. */
	public static boolean checkSpawnRules(EntityType<TrufflerEntity> type, LevelAccessor level, EntitySpawnReason spawnType,
										  BlockPos pos, RandomSource random) {
		return level.getBlockState(pos.below()).is(BlockTags.DIRT)
				&& (EntitySpawnReason.ignoresLightRequirements(spawnType) || isBrightEnoughToSpawn(level, pos));
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, this::isFood, false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new RootOutGoal());
		this.goalSelector.addGoal(6, new SniffGoal());
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(GlowFoods.truffle());
	}

	@Nullable
	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return GlowCreatures.TRUFFLER.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_SNIFF) {
			sniffState.start(this.tickCount);
		} else if (id == EVENT_DIG) {
			digState.start(this.tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	private void dirtBurst(BlockPos at, BlockState state) {
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 1.0,
					at.getZ() + 0.5, 18, 0.3, 0.1, 0.3, 0.1);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return GlowSounds.TRUFFLER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return GlowSounds.TRUFFLER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return GlowSounds.TRUFFLER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(net.minecraft.sounds.SoundEvents.PIG_STEP.value(), 0.15F, 0.9F);
	}

	/** Stops, sniffs, and sometimes digs a truffle out of the ground it stands on. */
	private class SniffGoal extends Goal {
		private int cooldown = 200;
		private int ticks;
		private boolean digging;

		SniffGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			TrufflerEntity self = TrufflerEntity.this;
			if (--cooldown > 0 || !self.onGround() || self.isBaby()) {
				return false;
			}
			cooldown = 300 + self.random.nextInt(500);
			return true;
		}

		@Override
		public void start() {
			TrufflerEntity self = TrufflerEntity.this;
			ticks = 0;
			digging = false;
			self.getNavigation().stop();
			self.level().broadcastEntityEvent(self, EVENT_SNIFF);
			self.playSound(GlowSounds.TRUFFLER_SNIFF, 0.8F, 0.9F + self.random.nextFloat() * 0.2F);
		}

		@Override
		public boolean canContinueToUse() {
			return ticks < (digging ? 60 : 28);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			TrufflerEntity self = TrufflerEntity.this;
			ticks++;
			self.getNavigation().stop();
			BlockPos ground = self.blockPosition().below();
			BlockState state = self.level().getBlockState(ground);
			if (ticks == 28 && !digging && state.is(BlockTags.DIRT)
					&& self.random.nextDouble() < GlowCreatures.TRUFFLER_DIG_CHANCE) {
				digging = true;
				self.level().broadcastEntityEvent(self, EVENT_DIG);
				self.playSound(GlowSounds.TRUFFLER_DIG, 0.9F, 1.0F);
			}
			if (digging && (ticks == 40 || ticks == 50)) {
				dirtBurst(ground, state);
			}
			if (digging && ticks == 56) {
				if (self.level() instanceof ServerLevel level) {
					self.spawnAtLocation(level, new ItemStack(GlowFoods.truffle()), 0.3F);
				}
				self.playSound(GlowSounds.TRUFFLER_DIG, 0.9F, 1.2F);
			}
		}
	}

	/** Smells Truffle Soil within 8 blocks (up to 4 below the surface), walks over and roots it out. */
	private class RootOutGoal extends Goal {
		private int cooldown = 400;
		@Nullable
		private BlockPos soil;
		private int ticks;
		private int digTicks;

		RootOutGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			TrufflerEntity self = TrufflerEntity.this;
			if (--cooldown > 0 || self.isBaby()) {
				return false;
			}
			cooldown = 400 + self.random.nextInt(400);
			BlockPos origin = self.blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -5, -8), origin.offset(8, 1, 8))) {
				if (self.level().getBlockState(pos).is(GlowFoods.truffleSoil())) {
					soil = pos.immutable();
					return true;
				}
			}
			return false;
		}

		@Override
		public void start() {
			ticks = 0;
			digTicks = 0;
			BlockPos above = surfaceAbove(soil);
			TrufflerEntity.this.getNavigation().moveTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, 1.0);
		}

		private BlockPos surfaceAbove(BlockPos pos) {
			return TrufflerEntity.this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
		}

		@Override
		public boolean canContinueToUse() {
			return soil != null && ticks < 300 && TrufflerEntity.this.level().getBlockState(soil).is(GlowFoods.truffleSoil());
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			// Running goals are ticked every tick but only checked (canContinueToUse) every other tick, so this can
			// run once more after the dig cleared the soil: that crashed the server ("this.soil" is null).
			if (soil == null) {
				return;
			}
			TrufflerEntity self = TrufflerEntity.this;
			ticks++;
			double dx = soil.getX() + 0.5 - self.getX();
			double dz = soil.getZ() + 0.5 - self.getZ();
			if (dx * dx + dz * dz > 2.25) {
				if (self.getNavigation().isDone()) {
					BlockPos above = surfaceAbove(soil);
					self.getNavigation().moveTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, 1.0);
				}
				return;
			}
			self.getNavigation().stop();
			self.getLookControl().setLookAt(soil.getX() + 0.5, soil.getY() + 1.0, soil.getZ() + 0.5);
			if (digTicks == 0) {
				self.level().broadcastEntityEvent(self, EVENT_DIG);
				self.playSound(GlowSounds.TRUFFLER_DIG, 0.9F, 1.0F);
			}
			digTicks++;
			if (digTicks % 10 == 0) {
				dirtBurst(self.blockPosition().below(), self.level().getBlockState(self.blockPosition().below()));
			}
			if (digTicks >= 32) {
				self.level().setBlockAndUpdate(soil, Blocks.COARSE_DIRT.defaultBlockState());
				if (self.level() instanceof ServerLevel level) {
					self.spawnAtLocation(level, new ItemStack(GlowFoods.truffle(), 2), 0.3F);
				}
				self.playSound(GlowSounds.TRUFFLER_DIG, 1.0F, 1.2F);
				soil = null;
			}
		}

		@Override
		public void stop() {
			soil = null;
		}
	}
}
