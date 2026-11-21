package com.jaymoskingdoms.mixin.classtrees;

import com.jaymoskingdoms.RogueExtras;
import com.jaymoskingdoms.Workshop;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Fletcher Ranger's arrows come six a set ({@link RogueExtras#fletched}), and a Gunsmith's shot half again as many
 * ({@link Workshop#smithed}): asked as the crafted count is settled, which vanilla does once for every way of taking
 * from the grid (a click, a shift click, a drop).
 */
@Mixin(ResultSlot.class)
abstract class FletcherCraftMixin {
	@Shadow
	@Final
	private Player player;
	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void jaymoskingdoms$fletcher(ItemStack carried, CallbackInfo ci) {
		if (this.removeCount > 0) {
			RogueExtras.fletched(this.player, carried, this.removeCount);
			Workshop.smithed(this.player, carried, this.removeCount);
		}
	}
}
