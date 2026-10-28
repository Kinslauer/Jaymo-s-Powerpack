package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.smithing.Wear;
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

/** A graded item that would break stays at its last point of durability, Broken, until it is repaired ({@link Wear}). */
@Mixin(ItemStack.class)
abstract class GradedWearMixin {
	@Shadow
	private int processDurabilityChange(int amount, ServerLevel level, @Nullable ServerPlayer player) {
		throw new AssertionError();
	}

	@Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$brokenNotGone(int amount, ServerLevel level, @Nullable ServerPlayer player, Consumer<ItemStack> onBreak,
		CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (!Wear.applies(self)) {
			return;
		}
		ci.cancel();
		if (amount > 0 && Wear.broken(self)) {
			return;
		}
		int wear = processDurabilityChange(amount, level, player);
		if (wear == 0) {
			return;
		}
		int last = Math.max(0, self.getMaxDamage() - 1);
		int damage = Mth.clamp(self.getDamageValue() + wear, 0, last);
		if (player != null) {
			CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player, self, damage);
		}
		self.setDamageValue(damage);
		if (damage >= last && wear > 0) {
			Wear.breakDown(self);
			onBreak.accept(self.copy());
		} else if (self.has(Wear.BROKEN)) {
			Wear.mend(self);
		}
	}
}
