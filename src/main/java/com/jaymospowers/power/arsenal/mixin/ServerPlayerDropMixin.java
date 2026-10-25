package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.armory.FlintlockDraw;
import com.jaymospowers.power.arsenal.ArsenalFlintlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dropping (Q) one of a pair of summoned guns drops the other hand's too. Dropping one of your own flintlocks reaches
 * for the next you carry (see FlintlockDraw).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDropMixin {
	@Unique
	private ItemStack jaymospowers$dropping = ItemStack.EMPTY;

	@Inject(method = "drop(Z)V", at = @At("HEAD"))
	private void jaymospowers$dropPair(boolean all, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		this.jaymospowers$dropping = player.getMainHandItem().copy();
		ArsenalFlintlock.dropPartner(player);
	}

	@Inject(method = "drop(Z)V", at = @At("RETURN"))
	private void jaymospowers$drawSpare(boolean all, CallbackInfo ci) {
		ItemStack dropped = this.jaymospowers$dropping;
		this.jaymospowers$dropping = ItemStack.EMPTY;
		FlintlockDraw.dropped((ServerPlayer) (Object) this, dropped);
	}
}
