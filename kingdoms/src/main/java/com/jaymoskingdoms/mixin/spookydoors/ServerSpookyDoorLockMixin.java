package com.jaymoskingdoms.mixin.spookydoors;

import com.jaymoskingdoms.Locks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spooky Doors (BlayTheNinth, All Rights Reserved; nothing of it copied, only hooked) swings a door on the server through
 * one method, whoever moves it: a player dragging it with the use key held or walking into it (its own packet, never a
 * click), a mob pushing past, a downed player's struggle, another mod's call. None of that met the lock (Andy, 2026-11-13:
 * "i was able to open a locked door without a pick"). A swing further open is refused when the door is locked against
 * whoever swings it ({@link Locks#pushBarred}), and the clients are told the door hasn't moved. Swinging it shut is never
 * stopped. Skipped without Spooky Doors.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.spookydoors.core.ServerSpookyDoor", remap = false)
abstract class ServerSpookyDoorLockMixin {
	@Shadow
	public abstract ServerLevel level();

	@Shadow
	public abstract BlockPos pos();

	@Shadow
	public abstract void syncToClients();

	@Inject(method = "operate(Lnet/minecraft/world/entity/Entity;FF)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskingdoms$locked(@Nullable Entity by, float from, float to, CallbackInfo ci) {
		if (to > from && Locks.pushBarred(level(), pos(), by)) {
			syncToClients();
			ci.cancel();
		}
	}
}
