package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.BlastFright;
import com.jaymoskingdoms.BlastMend;
import com.jaymoskingdoms.equestrian.Equestrian;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.34) Horses near a blast may shy ({@link Equestrian#blast}); (0.7.576) villagers who hear it run ({@link BlastFright}). */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {
	@Inject(method = "explode", at = @At("HEAD"))
	private void jaymoskingdoms$frighten(CallbackInfoReturnable<Integer> cir) {
		ServerExplosion self = (ServerExplosion)(Object)this;
		Equestrian.blast(self.level(), self.center(), self.radius());
		BlastFright.heard(self.level(), self.center());
	}

	/** (village mods 2) The blocks a blast is about to break: a settlement's builders note them to put back, or the wild ground heals. */
	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void jaymoskingdoms$note(List<BlockPos> targets, CallbackInfo ci) {
		ServerExplosion self = (ServerExplosion)(Object)this;
		BlastMend.noted(self.level(), self.center(), targets, self.getIndirectSourceEntity() instanceof Player);
	}
}
