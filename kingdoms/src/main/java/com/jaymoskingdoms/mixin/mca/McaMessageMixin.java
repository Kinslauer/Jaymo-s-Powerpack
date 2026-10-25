package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Languages;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.network.s2c.VillagerMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.20) What an MCA villager says to a player who doesn't speak their tongue ({@link Languages}) reaches them as
 * words they don't know (the same gibberish our own speech bubbles show).
 * (0.7.203) So does the line at the top of their talk menu (the menu itself offers a foreigner only gestures).
 * (0.7.67) To a masked player ({@link com.jaymoskingdoms.Identities}), the player's names are swapped for the costume's.
 */
@Mixin(Network.class)
abstract class McaMessageMixin {
	/** (0.7.203) How far round a player to look for the villager they're talking to. */
	@Unique
	private static final double TALK_RANGE = 16;
	/** Set while the gibberish is sent on (so it isn't looked at again). */
	@Unique
	private static boolean jaymoskingdoms$garbling;

	@Inject(method = "sendToPlayer", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$tongue(HandleablePayload payload, ServerPlayer player, CallbackInfo ci) {
		if (!jaymoskingdoms$garbling && payload instanceof InteractionDialogueQuestionResponse question) {
			// (0.7.203) the line at the top of their talk menu, too
			Entity speaker = jaymoskingdoms$talkingTo(player);
			if (speaker != null && !Languages.understands(player, speaker)) {
				ci.cancel();
				jaymoskingdoms$garbling = true;
				try {
					Network.sendToPlayer(new InteractionDialogueQuestionResponse(Languages.heard(player, speaker, question.questionText()), question.silent()), player);
				} finally {
					jaymoskingdoms$garbling = false;
				}
			}
			return;
		}
		if (jaymoskingdoms$garbling || !(payload instanceof VillagerMessage original)) {
			return;
		}
		// (0.7.67) to a masked player they speak to the costume: their names (MCA's and their own) give way to its name
		VillagerMessage message = original;
		net.minecraft.network.chat.Component hidden = com.jaymoskingdoms.Identities.withoutName(player, original.message(),
			net.conczin.mca.entity.ai.Messenger.getName(player));
		if (hidden != original.message()) {
			message = new VillagerMessage(original.prefix(), hidden, original.uuid());
		}
		Entity speaker = player.level().getEntity(message.uuid());
		// (0.7.51) what they say to you, everyone near sees over their head too (in their own understanding of it)
		if (speaker != null && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
			com.jaymoskingdoms.Speech.overheard(level, speaker, player, message.message(), com.jaymoskingdoms.Speech.WHITE);
		}
		if (speaker == null || Languages.understands(player, speaker)) {
			if (message != original) {
				ci.cancel();
				jaymoskingdoms$garbling = true;
				try {
					Network.sendToPlayer(message, player);
				} finally {
					jaymoskingdoms$garbling = false;
				}
			}
			return;
		}
		ci.cancel();
		jaymoskingdoms$garbling = true;
		try {
			Network.sendToPlayer(new VillagerMessage(message.prefix(), Languages.heard(player, speaker, message.message()), message.uuid()), player);
		} finally {
			jaymoskingdoms$garbling = false;
		}
	}

	/** (0.7.203) The MCA villager whose talk menu {@code player} has open (MCA's question line names no speaker). */
	@Unique
	private static Entity jaymoskingdoms$talkingTo(ServerPlayer player) {
		for (VillagerEntityMCA villager : player.level().getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(TALK_RANGE))) {
			if (villager.getInteractions().getInteractingPlayer().orElse(null) == player) {
				return villager;
			}
		}
		return null;
	}
}
