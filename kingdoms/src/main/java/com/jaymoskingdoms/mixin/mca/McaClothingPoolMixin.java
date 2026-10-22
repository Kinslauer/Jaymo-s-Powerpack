package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.ClothingLibrary;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.WeightedPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.171) A villager's clothes come from MCA's clothing library: their job's, or a ruler's royal ones ({@link ClothingLibrary}). */
@Mixin(ClothingList.class)
abstract class McaClothingPoolMixin {
	@Inject(method = "getPool(Lnet/conczin/mca/entity/VillagerLike;)Lnet/conczin/mca/resources/WeightedPool;", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$library(VillagerLike<?> villager, CallbackInfoReturnable<WeightedPool<String>> cir) {
		if (villager instanceof VillagerEntityMCA v && !v.level().isClientSide()) {
			WeightedPool<String> pool = ClothingLibrary.pool(v);
			if (pool != null) {
				cir.setReturnValue(pool);
			}
		}
	}
}
