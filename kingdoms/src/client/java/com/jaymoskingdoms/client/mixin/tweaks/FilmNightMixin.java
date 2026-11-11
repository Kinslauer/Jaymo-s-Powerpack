package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.films.LoreFilmClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A lore film is set at night ({@link LoreFilmClient#nightTime}): while one plays, this game's clocks read the hour the
 * film is set at, whatever the hour is (Andy: "The fight should take place at night"). Only what this game draws
 * changes; the world's own time is untouched.
 */
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
abstract class FilmNightMixin {
	@Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$filmNight(CallbackInfoReturnable<Long> cir) {
		if (LoreFilmClient.locked()) {
			cir.setReturnValue(LoreFilmClient.nightTime(cir.getReturnValue()));
		}
	}
}
