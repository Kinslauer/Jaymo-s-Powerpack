package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.TinkerToolItem;
import com.jaymospowers.tinkering.ToolStats;
import java.util.function.Consumer;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tinker tools wear down to broken and stay in your hand (Tanned, Stringy and Solar Powered soften the wear). */
@Mixin(ItemStack.class)
abstract class TinkerToolWearMixin {
	@Shadow
	private int processDurabilityChange(int amount, ServerLevel level, @Nullable ServerPlayer player) {
		throw new AssertionError();
	}

	@Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$wearWithoutBreaking(int amount, ServerLevel level, @Nullable ServerPlayer player, Consumer<ItemStack> onBreak,
		CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (!(self.getItem() instanceof TinkerToolItem)) {
			return;
		}
		ci.cancel();
		if (amount > 0 && ToolStats.isBroken(self)) {
			return;
		}
		int wear = processDurabilityChange(ToolStats.wear(self, amount, level, player), level, player);
		if (wear == 0) {
			return;
		}
		int damage = Math.clamp(self.getDamageValue() + wear, 0, self.getMaxDamage());
		if (player != null) {
			CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player, self, damage);
		}
		self.setDamageValue(damage);
		if (ToolStats.isBroken(self)) {
			onBreak.accept(self.copy());
		}
		ToolStats.apply(self);
	}
}
