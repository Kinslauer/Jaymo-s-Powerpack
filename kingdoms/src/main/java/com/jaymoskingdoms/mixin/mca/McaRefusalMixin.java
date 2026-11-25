package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.RefusalHints;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.Dialogues;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Refusal hints ({@link RefusalHints}): when MCA's dice turn a chat, joke, story, flirt, hug, kiss, greeting, apology or
 * ask for rumours into a no, the villager follows their line with why ({@link #jaymoskingdoms$refusal}): tired of talk, not
 * close enough, a bad mood, their nature, a tale told before or not believed, or only bad luck. Asking a wanderer to
 * stay is left out: MCA's lines for that already say why (crowded, asked already, an adventurer at heart).
 */
@Mixin(Dialogues.class)
abstract class McaRefusalMixin {
	@WrapOperation(method = "selectAnswer", at = @At(value = "INVOKE",
		target = "Lnet/conczin/mca/resources/data/dialogue/Actions;trigger(Lnet/conczin/mca/entity/VillagerEntityMCA;Lnet/minecraft/server/level/ServerPlayer;)V"))
	private void jaymoskingdoms$why(Actions actions, VillagerEntityMCA villager, ServerPlayer player, Operation<Void> original,
		@Local(argsOnly = true, ordinal = 0) String question, @Local(argsOnly = true, ordinal = 1) String answer) {
		RefusalHints.Cause why = "main".equals(question) && "stay".equals(answer) ? null
			: jaymoskingdoms$refusal(villager, player, question, answer, actions);
		original.call(actions, villager, player);
		if (why != null) {
			RefusalHints.hint(player, villager, why);
		}
	}

	/** MCA's chance keys that, pushing a no or missing from a yes, name a reason ({@link #jaymoskingdoms$refusal}). */
	@Unique
	private static final java.util.Set<String> MISSED_KEYS = java.util.Set.of("hearts", "hearts_min", "advancement");

	/**
	 * ({@link #jaymoskingdoms$why}) MCA picked {@code chosen}, one of answer {@code answer}'s results to {@code question}: if it
	 * was a no (a negative result, or one a dislike of you weighed toward), why, read from MCA's own chances for each
	 * result. The heaviest reason wins: what weighed toward the no (their fatigue, mood, personality, a story already
	 * told, your hearts) or what a yes needed and you hadn't (hearts, a deed your tale claimed). With only the dice
	 * against you, {@link RefusalHints.Cause#LUCK}. Null if it wasn't a no.
	 */
	@Unique
	private static RefusalHints.@Nullable Cause jaymoskingdoms$refusal(VillagerEntityMCA v, ServerPlayer p, String question, @Nullable String answer,
		net.conczin.mca.resources.data.dialogue.Actions chosen) {
		net.conczin.mca.resources.data.dialogue.Question q = net.conczin.mca.resources.Dialogues.getInstance().getQuestion(question);
		net.conczin.mca.resources.data.dialogue.Answer a = q == null ? null : q.getAnswer(answer);
		if (a == null) {
			return null;
		}
		String best = null;
		int most = 0;
		boolean no = chosen.isNegative();
		for (net.conczin.mca.resources.data.dialogue.Result r : a.getResults()) {
			if (r.getActions() == chosen) {
				for (net.conczin.mca.resources.data.SerializablePair<String, Integer> s : r.getChances(v, p).getSummands()) {
					if ("hearts_max".equals(s.left()) && s.right() > 0) {
						// (only a no weighs a dislike of you: MCA's rumours and the like have no "negative" mark)
						no = true;
					}
					if (!"base".equals(s.left()) && s.right() > most) {
						most = s.right();
						best = s.left();
					}
				}
			} else if (r.getActions().isPositive()) {
				for (net.conczin.mca.entity.interaction.InteractionPredicate c : r.getConditions()) {
					java.util.List<String> keys = c.getConditionKeys();
					if (keys.isEmpty() || !MISSED_KEYS.contains(keys.getFirst()) || c.getChance() <= most || c.test(v, p) > 0.0F) {
						continue;
					}
					most = c.getChance();
					best = keys.getFirst();
				}
			}
		}
		if (!no) {
			return null;
		}
		if (best == null) {
			return RefusalHints.Cause.LUCK;
		}
		return switch (best) {
			case "fatigue" -> RefusalHints.Cause.TIRED;
			case "hearts", "hearts_min", "hearts_max", "heartsBonus" -> RefusalHints.hearts(p, v);
			case "mood", "mood_group" -> RefusalHints.Cause.MOOD;
			case "personality", "trait" -> RefusalHints.Cause.NATURE;
			case "memory" -> RefusalHints.Cause.REPEAT;
			case "advancement" -> RefusalHints.Cause.DOUBT;
			case "age_group" -> RefusalHints.Cause.YOUNG;
			default -> RefusalHints.Cause.LUCK;
		};
	}
}
