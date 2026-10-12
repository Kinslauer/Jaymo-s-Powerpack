package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.McaCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.5.21) MCA's Professions menu: its builder, farmer, miner and woodcutter buttons are the settlement's to answer ({@link McaCompat#promote}).
 * (0.5.26) And the main page's Quests button opens the villager's quests.
 */
@Mixin(VillagerCommandHandler.class)
abstract class McaCommandMixin extends EntityCommandHandler<VillagerEntityMCA> {
	McaCommandMixin(VillagerEntityMCA entity) {
		super(entity);
	}

	@Inject(method = "handle", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$job(ServerPlayer player, String command, CallbackInfoReturnable<Boolean> cir) {
		// (0.5.26) the main page's Quests button
		if (command.equals("quests")) {
			com.jaymoskingdoms.Quests.openLater(player, this.entity);
			cir.setReturnValue(true);
			return;
		}
		if (command.startsWith("profession.") && McaCompat.promote(player, this.entity, command.substring("profession.".length()))) {
			cir.setReturnValue(true);
		}
	}
}
