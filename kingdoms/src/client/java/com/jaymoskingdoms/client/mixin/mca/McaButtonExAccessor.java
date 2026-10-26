package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.MCAButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.7.230) Which of MCA's buttons a widget is, so a stranger's page can keep only Trade ({@code McaStrangerPageMixin}). */
@Mixin(targets = "net.conczin.mca.client.gui.AbstractDynamicScreen$ButtonEx")
public interface McaButtonExAccessor {
	@Accessor("apiButton")
	MCAButton jaymoskingdoms$apiButton();
}
