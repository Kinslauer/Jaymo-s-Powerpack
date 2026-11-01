package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Introductions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.180) On a client, a villager whose name you haven't asked ({@link Introductions}) goes by what they are
 * ("Villager", their trade) wherever the client names them: Jade, MCA's screens. (0.7.472) A goblin or an orc goes by
 * "Goblin" or "Orc", never MCA's "Male Villager".
 */
@Mixin(VillagerEntityMCA.class)
abstract class McaIntroNameMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$unasked(CallbackInfoReturnable<Component> cir) {
		VillagerEntityMCA self = (VillagerEntityMCA)(Object)this;
		if (self.level().isClientSide() && !Introductions.knownHere(self)) {
			cir.setReturnValue(Introductions.what(self));
		}
	}
}
