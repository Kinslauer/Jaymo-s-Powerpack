package com.jaymoskingdoms.equestrian;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.34) A war elephant: a mount for desert kingdoms, ridden, tamed, fed and bred like a horse, with the gaits and
 * stamina of {@link Equestrian}. Saddled it carries a howdah, a caparison and a forehead plate. It barely jumps, it's
 * hard to frighten (fire still does it), and at the canter or faster it tramples the monsters in its way, harder at a
 * charge (sprint), trumpeting as it goes. Its breed is {@link Breed#WAR_ELEPHANT} (size, health, stats).
 */
public class WarElephant extends AbstractHorse {
	public WarElephant(EntityType<? extends WarElephant> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createBaseHorseAttributes()
			.add(Attributes.MAX_HEALTH, 100.0)
			.add(Attributes.MOVEMENT_SPEED, 0.15)
			.add(Attributes.JUMP_STRENGTH, 0.44)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.ARMOR, 6.0)
			.add(Attributes.STEP_HEIGHT, 1.1)
			.add(Attributes.SAFE_FALL_DISTANCE, 4.0);
	}

	/** A mammoth goes by its own name. */
	@Override
	protected Component getTypeName() {
		Equine e = this.getAttached(Equestrian.EQUINE);
		return e != null && e.breed() == Breed.WOOLLY_MAMMOTH ? Component.translatable(e.breed().key()) : super.getTypeName();
	}

	/** The charge: a trumpet blast. */
	void trumpet(ServerLevel level) {
		level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 1.6F, 1.45F);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		boolean openInventory = !this.isBaby() && this.isTamed() && player.isSecondaryUseActive();
		if (!this.isVehicle() && !openInventory) {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.isEmpty()) {
				if (this.isFood(stack)) {
					return this.fedFood(player, stack);
				}
				if (!this.isTamed()) {
					this.makeMad();
					return InteractionResult.SUCCESS;
				}
			}
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean canMate(Animal partner) {
		return partner != this && partner instanceof WarElephant other && this.canParent() && other.canParent();
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		WarElephant baby = Equestrian.WAR_ELEPHANT.create(level, EntitySpawnReason.BREEDING);
		if (baby != null) {
			this.setOffspringAttributes(partner, baby);
		}
		return baby;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CAMEL_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	@Override
	protected @Nullable SoundEvent getAngrySound() {
		return SoundEvents.RAVAGER_ROAR;
	}

	@Override
	protected @Nullable SoundEvent getEatingSound() {
		return SoundEvents.CAMEL_EAT;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		if (!state.liquid()) {
			this.playSound(SoundEvents.RAVAGER_STEP, 0.35F, 0.6F);
		}
	}

	@Override
	protected void playJumpSound() {
		this.playSound(SoundEvents.RAVAGER_STEP, 0.6F, 0.5F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 600;
	}
}
