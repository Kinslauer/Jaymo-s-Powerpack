package com.jaymoskingdoms.mixin.mca;

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
 * (0.7.138) "Seek an audience" ({@link Rulers#ANSWER}) shows only to a villager on a throne.
 * (0.7.180) "Ask their name" ({@link Introductions#ANSWER}) shows only while you don't know it.
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
		if ("main".equals(name) && !Rulers.is(villager)) {
			// (0.7.138) only a ruler holds court
			cir.getReturnValue().remove(Rulers.ANSWER);
		}
		if ("main".equals(name) && Introductions.knows(player, villager)) {
			// (0.7.180) you know their name already
			cir.getReturnValue().remove(Introductions.ANSWER);
		}
		if (!Languages.understands(player, villager)) {
			// (0.7.20) without their tongue, only what hands can say
			cir.getReturnValue().removeIf(answer -> !Languages.simpleDialogue(name, answer));
		}
	}
}
