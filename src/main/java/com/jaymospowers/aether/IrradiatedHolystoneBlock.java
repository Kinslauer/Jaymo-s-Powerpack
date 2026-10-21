package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Irradiated holystone: poisons anyone standing on it, once a second (Poison I, two seconds each time). */
public class IrradiatedHolystoneBlock extends Block {
	// ---- tuning ----
	/** Ticks between doses, how long a dose lasts, and its level (0 is Poison I). */
	public static final int INTERVAL = 20, POISON_TICKS = 40, POISON_LEVEL = 0;

	public IrradiatedHolystoneBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (!level.isClientSide() && entity instanceof LivingEntity living && entity.tickCount % INTERVAL == 0) {
			living.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_LEVEL));
		}
		super.stepOn(level, pos, state, entity);
	}
}
