package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.Poisons;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Whoever finishes eating or drinking something dosed with poison takes the dose ({@link Poisons#eaten}); a bottle of water in a lair's land is foul ({@link com.jaymoskingdoms.LairLand#sipped}). */
@Mixin(LivingEntity.class)
abstract class DosedDishMixin {
	@Shadow
	protected ItemStack useItem;

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void jaymoskingdoms$dosed(CallbackInfo ci) {
		Poisons.eaten((LivingEntity) (Object) this, this.useItem);
		com.jaymoskingdoms.HeroesFeast.eaten((LivingEntity) (Object) this, this.useItem);
		com.jaymoskingdoms.Cantrips.eaten((LivingEntity) (Object) this, this.useItem);
		com.jaymoskingdoms.LairLand.sipped((LivingEntity) (Object) this, this.useItem);
	}
}
