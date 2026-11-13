package com.jaymoskingdoms.blossomland;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The snow monkey of the cherry hills, a macaque that lives in troops.
 * <ul>
 * <li><b>What it does.</b> It keeps with its troop, runs from wolves and the other hunters, and by day a hungry one
 * goes into the fields and eats a ripe crop down to the ground ({@link #RAID_TICKS} before it wants another). In
 * winter it finds the still, warm water of a settlement (the bath house pool) and sits in it.</li>
 * <li><b>What comes back.</b> A troop by a village costs it part of its harvest for as long as the troop is hungry.</li>
 * <li><b>Looked after.</b> Fruit or roots from the hand feed the whole troop near for a day ({@link #FED_TICKS}), and a
 * fed troop leaves the fields alone and cries out when a monster comes near ({@link #ALARM_RANGE}), as macaques do
 * for a snake or a hawk.</li>
 * </ul>
 */
public class SnowMonkey extends BlossomAnimal {
	static final double HEALTH = 10.0, SPEED = 0.28;
	static final long FED_TICKS = 24000L, RAID_TICKS = 6000L;
	static final double SHARE_RANGE = 10.0, ALARM_RANGE = 12.0;
	static final int ALARM_EVERY = 100, SOAK_TICKS = 1200;
	static final Set<Item> FOOD = Set.of(Items.APPLE, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.MELON_SLICE, Items.CARROT, Items.POTATO,
		Items.BEETROOT, Items.BREAD, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS);

	private int soakUntil;

	public SnowMonkey(EntityType<? extends SnowMonkey> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	static boolean food(ItemStack stack) {
		return FOOD.contains(stack.getItem());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, this.fleePredators(10.0F, 1.3));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.5));
		this.goalSelector.addGoal(2, new Soak());
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, SnowMonkey::food, false));
		this.goalSelector.addGoal(4, new SeekBlock(10, 3, 100, 1.1, 1.6) {
			@Override
			protected boolean wants() {
				return !SnowMonkey.this.fed() && SnowMonkey.this.level().isBrightOutside();
			}

			@Override
			protected boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
				return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
			}

			@Override
			protected void arrive(ServerLevel level, BlockPos pos) {
				// it eats the ripe crop where it stands and leaves the root
				BlockState state = level.getBlockState(pos);
				level.setBlock(pos, ((CropBlock) state.getBlock()).getStateForAge(0), 3);
				level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
				SnowMonkey.this.feed(level, RAID_TICKS);
			}
		});
		this.goalSelector.addGoal(5, new SeekBlock(12, 3, 200, 1.0, 1.2) {
			@Override
			protected boolean wants() {
				return winter(SnowMonkey.this.level()) && SnowMonkey.this.soakUntil < SnowMonkey.this.tickCount && SnowMonkey.this.amongBlossomFolk();
			}

			@Override
			protected boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
				return level.getFluidState(pos).is(FluidTags.WATER) && level.getBlockState(pos.above()).isAir() && level.getFluidState(pos.below()).isEmpty();
			}

			@Override
			protected void arrive(ServerLevel level, BlockPos pos) {
				SnowMonkey.this.soakUntil = SnowMonkey.this.tickCount + SOAK_TICKS;
			}
		});
		this.goalSelector.addGoal(6, new KeepTogether(10.0, 1.1));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.fed() && (this.tickCount + this.getId()) % ALARM_EVERY == 0) {
			Monster seen = level.getNearestEntity(Monster.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().range(ALARM_RANGE),
				this, this.getX(), this.getY(), this.getZ(), this.getBoundingBox().inflate(ALARM_RANGE, 4.0, ALARM_RANGE));
			if (seen != null && this.hasLineOfSight(seen)) {
				this.getLookControl().setLookAt(seen);
				this.playSound(SoundEvents.FOX_SCREECH, 1.4F, 1.7F);
			}
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		InteractionResult fed = this.handFeed(player, hand, SnowMonkey::food, FED_TICKS);
		if (fed == InteractionResult.PASS) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			// what one is given the troop shares
			for (SnowMonkey m : level.getEntitiesOfClass(SnowMonkey.class, this.getBoundingBox().inflate(SHARE_RANGE), m -> m != this)) {
				m.fedUntil = Math.max(m.fedUntil, this.fedUntil);
			}
		}
		return fed;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.FOX_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FOX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FOX_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.5F;
	}

	/** Sitting in the warm water: it does not move while the soak lasts and it is still in the water. */
	private final class Soak extends Goal {
		Soak() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return SnowMonkey.this.tickCount < SnowMonkey.this.soakUntil && SnowMonkey.this.isInWater();
		}

		@Override
		public void start() {
			SnowMonkey.this.getNavigation().stop();
		}
	}
}
