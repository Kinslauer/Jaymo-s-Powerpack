package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Hermits;
import com.jaymoskingdoms.SkinTones;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.Dialogues;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.44) Chatting with a newcomer or an immigrant: now and then they talk about where their family is from
 * ({@link SkinTones#ORIGIN}) instead of MCA's own small talk. Said the way MCA says a line with no follow-up question.
 * (0.5.60) A floating island's hermit ({@link Hermits}) mostly talks of why they live up there.
 */
@Mixin(Dialogues.class)
abstract class McaDialogueMixin {
	@Inject(method = "selectAnswer", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$homeTalk(VillagerEntityMCA villager, ServerPlayer player, String question, String answer, CallbackInfo ci) {
		if (!"chat.topic".equals(question)) {
			return;
		}
		if (Hermits.is(villager) && villager.getRandom().nextFloat() < Hermits.CHAT_CHANCE) {
			// (0.5.60) a floating island's hermit talks of why they're up there
			villager.sendChatMessage(player, "dialogue.chat.jaymoskingdoms.hermit_" + Hermits.story(villager));
			villager.getInteractions().stopInteracting();
			ci.cancel();
			return;
		}
		String kind = SkinTones.originTalk(villager);
		if (kind != null && villager.getRandom().nextFloat() < SkinTones.ORIGIN_TALK_CHANCE) {
			villager.sendChatMessage(player, "dialogue.chat.jaymoskingdoms." + kind, SkinTones.originLand(villager));
			villager.getInteractions().stopInteracting();
			ci.cancel();
		}
	}
}
