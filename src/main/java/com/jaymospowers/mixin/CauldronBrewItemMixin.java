package com.jaymospowers.mixin;

import com.jaymospowers.alchemy.cauldron.CauldronBrewing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Items thrown into a water cauldron go to cauldron brewing ({@link CauldronBrewing#itemInside}). */
@Mixin(LayeredCauldronBlock.class)
abstract class CauldronBrewItemMixin {
	@Inject(method = "entityInside", at = @At("HEAD"))
	private void jaymospowers$brew(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise,
		CallbackInfo ci) {
		if (entity instanceof ItemEntity item && level instanceof ServerLevel server && state.is(Blocks.WATER_CAULDRON)) {
			CauldronBrewing.itemInside(server, pos, state, item);
		}
	}
}
