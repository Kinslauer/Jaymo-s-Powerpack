package com.jaymoskingdoms.client.mixin.mca;

import java.util.List;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** (0.7.175) MCA's tooltip boxes, for the lines {@code McaInteractInfoMixin} adds to its interact screen. */
@Mixin(AbstractDynamicScreen.class)
public interface McaDynamicScreenInvoker {
	@Invoker("drawTooltip")
	void jaymoskingdoms$drawTooltip(GuiGraphicsExtractor graphics, Component text, int x, int y);

	@Invoker("drawTooltip")
	void jaymoskingdoms$drawTooltip(GuiGraphicsExtractor graphics, List<Component> text, int x, int y);
}
