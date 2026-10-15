package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.MCAButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.53) MCA's Change Clothes button ({@code McaScreensMixin}) is only there on a tailor: a grown shepherd
 * ({@code Tailors.is}; the server checks again).
 */
@Mixin(targets = "net.conczin.mca.client.gui.AbstractDynamicScreen$ButtonEx")
abstract class McaTailorButtonMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void jaymoskingdoms$tailorsOnly(MCAButton apiButton, AbstractDynamicScreen screen, CallbackInfo ci) {
		if (!apiButton.identifier().equals("gui.button.tailor")) {
			return;
		}
		boolean tailor = screen instanceof McaInteractScreenAccessor interact
			&& interact.jaymoskingdoms$villager() instanceof Villager v && !v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.SHEPHERD);
		if (!tailor) {
			AbstractWidget self = (AbstractWidget)(Object)this;
			self.visible = false;
			self.active = false;
		}
	}
}
