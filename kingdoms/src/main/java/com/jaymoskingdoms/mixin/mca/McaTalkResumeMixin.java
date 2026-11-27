package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Conversations;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.Questioning;
import java.util.UUID;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.c2s.InteractionDialogueInitMessage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Andy (2026-11-27): "conversations end prematurely it should go back to the wheel". When a talk goes back to the wheel
 * after a reply ({@link Conversations#again}), the villager's menu opens again and asks for the talk as a fresh menu
 * does; the talk is already under way, so it goes straight to the wheel (MCA's main answers) with no new greeting.
 * One held down for questioning still gets only the questions (their own opening, {@code McaDialogueMixin}).
 */
@Mixin(InteractionDialogueInitMessage.class)
abstract class McaTalkResumeMixin {
	@Shadow
	@Final
	private UUID villagerUUID;

	@Inject(method = "handleServer", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$goesOn(ServerPlayer player, CallbackInfo ci) {
		if (player.level().getEntity(this.villagerUUID) instanceof VillagerEntityMCA villager && Conversations.takeResumed(player, villager)
			&& !Questioning.active(player, villager) && McaCompat.talkMain(villager, player)) {
			ci.cancel();
		}
	}
}
