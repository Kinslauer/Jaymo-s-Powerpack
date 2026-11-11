package com.jaymospowers.mixin;

import com.jaymospowers.combat.ClashFx;
import com.jaymospowers.combat.ShieldShatter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a blow on a raised guard throws off ({@link ClashFx#blocked}: sparks off steel, chips off a wooden shield), for
 * anyone who blocks, and the burst of a wooden shield that breaks ({@link ShieldShatter#broke}). The guard is noted
 * before vanilla wears it, since a shield on its last point is gone by the time the block is done.
 */
@Mixin(LivingEntity.class)
abstract class BlockFxMixin {
	@Unique
	private ItemStack jaymospowers$guardHeld = ItemStack.EMPTY;

	@Inject(method = "applyItemBlocking", at = @At("HEAD"))
	private void jaymospowers$noteGuard(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		ItemStack guard = ((LivingEntity) (Object) this).getItemBlockingWith();
		this.jaymospowers$guardHeld = guard == null || guard.isEmpty() ? ItemStack.EMPTY : guard.copy();
	}

	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void jaymospowers$blockFx(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		ItemStack guard = this.jaymospowers$guardHeld;
		this.jaymospowers$guardHeld = ItemStack.EMPTY;
		if (!guard.isEmpty() && cir.getReturnValue() > 0.0F) {
			ClashFx.blocked((LivingEntity) (Object) this, level, source, guard, cir.getReturnValue());
		}
	}

	@Inject(method = "onEquippedItemBroken", at = @At("HEAD"))
	private void jaymospowers$shieldBroke(ItemStack broken, EquipmentSlot slot, CallbackInfo ci) {
		ShieldShatter.broke((LivingEntity) (Object) this, broken);
	}
}
