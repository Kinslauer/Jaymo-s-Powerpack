package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Identities;
import java.util.UUID;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * (0.7.67) A player masked by Jaymo's Powers' costume is a stranger to MCA's villagers: what a villager remembers of
 * them (hearts, fatigue, last seen) is looked up and kept under the costume's own stand-in id ({@link Identities#standIn}),
 * so they start at 0 hearts, and the hearts of the bare face stay as they were till the mask comes off. Both sides
 * (the interaction screen reads the same memories), by Kingdoms' synced {@code masked_as}.
 */
@Mixin(VillagerBrain.class)
abstract class McaMemoriesMixin {
	@Redirect(method = "getMemoriesForPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getUUID()Ljava/util/UUID;"))
	private UUID jaymoskingdoms$costume(Player player) {
		String mask = Identities.maskedAs(player);
		return mask == null ? player.getUUID() : Identities.standIn(mask);
	}
}
