package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.ChatPrompts;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.chatAI.ChatAIContext;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.109) MCA's villager chat AI (off unless an op runs {@code /mca chatAI default}) is told what Kingdoms knows: the
 * villager's realm, ruler, wars and neighbours, how the realm takes the player, and what the villager remembers of
 * them ({@link ChatPrompts}).
 */
@Mixin(ChatAIContext.class)
abstract class McaChatAIMixin {
	@Inject(method = "appendPrompts", at = @At("TAIL"))
	private static void jaymoskingdoms$realm(StringBuilder prompt, ServerPlayer player, VillagerEntityMCA villager, Village village, CallbackInfo ci) {
		ChatPrompts.append(prompt, player, villager);
	}
}
