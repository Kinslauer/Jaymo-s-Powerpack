package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.creatures.Looks;
import com.jaymoskingdoms.seas.Mohknee;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The giant salamander of Mohknee's rivers: a slow, flat, mottled animal as long as a man, the old one of its pool.
 * <ul>
 * <li><b>What it does.</b> It lies on the riverbed and does not move for hours. At night a hungry one takes a fish
 * that swims within reach ({@link #LUNGE} blocks) in one snap, and then wants nothing for a day
 * ({@link #FED_TICKS}). It bites what hurts it and is otherwise no danger to anyone.</li>
 * <li><b>What comes back.</b> It is only ever found where the water is unspoiled: in a river, in nobody's land,
 * with nothing built at the water ({@link #spoiled}).</li>
 * <li><b>Looked after.</b> A river whose salamander has been left in peace ({@link #PEACE_TICKS} unhurt) is a good
 * one: once a day a fish comes up its stretch.</li>
 * </ul>
 */
public class Salamander extends AbstractFish implements Looks {
	static final double HEALTH = 24.0, DAMAGE = 5.0, LUNGE = 4.0;
	static final long FED_TICKS = 24000L, PEACE_TICKS = 24000L;
	static final int SPOIL_REACH = 6;

	private long fedUntil, hurtAt = Long.MIN_VALUE / 2, fishDay = -1L;

	public Salamander(EntityType<? extends Salamander> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.MOVEMENT_SPEED, 0.5)
			.add(Attributes.FOLLOW_RANGE, 8.0);
	}

	/** A river on Mohknee, in nobody's land, with nothing built at the water. */
	public static boolean checkSpawn(EntityType<Salamander> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!Mohknee.on(pos.getX(), pos.getZ()) || !level.getFluidState(pos).is(FluidTags.WATER)) {
			return false;
		}
		ServerLevel server = level.getLevel();
		return Factions.at(server.getServer(), server, pos) == null && !spoiled(level, pos)
			&& server.getEntitiesOfClass(Salamander.class, new net.minecraft.world.phys.AABB(pos).inflate(48.0), s -> true).isEmpty();
	}

	/** Whether anything made stands at the water here: worked wood or stone, a fire, a field. */
	static boolean spoiled(ServerLevelAccessor level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-SPOIL_REACH, -2, -SPOIL_REACH), pos.offset(SPOIL_REACH, 3, SPOIL_REACH))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.PLANKS) || state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.STONE_BRICKS) || state.is(Blocks.COBBLESTONE)
				|| state.is(Blocks.FARMLAND) || state.is(Blocks.TORCH) || state.is(Blocks.FURNACE)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.8, true));
		this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 0.5, 400));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractFish.class, 20, true, false,
			(target, level) -> !(target instanceof Salamander) && this.hungry() && level.isDarkOutside() && target.distanceToSqr(this) <= LUNGE * LUNGE));
	}

	boolean hungry() {
		return this.level().getGameTime() >= this.fedUntil;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.isInWater() && this.getTarget() == null && this.getNavigation().isDone() && !this.onGround()) {
			// it settles to the bed and lies there
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6).add(0.0, -0.02, 0.0));
		}
		long now = level.getGameTime(), day = now / 24000L;
		if (this.tickCount % 200 == 0 && day != this.fishDay && now - this.hurtAt >= PEACE_TICKS && level.isBrightOutside() && this.isInWater()) {
			this.fishDay = day;
			Entity fish = (this.random.nextBoolean() ? net.minecraft.world.entity.EntityTypes.SALMON : net.minecraft.world.entity.EntityTypes.COD).create(level, EntitySpawnReason.NATURAL);
			if (fish != null) {
				fish.snapTo(this.getX() + this.random.nextInt(7) - 3, this.getY() + 0.5, this.getZ() + this.random.nextInt(7) - 3, 0.0F, 0.0F);
				if (level.getFluidState(fish.blockPosition()).is(FluidTags.WATER)) {
					level.addFreshEntity(fish);
				}
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof AbstractFish fish && !fish.isAlive()) {
			this.fedUntil = level.getGameTime() + FED_TICKS;
			this.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 0.6F);
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity) {
			this.hurtAt = level.getGameTime();
		}
		return hurt;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public int look() {
		return 0;
	}

	@Override
	public float fullness() {
		return 0.0F;
	}

	@Override
	public ItemStack getBucketItemStack() {
		return new ItemStack(Items.WATER_BUCKET);
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.AXOLOTL_SPLASH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("fed_until", this.fedUntil);
		output.putLong("fish_day", this.fishDay);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.fedUntil = input.getLongOr("fed_until", 0L);
		this.fishDay = input.getLongOr("fish_day", -1L);
	}
}
