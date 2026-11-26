package com.jaymoskingdoms.inhabitants;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

/**
 * The beaver, ported from Eager Beavers by sqwigly (github.com/bodhiahn/eager-beavers, CC0-1.0, kingdoms/art/beaver-LICENSE.txt): its model, texture,
 * animations and call are the mod's own, and its ways are the mod's, rewritten for 26.3. A beaver lives by rivers. It
 * gnaws through the second log of a tree that doesn't stand by water, takes the log in its teeth and the rest of the trunk
 * falls over sideways; it gnaws fallen logs loose too; then it carries the log to still water against a bank and lays it
 * there, so its dams grow log by log. It swims and dives, chews sticks it finds, plants saplings it picks up, breeds on
 * logs, and a stick offered to it tames it one time in three (a tame beaver sits when told, and follows). Felling needs
 * the mobGriefing rule.
 */
public class Beaver extends TamableAnimal implements GeoEntity {
	static final double HEALTH = 20.0, SPEED = 0.4, KNOCKBACK_RESIST = 1.0, WORK_PACE = 0.6;
	/** How long it gnaws at a log before it comes away (ticks). */
	static final int GNAW_TIME = 30;
	/** How far it looks for trees and for dam spots (blocks), and how high or low. */
	static final int TREE_LOOK = 16, TREE_RISE = 3, DAM_LOOK = 30, DAM_RISE = 1, FALLEN_LOOK = 10;
	/** One in this many sticks tames it. */
	static final int TAME_ODDS = 3;
	/** How often a diving swimmer goes under (one in this many ticks), and for how long (ticks, plus up to as many again). */
	static final int DIVE_ODDS = 80, DIVE_TIME = 40;
	public static final int SPAWN_WEIGHT = 8, SPAWN_MIN = 1, SPAWN_MAX = 4;

	static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.beaver.walk");
	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.beaver.idle");
	static final RawAnimation HOLD_WALK = RawAnimation.begin().thenLoop("animation.beaver.holdwalk");
	static final RawAnimation HOLD_IDLE = RawAnimation.begin().thenLoop("animation.beaver.holdidle");
	static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.beaver.swim");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int dive;

	public Beaver(EntityType<? extends Beaver> type, Level level) {
		super(type, level);
		this.setCanPickUpLoot(true);
		this.setPathfindingMalus(PathType.WATER, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESIST);
	}

