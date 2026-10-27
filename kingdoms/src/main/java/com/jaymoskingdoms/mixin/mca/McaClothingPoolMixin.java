package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.ClothingLibrary;
import com.jaymoskingdoms.Uniforms;
import com.jaymoskingdoms.Wardrobe;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.WeightedPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.171) A villager's clothes come from MCA's clothing library: their job's, or a ruler's royal ones ({@link ClothingLibrary}).
 * (0.7.177) MCA's own pick leaves out the banned, off-theme clothes ({@link Wardrobe}).
 * (0.7.270) Captains, crews, rulers and guards wear only our own outfits ({@link Uniforms}); the library is off.
 */
@Mixin(ClothingList.class)
abstract class McaClothingPoolMixin {
	@Inject(method = "getPool(Lnet/conczin/mca/entity/VillagerLike;)Lnet/conczin/mca/resources/WeightedPool;", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$library(VillagerLike<?> villager, CallbackInfoReturnable<WeightedPool<String>> cir) {
		if (villager instanceof VillagerEntityMCA v && !v.level().isClientSide()) {
			// (0.7.270) our named characters wear only their own outfits
			WeightedPool<String> pool = Uniforms.pool(v);
			if (pool != null) {
				cir.setReturnValue(pool);
				return;
			}
			pool = ClothingLibrary.pool(v);
			if (pool != null) {
				cir.setReturnValue(pool);
			}
		}
	}

	@Inject(method = "getPool(Lnet/conczin/mca/entity/VillagerLike;)Lnet/conczin/mca/resources/WeightedPool;", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$themed(VillagerLike<?> villager, CallbackInfoReturnable<WeightedPool<String>> cir) {
		if (villager instanceof VillagerEntityMCA v && !v.level().isClientSide() && Uniforms.outfits(v).isEmpty()) {
			cir.setReturnValue(Wardrobe.filter(cir.getReturnValue(), v));
		}
	}
}
