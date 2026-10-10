package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.kitchen.OvenHob;
import net.blay09.mods.cookingforblockheads.block.OvenBlock;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A lit Cooking for Blockheads oven (see OvenHob) crackles; a pot on it bubbles and steams, a pan smokes. */
@Mixin(OvenBlock.class)
abstract class OvenBlockMixin {
	@Inject(method = "animateTick", at = @At("TAIL"))
	private void jaymoskitchen$hob(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (!(level.getBlockEntity(pos) instanceof OvenBlockEntity oven) || !OvenHob.isLit(oven)) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		if (random.nextDouble() < 0.1) {
			level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 1.0F, false);
		}
		switch (OvenHob.cookware(oven)) {
			case POT -> {
				if (random.nextInt(2) == 0) {
					level.addParticle(ParticleTypes.BUBBLE_POP, x + random.nextDouble() * 0.5 - 0.25, y + 0.3,
						z + random.nextDouble() * 0.5 - 0.25, 0.0, 0.02, 0.0);
				}
				if (random.nextInt(3) == 0) {
					level.addParticle(ParticleTypes.CLOUD, x, y + 0.4, z, 0.0, 0.03, 0.0);
				}
			}
			case PAN -> {
				if (random.nextInt(3) == 0) {
					level.addParticle(ParticleTypes.SMOKE, x + random.nextDouble() * 0.5 - 0.25, y + 0.15,
						z + random.nextDouble() * 0.5 - 0.25, 0.0, 0.02, 0.0);
				}
			}
			case NONE -> {
			}
		}
	}
}
