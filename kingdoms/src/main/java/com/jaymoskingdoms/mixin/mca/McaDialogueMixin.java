package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Hermits;
import com.jaymoskingdoms.Languages;
import com.jaymoskingdoms.SkinTones;
import com.jaymoskingdoms.Tails;
import com.jaymoskingdoms.VillagerHeritage;
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
 * (0.5.62) A floating island's hermit ({@link Hermits}) mostly talks of why they live up there.
 * (0.7.10) "Explain your tail" ({@link Tails}): the villager understands the Z Fighter is no monster.
 * (0.7.20) A foreigner ({@link Languages}) gets a shrug for anything that takes words ({@link Languages#simpleDialogue}).
 * "Ask about their blood" ({@link VillagerHeritage#ANSWER}), and now and then in a chat: what blood they are, and a
 * Warrior-born why there's no tail to see.
 */
@Mixin(Dialogues.class)
abstract class McaDialogueMixin {
	@Inject(method = "selectAnswer", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$homeTalk(VillagerEntityMCA villager, ServerPlayer player, String question, String answer, CallbackInfo ci) {
		if (!Languages.simpleDialogue(question, answer) && !Languages.understands(player, villager)) {
			// (0.7.20) that takes words you don't share
			Languages.shrug(player, villager);
			villager.getInteractions().stopInteracting();
			ci.cancel();
			return;
		}
		if ("main".equals(question) && Tails.ANSWER.equals(answer)) {
			// (0.7.10) a Z Fighter tells them about the tail: they understand, and fear it no more
			if (Tails.canExplain(player, villager)) {
				villager.sendChatMessage(player, "dialogue.chat.jaymoskingdoms.tail_explained");
				Tails.explain(player, villager);
			}
			villager.getInteractions().stopInteracting();
			ci.cancel();
			return;
		}
		if ("main".equals(question) && VillagerHeritage.ANSWER.equals(answer)) {
			// asked about their blood: what they are (a Warrior-born: why there's no tail to see)
			String blood = VillagerHeritage.talkKey(villager);
			if (blood != null) {
				villager.sendChatMessage(player, blood);
			}
			villager.getInteractions().stopInteracting();
			ci.cancel();
			return;
		}
		if (!"chat.topic".equals(question)) {
			return;
		}
		if (villager.getRandom().nextFloat() < VillagerHeritage.chatChance(villager)) {
			// now and then they talk of their blood
			String blood = VillagerHeritage.talkKey(villager);
			if (blood != null) {
				villager.sendChatMessage(player, blood);
				villager.getInteractions().stopInteracting();
				ci.cancel();
				return;
			}
		}
		if (Hermits.is(villager) && villager.getRandom().nextFloat() < Hermits.CHAT_CHANCE) {
			// (0.5.62) a floating island's hermit talks of why they're up there
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
