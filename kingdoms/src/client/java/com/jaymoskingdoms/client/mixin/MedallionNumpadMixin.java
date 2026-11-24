package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.MedallionClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Andy: "all numpad binds should be disabled while the cleric board is active") While the medallion board is up, a numpad
 * key presses only the medallion's own marks: every other binding on the pad gets no press and is not held down. Letting a
 * key go still reaches every binding, so nothing is left held.
 */
@Mixin(KeyMapping.class)
abstract class MedallionNumpadMixin {
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
}
