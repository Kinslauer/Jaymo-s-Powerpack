package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.MCAButton;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.55) MCA's Change Clothes button (Wardrobe Editor since 0.7.166; {@code McaScreensMixin}) is only there on a tailor: a grown shepherd
 * ({@code Tailors.is}; the server checks again). (0.7.69) So are the tailor's costume buttons, and only with Jaymo's
 * Powers (whose Costume Table and designer they use).
 */
@Mixin(targets = "net.conczin.mca.client.gui.AbstractDynamicScreen$ButtonEx")
abstract class McaTailorButtonMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void jaymoskingdoms$tailorsOnly(MCAButton apiButton, AbstractDynamicScreen screen, CallbackInfo ci) {
		String id = apiButton.identifier();
		boolean costume = id.equals("gui.button.costume") || id.equals("gui.button.costume_table");
		if (!id.equals("gui.button.tailor") && !costume) {
			return;
		}
		boolean tailor = screen instanceof McaInteractScreenAccessor interact
			&& interact.jaymoskingdoms$villager() instanceof Villager v && !v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.SHEPHERD);
		if (!tailor || costume && !FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			AbstractWidget self = (AbstractWidget)(Object)this;
			self.visible = false;
			self.active = false;
		}
	}
}
