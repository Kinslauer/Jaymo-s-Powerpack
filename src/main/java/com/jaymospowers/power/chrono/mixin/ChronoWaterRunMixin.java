package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoSpeed;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.10.28) Overclock's water run: a sprinting, overclocked player stands on water the way a strider stands on lava
 * (the surface of still water turns solid underfoot, on both sides). Stop sprinting and you sink.
 *
 * <p>(0.10.96) In 26.3 standing on a fluid takes two things: {@code canStandOnFluid} says yes, and
 * {@code getLiquidCollisionShape} gives the shape to stand on. That shape is empty for everyone but striders, so 0.10.28
 * runners always sank. Ours is a full block, so you run level with the shore, above the water rather than in it.
 *
 * <p>(0.11.40) Lava holds you up the same way (see {@link ChronoSpeed#runsOn}).
 */
@Mixin(LivingEntity.class)
abstract class ChronoWaterRunMixin {
	@Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$chronoWaterRun(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && ChronoSpeed.runsOn(player, fluid)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getLiquidCollisionShape", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$chronoWaterRunShape(CallbackInfoReturnable<VoxelShape> cir) {
		// the fluid itself is checked by canStandOnFluid above (water or lava)
		if ((Object) this instanceof Player player && ChronoSpeed.runsOnFluids(player)) {
			cir.setReturnValue(Shapes.block());
		}
	}
}
