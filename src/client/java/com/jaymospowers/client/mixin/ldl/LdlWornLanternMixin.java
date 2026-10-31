package com.jaymospowers.client.mixin.ldl;

import com.jaymospowers.baubles.Baubles;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Motion patch 4) A worn lantern lights the way: LambDynamicLights (optional; Lambda License, nothing of it copied)
 * works out how much light a living thing gives off from what it holds and wears, and a player's hip lantern (belt slot)
 * or lantern pole (body slot) counts as well, as bright as the lantern block it is. Skipped when LambDynamicLights
 * isn't there.
 */
@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.LambDynLights", remap = false)
abstract class LdlWornLanternMixin {
	/** The lantern pole always carries a plain lantern. */
	private static final int POLE_LIGHT = 15;

	@Inject(method = "getLivingEntityLuminanceFromItems", at = @At("RETURN"), cancellable = true, require = 0)
	private static void jaymospowers$wornLanterns(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
		if (!(entity instanceof Player player)) {
			return;
		}
		int light = cir.getReturnValueI();
		ItemStack hip = Baubles.shown(player, 0);
		if (hip.getItem() instanceof BlockItem item) {
			light = Math.max(light, item.getBlock().defaultBlockState().getLightEmission());
		}
		if (!Baubles.shown(player, 1).isEmpty()) {
			light = Math.max(light, POLE_LIGHT);
		}
		if (light != cir.getReturnValueI()) {
			cir.setReturnValue(light);
		}
	}
}
