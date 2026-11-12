package com.jaymospowers.baubles.artifacts.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (Artifacts) Snowshoes: walk over powder snow as in leather boots. Rime walks on it too. */
@Mixin(PowderSnowBlock.class)
public abstract class ArtifactPowderSnowMixin {
	@ModifyReturnValue(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"))
	private static boolean jaymospowers$snowshoes(boolean original, Entity entity) {
		// (and Rime, the frost alien, walks on it as a snow golem would: Andy, 2026-11-12, "fix this")
		return original || ArtifactEffects.wears(entity, ArtifactBaubles.SNOWSHOES)
			|| entity instanceof net.minecraft.world.entity.player.Player player
				&& com.jaymospowers.power.xenoform.XenoformState.form(player) == com.jaymospowers.power.xenoform.XenoForms.RIME;
	}
}
