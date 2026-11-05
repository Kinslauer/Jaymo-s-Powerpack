package com.jaymospowers.client.mixin;

import com.jaymospowers.client.blood.BloodItemOverlay;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.20.41) A bloody weapon (or any bloody item) is drawn with its blood over it, in the hand, dropped or framed (BloodItemOverlay). */
@Mixin(ItemModelResolver.class)
abstract class BloodItemOverlayMixin {
	@Inject(method = "appendItemLayers", at = @At("TAIL"), require = 0)
	private void jaymospowers$blood(ItemStackRenderState output, ItemStack item, ItemDisplayContext context, Level level, ItemOwner owner, int seed,
		CallbackInfo ci) {
		BloodItemOverlay.append(output, item, context);
	}
}
