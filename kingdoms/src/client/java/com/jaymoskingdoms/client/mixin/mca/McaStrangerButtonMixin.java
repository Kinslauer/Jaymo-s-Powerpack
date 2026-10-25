package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Strangers;
import java.util.Set;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.MCAButton;
import net.conczin.mca.entity.interaction.Constraint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.203) MCA's Pick Up, Locations, Follow Me, Stay Here, Ride Mount and Armor buttons are hidden on a villager you're
 * not involved with ({@link Strangers}: family, hired, or {@link Strangers#TRUSTED_HEARTS} hearts). MCA lays the page out
 * again when the villager's ties arrive from the server, so this is looked at again then. The server refuses them too.
 */
@Mixin(targets = "net.conczin.mca.client.gui.AbstractDynamicScreen$ButtonEx")
abstract class McaStrangerButtonMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void jaymoskingdoms$strangers(MCAButton apiButton, AbstractDynamicScreen screen, CallbackInfo ci) {
		if (!Strangers.BUTTONS.contains(apiButton.identifier()) || !(screen instanceof McaInteractScreenAccessor interact)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Set<Constraint> ties = screen.getConstraints();
		int hearts = mc.player == null ? 0 : interact.jaymoskingdoms$villager().getVillagerBrain().getMemoriesForPlayer(mc.player).getHearts();
		if (!Strangers.involved(ties == null ? Set.of() : ties, hearts)) {
			AbstractWidget self = (AbstractWidget)(Object)this;
			self.visible = false;
			self.active = false;
		}
	}
}
