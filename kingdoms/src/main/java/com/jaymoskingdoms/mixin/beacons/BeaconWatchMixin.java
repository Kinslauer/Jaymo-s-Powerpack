package com.jaymoskingdoms.mixin.beacons;

import com.jaymoskingdoms.BeaconWatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (BWM batch 5) A lit beacon's watch-light ({@link BeaconWatch}): the game calls this every 80 ticks for a beacon with
 * a pyramid and an open beam, before its own powers (which it skips when none is chosen); we go first, so a beacon
 * with no power picked still keeps watch.
 */
@Mixin(BeaconBlockEntity.class)
abstract class BeaconWatchMixin {
	@Inject(method = "applyEffects", at = @At("HEAD"))
	private static void jaymoskingdoms$watch(Level level, BlockPos pos, int levels, Holder<MobEffect> primary, Holder<MobEffect> secondary,
		CallbackInfo ci) {
		if (level instanceof ServerLevel server) {
			BeaconWatch.pulse(server, pos, levels);
		}
	}
}
