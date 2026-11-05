package com.jaymospowers.baubles.artifacts.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (Artifacts) The villager hat: villagers price their trades as if they liked you a good deal more. */
@Mixin(Villager.class)
public abstract class ArtifactVillagerMixin {
	@ModifyExpressionValue(method = "updateSpecialPrices", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/npc/villager/Villager;getPlayerReputation(Lnet/minecraft/world/entity/player/Player;)I"))
	private int jaymospowers$villagerHat(int original, Player player) {
		return ArtifactEffects.wears(player, ArtifactBaubles.VILLAGER_HAT) ? original + ArtifactEffects.HAT_REPUTATION : original;
	}
}
