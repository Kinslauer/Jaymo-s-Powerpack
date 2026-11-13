package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.MaskedFaces;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.interaction.Constraint;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A villager doesn't know a masked player for family ({@link MaskedFaces#kinHidden}): the talk menu's family answers
 * (MCA's family, relative, spouse, engaged, promised, kids and parent constraints) read as if they weren't kin.
 */
@Mixin(Constraint.class)
abstract class McaKinConstraintMixin {
	@Inject(method = "test(Lnet/conczin/mca/entity/VillagerLike;Lnet/minecraft/server/level/ServerPlayer;)Z", at = @At("HEAD"), cancellable = true, remap = false)
	private void jaymoskingdoms$maskedKin(VillagerLike<?> villager, ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
		if (!MaskedFaces.kinHidden(villager, player)) {
			return;
		}
		switch (((Enum<?>)(Object)this).name()) {
			case "FAMILY", "RELATIVE", "SPOUSE", "ENGAGED", "PROMISED", "KIDS", "PARENT" -> cir.setReturnValue(false);
			case "NOT_FAMILY", "NOT_RELATIVE", "NOT_SPOUSE", "NOT_ENGAGED", "NOT_PROMISED", "NOT_KIDS", "NOT_PARENT" -> cir.setReturnValue(true);
			default -> {
			}
		}
	}
}
