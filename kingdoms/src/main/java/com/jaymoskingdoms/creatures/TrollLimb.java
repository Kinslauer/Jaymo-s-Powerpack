package com.jaymoskingdoms.creatures;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A troll's severed arm, crawling back to the one it came from. When it arrives it rejoins and heals the troll. Killed on
 * the way (a blow, fire, acid) it is gone for good, and the troll stays without it; it also wastes away if the troll is gone.
 */
public class TrollLimb extends Monster {
	static final double HEALTH = 6.0, SPEED = 0.32;
	/** It rejoins when this near (blocks), looks again for a path this often (ticks), and withers after this long (ticks). */
	static final double JOINS = 1.3;
	static final int REPATH = 10, WITHERS = 20 * 90;

	private @Nullable UUID owner;

	public TrollLimb(EntityType<? extends TrollLimb> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	void belongsTo(Troll troll) {
		this.owner = troll.getUUID();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		Troll troll = this.owner != null && level.getEntity(this.owner) instanceof Troll t && t.isAlive() ? t : null;
		if (troll == null || this.tickCount > WITHERS) {
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.5), this.getZ(), 6, 0.1, 0.1, 0.1, 0.01);
			this.discard();
			return;
		}
		if (this.distanceTo(troll) < JOINS) {
			troll.limbBack(false);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 1.0F, 0.8F);
			this.discard();
			return;
		}
		if (this.tickCount % REPATH == 0) {
			this.getNavigation().moveTo(troll, 1.2);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && this.owner != null && level.getEntity(this.owner) instanceof Troll t) {
			t.limbDestroyed();
		}
	}

	@Override
	public boolean fireImmune() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.owner != null) {
			output.putString("Owner", this.owner.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.owner = input.getString("Owner").map(UUID::fromString).orElse(null);
	}
}
