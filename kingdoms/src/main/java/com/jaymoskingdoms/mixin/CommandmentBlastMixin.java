package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Commandments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.535) A blast a player set off that breaks the ground breaks a commandment or two ({@link Commandments#blast}). */
@Mixin(ServerExplosion.class)
abstract class CommandmentBlastMixin {
	@Inject(method = "explode", at = @At("HEAD"))
	private void jaymoskingdoms$commandments(CallbackInfoReturnable<Integer> cir) {
		ServerExplosion self = (ServerExplosion)(Object)this;
		Commandments.blast(self.getIndirectSourceEntity(), self.getBlockInteraction() != Explosion.BlockInteraction.KEEP);
	}
}
