package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.disasters.Storms;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.120) Rain under a storm cell ({@link Storms}) puts fires out and stops them spreading, as vanilla's rain does.
 * The fire only asked the world's weather ({@code isRaining}), so under a cell on a clear day its lightning's fires
 * burnt on through the downpour. Where it rains is still {@code isRainingAt} (which {@link LevelWeatherMixin} already
 * answers for cells), so fires under leaves or a roof stay lit, as before.
 */
@Mixin(FireBlock.class)
abstract class FireBlockRainMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
	private boolean jaymoskingdoms$stormRain(ServerLevel level, Operation<Boolean> original, BlockState state, ServerLevel tickLevel,
		BlockPos pos, RandomSource random) {
		return original.call(level) || Storms.cover(level, pos.getX() + 0.5, pos.getZ() + 0.5) > Storms.RAINS;
	}
}
