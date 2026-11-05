package com.jaymospowers.client.mixin.respawnrecap;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;

/**
 * (0.20.50) Empty: applied to {@link Gui} after Respawn Recap's own mixin there (higher priority), so that
 * {@code RespawnRecapPlugin#postApply} (client/compat) sees Respawn Recap's handler in the class and can gate it.
 */
@Mixin(value = Gui.class, priority = 2000)
abstract class RecapGuiMarkerMixin {
}
