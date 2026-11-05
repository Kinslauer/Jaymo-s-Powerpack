package com.jaymospowers.client.mixin.weaponmaster;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.20.73) Only one tool or weapon is carried on the body at a time (Andy: "only one tool or weapon should show at a
 * time, in whatever slot"). YDM's Weapon Master (optional; CC0, nothing of it copied) hangs every hotbar item on the
 * player's back and belt at once. Each player's pass of its layer now draws the first item it reaches (the lowest
 * hotbar slot that isn't in hand) and skips the rest. Every item it draws, for your own body and for other players',
 * goes through {@code HumanoidItemLayer.renderItem}; its quiver isn't an item and still shows. Skipped when Weapon
 * Master isn't there.
 */
@Pseudo
@Mixin(targets = "yourdailymodder.weaponmaster.itemlayers.HumanoidItemLayer", remap = false)
abstract class WeaponMasterOneItemMixin {
	/** How many items one body may show at once. */
	@Unique
	private static final int MAX_SHOWN = 1;

	/** Items drawn so far in this body's pass of the layer (rendering is single-threaded). */
	@Unique
	private static int jaymospowers$shown;

	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
		at = @At("HEAD"), require = 0)
	private void jaymospowers$newBody(CallbackInfo ci) {
		jaymospowers$shown = 0;
	}

	@Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
		at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$oneItem(net.minecraft.world.entity.LivingEntity entity, ItemStack stack,
			net.minecraft.world.item.ItemDisplayContext context, com.mojang.blaze3d.vertex.PoseStack pose,
			net.minecraft.client.renderer.SubmitNodeCollector collector, int light, CallbackInfo ci) {
		jaymospowers$gate(stack, ci);
	}

	@Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
		at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$oneItemPlain(net.minecraft.world.entity.LivingEntity entity, ItemStack stack,
			com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		jaymospowers$gate(stack, ci);
	}

	@Unique
	private static void jaymospowers$gate(ItemStack stack, CallbackInfo ci) {
		if (stack.isEmpty()) {
			return;
		}
		if (jaymospowers$shown >= MAX_SHOWN) {
			ci.cancel();
			return;
		}
		jaymospowers$shown++;
	}
}
