package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Audiences;
import com.jaymoskingdoms.Careers;
import com.jaymoskingdoms.GiftTales;
import com.jaymoskingdoms.HermitAlchemy;
import com.jaymoskingdoms.Hermits;
import com.jaymoskingdoms.Introductions;
import com.jaymoskingdoms.Languages;
import com.jaymoskingdoms.Rulers;
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
 * (0.7.220) "Talk about work" ({@link Careers#ANSWER}) shows only on a grown villager of a settlement.
 * (0.7.227) "Ask about their power" ({@link GiftTales#ANSWER}) shows only once you've seen their gift at work, until you ask.
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
		if ("main".equals(name) && !Careers.offersWork(villager)) {
			// (0.7.220) only a grown villager of a settlement talks work
			cir.getReturnValue().remove(Careers.ANSWER);
		}
		if ("main".equals(name) && !Hermits.is(villager)) {
			// (0.7.215) only the floating islands' hermits talk alchemy
			cir.getReturnValue().remove(HermitAlchemy.ANSWER);
		}
		if ("main".equals(name) && !com.jaymoskingdoms.ManaFolk.canAsk(villager)) {
			// (0.7.235) only one who works magic talks of it
			cir.getReturnValue().remove(com.jaymoskingdoms.ManaFolk.ANSWER);
		}
		if ("main".equals(name) && !GiftTales.canAsk(player, villager)) {
			// (0.7.227) only a gift you've seen at work, and only until they've told you
			cir.getReturnValue().remove(GiftTales.ANSWER);
		}
		if (!Languages.understands(player, villager)) {
			// (0.7.20) without their tongue, only what hands can say
			cir.getReturnValue().removeIf(answer -> !Languages.simpleDialogue(name, answer));
		}
	}
}
