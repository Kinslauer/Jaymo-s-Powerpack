package com.jaymoskingdoms.mixin.fts;

import com.jaymoskingdoms.inhabitants.fts.NehemothPart;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The server finds a Nehemoth's part by the id a player's attack or use names, as it finds the ender dragon's. */
@Mixin(ServerLevel.class)
abstract class ServerLevelNehemothPartsMixin {
	@Inject(method = "getEntityOrPart", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$nehemothPart(int id, CallbackInfoReturnable<Entity> cir) {
		if (cir.getReturnValue() == null) {
			NehemothPart part = NehemothPart.byId((ServerLevel)(Object)this, id);
			if (part != null) {
				cir.setReturnValue(part);
				return;
			}
			com.jaymoskingdoms.dragons.DragonPart dragonPart = com.jaymoskingdoms.dragons.DragonPart.byId((ServerLevel)(Object)this, id);
			if (dragonPart != null) {
				cir.setReturnValue(dragonPart);
			}
		}
	}
}
