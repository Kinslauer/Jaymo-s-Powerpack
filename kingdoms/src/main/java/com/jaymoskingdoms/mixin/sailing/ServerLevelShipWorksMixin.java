package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.ShipWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A brewing stand aboard a ship ({@link ShipWorks}) that finishes a brew is heard where it is on her, not at its old place. */
@Mixin(ServerLevel.class)
abstract class ServerLevelShipWorksMixin {
	@Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$shipWorkEvent(@Nullable Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
		if (!ShipWorks.ours(pos)) {
			return;
		}
		ci.cancel();
		Vec3 at = ShipWorks.where();
		if (at != null) {
			ServerLevel self = (ServerLevel)(Object)this;
			ShipWorks.outside(() -> self.levelEvent(source, type, BlockPos.containing(at), data));
		}
	}
}
