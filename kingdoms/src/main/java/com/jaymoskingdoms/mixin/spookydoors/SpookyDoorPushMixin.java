package com.jaymoskingdoms.mixin.spookydoors;

import com.jaymoskingdoms.Locks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Spooky Doors door walked into swings open before the walker: on the walking player's own client (which then tells the
 * server), and on the server for mobs. A locked door they've no key to doesn't give ({@link Locks#pushBarred}), so the
 * client never shows it swinging and the player is told it's locked. The server's own check
 * ({@code ServerSpookyDoorLockMixin}) stands behind this. Skipped without Spooky Doors.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.spookydoors.block.SpookyDoorBlockHooks", remap = false)
abstract class SpookyDoorPushMixin {
	@Inject(method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
		at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymoskingdoms$locked(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
		if (Locks.pushBarred(level, pos, entity)) {
			ci.cancel();
		}
	}
}
