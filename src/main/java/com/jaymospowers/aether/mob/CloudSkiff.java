package com.jaymospowers.aether.mob;

import com.jaymospowers.core.wounds.Karma;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The cloud skiff: a sled of the Aether's own cold cloud on a skyroot keel, with a cloudwool sail. It hangs in the air where
 * it is left and flies where its rider looks (forward to go, jump to rise). The cloud is the afterlife's, so it holds only
 * the just (see {@link Karma#just}): anyone else sinks through it, and a rider who stops being just is let fall. A blow from
 * a player folds it back into its item; nothing else harms it.
 */
public class CloudSkiff extends Mob {
	// ---- tuning ----
	public static final double HEALTH = 10.0;
	/** Flying speed (the attribute): about twice a happy ghast's. */
	public static final double FLY_SPEED = 0.11;
	/** Backwards it goes at this share of forward speed, and holding jump adds this much climb. */
	public static final float REVERSE = 0.5F, CLIMB = 0.5F;
	/** How often (ticks) it weighs who is aboard. */
	public static final int WEIGH_EVERY = 20;
	/** It carries this many. */
	public static final int SEATS = 2;

	public CloudSkiff(EntityType<? extends CloudSkiff> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.FLYING_SPEED, FLY_SPEED);
	}

	// ---- who may ride ----

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player.isSecondaryUseActive() || this.getPassengers().size() >= SEATS) {
			return InteractionResult.PASS;
		}
		if (this.level() instanceof ServerLevel level) {
			if (Karma.just(player)) {
				player.startRiding(this);
			} else {
				// the cloud gives way under them
				level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.3, this.getZ(), 12, 0.5, 0.1, 0.5, 0.02);
				this.playSound(SoundEvents.WOOL_FALL, 0.8F, 0.7F);
				player.sendOverlayMessage(Component.translatable("entity.jaymospowers.cloud_skiff.sinks"));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < SEATS;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.tickCount % WEIGH_EVERY == 0) {
			for (Entity passenger : this.getPassengers()) {
				if (passenger instanceof Player player && !Karma.just(player)) {
					player.stopRiding();
					level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.3, this.getZ(), 12, 0.5, 0.1, 0.5, 0.02);
				}
			}
		}
	}

	// ---- flying ----

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	/** Forward goes where the rider looks, up and down with it; jump climbs. */
	@Override
	protected Vec3 getRiddenInput(Player rider, Vec3 input) {
		float forward = 0.0F, up = 0.0F;
		if (rider.zza != 0.0F) {
			float pitch = rider.getXRot() * Mth.DEG_TO_RAD;
			float back = rider.zza < 0.0F ? -REVERSE : 1.0F;
			forward = Mth.cos(pitch) * back;
			up = -Mth.sin(pitch) * back;
		}
		if (rider.isJumping()) {
			up += CLIMB;
		}
		return new Vec3(rider.xxa, up, forward).scale(3.9 * this.getAttributeValue(Attributes.FLYING_SPEED));
	}

	@Override
	protected void tickRidden(Player rider, Vec3 input) {
		super.tickRidden(rider, input);
		this.setRot(rider.getYRot(), rider.getXRot() * 0.5F);
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
	}

	@Override
	public void travel(Vec3 input) {
		float speed = (float)this.getAttributeValue(Attributes.FLYING_SPEED) * 5.0F / 3.0F;
		this.travelFlying(input, speed, speed, speed);
	}

	// ---- a thing, not a creature ----

	/** A player's blow folds it up; only the void takes it otherwise. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		if (source.getEntity() instanceof Player player && !this.hasPassenger(player)) {
			this.ejectPassengers();
			if (!player.isCreative()) {
				this.spawnAtLocation(level, new ItemStack(AetherAnimals.CLOUD_SKIFF_ITEM));
			}
			this.playSound(SoundEvents.WOOL_BREAK, 0.8F, 1.0F);
			this.discard();
			return true;
		}
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
