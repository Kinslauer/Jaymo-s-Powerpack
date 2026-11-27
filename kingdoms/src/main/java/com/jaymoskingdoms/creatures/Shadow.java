package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.ShadowDrain;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A shadow of the ruins: a cold, thin dark shape that lives where it is dim and withers in the light (a bright place, or the
 * day, hurts it). Its touch drains Might ({@link ShadowDrain}); a player drained to nothing goes down, never to death, and
 * their own shadow peels away from them and joins the others (it carries the Might it took, and gives it back when slain).
 * It slips through gaps a man cannot, and plain blows do half as much to it. It has no body to fall: it never goes down
 * like a person (Powers' body/simple tag), and at its end it breaks up into smoke and leaves nothing to carry or butcher
 * (Kitchen's no_corpse tag).
 * <p>
 * Andy (2026-11-26): "shadows should be transparent, like our ghost texture, they should also be varying sizes and some
 * should have the wispy tail instead of legs. they should also disappear or go invisible between attacks. and when they
 * are hit by sun they should instantly dissipate instead of taking damage". So: each one is drawn in the ghost's see-through
 * skin, dark (ShadowRenderer); it is born a size of its own ({@link #SMALLEST} to {@link #BIGGEST}, its scale) and
 * {@link #WISPY_SHARE} of them trail the ghost's wispy tail instead of legs ({@link #wispy}). It strikes and is gone: it
 * shows itself only once it is near its prey ({@link #RETURN_RANGE}), gathers for {@link #GATHER} ticks before it can touch,
 * lingers {@link #LINGER} ticks after a touch (or {@link #SHOWN_MAX} if it never lands one), then thins away to nothing for
 * {@link #GONE_MIN} to {@link #GONE_MAX} ticks, unseen, untouchable and walking through bodies, while it closes in again. Out
 * in the sun it is unmade at once ({@link #sunlit}), with no harm taken and nothing left; under cover a bright lamp still
 * withers it as before.
 */
public class Shadow extends Beast {
	static final double HEALTH = 14.0, SPEED = 0.3, DAMAGE = 1.0;
	/** Light (0-15) from which it withers, the harm each second, and the share of a plain blow it takes. */
	static final int BRIGHT = 10;
	static final float WITHERS = 1.5F, PLAIN_SHARE = 0.6F;
	/** Its size, smallest to biggest (as a scale); the share of them with a wispy tail instead of legs. */
	static final float SMALLEST = 0.6F, BIGGEST = 1.5F, WISPY_SHARE = 0.4F;
	/** Sky light (0-15) at its head that counts as standing in the sun, by day and with no rain (a thin canopy lets it through). */
	static final int SUN_SKY = 13;
	/** How near its prey it shows itself again (blocks); ticks it gathers before it can touch, lingers after, and shows at most. */
	static final double RETURN_RANGE = 4.0;
	static final int GATHER = 10, LINGER = 16, SHOWN_MAX = 80;
	/** Ticks it stays gone between strikes, least and most. */
	static final int GONE_MIN = 30, GONE_MAX = 70;

	private static final EntityDataAccessor<Boolean> WISPY = SynchedEntityData.defineId(Shadow.class, EntityDataSerializers.BOOLEAN);

	private @Nullable UUID owner;
	private int carries;
	/** Server: the tick it may touch from (after gathering), when it goes again, and how long it has been gone for. */
	private int touchFrom, goesAt = -1, goneFor;
	/** Client: how much of it shows (0 gone to 1), now and last tick. */
	private float shown = 1.0F, shownO = 1.0F;

	public Shadow(EntityType<? extends Shadow> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	/** The shadow of {@code player}, peeled away from them carrying {@code points} of their Might. */
	public static @Nullable Shadow peeledFrom(ServerLevel level, ServerPlayer player, int points) {
		Shadow s = Creatures.SHADOW.create(level, EntitySpawnReason.TRIGGERED);
		if (s == null) {
			return null;
		}
		s.snapTo(player.getX() + 1.0, player.getY(), player.getZ(), player.getYRot(), 0.0F);
		s.owner = player.getUUID();
		s.carries = points;
		s.setPersistenceRequired();
		s.setCustomName(net.minecraft.network.chat.Component.translatable("entity.jaymoskingdoms.shadow.own", player.getName()));
		return s;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(WISPY, false);
	}

	/** Whether it trails a wispy tail instead of legs. */
	public boolean wispy() {
		return this.entityData.get(WISPY);
	}

	/** How much of it shows this frame (0 gone to 1), eased in and out on the client. */
	public float shown(float partialTicks) {
		return Mth.lerp(partialTicks, this.shownO, this.shown);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
		RandomSource random = level.getRandom();
		// most near a man's size, a few child-small or hulking
		float size = SMALLEST + (BIGGEST - SMALLEST) * (random.nextFloat() + random.nextFloat()) / 2.0F;
		this.getAttribute(Attributes.SCALE).setBaseValue(size);
		this.entityData.set(WISPY, random.nextFloat() < WISPY_SHARE);
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> level.getDifficulty() != Difficulty.PEACEFUL
			&& !target.getUUID().equals(this.owner)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// gone, or still gathering itself, it cannot touch
		if (this.isInvisible() || this.tickCount < this.touchFrom) {
			return false;
		}
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity who) {
			ShadowDrain.touch(level, who, this);
		}
		if (hit) {
			// struck: it lingers a moment, then is gone again
			this.goesAt = Math.min(this.goesAt, this.tickCount + LINGER);
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// gone, there is nothing to strike (only what strikes at everything reaches it)
		if (this.isInvisible() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return super.hurtServer(level, source, source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) ? damage : damage * PLAIN_SHARE);
	}

	@Override
	public boolean isPickable() {
		return !this.isInvisible() && super.isPickable();
	}

	@Override
	public boolean isPushable() {
		return !this.isInvisible() && super.isPushable();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			// it thins away and gathers again over a few ticks, not all at once
			this.shownO = this.shown;
			this.shown = Mth.approach(this.shown, this.isInvisible() ? 0.0F : 1.0F, this.isInvisible() ? 0.12F : 0.2F);
		}
		// one left lying downed from before shadows stopped going down (or laid out some other way) is unmade at once
		if (this.level() instanceof ServerLevel level && this.isAlive() && this.tickCount % 10 == 0 && com.jaymoskingdoms.Downed.isDowned(this)) {
			this.kill(level);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.sunlit(level)) {
			this.dissipate(level);
			return;
		}
		if (this.tickCount % 20 == 0 && !this.isInvisible() && level.getMaxLocalRawBrightness(this.blockPosition()) >= BRIGHT) {
			this.hurtServer(level, level.damageSources().magic(), WITHERS);
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.6), this.getZ(), 6, 0.2, 0.4, 0.2, 0.02);
		}
		this.strikeAndGo(level);
	}

	/** Out under the day's sky: by day, no rain, and the sky's light on its head. */
	private boolean sunlit(ServerLevel level) {
		if (!level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, this.position()) || level.isRaining()) {
			return false;
		}
		return level.getBrightness(LightLayer.SKY, BlockPos.containing(this.getX(), this.getEyeY(), this.getZ())) >= SUN_SKY;
	}

	/** The sun has it: it comes apart into smoke at once and is gone, giving back any Might it carried. */
	private void dissipate(ServerLevel level) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.5), this.getZ(), 20, 0.25 * this.getScale(), 0.6 * this.getScale(), 0.25 * this.getScale(), 0.03);
		level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.5), this.getZ(), 30, 0.3 * this.getScale(), 0.7 * this.getScale(), 0.3 * this.getScale(), 0.05);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.7F, 0.6F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 1.4F);
		if (this.owner != null) {
			ShadowDrain.returned(level, this.owner, this.carries);
			this.carries = 0;
		}
		this.discard();
	}

	/**
	 * Between strikes it is gone: unseen and untouchable it closes on its prey, shows itself only once near, gathers, strikes,
	 * lingers a moment, and is gone again. With no prey it walks shown.
	 */
	private void strikeAndGo(ServerLevel level) {
		LivingEntity prey = this.getTarget();
		if (this.isInvisible()) {
			this.goneFor--;
			if (this.goneFor <= 0 && (prey == null || this.distanceToSqr(prey) <= RETURN_RANGE * RETURN_RANGE)) {
				this.setInvisible(false);
				this.touchFrom = this.tickCount + GATHER;
				this.goesAt = this.tickCount + SHOWN_MAX;
				level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.5), this.getZ(), 10, 0.3, 0.6, 0.3, -0.02);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.8F, 0.5F);
			}
			return;
		}
		if (prey == null) {
			this.goesAt = -1;
			return;
		}
		if (this.goesAt < 0) {
			// it has only now found its prey: it has the first strike shown, as it was when it came on
			this.touchFrom = this.tickCount;
			this.goesAt = this.tickCount + SHOWN_MAX;
		}
		if (this.tickCount >= this.goesAt && this.hurtTime == 0) {
			this.setInvisible(true);
			this.goesAt = -1;
			this.goneFor = GONE_MIN + this.random.nextInt(GONE_MAX - GONE_MIN + 1);
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.5), this.getZ(), 16, 0.25, 0.6, 0.25, 0.02);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.6F, 0.8F);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.5), this.getZ(), 24, 0.25, 0.6, 0.25, 0.02);
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.5), this.getZ(), 30, 0.3, 0.7, 0.3, 0.04);
			if (this.owner != null) {
				ShadowDrain.returned(level, this.owner, this.carries);
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return this.owner == null && super.removeWhenFarAway(distance);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SOUL_ESCAPE.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PHANTOM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PHANTOM_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Carries", this.carries);
		output.putBoolean("Wispy", this.wispy());
		if (this.owner != null) {
			output.putString("Owner", this.owner.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.carries = input.getIntOr("Carries", 0);
		this.entityData.set(WISPY, input.getBooleanOr("Wispy", false));
		this.owner = input.getString("Owner").map(UUID::fromString).orElse(null);
	}
}
