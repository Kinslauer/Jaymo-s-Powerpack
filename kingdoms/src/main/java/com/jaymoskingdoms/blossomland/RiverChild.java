package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.creatures.Beast;
import com.jaymoskingdoms.seas.Mohknee;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The River Child: a small, slick thing that lives in the rivers by the blossom villages. It goes for whoever swims in
 * its water and pulls them under ({@link #DRAG}); it leaves people on the bank alone unless they hurt it. By night it
 * comes up into the paddies and takes a ripe plant. Food left in its water it takes as a gift: whoever threw it is
 * left alone, swimming or not, for {@link #GIFT_DAYS} days, and it dips its head to them. The blossom folk leave it
 * something at the water's edge and say no more about it.
 */
public class RiverChild extends Beast {
	/** The pose it bows in (the model's head-down pose). */
	static final int BOWING = 1;
	static final double HEALTH = 16.0, SPEED = 0.26, DAMAGE = 3.0;
	/** How hard it pulls a swimmer down each tick it has hold of them, and from how near. */
	static final double DRAG = 0.09, HOLD = 2.5;
	static final int GIFT_DAYS = 3, BOW_TICKS = 40, STEAL_EVERY = 400, STEAL_RANGE = 6;
	static final double GIFT_REACH = 3.0;

	private final Map<UUID, Long> gifts = new HashMap<>();
	private int bowLeft;

	public RiverChild(EntityType<? extends RiverChild> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.FOLLOW_RANGE, 16.0).add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0).add(Attributes.OXYGEN_BONUS, 100.0);
	}

	/** At night, in the water of a river or a cherry grove, on blossom land. */
	public static boolean checkSpawn(EntityType<RiverChild> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return level.getDifficulty() != Difficulty.PEACEFUL && level.getLevel().isDarkOutside() && level.getFluidState(pos).is(FluidTags.WATER)
			&& (level.getBiome(pos).is(Biomes.CHERRY_GROVE) || Mohknee.on(pos.getX(), pos.getZ()))
			&& level.getLevel().getEntitiesOfClass(RiverChild.class, new net.minecraft.world.phys.AABB(pos).inflate(48.0), c -> true).isEmpty();
	}

	/** Whether it has had a gift from {@code who} lately. */
	boolean gifted(LivingEntity who) {
		Long until = this.gifts.get(who.getUUID());
		return until != null && this.level().getGameTime() < until;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
		this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(target, level) -> target.isInWater() && !this.gifted(target)));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (target != null && target != this.getLastHurtByMob() && (this.gifted(target) || !target.isInWater())) {
			// they are out of its water, or they have paid
			this.setTarget(null);
			target = null;
		}
		if (target != null && target.isInWater() && this.distanceToSqr(target) <= HOLD * HOLD) {
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, -DRAG, 0.0));
			target.syncVelocity = true;
		}
		if (this.bowLeft > 0 && --this.bowLeft == 0) {
			this.setLook(0);
		}
		if (this.tickCount % 20 == 0) {
			this.takeGifts(level);
		}
		if (target == null && level.isDarkOutside() && (this.tickCount + this.getId()) % STEAL_EVERY == 0) {
			this.steal(level);
		}
	}

	private void takeGifts(ServerLevel level) {
		List<ItemEntity> left = level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(GIFT_REACH), i -> i.isAlive() && i.getItem().has(DataComponents.FOOD));
		for (ItemEntity gift : left) {
			if (gift.getOwner() instanceof Player giver) {
				this.gifts.put(giver.getUUID(), level.getGameTime() + GIFT_DAYS * 24000L);
				this.getLookControl().setLookAt(giver);
			}
			ItemStack stack = gift.getItem();
			stack.shrink(1);
			if (stack.isEmpty()) {
				gift.discard();
			} else {
				gift.setItem(stack);
			}
			this.bowLeft = BOW_TICKS;
			this.setLook(BOWING);
			this.playSound(SoundEvents.FROG_EAT, 1.0F, 0.8F);
			level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.5, this.getZ(), 8, 0.3, 0.2, 0.3, 0.0);
			return;
		}
	}

	/** One ripe plant from a field by the water, taken where it stands if it is in reach, walked to otherwise. */
	private void steal(ServerLevel level) {
		BlockPos at = this.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-STEAL_RANGE, -1, -STEAL_RANGE), at.offset(STEAL_RANGE, 2, STEAL_RANGE))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
				if (p.closerToCenterThan(this.position(), 2.0)) {
					level.setBlock(p, crop.getStateForAge(0), 3);
					level.levelEvent(2001, p, net.minecraft.world.level.block.Block.getId(state));
				} else {
					this.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.0);
				}
				return;
			}
		}
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.FROG_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FROG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FROG_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		StringBuilder kept = new StringBuilder();
		this.gifts.forEach((who, until) -> kept.append(who).append('=').append(until).append(';'));
		output.putString("gifts", kept.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.gifts.clear();
		for (String pair : input.getStringOr("gifts", "").split(";")) {
			int cut = pair.indexOf('=');
			if (cut > 0) {
				try {
					this.gifts.put(UUID.fromString(pair.substring(0, cut)), Long.parseLong(pair.substring(cut + 1)));
				} catch (IllegalArgumentException ignored) {
					// a line we cannot read is a gift forgotten
				}
			}
		}
	}
}
