package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.DestinyScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.7.215) Whether MCA's start-up creator may send you somewhere ({@code McaDestinySkipMixin}). */
@Mixin(DestinyScreen.class)
public interface McaDestinyAccessor {
	@Accessor("allowTeleportation")
	boolean jaymoskingdoms$allowTeleportation();
}
