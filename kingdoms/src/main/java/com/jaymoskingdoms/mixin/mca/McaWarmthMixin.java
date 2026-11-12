package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Presence;
import java.util.UUID;
import net.conczin.mca.entity.ai.Memories;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Hearts a stranger gives a good deed come quicker to someone with Presence ({@link Presence#warmed}). */
@Mixin(Memories.class)
abstract class McaWarmthMixin {
	@Shadow
	@Final
	private UUID playerUUID;
	@Shadow
	private int hearts;

	@ModifyVariable(method = "modHearts", at = @At("HEAD"), argsOnly = true)
	private int jaymoskingdoms$warm(int by) {
		return Presence.warmed(this.playerUUID, this.hearts, by);
	}
}
