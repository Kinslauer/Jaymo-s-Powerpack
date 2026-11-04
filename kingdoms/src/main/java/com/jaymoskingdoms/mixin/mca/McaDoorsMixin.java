package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Locks;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.497) MCA's villagers don't open a door, trapdoor or gate that is locked against them. This covers its fence gates,
 * which skip {@code DoorBlock.setOpen} ({@link Locks#barred}).
 */
@Mixin(PathingBlockInteraction.class)
abstract class McaDoorsMixin {
	@Inject(method = "setOpen", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$locked(Entity entity, Level level, BlockState state, BlockPos pos, boolean open, CallbackInfoReturnable<Boolean> cir) {
		if (open && Locks.barred(level, pos, entity)) {
			cir.setReturnValue(false);
		}
	}
}
