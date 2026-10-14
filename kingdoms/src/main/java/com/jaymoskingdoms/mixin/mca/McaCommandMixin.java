package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Languages;
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
 * (0.5.29) And the main page's Quests button opens the villager's quests.
 * (0.7.19) A villager whose tongue you don't speak ({@link Languages}) only does what needs no words ({@link Languages#SIMPLE_COMMANDS}).
 */
@Mixin(VillagerCommandHandler.class)
abstract class McaCommandMixin extends EntityCommandHandler<VillagerEntityMCA> {
	McaCommandMixin(VillagerEntityMCA entity) {
		super(entity);
	}

	@Inject(method = "handle", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$job(ServerPlayer player, String command, CallbackInfoReturnable<Boolean> cir) {
		if (!Languages.SIMPLE_COMMANDS.contains(command) && !Languages.understands(player, this.entity)) {
			// (0.7.19) asking that of them takes words you don't share
			Languages.shrug(player, this.entity);
			cir.setReturnValue(true);
			return;
		}
		// (0.5.29) the main page's Quests button
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
