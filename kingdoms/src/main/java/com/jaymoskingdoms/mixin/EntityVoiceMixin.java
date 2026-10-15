package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Languages;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.61) A villager's spoken voice line (MCA's hello, yes, no...) is heard as a grunt by anyone who doesn't speak their tongue ({@link Languages#voice}). */
@Mixin(Entity.class)
abstract class EntityVoiceMixin {
	@Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$voice(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
		if (Languages.voice((Entity)(Object)this, sound, volume, pitch)) {
			ci.cancel();
		}
	}
}
