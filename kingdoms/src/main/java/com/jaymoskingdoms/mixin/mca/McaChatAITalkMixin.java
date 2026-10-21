package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Recollections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.chatAI.ChatAI;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.109) What a player says to a villager through MCA's chat AI is remembered ({@link Recollections}): the last few
 * things, for a month, so the next conversation (days later, or after a restart) can pick up where it left off.
 */
@Mixin(ChatAI.class)
abstract class McaChatAITalkMixin {
	@Inject(method = "answerAsync", at = @At("HEAD"))
	private static void jaymoskingdoms$remember(ServerPlayer player, VillagerEntityMCA villager, String msg,
		CallbackInfoReturnable<CompletableFuture<Optional<String>>> cir) {
		Recollections.talked(villager, player, msg);
	}
}
