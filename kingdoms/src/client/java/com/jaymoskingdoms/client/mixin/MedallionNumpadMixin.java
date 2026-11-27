package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.MedallionClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Andy: "all numpad binds should be disabled while the cleric board is active") While the medallion board is up, a numpad
 * key presses only the medallion's own marks: every other binding on the pad gets no press and is not held down. Letting a
 * key go still reaches every binding, so nothing is left held.
 *
 * <p>(Andy, 2026-11-25: "numpad keybinds are still going through the cleric inputs") Stopping the press on its way in
 * ({@code click}, {@code set}) was not enough in the pack: with the board up, keypad 3, 5, 7 and 9 still opened Jade's
 * recipes, Jade's narrator, the boon screen and combat mode. So the hold now works at the other end too, where a binding is
 * read: while the board is up, any binding but the marks that sits on the numpad reads as not held and its presses are
 * thrown away, however they reached it. ({@link MedallionClient} also clears presses left waiting each tick, for bindings
 * nobody reads while the board is up.)
 */
@Mixin(KeyMapping.class)
abstract class MedallionNumpadMixin {
	@Shadow
	protected InputConstants.Key key;

	@Shadow
	private int clickCount;

	@Inject(method = "click", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$boardClick(InputConstants.Key key, CallbackInfo ci) {
		if (MedallionClient.holdsPad(key)) {
			for (KeyMapping mark : MedallionClient.marks()) {
				MedallionKeyMappingAccessor m = (MedallionKeyMappingAccessor) mark;
				if (key.equals(m.jaymoskingdoms$key())) {
					m.jaymoskingdoms$setClicks(m.jaymoskingdoms$clicks() + 1);
				}
			}
			ci.cancel();
		}
	}

	@Inject(method = "set", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$boardSet(InputConstants.Key key, boolean down, CallbackInfo ci) {
		if (down && MedallionClient.holdsPad(key)) {
			for (KeyMapping mark : MedallionClient.marks()) {
				if (key.equals(((MedallionKeyMappingAccessor) mark).jaymoskingdoms$key())) {
					mark.setDown(true);
				}
			}
			ci.cancel();
		}
	}

	@Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$boardConsume(CallbackInfoReturnable<Boolean> cir) {
		if (MedallionClient.silenced((KeyMapping) (Object) this, this.key)) {
			this.clickCount = 0;
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$boardIsDown(CallbackInfoReturnable<Boolean> cir) {
		if (MedallionClient.silenced((KeyMapping) (Object) this, this.key)) {
			cir.setReturnValue(false);
		}
	}
}
