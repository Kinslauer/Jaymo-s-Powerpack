package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Audiences;
import com.jaymoskingdoms.Careers;
import com.jaymoskingdoms.GiftTales;
import com.jaymoskingdoms.HermitAlchemy;
import com.jaymoskingdoms.Hermits;
import com.jaymoskingdoms.Introductions;
import com.jaymoskingdoms.Languages;
import com.jaymoskingdoms.Rulers;
import com.jaymoskingdoms.SharedHistory;
import com.jaymoskingdoms.ShipTalk;
import com.jaymoskingdoms.Tails;
import com.jaymoskingdoms.VillagerHeritage;
import java.util.List;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.10) "Explain your tail" ({@link Tails#ANSWER}, added to MCA's {@code main} menu by our dialogue data) shows only
 * to a Warrior-born Z Fighter who hasn't yet told this villager, once they're close enough friends.
 * (0.7.138) "Seek an audience" ({@link Rulers#ANSWER}) shows only to a villager on a throne, (0.7.184) or a realm's guard.
 * (0.7.180) "Ask their name" ({@link Introductions#ANSWER}) shows only while you don't know it; (0.7.204) "Offer a
 * handshake" ({@link Introductions#HANDSHAKE}) too.
 * (0.7.215) "Ask about alchemy" ({@link HermitAlchemy#ANSWER}) shows only on a floating island's hermit.
 * (0.7.220) "Talk about work" ({@link Careers#ANSWER}) shows only on a grown villager of a settlement, (0.7.270) or one of a
 * ship's crew ({@link ShipTalk}).
 * (0.7.319) "Talk about work" also shows on Powers' Z Fighter characters ({@link com.jaymoskingdoms.ZFighterFolk}); "Talk
 * about training" only on a Z Fighter master.
 * (0.7.384) "Ask about the stars" ({@link com.jaymoskingdoms.StarFolk#ANSWER}) shows only on a hermit or a stargazer.
 * (0.7.227) "Ask about their power" ({@link GiftTales#ANSWER}) shows only once you've seen their gift at work, until you ask.
 * (0.7.254) "Thank them", "Apologise", "Clear the air" and "Ask how they are" ({@link SharedHistory}) show only while
 * your history with them has something to take up; (0.7.380) only the one for the latest of it ({@link SharedHistory#current}).
 * (0.7.340) "Speak of the rescue" ({@link com.jaymoskingdoms.Captives#ANSWER}) shows only on a guard of a town that owes you a favour.
 * (0.7.20) To a foreigner ({@link Languages}) a menu offers only what needs no words ({@link Languages#simpleDialogue}).
 */
@Mixin(Question.class)
abstract class McaAnswersMixin {
	@Inject(method = "getValidAnswers", at = @At("RETURN"))
	private void jaymoskingdoms$tail(ServerPlayer player, VillagerEntityMCA villager, CallbackInfoReturnable<List<String>> cir) {
		String name = ((Question)(Object)this).getName();
		if ("main".equals(name) && !Tails.canExplain(player, villager)) {
			cir.getReturnValue().remove(Tails.ANSWER);
		}
		if ("main".equals(name) && !VillagerHeritage.hasTale(villager)) {
			// a plain Heartlander has no blood to tell of
			cir.getReturnValue().remove(VillagerHeritage.ANSWER);
		}
		if ("main".equals(name) && !Rulers.is(villager) && !Audiences.canAsk(villager)) {
			// (0.7.138) only a ruler holds court; (0.7.184) a guard takes you to them
			cir.getReturnValue().remove(Rulers.ANSWER);
		}
		if ("main".equals(name) && Introductions.knows(player, villager)) {
			// (0.7.180) you know their name already
			cir.getReturnValue().remove(Introductions.ANSWER);
			cir.getReturnValue().remove(Introductions.HANDSHAKE);
		}
		if ("main".equals(name) && !Careers.offersWork(villager) && !ShipTalk.offersWork(villager) && !com.jaymoskingdoms.ZFighterFolk.is(villager)) {
			// (0.7.220) only a grown villager of a settlement talks work; (0.7.270) or one of a ship's crew
			cir.getReturnValue().remove(Careers.ANSWER);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.ZFighterFolk.trains(villager)) {
			// (0.7.319) only a Z Fighter master talks training
			cir.getReturnValue().remove(com.jaymoskingdoms.ZFighterFolk.TRAINING);
		}
		if ("main".equals(name) && !Hermits.is(villager)) {
			// (0.7.215) only the floating islands' hermits talk alchemy
			cir.getReturnValue().remove(HermitAlchemy.ANSWER);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.StarFolk.canAsk(villager)) {
			// (0.7.384) only a floating island's hermit or a stargazer talks of the stars (and sells their charts)
			cir.getReturnValue().remove(com.jaymoskingdoms.StarFolk.ANSWER);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.ManaFolk.canAsk(villager)) {
			// (0.7.244) only one who works magic talks of it
			cir.getReturnValue().remove(com.jaymoskingdoms.ManaFolk.ANSWER);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.ValorFolk.canAsk(player, villager)) {
			// (0.7.361) only a Valor you've seen
			cir.getReturnValue().remove(com.jaymoskingdoms.ValorFolk.TALE);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.ValorFolk.canSpar(player, villager)) {
			// (0.7.361) only one who's told you their tale, once you have Valor of your own
			cir.getReturnValue().remove(com.jaymoskingdoms.ValorFolk.SPAR);
		}
		if ("main".equals(name) && !GiftTales.canAsk(player, villager)) {
			// (0.7.227) only a gift you've seen at work, and only until they've told you
			cir.getReturnValue().remove(GiftTales.ANSWER);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.Accusations.canAccuse(player, villager)) {
			// (0.7.332) only a priest, with someone here to accuse
			cir.getReturnValue().remove(com.jaymoskingdoms.Accusations.ANSWER);
		}
		if ("main".equals(name)) {
			// (0.7.254) only what your history with them calls for; (0.7.380) and of that, only what answers the latest of it
			String current = SharedHistory.current(player, villager);
			for (String answer : List.of(SharedHistory.THANK, SharedHistory.APOLOGISE, SharedHistory.AIR, SharedHistory.ASK_AFTER)) {
				if (!answer.equals(current)) {
					cir.getReturnValue().remove(answer);
				}
			}
		}
		if ("main".equals(name) && !com.jaymoskingdoms.Captives.canSpeakOf(player, villager)) {
			// (0.7.340) only a guard of a town that owes you for one you brought home
			cir.getReturnValue().remove(com.jaymoskingdoms.Captives.ANSWER);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.forms.GuardDrills.canOffer(player, villager)) {
			// (0.7.381) only a veteran guard sets drills
			cir.getReturnValue().remove(com.jaymoskingdoms.forms.GuardDrills.ANSWER);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.forms.Schools.canTalk(player, villager)) {
			// (0.7.384) only a school master talks of the school
			cir.getReturnValue().remove(com.jaymoskingdoms.forms.Schools.ANSWER);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.forms.WizardTrials.canOffer(player, villager)) {
			// (0.7.384) only a court wizard (or a realm's necromancer) sets trials
			cir.getReturnValue().remove(com.jaymoskingdoms.forms.WizardTrials.ANSWER);
		}
		if (!Languages.understands(player, villager)) {
			// (0.7.20) without their tongue, only what hands can say
			cir.getReturnValue().removeIf(answer -> !Languages.simpleDialogue(name, answer));
		}
	}
}
