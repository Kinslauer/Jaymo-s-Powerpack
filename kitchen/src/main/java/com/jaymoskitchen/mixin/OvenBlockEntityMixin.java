package com.jaymoskitchen.mixin;

import com.jaymoskitchen.kitchen.HobQueue;
import com.jaymoskitchen.kitchen.OvenFuel;
import com.jaymoskitchen.kitchen.OvenHob;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The oven heats up and cools down as a stove (see OvenHob), using its own idea of what burns and for how long. */
@Mixin(OvenBlockEntity.class)
abstract class OvenBlockEntityMixin implements OvenFuel {
	@Shadow
	protected abstract int getBurnTime(Level level, ItemStack stack);

	@Override
	public int jaymoskitchen$burnTime(Level level, ItemStack stack) {
		return getBurnTime(level, stack);
	}

	@Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
		at = @At("TAIL"))
	private void jaymoskitchen$heat(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
		OvenHob.serverTick(level, pos, (OvenBlockEntity) (Object) this);
		HobQueue.serverTick(level, pos, (OvenBlockEntity) (Object) this);
	}

	/** Broken while dishes are still cooking on it: they drop. */
	@Inject(method = "preRemoveSideEffects(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
		at = @At("HEAD"))
	private void jaymoskitchen$dropPending(BlockPos pos, BlockState state, CallbackInfo ci) {
		OvenBlockEntity oven = (OvenBlockEntity) (Object) this;
		HobQueue.dropAll(oven.getLevel(), pos, oven);
	}
}
