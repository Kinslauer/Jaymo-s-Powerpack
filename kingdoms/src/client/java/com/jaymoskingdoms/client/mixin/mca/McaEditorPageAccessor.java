package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.VillagerEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.7.165) The page MCA's editor is on, so leaving the creator's name page can save the name ({@code McaDestinyNameMixin}). */
@Mixin(VillagerEditorScreen.class)
public interface McaEditorPageAccessor {
	@Accessor("page")
	String jaymoskingdoms$page();
}
