package com.jaymoskingdoms.client.mixin.spookydoors;

import com.jaymoskingdoms.Locks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spooky Doors takes the use key pressed on its door for a drag (the mouse swings the door), so the click never reaches the
 * lock. On a door locked against this player the press is left alone: it's an ordinary use, which the lock answers (its
 * lockpicking game, or "locked up for the night") as on any locked door. Skipped without Spooky Doors.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.spookydoors.client.SpookyDoorsClient", remap = false)
abstract class SpookyDoorsDragLockMixin {
	/** GLFW's "pressed". */
	private static final int PRESS = 1;

	@Inject(method = "handleUseInput(I)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymoskingdoms$locked(int action, CallbackInfoReturnable<Boolean> cir) {
		if (action != PRESS) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		Locks.Fitted f = Locks.lockOn(mc.level, hit.getBlockPos());
		if (f != null && f.locked() && !mc.player.isCreative() && !Locks.holdsKey(mc.player, f.key())) {
			cir.setReturnValue(false);
		}
	}
}