	/** It spawns on the ground or in the shallows of a river biome (the biome tag picks where). */
	public static boolean checkSpawn(EntityType<Beaver> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return EntitySpawnReason.isSpawner(reason) || level.getBlockState(pos.below()).isSolid() || level.getFluidState(pos).is(FluidTags.WATER);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new Swim());
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.0));
		this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(2, new Dam());
		this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new GnawFallen());
		this.goalSelector.addGoal(4, new Fell());
		this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
		this.goalSelector.addGoal(7, new TemptGoal(this, 0.5, this::isFood, false));
		this.goalSelector.addGoal(8, new FollowParentGoal(this, 0.7));
		this.goalSelector.addGoal(9, new RandomStrollGoal(this, 0.5));
		this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));
		this.goalSelector.addGoal(15, new RandomSwimmingGoal(this, 1.0, 40));
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(ItemTags.LOGS);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		Beaver young = Inhabitants.BEAVER.create(level, EntitySpawnReason.BREEDING);
		if (young != null && this.isTame() && this.getOwner() instanceof Player owner) {
			young.tame(owner);
		}
		return young;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!this.isTame() && stack.is(Items.STICK)) {
			if (this.level().isClientSide()) {
				return InteractionResult.CONSUME;
			}
			this.usePlayerItem(player, hand, stack);
			if (this.random.nextInt(TAME_ODDS) == 0) {
				this.tame(player);
				this.navigation.stop();
				this.setTarget(null);
				this.setOrderedToSit(true);
				this.level().broadcastEntityEvent(this, (byte) 7);
			} else {
				this.level().broadcastEntityEvent(this, (byte) 6);
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		InteractionResult result = super.mobInteract(player, hand);
		if (!result.consumesAction() && this.isTame() && this.isOwnedBy(player) && !this.level().isClientSide()) {
			this.setOrderedToSit(!this.isOrderedToSit());
			this.navigation.stop();
			return InteractionResult.SUCCESS_SERVER;
		}
		return result;
	}

	/** It picks up sticks (to chew), saplings (to plant) and logs (for its dam), one at a time in its teeth. */
	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return this.held().isEmpty() && (stack.is(Items.STICK) || stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.LOGS));
	}

	@Override
	protected void pickUpItem(ServerLevel level, ItemEntity entity) {
		ItemStack stack = entity.getItem();
		if (!this.wantsToPickUp(level, stack)) {
			return;
		}
		this.onItemPickup(entity);
		this.setItemSlot(EquipmentSlot.MAINHAND, stack.split(1));
		this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		this.take(entity, 1);
		if (stack.isEmpty()) {
			entity.discard();
		}
	}

	private ItemStack held() {
		return this.getItemBySlot(EquipmentSlot.MAINHAND);
	}

	/** The log it carries, as it will lie in the dam (on its side), or null. */
	private @Nullable BlockState carried() {
		ItemStack stack = this.held();
		if (!stack.is(ItemTags.LOGS) || !(stack.getItem() instanceof BlockItem item)) {
			return null;
		}
		return sideways(item.getBlock().defaultBlockState());
	}

	private static BlockState sideways(BlockState log) {
		return log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X) : log;
	}

	@Override
	public void aiStep() {
		if (this.level() instanceof ServerLevel level && this.isAlive()) {
			ItemStack stack = this.held();
			if (stack.is(Items.STICK)) {
				stack.shrink(1);
				this.playSound(SoundEvents.GENERIC_EAT.value(), 0.7F, 2.5F);
			} else if (stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem item && this.onGround()) {
				BlockPos at = this.blockPosition();
				BlockState sapling = item.getBlock().defaultBlockState();
				if (level.getBlockState(at).isAir() && sapling.canSurvive(level, at) && level.setBlock(at, sapling, 3)) {
					stack.shrink(1);
				}
			}
		}
		super.aiStep();
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Beaver>("move", 3, test -> {
			Beaver b = test.animatable();
			if (b.isInWater()) {
				return test.setAndContinue(SWIM);
			}
			boolean holding = !b.held().isEmpty();
			return test.setAndContinue(test.isMoving() ? (holding ? HOLD_WALK : WALK) : (holding ? HOLD_IDLE : IDLE));
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return Inhabitants.BEAVER_AMBIENT.value();
	}

	@Override
	protected float getSoundVolume() {
		return this.isBaby() ? 0.12F : 0.07F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.PIG_STEP.value(), 0.15F, 1.5F);
	}

	private static boolean log(BlockState state) {
		return state.is(BlockTags.LOGS);
	}

	private static boolean byWater(LevelReader level, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** Chips flying from the log it gnaws, and the gnawing sound. */
	private void gnaw(ServerLevel level, BlockPos log) {
		this.getLookControl().setLookAt(log.getX() + 0.5, log.getY() + 0.5, log.getZ() + 0.5);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(log)), log.getX() + 0.5, log.getY() + 0.5,
			log.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.1);
		this.playSound(SoundEvents.GENERIC_EAT.value(), 0.4F, 1.5F);
	}

	/** It takes the log at {@code pos} in its teeth. */
	private void take(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(state.getBlock()));
		this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		this.playSound(SoundEvents.WOOD_BREAK, 1.0F, 1.0F);
		level.destroyBlock(pos, false);
	}

	/** In water it swims and now and then dives. */
	class Swim extends net.minecraft.world.entity.ai.goal.Goal {
		Swim() {
			this.setFlags(java.util.EnumSet.of(Flag.JUMP, Flag.MOVE));
			Beaver.this.getNavigation().setCanFloat(true);
		}

		@Override
		public boolean canUse() {
			return Beaver.this.isInWater() && Beaver.this.getFluidHeight(FluidTags.WATER) > (Beaver.this.isBaby() ? 0.1 : 0.2);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			RandomSource random = Beaver.this.getRandom();
			if (Beaver.this.dive == 0 && random.nextInt(DIVE_ODDS) == 0) {
				Beaver.this.dive = DIVE_TIME + random.nextInt(DIVE_TIME);
			}
			if (Beaver.this.dive > 0) {
				var v = Beaver.this.getDeltaMovement();
				Beaver.this.setDeltaMovement(v.x, -0.03, v.z);
				Beaver.this.dive--;
			} else if (random.nextFloat() < 0.8F) {
				Beaver.this.getJumpControl().jump();
			}
			if (Beaver.this.level() instanceof ServerLevel level && random.nextInt(4) == 0) {
				level.sendParticles(ParticleTypes.BUBBLE, Beaver.this.getX(), Beaver.this.getY(), Beaver.this.getZ(), 1, 0.1, 0.1, 0.1, 0.05);
			}
		}
	}

	/** A goal at a block that ends with gnawing it for {@link #GNAW_TIME}. */
	abstract class Gnaw extends MoveToBlockGoal {
		private int timer;

		Gnaw(int range, int rise) {
			super(Beaver.this, WORK_PACE, range, rise);
		}

		@Override
		public boolean canUse() {
			return Beaver.this.held().isEmpty() && !Beaver.this.isOrderedToSit() && !Beaver.this.isBaby() && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return Beaver.this.held().isEmpty() && log(Beaver.this.level().getBlockState(this.blockPos)) && super.canContinueToUse();
		}

		@Override
		public void start() {
			this.timer = 0;
			super.start();
		}

		@Override
		public double acceptedDistance() {
			return 2.5;
		}

		@Override
		public void tick() {
			super.tick();
			if (!this.isReachedTarget() || !(Beaver.this.level() instanceof ServerLevel level)) {
				return;
			}
			if (this.timer++ % 5 == 0) {
				Beaver.this.gnaw(level, this.blockPos);
			}
			if (this.timer >= GNAW_TIME) {
				this.done(level);
				this.timer = 0;
			}
		}

		abstract void done(ServerLevel level);
	}

	/** Gnaws through a tree's second log; the trunk above falls over beside it, on its side. */
	class Fell extends Gnaw {
		Fell() {
			super(TREE_LOOK, TREE_RISE);
		}

		@Override
		public boolean canUse() {
			return Beaver.this.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.MOB_GRIEFING) && super.canUse();
		}

		@Override
		public boolean shouldRecalculatePath() {
			return this.tryTicks % 100 == 0;
		}

		@Override
		protected boolean isValidTarget(LevelReader level, BlockPos pos) {
			return log(level.getBlockState(pos)) && log(level.getBlockState(pos.below())) && !byWater(level, pos);
		}

		@Override
		void done(ServerLevel level) {
			List<BlockPos> above = new ArrayList<>();
			for (BlockPos p = this.blockPos.above(); log(level.getBlockState(p)); p = p.above()) {
				above.add(p);
			}
			Beaver.this.take(level, this.blockPos);
			for (BlockPos p : above) {
				BlockState state = level.getBlockState(p);
				BlockPos to = this.blockPos.offset(p.getY() - this.blockPos.getY() + 1, -1, 0);
				while (!level.getBlockState(to).isAir() && level.getBlockState(to).canOcclude() && to.getY() < level.getMaxY()) {
					to = to.above();
				}
				while ((level.getBlockState(to.below()).isAir() || !level.getBlockState(to.below()).canOcclude()) && to.getY() > level.getMinY()) {
					to = to.below();
				}
				level.removeBlock(p, false);
				level.setBlock(to, sideways(state), 3);
			}
		}
	}

	/** Gnaws a fallen (sideways) log loose and carries it off. */
	class GnawFallen extends Gnaw {
		GnawFallen() {
			super(FALLEN_LOOK, 1);
		}

		@Override
		protected boolean isValidTarget(LevelReader level, BlockPos pos) {
			BlockState state = level.getBlockState(pos);
			return log(state) && state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y
				&& !byWater(level, pos);
		}

		@Override
		void done(ServerLevel level) {
			Beaver.this.take(level, this.blockPos);
		}
	}

	/** Carries the log it holds to still water against a bank, with open air above, and lays it there. */
	class Dam extends MoveToBlockGoal {
		Dam() {
			super(Beaver.this, WORK_PACE, DAM_LOOK, DAM_RISE);
		}

		@Override
		public boolean canUse() {
			return Beaver.this.carried() != null && !Beaver.this.isOrderedToSit() && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return Beaver.this.carried() != null && super.canContinueToUse();
		}

		@Override
		public double acceptedDistance() {
			return 1.5;
		}

		@Override
		protected boolean isValidTarget(LevelReader level, BlockPos pos) {
			if (!level.getFluidState(pos).isSourceOfType(Fluids.WATER) || !level.getBlockState(pos.above()).isAir()) {
				return false;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (level.getBlockState(pos.relative(d)).canOcclude()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public void tick() {
			super.tick();
			BlockState log = Beaver.this.carried();
			if (this.isReachedTarget() && log != null && Beaver.this.level() instanceof ServerLevel level && this.isValidTarget(level, this.blockPos)
				&& level.setBlock(this.blockPos, log, 3)) {
				level.gameEvent(Beaver.this, net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE, this.blockPos);
				Beaver.this.held().shrink(1);
				this.stop();
			}
		}
	}
}
