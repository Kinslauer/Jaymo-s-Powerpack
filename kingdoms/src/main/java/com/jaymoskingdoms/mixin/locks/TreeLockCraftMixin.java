package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.TreeLocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A trade's stations come out of a crafting grid only for those who learned the trade, and a saw cuts only for a sawyer
 * ({@link TreeLocks#mayCraft}): the result stays in its slot, with a line on the action bar. Injected into
 * {@link Slot#mayPickup}, which always exists, and filtered to result slots: The Graveyard overrides {@code mayPickup} on
 * {@link ResultSlot} (calling the Slot one), and vanilla's ResultSlot declares none, so targeting ResultSlot would fail
 * without The Graveyard.
 */
@Mixin(Slot.class)
abstract class TreeLockCraftMixin {
	@Inject(method = "mayPickup", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$treeLock(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && (Object) this instanceof ResultSlot result && result.hasItem()
				&& !TreeLocks.mayCraft(player, result.getItem(), ((ResultSlotAccessor) result).jaymoskingdoms$craftSlots().getItems())) {
			cir.setReturnValue(false);
		}
	}
}
