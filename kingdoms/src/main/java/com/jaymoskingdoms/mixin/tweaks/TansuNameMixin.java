package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.blossomland.BlossomFurnishings;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A tansu is a barrel underneath; its screen is headed with its own name, not "Barrel". */
@Mixin(BarrelBlockEntity.class)
public abstract class TansuNameMixin {
	@Inject(method = "getDefaultName", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$tansu(CallbackInfoReturnable<Component> cir) {
		if (BlossomFurnishings.TANSU != null && ((BlockEntity)(Object)this).getBlockState().is(BlossomFurnishings.TANSU)) {
			cir.setReturnValue(Component.translatable("container.jaymoskingdoms.tansu"));
		}
	}
}
