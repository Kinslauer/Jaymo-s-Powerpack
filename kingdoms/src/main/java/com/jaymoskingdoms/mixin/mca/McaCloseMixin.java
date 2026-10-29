package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Languages;
import java.util.UUID;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.c2s.InteractionCloseRequest;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.348) Leaving a villager's talk menu: if you don't speak their tongue, they bid you goodbye in it ({@link Languages#farewell}). */
@Mixin(InteractionCloseRequest.class)
abstract class McaCloseMixin {
	@Shadow
	@Final
	private UUID villagerUUID;

	@Inject(method = "handleServer", at = @At("HEAD"))
	private void jaymoskingdoms$farewell(ServerPlayer player, CallbackInfo ci) {
		if (player.level().getEntity(this.villagerUUID) instanceof VillagerEntityMCA villager) {
			Languages.farewell(player, villager, villager.getVoicePitch());
		}
	}
}
