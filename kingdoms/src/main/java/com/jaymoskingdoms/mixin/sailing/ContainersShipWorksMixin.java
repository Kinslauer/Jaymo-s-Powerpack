package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.ShipWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What a workshop aboard a ship ({@link ShipWorks}) spits out (an empty bucket off a fuel stack) drops where it is on her. */
@Mixin(Containers.class)
abstract class ContainersShipWorksMixin {
	@Inject(method = "dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$shipWorkDrop(Level level, double x, double y, double z, ItemStack stack, CallbackInfo ci) {
		if (!ShipWorks.working() || !ShipWorks.ours(BlockPos.containing(x, y, z))) {
			return;
		}
		ci.cancel();
		Vec3 at = ShipWorks.where();
		if (at != null) {
			ShipWorks.outside(() -> Containers.dropItemStack(level, at.x, at.y, at.z, stack));
		}
	}
}
