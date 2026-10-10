package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.kitchen.HobQueue;
import com.jaymoskitchen.kitchen.OvenHob;
import java.util.List;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.blay09.mods.cookingforblockheads.client.render.OvenRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Cooking for Blockheads oven that's been turned on (see OvenHob), or has dishes cooking in it, shows its window lit,
 * as when it's burning fuel; and the dishes cooking in it (see HobQueue) sit on its rack with whatever it's smelting.
 */
@Mixin(OvenRenderer.class)
abstract class OvenRendererMixin {
	@org.spongepowered.asm.mixin.Unique
	/** The rack: CfB draws {@code items} by index in a 3x3 grid, one per interior slot, skipping empty ones. */
	private static final int RACK_SPOTS = 9;

	@Shadow
	@Final
	private ItemModelResolver itemModelResolver;

	@Inject(method = "extractRenderState(Lnet/blay09/mods/cookingforblockheads/block/entity/OvenBlockEntity;"
		+ "Lnet/blay09/mods/cookingforblockheads/client/render/OvenRenderer$OvenRenderState;FLnet/minecraft/world/phys/Vec3;"
		+ "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("TAIL"))
	private void jaymoskitchen$lit(OvenBlockEntity oven, OvenRenderer.OvenRenderState state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
		List<HobQueue.Pending> pending = HobQueue.pending(oven);
		if (OvenHob.isLit(oven) || !pending.isEmpty()) {
			state.active = true;
		}
		List<ItemStackRenderState> items = state.items;
		if (pending.isEmpty() || items == null) {
			return;
		}
		// the dishes take the rack spots the oven's own items leave free, in order; any past nine aren't shown
		int seed = (int) oven.getBlockPos().asLong();
		int next = 0;
		for (int spot = 0; spot < RACK_SPOTS && next < pending.size(); spot++) {
			while (items.size() <= spot) {
				items.add(new ItemStackRenderState());
			}
			if (!items.get(spot).isEmpty()) {
				continue;
			}
			ItemStackRenderState dish = new ItemStackRenderState();
			itemModelResolver.updateForTopItem(dish, pending.get(next++).stack(), ItemDisplayContext.FIXED, oven.getLevel(),
				null, seed + spot);
			items.set(spot, dish);
		}
	}
}
