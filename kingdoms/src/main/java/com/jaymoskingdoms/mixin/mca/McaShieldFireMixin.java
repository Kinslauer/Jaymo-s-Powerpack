package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.MobAmmo;
import com.jaymoskingdoms.MobShields;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (fighting AI patch 3, sh-busy) An MCA villager's bow or crossbow shot (MCA's bow task, its crossbow task) never goes with the
 * shield up: held up for a shot on its way, the shot waits; up for anything else, it comes down to fire
 * ({@link MobShields#holdsFire}).
 * <p>
 * And no free arrows: MCA hands a villager a new arrow for every shot when it holds none. Each bow shot, and each loaded crossbow's,
 * spends an arrow from its quiver ({@link MobAmmo}); with none left the bow looses nothing and the crossbow can't be loaded, so it
 * takes up another weapon ({@code WeaponSense}). The arrows lie where they land to be picked up again ({@code MobAmmo}).
 */
@Mixin(VillagerEntityMCA.class)
abstract class McaShieldFireMixin {
	@Inject(method = "performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$shieldDown(LivingEntity target, float power, CallbackInfo ci) {
		VillagerEntityMCA villager = (VillagerEntityMCA) (Object) this;
		if (MobShields.holdsFire(villager, false)) {
			ci.cancel();
			return;
		}
		if (villager.level().isClientSide()) {
			return;
		}
		InteractionHand hand = RangedWeaponHelper.getWeaponHoldingHand(villager);
		ItemStack weapon = hand == null ? ItemStack.EMPTY : villager.getItemInHand(hand);
		// (a crossbow with nothing loaded looses nothing: no arrow spent for it)
		boolean shoots = weapon.getItem() instanceof CrossbowItem ? CrossbowItem.isCharged(weapon) : weapon.getItem() instanceof BowItem;
		if (shoots && !MobAmmo.spend(villager, MobAmmo.Kind.ARROWS)) {
			ci.cancel();
		}
	}

	/** With its quiver empty, there's nothing to nock or load. */
	@Inject(method = "getProjectile(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$quiver(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
		VillagerEntityMCA villager = (VillagerEntityMCA) (Object) this;
		if (!villager.level().isClientSide() && weapon.getItem() instanceof ProjectileWeaponItem && !MobAmmo.has(villager, MobAmmo.Kind.ARROWS)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
