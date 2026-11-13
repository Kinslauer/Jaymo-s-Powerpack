package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.LairLand;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Birds in the land of a great lair make no sound ({@link LairLand#quiet}). */
@Mixin(Mob.class)
abstract class BirdSongMixin {
	@Inject(method = "playAmbientSound", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$quiet(CallbackInfo ci) {
		if (LairLand.quiet((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
