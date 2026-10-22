package com.jaymospowers.aether.monster;

import com.jaymospowers.aether.AetherBlocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A rooted monster plant (the aechor plant and the carrion sprout): it never moves, cannot be shoved, and dies when
 * the aether grass it grows from is gone.
 */
public abstract class PlantMonster extends AetherMonster {
	/** How often it checks the ground under it (ticks). */
	public static final int ROOT_CHECK = 20;

	protected PlantMonster(EntityType<? extends PlantMonster> type, Level level, int slash, int impact, int pierce) {
		super(type, level, slash, impact, pierce);
	}

	/** The attributes a plant shares: no speed, no knockback. */
	protected static AttributeSupplier.Builder plantAttributes(double health, double follow) {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, follow);
	}

	/** Whether a block is the aether grass a plant roots in. */
	public static boolean grass(BlockState state) {
		return state.is(AetherBlocks.AETHER_GRASS_BLOCK) || state.is(AetherBlocks.ENCHANTED_AETHER_GRASS_BLOCK);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(Vec3 impulse) {
	}

	@Override
	public void push(double x, double y, double z) {
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.tickCount % ROOT_CHECK == 0 && !grass(level.getBlockState(this.blockPosition().below()))) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.3, this.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
			this.kill(level);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MonsterSounds.get("block.grass.hit");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MonsterSounds.get("block.grass.break");
	}
}
