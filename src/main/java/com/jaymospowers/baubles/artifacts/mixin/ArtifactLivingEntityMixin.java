package com.jaymospowers.baubles.artifacts.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * (Artifacts) Waterstrider boots (run on water while sprinting) and strider shoes (walk on lava unless sneaking), the drinking
 * hats (eat and drink faster) and the golden hook (more experience from what you kill).
 */
@Mixin(LivingEntity.class)
public abstract class ArtifactLivingEntityMixin {
	@Shadow
	public abstract Player getLastHurtByPlayer();

	@ModifyReturnValue(method = "canStandOnFluid", at = @At("RETURN"))
	private boolean jaymospowers$walkOnFluid(boolean original, FluidState fluid) {
		if (original || !((Object) this instanceof Player player) || player.isShiftKeyDown()) {
			return original;
		}
		if (fluid.is(FluidTags.WATER)) {
			return player.isSprinting() && ArtifactEffects.wears(player, ArtifactBaubles.WATERSTRIDER_BOOTS);
		}
		return fluid.is(FluidTags.LAVA) && ArtifactEffects.wears(player, ArtifactBaubles.STRIDER_SHOES);
	}

	@ModifyExpressionValue(method = "startUsingItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStack;getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"))
	private int jaymospowers$drinkingHat(int original, InteractionHand hand) {
		if (original <= 0 || !((Object) this instanceof Player player)) {
			return original;
		}
		boolean hat = ArtifactEffects.wears(player, ArtifactBaubles.ALEWIFE_CAP) || ArtifactEffects.wears(player, ArtifactBaubles.JESTER_CAP);
		if (!hat) {
			return original;
		}
		ItemStack stack = player.getItemInHand(hand);
		ItemUseAnimation animation = stack.getUseAnimation();
		double speed = animation == ItemUseAnimation.DRINK ? ArtifactEffects.HAT_DRINK : animation == ItemUseAnimation.EAT ? ArtifactEffects.HAT_EAT : 1.0;
		return (int) Math.max(1, Math.round(original / speed));
	}

	@ModifyArg(method = "dropExperience", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
	private int jaymospowers$goldenHook(int xp) {
		Player killer = this.getLastHurtByPlayer();
		if (xp <= 0 || killer == null || (Object) this instanceof Player || !ArtifactEffects.wears(killer, ArtifactBaubles.GOLDEN_HOOK)) {
			return xp;
		}
		return (int) Math.round(xp * ArtifactEffects.HOOK_XP);
	}
}
