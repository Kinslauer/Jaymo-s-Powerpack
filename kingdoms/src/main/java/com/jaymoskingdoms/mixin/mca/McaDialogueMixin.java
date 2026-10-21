package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.EmberHugs;
import com.jaymoskingdoms.Hermits;
import com.jaymoskingdoms.Languages;
import com.jaymoskingdoms.Recollections;
import com.jaymoskingdoms.SkinTones;
import com.jaymoskingdoms.Tails;
import com.jaymoskingdoms.VillagerHeritage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.Dialogues;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 * (0.7.109) What they remember of you ({@link Recollections}): how a chat, joke, story, hug, flirt or kiss went is
 * remembered; one who bears you too much ill will won't have any of it; and in a chat they bring up what they
 * remember and haven't said yet.
 * (0.7.102) A hug where one of you is Emberborn and the other has no fire resistance ({@link EmberHugs}): whoever isn't
 * safe is burned and pulls away, and the villager says so.
 */
@Mixin(Dialogues.class)
abstract class McaDialogueMixin {
	/** (0.7.109) The talk that's remembered by how it went (MCA's question for each), and the main menu's answers a grudge refuses. */
	@Unique
	private static final Set<String> SOCIAL = Set.of("chat", "joke", "story", "hug", "flirt", "kiss");
	/** (0.7.109) "villager/player" → their hearts as an answer to one of those began (to see how it went). */
	@Unique
	private static final Map<String, Integer> BEFORE = new HashMap<>();

	@Inject(method = "selectAnswer", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$homeTalk(VillagerEntityMCA villager, ServerPlayer player, String question, String answer, CallbackInfo ci) {
		if (!Languages.simpleDialogue(question, answer) && !Languages.understands(player, villager)) {
			// (0.7.20) that takes words you don't share
			Languages.shrug(player, villager);
			villager.getInteractions().stopInteracting();
			ci.cancel();
			return;
		}
		if ("main".equals(question) && SOCIAL.contains(answer) && Recollections.refuses(villager, player)) {
			// (0.7.109) they remember too much ill of you for that
			villager.sendChatMessage(Component.translatable("recall.jaymoskingdoms.refuse." + villager.getRandom().nextInt(Recollections.REFUSALS),
				Recollections.nameTo(player, villager)), player);
			villager.getInteractions().stopInteracting();
			ci.cancel();
			return;
		}
		if ("main".equals(question) && "hug".equals(answer)) {
			// (0.7.102) an Emberborn is too hot to hold without fire resistance (either of you)
			LivingEntity burned = EmberHugs.burned(player, villager);
			if (burned != null) {
				EmberHugs.burn((ServerLevel) villager.level(), burned == player ? villager : player, burned);
				villager.sendChatMessage(player, burned == player ? "dialogue.chat.jaymoskingdoms.ember_hug_you" : "dialogue.chat.jaymoskingdoms.ember_hug_me");
				villager.getInteractions().stopInteracting();
				ci.cancel();
				return;
			}
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
		if (villager.getRandom().nextFloat() < Recollections.CHAT_REMARK_CHANCE) {
			// (0.7.109) something they remember of you, and haven't said yet
			Component remark = Recollections.remark(villager, player, villager.getRandom());
			if (remark != null) {
				villager.sendChatMessage(remark, player);
				villager.getInteractions().stopInteracting();
				ci.cancel();
				return;
			}
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

	/** (0.7.109) Note their hearts as a chat, joke, story, hug, flirt or kiss begins (after anything above has had its say). */
	@Inject(method = "selectAnswer", at = @At("HEAD"))
	private void jaymoskingdoms$before(VillagerEntityMCA villager, ServerPlayer player, String question, String answer, CallbackInfo ci) {
		if (SOCIAL.contains(question)) {
			BEFORE.put(villager.getUUID() + "/" + player.getUUID(), villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts());
		}
	}

	/** (0.7.109) And how it went: they remember it. */
	@Inject(method = "selectAnswer", at = @At("RETURN"))
	private void jaymoskingdoms$after(VillagerEntityMCA villager, ServerPlayer player, String question, String answer, CallbackInfo ci) {
		if (!SOCIAL.contains(question)) {
			return;
		}
		Integer before = BEFORE.remove(villager.getUUID() + "/" + player.getUUID());
		if (before != null) {
			Recollections.socialised(villager, player, question, villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts() - before);
		}
	}
}
