package com.jaymoskingdoms.mixin.mca;

import net.conczin.mca.entity.VillagerLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.47) Andy: "lock everyone to make a villager instead of vanilla or player". MCA lets a player be drawn as a
 * villager, as a player-shaped body, or as vanilla Steve; every stored choice is read back through {@code byId}, so
 * everyone (old saves too) is a villager, sized like one.
 */
@Mixin(VillagerLike.PlayerModel.class)
abstract class McaVillagerBodyMixin {
	@Inject(method = "byId", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$alwaysVillager(int id, CallbackInfoReturnable<VillagerLike.PlayerModel> cir) {
		cir.setReturnValue(VillagerLike.PlayerModel.VILLAGER);
	}
}
