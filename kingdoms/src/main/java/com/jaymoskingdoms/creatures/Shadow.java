package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.ShadowDrain;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A shadow of the ruins: a cold, thin dark shape that lives where it is dim and withers in the light (a bright place, or the
 * day, hurts it). Its touch drains Might ({@link ShadowDrain}); a player drained to nothing goes down, never to death, and
 * their own shadow peels away from them and joins the others (it carries the Might it took, and gives it back when slain).
 * It slips through gaps a man cannot, and plain blows do half as much to it.
 */
public class Shadow extends Beast {
	static final double HEALTH = 14.0, SPEED = 0.3, DAMAGE = 1.0;
	/** Light (0-15) from which it withers, the harm each second, and the share of a plain blow it takes. */
	static final int BRIGHT = 10;
	static final float WITHERS = 1.5F, PLAIN_SHARE = 0.6F;

	private @Nullable UUID owner;
	private int carries;

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
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity who) {
			ShadowDrain.touch(level, who, this);
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return super.hurtServer(level, source, source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) ? damage : damage * PLAIN_SHARE);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % 20 == 0 && level.getMaxLocalRawBrightness(this.blockPosition()) >= BRIGHT) {
			this.hurtServer(level, level.damageSources().magic(), WITHERS);
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.6), this.getZ(), 6, 0.2, 0.4, 0.2, 0.02);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.owner != null && this.level() instanceof ServerLevel level) {
			ShadowDrain.returned(level, this.owner, this.carries);
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
		if (this.owner != null) {
			output.putString("Owner", this.owner.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.carries = input.getIntOr("Carries", 0);
		this.owner = input.getString("Owner").map(UUID::fromString).orElse(null);
	}
}
