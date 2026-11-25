package com.jaymoskingdoms.monsters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The manta ray and the stingray (after Mo' Creatures' ray, GPL-3, see {@code licenses/mocreatures}), the two of one body.
 * <ul>
 * <li><b>Manta rays</b> glide through the warm and lukewarm seas, slow and harmless. Sympathy: they swim with divers, and
 * a diver near one is drawn along in its wake (Dolphin's Grace, {@link #WAKE_TICKS}); they are never fished out in a
 * bucket. Stewardship: nothing is lost by leaving them be, and the wake is theirs to give to whoever swims by.</li>
 * <li><b>Stingrays</b> lie buried in the sand of the shallows, only their eyes showing. Sympathy: whoever wades onto one
 * is stung (poison and a slowed leg), but a wader who <i>shuffles</i> (sneaks) is not, as the real advice goes. A stung one
 * swims off and buries itself again after a while ({@link #CALM_TICKS}). Feedback: a stung wader learns to shuffle, and a
 * ray that is left in peace stays buried and unseen: the shallows are safe for the sneaking and for those who keep out.
 * Stewardship: nobody is hurt by a ray they step lightly round.</li>
 * </ul>
 */
public class SeaRay extends AbstractFish {
	static final double MANTA_HEALTH = 20.0, STING_HEALTH = 8.0, MANTA_SPEED = 0.5, STING_SPEED = 0.6;
	/** The sting: how far round a buried ray it is felt (blocks), the hurt, the poison and the slowing (ticks). */
	static final double STING_REACH = 1.1;
	static final float STING_HURT = 3.0F;
	static final int POISON_TICKS = 100, SLOW_TICKS = 160;
	/** After stinging a ray leaves its place and stays up this long before it buries again (ticks); how deep the water may be for it to bury itself (blocks). */
	static final int CALM_TICKS = 600, BURY_DEPTH = 4;
	/** How often a buried ray is looked at and tries to bury (ticks); the manta's wake: how often, how long (ticks) and how far (blocks). */
	static final int LOOK_EVERY = 4, BURY_EVERY = 100, WAKE_EVERY = 100, WAKE_TICKS = 200;
	static final double WAKE_RANGE = 5.0;

	private static final EntityDataAccessor<Boolean> BURIED = SynchedEntityData.defineId(SeaRay.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> STINGING = SynchedEntityData.defineId(SeaRay.class, EntityDataSerializers.BOOLEAN);

	public final boolean manta;
	private long calmUntil;

	public SeaRay(EntityType<? extends SeaRay> type, Level level, boolean manta) {
		super(type, level);
		this.manta = manta;
	}

	public static AttributeSupplier.Builder createAttributes(boolean manta) {
		return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, manta ? MANTA_HEALTH : STING_HEALTH)
			.add(Attributes.MOVEMENT_SPEED, manta ? MANTA_SPEED : STING_SPEED);
	}

	/** A stingray: sand or gravel under it in water three or less deep. */
	public static boolean checkStingraySpawn(EntityType<SeaRay> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!level.getFluidState(pos).is(FluidTags.WATER)) {
			return false;
		}
		BlockPos floor = pos.below();
		return (level.getBlockState(floor).is(BlockTags.SAND) || level.getBlockState(floor).is(Blocks.GRAVEL)) && depth(level, pos) <= BURY_DEPTH - 1;
	}

	/** Blocks of water from {@code pos} up to the air. */
	static int depth(LevelReader level, BlockPos pos) {
		int d = 0;
		BlockPos p = pos;
		while (d <= BURY_DEPTH + 1 && level.getFluidState(p).is(FluidTags.WATER)) {
			d++;
			p = p.above();
		}
		return d;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BURIED, false);
		builder.define(STINGING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new PanicGoal(this, 1.5) {
			@Override
			public boolean canUse() {
				return !SeaRay.this.buried() && super.canUse();
			}
		});
		this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 60) {
			@Override
			public boolean canUse() {
				return !SeaRay.this.buried() && super.canUse();
			}
		});
	}

	public boolean buried() {
		return this.entityData.get(BURIED);
	}

	/** Its tail is raised to sting. */
	public boolean stinging() {
		return this.entityData.get(STINGING);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		long now = level.getGameTime();
		if (this.entityData.get(STINGING) && this.tickCount % 20 == 0) {
			this.entityData.set(STINGING, false);
		}
		if (this.manta) {
			if (this.tickCount % WAKE_EVERY == 0) {
				for (Player diver : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(WAKE_RANGE), p -> p.isInWater() && !p.isSpectator())) {
					diver.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, WAKE_TICKS, 0, true, false));
				}
			}
			return;
		}
		if (this.buried()) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
			if (this.tickCount % LOOK_EVERY == 0) {
				for (Player wader : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(STING_REACH, 1.0, STING_REACH),
					p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && !p.isShiftKeyDown() && p.getY() < this.getY() + 1.6)) {
					this.sting(level, wader, now);
					break;
				}
			}
		} else if (this.tickCount % BURY_EVERY == 0 && now >= this.calmUntil && this.random.nextFloat() < 0.5F && this.canBuryHere(level)) {
			this.entityData.set(BURIED, true);
			level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		}
	}

	private boolean canBuryHere(ServerLevel level) {
		BlockPos at = this.blockPosition();
		return this.isInWater() && this.onGround() && (level.getBlockState(at.below()).is(BlockTags.SAND) || level.getBlockState(at.below()).is(Blocks.GRAVEL))
			&& depth(level, at) <= BURY_DEPTH;
	}

	/** The sting: a hurt, poison and a heavy leg; the ray comes up and swims off. */
	private void sting(ServerLevel level, Player wader, long now) {
		wader.hurtServer(level, level.damageSources().mobAttack(this), STING_HURT);
		wader.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
		wader.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1));
		this.entityData.set(BURIED, false);
		this.entityData.set(STINGING, true);
		this.calmUntil = now + CALM_TICKS;
		this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.25, 0.0));
		this.playSound(SoundEvents.PUFFER_FISH_STING, 0.8F, 1.2F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.buried()) {
			this.entityData.set(BURIED, false);
			this.calmUntil = level.getGameTime() + CALM_TICKS / 2;
		}
		return hurt;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.isPersistenceRequired() && !this.hasCustomName();
	}

	@Override
	public ItemStack getBucketItemStack() {
		return new ItemStack(Items.WATER_BUCKET);
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.COD_FLOP;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("buried", this.buried());
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(BURIED, input.getBooleanOr("buried", false));
	}
}
