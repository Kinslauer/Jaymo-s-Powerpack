package com.jaymospowers.client.mixin;

import com.jaymospowers.core.wounds.Mortality;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.20.83) Andy: "can you change that button to say "reincarnate" instead of Respawn". A death that ends the character
 * ({@link Mortality#FINAL}, told to its player) offers to reincarnate: what follows is a new life from nothing
 * ({@link Mortality#endLife}). Checked each tick as well, in case the word of it lands after the screen opens.
 */
@Mixin(DeathScreen.class)
abstract class DeathScreenReincarnateMixin {
	@Shadow
	@Final
	private List<Button> exitButtons;

	@Shadow
	@Final
	private LocalPlayer player;

	@Inject(method = "init", at = @At("TAIL"))
	private void jaymospowers$reincarnateOnInit(CallbackInfo ci) {
		jaymospowers$relabel();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void jaymospowers$reincarnateOnTick(CallbackInfo ci) {
		jaymospowers$relabel();
	}

	@Unique
	private void jaymospowers$relabel() {
		if (player == null || exitButtons == null) {
			return;
		}
		boolean ending = player.hasAttached(Mortality.FINAL);
		for (Button button : exitButtons) {
			if (button.getMessage().getContents() instanceof TranslatableContents words) {
				if (ending && "deathScreen.respawn".equals(words.getKey())) {
					button.setMessage(Component.translatable("deathScreen.jaymospowers.reincarnate"));
				} else if (!ending && "deathScreen.jaymospowers.reincarnate".equals(words.getKey())) {
					button.setMessage(Component.translatable("deathScreen.respawn"));
				}
			}
		}
	}
}
