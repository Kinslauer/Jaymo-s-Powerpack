package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourWear;
import java.util.function.Consumer;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.20.68) An armour piece worn to 0 stays, broken and protecting nothing, until it is repaired ({@link ArmourWear}). */
@Mixin(ItemStack.class)
abstract class ArmourWearStackMixin {
	@Shadow
	private int processDurabilityChange(int amount, ServerLevel level, @Nullable ServerPlayer player) {
		throw new AssertionError();
	}

	@Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$armourStays(int amount, ServerLevel level, @Nullable ServerPlayer player, Consumer<ItemStack> onBreak,
		CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (!ArmourWear.applies(self)) {
			return;
		}
		ci.cancel();
		if (amount > 0 && self.isBroken()) {
			return;
		}
		int wear = processDurabilityChange(amount, level, player);
		if (wear == 0) {
			return;
		}
		int damage = Mth.clamp(self.getDamageValue() + wear, 0, self.getMaxDamage());
		if (player != null) {
			CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player, self, damage);
		}
		self.setDamageValue(damage);
		if (self.isBroken() && wear > 0) {
			// the break's sound and puff; the piece itself stays where it is
			onBreak.accept(self.copy());
		}
	}
}
