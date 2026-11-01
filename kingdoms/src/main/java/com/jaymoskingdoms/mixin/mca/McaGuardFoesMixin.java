package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.McaCompat;
import java.util.Optional;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.21) A realm's MCA guards also go for its foes: players it counts hostile, barbarians, its enemies' folk in a war ({@link McaCompat#foe}). */
@Mixin(GuardEnemiesSensor.class)
abstract class McaGuardFoesMixin {
	/** As keen as on a monster that's after them (MCA's 9), just under. */
	private static final int FOE_PRIORITY = 8;

	@Inject(method = "getPriority(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/Optional;Ljava/util/Optional;)I",
		at = @At("RETURN"), cancellable = true)
	private static void jaymoskingdoms$foes(LivingEntity entity, LivingEntity guard, Optional<Player> followed, Optional<Integer> configured,
		CallbackInfoReturnable<Integer> cir) {
		if (guard != null && (com.jaymoskingdoms.SwornCaptains.spares(guard, entity) || com.jaymoskingdoms.InBalming.spares(guard, entity))) {
			// (0.7.433) a sworn captain never goes for his lord
			cir.setReturnValue(-1);
			return;
		}
		if (cir.getReturnValueI() < FOE_PRIORITY && guard != null && McaCompat.foe(guard, entity)) {
			cir.setReturnValue(FOE_PRIORITY);
		}
	}
}
