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
 * Spooky Doors takes the use key pressed on its door for a drag (the mouse swings the door partway), so a click no longer
 * opened or shut it, and never reached the lock. Andy (2026-11-13): "can everyone also open doors with a click like the
 * old function?" A plain click is an ordinary use again: the door swings fully open or shut (Spooky Doors' own creak), and
 * a locked door answers as any locked door does (its lockpicking game, or "locked up for the night"). Sneaking while you
 * press use still drags the door partway, on a door with no lock fitted (sneaking at a lock is for its key). Skipped
 * without Spooky Doors.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.spookydoors.client.SpookyDoorsClient", remap = false)
abstract class SpookyDoorsDragLockMixin {
	/** GLFW's "pressed". */
	private static final int PRESS = 1;

	@Inject(method = "handleUseInput(I)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymoskingdoms$click(int action, CallbackInfoReturnable<Boolean> cir) {
		if (action != PRESS) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		if (!mc.player.isShiftKeyDown() || Locks.lockOn(mc.level, hit.getBlockPos()) != null) {
			// a click: the door opens or shuts whole, or its lock answers
			cir.setReturnValue(false);
		}
	}
}
