package com.jaymoskingdoms.mixin.eyes;

import com.jaymoskingdoms.Comrades;
import com.jaymoskingdoms.NpcEyes;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (ey-npc) A fighter picks its foe only from those it makes out ({@link NpcEyes#sees}): the cone, the light, sneaking and
 * distance that rule what monsters notice of a player rule it too.
 */
@Mixin(Comrades.class)
abstract class ComradesSightMixin {
	@Inject(method = "candidates", at = @At("RETURN"), cancellable = true)
	private static void jaymoskingdoms$seen(ServerLevel level, Mob m, String mine, CallbackInfoReturnable<List<LivingEntity>> cir) {
		List<LivingEntity> foes = cir.getReturnValue();
		foes.removeIf(foe -> !NpcEyes.sees(m, foe));
	}
}
