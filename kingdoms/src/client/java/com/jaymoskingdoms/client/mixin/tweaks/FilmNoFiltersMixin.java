package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.films.BossIntroClient;
import com.jaymoskingdoms.client.films.FarmWakeFilmClient;
import com.jaymoskingdoms.client.films.LoreFilmClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No screen filter is drawn over a film (Andy, as a Hollow: "the filter kicked in over my film. it should disable all
 * filters while viewing"). Every filter (darkvision, blood sight, and the pack's other mods' own) is asked for in
 * {@code update}; this empties the list just before the frame reads it, so it covers them all, whoever asked.
 */
@Mixin(GameRenderer.class)
abstract class FilmNoFiltersMixin {
	@Shadow public abstract java.util.List<Identifier> getRequestedPostEffects();

	@Inject(method = "extract", at = @At("HEAD"))
	private void jaymoskingdoms$filmNoFilters(DeltaTracker delta, boolean advance, CallbackInfo ci) {
		if (LoreFilmClient.locked() || BossIntroClient.locked() || FarmWakeFilmClient.running()) {
			this.getRequestedPostEffects().clear();
		}
	}
}
